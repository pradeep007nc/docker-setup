package dev.pradeep.dockerbackend.docgen.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Production browser pool for Playwright PDF generation.
 *
 * WHY a pool:
 *   Playwright is NOT thread-safe — each instance must live on the thread that created it.
 *   Launching a new Browser per request costs ~1-2 s. A pool amortises that cost to startup only.
 *   A fixed pool size caps concurrent Chromium processes, preventing OOM under load.
 *
 * Threading model:
 *   Each BrowserSlot owns a SingleThreadExecutor. Playwright + Browser are created ON that thread
 *   and every subsequent PDF task for that slot ALSO runs on that same thread.
 *   This guarantees Playwright's thread-affinity requirement without any user-visible complexity.
 *
 * Per-request cost after warmup:
 *   Acquire slot: ~0 ms (blocking queue poll)
 *   newContext():  ~5 ms  (lightweight isolation per request)
 *   setContent():  ~10-30 ms
 *   page.pdf():    ~80-200 ms
 *   Total: typically under 300 ms vs ~1500 ms when launching a fresh browser each time.
 */
@Component
public class PlaywrightBrowserPool implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);

    private static final List<String> CHROME_FLAGS = List.of(
        "--no-sandbox",                      // required in Docker (no user namespace)
        "--disable-setuid-sandbox",
        "--disable-gpu",
        "--disable-dev-shm-usage",           // Docker: /dev/shm is tiny by default, use /tmp
        "--disable-extensions",
        "--disable-background-networking",
        "--disable-background-timer-throttling",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-default-apps",
        "--disable-sync",
        "--disable-translate",
        "--hide-scrollbars",
        "--mute-audio",
        "--no-first-run",
        "--disable-software-rasterizer"
        // REMOVED: --no-zygote  — interferes with Chromium's process model, causes instability
        // REMOVED: --single-process — CRITICAL: crashes Chromium after a few renders in Docker.
        //          Single-process mode disables Chrome's multi-process safety checks and causes
        //          the renderer to crash/disconnect the browser after processing each page.
        //          --no-sandbox + --disable-setuid-sandbox is the correct Docker approach.
    );

    private final int acquireTimeoutSeconds;
    private final BlockingQueue<BrowserSlot> pool;
    private final List<BrowserSlot> allSlots;

    public PlaywrightBrowserPool(
            @Value("${docgen.browser.pool-size:3}") int poolSize,
            @Value("${docgen.browser.acquire-timeout-seconds:30}") int acquireTimeoutSeconds) {

        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
        this.pool     = new ArrayBlockingQueue<>(poolSize);
        this.allSlots = new ArrayList<>(poolSize);

        log.info("[BROWSER-POOL] Initialising {} browser slot(s)…", poolSize);
        for (int i = 0; i < poolSize; i++) {
            BrowserSlot slot = createSlot(i);
            pool.offer(slot);
            allSlots.add(slot);
        }
        log.info("[BROWSER-POOL] All {} browser slot(s) ready.", poolSize);
    }

    /**
     * Submit a PDF task. The task receives a live Browser and returns PDF bytes.
     * The browser is always returned to the pool after the task completes or fails.
     *
     * The task runs on the slot's dedicated thread — Playwright thread-affinity is guaranteed.
     *
     * @param task Function<Browser, byte[]> — called with the slot's browser on its thread
     * @return CompletableFuture that resolves to the PDF bytes
     */
    public CompletableFuture<byte[]> submit(Function<Browser, byte[]> task) {
        return CompletableFuture.supplyAsync(() -> {
            BrowserSlot slot = acquire();
            // Tracks whether the slot (or its replacement) has already been returned to the pool.
            // Using a single-element array so the value can be mutated inside the lambda
            // without violating the effectively-final rule for inner lambdas.
            boolean[] returned = {false};
            try {
                // Extract browser as a separate effectively-final local so the inner
                // lambda can capture it safely — slot is not captured by any inner lambda.
                Browser browser = slot.browser();
                byte[] result = slot.executor()
                                    .submit(() -> task.apply(browser))
                                    .get();
                return result;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.offer(slot);
                returned[0] = true;
                throw new RuntimeException("PDF task interrupted", e);

            } catch (Exception e) {
                returnOrReplace(slot);
                returned[0] = true;
                throw new RuntimeException("PDF generation failed", e);

            } finally {
                // Success path: neither catch ran, so we return the slot here.
                // All exception paths already set returned[0] = true.
                if (!returned[0]) {
                    returnOrReplace(slot);
                }
            }
        });
    }

    public int availableSlots() { return pool.size(); }
    public int totalSlots()     { return allSlots.size(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void destroy() {
        log.info("[BROWSER-POOL] Shutting down {} slot(s)…", allSlots.size());
        allSlots.forEach(BrowserSlot::shutdownQuietly);
        log.info("[BROWSER-POOL] Shutdown complete.");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Checks browser health on the slot's own thread, then either returns it to the pool
     * or replaces it if the browser has disconnected (crashed, OOM-killed, etc.).
     * Called on both the success path and the error path so a dead browser is NEVER
     * silently put back into rotation.
     */
    private void returnOrReplace(BrowserSlot slot) {
        try {
            // isConnected() must run on the slot's thread (Playwright thread-affinity)
            boolean alive = slot.executor().submit(() -> slot.browser().isConnected()).get();
            if (alive) {
                pool.offer(slot);
            } else {
                log.warn("[BROWSER-POOL] Slot {} browser disconnected — recreating", slot.id());
                BrowserSlot replacement = createSlot(slot.id());
                allSlots.set(slot.id(), replacement);
                slot.shutdownQuietly();
                pool.offer(replacement);
            }
        } catch (Exception ex) {
            // If we can't even check connectivity, assume dead and replace
            log.warn("[BROWSER-POOL] Slot {} health check failed — recreating: {}", slot.id(), ex.getMessage());
            try {
                BrowserSlot replacement = createSlot(slot.id());
                allSlots.set(slot.id(), replacement);
                pool.offer(replacement);
            } catch (Exception recreateEx) {
                log.error("[BROWSER-POOL] Slot {} failed to recreate: {}", slot.id(), recreateEx.getMessage());
            } finally {
                slot.shutdownQuietly();
            }
        }
    }

    private BrowserSlot createSlot(int id) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "playwright-slot-" + id);
            t.setDaemon(true);
            return t;
        });
        try {
            // Bootstrap Playwright and Browser on the slot's own thread — must stay there
            return executor.submit(() -> {
                Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(CHROME_FLAGS)
                );
                log.info("[BROWSER-POOL] Slot {} ready on thread '{}'", id, Thread.currentThread().getName());
                return new BrowserSlot(id, playwright, browser, executor);
            }).get();

        } catch (Exception e) {
            executor.shutdownNow();
            throw new RuntimeException("Failed to start browser slot " + id, e);
        }
    }

    private BrowserSlot acquire() {
        try {
            BrowserSlot slot = pool.poll(acquireTimeoutSeconds, TimeUnit.SECONDS);
            if (slot == null) {
                throw new RuntimeException(
                    "Browser pool exhausted — no slot free after " + acquireTimeoutSeconds + "s. " +
                    "Increase docgen.browser.pool-size or review concurrency.");
            }
            return slot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for browser slot", e);
        }
    }

    // ── Slot ─────────────────────────────────────────────────────────────────

    public record BrowserSlot(int id, Playwright playwright, Browser browser, ExecutorService executor) {

        void shutdownQuietly() {
            try {
                executor.submit(() -> {
                    try { browser.close();    } catch (Exception ignored) {}
                    try { playwright.close(); } catch (Exception ignored) {}
                }).get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
