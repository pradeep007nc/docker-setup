package dev.pradeep.dockerbackend.docgen.service;

import dev.pradeep.dockerbackend.docgen.enums.DocType;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocGenTemplateService {

    private final TemplateEngine templateEngine;

    public DocGenTemplateService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderTemplate(DocType docType) {
        Map<String, Object> data = switch (docType) {
            case INVOICE -> getDummyInvoiceData();
            case REPORT  -> getDummyReportData();
        };
        Context context = new Context();
        context.setVariables(data);
        return templateEngine.process("docgen/" + docType.name().toLowerCase(), context);
    }

    private Map<String, Object> getDummyInvoiceData() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        String due   = LocalDate.now().plusDays(30).format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("Backend API Development", 10, "150.00", "1,500.00"));
        items.add(item("Docker & Nginx Setup",     3,  "200.00",   "600.00"));
        items.add(item("JWT Security Integration", 5,  "180.00",   "900.00"));
        items.add(item("Database Schema Design",   2,  "250.00",   "500.00"));

        Map<String, Object> data = new HashMap<>();
        data.put("invoiceNumber",    "INV-2026-0042");
        data.put("invoiceDate",      today);
        data.put("dueDate",          due);
        data.put("customerName",     "Acme Corporation");
        data.put("customerEmail",    "billing@acme.com");
        data.put("customerAddress",  "742 Evergreen Terrace, Springfield, IL 62701");
        data.put("companyName",      "Pradeep Dev Solutions");
        data.put("companyEmail",     "pradeep@devsolutions.io");
        data.put("companyPhone",     "+91 98765 43210");
        data.put("items",            items);
        data.put("subtotal",         "3,500.00");
        data.put("taxRate",          "18%");
        data.put("taxAmount",        "630.00");
        data.put("grandTotal",       "4,130.00");
        data.put("notes",            "Payment due within 30 days. Bank transfer preferred.");
        return data;
    }

    private Map<String, Object> getDummyReportData() {
        Map<String, Object> data = new HashMap<>();
        data.put("reportTitle",   "Monthly Activity Report");
        data.put("reportDate",    LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        data.put("generatedBy",   "System");
        data.put("totalUsers",    "128");
        data.put("activeUsers",   "94");
        data.put("totalRequests", "45,231");
        data.put("failedRequests","312");
        return data;
    }

    private Map<String, Object> item(String desc, int qty, String rate, String total) {
        return Map.of(
            "description", desc,
            "quantity",    qty,
            "rate",        rate,
            "total",       total
        );
    }
}
