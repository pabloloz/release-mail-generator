package release_mail_generator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import release_mail_generator.model.DocumentVersion;
import release_mail_generator.service.DocumentHistoryService;

import java.util.*;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class DocumentHistoryController {

    private final DocumentHistoryService historyService;

    /** Lista todas las versiones (opcionalmente filtradas por tipo). */
    @GetMapping("/api")
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q) {

        List<DocumentVersion> versions;
        if (q != null && !q.isBlank()) {
            versions = historyService.search(q);
        } else if (type != null && !type.isBlank()) {
            versions = historyService.listByType(type);
        } else {
            versions = historyService.listAll();
        }

        List<Map<String, Object>> result = versions.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(result);
    }

    /** Versiones de un documento específico. */
    @GetMapping("/api/versions")
    public ResponseEntity<List<Map<String, Object>>> versions(
            @RequestParam String type, @RequestParam String ref) {
        List<DocumentVersion> versions = historyService.listVersionsOf(type, ref);
        return ResponseEntity.ok(versions.stream().map(this::toSummary).toList());
    }

    /** Contenido completo de una versión. */
    @GetMapping("/api/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return historyService.findById(id)
                .map(v -> {
                    Map<String, Object> m = toSummary(v);
                    m.put("content", v.getContent());
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Compara dos versiones. */
    @GetMapping("/api/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam String v1, @RequestParam String v2) {
        try {
            return ResponseEntity.ok(historyService.compare(v1, v2));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Restaura una versión (crea nueva con el contenido de la anterior). */
    @PostMapping("/api/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String author = body != null ? body.getOrDefault("author", "Sistema") : "Sistema";
            DocumentVersion restored = historyService.restore(id, author);
            return ResponseEntity.ok(toSummary(restored));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Elimina una versión. */
    @DeleteMapping("/api/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        historyService.delete(id);
        return ResponseEntity.ok().build();
    }

    /** Estadísticas del historial. */
    @GetMapping("/api/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(historyService.getStats());
    }

    /** Dashboard completo: contadores, actividad reciente, gráfica. */
    @GetMapping("/api/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(historyService.getDashboardData());
    }

    private Map<String, Object> toSummary(DocumentVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("documentType", v.getDocumentType());
        m.put("documentRef", v.getDocumentRef());
        m.put("title", v.getTitle());
        m.put("author", v.getAuthor());
        m.put("action", v.getAction());
        m.put("format", v.getFormat());
        m.put("versionNumber", v.getVersionNumber());
        m.put("createdAt", v.getCreatedAt().toString());
        m.put("contentSize", v.getContentSize());
        m.put("contentHash", v.getContentHash());
        return m;
    }
}
