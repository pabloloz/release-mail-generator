package release_mail_generator.controller;

import release_mail_generator.model.UatRequest;
import release_mail_generator.service.UatEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Controlador VoBo UAT — version ligera (sin persistencia).
 * El formulario vive como tab embebido en index.html.
 */
@Controller
public class UatController {

    @Autowired
    private UatEmailService uatEmailService;

    /** Redirige /uat al inicio (el tab vive en index.html). */
    @GetMapping("/uat")
    public String uatRedirect() {
        return "redirect:/";
    }

    /** Genera la vista previa HTML del correo. */
    @PostMapping("/generate-uat")
    @ResponseBody
    public String generateUat(@RequestBody UatRequest r) {
        return uatEmailService.generateEmail(r);
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
        String page = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<title>VoBo UAT \u2013 " + escHtml(r.getRfcNumber()) + "</title></head><body>"
                + body + "</body></html>";
        String filename = "VoBo-UAT-" + sanitize(r.getRfcNumber()) + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(page.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "sin-rfc";
        return s.trim().replaceAll("[^a-zA-Z0-9\\-_]", "-");
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}