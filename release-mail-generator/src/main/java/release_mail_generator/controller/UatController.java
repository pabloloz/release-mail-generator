package release_mail_generator.controller;

import release_mail_generator.model.UatRequest;
import release_mail_generator.service.UatEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/uat")
public class UatController {

    @Autowired
    private UatEmailService uatEmailService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("requests", uatEmailService.findAll());
        return "uat-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("record", new UatRequest());
        model.addAttribute("isNew", true);
        return "uat-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        UatRequest record = uatEmailService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UAT request not found: " + id));
        model.addAttribute("record", record);
        model.addAttribute("isNew", false);
        return "uat-form";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        UatRequest record = uatEmailService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UAT request not found: " + id));
        model.addAttribute("record", record);
        model.addAttribute("emailHtml", uatEmailService.generateEmail(record));
        return "uat-view";
    }

    @PostMapping("/preview")
    @ResponseBody
    public String preview(@RequestBody UatRequest r) {
        return uatEmailService.generateEmail(r);
    }

    @PostMapping("/save")
    @ResponseBody
    public Map<String, String> save(@RequestBody UatRequest r) {
        UatRequest saved = uatEmailService.save(r);
        return Map.of("id", saved.getId(), "redirect", "/uat/" + saved.getId());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) throws Exception {
        UatRequest record = uatEmailService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UAT request not found: " + id));
        byte[] pdf = uatEmailService.generatePdf(record);
        String filename = "VoBo-UAT-" + sanitize(record.getRfcNumber()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{id}/markdown")
    public ResponseEntity<byte[]> downloadMarkdown(@PathVariable String id) {
        UatRequest record = uatEmailService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UAT request not found: " + id));
        byte[] md = uatEmailService.generateMarkdown(record);
        String filename = "VoBo-UAT-" + sanitize(record.getRfcNumber()) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(md);
    }

    @GetMapping("/{id}/html")
    public ResponseEntity<byte[]> downloadHtml(@PathVariable String id) {
        UatRequest record = uatEmailService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("UAT request not found: " + id));
        String body = uatEmailService.generateEmail(record);
        String page = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<title>VoBo UAT \u2013 " + record.getRfcNumber() + "</title></head><body>"
                + body + "</body></html>";
        String filename = "VoBo-UAT-" + sanitize(record.getRfcNumber()) + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(page.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        uatEmailService.delete(id);
        return "redirect:/uat";
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "sin-rfc";
        return s.trim().replaceAll("[^a-zA-Z0-9\\-_]", "-");
    }
}
