package dev.pradeep.dockerbackend.docgen.controller;

import dev.pradeep.dockerbackend.docgen.enums.DocType;
import dev.pradeep.dockerbackend.docgen.service.DocGenService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/public/doc-gen")
public class DocGenController {

    private final DocGenService docGenService;

    public DocGenController(DocGenService docGenService) {
        this.docGenService = docGenService;
    }

    /**
     * Download a generated PDF.
     *
     * GET /api/public/doc-gen/download?type=INVOICE
     * GET /api/public/doc-gen/download?type=REPORT
     *
     * Returns CompletableFuture — Spring MVC releases the Tomcat thread immediately
     * and streams the HTTP response when the PDF is ready (non-blocking end-to-end).
     */
    @GetMapping("/download")
    public CompletableFuture<ResponseEntity<Resource>> download(@RequestParam DocType type) {
        return docGenService.generateDocument(type);
    }
}
