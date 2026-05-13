package release_mail_generator.service;

import release_mail_generator.model.ReleaseRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class EmailGeneratorService {

    private String clean(String value) {
        return (value == null || value.trim().isEmpty()) ? "" : value.trim();
    }

    /** Escapa caracteres especiales HTML en texto de usuario. */
    private String esc(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /**
     * Convierte una ruta (UNC o URL) en un enlace con estilo hipervínculo.
     * Las rutas UNC (\\servidor\...) se convierten a file://servidor/...
     */
    private String pathLink(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String path = raw.trim();
        String href;
        if (path.startsWith("\\\\")) {
            // UNC → file://servidor/ruta
            href = "file://" + path.substring(2).replace('\\', '/');
        } else {
            href = esc(path);
        }
        return "<a href=\"" + href + "\" style=\"color: #0563C1;\">" + esc(path) + "</a>";
    }

    public String generateEmail(ReleaseRequest r) {
        StringBuilder html = new StringBuilder();

        html.append("<div style=\"font-family: Calibri, Arial, sans-serif; font-size: 11pt;"
                  + " color: #000000; line-height: 1.5;\">");

        // ── Saludo ────────────────────────────────────────────────────────────
        html.append("<p style=\"margin: 0 0 10px 0;\"><b>Buen día Operaciones:</b></p>");

        // ── Párrafo de introducción dinámico ──────────────────────────────────
        boolean hasSps = r.getSpsTickets() != null && !r.getSpsTickets().isBlank();

        List<String> introItems = new ArrayList<>();
        if (r.isHasModules()) introItems.add("<b>Módulos</b>");
        if (r.isHasCitrix())  introItems.add("<b>Citrix</b>");
        if (r.isHasDll())     introItems.add("<b><i>SFyCWSDLL</i></b>");
        if (r.isHasWinterX()) introItems.add("<b><i>WinterX</i></b>");
        if (r.isHasScripts()) introItems.add("<b>Scripts</b>");
        if (hasSps)           introItems.add("<b><i>SP's @DBAs TI</i></b>");

        html.append("<p style=\"margin: 0 0 12px 0;\">")
            .append("Solicitando su apoyo para la distribución de ")
            .append(introItems.isEmpty() ? "los artefactos indicados" : String.join(", ", introItems))
            .append("; a continuación se mencionan las rutas donde se encontrará")
            .append(" lo necesario para el cambio.</p>");

        // ── Lista de artefactos ───────────────────────────────────────────────
        boolean hasAny = r.isHasModules() || r.isHasCitrix() || r.isHasDll()
                      || r.isHasWinterX() || r.isHasScripts() || hasSps;

        if (hasAny) {
            html.append("<ul style=\"margin: 0 0 12px 0; padding-left: 24px;\">");

            if (r.isHasModules()) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b>Módulos</b><br>")
                    .append("Se encuentra en la siguiente ubicación:&nbsp;")
                    .append(pathLink(clean(r.getPathModules())))
                    .append("</li>");
            }
            if (r.isHasCitrix()) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b>Citrix</b><br>")
                    .append("Se encuentra en la siguiente ubicación:&nbsp;")
                    .append(pathLink(clean(r.getPathCitrix())))
                    .append("</li>");
            }
            if (r.isHasDll()) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b><i>SFyCWSDLL</i></b><br>")
                    .append("Se encuentra en la siguiente ubicación:&nbsp;")
                    .append(pathLink(clean(r.getPathDll())))
                    .append("</li>");
            }
            if (r.isHasWinterX()) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b><i>WinterX</i></b><br>")
                    .append("Se encuentra en la siguiente ubicación:&nbsp;")
                    .append(pathLink(clean(r.getPathWinterX())))
                    .append("</li>");
            }

            // SPs (antes de Scripts, como en el ejemplo)
            if (hasSps) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b><i>SP's @DBAs TI</i></b><br>")
                    .append("Se encuentra en la ubicación:&nbsp;")
                    .append("<a href=\"https://github.com/Megacable-IT/storedproc.git\"")
                    .append(" style=\"color: #0563C1;\">")
                    .append("https://github.com/Megacable-IT/storedproc.git</a>")
                    .append("; los scripts correspondientes se encuentran adjuntos en este correo.")
                    .append("<br><br>");

                Arrays.stream(r.getSpsTickets().trim().split("\n\n"))
                    .map(String::trim)
                    .filter(blk -> !blk.isEmpty())
                    .forEach(block -> {
                        for (String line : block.split("\n")) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            String lower = line.toLowerCase();
                            if (lower.startsWith("ticket") || lower.startsWith("n°")) {
                                html.append("<b>").append(esc(line)).append("</b><br>");
                            } else if (lower.startsWith("sp:")) {
                                String spName = line.substring(line.indexOf(':') + 1).trim();
                                html.append("<span style=\"color: #7030A0;\">")
                                    .append(esc(spName)).append("</span><br>");
                            } else {
                                html.append(esc(line)).append("<br>");
                            }
                        }
                        html.append("<br>");
                    });

                html.append("</li>");
            }

            if (r.isHasScripts() && r.getScripts() != null && !r.getScripts().isBlank()) {
                html.append("<li style=\"margin-bottom: 8px;\">")
                    .append("<b>Scripts</b><br>")
                    .append("Se encuentra en la siguiente ubicación:&nbsp;")
                    .append(pathLink(clean(r.getPathScripts())))
                    .append("<br>");

                Arrays.stream(r.getScripts().split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(script -> html.append(esc(script)).append("<br>"));

                html.append("</li>");
            }

            html.append("</ul>");
        }

        // ── Versión / Fecha ───────────────────────────────────────────────────
        boolean hasVersion  = r.getVersion()         != null && !r.getVersion().isBlank();
        boolean hasRollback = r.getRollbackVersion() != null && !r.getRollbackVersion().isBlank();
        boolean hasDate     = r.getPublishDate()     != null && !r.getPublishDate().isBlank();

        if (hasVersion || hasRollback || hasDate) {
            html.append("<p style=\"margin: 0 0 12px 0;\">");
            if (hasVersion)  html.append("<b>Versión:</b>&nbsp;").append(esc(r.getVersion().trim())).append("<br>");
            if (hasRollback) html.append("<b>Rollback:</b>&nbsp;").append(esc(r.getRollbackVersion().trim())).append("<br>");
            if (hasDate)     html.append("<b>Publicar:</b>&nbsp;").append(esc(r.getPublishDate().trim())).append("<br>");
            html.append("</p>");
        }

        // ── Branches ──────────────────────────────────────────────────────────
        boolean hasBranchM = r.getBranchModules() != null && !r.getBranchModules().isBlank();
        boolean hasBranchW = r.getBranchWinter()  != null && !r.getBranchWinter().isBlank();
        if (hasBranchM || hasBranchW) {
            html.append("<p style=\"margin: 0 0 12px 0;\"><b>Branches de Compilación:</b><br>");
            if (hasBranchM) html.append("<b>Módulos:</b>&nbsp;").append(esc(r.getBranchModules().trim())).append("<br>");
            if (hasBranchW) html.append("<b>WinterX:</b>&nbsp;").append(esc(r.getBranchWinter().trim())).append("<br>");
            html.append("</p>");
        }

        // ── Proyectos / RFCs ──────────────────────────────────────────────────
        if (r.getProjects() != null && !r.getProjects().isBlank()) {
            html.append("<p style=\"margin: 0 0 12px 0;\"><b>Proyectos:</b><br>");
            Arrays.stream(r.getProjects().split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(p -> html.append(esc(p)).append("<br>"));
            html.append("</p>");
        }

        // ── Release URL ───────────────────────────────────────────────────────
        if (r.getReleaseUrl() != null && !r.getReleaseUrl().isBlank()) {
            html.append("<p style=\"margin: 0 0 12px 0;\">")
                .append("<b>Release:</b>&nbsp;")
                .append("<a href=\"").append(esc(r.getReleaseUrl().trim()))
                .append("\" style=\"color: #0563C1;\">")
                .append(esc(r.getReleaseUrl().trim()))
                .append("</a></p>");
        }

        // ── Notas ─────────────────────────────────────────────────────────────
        if (r.getNotes() != null && !r.getNotes().isBlank()) {
            html.append("<p style=\"margin: 0 0 12px 0; background-color: #FFF8E1;"
                      + " padding: 8px 12px; border-left: 4px solid #FFC107;\">")
                .append("<b>NOTA IMPORTANTE:</b><br>")
                .append(esc(r.getNotes().trim()).replace("\n", "<br>"))
                .append("</p>");
        }

        // ── Despedida ─────────────────────────────────────────────────────────
        html.append("<p style=\"margin: 0;\">")
            .append("Quedo a sus órdenes para cualquier duda o aclaración.<br>")
            .append("<b>Saludos.</b></p>");

        html.append("</div>");
        return html.toString();
    }
}
