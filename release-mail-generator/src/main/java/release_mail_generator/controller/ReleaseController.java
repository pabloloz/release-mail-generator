package release_mail_generator.controller;

import release_mail_generator.model.ReleaseRequest;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.service.EmailGeneratorService;
import release_mail_generator.service.DocumentHistoryService;
import release_mail_generator.util.FileNameUtils;
import release_mail_generator.util.StringHelper;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Controller
public class ReleaseController {

    private final EmailGeneratorService emailGeneratorService;
    private final DocumentHistoryService historyService;

    public ReleaseController(
            EmailGeneratorService emailGeneratorService,
            DocumentHistoryService historyService
    ) {
        this.emailGeneratorService = emailGeneratorService;
        this.historyService = historyService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("releaseRequest", new ReleaseRequest());
        return "index";
    }

    @PostMapping("/generate")
    public String generateEmail(
            @ModelAttribute ReleaseRequest releaseRequest,
            Model model
    ) {
        String generatedEmail = emailGeneratorService.generateEmail(releaseRequest);
        historyService.saveVersion("RELEASE",
                nvl(releaseRequest.getVersion(), "sin-version"),
                "Correo de Liberación" + (releaseRequest.getVersion() != null && !releaseRequest.getVersion().isBlank() ? " v" + releaseRequest.getVersion() : ""),
                null, generatedEmail, "CREATED", "HTML");
        model.addAttribute("generatedEmail", generatedEmail);
        model.addAttribute("releaseRequest", releaseRequest);
        model.addAttribute("activeTab", "releases");
        return "index";
    }

    @PostMapping(value = "/generate-release-message", consumes = "application/json", produces = "text/plain")
    @ResponseBody
    public String generateReleaseMessage(@RequestBody ReleaseRequest releaseRequest) {
        String msg = emailGeneratorService.generateTelegramMessage(releaseRequest);
        historyService.saveVersion("TELEGRAM",
                nvl(releaseRequest.getVersion(), "sin-version"),
                "Telegram Liberación" + (releaseRequest.getVersion() != null && !releaseRequest.getVersion().isBlank() ? " v" + releaseRequest.getVersion() : ""),
                null, msg, "CREATED", "TEXT");
        return msg;
    }

    @PostMapping(value = "/generate-rdl", consumes = "application/json", produces = "text/html")
    @ResponseBody
    public String generateRdlEmail(@RequestBody RdlReleaseRequest rdlReleaseRequest) {
        String html = emailGeneratorService.generateRdlEmail(rdlReleaseRequest);
        historyService.saveVersion("RDL",
                nvl(rdlReleaseRequest.getRdlReleaseDate(), "sin-fecha"),
                "Correo RDL" + (rdlReleaseRequest.getRdlReleaseDate() != null && !rdlReleaseRequest.getRdlReleaseDate().isBlank() ? " " + rdlReleaseRequest.getRdlReleaseDate() : ""),
                null, html, "CREATED", "HTML");
        return html;
    }

    @PostMapping(value = "/generate-rdl-message", consumes = "application/json", produces = "text/plain")
    @ResponseBody
    public String generateRdlMessage(@RequestBody RdlReleaseRequest rdlReleaseRequest) {
        String msg = emailGeneratorService.generateRdlTelegramMessage(rdlReleaseRequest);
        historyService.saveVersion("RDL_TELEGRAM",
                nvl(rdlReleaseRequest.getRdlReleaseDate(), "sin-fecha"),
                "Telegram RDL" + (rdlReleaseRequest.getRdlReleaseDate() != null && !rdlReleaseRequest.getRdlReleaseDate().isBlank() ? " " + rdlReleaseRequest.getRdlReleaseDate() : ""),
                null, msg, "CREATED", "TEXT");
        return msg;
    }

    // ── Export: Release Email ─────────────────────────────────────────────

    @PostMapping(value = "/release/export/pdf", consumes = "application/json")
    public ResponseEntity<byte[]> exportReleasePdf(@RequestBody ReleaseRequest r) throws Exception {
        byte[] pdf = emailGeneratorService.generateReleasePdf(r);
        String filename = "Liberacion-" + sanitize(r.getVersion()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping(value = "/release/export/markdown", consumes = "application/json")
    public ResponseEntity<byte[]> exportReleaseMarkdown(@RequestBody ReleaseRequest r) {
        byte[] md = emailGeneratorService.generateReleaseMarkdown(r);
        String filename = "Liberacion-" + sanitize(r.getVersion()) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(md);
    }

    @PostMapping(value = "/release/export/html", consumes = "application/json")
    public ResponseEntity<byte[]> exportReleaseHtml(@RequestBody ReleaseRequest r) {
        String body = emailGeneratorService.generateEmail(r);
        String page = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<title>Liberaci\u00f3n " + escHtml(r.getVersion()) + "</title></head><body>"
                + body + "</body></html>";
        String filename = "Liberacion-" + sanitize(r.getVersion()) + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(page.getBytes(StandardCharsets.UTF_8));
    }

    // ── Export: RDL Email ─────────────────────────────────────────────────

    @PostMapping(value = "/rdl/export/pdf", consumes = "application/json")
    public ResponseEntity<byte[]> exportRdlPdf(@RequestBody RdlReleaseRequest r) throws Exception {
        byte[] pdf = emailGeneratorService.generateRdlPdf(r);
        String filename = "RDL-" + sanitize(r.getRdlReleaseDate()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping(value = "/rdl/export/markdown", consumes = "application/json")
    public ResponseEntity<byte[]> exportRdlMarkdown(@RequestBody RdlReleaseRequest r) {
        byte[] md = emailGeneratorService.generateRdlMarkdown(r);
        String filename = "RDL-" + sanitize(r.getRdlReleaseDate()) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(md);
    }

    @PostMapping(value = "/rdl/export/html", consumes = "application/json")
    public ResponseEntity<byte[]> exportRdlHtml(@RequestBody RdlReleaseRequest r) {
        String body = emailGeneratorService.generateRdlEmail(r);
        String page = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<title>Reporte RDL</title></head><body>" + body + "</body></html>";
        String filename = "RDL-" + sanitize(r.getRdlReleaseDate()) + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(page.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitize(String s) {
        return FileNameUtils.sanitize(s, "sin-version");
    }

    private String escHtml(String s) {
        return StringHelper.escapeHtml(s);
    }

    private String nvl(String s, String fallback) {
        return StringHelper.nvl(s, fallback);
    }
}

