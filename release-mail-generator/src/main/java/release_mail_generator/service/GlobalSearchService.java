package release_mail_generator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import release_mail_generator.model.DocumentVersion;
import release_mail_generator.model.RfcTechnicalRecord;
import release_mail_generator.repository.DocumentVersionRepository;
import release_mail_generator.repository.RfcTechnicalRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final RfcTechnicalRepository rfcRepo;
    private final DocumentVersionRepository docRepo;

    /**
     * Searches across all data sources and returns results grouped by module.
     * Limits to 5 results per group for performance.
     */
    public Map<String, Object> search(String query) {
        if (query == null || query.isBlank()) return Map.of("groups", List.of(), "total", 0);

        String q = query.trim();
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        int total = 0;

        // 1. RFC Técnico — search in all fields including JSON content
        List<RfcTechnicalRecord> rfcs = rfcRepo.search(q);
        if (!rfcs.isEmpty()) {
            List<Map<String, String>> items = rfcs.stream().limit(5).map(r -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("title", "RFC " + r.getRfcNumber() + " — " + nvl(r.getChangeName()));
                m.put("subtitle", nvl(r.getTesterName()) + " · " + nvl(r.getStatus()));
                m.put("url", "/rfc/" + r.getId());
                m.put("icon", "📋");
                return m;
            }).collect(Collectors.toList());
            groups.put("RFC Técnico", items);
            total += rfcs.size();
        }

        // 2. Search RFC deep content (DB-level query, no full-table scan)
        List<RfcTechnicalRecord> deepRfcs = rfcRepo.deepSearch(q);
        List<Map<String, String>> deepMatches = new ArrayList<>();
        String ql = q.toLowerCase();
        for (RfcTechnicalRecord r : deepRfcs) {
            if (rfcs.stream().anyMatch(x -> x.getId().equals(r.getId()))) continue;

            String searchable = buildSearchableText(r);
            String context = extractContext(searchable, ql);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", "RFC " + r.getRfcNumber() + " — " + nvl(r.getChangeName()));
            m.put("subtitle", context);
            m.put("url", "/rfc/" + r.getId());
            m.put("icon", "🔍");
            deepMatches.add(m);
            if (deepMatches.size() >= 5) break;
        }
        if (!deepMatches.isEmpty()) {
            groups.put("Contenido RFC (casos de prueba, SPs, notas...)", deepMatches);
            total += deepMatches.size();
        }

        // 3. Document History — search titles/refs across all types
        List<DocumentVersion> docs = docRepo.search(q);
        Map<String, List<DocumentVersion>> byType = docs.stream()
                .collect(Collectors.groupingBy(DocumentVersion::getDocumentType, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> typeLabels = Map.of(
                "RELEASE", "Correos de Liberación",
                "TELEGRAM", "Mensajes Telegram",
                "RDL", "Reportes RDL",
                "RDL_TELEGRAM", "Telegram RDL",
                "UAT", "VoBo UAT"
        );
        Map<String, String> typeIcons = Map.of(
                "RELEASE", "📧", "TELEGRAM", "✈️", "RDL", "📊",
                "RDL_TELEGRAM", "✈️", "UAT", "✅"
        );

        for (Map.Entry<String, List<DocumentVersion>> entry : byType.entrySet()) {
            String type = entry.getKey();
            if ("RFC".equals(type)) continue; // Already handled above
            String label = typeLabels.getOrDefault(type, type);
            List<Map<String, String>> items = entry.getValue().stream().limit(5).map(d -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("title", nvl(d.getTitle()));
                m.put("subtitle", "v" + d.getVersionNumber() + " · " + nvl(d.getAuthor()) + " · " +
                        d.getCreatedAt().toLocalDate().toString());
                m.put("url", "");
                m.put("icon", typeIcons.getOrDefault(type, "📄"));
                m.put("action", "view-history");
                return m;
            }).collect(Collectors.toList());
            if (!items.isEmpty()) {
                groups.put(label, items);
                total += entry.getValue().size();
            }
        }

        // 4. Search document content via DB query (avoids loading all CLOBs)
        if (groups.size() < 3) {
            List<DocumentVersion> contentMatches = docRepo.searchContent(q);
            List<Map<String, String>> contentHits = new ArrayList<>();
            for (DocumentVersion d : contentMatches) {
                // Skip already matched docs
                if (docs.stream().anyMatch(x -> x.getId().equals(d.getId()))) continue;
                String plainText = d.getContent() != null ? d.getContent().replaceAll("<[^>]+>", " ") : "";
                String ctx = extractContext(plainText, ql);
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("title", nvl(d.getTitle()));
                m.put("subtitle", ctx);
                m.put("url", "");
                m.put("icon", "🔎");
                m.put("action", "view-history");
                contentHits.add(m);
                if (contentHits.size() >= 5) break;
            }
            if (!contentHits.isEmpty()) {
                groups.put("Contenido de documentos", contentHits);
                total += contentHits.size();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groups", groups);
        result.put("total", total);
        result.put("query", q);
        return result;
    }

    private String buildSearchableText(RfcTechnicalRecord r) {
        StringBuilder sb = new StringBuilder();
        appendIfNotNull(sb, r.getChangeContext());
        appendIfNotNull(sb, r.getMainObjective());
        appendIfNotNull(sb, r.getSpecificObjectives());
        appendIfNotNull(sb, r.getModules());
        appendIfNotNull(sb, r.getStoredProcedures());
        appendIfNotNull(sb, r.getJobs());
        appendIfNotNull(sb, r.getTables());
        appendIfNotNull(sb, r.getReports());
        appendIfNotNull(sb, r.getOtherComponents());
        appendIfNotNull(sb, r.getObservations());
        appendIfNotNull(sb, r.getRisks());
        appendIfNotNull(sb, r.getRecommendations());
        appendIfNotNull(sb, r.getFinalNotes());
        if (r.getBusinessRules() != null) {
            r.getBusinessRules().forEach(br -> appendIfNotNull(sb, br.getDescription()));
        }
        if (r.getTestCases() != null) {
            r.getTestCases().forEach(tc -> {
                appendIfNotNull(sb, tc.getCaseName());
                if (tc.getSteps() != null) tc.getSteps().forEach(s -> {
                    appendIfNotNull(sb, s.getAction());
                    appendIfNotNull(sb, s.getExpectedResult());
                });
            });
        }
        if (r.getRelatedBugs() != null) {
            r.getRelatedBugs().forEach(b -> {
                appendIfNotNull(sb, b.getIdentifier());
                appendIfNotNull(sb, b.getDescription());
            });
        }
        return sb.toString();
    }

    private void appendIfNotNull(StringBuilder sb, String s) {
        if (s != null && !s.isBlank()) sb.append(' ').append(s);
    }

    /** Extracts ~60 chars of context around the first match. */
    private String extractContext(String text, String query) {
        int idx = text.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) return "";
        int start = Math.max(0, idx - 30);
        int end = Math.min(text.length(), idx + query.length() + 30);
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "..." : "") + snippet + (end < text.length() ? "..." : "");
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
