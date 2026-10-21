package release_mail_generator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import release_mail_generator.model.DocumentVersion;
import release_mail_generator.repository.DocumentVersionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentHistoryService {

    private final DocumentVersionRepository repo;

    /**
     * Guarda una nueva versión de un documento.
     * Si el contenido es idéntico al último, no crea duplicado.
     */
    public DocumentVersion saveVersion(String type, String ref, String title,
                                        String author, String content,
                                        String action, String format) {
        String hash = sha256(content);

        // Evitar duplicados consecutivos del mismo contenido (skip for EXPORTED actions — each export counts)
        if (!"EXPORTED".equals(action)) {
            List<DocumentVersion> existing = repo.findVersions(type, ref);
            if (!existing.isEmpty() && hash.equals(existing.get(0).getContentHash())) {
                return existing.get(0);
            }
        }

        int nextVersion = repo.findMaxVersion(type, ref) + 1;

        DocumentVersion v = DocumentVersion.builder()
                .id(UUID.randomUUID().toString())
                .documentType(type)
                .documentRef(ref != null ? ref : "")
                .title(title != null ? title : type)
                .author(author != null && !author.isBlank() ? author : "Sistema")
                .content(content)
                .contentHash(hash)
                .action(action)
                .format(format)
                .versionNumber(nextVersion)
                .createdAt(LocalDateTime.now())
                .contentSize(content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0)
                .build();

        return repo.save(v);
    }

    public List<DocumentVersion> listAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public List<DocumentVersion> listByType(String type) {
        return repo.findByDocumentTypeOrderByCreatedAtDesc(type);
    }

    /**
     * Variantes ligeras para listados/paginación: no cargan el CLOB `content`
     * (HTML con imágenes base64 embebidas), que podía causar picos de memoria
     * al listar el historial completo en cada carga de página.
     */
    public List<DocumentVersionRepository.DocumentVersionSummary> listAllSummaries() {
        return repo.findSummaryAllByOrderByCreatedAtDesc();
    }

    public List<DocumentVersionRepository.DocumentVersionSummary> listByTypeSummaries(String type) {
        return repo.findSummaryByDocumentType(type);
    }

    public List<DocumentVersionRepository.DocumentVersionSummary> searchSummaries(String query) {
        if (query == null || query.isBlank()) return listAllSummaries();
        return repo.searchSummary(query.trim());
    }

    /** Resultado de una página del historial, listo para que el controller lo serialice. */
    public record PageResult(List<DocumentVersionRepository.DocumentVersionSummary> items,
                              int page, int size, long total, int totalPages) { }

    /**
     * Listado paginado del historial a nivel de base de datos (sin cargar todos los registros
     * en memoria). Combina filtro por tipo y búsqueda si ambos vienen presentes, y aplica el
     * orden solicitado (newest/oldest/title/type) con desempate estable por id.
     *
     * <p>Páginas: 1-indexadas. {@code size} se acota entre 1 y 100. Si {@code page} queda fuera
     * de rango (menor a 1, o mayor al total de páginas disponibles), se ajusta ("clamp") a la
     * página válida más cercana (1 o la última), igual que el comportamiento previo de la UI —
     * nunca se lanza un error por un número de página fuera de rango.</p>
     */
    public PageResult findPage(String type, String q, String sort, int page, int size) {
        String normalizedType = (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) ? null : type;
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        int safeSize = Math.max(1, Math.min(size, 100));
        Sort sortObj = buildSort(sort);

        int initialIndex = Math.max(0, page - 1);
        Page<DocumentVersionRepository.DocumentVersionSummary> result =
                repo.findPage(normalizedType, normalizedQ, PageRequest.of(initialIndex, safeSize, sortObj));

        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        int safePage = Math.max(1, Math.min(page, Math.max(1, totalPages)));

        if (safePage - 1 != result.getNumber()) {
            result = repo.findPage(normalizedType, normalizedQ, PageRequest.of(safePage - 1, safeSize, sortObj));
        }

        return new PageResult(result.getContent(), safePage, safeSize, total, totalPages);
    }

    /**
     * Mapea los 4 órdenes soportados por la UI (newest/oldest/title/type) a un {@link Sort} de
     * Spring Data, siempre con desempate estable por {@code id} para que el orden no cambie
     * entre páginas cuando hay valores repetidos (misma fecha, mismo título, etc.).
     * "title" usa {@code ignoreCase()} para igualar el comportamiento previo (comparación
     * insensible a mayúsculas hecha en memoria con {@code toLowerCase()}).
     */
    private Sort buildSort(String sort) {
        Sort.Order primary = switch (sort) {
            case "oldest" -> Sort.Order.asc("createdAt");
            case "title"  -> Sort.Order.asc("title").ignoreCase();
            case "type"   -> Sort.Order.asc("documentType");
            default       -> Sort.Order.desc("createdAt"); // "newest"
        };
        return Sort.by(primary, Sort.Order.asc("id"));
    }

    public List<DocumentVersionRepository.DocumentVersionSummary> listVersionsOf(String type, String ref) {
        return repo.findVersionsSummary(type, ref);
    }

    public Optional<DocumentVersion> findById(String id) {
        return repo.findById(id);
    }

    public List<DocumentVersion> search(String query) {
        if (query == null || query.isBlank()) return listAll();
        return repo.search(query.trim());
    }

    /** Restaura una versión: crea una nueva versión con el contenido de la anterior. */
    public DocumentVersion restore(String versionId, String author) {
        DocumentVersion original = repo.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Versión no encontrada: " + versionId));

        return saveVersion(
                original.getDocumentType(),
                original.getDocumentRef(),
                original.getTitle(),
                author,
                original.getContent(),
                "RESTORED",
                original.getFormat()
        );
    }

    public void delete(String id) {
        repo.deleteById(id);
    }

    /** Estadísticas para el dashboard del historial. */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = repo.count();
        stats.put("total", total);
        for (String type : List.of("RFC", "UAT", "RELEASE", "RDL", "TELEGRAM", "RDL_TELEGRAM")) {
            stats.put(type, repo.countByDocumentType(type));
        }
        return stats;
    }

    /** Dashboard completo con estadísticas, actividad reciente y gráfica. */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // Counts by type
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : repo.countByTypeGrouped()) {
            counts.put((String) row[0], (Long) row[1]);
        }
        data.put("counts", counts);
        data.put("total", repo.count());
        data.put("exports", repo.countByFormat("PDF") + repo.countByFormat("MARKDOWN"));
        data.put("restored", repo.countByAction("RESTORED"));

        // Recent activity (last 10) — proyección ligera, sin el CLOB content
        List<Map<String, Object>> recent = repo.findTop10Summary(org.springframework.data.domain.PageRequest.of(0, 10))
                .stream().map(this::toMetadata).toList();
        data.put("recent", recent);

        // Daily activity chart (last 14 days) — agrupado en Java (portable entre H2 y PostgreSQL;
        // ver DocumentVersionRepository.findCreatedAtSince).
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        Map<java.time.LocalDate, Long> byDay = new TreeMap<>();
        for (LocalDateTime createdAt : repo.findCreatedAtSince(since)) {
            byDay.merge(createdAt.toLocalDate(), 1L, Long::sum);
        }
        List<Map<String, Object>> chart = new java.util.ArrayList<>();
        for (Map.Entry<java.time.LocalDate, Long> entry : byDay.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day", entry.getKey().toString());
            point.put("count", entry.getValue().intValue());
            chart.add(point);
        }
        data.put("chart", chart);

        return data;
    }

    /** Retorna los dos contenidos para comparación client-side. */
    public Map<String, Object> compare(String id1, String id2) {
        DocumentVersion v1 = repo.findById(id1)
                .orElseThrow(() -> new NoSuchElementException("Versión no encontrada: " + id1));
        DocumentVersion v2 = repo.findById(id2)
                .orElseThrow(() -> new NoSuchElementException("Versión no encontrada: " + id2));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v1", toMetadata(v1));
        result.put("v2", toMetadata(v2));
        result.put("content1", v1.getContent());
        result.put("content2", v2.getContent());
        result.put("identical", v1.getContentHash().equals(v2.getContentHash()));
        return result;
    }

    private Map<String, Object> toMetadata(DocumentVersion v) {
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
        return m;
    }

    private Map<String, Object> toMetadata(DocumentVersionRepository.DocumentVersionSummary v) {
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
        return m;
    }

    private String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
