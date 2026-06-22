package release_mail_generator.service;

import release_mail_generator.model.ReleaseRequest;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.model.RdlItem;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
public class EmailGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(EmailGeneratorService.class);

    /** Base64 de la imagen MegaCarteraDRP; se carga una sola vez al inicio. */
    private String megaCarteraDrpImg;

    @PostConstruct
    private void init() {
        try (var is = getClass().getResourceAsStream("/static/images/MegaCarteraDRP.png")) {
            megaCarteraDrpImg = (is != null)
                ? Base64.getEncoder().encodeToString(is.readAllBytes())
                : "";
        } catch (IOException e) {
            log.warn("No se pudo cargar imagen MegaCarteraDRP: {}", e.getMessage());
            megaCarteraDrpImg = "";
        }
    }

    private String getMegaCarteraDrpImg() {
        return megaCarteraDrpImg != null ? megaCarteraDrpImg : "";
    }

    private static final Set<String> ALLOWED_DISTRIBUTION_MODULES = Set.of(
        "Cartera", "Servicios", "Control", "Hightech", "Equipos", "Ventas"
    );

    private static final Set<String> ALLOWED_TELEGRAM_MODULES = Set.of(
        "Cartera", "Servicios", "Control", "Hightech", "Equipos", "Ventas", "Citrix", "DLL", "WinterX", "DLL C#"
    );

    private List<String> normalizedDistributionModules(ReleaseRequest r) {
        if (r.getDistributionModules() == null) return List.of();
        return r.getDistributionModules().stream()
            .map(this::clean)
            .filter(v -> !v.isEmpty())
            .filter(ALLOWED_DISTRIBUTION_MODULES::contains)
            .distinct()
            .collect(Collectors.toList());
    }

    private String clean(String value) {
        return (value == null || value.trim().isEmpty()) ? "" : value.trim();
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
            .map(this::clean)
            .filter(v -> !v.isEmpty())
            .collect(Collectors.toList());
    }

    private List<String> linesFromText(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\r?\\n"))
            .map(this::clean)
            .filter(v -> !v.isEmpty())
            .collect(Collectors.toList());
    }

    private String formatDateEs(String yyyyMmDd) {
        String v = clean(yyyyMmDd);
        if (v.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = v.split("-");
            return p[2] + "/" + p[1] + "/" + p[0];
        }
        return v;
    }

    public String generateTelegramMessage(ReleaseRequest r) {
        StringBuilder msg = new StringBuilder();

        List<String> actionModules = cleanList(r.getTelegramModules()).stream()
            .filter(ALLOWED_TELEGRAM_MODULES::contains)
            .collect(Collectors.toList());

        if (actionModules.isEmpty()) {
            LinkedHashSet<String> fallback = new LinkedHashSet<>();
            fallback.addAll(normalizedDistributionModules(r));
            if (r.isHasCitrix()) fallback.add("Citrix");
            if (r.isHasDll()) fallback.add("DLL");
            if (r.isHasWinterX()) fallback.add("WinterX");
            actionModules = new ArrayList<>(fallback);
        }

        if (!actionModules.isEmpty()) {
            msg.append("Acción: actualizar módulos ")
               .append(String.join(", ", actionModules))
               .append("\n\n");
        }

        List<String> scripts = cleanList(r.getTelegramScripts());
        if (scripts.isEmpty()) scripts = linesFromText(r.getScripts());
        if (!scripts.isEmpty()) {
            msg.append("Ejecutar scripts:\n");
            scripts.forEach(s -> msg.append(s).append("\n"));
            msg.append("\n");
        }

        String notes = clean(r.getTelegramNotes());
        if (notes.isEmpty()) notes = clean(r.getNotes());
        if (!notes.isEmpty()) {
            msg.append("Nota: ").append(notes).append("\n");
            msg.append("\n");
        }

        String publishDate = formatDateEs(r.getPublishDate());
        if (!publishDate.isEmpty()) {
            msg.append("Publicar: ").append(publishDate).append("\n");
        }

        String versionModule = clean(r.getTelegramVersionModule());
        if (versionModule.isEmpty()) versionModule = clean(r.getVersion());
        String versionDll = clean(r.getTelegramVersionDllCsharp());
        String versionWinterX = clean(r.getTelegramVersionWinterX());
        if (!versionModule.isEmpty()) msg.append("Versión Módulo: ").append(versionModule).append("\n");
        if (!versionDll.isEmpty()) msg.append("Versión DLL C#: ").append(versionDll).append("\n");
        if (!versionWinterX.isEmpty()) msg.append("Versión WinterX: ").append(versionWinterX).append("\n");
        if (!publishDate.isEmpty() || !versionModule.isEmpty() || !versionDll.isEmpty() || !versionWinterX.isEmpty()) {
            msg.append("\n");
        }

        List<String> changeIds = cleanList(r.getTelegramChangeIds());
        List<String> changeDescriptions = cleanList(r.getTelegramChangeDescriptions());
        List<String> collectedChanges = new ArrayList<>();
        int max = Math.max(changeIds.size(), changeDescriptions.size());
        if (max > 0) {
            IntStream.range(0, max).forEach(i -> {
                String id = i < changeIds.size() ? changeIds.get(i) : "";
                String desc = i < changeDescriptions.size() ? changeDescriptions.get(i) : "";
                if (!id.isEmpty() && !desc.isEmpty()) collectedChanges.add(id + " " + desc);
                else if (!id.isEmpty()) collectedChanges.add(id);
                else if (!desc.isEmpty()) collectedChanges.add(desc);
            });
        }
        List<String> changes = collectedChanges.isEmpty() ? linesFromText(r.getProjects()) : collectedChanges;

        if (!changes.isEmpty()) {
            msg.append("Cambios Reléase\n");
            changes.forEach(c -> msg.append(c).append("\n"));
            msg.append("\n");
        }

        String rollback = clean(r.getTelegramRollbackVersion());
        if (rollback.isEmpty()) rollback = clean(r.getRollbackVersion());
        if (!rollback.isEmpty()) {
            msg.append("Versión Rollback: ").append(rollback).append("\n");
        }

        String branchModules = clean(r.getTelegramBranchModules());
        if (branchModules.isEmpty()) branchModules = clean(r.getBranchModules());
        String branchWinter = clean(r.getTelegramBranchWinterX());
        if (branchWinter.isEmpty()) branchWinter = clean(r.getBranchWinter());
        String dllRepo = clean(r.getTelegramBranchDllRepoUrl());
        String dllBranch = clean(r.getTelegramBranchDllName());

        if (!branchModules.isEmpty()) msg.append("Branch compilación Modulos: ").append(branchModules).append("\n");
        if (!branchWinter.isEmpty()) msg.append("Branch compilación winterx: ").append(branchWinter).append("\n");
        if (!dllRepo.isEmpty() || !dllBranch.isEmpty()) {
            msg.append("Branch compilación DLL c#: ");
            if (!dllRepo.isEmpty()) msg.append(dllRepo);
            if (!dllBranch.isEmpty()) {
                if (!dllRepo.isEmpty()) msg.append("  ");
                msg.append("branch ").append(dllBranch);
            }
            msg.append("\n");
        }

        String releaseUrl = clean(r.getTelegramReleaseUrl());
        if (releaseUrl.isEmpty()) releaseUrl = clean(r.getReleaseUrl());
        if (!releaseUrl.isEmpty()) {
            msg.append("Release Publicación: ").append(releaseUrl).append("\n");
        }

        return msg.toString().trim();
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
            // UNC → file://servidor/ruta  (el href también se escapa)
            href = "file://" + esc(path.substring(2).replace('\\', '/'));
        } else {
            href = esc(path);
        }
        return "<a href=\"" + href + "\" style=\"color: #0563C1;\">" + esc(path) + "</a>";
    }

    public String generateEmail(ReleaseRequest r) {
        StringBuilder html = new StringBuilder();
        List<String> selectedDistributionModules = normalizedDistributionModules(r);
        String selectedModulesLabel = selectedDistributionModules.isEmpty()
            ? "Sin módulos seleccionados"
            : String.join(", ", selectedDistributionModules);

        html.append("<div style=\"font-family: Calibri, Arial, sans-serif; font-size: 11pt;"
                  + " color: #000000; line-height: 1.5;\">");

        // ── Saludo ────────────────────────────────────────────────────────────
        html.append("<p style=\"margin: 0 0 10px 0;\"><b>Buen día Operaciones:</b></p>");

        // ── Párrafo de introducción dinámico ──────────────────────────────────
        boolean hasSps = r.getSpsTickets() != null && !r.getSpsTickets().isBlank();

        List<String> introItems = new ArrayList<>();
        if (r.isHasModules()) {
            introItems.add("<b>Módulos (" + esc(selectedModulesLabel) + ")</b>");
        }
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
                    .append("Módulos seleccionados: <b>")
                    .append(esc(selectedModulesLabel))
                    .append("</b><br>")
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
            int branchCount = (hasBranchM ? 1 : 0) + (hasBranchW ? 1 : 0);
            String branchTitle = branchCount == 1 ? "Branch de Compilación" : "Branches de Compilación";
            html.append("<p style=\"margin: 0 0 12px 0;\"><b>")
                .append(branchTitle)
                .append(":</b><br>");
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

        // ── Fecha de Liberación (global) ───────────────────────────────────────────────────
        if (r.getRdlReleaseDate() != null && !r.getRdlReleaseDate().isBlank()) {
            html.append("<p style=\"margin: 16px 0 8px 0;\">")
                .append("<b>Fecha Liberación:</b>&nbsp;")
                .append(esc(r.getRdlReleaseDate().trim()))
                .append("</p>");
        }

        // ── Release URL (Jira) ─────────────────────────────────────────────────────────
        if (r.getRdlReleaseUrl() != null && !r.getRdlReleaseUrl().isBlank()) {
            html.append("<p style=\"margin: 0 0 12px 0;\">")
                .append("<b>Release:</b>&nbsp;")
                .append("<a href=\"")
                .append(esc(r.getRdlReleaseUrl().trim()))
                .append("\" style=\"color: #0563C1;\">")
                .append(esc(r.getRdlReleaseUrl().trim()))
                .append("</a></p>");
        }

        html.append("</div>");
        return html.toString();
    }

    public String generateRdlTelegramMessage(RdlReleaseRequest r) {
        StringBuilder msg = new StringBuilder();

        String action = clean(r.getRdlAction());
        if (action.isEmpty()) action = "Deployment de RDLs y Stored Procedures";
        msg.append("Acción: ").append(action).append("\n\n");

        List<String> rdlNames = new ArrayList<>();
        List<String> spNames = new ArrayList<>();
        List<String> projects = new ArrayList<>();

        List<RdlItem> items = r.getRdls();
        if (items != null) {
            for (RdlItem item : items) {
                if (item == null) continue;

                String reportName = clean(item.getRdlReportName());
                if (!reportName.isEmpty()) rdlNames.add(reportName);

                List<String> itemSpNames = linesFromText(item.getRdlSpName());
                if (!itemSpNames.isEmpty()) spNames.addAll(itemSpNames);

                String project = clean(item.getRdlProject());
                if (!project.isEmpty()) projects.add(project);
            }
        }

        if (!rdlNames.isEmpty()) {
            msg.append("RDLs a desplegar:\n\n");
            rdlNames.forEach(name -> msg.append(name).append("\n"));
            msg.append("\n");
        }

        if (!spNames.isEmpty()) {
            msg.append("Stored Procedures:\n\n");
            spNames.forEach(sp -> msg.append(sp).append("\n"));
            msg.append("\n");
        }

        String releaseDate = clean(r.getRdlReleaseDate());
        if (!releaseDate.isEmpty()) {
            msg.append("Fecha de publicación: ").append(releaseDate).append("\n\n");
        }

        if (!projects.isEmpty()) {
            msg.append("Proyectos relacionados:\n\n");
            projects.forEach(project -> msg.append(project).append("\n"));
        }

        return msg.toString().trim();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT: RELEASE EMAIL → PDF / MARKDOWN
    // ══════════════════════════════════════════════════════════════════════════

    private static final Color PDF_PRIMARY = new Color(37, 99, 235);
    private static final Color PDF_TEXT    = new Color(15, 23, 42);
    private static final Color PDF_MUTED   = new Color(100, 116, 139);
    private static final Color PDF_BORDER  = new Color(226, 232, 240);

    public byte[] generateReleasePdf(ReleaseRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        final String headerLabel = "Correo de Liberación — v" + clean(r.getVersion());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 8, Font.NORMAL, PDF_MUTED);
                    cb.setLineWidth(0.5f); cb.setColorStroke(PDF_BORDER);
                    cb.moveTo(d.left(), d.bottom()-4); cb.lineTo(d.right(), d.bottom()-4); cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Página "+w.getPageNumber(), f), d.right(), d.bottom()-16, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,  new Phrase(headerLabel, f), d.left(), d.bottom()-16, 0);
                } catch (Exception ignored) {}
            }
        });
        doc.open();

        Font titleF = new Font(Font.HELVETICA, 18, Font.BOLD,   PDF_TEXT);
        Font secF   = new Font(Font.HELVETICA, 12, Font.BOLD,   PDF_PRIMARY);
        Font bodyF  = new Font(Font.HELVETICA, 10, Font.NORMAL, PDF_TEXT);
        Font boldF  = new Font(Font.HELVETICA, 10, Font.BOLD,   PDF_TEXT);
        Font monoF  = new Font(Font.COURIER,   9,  Font.NORMAL, PDF_TEXT);

        Paragraph title = new Paragraph("Correo de Liberación — v" + clean(r.getVersion()), titleF);
        title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(12); doc.add(title);
        doc.add(new LineSeparator(1.5f, 100, PDF_PRIMARY, Element.ALIGN_CENTER, -4));

        // Datos generales
        pdfSection(doc, secF, "Datos Generales");
        pdfLine(doc, boldF, bodyF, "Versión:", clean(r.getVersion()));
        pdfLine(doc, boldF, bodyF, "Rollback:", clean(r.getRollbackVersion()));
        pdfLine(doc, boldF, bodyF, "Fecha:", formatDateEs(r.getPublishDate()));

        // Artefactos
        pdfSection(doc, secF, "Artefactos");
        List<String> mods = normalizedDistributionModules(r);
        if (r.isHasModules() && !mods.isEmpty())
            pdfLine(doc, boldF, bodyF, "Módulos:", String.join(", ", mods));
        if (r.isHasCitrix()) pdfLine(doc, boldF, monoF, "Citrix:", clean(r.getPathCitrix()));
        if (r.isHasDll())    pdfLine(doc, boldF, monoF, "SFyCWSDLL:", clean(r.getPathDll()));
        if (r.isHasWinterX()) pdfLine(doc, boldF, monoF, "WinterX:", clean(r.getPathWinterX()));
        if (r.isHasScripts()) {
            pdfLine(doc, boldF, monoF, "Scripts:", clean(r.getPathScripts()));
            linesFromText(r.getScripts()).forEach(s -> {
                try { addPara(doc, "  • " + s, monoF, 2); } catch (Exception e) {}
            });
        }

        // SPS
        if (r.getSpsTickets() != null && !r.getSpsTickets().isBlank()) {
            pdfSection(doc, secF, "SPS — @DBAs TI");
            linesFromText(r.getSpsTickets()).forEach(line -> {
                try { addPara(doc, line, bodyF, 2); } catch (Exception e) {}
            });
        }

        // Branches
        String bm = clean(r.getBranchModules()), bw = clean(r.getBranchWinter());
        if (!bm.isEmpty() || !bw.isEmpty()) {
            pdfSection(doc, secF, "Branches de Compilación");
            if (!bm.isEmpty()) pdfLine(doc, boldF, bodyF, "Módulos:", bm);
            if (!bw.isEmpty()) pdfLine(doc, boldF, bodyF, "WinterX:", bw);
        }

        // Proyectos
        List<String> projects = linesFromText(r.getProjects());
        if (!projects.isEmpty()) {
            pdfSection(doc, secF, "Proyectos / RFCs");
            projects.forEach(p -> { try { addPara(doc, "• " + p, bodyF, 3); } catch (Exception e) {} });
        }

        // Notas
        if (r.getNotes() != null && !r.getNotes().isBlank()) {
            pdfSection(doc, secF, "Nota");
            addPara(doc, r.getNotes().trim(), bodyF, 4);
        }

        doc.close();
        return bos.toByteArray();
    }

    public byte[] generateReleaseMarkdown(ReleaseRequest r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();
        md.append("# Correo de Liberación — v").append(clean(r.getVersion())).append("\n\n");
        md.append("> Generado el ").append(now).append("\n\n");

        md.append("## Datos Generales\n\n");
        md.append("| Campo | Valor |\n|-------|-------|\n");
        md.append("| Versión | ").append(clean(r.getVersion())).append(" |\n");
        md.append("| Rollback | ").append(clean(r.getRollbackVersion())).append(" |\n");
        md.append("| Fecha | ").append(formatDateEs(r.getPublishDate())).append(" |\n\n");

        md.append("## Artefactos\n\n");
        List<String> mods = normalizedDistributionModules(r);
        if (r.isHasModules() && !mods.isEmpty())
            md.append("- **Módulos:** ").append(String.join(", ", mods)).append("\n");
        if (r.isHasCitrix()) md.append("- **Citrix:** `").append(clean(r.getPathCitrix())).append("`\n");
        if (r.isHasDll())    md.append("- **SFyCWSDLL:** `").append(clean(r.getPathDll())).append("`\n");
        if (r.isHasWinterX()) md.append("- **WinterX:** `").append(clean(r.getPathWinterX())).append("`\n");
        if (r.isHasScripts()) {
            md.append("- **Scripts:** `").append(clean(r.getPathScripts())).append("`\n");
            linesFromText(r.getScripts()).forEach(s -> md.append("  - `").append(s).append("`\n"));
        }
        md.append("\n");

        if (r.getSpsTickets() != null && !r.getSpsTickets().isBlank()) {
            md.append("## SPS — @DBAs TI\n\n```\n").append(r.getSpsTickets().trim()).append("\n```\n\n");
        }

        String bm = clean(r.getBranchModules()), bw = clean(r.getBranchWinter());
        if (!bm.isEmpty() || !bw.isEmpty()) {
            md.append("## Branches\n\n");
            if (!bm.isEmpty()) md.append("- **Módulos:** `").append(bm).append("`\n");
            if (!bw.isEmpty()) md.append("- **WinterX:** `").append(bw).append("`\n");
            md.append("\n");
        }

        List<String> projects = linesFromText(r.getProjects());
        if (!projects.isEmpty()) {
            md.append("## Proyectos / RFCs\n\n");
            projects.forEach(p -> md.append("- ").append(p).append("\n"));
            md.append("\n");
        }

        if (r.getNotes() != null && !r.getNotes().isBlank()) {
            md.append("## Nota\n\n").append(r.getNotes().trim()).append("\n\n");
        }

        String url = clean(r.getReleaseUrl());
        if (!url.isEmpty()) md.append("**Release URL:** ").append(url).append("\n\n");

        md.append("---\n_Generado mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT: RDL EMAIL → PDF / MARKDOWN
    // ══════════════════════════════════════════════════════════════════════════

    public byte[] generateRdlPdf(RdlReleaseRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        final String headerLabel = "Reporte RDL — " + clean(r.getRdlReleaseDate());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 8, Font.NORMAL, PDF_MUTED);
                    cb.setLineWidth(0.5f); cb.setColorStroke(PDF_BORDER);
                    cb.moveTo(d.left(), d.bottom()-4); cb.lineTo(d.right(), d.bottom()-4); cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Página "+w.getPageNumber(), f), d.right(), d.bottom()-16, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,  new Phrase(headerLabel, f), d.left(), d.bottom()-16, 0);
                } catch (Exception ignored) {}
            }
        });
        doc.open();

        Font titleF = new Font(Font.HELVETICA, 18, Font.BOLD,   PDF_TEXT);
        Font secF   = new Font(Font.HELVETICA, 12, Font.BOLD,   PDF_PRIMARY);
        Font bodyF  = new Font(Font.HELVETICA, 10, Font.NORMAL, PDF_TEXT);
        Font boldF  = new Font(Font.HELVETICA, 10, Font.BOLD,   PDF_TEXT);
        Font monoF  = new Font(Font.COURIER,   9,  Font.NORMAL, PDF_TEXT);

        Paragraph title = new Paragraph("Reporte RDL — Liberación", titleF);
        title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(12); doc.add(title);
        doc.add(new LineSeparator(1.5f, 100, PDF_PRIMARY, Element.ALIGN_CENTER, -4));

        String date = clean(r.getRdlReleaseDate());
        if (!date.isEmpty()) pdfLine(doc, boldF, bodyF, "Fecha de liberación:", date);
        String url = clean(r.getRdlReleaseUrl());
        if (!url.isEmpty()) pdfLine(doc, boldF, bodyF, "Release Jira:", url);

        List<RdlItem> rdls = r.getRdls() != null ? r.getRdls() : List.of();
        for (int i = 0; i < rdls.size(); i++) {
            RdlItem item = rdls.get(i);
            pdfSection(doc, secF, "RDL #" + (i+1) + ": " + clean(item.getRdlReportName()));
            if (item.getRdlReportFolder() != null && !item.getRdlReportFolder().isBlank())
                pdfLine(doc, boldF, monoF, "Carpeta SSRS:", clean(item.getRdlReportFolder()));
            if (item.getRdlUrlMegang() != null && !item.getRdlUrlMegang().isBlank())
                pdfLine(doc, boldF, monoF, "MEGANG-612:", clean(item.getRdlUrlMegang()));
            if (item.getRdlUrlNtrs02() != null && !item.getRdlUrlNtrs02().isBlank())
                pdfLine(doc, boldF, monoF, "NTRS02:", clean(item.getRdlUrlNtrs02()));
            if (item.getRdlPathMegang() != null && !item.getRdlPathMegang().isBlank())
                pdfLine(doc, boldF, monoF, "Sprint MEGANG:", clean(item.getRdlPathMegang()));
            if (item.getRdlPathNtrs02() != null && !item.getRdlPathNtrs02().isBlank())
                pdfLine(doc, boldF, monoF, "Sprint NTRS02:", clean(item.getRdlPathNtrs02()));
            if (item.isHasRdlSp()) {
                linesFromText(item.getRdlSpName()).forEach(sp -> {
                    try { addPara(doc, "  SP: " + sp, monoF, 2); } catch (Exception e) {}
                });
                linesFromText(item.getRdlSpTicket()).forEach(t -> {
                    try { addPara(doc, "  Ticket: " + t, bodyF, 2); } catch (Exception e) {}
                });
            }
            if (item.getRdlProject() != null && !item.getRdlProject().isBlank())
                pdfLine(doc, boldF, bodyF, "Proyecto:", clean(item.getRdlProject()));
        }

        doc.close();
        return bos.toByteArray();
    }

    public byte[] generateRdlMarkdown(RdlReleaseRequest r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();
        md.append("# Reporte RDL — Liberación\n\n");
        md.append("> Generado el ").append(now).append("\n\n");

        String date = clean(r.getRdlReleaseDate());
        if (!date.isEmpty()) md.append("**Fecha de liberación:** ").append(date).append("\n\n");
        String url = clean(r.getRdlReleaseUrl());
        if (!url.isEmpty()) md.append("**Release Jira:** ").append(url).append("\n\n");

        List<RdlItem> rdls = r.getRdls() != null ? r.getRdls() : List.of();
        for (int i = 0; i < rdls.size(); i++) {
            RdlItem item = rdls.get(i);
            md.append("## RDL #").append(i+1).append(": ").append(clean(item.getRdlReportName())).append("\n\n");
            if (item.getRdlReportFolder() != null && !item.getRdlReportFolder().isBlank())
                md.append("- **Carpeta SSRS:** `").append(clean(item.getRdlReportFolder())).append("`\n");
            if (item.getRdlUrlMegang() != null && !item.getRdlUrlMegang().isBlank())
                md.append("- **MEGANG-612:** `").append(clean(item.getRdlUrlMegang())).append("`\n");
            if (item.getRdlUrlNtrs02() != null && !item.getRdlUrlNtrs02().isBlank())
                md.append("- **NTRS02:** `").append(clean(item.getRdlUrlNtrs02())).append("`\n");
            if (item.getRdlPathMegang() != null && !item.getRdlPathMegang().isBlank())
                md.append("- **Sprint MEGANG:** `").append(clean(item.getRdlPathMegang())).append("`\n");
            if (item.getRdlPathNtrs02() != null && !item.getRdlPathNtrs02().isBlank())
                md.append("- **Sprint NTRS02:** `").append(clean(item.getRdlPathNtrs02())).append("`\n");
            if (item.isHasRdlSp()) {
                linesFromText(item.getRdlSpName()).forEach(sp -> md.append("- **SP:** `").append(sp).append("`\n"));
                linesFromText(item.getRdlSpTicket()).forEach(t -> md.append("- **Ticket:** ").append(t).append("\n"));
            }
            if (item.getRdlProject() != null && !item.getRdlProject().isBlank())
                md.append("- **Proyecto:** ").append(clean(item.getRdlProject())).append("\n");
            md.append("\n");
        }

        md.append("---\n_Generado mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── PDF helpers ──────────────────────────────────────────────────────────

    private void pdfSection(Document doc, Font secF, String title) throws Exception {
        Paragraph p = new Paragraph(title, secF);
        p.setSpacingBefore(14); p.setSpacingAfter(6); doc.add(p);
        doc.add(new LineSeparator(0.5f, 100, PDF_BORDER, Element.ALIGN_CENTER, -2));
    }

    private void pdfLine(Document doc, Font labelF, Font valueF, String label, String value) throws Exception {
        Paragraph p = new Paragraph();
        p.add(new Phrase(label + " ", labelF));
        p.add(new Phrase(value, valueF));
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private void addPara(Document doc, String text, Font f, float spacingAfter) throws Exception {
        Paragraph p = new Paragraph(text, f);
        p.setSpacingAfter(spacingAfter);
        doc.add(p);
    }
}
