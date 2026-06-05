package release_mail_generator.service;

import release_mail_generator.model.ReleaseRequest;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.model.RdlItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
public class EmailGeneratorService {

    /** Base64 de la imagen MegaCarteraDRP; se carga una sola vez de forma lazy. */
    private volatile String megaCarteraDrpImg = null;

    private String getMegaCarteraDrpImg() {
        if (megaCarteraDrpImg == null) {
            try (var is = getClass().getResourceAsStream("/static/images/MegaCarteraDRP.png")) {
                megaCarteraDrpImg = (is != null)
                    ? Base64.getEncoder().encodeToString(is.readAllBytes())
                    : "";
            } catch (IOException e) {
                megaCarteraDrpImg = "";
            }
        }
        return megaCarteraDrpImg;
    }

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

    // ════════════════════════════════════════════════════════════════════════
    //  GENERADOR DE CORREO — REPORTES RDL
    // ════════════════════════════════════════════════════════════════════════

    public String generateRdlEmail(RdlReleaseRequest r) {
        StringBuilder html = new StringBuilder();

        // ── Filtrar entradas vacías ───────────────────────────────────────────
        List<RdlItem> allRdls = r.getRdls();
        if (allRdls == null) allRdls = new ArrayList<>();
        List<RdlItem> rdlList = new ArrayList<>();
        for (RdlItem item : allRdls) {
            if (item == null) continue;
            if ((item.getRdlReportName() != null && !item.getRdlReportName().isBlank())
             || (item.getRdlUrlMegang()   != null && !item.getRdlUrlMegang().isBlank())
             || (item.getRdlPathMegang()  != null && !item.getRdlPathMegang().isBlank())) {
                rdlList.add(item);
            }
        }
        boolean multi = rdlList.size() > 1;

        html.append("<div style=\"font-family: Calibri, Arial, sans-serif; font-size: 11pt;"
                  + " color: #000000; line-height: 1.5;\">");

        // ── Saludo ────────────────────────────────────────────────────────────
        html.append("<p style=\"margin: 0 0 10px 0;\"><b>Buen día Operaciones/dba's:</b></p>");

        // ── Párrafo de introducción ───────────────────────────────────────────
        boolean anyHasSp = false;
        for (RdlItem chk : rdlList) { if (chk.isHasRdlSp()) { anyHasSp = true; break; } }

        html.append("<p style=\"margin: 0 0 12px 0;\">")
            .append("Solicitando su apoyo para cargar RDL en los servidores ")
            .append("<b>NTRS02</b> y <b>MEGANG-612</b> (configurar el data source)");
        if (anyHasSp) {
            html.append(", <b>@DBAs TI</b> su apoyo con la actualización del SP");
        }
        html.append("<br>a continuación, les comparto las rutas para el cambio:</p>");

        // ── Sección por cada RDL ──────────────────────────────────────────────
        for (int i = 0; i < rdlList.size(); i++) {
            RdlItem item = rdlList.get(i);

            boolean hasUrlMegang  = item.getRdlUrlMegang()  != null && !item.getRdlUrlMegang().isBlank();
            boolean hasUrlNtrs02  = item.getRdlUrlNtrs02()  != null && !item.getRdlUrlNtrs02().isBlank();
            boolean hasPathMegang = item.getRdlPathMegang() != null && !item.getRdlPathMegang().isBlank();
            boolean hasPathNtrs02 = item.getRdlPathNtrs02() != null && !item.getRdlPathNtrs02().isBlank();
            boolean hasName       = item.getRdlReportName() != null && !item.getRdlReportName().isBlank();

            // Cabecera de separación cuando hay múltiples RDL's
            if (multi) {
                String title = hasName ? esc(item.getRdlReportName().trim()) : ("RDL " + (i + 1));
                html.append("<p class=\"rdl-hdr\" style=\"margin: 16px 0 8px 0; padding: 6px 12px;"
                          + " background: #eff6ff; border-left: 4px solid #2563eb;"
                          + " border-radius: 4px;\">")
                    .append("<b>").append(i + 1).append(". ").append(title).append("</b></p>");
            }

            if (hasUrlMegang) {
                html.append("<p style=\"margin: 0 0 4px 0;\"><b>Ruta para el RDL del server MEGANG-612 :</b></p>")
                    .append("<p style=\"margin: 0 0 12px 0;\">")
                    .append("<a href=\"").append(esc(item.getRdlUrlMegang().trim()))
                    .append("\" style=\"color: #0563C1;\">")
                    .append(esc(item.getRdlUrlMegang().trim()))
                    .append("</a></p>");
            }

            if (hasUrlNtrs02) {
                html.append("<p style=\"margin: 0 0 4px 0;\"><b>Rutas para el RDL del server NTRS02:</b></p>")
                    .append("<p style=\"margin: 0 0 12px 0;\">")
                    .append("<a href=\"").append(esc(item.getRdlUrlNtrs02().trim()))
                    .append("\" style=\"color: #0563C1;\">")
                    .append(esc(item.getRdlUrlNtrs02().trim()))
                    .append("</a></p>");
            }

            // Data source: se muestra en cada RDL
            html.append("<p style=\"margin: 0 0 8px 0;\">")
                .append("<b>Data source:</b> especificar el <b>MegaCarteraDRP</b></p>");
            String img = getMegaCarteraDrpImg();
            if (!img.isEmpty()) {
                html.append("<p style=\"margin: 0 0 16px 0;\">")
                    .append("<img src=\"data:image/png;base64,").append(img).append("\"")
                    .append(" alt=\"MegaCarteraDRP\"")
                    .append(" style=\"max-width:320px; border:1px solid #e2e8f0; border-radius:4px;\">")
                    .append("</p>");
            } else {
                html.append("<br>");
            }

            if (hasPathMegang || hasPathNtrs02) {
                html.append("<p style=\"margin: 0 0 10px 0;\"><b>Ruta RDL's&nbsp; Archivos :</b></p>");

                if (hasPathMegang) {
                    html.append("<p style=\"margin: 0 0 10px 0;\">")
                        .append("<b>Ruta rdl MEGANG-612:</b><br>")
                        .append(pathLink(clean(item.getRdlPathMegang())));
                    if (hasName) {
                        html.append("<br><b>NOMBRE :</b>&nbsp;")
                            .append(esc(item.getRdlReportName().trim()));
                    }
                    html.append("</p>");
                }

                if (hasPathNtrs02) {
                    html.append("<p style=\"margin: 0 0 14px 0;\">")
                        .append("<b>Ruta rdl NTRS02:</b><br>")
                        .append(pathLink(clean(item.getRdlPathNtrs02())));
                    if (hasName) {
                        html.append("<br><b>NOMBRE :</b>&nbsp;")
                            .append(esc(item.getRdlReportName().trim()));
                    }
                    html.append("</p>");
                }
            }

            // ── SP @DBAs TI (por RDL, opcional) ──────────────────────────────────────
            if (item.isHasRdlSp()) {
                String[] spNames = (item.getRdlSpName() != null && !item.getRdlSpName().isBlank())
                    ? Arrays.stream(item.getRdlSpName().trim().split("\\r?\\n"))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
                    : new String[0];
                String[] tickets = (item.getRdlSpTicket() != null && !item.getRdlSpTicket().isBlank())
                    ? Arrays.stream(item.getRdlSpTicket().trim().split("\\r?\\n"))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
                    : new String[0];

                int maxLen = Math.max(spNames.length, tickets.length);
                String spHeader = maxLen > 1 ? "Ejecutar SP's @DBAs TI :" : "Ejecutar SP @DBAs TI :";

                html.append("<p style=\"margin: 0 0 4px 0;\"><b>").append(spHeader).append("</b></p>")
                    .append("<p style=\"margin: 0 0 14px 0;\">")
                    .append("<b>Ruta SP Github:</b>&nbsp;")
                    .append("<a href=\"https://github.com/Megacable-IT/storedproc\"")
                    .append(" style=\"color: #0563C1;\">https://github.com/Megacable-IT/storedproc</a><br>");

                if (maxLen <= 1) {
                    // Caso simple: un SP y/o un ticket
                    if (spNames.length > 0) {
                        html.append("<b>Nombre del SP:</b>&nbsp;")
                            .append(esc(spNames[0])).append("<br>");
                    }
                    if (tickets.length > 0) {
                        html.append("<b>Numero de ticket del VoBo:</b>&nbsp;")
                            .append(esc(tickets[0])).append("<br>");
                    }
                } else {
                    // Caso múltiple: cada SP emparejado con su ticket por posición
                    html.append("<b>SP's a ejecutar:</b><br>");
                    for (int j = 0; j < maxLen; j++) {
                        String sp     = j < spNames.length ? spNames[j] : "";
                        String ticket = j < tickets.length ? tickets[j] : "";
                        html.append("&nbsp;&nbsp;").append(j + 1).append(".&nbsp;");
                        if (!sp.isEmpty()) {
                            html.append("<span style=\"color:#7030A0;\">").append(esc(sp)).append("</span>");
                        }
                        if (!sp.isEmpty() && !ticket.isEmpty()) {
                            html.append("&nbsp;&mdash;&nbsp;<b>Ticket VoBo:</b>&nbsp;").append(esc(ticket));
                        } else if (!ticket.isEmpty()) {
                            html.append("<b>Ticket VoBo:</b>&nbsp;").append(esc(ticket));
                        }
                        html.append("<br>");
                    }
                }
                html.append("</p>");
            }

            // ── Fecha Liberación (por RDL) ────────────────────────────────────────────
            if (item.getRdlReleaseDate() != null && !item.getRdlReleaseDate().isBlank()) {
                html.append("<p style=\"margin: 0 0 12px 0;\">")
                    .append("<b>Fecha Liberación:</b>&nbsp;")
                    .append(esc(item.getRdlReleaseDate().trim()))
                    .append("</p>");
            }

            // ── Proyecto / RFC (por RDL) ──────────────────────────────────────────────
            if (item.getRdlProject() != null && !item.getRdlProject().isBlank()) {
                html.append("<p style=\"margin: 0 0 12px 0;\">")
                    .append("<b>Proyecto:</b><br>")
                    .append(esc(item.getRdlProject().trim()))
                    .append("</p>");
            }
        }

        // Si no hay RDL's igual se muestra el data source
        if (rdlList.isEmpty()) {
            html.append("<p style=\"margin: 0 0 8px 0;\">")
                .append("<b>Data source:</b> especificar el <b>MegaCarteraDRP</b></p>");
            String img = getMegaCarteraDrpImg();
            if (!img.isEmpty()) {
                html.append("<p style=\"margin: 0 0 16px 0;\">")
                    .append("<img src=\"data:image/png;base64,").append(img).append("\"")
                    .append(" alt=\"MegaCarteraDRP\"")
                    .append(" style=\"max-width:320px; border:1px solid #e2e8f0; border-radius:4px;\">")
                    .append("</p>");
            } else {
                html.append("<br>");
            }
        }

        html.append("</div>");
        return html.toString();
    }
}
