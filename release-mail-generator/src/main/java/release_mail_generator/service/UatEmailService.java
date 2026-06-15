package release_mail_generator.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
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
import release_mail_generator.model.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UatEmailService {

    private final Map<String, UatRequest> store = new ConcurrentHashMap<>();

    // ── Color palette ──────────────────────────────────────────────────────
    private static final Color C_PRIMARY = new Color(37, 99, 235);
    private static final Color C_DARK    = new Color(30, 58, 138);
    private static final Color C_BORDER  = new Color(226, 232, 240);
    private static final Color C_BG_ALT  = new Color(241, 245, 249);
    private static final Color C_ROW_ALT = new Color(248, 250, 252);
    private static final Color C_TEXT    = new Color(15, 23, 42);
    private static final Color C_MUTED   = new Color(100, 116, 139);
    private static final Color C_SUCCESS = new Color(5, 150, 105);

    // ── CRUD ───────────────────────────────────────────────────────────────

    public List<UatRequest> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(
                        r -> r.getCreatedAt() == null ? LocalDateTime.MIN : r.getCreatedAt(),
                        Comparator.reverseOrder()))
                .toList();
    }

    public Optional<UatRequest> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public UatRequest save(UatRequest r) {
        if (r.getScopeItems()      == null) r.setScopeItems(new ArrayList<>());
        if (r.getScenarios()       == null) r.setScenarios(new ArrayList<>());
        if (r.getRequirements()    == null) r.setRequirements(new ArrayList<>());
        if (r.getTestCases()       == null) r.setTestCases(new ArrayList<>());
        if (r.getExecutionSteps()  == null) r.setExecutionSteps(new ArrayList<>());
        if (r.getAttachmentNames() == null) r.setAttachmentNames(new ArrayList<>());
        if (r.getStatus() == null || r.getStatus().isBlank()) r.setStatus("Borrador");
        if (r.getId() == null || r.getId().isBlank()) {
            r.setId(UUID.randomUUID().toString());
            r.setCreatedAt(LocalDateTime.now());
        } else {
            findById(r.getId()).ifPresent(e -> r.setCreatedAt(e.getCreatedAt()));
            if (r.getCreatedAt() == null) r.setCreatedAt(LocalDateTime.now());
        }
        r.setUpdatedAt(LocalDateTime.now());
        store.put(r.getId(), r);
        return r;
    }

    public boolean delete(String id) {
        return store.remove(id) != null;
    }

    // ── HTML Email Generation ──────────────────────────────────────────────

    public String generateEmail(UatRequest r) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Calibri,Arial,sans-serif;font-size:11pt;color:#000;line-height:1.6;\">");

        // Greeting
        html.append("<p style=\"margin:0 0 10px 0;\"><b>Hola, buen día team:</b></p>");

        // Introduction
        html.append("<p style=\"margin:0 0 14px 0;\">")
            .append("Solicito de su apoyo con la revisión y VoBo del ")
            .append("<b>").append(esc(s(r.getRfcNumber()))).append(" \u2013 ")
            .append(esc(s(r.getRfcName()))).append("</b>")
            .append(", en el m\u00f3dulo de <b>").append(esc(s(r.getModuleName()))).append(".</b>");
        if (notBlank(r.getScopeDescription())) {
            html.append("<br><br>").append(esc(r.getScopeDescription()));
        }
        // Attachment mention
        List<String> attNames = orEmpty(r.getAttachmentNames()).stream()
                .filter(a -> a != null && !a.isBlank()).toList();
        if (!attNames.isEmpty()) {
            html.append("<br>Adjunto: <b>").append(esc(String.join(", ", attNames))).append("</b>");
        }
        html.append("</p>");

        // ── ALCANCE DE LA VALIDACIÓN ──────────────────────────────────────
        boolean hasScope = !orEmpty(r.getScopeItems()).stream()
                .filter(i -> i != null && !i.isBlank()).toList().isEmpty()
                || notBlank(r.getPromotionNumbers());
        if (hasScope) {
            html.append(sectionHdr("\uD83D\uDDD2\uFE0F", "Alcance de la validaci\u00f3n"));
            if (notBlank(r.getFunctionalScope())) {
                html.append("<p style=\"margin:0 0 8px 0;\">").append(esc(r.getFunctionalScope())).append("</p>");
            }
            List<String> items = orEmpty(r.getScopeItems()).stream()
                    .filter(i -> i != null && !i.isBlank()).toList();
            if (!items.isEmpty()) {
                html.append("<ul style=\"margin:0 0 10px 0;padding-left:24px;\">");
                for (String item : items) {
                    html.append("<li style=\"margin-bottom:4px;\">").append(esc(item)).append("</li>");
                }
                html.append("</ul>");
            }
            if (notBlank(r.getPromotionNumbers())) {
                html.append("<p style=\"margin:0 0 12px 0;\">")
                    .append("Incluye promociones como: <b>").append(esc(r.getPromotionNumbers())).append("</b></p>");
            }
        }

        // ── ESCENARIOS A VALIDAR ──────────────────────────────────────────
        List<String> scenarios = orEmpty(r.getScenarios()).stream()
                .filter(sc -> sc != null && !sc.isBlank()).toList();
        if (!scenarios.isEmpty()) {
            html.append(sectionHdr("\uD83D\uDD35", "Escenarios a validar"));
            html.append("<p style=\"margin:0 0 8px 0;\">Se solicita su apoyo para validar:</p>");
            html.append("<ul style=\"margin:0 0 12px 0;padding-left:24px;\">");
            for (String sc : scenarios) {
                html.append("<li style=\"margin-bottom:4px;\">").append(esc(sc)).append("</li>");
            }
            html.append("</ul>");
        }

        // ── NOTA (requisitos) ────────────────────────────────────────────
        List<UatRequirement> reqs = orEmpty(r.getRequirements()).stream()
                .filter(req -> req != null && anyField(req)).toList();
        if (!reqs.isEmpty()) {
            html.append(sectionHdr("\u26A0\uFE0F", "Nota"));
            html.append("<p style=\"margin:0 0 8px 0;\">Para la validaci\u00f3n, se deber\u00e1n proporcionar:</p>");
            html.append("<ul style=\"margin:0 0 8px 0;padding-left:24px;\">");
            for (UatRequirement req : reqs) {
                html.append("<li style=\"margin-bottom:5px;\">");
                if (notBlank(req.getSucursal()))
                    html.append("<b>Sucursal:</b> ").append(esc(req.getSucursal())).append(" &nbsp;");
                if (notBlank(req.getSuscriptor()))
                    html.append("<b>Suscriptor:</b> ").append(esc(req.getSuscriptor())).append(" &nbsp;");
                if (notBlank(req.getServicio()))
                    html.append("<b>Servicio:</b> ").append(esc(req.getServicio()));
                if (notBlank(req.getAdditionalData()))
                    html.append("<br><span style=\"color:#555;\">").append(esc(req.getAdditionalData())).append("</span>");
                html.append("</li>");
            }
            html.append("</ul>");
            html.append("<p style=\"margin:0 0 12px 0;font-size:10pt;color:#64748b;\">")
                .append("Esto con la finalidad de validar la correcta convivencia en distintos escenarios.</p>");
        }

        // ── EJEMPLOS VALIDADOS ────────────────────────────────────────────
        List<UatTestCaseItem> tcs = orEmpty(r.getTestCases()).stream()
                .filter(tc -> tc != null && notBlank(tc.getSucursal()) || (tc != null && notBlank(tc.getSuscriptor()))).toList();
        if (!tcs.isEmpty()) {
            html.append(sectionHdr("\u2705", "Ejemplos validados"));
            html.append("<table style=\"border-collapse:collapse;width:100%;font-size:10pt;margin-bottom:12px;\">");
            html.append("<thead><tr style=\"background:#2563eb;color:white;\">");
            for (String h : new String[]{"Sucursal", "Suscriptor", "Servicio", "Resultado Esperado", "Resultado Obtenido", "Observaciones"}) {
                html.append("<th style=\"padding:7px 10px;text-align:left;border:1px solid #1d4ed8;font-weight:600;\">")
                    .append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");
            for (int i = 0; i < tcs.size(); i++) {
                UatTestCaseItem tc = tcs.get(i);
                String bg = i % 2 == 0 ? "white" : "#f8fafc";
                html.append("<tr style=\"background:").append(bg).append(";\">");
                for (String v : new String[]{tc.getSucursal(), tc.getSuscriptor(), tc.getServicio(),
                        tc.getExpectedResult(), tc.getObtainedResult(), tc.getObservations()}) {
                    html.append("<td style=\"padding:6px 10px;border:1px solid #e2e8f0;vertical-align:top;\">")
                        .append(esc(s(v))).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }

        // ── PASOS DE EJECUCIÓN ────────────────────────────────────────────
        List<String> steps = orEmpty(r.getExecutionSteps()).stream()
                .filter(st -> st != null && !st.isBlank()).toList();
        if (!steps.isEmpty()) {
            html.append(sectionHdr("\u2699\uFE0F", "Pasos para ejecuci\u00f3n"));
            html.append("<ol style=\"margin:0 0 12px 0;padding-left:24px;\">");
            for (String step : steps) {
                html.append("<li style=\"margin-bottom:6px;\">").append(esc(step)).append("</li>");
            }
            html.append("</ol>");
        }

        // ── CONFIGURACIÓN TÉCNICA ─────────────────────────────────────────
        boolean hasTech = notBlank(r.getServer()) || notBlank(r.getPath())
                || notBlank(r.getAppUrl()) || notBlank(r.getApplication())
                || notBlank(r.getTechnicalEnvironment());
        if (hasTech) {
            html.append(sectionHdr("\uD83D\uDD27", "Configuraci\u00f3n T\u00e9cnica"));
            html.append("<ul style=\"margin:0 0 12px 0;padding-left:24px;\">");
            if (notBlank(r.getServer()))               html.append("<li><b>Servidor:</b> ").append(esc(r.getServer())).append("</li>");
            if (notBlank(r.getTechnicalEnvironment())) html.append("<li><b>Ambiente:</b> ").append(esc(r.getTechnicalEnvironment())).append("</li>");
            if (notBlank(r.getPath()))                 html.append("<li><b>Ruta:</b> ").append(esc(r.getPath())).append("</li>");
            if (notBlank(r.getAppUrl()))               html.append("<li><b>URL:</b> ").append(esc(r.getAppUrl())).append("</li>");
            if (notBlank(r.getApplication()))          html.append("<li><b>Aplicaci\u00f3n:</b> ").append(esc(r.getApplication())).append("</li>");
            html.append("</ul>");
        }

        // Final comments
        if (notBlank(r.getFinalComments())) {
            html.append("<p style=\"margin:12px 0 8px 0;\">").append(esc(r.getFinalComments())).append("</p>");
        }

        // Sign-off
        html.append("<p style=\"margin:14px 0 4px 0;\">Quedamos al pendiente de cualquier duda, comentario o casos de prueba adicionales que requieran validar.</p>");
        html.append("<p style=\"margin:6px 0 0 0;\"><b>Muchas gracias.</b></p>");
        html.append("</div>");
        return html.toString();
    }

    private String sectionHdr(String emoji, String title) {
        return "<p style=\"margin:18px 0 8px 0;padding:8px 14px;background:#eff6ff;"
             + "border-left:4px solid #2563eb;font-size:11.5pt;font-weight:bold;color:#1e3a8a;\">"
             + emoji + "&nbsp;" + title + "</p>";
    }

    // ── PDF Generation ─────────────────────────────────────────────────────

    public byte[] generatePdf(UatRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 72, 62);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);

        final String rfcLabel = "VoBo UAT  \u2014  " + s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 8, Font.NORMAL, C_MUTED);
                    cb.setLineWidth(0.5f);
                    cb.setColorStroke(C_BORDER);
                    cb.moveTo(d.left(), d.bottom() - 4);
                    cb.lineTo(d.right(), d.bottom() - 4);
                    cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase("P\u00e1gina " + w.getPageNumber(), f),
                            d.right(), d.bottom() - 16, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase(rfcLabel, f), d.left(), d.bottom() - 16, 0);
                    cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top() + 8);
                    cb.lineTo(d.right(), d.top() + 8);
                    cb.stroke();
                } catch (Exception ignored) {}
            }
        });

        doc.open();
        Font titleFont   = new Font(Font.HELVETICA, 20, Font.BOLD, C_TEXT);
        Font subFont     = new Font(Font.HELVETICA, 10, Font.ITALIC, C_MUTED);
        Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, C_PRIMARY);
        Font labelFont   = new Font(Font.HELVETICA, 9,  Font.BOLD, new Color(55, 65, 81));
        Font valueFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, C_TEXT);
        Font tHdrFont    = new Font(Font.HELVETICA, 9,  Font.BOLD, Color.WHITE);
        Font tBodyFont   = new Font(Font.HELVETICA, 9,  Font.NORMAL, C_TEXT);
        Font mutedFont   = new Font(Font.HELVETICA, 9,  Font.ITALIC, C_MUTED);

        // Title
        Paragraph title = new Paragraph("VoBo UAT \u2013 Solicitud de Validaci\u00f3n", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        doc.add(title);

        Paragraph sub = new Paragraph(s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName()), subFont);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(2);
        doc.add(sub);
        addHRule(doc, C_PRIMARY, 1.5f);

        // 1. General info
        addSH(doc, "1. Informaci\u00f3n General", sectionFont);
        PdfPTable info = new PdfPTable(new float[]{1f, 2.2f, 1f, 2.2f});
        info.setWidthPercentage(100); info.setSpacingBefore(4); info.setSpacingAfter(12);
        addIP(info, "RFC:", s(r.getRfcNumber()), labelFont, valueFont);
        addIP(info, "Nombre del RFC:", s(r.getRfcName()), labelFont, valueFont);
        addIP(info, "M\u00f3dulo:", s(r.getModuleName()), labelFont, valueFont);
        addIP(info, "Ambiente:", s(r.getEnvironment()), labelFont, valueFont);
        addIP(info, "Responsable QA:", s(r.getQaResponsible()), labelFont, valueFont);
        addIP(info, "Fecha de solicitud:", s(r.getRequestDate()), labelFont, valueFont);
        addIP(info, "Estado:", s(r.getStatus()), labelFont, valueFont);
        addIP(info, "", "", labelFont, valueFont);
        doc.add(info);

        // 2. Scope
        addSH(doc, "2. Alcance de la Validaci\u00f3n", sectionFont);
        if (notBlank(r.getScopeDescription())) addBT(doc, r.getScopeDescription(), valueFont, mutedFont);
        if (notBlank(r.getFunctionalScope()))  addBT(doc, r.getFunctionalScope(), valueFont, mutedFont);
        List<String> scopeItems = orEmpty(r.getScopeItems()).stream()
                .filter(i -> i != null && !i.isBlank()).toList();
        if (!scopeItems.isEmpty()) {
            for (String item : scopeItems) {
                Paragraph p = new Paragraph("• " + item, valueFont);
                p.setIndentationLeft(12); p.setSpacingAfter(3);
                doc.add(p);
            }
        }
        if (notBlank(r.getPromotionNumbers())) {
            addLT(doc, "Incluye promociones:", s(r.getPromotionNumbers()), labelFont, valueFont, mutedFont);
        }

        // 3. Scenarios
        List<String> sc = orEmpty(r.getScenarios()).stream()
                .filter(x -> x != null && !x.isBlank()).toList();
        if (!sc.isEmpty()) {
            addSH(doc, "3. Escenarios a Validar", sectionFont);
            for (String item : sc) {
                Paragraph p = new Paragraph("• " + item, valueFont);
                p.setIndentationLeft(12); p.setSpacingAfter(3);
                doc.add(p);
            }
        }

        // 4. Requirements
        List<UatRequirement> reqs = orEmpty(r.getRequirements()).stream()
                .filter(req -> req != null && anyField(req)).toList();
        if (!reqs.isEmpty()) {
            addSH(doc, "4. Requisitos para la Validaci\u00f3n", sectionFont);
            PdfPTable rt = new PdfPTable(new float[]{1.2f, 1.2f, 1.5f, 2f});
            rt.setWidthPercentage(100); rt.setSpacingBefore(4); rt.setSpacingAfter(12);
            addTH(rt, new String[]{"Sucursal", "Suscriptor", "Servicio", "Datos adicionales"}, tHdrFont);
            for (int i = 0; i < reqs.size(); i++) {
                UatRequirement req = reqs.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                rt.addCell(tCell(s(req.getSucursal()), tBodyFont, bg, Element.ALIGN_LEFT));
                rt.addCell(tCell(s(req.getSuscriptor()), tBodyFont, bg, Element.ALIGN_LEFT));
                rt.addCell(tCell(s(req.getServicio()), tBodyFont, bg, Element.ALIGN_LEFT));
                rt.addCell(tCell(s(req.getAdditionalData()), tBodyFont, bg, Element.ALIGN_LEFT));
            }
            doc.add(rt);
        }

        // 5. Test cases
        List<UatTestCaseItem> tcs = orEmpty(r.getTestCases()).stream()
                .filter(tc -> tc != null && (notBlank(tc.getSucursal()) || notBlank(tc.getSuscriptor()))).toList();
        if (!tcs.isEmpty()) {
            addSH(doc, "5. Casos de Prueba / Ejemplos Validados", sectionFont);
            PdfPTable tt = new PdfPTable(new float[]{1f, 1f, 1.2f, 1.5f, 1.5f, 1.5f});
            tt.setWidthPercentage(100); tt.setSpacingBefore(4); tt.setSpacingAfter(12);
            addTH(tt, new String[]{"Sucursal", "Suscriptor", "Servicio", "Esperado", "Obtenido", "Observaciones"}, tHdrFont);
            for (int i = 0; i < tcs.size(); i++) {
                UatTestCaseItem tc = tcs.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                tt.addCell(tCell(s(tc.getSucursal()), tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tCell(s(tc.getSuscriptor()), tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tCell(s(tc.getServicio()), tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tCell(s(tc.getExpectedResult()), tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tCell(s(tc.getObtainedResult()), tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tCell(s(tc.getObservations()), tBodyFont, bg, Element.ALIGN_LEFT));
            }
            doc.add(tt);
        }

        // 6. Execution steps
        List<String> steps = orEmpty(r.getExecutionSteps()).stream()
                .filter(x -> x != null && !x.isBlank()).toList();
        if (!steps.isEmpty()) {
            addSH(doc, "6. Pasos de Ejecuci\u00f3n", sectionFont);
            for (int i = 0; i < steps.size(); i++) {
                Paragraph p = new Paragraph((i + 1) + ". " + steps.get(i), valueFont);
                p.setIndentationLeft(12); p.setSpacingAfter(4);
                doc.add(p);
            }
        }

        // 7. Technical config
        boolean hasTech = notBlank(r.getServer()) || notBlank(r.getPath())
                || notBlank(r.getAppUrl()) || notBlank(r.getApplication())
                || notBlank(r.getTechnicalEnvironment());
        if (hasTech) {
            addSH(doc, "7. Configuraci\u00f3n T\u00e9cnica", sectionFont);
            PdfPTable ct = new PdfPTable(new float[]{1.2f, 3f});
            ct.setWidthPercentage(100); ct.setSpacingBefore(4); ct.setSpacingAfter(12);
            if (notBlank(r.getServer()))               addCR(ct, "Servidor:", s(r.getServer()), labelFont, tBodyFont);
            if (notBlank(r.getTechnicalEnvironment())) addCR(ct, "Ambiente:", s(r.getTechnicalEnvironment()), labelFont, tBodyFont);
            if (notBlank(r.getPath()))                 addCR(ct, "Ruta:", s(r.getPath()), labelFont, tBodyFont);
            if (notBlank(r.getAppUrl()))               addCR(ct, "URL:", s(r.getAppUrl()), labelFont, tBodyFont);
            if (notBlank(r.getApplication()))          addCR(ct, "Aplicaci\u00f3n:", s(r.getApplication()), labelFont, tBodyFont);
            doc.add(ct);
        }

        // Final comments
        if (notBlank(r.getFinalComments())) {
            addSH(doc, "Comentarios Finales", sectionFont);
            addBT(doc, r.getFinalComments(), valueFont, mutedFont);
        }

        doc.close();
        return bos.toByteArray();
    }

    // ── Markdown Generation ────────────────────────────────────────────────

    public byte[] generateMarkdown(UatRequest r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();

        md.append("# VoBo UAT \u2013 ").append(s(r.getRfcNumber())).append(" \u2013 ").append(s(r.getRfcName())).append("\n\n");
        md.append("> **Solicitud de Validaci\u00f3n UAT** \u2014 Generado el ").append(now).append("\n\n");
        md.append("**RFC:** ").append(s(r.getRfcNumber())).append("  \n");
        md.append("**M\u00f3dulo:** ").append(s(r.getModuleName())).append("  \n");
        md.append("**Responsable QA:** ").append(s(r.getQaResponsible())).append("  \n");
        md.append("**Estado:** ").append(s(r.getStatus())).append("\n\n---\n\n");

        md.append("## 1. Informaci\u00f3n General\n\n");
        md.append("| Campo | Valor |\n|-------|-------|\n");
        mdRow(md, "RFC", s(r.getRfcNumber()));
        mdRow(md, "Nombre del RFC", s(r.getRfcName()));
        mdRow(md, "M\u00f3dulo", s(r.getModuleName()));
        mdRow(md, "Ambiente", s(r.getEnvironment()));
        mdRow(md, "Responsable QA", s(r.getQaResponsible()));
        mdRow(md, "Fecha de solicitud", s(r.getRequestDate()));
        mdRow(md, "Estado", s(r.getStatus()));
        md.append("\n---\n\n");

        md.append("## 2. Alcance de la Validaci\u00f3n\n\n");
        if (notBlank(r.getScopeDescription())) md.append(r.getScopeDescription().trim()).append("\n\n");
        if (notBlank(r.getFunctionalScope()))  md.append(r.getFunctionalScope().trim()).append("\n\n");
        orEmpty(r.getScopeItems()).stream().filter(i -> i != null && !i.isBlank())
                .forEach(i -> md.append("- ").append(i.trim()).append("\n"));
        if (notBlank(r.getPromotionNumbers())) {
            md.append("\n**Incluye promociones:** ").append(r.getPromotionNumbers().trim()).append("\n");
        }
        md.append("\n---\n\n");

        List<String> sc = orEmpty(r.getScenarios()).stream().filter(x -> x != null && !x.isBlank()).toList();
        md.append("## 3. Escenarios a Validar\n\n");
        if (sc.isEmpty()) { md.append("*Sin escenarios registrados.*\n\n"); }
        else { sc.forEach(s -> md.append("- ").append(s.trim()).append("\n")); md.append("\n"); }
        md.append("---\n\n");

        List<UatRequirement> reqs = orEmpty(r.getRequirements()).stream()
                .filter(req -> req != null && anyField(req)).toList();
        md.append("## 4. Requisitos para la Validaci\u00f3n\n\n");
        if (reqs.isEmpty()) { md.append("*Sin requisitos registrados.*\n\n"); }
        else {
            md.append("| Sucursal | Suscriptor | Servicio | Datos adicionales |\n");
            md.append("|----------|------------|----------|-------------------|\n");
            for (UatRequirement req : reqs) {
                md.append("| ").append(mdCell(s(req.getSucursal())))
                  .append(" | ").append(mdCell(s(req.getSuscriptor())))
                  .append(" | ").append(mdCell(s(req.getServicio())))
                  .append(" | ").append(mdCell(s(req.getAdditionalData())))
                  .append(" |\n");
            }
            md.append("\n");
        }
        md.append("---\n\n");

        List<UatTestCaseItem> tcs = orEmpty(r.getTestCases()).stream()
                .filter(tc -> tc != null && (notBlank(tc.getSucursal()) || notBlank(tc.getSuscriptor()))).toList();
        md.append("## 5. Casos de Prueba / Ejemplos Validados\n\n");
        if (tcs.isEmpty()) { md.append("*Sin casos de prueba registrados.*\n\n"); }
        else {
            md.append("| Sucursal | Suscriptor | Servicio | Resultado Esperado | Resultado Obtenido | Observaciones |\n");
            md.append("|----------|------------|----------|--------------------|--------------------|---------------|\n");
            for (UatTestCaseItem tc : tcs) {
                md.append("| ").append(mdCell(s(tc.getSucursal())))
                  .append(" | ").append(mdCell(s(tc.getSuscriptor())))
                  .append(" | ").append(mdCell(s(tc.getServicio())))
                  .append(" | ").append(mdCell(s(tc.getExpectedResult())))
                  .append(" | ").append(mdCell(s(tc.getObtainedResult())))
                  .append(" | ").append(mdCell(s(tc.getObservations())))
                  .append(" |\n");
            }
            md.append("\n");
        }
        md.append("---\n\n");

        List<String> steps = orEmpty(r.getExecutionSteps()).stream()
                .filter(x -> x != null && !x.isBlank()).toList();
        md.append("## 6. Pasos de Ejecuci\u00f3n\n\n");
        if (steps.isEmpty()) { md.append("*Sin pasos registrados.*\n\n"); }
        else { for (int i = 0; i < steps.size(); i++) md.append((i+1) + ". ").append(steps.get(i).trim()).append("\n"); md.append("\n"); }
        md.append("---\n\n");

        boolean hasTech = notBlank(r.getServer()) || notBlank(r.getPath())
                || notBlank(r.getAppUrl()) || notBlank(r.getApplication()) || notBlank(r.getTechnicalEnvironment());
        if (hasTech) {
            md.append("## 7. Configuraci\u00f3n T\u00e9cnica\n\n");
            md.append("| Campo | Valor |\n|-------|-------|\n");
            if (notBlank(r.getServer()))               mdRow(md, "Servidor", s(r.getServer()));
            if (notBlank(r.getTechnicalEnvironment())) mdRow(md, "Ambiente", s(r.getTechnicalEnvironment()));
            if (notBlank(r.getPath()))                 mdRow(md, "Ruta", s(r.getPath()));
            if (notBlank(r.getAppUrl()))               mdRow(md, "URL", s(r.getAppUrl()));
            if (notBlank(r.getApplication()))          mdRow(md, "Aplicaci\u00f3n", s(r.getApplication()));
            md.append("\n---\n\n");
        }

        if (notBlank(r.getFinalComments())) {
            md.append("## Comentarios Finales\n\n").append(r.getFinalComments().trim()).append("\n\n---\n\n");
        }

        md.append("*Documento generado el ").append(now)
          .append(" mediante el sistema **Release Notifier QA**.*\n");

        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String s(String v) { return v != null && !v.isBlank() ? v.trim() : ""; }
    private boolean notBlank(String v) { return v != null && !v.isBlank(); }
    private <T> List<T> orEmpty(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private boolean anyField(UatRequirement r) {
        return notBlank(r.getSucursal()) || notBlank(r.getSuscriptor())
                || notBlank(r.getServicio()) || notBlank(r.getAdditionalData());
    }
    private String esc(String v) {
        if (v == null || v.isBlank()) return "";
        return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String mdCell(String v) {
        if (v == null || v.isBlank()) return "\u2014";
        return v.trim().replace("|","\\|").replace("\r","").replace("\n"," ");
    }
    private void mdRow(StringBuilder sb, String l, String v) {
        sb.append("| ").append(mdCell(l)).append(" | ").append(mdCell(v)).append(" |\n");
    }

    // PDF helpers
    private void addHRule(Document doc, Color color, float lw) throws DocumentException {
        LineSeparator ls = new LineSeparator(lw, 100f, color, Element.ALIGN_CENTER, -2f);
        Paragraph p = new Paragraph(new Chunk(ls));
        p.setSpacingBefore(4); p.setSpacingAfter(12); doc.add(p);
    }
    private void addSH(Document doc, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(16); p.setSpacingAfter(0); doc.add(p);
        LineSeparator ls = new LineSeparator(1.5f, 100f, C_PRIMARY, Element.ALIGN_LEFT, -4f);
        Paragraph line = new Paragraph(new Chunk(ls));
        line.setSpacingBefore(2); line.setSpacingAfter(8); doc.add(line);
    }
    private void addBT(Document doc, String text, Font vf, Font mf) throws DocumentException {
        boolean empty = text == null || text.isBlank();
        Paragraph p = new Paragraph(empty ? "\u2014" : text, empty ? mf : vf);
        p.setSpacingBefore(2); p.setSpacingAfter(8); doc.add(p);
    }
    private void addLT(Document doc, String label, String text, Font lf, Font vf, Font mf) throws DocumentException {
        doc.add(new Paragraph(label, lf));
        boolean empty = text == null || text.isBlank();
        Paragraph vp = new Paragraph(empty ? "\u2014" : text, empty ? mf : vf);
        vp.setIndentationLeft(10); vp.setSpacingAfter(6); doc.add(vp);
    }
    private void addIP(PdfPTable t, String l, String v, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(l, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPaddingBottom(7); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(v.isBlank() ? "\u2014" : v, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPaddingBottom(7); t.addCell(vc);
    }
    private void addCR(PdfPTable t, String l, String v, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(l, lf));
        lc.setBackgroundColor(C_BG_ALT); lc.setBorderColor(C_BORDER); lc.setPadding(7); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(v.isBlank() ? "\u2014" : v, vf));
        vc.setBorderColor(C_BORDER); vc.setPadding(7); t.addCell(vc);
    }
    private void addTH(PdfPTable t, String[] headers, Font font) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(C_PRIMARY); cell.setPadding(8); cell.setBorderColor(C_DARK); t.addCell(cell);
        }
    }
    private PdfPCell tCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text.isBlank() ? "\u2014" : text, font));
        cell.setBackgroundColor(bg); cell.setPadding(7);
        cell.setBorderColor(C_BORDER); cell.setHorizontalAlignment(align);
        return cell;
    }
}
