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
import release_mail_generator.repository.RfcTechnicalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RfcTechnicalService {

    private static final Logger log = LoggerFactory.getLogger(RfcTechnicalService.class);

    @Autowired
    private RfcTechnicalRepository repository;

    // ── Color palette ──────────────────────────────────────────────────────
    private static final Color C_PRIMARY    = new Color(37, 99, 235);
    private static final Color C_DARK       = new Color(30, 58, 138);
    private static final Color C_BORDER     = new Color(226, 232, 240);
    private static final Color C_BG_ALT     = new Color(241, 245, 249);
    private static final Color C_ROW_ALT    = new Color(248, 250, 252);
    private static final Color C_TEXT       = new Color(15, 23, 42);
    private static final Color C_MUTED      = new Color(100, 116, 139);
    private static final Color C_SUCCESS    = new Color(5, 150, 105);
    private static final Color C_DANGER     = new Color(220, 38, 38);
    private static final Color C_WARNING    = new Color(217, 119, 6);

    // ── CRUD ───────────────────────────────────────────────────────────────

    public List<RfcTechnicalRecord> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<RfcTechnicalRecord> findById(String id) {
        return repository.findById(id);
    }

    public RfcTechnicalRecord save(RfcTechnicalRecord record) {
        if (record.getBusinessRules() == null) record.setBusinessRules(new ArrayList<>());
        if (record.getTestCases()     == null) record.setTestCases(new ArrayList<>());
        if (record.getRelatedBugs()   == null) record.setRelatedBugs(new ArrayList<>());
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("Borrador");
        }
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(UUID.randomUUID().toString());
            record.setCreatedAt(LocalDateTime.now());
        } else {
            repository.findById(record.getId())
                    .ifPresent(existing -> record.setCreatedAt(existing.getCreatedAt()));
            if (record.getCreatedAt() == null) record.setCreatedAt(LocalDateTime.now());
        }
        record.setUpdatedAt(LocalDateTime.now());
        RfcTechnicalRecord saved = repository.save(record);
        log.info("RFC guardado: id={} rfc={} status={}", saved.getId(), saved.getRfcNumber(), saved.getStatus());
        return saved;
    }

    public boolean delete(String id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        log.info("RFC eliminado: id={}", id);
        return true;
    }

    // ── PDF Generation ─────────────────────────────────────────────────────

    public byte[] generatePdf(RfcTechnicalRecord r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 72, 62);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);

        final String rfcLabel = "RFC Técnico Final  —  " + s(r.getRfcNumber());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, C_MUTED);
                    // Footer rule
                    cb.setLineWidth(0.5f);
                    cb.setColorStroke(C_BORDER);                    cb.moveTo(d.left(), d.bottom() - 4);
                    cb.lineTo(d.right(), d.bottom() - 4);
                    cb.stroke();
                    // Page number right
                    Phrase pageNum = new Phrase("Página " + w.getPageNumber(), footerFont);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, pageNum,
                            d.right(), d.bottom() - 16, 0);
                    // Label left
                    Phrase label = new Phrase(rfcLabel, footerFont);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, label,
                            d.left(), d.bottom() - 16, 0);
                    // Header rule
                    cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top() + 8);
                    cb.lineTo(d.right(), d.top() + 8);
                    cb.stroke();
                } catch (Exception ignored) {}
            }
        });

        doc.open();

        Font titleFont   = new Font(Font.HELVETICA, 22, Font.BOLD, C_TEXT);
        Font purposeFont = new Font(Font.HELVETICA, 9,  Font.ITALIC, C_MUTED);
        Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, C_PRIMARY);
        Font labelFont   = new Font(Font.HELVETICA, 9,  Font.BOLD, new Color(55, 65, 81));
        Font valueFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, C_TEXT);
        Font tHeaderFont = new Font(Font.HELVETICA, 9,  Font.BOLD, Color.WHITE);
        Font tBodyFont   = new Font(Font.HELVETICA, 9,  Font.NORMAL, C_TEXT);
        Font mutedFont   = new Font(Font.HELVETICA, 9,  Font.ITALIC, C_MUTED);

        // ── Title block ───────────────────────────────────────────────────
        Paragraph titleP = new Paragraph("RFC Técnico Final", titleFont);
        titleP.setAlignment(Element.ALIGN_CENTER);
        titleP.setSpacingAfter(4);
        doc.add(titleP);

        Paragraph purposeP = new Paragraph(
                "Propósito del documento: Dejar evidencia clara, trazable y defendible de que un cambio fue " +
                "validado y cumple o no cumple con lo solicitado.", purposeFont);
        purposeP.setAlignment(Element.ALIGN_CENTER);
        purposeP.setSpacingAfter(2);
        doc.add(purposeP);
        addHRule(doc, C_PRIMARY, 1.5f);

        // ── 1. Información General ────────────────────────────────────────
        addSectionHeading(doc, "1. Información General", sectionFont);
        PdfPTable info = new PdfPTable(new float[]{1f, 2.4f, 1f, 2.4f});
        info.setWidthPercentage(100);
        info.setSpacingBefore(4);
        info.setSpacingAfter(12);
        addInfoPair(info, "RFC:", s(r.getRfcNumber()), labelFont, valueFont);
        addInfoPair(info, "Nombre del cambio:", s(r.getChangeName()), labelFont, valueFont);
        addInfoPair(info, "Fecha de validación:", s(r.getValidationDate()), labelFont, valueFont);
        addInfoPair(info, "Tester responsable:", s(r.getTesterName()), labelFont, valueFont);
        addInfoPair(info, "Solicitante / Área:", s(r.getRequester()), labelFont, valueFont);
        addInfoPair(info, "Ambiente:", s(r.getEnvironment()), labelFont, valueFont);
        addInfoPair(info, "Estado:", s(r.getStatus()), labelFont, valueFont);
        addInfoPair(info, "", "", labelFont, valueFont);
        doc.add(info);

        // ── 2. Contexto del Cambio ────────────────────────────────────────
        addSectionHeading(doc, "2. Contexto del Cambio", sectionFont);
        addBodyText(doc, s(r.getChangeContext()), valueFont, mutedFont);

        // ── 3. Objetivo de la Validación ──────────────────────────────────
        addSectionHeading(doc, "3. Objetivo de la Validación", sectionFont);
        addLabelAndText(doc, "Objetivo principal:", s(r.getMainObjective()), labelFont, valueFont, mutedFont);
        if (notBlank(r.getSpecificObjectives())) {
            addLabelAndText(doc, "Objetivos específicos:", s(r.getSpecificObjectives()), labelFont, valueFont, mutedFont);
        }

        // ── 4. Componentes Técnicos Impactados ────────────────────────────
        addSectionHeading(doc, "4. Componentes Técnicos Impactados", sectionFont);
        PdfPTable comp = new PdfPTable(new float[]{1.4f, 3f});
        comp.setWidthPercentage(100);
        comp.setSpacingBefore(4);
        comp.setSpacingAfter(12);
        addCompRow(comp, "Módulos:", s(r.getModules()), labelFont, tBodyFont);
        addCompRow(comp, "Stored Procedures:", s(r.getStoredProcedures()), labelFont, tBodyFont);
        addCompRow(comp, "Jobs:", s(r.getJobs()), labelFont, tBodyFont);
        addCompRow(comp, "Tablas:", s(r.getTables()), labelFont, tBodyFont);
        addCompRow(comp, "Reportes:", s(r.getReports()), labelFont, tBodyFont);
        addCompRow(comp, "Otros:", s(r.getOtherComponents()), labelFont, tBodyFont);
        doc.add(comp);

        // ── 5. Reglas de Negocio Validadas ───────────────────────────────
        addSectionHeading(doc, "5. Reglas de Negocio Validadas", sectionFont);
        List<BusinessRule> rules = orEmpty(r.getBusinessRules());
        if (rules.isEmpty()) {
            doc.add(emptyNote(mutedFont));
        } else {
            PdfPTable rt = new PdfPTable(new float[]{4f, 1.2f});
            rt.setWidthPercentage(100);
            rt.setSpacingBefore(4);
            rt.setSpacingAfter(12);
            addTableHeader(rt, new String[]{"Descripción de la Regla", "Estado"}, tHeaderFont);
            for (int i = 0; i < rules.size(); i++) {
                BusinessRule rule = rules.get(i);
                Color rowBg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                rt.addCell(tableCell(s(rule.getDescription()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                Color sc = ruleStatusColor(rule.getValidationStatus());
                rt.addCell(tableCell(s(rule.getValidationStatus()),
                        new Font(Font.HELVETICA, 9, Font.BOLD, sc), rowBg, Element.ALIGN_CENTER));
            }
            doc.add(rt);
        }

        // ── 6. Casos de Prueba Ejecutados ────────────────────────────────
        addSectionHeading(doc, "6. Casos de Prueba Ejecutados", sectionFont);
        List<TestCase> tcs = orEmpty(r.getTestCases());
        if (tcs.isEmpty()) {
            doc.add(emptyNote(mutedFont));
        } else {
            PdfPTable tt = new PdfPTable(new float[]{0.7f, 2f, 2f, 2f, 1f});
            tt.setWidthPercentage(100);
            tt.setSpacingBefore(4);
            tt.setSpacingAfter(12);
            addTableHeader(tt, new String[]{"ID", "Descripción", "Resultado Esperado", "Resultado Obtenido", "Estado"}, tHeaderFont);
            for (int i = 0; i < tcs.size(); i++) {
                TestCase tc = tcs.get(i);
                Color rowBg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                tt.addCell(tableCell(s(tc.getCaseId()), tBodyFont, rowBg, Element.ALIGN_CENTER));
                tt.addCell(tableCell(s(tc.getDescription()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                tt.addCell(tableCell(s(tc.getExpectedResult()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                tt.addCell(tableCell(s(tc.getObtainedResult()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                boolean passed = "Exitoso".equalsIgnoreCase(tc.getResult());
                Color resultColor = passed ? C_SUCCESS : C_DANGER;
                tt.addCell(tableCell(s(tc.getResult()),
                        new Font(Font.HELVETICA, 9, Font.BOLD, resultColor), rowBg, Element.ALIGN_CENTER));
            }
            doc.add(tt);
        }

        // ── 7. Bugs Relacionados ──────────────────────────────────────────
        addSectionHeading(doc, "7. Bugs Relacionados", sectionFont);
        List<RelatedBug> bugs = orEmpty(r.getRelatedBugs());
        if (bugs.isEmpty()) {
            doc.add(emptyNote(mutedFont));
        } else {
            PdfPTable bt = new PdfPTable(new float[]{1.2f, 3.5f, 1.2f});
            bt.setWidthPercentage(100);
            bt.setSpacingBefore(4);
            bt.setSpacingAfter(12);
            addTableHeader(bt, new String[]{"Identificador", "Descripción", "Estatus"}, tHeaderFont);
            for (int i = 0; i < bugs.size(); i++) {
                RelatedBug bug = bugs.get(i);
                Color rowBg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                bt.addCell(tableCell(s(bug.getIdentifier()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                bt.addCell(tableCell(s(bug.getDescription()), tBodyFont, rowBg, Element.ALIGN_LEFT));
                bt.addCell(tableCell(s(bug.getBugStatus()), tBodyFont, rowBg, Element.ALIGN_LEFT));
            }
            doc.add(bt);
        }

        // ── 8. Conclusiones ───────────────────────────────────────────────
        addSectionHeading(doc, "8. Conclusiones", sectionFont);
        if (notBlank(r.getFinalResult())) {
            boolean cumple = "Cumple".equalsIgnoreCase(r.getFinalResult());
            Color rc = cumple ? C_SUCCESS : C_DANGER;
            Paragraph rp = new Paragraph("Resultado final del RFC: " + r.getFinalResult(),
                    new Font(Font.HELVETICA, 13, Font.BOLD, rc));
            rp.setSpacingBefore(4);
            rp.setSpacingAfter(8);
            doc.add(rp);
        }
        addLabelAndText(doc, "Observaciones relevantes:", s(r.getObservations()), labelFont, valueFont, mutedFont);
        if (notBlank(r.getRisks())) {
            addLabelAndText(doc, "Riesgos identificados:", s(r.getRisks()), labelFont, valueFont, mutedFont);
        }
        if (notBlank(r.getRecommendations())) {
            addLabelAndText(doc, "Recomendaciones:", s(r.getRecommendations()), labelFont, valueFont, mutedFont);
        }

        // ── 9. Notas Finales ──────────────────────────────────────────────
        addSectionHeading(doc, "9. Notas Finales", sectionFont);
        addBodyText(doc, s(r.getFinalNotes()), valueFont, mutedFont);

        doc.close();
        return bos.toByteArray();
    }

    // ── Markdown Generation ───────────────────────────────────────────────

    public byte[] generateMarkdown(RfcTechnicalRecord r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();

        // ── Front matter / header ────────────────────────────────────────
        md.append("# RFC Técnico Final\n\n");
        md.append("> **Propósito del documento:** Dejar evidencia clara, trazable y defendible de que un cambio fue "
                + "validado y **cumple o no cumple** con lo solicitado.\n\n");
        md.append("**Generado el:** ").append(now).append("  \n");
        md.append("**RFC:** ").append(s(r.getRfcNumber())).append("  \n");
        md.append("**Nombre del cambio:** ").append(s(r.getChangeName())).append("  \n");
        md.append("**Tester responsable:** ").append(s(r.getTesterName())).append("  \n");
        md.append("**Estado:** ").append(s(r.getStatus())).append("\n\n");
        md.append("---\n\n");

        // ── 1. Información General ───────────────────────────────────────
        md.append("## 1. Información General\n\n");
        md.append("| Campo | Valor |\n");
        md.append("|-------|-------|\n");
        mdRow(md, "RFC",                 s(r.getRfcNumber()));
        mdRow(md, "Nombre del cambio",   s(r.getChangeName()));
        mdRow(md, "Fecha de validación", s(r.getValidationDate()));
        mdRow(md, "Tester responsable",  s(r.getTesterName()));
        mdRow(md, "Solicitante / Área",  s(r.getRequester()));
        mdRow(md, "Ambiente",            s(r.getEnvironment()));
        mdRow(md, "Estado",              s(r.getStatus()));
        md.append("\n---\n\n");

        // ── 2. Contexto del Cambio ───────────────────────────────────────
        md.append("## 2. Contexto del Cambio\n\n");
        md.append(mdBlock(r.getChangeContext())).append("\n\n---\n\n");

        // ── 3. Objetivo de la Validación ─────────────────────────────────
        md.append("## 3. Objetivo de la Validación\n\n");
        md.append("**Objetivo principal:**\n\n");
        md.append(mdBlock(r.getMainObjective())).append("\n\n");
        if (notBlank(r.getSpecificObjectives())) {
            md.append("**Objetivos específicos:**\n\n");
            md.append(r.getSpecificObjectives().trim()).append("\n\n");
        }
        md.append("---\n\n");

        // ── 4. Componentes Técnicos Impactados ───────────────────────────
        md.append("## 4. Componentes Técnicos Impactados\n\n");
        md.append("| Componente | Detalle |\n");
        md.append("|------------|---------|\n");
        mdRow(md, "Módulos",             s(r.getModules()));
        mdRow(md, "Stored Procedures",   s(r.getStoredProcedures()));
        mdRow(md, "Jobs",                s(r.getJobs()));
        mdRow(md, "Tablas",              s(r.getTables()));
        mdRow(md, "Reportes",            s(r.getReports()));
        mdRow(md, "Otros",               s(r.getOtherComponents()));
        md.append("\n---\n\n");

        // ── 5. Reglas de Negocio Validadas ───────────────────────────────
        md.append("## 5. Reglas de Negocio Validadas\n\n");
        List<BusinessRule> rules = orEmpty(r.getBusinessRules());
        if (rules.isEmpty()) {
            md.append("*Sin reglas de negocio registradas.*\n\n");
        } else {
            md.append("| # | Descripción | Estado |\n");
            md.append("|---|-------------|--------|\n");
            for (int i = 0; i < rules.size(); i++) {
                BusinessRule rule = rules.get(i);
                md.append("| ").append(i + 1)
                  .append(" | ").append(mdCell(s(rule.getDescription())))
                  .append(" | ").append(ruleEmoji(rule.getValidationStatus()))
                  .append(" ").append(mdCell(s(rule.getValidationStatus())))
                  .append(" |\n");
            }
            md.append("\n");
        }
        md.append("---\n\n");

        // ── 6. Casos de Prueba Ejecutados ────────────────────────────────
        md.append("## 6. Casos de Prueba Ejecutados\n\n");
        List<TestCase> tcs = orEmpty(r.getTestCases());
        if (tcs.isEmpty()) {
            md.append("*Sin casos de prueba registrados.*\n\n");
        } else {
            md.append("| ID | Descripción | Resultado Esperado | Resultado Obtenido | Estado |\n");
            md.append("|----|-------------|--------------------|--------------------|--------|\n");
            for (TestCase tc : tcs) {
                boolean passed = "Exitoso".equalsIgnoreCase(tc.getResult());
                md.append("| ").append(mdCell(s(tc.getCaseId())))
                  .append(" | ").append(mdCell(s(tc.getDescription())))
                  .append(" | ").append(mdCell(s(tc.getExpectedResult())))
                  .append(" | ").append(mdCell(s(tc.getObtainedResult())))
                  .append(" | ").append(passed ? "✅" : "❌")
                  .append(" ").append(mdCell(s(tc.getResult())))
                  .append(" |\n");
            }
            md.append("\n");
        }
        md.append("---\n\n");

        // ── 7. Bugs Relacionados ─────────────────────────────────────────
        md.append("## 7. Bugs Relacionados\n\n");
        List<RelatedBug> bugs = orEmpty(r.getRelatedBugs());
        if (bugs.isEmpty()) {
            md.append("*Sin bugs relacionados.*\n\n");
        } else {
            md.append("| Identificador | Descripción | Estatus |\n");
            md.append("|---------------|-------------|---------|\n");
            for (RelatedBug bug : bugs) {
                md.append("| ").append(mdCell(s(bug.getIdentifier())))
                  .append(" | ").append(mdCell(s(bug.getDescription())))
                  .append(" | ").append(mdCell(s(bug.getBugStatus())))
                  .append(" |\n");
            }
            md.append("\n");
        }
        md.append("---\n\n");

        // ── 8. Conclusiones ──────────────────────────────────────────────
        md.append("## 8. Conclusiones\n\n");
        if (notBlank(r.getFinalResult())) {
            boolean cumple = "Cumple".equalsIgnoreCase(r.getFinalResult());
            md.append("**Resultado final del RFC:** ")
              .append(cumple ? "✅" : "❌")
              .append(" **").append(r.getFinalResult()).append("**\n\n");
        }
        if (notBlank(r.getObservations())) {
            md.append("**Observaciones relevantes:**\n\n")
              .append(r.getObservations().trim()).append("\n\n");
        }
        if (notBlank(r.getRisks())) {
            md.append("**Riesgos identificados:**\n\n")
              .append(r.getRisks().trim()).append("\n\n");
        }
        if (notBlank(r.getRecommendations())) {
            md.append("**Recomendaciones:**\n\n")
              .append(r.getRecommendations().trim()).append("\n\n");
        }
        md.append("---\n\n");

        // ── 9. Notas Finales ─────────────────────────────────────────────
        md.append("## 9. Notas Finales\n\n");
        md.append(mdBlock(r.getFinalNotes())).append("\n\n");
        md.append("---\n\n");

        // ── Footer ────────────────────────────────────────────────────────
        md.append("*Documento generado el ").append(now)
          .append(" mediante el sistema **Release Notifier QA**.*\n");

        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── PDF Helper methods ─────────────────────────────────────────────────

    private String s(String v) {
        return v != null && !v.isBlank() ? v.trim() : "";
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private void addHRule(Document doc, Color color, float lineWidth) throws DocumentException {
        LineSeparator ls = new LineSeparator(lineWidth, 100f, color, Element.ALIGN_CENTER, -2f);
        Paragraph p = new Paragraph(new Chunk(ls));
        p.setSpacingBefore(4);
        p.setSpacingAfter(12);
        doc.add(p);
    }

    private void addSectionHeading(Document doc, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(16);
        p.setSpacingAfter(0);
        doc.add(p);
        LineSeparator ls = new LineSeparator(1.5f, 100f, C_PRIMARY, Element.ALIGN_LEFT, -4f);
        Paragraph line = new Paragraph(new Chunk(ls));
        line.setSpacingBefore(2);
        line.setSpacingAfter(8);
        doc.add(line);
    }

    private void addBodyText(Document doc, String text, Font valueFont, Font mutedFont) throws DocumentException {
        boolean empty = text == null || text.isBlank();
        Paragraph p = new Paragraph(empty ? "—" : text, empty ? mutedFont : valueFont);
        p.setSpacingBefore(2);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void addLabelAndText(Document doc, String label, String text, Font lf, Font vf, Font mf)
            throws DocumentException {
        Paragraph lp = new Paragraph(label, lf);
        lp.setSpacingBefore(4);
        doc.add(lp);
        boolean empty = text == null || text.isBlank();
        Paragraph vp = new Paragraph(empty ? "—" : text, empty ? mf : vf);
        vp.setIndentationLeft(10);
        vp.setSpacingAfter(6);
        doc.add(vp);
    }

    private void addInfoPair(PdfPTable table, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setPaddingBottom(7);
        table.addCell(lc);
        String display = (value == null || value.isBlank()) ? "—" : value;
        PdfPCell vc = new PdfPCell(new Phrase(display, vf));
        vc.setBorder(Rectangle.NO_BORDER);
        vc.setPaddingBottom(7);
        table.addCell(vc);
    }

    private void addCompRow(PdfPTable table, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBackgroundColor(C_BG_ALT);
        lc.setBorderColor(C_BORDER);
        lc.setPadding(7);
        table.addCell(lc);
        String display = (value == null || value.isBlank()) ? "—" : value;
        PdfPCell vc = new PdfPCell(new Phrase(display, vf));
        vc.setBorderColor(C_BORDER);
        vc.setPadding(7);
        table.addCell(vc);
    }

    private void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(C_PRIMARY);
            cell.setPadding(8);
            cell.setBorderColor(C_DARK);
            table.addCell(cell);
        }
    }

    private PdfPCell tableCell(String text, Font font, Color bg, int align) {
        String display = (text == null || text.isBlank()) ? "—" : text;
        PdfPCell cell = new PdfPCell(new Phrase(display, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(7);
        cell.setBorderColor(C_BORDER);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private Paragraph emptyNote(Font font) {
        Paragraph p = new Paragraph("(Sin registros)", font);
        p.setSpacingBefore(2);
        p.setSpacingAfter(10);
        return p;
    }

    private Color ruleStatusColor(String status) {
        if (status == null) return C_MUTED;
        return switch (status) {
            case "Válida"   -> C_SUCCESS;
            case "Inválida" -> C_DANGER;
            default         -> C_WARNING;
        };
    }

    // ── Markdown helpers ──────────────────────────────────────────────────

    /** Escapes pipe and strips newlines so a value is safe inside a Markdown table cell. */
    private String mdCell(String v) {
        if (v == null || v.isBlank()) return "—";
        return v.trim().replace("|", "\\|").replace("\r", "").replace("\n", " ");
    }

    /** Appends a two-column table row. */
    private void mdRow(StringBuilder sb, String label, String value) {
        sb.append("| ").append(mdCell(label)).append(" | ").append(mdCell(value)).append(" |\n");
    }

    /** Returns text or an italic placeholder if blank. */
    private String mdBlock(String v) {
        return (v == null || v.isBlank()) ? "*Sin información registrada.*" : v.trim();
    }

    private String ruleEmoji(String status) {
        if (status == null) return "⏳";
        return switch (status) {
            case "Válida"   -> "✅";
            case "Inválida" -> "❌";
            default         -> "⏳";
        };
    }
}
