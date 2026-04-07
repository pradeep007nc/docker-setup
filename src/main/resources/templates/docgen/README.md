# Document Generation — Playwright PDF Engine

This directory contains the Thymeleaf HTML templates that are rendered into PDFs by a headless
Chromium browser managed via **Microsoft Playwright** (`com.microsoft.playwright:playwright:1.51.0`).

---

## How it works end-to-end

```
HTTP request
   │
   ▼
DocGenService.generateDocument(DocType)
   │
   ├── 1. Thymeleaf renders the template → plain HTML string  (Tomcat thread, ~1-5 ms)
   │
   └── 2. Submits a task to PlaywrightBrowserPool
           │
           └── runs on a dedicated "playwright-slot-N" thread
                   │
                   ├── browser.newContext()      → isolated browser session (~5 ms)
                   ├── page.setContent(html)     → parses & lays out the HTML (~10-30 ms)
                   └── page.pdf(options)         → Chrome prints to PDF (~80-200 ms)
```

The HTTP response (`ResponseEntity<Resource>`) is returned asynchronously via
`CompletableFuture` — Tomcat threads are never blocked waiting for Chrome.

---

## Templates in this directory

| File | Purpose | Key Thymeleaf variables |
|------|---------|------------------------|
| `invoice.html` | Professional A4 invoice with line-item table, subtotal, GST, and totals | `companyName`, `companyEmail`, `companyPhone`, `invoiceNumber`, `customerName`, `customerEmail`, `customerAddress`, `invoiceDate`, `dueDate`, `items` (list of `description`, `quantity`, `rate`, `total`), `subtotal`, `taxRate`, `taxAmount`, `grandTotal`, `notes` |
| `report.html` | Summary report card with stat tiles | `reportTitle`, `reportDate`, `generatedBy`, `totalUsers`, `activeUsers`, `totalRequests`, `failedRequests` |

---

## Configuration reference

All properties live in `application.properties` under the `docgen.browser.*` namespace.
Every property is overridable via an environment variable (shown in parentheses).

### `docgen.browser.pool-size` (env: `DOCGEN_POOL_SIZE`)

```properties
docgen.browser.pool-size=${DOCGEN_POOL_SIZE:3}
```

**What it does:** Controls how many persistent Chromium browser instances are kept alive in the
pool. Each slot is one running Chrome process.

**Why a pool at all:**
- Playwright is **not thread-safe** — a `Playwright` instance and its `Browser` must live on the
  same thread they were created on.
- Launching a fresh browser per request costs ~1-2 seconds. A pre-warmed pool reduces that to
  virtually zero; only the lightweight `newContext()` + `setContent()` + `pdf()` path runs per
  request (~100-300 ms total).
- A fixed pool size caps the number of concurrent Chromium processes, preventing out-of-memory
  crashes under high load.

**Tuning guide:**

| Workload | Recommended value |
|----------|-------------------|
| Dev / local | 1 |
| Low traffic production | 2–3 (default) |
| High concurrency | 4–6 |

> Each slot consumes roughly **150–200 MB of RAM**. Do not exceed available memory.

**Implemented in:** `PlaywrightBrowserPool` constructor — creates one `BrowserSlot` per pool slot,
each with its own `SingleThreadExecutor` named `playwright-slot-N`.

---

### `docgen.browser.page-timeout-ms` (env: `DOCGEN_PAGE_TIMEOUT_MS`)

```properties
docgen.browser.page-timeout-ms=${DOCGEN_PAGE_TIMEOUT_MS:15000}
```

**What it does:** Sets the maximum time (milliseconds) that a single PDF generation task may
take — covering both `page.setContent()` and `page.pdf()` combined.

Applied via:

```java
page.setDefaultTimeout(pageTimeoutMs);
```

`setDefaultTimeout` is a catch-all: it applies to every subsequent Playwright action on that
`Page` object (`setContent`, `pdf`, `waitForSelector`, etc.) unless a per-action timeout
overrides it.

**What happens on timeout:** Playwright throws a `PlaywrightException`. `DocGenService` wraps
this in a `RuntimeException("PDF generation failed")`, which `CompletableFuture.exceptionally`
catches and re-throws to the caller as an HTTP 500.

**Tuning guide:**

| Template complexity | Recommended value |
|--------------------|-------------------|
| Simple (text only) | 5 000 ms |
| Moderate (tables, CSS) | 10 000–15 000 ms (default) |
| Heavy (charts, images) | 20 000–30 000 ms |

---

### `docgen.browser.acquire-timeout-seconds` (env: `DOCGEN_ACQUIRE_TIMEOUT_S`)

```properties
docgen.browser.acquire-timeout-seconds=${DOCGEN_ACQUIRE_TIMEOUT_S:30}
```

**What it does:** How long (in seconds) a request will wait in the queue for a free browser slot
before giving up. Uses `BlockingQueue.poll(timeout, TimeUnit.SECONDS)` internally.

**What happens on timeout:** A `RuntimeException` is thrown:

```
Browser pool exhausted — no slot free after 30s.
Increase docgen.browser.pool-size or review concurrency.
```

This surfaces as an HTTP 500. Consider adding a `@ExceptionHandler` or circuit breaker if you
need a graceful 503 response instead.

**Tuning guide:** Should be set to at least `2 × page-timeout-ms / 1000` so that a queue of
waiting requests can drain after a slow render finishes. The default of 30 s covers 2 sequential
15 s renders.

---

## Chromium launch flags explained

Every browser slot is launched with the following flags (defined in `PlaywrightBrowserPool.CHROME_FLAGS`):

| Flag | Why it is needed |
|------|-----------------|
| `--no-sandbox` | **Required in Docker.** Linux user namespaces (Chrome's default sandbox) are unavailable without `SYS_ADMIN` capability. Without this flag Chrome refuses to start. |
| `--disable-setuid-sandbox` | Disables the setuid sandbox as a second layer; works in conjunction with `--no-sandbox`. |
| `--disable-gpu` | No GPU in a headless container. Prevents Chrome from hanging trying to initialise a graphics device. |
| `--disable-dev-shm-usage` | Docker's `/dev/shm` defaults to only 64 MB. Chrome uses shared memory for renderer IPC; without this flag it crashes on large renders. Forces Chrome to use `/tmp` instead. |
| `--disable-extensions` | No browser extensions in a server environment; removes an attack surface and speeds up launch. |
| `--disable-background-networking` | Stops Chrome from making outbound network requests for Safe Browsing, component updates, etc. Reduces noise and improves startup speed. |
| `--disable-background-timer-throttling` | Prevents Chrome from throttling JS timers for background tabs. Ensures consistent render timing. |
| `--disable-backgrounding-occluded-windows` | Same intent — prevents Chrome from deprioritising "invisible" windows. |
| `--disable-renderer-backgrounding` | Keeps renderer processes at normal priority even when the browser window is not in the foreground (always true in headless mode). |
| `--disable-default-apps` | Skips loading of Chrome's built-in default applications on startup. |
| `--disable-sync` | Disables Google account sync — irrelevant in a server context but would otherwise generate network traffic and login prompts. |
| `--disable-translate` | Disables the page translation feature; removes a source of unexpected page mutations. |
| `--hide-scrollbars` | Hides scrollbars in rendered output so they do not appear in the PDF. |
| `--mute-audio` | Silences any audio — meaningless in headless mode but prevents OS-level errors. |
| `--no-first-run` | Skips Chrome's first-run setup wizard and profile creation delay. |
| `--disable-software-rasterizer` | Disables the software (CPU-only) fallback rasteriser, which is very slow and unnecessary since GPU rasterisation is also disabled. Chrome uses Skia directly. |

### Flags intentionally NOT used

| Flag | Why it was removed |
|------|--------------------|
| `--no-zygote` | Interferes with Chrome's multi-process architecture; causes instability when processing multiple pages. |
| `--single-process` | **Critical omission.** Running Chrome in single-process mode disables its internal safety checks and causes the renderer to crash or disconnect after processing only a few pages in Docker. The `--no-sandbox` + `--disable-setuid-sandbox` pair is the correct Docker solution. |

---

## Per-request Playwright objects explained

### `BrowserContext` — `browser.newContext()`

```java
try (BrowserContext ctx = browser.newContext()) { … }
```

A `BrowserContext` is Chrome's equivalent of an incognito window. It provides **full isolation**
between concurrent requests: separate cookies, localStorage, IndexedDB, and in-flight network
connections. Cost: ~5 ms. The `try-with-resources` guarantees it is closed (and all its pages
destroyed) after each PDF, preventing memory leaks.

### `Page` — `ctx.newPage()`

Represents a single browser tab. Playwright uses it to:
1. Inject HTML (`setContent`)
2. Wait for the DOM to be ready
3. Print to PDF (`page.pdf()`)

### `page.setContent(html, options)` — `WaitUntilState.DOMCONTENTLOADED`

```java
page.setContent(html, new Page.SetContentOptions()
        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
```

Injects the pre-rendered HTML string directly into the page (no HTTP round-trip).

`WaitUntilState.DOMCONTENTLOADED` tells Playwright to return as soon as the DOM is parsed,
equivalent to the browser's `DOMContentLoaded` event. This is intentionally chosen over
`NETWORKIDLE` or `LOAD` because:
- All CSS is **inline** (inside `<style>` tags in the templates) — no external stylesheets to fetch.
- There are no external images or scripts.
- `NETWORKIDLE` would add 500 ms of unnecessary waiting for a network that never goes busy.

### `page.pdf(options)` — PDF options

```java
page.pdf(new Page.PdfOptions()
        .setFormat("A4")
        .setPrintBackground(true)
        .setPreferCSSPageSize(false)
        .setMargin(new Margin()
                .setTop("20mm").setBottom("20mm")
                .setLeft("15mm").setRight("15mm")));
```

| Option | Value | Explanation |
|--------|-------|-------------|
| `setFormat` | `"A4"` | Standard A4 paper (210 × 297 mm). Matches the physical size most printers and accounting systems expect. |
| `setPrintBackground` | `true` | Includes CSS background colours and images in the PDF. Without this, coloured table headers (e.g., the blue `#1a73e8` invoice header row) would print as white. |
| `setPreferCSSPageSize` | `false` | Use the `format` setting (`A4`) rather than any `@page { size: … }` CSS rule. Keeps the output size predictable regardless of CSS authoring. |
| `setMargin.setTop` | `"20mm"` | 20 mm top margin — space for headers / letterhead. |
| `setMargin.setBottom` | `"20mm"` | 20 mm bottom margin — space for footers / page numbers. |
| `setMargin.setLeft` | `"15mm"` | 15 mm left margin — standard binding margin. |
| `setMargin.setRight` | `"15mm"` | 15 mm right margin. |

---

## Threading model

```
Tomcat thread  ──► renderTemplate() ──► browserPool.submit(lambda)
                                               │
                              CompletableFuture is returned immediately
                                               │
                                   [Tomcat thread is freed]
                                               │
                              playwright-slot-N thread picks up lambda
                                               │
                                   newContext → setContent → pdf()
                                               │
                              CompletableFuture resolves → HTTP response sent
```

- **One `SingleThreadExecutor` per slot** (`playwright-slot-N`) — Playwright's thread-affinity
  requirement (every call on a `Playwright`/`Browser`/`Page` must happen on the creating thread)
  is satisfied automatically.
- **`DisposableBean.destroy()`** — Spring calls this on shutdown, which closes every `Browser` and
  `Playwright` instance gracefully on their own threads, then shuts down the executors.

---

## Adding a new template

1. Create `your-template.html` in this directory using `xmlns:th="http://www.thymeleaf.org"`.
2. Keep all CSS **inline** inside a `<style>` tag — no external stylesheets (they would require
   a network round-trip during `setContent`, adding latency).
3. Add the corresponding `DocType` enum value in `DocType.java`.
4. Register the template name and variable bindings in `DocGenTemplateService`.
5. No Playwright configuration changes are needed — the pool handles new document types automatically.

---

## Environment variable quick reference

| Environment variable | Default | Description |
|----------------------|---------|-------------|
| `DOCGEN_POOL_SIZE` | `3` | Number of persistent Chromium browser instances |
| `DOCGEN_PAGE_TIMEOUT_MS` | `15000` | Max ms per PDF render (setContent + pdf combined) |
| `DOCGEN_ACQUIRE_TIMEOUT_S` | `30` | Max seconds to wait for a free slot before rejecting |