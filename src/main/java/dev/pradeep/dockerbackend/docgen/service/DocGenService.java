package dev.pradeep.dockerbackend.docgen.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import dev.pradeep.dockerbackend.docgen.config.PlaywrightBrowserPool;
import dev.pradeep.dockerbackend.docgen.enums.DocType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class DocGenService {

    private static final Logger log = LoggerFactory.getLogger(DocGenService.class);

    private final PlaywrightBrowserPool browserPool;
    private final DocGenTemplateService templateService;
    private final int pageTimeoutMs;

    public DocGenService(
            PlaywrightBrowserPool browserPool,
            DocGenTemplateService templateService,
            @Value("${docgen.browser.page-timeout-ms:15000}") int pageTimeoutMs) {
        this.browserPool     = browserPool;
        this.templateService = templateService;
        this.pageTimeoutMs   = pageTimeoutMs;
    }

    /**
     * Async PDF generation.
     * Thymeleaf rendering runs on the calling (Tomcat) thread — it's pure CPU, no I/O.
     * Chrome rendering is offloaded to the pool's dedicated thread via CompletableFuture.
     * The Tomcat thread is freed immediately after submitting to the pool.
     *
     * Spring MVC resolves CompletableFuture<ResponseEntity> transparently —
     * the HTTP response is streamed when the future completes.
     */
    public CompletableFuture<ResponseEntity<Resource>> generateDocument(DocType docType) {
        log.info("[DOC-GEN] Queuing {} — pool slots free: {}/{}",
                docType, browserPool.availableSlots(), browserPool.totalSlots());

        // Render HTML on the calling thread (CPU-only, ~1-5 ms — no need to offload)
        String html     = templateService.renderTemplate(docType);
        String filename = docType.name().toLowerCase() + ".pdf";

        return browserPool.submit(browser -> {
                    // This lambda runs on the slot's dedicated thread — Playwright thread-safe
                    long t0 = System.currentTimeMillis();

                    // BrowserContext: fresh per request — provides full isolation
                    // (separate cookies, storage, in-flight network) at ~5 ms cost
                    try (BrowserContext ctx = browser.newContext()) {

                        Page page = ctx.newPage();
                        page.setDefaultTimeout(pageTimeoutMs);

                        // Inject pre-rendered HTML directly — skips all network latency.
                        // DOMCONTENTLOADED is enough: we control the HTML, there are no
                        // external stylesheets or images to wait for.
                        page.setContent(html, new Page.SetContentOptions()
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                        byte[] pdf = page.pdf(new Page.PdfOptions()
                                .setFormat("A4")
                                .setPrintBackground(true)
                                .setPreferCSSPageSize(false)
                                .setMargin(new Margin()
                                        .setTop("20mm")
                                        .setBottom("20mm")
                                        .setLeft("15mm")
                                        .setRight("15mm"))
                        );

                        log.info("[DOC-GEN] {} rendered in {} ms ({} bytes)",
                                docType, System.currentTimeMillis() - t0, pdf.length);
                        return pdf;
                    }
                })
                .thenApply(pdfBytes ->
                        ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_PDF)
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + filename + "\"")
                                .contentLength(pdfBytes.length)
                                .<Resource>body(new ByteArrayResource(pdfBytes))
                )
                .exceptionally(ex -> {
                    log.error("[DOC-GEN] Failed to generate {} PDF: {}", docType, ex.getMessage());
                    throw new RuntimeException("PDF generation failed for " + docType, ex);
                });
    }
}
