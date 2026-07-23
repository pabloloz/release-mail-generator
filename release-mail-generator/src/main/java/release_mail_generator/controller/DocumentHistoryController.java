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

    /** Lista versiones con paginación, filtro y ordenamiento. */
    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newest") String sort) {

        List<DocumentVersion> all;
        if (q != null && !q.isBlank()) {
            all = historyService.search(q);
        } else if (type != null && !type.isBlank() && !"ALL".equals(type)) {
            all = historyService.listByType(type);
        } else {
            all = historyService.listAll();
        }

        // Sort
        Comparator<DocumentVersion> cmp = switch (sort) {
            case "oldest" -> Comparator.comparing(DocumentVersion::getCreatedAt);
            case "title"  -> Comparator.comparing(v -> v.getTitle() != null ? v.getTitle().toLowerCase() : "", Comparator.naturalOrder());
            case "type"   -> Comparator.comparing(DocumentVersion::getDocumentType);
            default       -> Comparator.comparing(DocumentVersion::getCreatedAt).reversed();
        };
        all = all.stream().sorted(cmp).toList();

        int total = all.size();
        size = Math.max(1, Math.min(size, 100));
        int totalPages = (int) Math.ceil((double) total / size);
        page = Math.max(1, Math.min(page, Math.max(1, totalPages)));

        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<Map<String, Object>> items = all.subList(from, to).stream().map(this::toSummary).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("totalPages", totalPages);
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
