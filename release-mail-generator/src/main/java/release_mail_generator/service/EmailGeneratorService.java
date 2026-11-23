package release_mail_generator.service;

import release_mail_generator.model.ReleaseRequest;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.model.RdlItem;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
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

import static release_mail_generator.util.EmailHtmlKit.*;

@Service
public class EmailGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(EmailGeneratorService.class);

    /** Base64 de la imagen MegaCarteraDRP; se carga una sola vez al inicio. */
    private String megaCarteraDrpImg;
    private byte[] logoBytes;

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
        try (var is = getClass().getResourceAsStream("/static/images/logo-empresa.png")) {
            logoBytes = (is != null) ? is.readAllBytes() : null;
        } catch (IOException e) {
            log.warn("No se pudo cargar logo-empresa.png: {}", e.getMessage());
            logoBytes = null;
        }
    }

    private String getMegaCarteraDrpImg() {
        return megaCarteraDrpImg != null ? megaCarteraDrpImg : "";
    }

    private static final Set<String> ALLOWED_DISTRIBUTION_MODULES = Set.of(
        "Cartera", "Servicios", "Control", "Hightech", "Equipos", "Ventas", "Wirelink"
    );

    private static final Set<String> ALLOWED_TELEGRAM_MODULES = Set.of(
        "Cartera", "Servicios", "Control", "Hightech", "Equipos", "Ventas", "Wirelink", "Citrix", "DLL", "WinterX", "DLL C#"
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

    /** Versión de la DLL C#: solo aplica si el artefacto está marcado (el campo se oculta al desmarcarlo). */
    private String dllCsharpVersion(ReleaseRequest r) {
        return r.isHasDllCsharp() ? clean(r.getVersionDllCsharp()) : "";
    }

    /**
     * Convierte bloques "Ticket: N" / "SP: nombre" (separados por línea en blanco) en líneas para Telegram,
     * con icono por línea: 🎫 para el ticket y 📂 para cada procedimiento (sin el prefijo "SP:").
     */
    private List<String> telegramSpLines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        boolean pendingBlank = false;
        for (String line : raw.split("\\r?\\n", -1)) {
            String l = clean(line);
            if (l.isEmpty()) {
                pendingBlank = !out.isEmpty();
                continue;
            }
            if (pendingBlank) {
                out.add("");
                pendingBlank = false;
            }
            if (l.regionMatches(true, 0, "ticket", 0, 6)) {
                out.add("\uD83C\uDFAB " + l);
            } else {
                if (l.regionMatches(true, 0, "sp:", 0, 3)) l = clean(l.substring(3));
                if (!l.isEmpty()) out.add("\uD83D\uDCC2 " + l);
            }
        }
        return out;
    }

    public String generateTelegramMessage(ReleaseRequest r) {
        StringBuilder msg = new StringBuilder();
        msg.append("```\n");

        List<String> actionModules = cleanList(r.getTelegramModules()).stream()
            .filter(ALLOWED_TELEGRAM_MODULES::contains)
            .collect(Collectors.toList());

        if (actionModules.isEmpty()) {
            LinkedHashSet<String> fallback = new LinkedHashSet<>();
            fallback.addAll(normalizedDistributionModules(r));
            if (r.isHasCitrix()) fallback.add("Citrix");
            if (r.isHasDll()) fallback.add("DLL");
            if (r.isHasDllCsharp()) fallback.add("DLL C#");
            if (r.isHasWinterX()) fallback.add("WinterX");
            actionModules = new ArrayList<>(fallback);
        }

        // ── Acción / Módulos ──
        if (!actionModules.isEmpty()) {
            msg.append("Acci\u00f3n: Actualizar m\u00f3dulos\n");
            actionModules.forEach(m -> msg.append("\uD83D\uDCE6 ").append(m).append("\n"));
            msg.append("\n");
        }

        // ── Stored Procedures ──
        String sps = clean(r.getTelegramSps());
        if (sps.isEmpty()) sps = clean(r.getSpsTickets());
        List<String> spLines = telegramSpLines(sps);
        if (!spLines.isEmpty()) {
            msg.append("Actualizar SP:\n");
            spLines.forEach(l -> msg.append(l).append("\n"));
            msg.append("\n");
        }

        // ── Scripts ──
        List<String> scripts = cleanList(r.getTelegramScripts());
        if (scripts.isEmpty()) scripts = linesFromText(r.getScripts());
        if (!scripts.isEmpty()) {
            msg.append("Ejecutar Scripts:\n");
            scripts.forEach(s -> msg.append("\uD83D\uDCC4 ").append(s).append("\n"));
            msg.append("\n");
        }

        // ── Nota ──
        String notes = clean(r.getTelegramNotes());
        if (notes.isEmpty()) notes = clean(r.getNotes());
        if (!notes.isEmpty()) {
            msg.append("\u26A0\uFE0F Nota: ").append(notes).append("\n\n");
        }

        // ── Fecha y versiones ──
        String publishDate = formatDateEs(r.getPublishDate());
        if (!publishDate.isEmpty()) {
            msg.append("Publicar: ").append(publishDate).append("\n");
        }

        String versionModule = clean(r.getTelegramVersionModule());
        if (versionModule.isEmpty()) versionModule = clean(r.getVersion());
        String versionDll = clean(r.getTelegramVersionDllCsharp());
        if (versionDll.isEmpty()) versionDll = dllCsharpVersion(r);
        if (!versionModule.isEmpty()) msg.append("\uD83C\uDFF7\uFE0F Versi\u00f3n M\u00f3dulo: ").append(versionModule).append("\n");
        if (!versionDll.isEmpty()) msg.append("\uD83C\uDFF7\uFE0F Versi\u00f3n DLL C#: ").append(versionDll).append("\n");
        if (!publishDate.isEmpty() || !versionModule.isEmpty() || !versionDll.isEmpty()) {
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

        // ── Cambios ──
        if (!changes.isEmpty()) {
            msg.append("Cambios Release:\n");
            changes.forEach(c -> msg.append("\u2705 ").append(c).append("\n"));
            msg.append("\n");
        }

        // ── Rollback y branches ──
        String rollback = clean(r.getTelegramRollbackVersion());
        if (rollback.isEmpty()) rollback = clean(r.getRollbackVersion());
        if (!rollback.isEmpty()) {
            msg.append("\u21A9\uFE0F Versi\u00f3n Rollback: ").append(rollback).append("\n");
        }

        String branchModules = clean(r.getTelegramBranchModules());
        if (branchModules.isEmpty()) branchModules = clean(r.getBranchModules());
        String branchWinter = clean(r.getTelegramBranchWinterX());
        if (branchWinter.isEmpty()) branchWinter = clean(r.getBranchWinter());
        String dllRepo = clean(r.getTelegramBranchDllRepoUrl());
        String dllBranch = clean(r.getTelegramBranchDllName());

        final String branchIcon = "\uD83C\uDF3F ";
        if (!branchModules.isEmpty()) msg.append(branchIcon).append("Branch compilaci\u00f3n M\u00f3dulos: ").append(branchModules).append("\n");
        if (!branchWinter.isEmpty()) msg.append(branchIcon).append("Branch compilaci\u00f3n WinterX: ").append(branchWinter).append("\n");
        if (!dllRepo.isEmpty() || !dllBranch.isEmpty()) {
            msg.append(branchIcon).append("Branch compilaci\u00f3n DLL C#: ");
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
            if (msg.length() > 4) msg.append("\n");
            msg.append("\uD83D\uDD17 Release Publicaci\u00f3n: ").append(releaseUrl).append("\n");
        }

        msg.append("```");
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
        return pathLink(raw, "#0563C1", "");
    }

    private String pathLink(String raw, String color) {
        return pathLink(raw, color, "");
    }

    private String pathLink(String raw, String color, String extraStyle) {
        if (raw == null || raw.isBlank()) return "";
        String path = raw.trim();
        String href;
        if (path.startsWith("\\\\")) {
            // UNC → file://servidor/ruta  (el href también se escapa; '#' se codifica porque
            // en una URL inicia el fragmento y truncaría rutas como "...\DLL C#")
            href = "file://" + esc(path.substring(2).replace('\\', '/').replace("#", "%23"));
        } else {
            href = esc(path);
        }
        return "<a href=\"" + href + "\" style=\"color: " + color + ";" + (extraStyle.isEmpty() ? "" : " " + extraStyle)
            + "\">" + esc(path) + "</a>";
    }

    /** Color de acento y neutros del correo de liberación. */
    private static final String MAIL_BLUE = "#2F6DB5";
    private static final String MAIL_FONT = "font-family: 'Segoe UI', Calibri, Arial, sans-serif; line-height: 1.5;";
    private static final String MAIL_MONO = "font-family: Consolas, 'Courier New', monospace; font-size: 12px; font-weight: normal;";
    private static final String MAIL_REPO = "https://github.com/Megacable-IT/storedproc.git";

    private static final java.util.regex.Pattern TIME_12H = java.util.regex.Pattern.compile(
        "\\b(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?\\s?m\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern TIME_24H = java.util.regex.Pattern.compile(
        "\\b(\\d{1,2}):(\\d{2})\\b(?!\\s*[ap]\\.?\\s?m\\b)(?:\\s*(?:hrs?\\b\\.?|h\\b))?", java.util.regex.Pattern.CASE_INSENSITIVE);

    private record MailComponent(String name, String detail, String path) {}
    private record MailSp(String ticket, List<String> scripts) {}
    private record ReleaseMailData(String subject, List<String> componentNames, List<String[]> rows,
                                   List<MailComponent> components, List<MailSp> sps, boolean hasScripts,
                                   String scriptsPath, List<String> scriptFiles, List<String> projects,
                                   List<String[]> branches, String releaseUrl, String notes) {}

    /** Unifica los horarios del texto al formato "11:00 pm" (acepta "11pm", "11:00 PM", "23:00 hrs", etc.). */
    String normalizeTimes(String text) {
        if (text == null || text.isEmpty()) return "";
        String twelve = TIME_12H.matcher(text).replaceAll(m -> {
            int h = Integer.parseInt(m.group(1));
            if (h < 1 || h > 12) return java.util.regex.Matcher.quoteReplacement(m.group());
            String minutes = m.group(2) != null ? m.group(2) : "00";
            return h + ":" + minutes + " " + m.group(3).toLowerCase() + "m";
        });
        return TIME_24H.matcher(twelve).replaceAll(m -> {
            int h = Integer.parseInt(m.group(1));
            if (h > 23) return java.util.regex.Matcher.quoteReplacement(m.group());
            String suffix = h < 12 ? "am" : "pm";
            int h12 = h % 12 == 0 ? 12 : h % 12;
            return h12 + ":" + m.group(2) + " " + suffix;
        });
    }

    /** Reúne y normaliza los datos del correo de liberación (los comparten la versión HTML y la de texto). */
    private ReleaseMailData releaseMailData(ReleaseRequest r) {
        List<String> modules = normalizedDistributionModules(r);
        boolean hasSps = r.getSpsTickets() != null && !r.getSpsTickets().isBlank();
        String version = clean(r.getVersion());
        String versionDll = dllCsharpVersion(r);
        String rollback = clean(r.getRollbackVersion());
        String publishDate = formatDateEs(clean(r.getPublishDate()));

        StringBuilder subject = new StringBuilder("Solicitud de liberación");
        if (!version.isEmpty()) subject.append(' ').append(version);
        if (!publishDate.isEmpty()) subject.append(" – ").append(publishDate);

        List<String[]> rows = new ArrayList<>();
        if (!publishDate.isEmpty()) rows.add(new String[]{"Fecha de publicación", publishDate});
        if (!version.isEmpty()) rows.add(new String[]{"Versión", version});
        if (!versionDll.isEmpty()) rows.add(new String[]{"Versión DLL C#", versionDll});
        if (!rollback.isEmpty()) rows.add(new String[]{"Rollback", rollback});

        List<MailComponent> components = new ArrayList<>();
        if (r.isHasModules()) components.add(new MailComponent("Módulos", String.join(", ", modules), clean(r.getPathModules())));
        if (r.isHasCitrix()) components.add(new MailComponent("Citrix", "", clean(r.getPathCitrix())));
        if (r.isHasDll()) components.add(new MailComponent("SFyCWSDLL", "", clean(r.getPathDll())));
        if (r.isHasDllCsharp()) components.add(new MailComponent("DLL C#", "", clean(r.getPathDllCsharp())));
        if (r.isHasWinterX()) components.add(new MailComponent("WinterX", "", clean(r.getPathWinterX())));

        List<String> componentNames = new ArrayList<>();
        components.forEach(c -> componentNames.add(c.name() + (c.detail().isEmpty() ? "" : " (" + c.detail() + ")")));
        if (r.isHasScripts()) componentNames.add("Scripts");
        if (hasSps) componentNames.add("SP's @DBAs TI");

        List<MailSp> sps = hasSps ? parseSpBlocks(r.getSpsTickets()) : List.of();

        List<String[]> branches = new ArrayList<>();
        if (!clean(r.getBranchModules()).isEmpty()) branches.add(new String[]{"Módulos", clean(r.getBranchModules())});
        if (!clean(r.getBranchWinter()).isEmpty()) branches.add(new String[]{"WinterX", clean(r.getBranchWinter())});

        return new ReleaseMailData(subject.toString(), componentNames, rows, components, sps, r.isHasScripts(),
            clean(r.getPathScripts()), r.isHasScripts() ? linesFromText(r.getScripts()) : List.of(),
            linesFromText(r.getProjects()), branches, clean(r.getReleaseUrl()), normalizeTimes(clean(r.getNotes())));
    }

    /** Separa el texto "Ticket: N" / "SP: nombre" en bloques; cada ticket nuevo o línea en blanco inicia otro. */
    private List<MailSp> parseSpBlocks(String raw) {
        List<MailSp> blocks = new ArrayList<>();
        String ticket = "";
        List<String> scripts = new ArrayList<>();
        for (String line : raw.split("\\r?\\n", -1)) {
            String l = line.trim();
            String lower = l.toLowerCase();
            boolean isTicket = lower.startsWith("ticket") || lower.startsWith("n°");
            if (l.isEmpty() || (isTicket && (!ticket.isEmpty() || !scripts.isEmpty()))) {
                if (!ticket.isEmpty() || !scripts.isEmpty()) blocks.add(new MailSp(ticket, scripts));
                ticket = "";
                scripts = new ArrayList<>();
                if (l.isEmpty()) continue;
            }
            if (isTicket) {
                int colon = l.indexOf(':');
                ticket = colon >= 0 ? l.substring(colon + 1).trim() : l;
            } else if (lower.startsWith("sp:")) {
                scripts.add(l.substring(l.indexOf(':') + 1).trim());
            } else {
                scripts.add(l);
            }
        }
        if (!ticket.isEmpty() || !scripts.isEmpty()) blocks.add(new MailSp(ticket, scripts));
        return blocks;
    }

    private String contextSentence(ReleaseMailData d) {
        return "Solicitando su apoyo para la distribución de "
            + (d.componentNames().isEmpty() ? "los artefactos indicados" : String.join(", ", d.componentNames()))
            + "; a continuación se mencionan las rutas donde se encontrará lo necesario para el cambio.";
    }

    // ── Piezas HTML del correo de liberación: solo p, b, a, br y span con estilos en línea; sin tablas, fondos ni color de texto ──

    private static String mailP(String style, String html) {
        String size = style.contains("font-size") ? "" : " font-size: 13px;";
        return "<p style=\"" + MAIL_FONT + size + " " + style + "\">" + html + "</p>";
    }

    /** Párrafo de cuerpo con margen inferior explícito. */
    private static String mailBody(String html) {
        return mailP("margin: 0 0 6px 0;", html);
    }

    /** Título de sección: 15 px, negritas y azul, con emoji al inicio y 18 px de margen superior. */
    private String mailSection(String emoji, String title) {
        return mailP("margin: 18px 0 6px 0; font-size: 15px; font-weight: bold; color: " + MAIL_BLUE + ";",
            emoji + "&nbsp;" + esc(title));
    }

    private static String mailLink(String hrefEscaped, String textEscaped) {
        return "<a href=\"" + hrefEscaped + "\" style=\"color: " + MAIL_BLUE + ";\">" + textEscaped + "</a>";
    }

    /** Texto técnico (rutas, scripts, branches) en Consolas 12 px. */
    private static String mailMono(String textEscaped) {
        return "<span style=\"" + MAIL_MONO + "\">" + textEscaped + "</span>";
    }

    private static String mailBullet(String contentHtml) {
        return "&#8226; " + contentHtml;
    }

    /** Enlace en Consolas 12 px (repositorio). */
    private static String mailMonoLink(String urlEscaped) {
        return "<a href=\"" + urlEscaped + "\" style=\"color: " + MAIL_BLUE + "; " + MAIL_MONO + "\">" + urlEscaped + "</a>";
    }

    /** Componente: "• Nombre" en negritas y, debajo, "Se encuentra en la siguiente ubicación:" con la ruta como hipervínculo. */
    private String mailDistributionItem(String name, String path) {
        StringBuilder sb = new StringBuilder(mailBullet("<b>" + esc(name) + "</b>"));
        if (!path.isEmpty()) {
            sb.append("<br>Se encuentra en la siguiente ubicación: ").append(pathLink(path, MAIL_BLUE, MAIL_MONO));
        }
        return sb.toString();
    }

    public String generateEmail(ReleaseRequest r) {
        ReleaseMailData d = releaseMailData(r);
        StringBuilder h = new StringBuilder();

        // ── Asunto, saludo y contexto ─────────────────────────────────────────
        h.append(mailP("margin: 0 0 12px 0;", "<b>Asunto:</b> " + esc(d.subject())));
        h.append(mailP("margin: 0 0 8px 0;", "Buen día Operaciones:"));
        h.append(mailBody(esc(contextSentence(d))));

        // ── Distribución: componentes en orden, nombre en negritas y su ubicación ──
        boolean scriptsListed = d.hasScripts() && (!d.scriptsPath().isEmpty() || !d.scriptFiles().isEmpty());
        if (!d.components().isEmpty() || scriptsListed || !d.sps().isEmpty()) {
            h.append(mailSection("\uD83D\uDCE6", "Distribución"));
            for (MailComponent c : d.components()) {
                h.append(mailBody(mailDistributionItem(c.name(), c.path())));
            }
            if (scriptsListed) {
                StringBuilder scripts = new StringBuilder(mailDistributionItem("Scripts", d.scriptsPath()));
                d.scriptFiles().forEach(s -> scripts.append("<br>").append(mailBullet(mailMono(esc(s)))));
                h.append(mailBody(scripts.toString()));
            }
            if (!d.sps().isEmpty()) {
                StringBuilder sps = new StringBuilder(mailBullet("<b>SP's @DBAs TI</b>"));
                sps.append("<br>Se encuentra en la ubicación: ").append(mailMonoLink(MAIL_REPO))
                   .append("; los scripts correspondientes se encuentran adjuntos en este correo.");
                for (MailSp sp : d.sps()) {
                    if (!sp.ticket().isEmpty()) sps.append("<br>Ticket: ").append(esc(sp.ticket()));
                    sp.scripts().forEach(s -> sps.append("<br>").append(mailBullet(mailMono(esc(s)))));
                }
                h.append(mailBody(sps.toString()));
            }
        }

        // ── Datos de la liberación (incluye el enlace al release) ─────────────
        List<String> data = new ArrayList<>();
        d.rows().forEach(row -> data.add("<b>" + esc(row[0]) + ":</b> " + esc(row[1])));
        if (!d.releaseUrl().isEmpty()) data.add("<b>Release:</b> " + mailLink(esc(d.releaseUrl()), esc(d.releaseUrl())));
        if (!data.isEmpty()) {
            h.append(mailSection("\uD83D\uDCCB", "Datos de la liberación"));
            h.append(mailBody(String.join("<br>", data)));
        }

        // ── Branches de compilación ───────────────────────────────────────────
        if (!d.branches().isEmpty()) {
            h.append(mailSection("\uD83C\uDF3F", "Branches de compilación"));
            h.append(mailBody(d.branches().stream()
                .map(b -> "<b>" + esc(b[0]) + ":</b> " + mailMono(esc(b[1]))).collect(Collectors.joining("<br>"))));
        }

        // ── Proyectos ─────────────────────────────────────────────────────────
        if (!d.projects().isEmpty()) {
            h.append(mailSection("\uD83D\uDCDD", "Proyectos"));
            h.append(mailBody(d.projects().stream().map(p -> mailBullet(esc(p))).collect(Collectors.joining("<br>"))));
        }

        // ── Nota importante: es la "Nota Especial" capturada; se resalta en negritas ──
        if (!d.notes().isEmpty()) {
            h.append(mailSection("\u26A0\uFE0F", "Nota importante"));
            h.append(mailBody("<b>" + esc(d.notes()).replace("\n", "<br>") + "</b>"));
        }

        // ── Cierre ────────────────────────────────────────────────────────────
        h.append(mailP("margin: 18px 0 0 0;", "Quedo atento a cualquier duda.<br>Saludos."));
        return h.toString();
    }

    /** Versión en texto plano del correo (sin formato ni emojis), con la misma información y orden. */
    public String generateEmailPlainText(ReleaseRequest r) {
        ReleaseMailData d = releaseMailData(r);
        StringBuilder t = new StringBuilder();
        t.append("Asunto: ").append(d.subject()).append("\n\n");
        t.append("Buen día Operaciones:\n").append(contextSentence(d)).append("\n");

        boolean scriptsListed = d.hasScripts() && (!d.scriptsPath().isEmpty() || !d.scriptFiles().isEmpty());
        if (!d.components().isEmpty() || scriptsListed || !d.sps().isEmpty()) {
            t.append("\nDISTRIBUCIÓN\n");
            for (MailComponent c : d.components()) {
                t.append("• ").append(c.name()).append("\n");
                if (!c.path().isEmpty()) t.append("Se encuentra en la siguiente ubicación: ").append(c.path()).append("\n");
            }
            if (scriptsListed) {
                t.append("• Scripts\n");
                if (!d.scriptsPath().isEmpty()) t.append("Se encuentra en la siguiente ubicación: ").append(d.scriptsPath()).append("\n");
                d.scriptFiles().forEach(s -> t.append("• ").append(s).append("\n"));
            }
            if (!d.sps().isEmpty()) {
                t.append("• SP's @DBAs TI\nSe encuentra en la ubicación: ").append(MAIL_REPO)
                 .append("; los scripts correspondientes se encuentran adjuntos en este correo.\n");
                for (MailSp sp : d.sps()) {
                    if (!sp.ticket().isEmpty()) t.append("Ticket: ").append(sp.ticket()).append("\n");
                    sp.scripts().forEach(s -> t.append("• ").append(s).append("\n"));
                }
            }
        }

        List<String> data = new ArrayList<>();
        d.rows().forEach(row -> data.add(row[0] + ": " + row[1]));
        if (!d.releaseUrl().isEmpty()) data.add("Release: " + d.releaseUrl());
        if (!data.isEmpty()) t.append("\nDATOS DE LA LIBERACIÓN\n").append(String.join("\n", data)).append("\n");

        if (!d.branches().isEmpty()) {
            t.append("\nBRANCHES DE COMPILACIÓN\n");
            d.branches().forEach(b -> t.append(b[0]).append(": ").append(b[1]).append("\n"));
        }
        if (!d.projects().isEmpty()) {
            t.append("\nPROYECTOS\n");
            d.projects().forEach(p -> t.append("• ").append(p).append("\n"));
        }
        if (!d.notes().isEmpty()) t.append("\nNOTA IMPORTANTE\n").append(d.notes()).append("\n");

        t.append("\nQuedo atento a cualquier duda.\nSaludos.\n");
        return t.toString();
    }
    // ════════════════════════════════════════════════════════════════════════
    //  GENERADOR DE CORREO — REPORTES RDL
    // ════════════════════════════════════════════════════════════════════════

    public String generateRdlEmail(RdlReleaseRequest r) {
        StringBuilder html = new StringBuilder();

        // ── Filtrar entradas vacías ───────────────────────────────────────────
        List<RdlItem> rdlList = new ArrayList<>();
        if (r.getRdls() != null) {
            for (RdlItem item : r.getRdls()) {
                if (item == null) continue;
                if (!clean(item.getRdlReportName()).isEmpty()
                 || !clean(item.getRdlUrlMegang()).isEmpty()
                 || !clean(item.getRdlPathMegang()).isEmpty()) {
                    rdlList.add(item);
                }
            }
        }
        boolean multi = rdlList.size() > 1;
        boolean anyHasSp = rdlList.stream().anyMatch(RdlItem::isHasRdlSp);

        // ── Encabezado y datos clave ──────────────────────────────────────────
        html.append(header("MEGACABLE · QA &amp; LIBERACIONES", ICON_CHART, "Liberación de Reportes RDL", ""));

        List<String[]> facts = new ArrayList<>();
        String releaseDate = clean(r.getRdlReleaseDate());
        if (!releaseDate.isEmpty()) facts.add(new String[]{ICON_CALENDAR + "&nbsp;Fecha de liberación", esc(releaseDate)});
        if (!rdlList.isEmpty()) facts.add(new String[]{ICON_CHART + "&nbsp;Reportes", String.valueOf(rdlList.size())});
        facts.add(new String[]{ICON_DESKTOP + "&nbsp;Servidores", "MEGANG-612 · NTRS02"});
        html.append(facts(facts));

        // ── Saludo e introducción ─────────────────────────────────────────────
        html.append("<p style=\"margin: 0 0 10px 0;\"><b>Buen día Operaciones/dba's:</b></p>");
        html.append("<p style=\"margin: 0 0 14px 0;\">")
            .append("Solicitando su apoyo para cargar RDL en los servidores ")
            .append("<b>NTRS02</b> y <b>MEGANG-612</b> (configurar el data source)");
        if (anyHasSp) {
            html.append(", <b>@DBAs TI</b> su apoyo con la actualización del SP");
        }
        html.append("<br>a continuación, les comparto las rutas para el cambio:</p>");

        // ── Un bloque de tarjetas por cada RDL ────────────────────────────────
        for (int i = 0; i < rdlList.size(); i++) {
            html.append(rdlItemHtml(rdlList.get(i), i, multi));
        }
        if (rdlList.isEmpty()) {
            html.append(dataSourceCard());
        }

        // ── Release URL (Jira) ────────────────────────────────────────────────
        String releaseUrl = clean(r.getRdlReleaseUrl());
        if (!releaseUrl.isEmpty()) {
            String body = button(esc(releaseUrl), ICON_LINK + "&nbsp; Abrir release")
                + "<div style=\"padding-top: 6px; font-size: 9.5pt; word-break: break-all;\">"
                + plainLink(releaseUrl) + "</div>";
            html.append(card(ICON_LINK, "Release", "#2563eb", body));
        }

        return wrap(html.toString());
    }

    private String plainLink(String url) {
        return "<a href=\"" + esc(url) + "\" style=\"color: #0563C1;\">" + esc(url) + "</a>";
    }

    private String dataSourceCard() {
        StringBuilder body = new StringBuilder("Especificar el <b>MegaCarteraDRP</b>");
        String img = getMegaCarteraDrpImg();
        if (!img.isEmpty()) {
            body.append("<div style=\"padding-top: 8px;\"><img src=\"data:image/png;base64,").append(img)
                .append("\" alt=\"MegaCarteraDRP\" style=\"max-width: 320px; border: 1px solid #e2e8f0;\"></div>");
        }
        return card(ICON_DB, "Data source", "#0ea5e9", body.toString());
    }

    /** Tarjetas de un RDL: rutas web, data source, archivos, SP, scripts y proyecto. */
    private String rdlItemHtml(RdlItem item, int index, boolean multi) {
        StringBuilder html = new StringBuilder();
        String name = clean(item.getRdlReportName());
        String urlMegang = clean(item.getRdlUrlMegang());
        String urlNtrs02 = clean(item.getRdlUrlNtrs02());
        String pathMegang = clean(item.getRdlPathMegang());
        String pathNtrs02 = clean(item.getRdlPathNtrs02());

        if (multi) {
            html.append(banner((index + 1) + ". " + (name.isEmpty() ? "RDL " + (index + 1) : esc(name))));
        }

        if (!urlMegang.isEmpty() || !urlNtrs02.isEmpty()) {
            StringBuilder body = new StringBuilder();
            if (!urlMegang.isEmpty()) body.append(labeled("Ruta para el RDL del server MEGANG-612 :", plainLink(urlMegang)));
            if (!urlNtrs02.isEmpty()) body.append(labeled("Rutas para el RDL del server NTRS02:", plainLink(urlNtrs02)));
            String title = (!multi && !name.isEmpty()) ? esc(name) : "Rutas del reporte";
            html.append(card(ICON_LINK, title, "#2563eb", body.toString()));
        }

        html.append(dataSourceCard());

        if (!pathMegang.isEmpty() || !pathNtrs02.isEmpty()) {
            String nameLine = name.isEmpty() ? ""
                : "<div style=\"padding-top: 2px;\"><b>NOMBRE :</b>&nbsp;" + esc(name) + "</div>";
            StringBuilder body = new StringBuilder();
            if (!pathMegang.isEmpty()) body.append(labeled("Ruta rdl MEGANG-612:", pathLink(pathMegang))).append(nameLine);
            if (!pathNtrs02.isEmpty()) body.append(labeled("Ruta rdl NTRS02:", pathLink(pathNtrs02))).append(nameLine);
            html.append(card(ICON_FOLDER, "Ruta RDL's Archivos", "#f59e0b", body.toString()));
        }

        if (item.isHasRdlSp()) {
            List<String> spNames = linesFromText(item.getRdlSpName());
            List<String> tickets = linesFromText(item.getRdlSpTicket());
            int maxLen = Math.max(spNames.size(), tickets.size());
            StringBuilder body = new StringBuilder(
                labeled("Ruta SP Github:", plainLink("https://github.com/Megacable-IT/storedproc")));
            body.append(spacer(6));
            for (int j = 0; j < maxLen; j++) {
                String sp = j < spNames.size() ? spNames.get(j) : "";
                String ticket = j < tickets.size() ? tickets.get(j) : "";
                StringBuilder line = new StringBuilder();
                if (!sp.isEmpty()) line.append(ICON_DB).append("&nbsp; ").append(code(esc(sp))).append(" &nbsp;");
                if (!ticket.isEmpty()) line.append(pill(ICON_TICKET + "&nbsp;Ticket VoBo: " + esc(ticket), "#fef3c7", "#92400e"));
                body.append("<div style=\"padding: 3px 0;\">").append(line).append("</div>");
            }
            String title = maxLen > 1 ? "Ejecutar SP's @DBAs TI" : "Ejecutar SP @DBAs TI";
            html.append(card(ICON_DB, title, "#8b5cf6", body.toString()));
        }

        if (item.isHasRdlScript() && !clean(item.getRdlScriptName()).isEmpty()) {
            List<String> scripts = linesFromText(item.getRdlScriptName());
            StringBuilder body = new StringBuilder();
            scripts.forEach(s -> body.append(iconLine(ICON_FILE, code(esc(s)))));
            if (!clean(item.getRdlScriptPath()).isEmpty()) {
                body.append(labeled("Ubicación:", pathLink(clean(item.getRdlScriptPath()))));
            }
            html.append(card(ICON_SCRIPT, scripts.size() > 1 ? "Ejecutar Scripts" : "Ejecutar Script", "#d97706", body.toString()));
        }

        if (!clean(item.getRdlProject()).isEmpty()) {
            html.append(card(ICON_PIN, "Proyecto", "#ef4444", esc(clean(item.getRdlProject()))));
        }
        return html.toString();
    }

    public String generateRdlTelegramMessage(RdlReleaseRequest r) {
        StringBuilder msg = new StringBuilder();
        msg.append("```\n");

        String action = clean(r.getRdlAction());
        if (action.isEmpty()) action = "Deployment de RDLs y Stored Procedures";
        msg.append("Acci\u00f3n: ").append(action).append("\n");

        List<String> rdlNames = new ArrayList<>();
        List<String> spNames = new ArrayList<>();
        List<String> spTickets = new ArrayList<>();
        List<String> scriptNames = new ArrayList<>();
        List<String> projects = new ArrayList<>();

        List<RdlItem> items = r.getRdls();
        if (items != null) {
            for (RdlItem item : items) {
                if (item == null) continue;

                String reportName = clean(item.getRdlReportName());
                if (!reportName.isEmpty()) rdlNames.add(reportName);

                List<String> itemSpNames = linesFromText(item.getRdlSpName());
                if (!itemSpNames.isEmpty()) spNames.addAll(itemSpNames);

                String ticket = clean(item.getRdlSpTicket());
                if (!ticket.isEmpty()) spTickets.add(ticket);

                if (item.isHasRdlScript() && item.getRdlScriptName() != null && !item.getRdlScriptName().isBlank()) {
                    for (String s : item.getRdlScriptName().trim().split("\\r?\\n")) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) scriptNames.add(trimmed);
                    }
                }

                String project = clean(item.getRdlProject());
                if (!project.isEmpty()) projects.add(project);
            }
        }

        // ── SPs ──
        if (!spNames.isEmpty()) {
            msg.append("Actualizar SP:\n");
            spNames.forEach(sp -> msg.append("\uD83D\uDCC2 [").append(sp).append("]\n"));
            if (!spTickets.isEmpty()) {
                msg.append("\uD83C\uDFAB\uD83C\uDF9F Ticket VoBo SP: ").append(String.join(", ", spTickets)).append("\n");
            }
            msg.append("\n");
        }

        // ── Scripts ──
        if (!scriptNames.isEmpty()) {
            msg.append("Ejecutar Scripts:\n");
            scriptNames.forEach(script -> msg.append("\uD83D\uDCC4 ").append(script).append("\n"));
            msg.append("\n");
        }

        // ── Fecha ──
        String releaseDate = clean(r.getRdlReleaseDate());
        if (!releaseDate.isEmpty()) {
            msg.append("Publicar: ").append(releaseDate).append("\n\n");
        }

        // ── RDLs ──
        if (!rdlNames.isEmpty()) {
            msg.append("RDLs a desplegar:\n");
            rdlNames.forEach(name -> msg.append("\uD83D\uDCC1 ").append(name).append("\n"));
            msg.append("\n");
        }

        // ── Proyectos ──
        if (!projects.isEmpty()) {
            msg.append("Proyectos relacionados:\n");
            projects.forEach(project -> msg.append("\u2705 ").append(project).append("\n"));
            msg.append("\n");
        }

        // ── Release URL ──
        String releaseUrl = clean(r.getRdlReleaseUrl());
        if (!releaseUrl.isEmpty()) {
            msg.append("Release Publicaci\u00f3n: ").append(releaseUrl).append("\n");
        }

        msg.append("```");
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
        if (!dllCsharpVersion(r).isEmpty())
            pdfLine(doc, boldF, bodyF, "Versión DLL C#:", dllCsharpVersion(r));
        pdfLine(doc, boldF, bodyF, "Rollback:", clean(r.getRollbackVersion()));
        pdfLine(doc, boldF, bodyF, "Fecha:", formatDateEs(r.getPublishDate()));

        // Artefactos
        pdfSection(doc, secF, "Artefactos");
        List<String> mods = normalizedDistributionModules(r);
        if (r.isHasModules() && !mods.isEmpty())
            pdfLine(doc, boldF, bodyF, "Módulos:", String.join(", ", mods));
        if (r.isHasCitrix()) pdfLine(doc, boldF, monoF, "Citrix:", clean(r.getPathCitrix()));
        if (r.isHasDll())    pdfLine(doc, boldF, monoF, "SFyCWSDLL:", clean(r.getPathDll()));
        if (r.isHasDllCsharp()) pdfLine(doc, boldF, monoF, "DLL C#:", clean(r.getPathDllCsharp()));
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
        if (!dllCsharpVersion(r).isEmpty())
            md.append("| Versión DLL C# | ").append(dllCsharpVersion(r)).append(" |\n");
        md.append("| Rollback | ").append(clean(r.getRollbackVersion())).append(" |\n");
        md.append("| Fecha | ").append(formatDateEs(r.getPublishDate())).append(" |\n\n");

        md.append("## Artefactos\n\n");
        List<String> mods = normalizedDistributionModules(r);
        if (r.isHasModules() && !mods.isEmpty())
            md.append("- **Módulos:** ").append(String.join(", ", mods)).append("\n");
        if (r.isHasCitrix()) md.append("- **Citrix:** `").append(clean(r.getPathCitrix())).append("`\n");
        if (r.isHasDll())    md.append("- **SFyCWSDLL:** `").append(clean(r.getPathDll())).append("`\n");
        if (r.isHasDllCsharp()) md.append("- **DLL C#:** `").append(clean(r.getPathDllCsharp())).append("`\n");
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

    private static final Color C_TEXT    = new Color(30, 41, 59);
    private static final Color C_MUTED   = new Color(100, 116, 139);
    private static final Color C_ACCENT  = new Color(37, 99, 235);
    private static final Color C_LINE    = new Color(203, 213, 225);

    public byte[] generateRdlPdf(RdlReleaseRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 54, 54, 50, 54);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);

        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                if (w.getPageNumber() == 1) return; // Skip footer on cover
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 7, Font.NORMAL, C_MUTED);
                    cb.setLineWidth(0.3f); cb.setColorStroke(C_LINE);
                    cb.moveTo(d.left(), d.bottom() - 10);
                    cb.lineTo(d.right(), d.bottom() - 10);
                    cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase("Release Notifier QA", f), d.left(), d.bottom() - 22, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("P\u00e1gina " + (w.getPageNumber() - 1), f), d.right(), d.bottom() - 22, 0);
                } catch (Exception ignored) {}
            }
        });
        doc.open();

        // Fonts — sized for comfortable reading without zoom
        Font fDocTitle = new Font(Font.HELVETICA, 16, Font.BOLD, C_TEXT);
        Font fRdlTitle = new Font(Font.HELVETICA, 14, Font.BOLD, C_ACCENT);
        Font fSecTitle = new Font(Font.HELVETICA, 10, Font.BOLD, C_ACCENT);
        Font fLabel    = new Font(Font.HELVETICA, 9.5f, Font.BOLD, C_TEXT);
        Font fBody     = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, C_TEXT);
        Font fMono     = new Font(Font.COURIER, 9, Font.NORMAL, C_TEXT);
        Font fSmall    = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, C_MUTED);

        // ── Filter valid entries ─────────────────────────────────────────────
        String date = clean(r.getRdlReleaseDate());
        String releaseUrl = clean(r.getRdlReleaseUrl());
        List<RdlItem> rdls = r.getRdls() != null ? r.getRdls().stream()
            .filter(it -> it != null && ((it.getRdlReportName() != null && !it.getRdlReportName().isBlank())
                || (it.getRdlUrlMegang() != null && !it.getRdlUrlMegang().isBlank())))
            .collect(Collectors.toList()) : List.of();

        List<String> allProjects = new ArrayList<>();
        for (RdlItem it : rdls) { String p = clean(it.getRdlProject()); if (!p.isEmpty()) allProjects.add(p); }

        // ══════════════════════════════════════════════════════════════════════
        // COVER PAGE
        // ══════════════════════════════════════════════════════════════════════

        // Navy top band
        PdfPTable topBand = new PdfPTable(1);
        topBand.setWidthPercentage(110);
        PdfPCell bandCell = new PdfPCell();
        bandCell.setBackgroundColor(new Color(15, 23, 42));
        bandCell.setFixedHeight(5);
        bandCell.setBorder(Rectangle.NO_BORDER);
        topBand.addCell(bandCell);
        doc.add(topBand);
        doc.add(new Paragraph(" "));

        // Logo + branding
        if (logoBytes != null) {
            try {
                Image logo = Image.getInstance(logoBytes);
                float maxH = 44;
                float scale = maxH / logo.getHeight();
                logo.scaleAbsolute(logo.getWidth() * scale, maxH);
                logo.setAlignment(Element.ALIGN_LEFT);

                PdfPTable logoRow = new PdfPTable(new float[]{0.2f, 0.8f});
                logoRow.setWidthPercentage(100);
                logoRow.setSpacingAfter(20);

                PdfPCell logoCell = new PdfPCell(logo, false);
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                logoCell.setPaddingTop(6);
                logoRow.addCell(logoCell);

                Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_ACCENT);
                Phrase brandPhrase = new Phrase("RELEASE NOTIFIER QA\nEquipo QA & Liberaciones", brandFont);
                PdfPCell brandCell = new PdfPCell(brandPhrase);
                brandCell.setBorder(Rectangle.NO_BORDER);
                brandCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                brandCell.setPaddingLeft(8);
                logoRow.addCell(brandCell);

                doc.add(logoRow);
            } catch (Exception e) {
                Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_ACCENT);
                Paragraph brand = new Paragraph("RELEASE NOTIFIER QA  \u00b7  MEGACABLE", brandFont);
                brand.setSpacingAfter(30);
                doc.add(brand);
            }
        } else {
            Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_ACCENT);
            Paragraph brand = new Paragraph("RELEASE NOTIFIER QA  \u00b7  MEGACABLE", brandFont);
            brand.setSpacingAfter(30);
            doc.add(brand);
        }

        // Document type title
        Font coverTitleFont = new Font(Font.HELVETICA, 28, Font.BOLD, new Color(15, 23, 42));
        Paragraph coverTitle = new Paragraph("Liberaci\u00f3n de RDL", coverTitleFont);
        coverTitle.setSpacingAfter(8);
        doc.add(coverTitle);

        // Accent bar
        LineSeparator accentBar = new LineSeparator(3f, 12f, C_ACCENT, Element.ALIGN_LEFT, 0);
        Paragraph accentP = new Paragraph(new Chunk(accentBar));
        accentP.setSpacingAfter(24);
        doc.add(accentP);

        // Projects — each on its own line
        if (!allProjects.isEmpty()) {
            Font projFont = new Font(Font.HELVETICA, 11, Font.BOLD, C_TEXT);
            for (String proj : allProjects) {
                Paragraph projP = new Paragraph("\u2022  " + proj, projFont);
                projP.setSpacingAfter(4);
                doc.add(projP);
            }
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(30);
            doc.add(spacer);
        }

        // Metadata grid
        Font metaLabel = new Font(Font.HELVETICA, 7, Font.BOLD, C_MUTED);
        Font metaValue = new Font(Font.HELVETICA, 10, Font.NORMAL, C_TEXT);
        PdfPTable meta = new PdfPTable(new float[]{1f, 1f});
        meta.setWidthPercentage(70);
        meta.setHorizontalAlignment(Element.ALIGN_LEFT);

        rdlCoverMeta(meta, "FECHA DE LIBERACI\u00d3N", date.isEmpty() ? "\u2014" : date, metaLabel, metaValue);
        rdlCoverMeta(meta, "SERVIDORES", "NTRS02, MEGANG-612", metaLabel, metaValue);
        rdlCoverMeta(meta, "DATA SOURCE", "MegaCarteraDRP", metaLabel, metaValue);
        rdlCoverMeta(meta, "RDLs", String.valueOf(rdls.size()), metaLabel, metaValue);
        doc.add(meta);

        // MegaCarteraDRP image — below data source grid
        String imgB64 = getMegaCarteraDrpImg();
        if (!imgB64.isEmpty()) {
            try {
                Image dsImg = Image.getInstance(Base64.getDecoder().decode(imgB64));
                dsImg.scaleToFit(130, 38);
                dsImg.setSpacingBefore(8);
                dsImg.setSpacingAfter(12);
                dsImg.setAlignment(Element.ALIGN_LEFT);
                doc.add(dsImg);
            } catch (Exception ignored) {}
        }

        // Release Jira
        if (!releaseUrl.isEmpty()) {
            Paragraph pJira = new Paragraph();
            pJira.setSpacingBefore(8);
            pJira.setLeading(16);
            pJira.add(new Phrase("RELEASE JIRA\n", metaLabel));
            pJira.add(new Phrase(releaseUrl, fMono));
            doc.add(pJira);
        }

        // Cover footer
        Font footNote = new Font(Font.HELVETICA, 7.5f, Font.ITALIC, C_MUTED);
        Paragraph fn = new Paragraph("Documento generado autom\u00e1ticamente  \u00b7  Megacable  \u00b7  Equipo QA & Liberaciones", footNote);
        fn.setSpacingBefore(40);
        doc.add(fn);

        // ══════════════════════════════════════════════════════════════════════
        // RDLs — each on its own page
        // ══════════════════════════════════════════════════════════════════════
        for (int i = 0; i < rdls.size(); i++) {
            doc.newPage();

            RdlItem item = rdls.get(i);
            String rdlName = clean(item.getRdlReportName());
            if (rdlName.isEmpty()) rdlName = "RDL " + (i + 1);

            // ── RDL title ────────────────────────────────────────────────────
            Paragraph rdlH = new Paragraph("RDL " + String.format("%02d", i + 1) + " \u2014 " + rdlName, fRdlTitle);
            rdlH.setSpacingBefore(4);
            rdlH.setSpacingAfter(10);
            doc.add(rdlH);

            // ── UBICACIÓN ────────────────────────────────────────────────────
            boolean hasUrlMegang  = item.getRdlUrlMegang()  != null && !item.getRdlUrlMegang().isBlank();
            boolean hasUrlNtrs02  = item.getRdlUrlNtrs02()  != null && !item.getRdlUrlNtrs02().isBlank();
            boolean hasPathMegang = item.getRdlPathMegang() != null && !item.getRdlPathMegang().isBlank();
            boolean hasPathNtrs02 = item.getRdlPathNtrs02() != null && !item.getRdlPathNtrs02().isBlank();
            String folder = clean(item.getRdlReportFolder());

            if (hasUrlMegang || hasUrlNtrs02 || hasPathMegang || hasPathNtrs02 || !folder.isEmpty()) {
                rdlSec(doc, "UBICACI\u00d3N", fSecTitle);
                if (hasUrlMegang)  rdlLabelBlock(doc, "MEGANG-612 (URL)", clean(item.getRdlUrlMegang()), fLabel, fMono);
                if (hasUrlNtrs02)  rdlLabelBlock(doc, "NTRS02 (URL)",     clean(item.getRdlUrlNtrs02()), fLabel, fMono);
                if (hasPathMegang) rdlLabelBlock(doc, "Ruta MEGANG-612",  clean(item.getRdlPathMegang()), fLabel, fMono);
                if (hasPathNtrs02) rdlLabelBlock(doc, "Ruta NTRS02",      clean(item.getRdlPathNtrs02()), fLabel, fMono);
                if (!folder.isEmpty()) rdlLabelBlock(doc, "Carpeta SSRS", folder, fLabel, fMono);
            }

            // ── STORED PROCEDURES ────────────────────────────────────────────
            if (item.isHasRdlSp()) {
                List<String> spNames = linesFromText(item.getRdlSpName());
                if (!spNames.isEmpty()) {
                    rdlSec(doc, "STORED PROCEDURES", fSecTitle);
                    for (String sp : spNames) rdlBullet(doc, sp, fMono);
                }
            }

            // ── TICKET VoBo ──────────────────────────────────────────────────
            List<String> tickets = linesFromText(item.getRdlSpTicket());
            if (!tickets.isEmpty()) {
                rdlSec(doc, "TICKET DE VoBo", fSecTitle);
                Paragraph pTkt = new Paragraph(String.join(", ", tickets), fBody);
                pTkt.setSpacingAfter(6);
                doc.add(pTkt);
            }

            // ── SCRIPTS ──────────────────────────────────────────────────────
            if (item.isHasRdlScript() && item.getRdlScriptName() != null && !item.getRdlScriptName().isBlank()) {
                rdlSec(doc, "SCRIPTS A EJECUTAR", fSecTitle);
                for (String s : item.getRdlScriptName().trim().split("\\r?\\n")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) rdlBullet(doc, trimmed, fMono);
                }
                if (item.getRdlScriptPath() != null && !item.getRdlScriptPath().isBlank()) {
                    rdlLabelBlock(doc, "Ubicaci\u00f3n", clean(item.getRdlScriptPath()), fLabel, fMono);
                }
            }

            // ── PROYECTO / RFC ───────────────────────────────────────────────
            String project = clean(item.getRdlProject());
            if (!project.isEmpty()) {
                rdlSec(doc, "PROYECTO / RFC", fSecTitle);
                Paragraph pp = new Paragraph(project, fBody);
                pp.setSpacingAfter(6);
                doc.add(pp);
            }
        }

        // ── End note (stays on last RDL's page) ──────────────────────────────
        Paragraph endNote = new Paragraph("Documento generado el "
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            + " \u2014 Release Notifier QA", fSmall);
        endNote.setAlignment(Element.ALIGN_CENTER);
        endNote.setSpacingBefore(30);
        doc.add(endNote);

        doc.close();
        return bos.toByteArray();
    }

    // ── RDL PDF helpers ──────────────────────────────────────────────────────

    /** Section heading in accent color */
    private void rdlSec(Document doc, String label, Font f) throws Exception {
        Paragraph p = new Paragraph(label, f);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
        doc.add(new LineSeparator(0.3f, 100, C_LINE, Element.ALIGN_LEFT, 0));
    }

    /** Label on one line, monospace value indented below — for paths/URLs */
    private void rdlLabelBlock(Document doc, String label, String value, Font lf, Font mf) throws Exception {
        Paragraph pLabel = new Paragraph(label, lf);
        pLabel.setSpacingBefore(4);
        pLabel.setSpacingAfter(1);
        doc.add(pLabel);
        Paragraph pValue = new Paragraph(value, mf);
        pValue.setIndentationLeft(14);
        pValue.setSpacingAfter(5);
        doc.add(pValue);
    }

    /** Bullet point with monospace text */
    private void rdlBullet(Document doc, String text, Font f) throws Exception {
        Paragraph p = new Paragraph("\u2022  " + text, f);
        p.setIndentationLeft(14);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    /** Cover page metadata cell */
    private void rdlCoverMeta(PdfPTable table, String label, String value, Font lf, Font vf) {
        Phrase content = new Phrase();
        content.add(new Chunk(label + "\n", lf));
        content.add(new Chunk(value, vf));
        PdfPCell cell = new PdfPCell(content);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(C_LINE);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(10);
        cell.setPaddingBottom(14);
        cell.setPaddingLeft(0);
        table.addCell(cell);
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
            if (item.isHasRdlScript() && item.getRdlScriptName() != null && !item.getRdlScriptName().isBlank()) {
                String[] scripts = item.getRdlScriptName().trim().split("\\r?\\n");
                for (String script : scripts) {
                    String s = script.trim();
                    if (!s.isEmpty()) md.append("- **Script:** `").append(s).append("`\n");
                }
                if (item.getRdlScriptPath() != null && !item.getRdlScriptPath().isBlank())
                    md.append("- **Ubicación:** `").append(clean(item.getRdlScriptPath())).append("`\n");
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
