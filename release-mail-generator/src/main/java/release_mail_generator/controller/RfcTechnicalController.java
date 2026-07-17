package release_mail_generator.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import release_mail_generator.model.RfcTechnicalRecord;
import release_mail_generator.service.RfcTechnicalService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/rfc")
public class RfcTechnicalController {

    private final RfcTechnicalService rfcService;

    public RfcTechnicalController(RfcTechnicalService rfcService) {
        this.rfcService = rfcService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("rfcList", rfcService.findAll());
        return "rfc-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("record", new RfcTechnicalRecord());
        model.addAttribute("isEdit", false);
        return "rfc-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        return rfcService.findById(id)
                .map(r -> {
                    model.addAttribute("record", r);
                    model.addAttribute("isEdit", true);
                    return "rfc-form";
                })
                .orElse("redirect:/rfc");
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        return rfcService.findById(id)
                .map(r -> {
                    model.addAttribute("record", r);
                    return "rfc-view";
                })
                .orElse("redirect:/rfc");
    }

    @PostMapping(value = "/save",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> save(@RequestBody RfcTechnicalRecord record) {
        try {
            RfcTechnicalRecord saved = rfcService.save(record);
            return ResponseEntity.ok(Map.of(
                    "id",       saved.getId(),
                    "redirect", "/rfc/" + saved.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        return rfcService.findById(id)
                .map(r -> {
                    try {
                        byte[] pdf = rfcService.generatePdf(r);
                        String filename = "RFC_" + sanitize(r.getRfcNumber()) + ".pdf";
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(pdf);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().<byte[]>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/markdown")
    public ResponseEntity<byte[]> downloadMarkdown(@PathVariable String id) {
        return rfcService.findById(id)
                .map(r -> {
                    byte[] md = rfcService.generateMarkdown(r);
                    String filename = "RFC_" + sanitize(r.getRfcNumber()) + ".md";
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                            .body(md);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/testcases-pdf")
    public ResponseEntity<byte[]> downloadTestCasesPdf(@PathVariable String id) {
        return rfcService.findById(id)
                .map(r -> {
                    try {
                        byte[] pdf = rfcService.generateTestCasesPdf(r);
                        String filename = "CasosPrueba_" + sanitize(r.getRfcNumber()) + ".pdf";
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(pdf);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().<byte[]>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/testcases-markdown")
    public ResponseEntity<byte[]> downloadTestCasesMarkdown(@PathVariable String id) {
        return rfcService.findById(id)
                .map(r -> {
                    byte[] md = rfcService.generateTestCasesMarkdown(r);
                    String filename = "CasosPrueba_" + sanitize(r.getRfcNumber()) + ".md";
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                            .body(md);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        rfcService.delete(id);
        return "redirect:/rfc";
    }

    /** JSON search endpoint for the RFC search bar. */
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, String>> search(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return List.of();
        return rfcService.search(q).stream().map(r -> Map.of(
                "id",         r.getId() != null ? r.getId() : "",
                "rfcNumber",  r.getRfcNumber() != null ? r.getRfcNumber() : "",
                "changeName", r.getChangeName() != null ? r.getChangeName() : "",
                "status",     r.getStatus() != null ? r.getStatus() : "",
                "testerName", r.getTesterName() != null ? r.getTesterName() : ""
        )).toList();
    }

    /** JSON endpoint to get a single RFC record for inline loading. */
    @GetMapping(value = "/{id}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<RfcTechnicalRecord> getJson(@PathVariable String id) {
        return rfcService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "RFC";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
