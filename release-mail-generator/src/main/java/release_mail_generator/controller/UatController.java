package release_mail_generator.controller;

import release_mail_generator.model.UatRequest;
import release_mail_generator.service.UatEmailService;
import release_mail_generator.service.DocumentHistoryService;
import release_mail_generator.util.FileNameUtils;
import release_mail_generator.util.StringHelper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Controlador VoBo UAT.
 */
@Controller
public class UatController {

    private final UatEmailService uatEmailService;
    private final DocumentHistoryService historyService;

    public UatController(UatEmailService uatEmailService, DocumentHistoryService historyService) {
        this.uatEmailService = uatEmailService;
        this.historyService = historyService;
    }

    /** Redirige /uat al inicio (el tab vive en index.html). */
    @GetMapping("/uat")
    public String uatRedirect() {
        return "redirect:/";
    }

    /** Genera la vista previa HTML del correo. */
    @PostMapping("/generate-uat")
    @ResponseBody
    public String generateUat(@RequestBody UatRequest r) {
        String html = uatEmailService.generateEmail(r);
        String ref = (r.getRfcNumber() != null && !r.getRfcNumber().isBlank()) ? r.getRfcNumber().trim() : "";
        String rfcName = (r.getRfcName() != null && !r.getRfcName().isBlank()) ? r.getRfcName().trim() : "";
        String title = "VoBo UAT" + (!ref.isEmpty() ? " RFC " + ref : "") + (!rfcName.isEmpty() ? " — " + rfcName : "");
        historyService.saveVersion("UAT", ref.isEmpty() ? "sin-rfc" : ref, title, null, html, "CREATED", "HTML");
        return html;
    }

    /** Exporta el correo como PDF. */
    @PostMapping("/uat/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody UatRequest r) throws Exception {
        byte[] pdf = uatEmailService.generatePdf(r);
        String filename = "VoBo-UAT-" + sanitize(r.getRfcNumber()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Exporta el correo como Markdown. */
    @PostMapping("/uat/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@RequestBody UatRequest r) {
        byte[] md = uatEmailService.generateMarkdown(r);
        String filename = "VoBo-UAT-" + sanitize(r.getRfcNumber()) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(md);
    }

    /** Exporta el correo como archivo HTML standalone. */
    @PostMapping("/uat/export/html")
    public ResponseEntity<byte[]> exportHtml(@RequestBody UatRequest r) {
        String body = uatEmailService.generateEmail(r);
        String rfcNum = r.getRfcNumber() != null ? r.getRfcNumber() : "";
        String page = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<title>VoBo UAT \u2013 " + escHtml(rfcNum) + "</title></head><body>"
                + body + "</body></html>";
        String filename = "VoBo-UAT-" + sanitize(rfcNum) + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(page.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitize(String s) {
        return FileNameUtils.sanitize(s, "sin-rfc");
    }

    private String escHtml(String s) {
        return StringHelper.escapeHtml(s);
    }
}