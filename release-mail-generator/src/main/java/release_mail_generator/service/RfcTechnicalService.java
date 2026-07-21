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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
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

    private byte[] logoBytes;

    @PostConstruct
    private void init() {
        try {
            ClassPathResource res = new ClassPathResource("static/images/logo-empresa.png");
            logoBytes = res.getInputStream().readAllBytes();
            log.info("Logo de empresa cargado ({} bytes)", logoBytes.length);
        } catch (Exception e) {
            log.warn("No se pudo cargar logo-empresa.png: {}", e.getMessage());
            logoBytes = null;
        }
    }

    // ── Color palette (corporate modern) ──────────────────────────────────
    private static final Color C_PRIMARY    = new Color(37, 99, 235);
    private static final Color C_PRIMARY_BG = new Color(239, 246, 255);   // light blue tint
    private static final Color C_DARK       = new Color(30, 58, 138);
    private static final Color C_NAVY       = new Color(15, 23, 42);
    private static final Color C_BORDER     = new Color(226, 232, 240);
    private static final Color C_BG_ALT     = new Color(241, 245, 249);
    private static final Color C_ROW_ALT    = new Color(248, 250, 252);
    private static final Color C_CARD_BG    = new Color(248, 250, 252);
    private static final Color C_TEXT       = new Color(15, 23, 42);
    private static final Color C_MUTED      = new Color(100, 116, 139);
    private static final Color C_SUCCESS    = new Color(5, 150, 105);
    private static final Color C_SUCCESS_BG = new Color(209, 250, 229);
    private static final Color C_DANGER     = new Color(220, 38, 38);
    private static final Color C_DANGER_BG  = new Color(254, 226, 226);
    private static final Color C_WARNING    = new Color(217, 119, 6);
    private static final Color C_WARNING_BG = new Color(254, 243, 199);

    // ── CRUD ───────────────────────────────────────────────────────────────

    public List<RfcTechnicalRecord> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<RfcTechnicalRecord> search(String query) {
        return repository.search(query);
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
        // Auto-update status based on final result
        if (notBlank(record.getFinalResult())) {
            if ("Cumple".equalsIgnoreCase(record.getFinalResult())) {
                record.setStatus("Aprobado");
            } else if ("No cumple".equalsIgnoreCase(record.getFinalResult())) {
                record.setStatus("Rechazado");
            }
        }
        // Validate unique RFC number
        if (notBlank(record.getRfcNumber())) {
            List<RfcTechnicalRecord> existing = repository.findAllByRfcNumberIgnoreCase(record.getRfcNumber().trim());
            for (RfcTechnicalRecord ex : existing) {
                boolean isNew = record.getId() == null || record.getId().isBlank();
                boolean isDifferent = !ex.getId().equals(record.getId());
                if (isNew || isDifferent) {
                    throw new IllegalArgumentException("Ya existe un RFC con el número '" + record.getRfcNumber().trim() + "'");
                }
            }
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
        Document doc = new Document(PageSize.A4, 50, 50, 80, 65);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        final String rfcLabel = s(r.getRfcNumber()) + " — " + s(r.getChangeName());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                if (w.getPageNumber() == 1) return; // Skip footer on cover page
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font footerFont = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, C_MUTED);
                    // Footer line
                    cb.setLineWidth(0.4f); cb.setColorStroke(C_BORDER);
                    cb.moveTo(d.left(), d.bottom() - 8); cb.lineTo(d.right(), d.bottom() - 8); cb.stroke();
                    // Footer left: system name
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("Release Notifier QA", footerFont), d.left(), d.bottom() - 20, 0);
                    // Footer center: RFC
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase(rfcLabel, footerFont), (d.left() + d.right()) / 2, d.bottom() - 20, 0);
                    // Footer right: page
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase("Página " + (w.getPageNumber() - 1), footerFont), d.right(), d.bottom() - 20, 0);
                    // Header line
                    cb.setLineWidth(0.6f); cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top() + 10); cb.lineTo(d.right(), d.top() + 10); cb.stroke();
                    // Header right: date
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase(now, new Font(Font.HELVETICA, 7, Font.NORMAL, C_MUTED)), d.right(), d.top() + 14, 0);
                } catch (Exception ignored) {}
            }
        });

        doc.open();

        Font titleFont   = new Font(Font.HELVETICA, 20, Font.BOLD, C_TEXT);
        Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD, C_PRIMARY);
        Font labelFont   = new Font(Font.HELVETICA, 8.5f, Font.BOLD, new Color(55, 65, 81));
        Font valueFont   = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, C_TEXT);
        Font tHeaderFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
        Font tBodyFont   = new Font(Font.HELVETICA, 9, Font.NORMAL, C_TEXT);
        Font mutedFont   = new Font(Font.HELVETICA, 8.5f, Font.ITALIC, C_MUTED);

        // ═══════════════════════ COVER PAGE ═══════════════════════════════
        addCoverPage(doc, "RFC Técnico Final", s(r.getRfcNumber()), s(r.getChangeName()),
                s(r.getTesterName()), s(r.getValidationDate()), now);
        doc.newPage();

        // ═══════════════════════ CONTENT ══════════════════════════════════

        // ── Executive Summary Card ─────────────────────────────────────
        addExecutiveSummary(doc, r, labelFont, valueFont);

        // ── 1. Información General ────────────────────────────────────
        addSectionHeading(doc, "1. Información General", sectionFont);
        PdfPTable info = new PdfPTable(new float[]{1.2f, 2.8f, 1.2f, 2.8f});
        info.setWidthPercentage(100); info.setSpacingBefore(6); info.setSpacingAfter(16);
        info.setKeepTogether(true);
        addInfoPair(info, "RFC:", s(r.getRfcNumber()), labelFont, valueFont);
        addInfoPair(info, "Nombre del cambio:", s(r.getChangeName()), labelFont, valueFont);
        addInfoPair(info, "Fecha de validación:", s(r.getValidationDate()), labelFont, valueFont);
        addInfoPair(info, "Tester responsable:", s(r.getTesterName()), labelFont, valueFont);
        addInfoPair(info, "Solicitante / Área:", s(r.getRequester()), labelFont, valueFont);
        addInfoPair(info, "Ambiente:", s(r.getEnvironment()), labelFont, valueFont);
        addInfoPair(info, "Estado:", s(r.getStatus()), labelFont, valueFont);
        addInfoPair(info, "Resultado:", s(r.getFinalResult()), labelFont, valueFont);
        doc.add(info);

        // ── 2. Contexto del Cambio ────────────────────────────────────
        addSectionHeading(doc, "2. Contexto del Cambio", sectionFont);
        addBodyText(doc, s(r.getChangeContext()), valueFont, mutedFont);

        // ── 3. Objetivo de la Validación ──────────────────────────────
        addSectionHeading(doc, "3. Objetivo de la Validación", sectionFont);
        addLabelAndText(doc, "Objetivo principal:", s(r.getMainObjective()), labelFont, valueFont, mutedFont);
        if (notBlank(r.getSpecificObjectives()))
            addLabelAndText(doc, "Objetivos específicos:", s(r.getSpecificObjectives()), labelFont, valueFont, mutedFont);

        // ── 4. Componentes Técnicos ───────────────────────────────────
        addSectionHeading(doc, "4. Componentes Técnicos Impactados", sectionFont);
        PdfPTable comp = new PdfPTable(new float[]{1.4f, 3f});
        comp.setWidthPercentage(100); comp.setSpacingBefore(6); comp.setSpacingAfter(16);
        comp.setKeepTogether(true);
        addCompRow(comp, "Módulos:", s(r.getModules()), labelFont, tBodyFont);
        addCompRow(comp, "Stored Procedures:", s(r.getStoredProcedures()), labelFont, tBodyFont);
        addCompRow(comp, "Jobs:", s(r.getJobs()), labelFont, tBodyFont);
        addCompRow(comp, "Tablas:", s(r.getTables()), labelFont, tBodyFont);
        addCompRow(comp, "Reportes:", s(r.getReports()), labelFont, tBodyFont);
        addCompRow(comp, "Otros:", s(r.getOtherComponents()), labelFont, tBodyFont);
        doc.add(comp);

        // ── 5. Reglas de Negocio ──────────────────────────────────────
        addSectionHeading(doc, "5. Reglas de Negocio Validadas", sectionFont);
        List<BusinessRule> rules = orEmpty(r.getBusinessRules());
        if (rules.isEmpty()) { doc.add(emptyNote(mutedFont)); }
        else {
            PdfPTable rt = new PdfPTable(new float[]{0.5f, 4f, 1.2f});
            rt.setWidthPercentage(100); rt.setSpacingBefore(6); rt.setSpacingAfter(16);
            rt.setHeaderRows(1);
            addTableHeader(rt, new String[]{"#", "Descripción de la Regla", "Estado"}, tHeaderFont);
            for (int i = 0; i < rules.size(); i++) {
                BusinessRule rule = rules.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                rt.addCell(tableCell(String.valueOf(i + 1), tBodyFont, bg, Element.ALIGN_CENTER));
                rt.addCell(tableCell(s(rule.getDescription()), tBodyFont, bg, Element.ALIGN_LEFT));
                rt.addCell(tableCell(s(rule.getValidationStatus()),
                        new Font(Font.HELVETICA, 9, Font.BOLD, ruleStatusColor(rule.getValidationStatus())), bg, Element.ALIGN_CENTER));
            }
            doc.add(rt);
        }

        // ── 6. Casos de Prueba ────────────────────────────────────────
        addSectionHeading(doc, "6. Casos de Prueba Ejecutados", sectionFont);
        List<TestCase> tcs = orEmpty(r.getTestCases());
        if (tcs.isEmpty()) { doc.add(emptyNote(mutedFont)); }
        else {
            // Summary table
            PdfPTable tt = new PdfPTable(new float[]{0.8f, 2.5f, 1.2f, 1.2f});
            tt.setWidthPercentage(100); tt.setSpacingBefore(6); tt.setSpacingAfter(16);
            tt.setHeaderRows(1);
            addTableHeader(tt, new String[]{"ID", "Nombre / Descripción", "Tipo", "Estado"}, tHeaderFont);
            for (int i = 0; i < tcs.size(); i++) {
                TestCase tc = tcs.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                tt.addCell(tableCell(s(tc.getCaseId()), tBodyFont, bg, Element.ALIGN_CENTER));
                String name = notBlank(tc.getCaseName()) ? tc.getCaseName() : s(tc.getDescription());
                tt.addCell(tableCell(name, tBodyFont, bg, Element.ALIGN_LEFT));
                tt.addCell(tableCell(s(tc.getTestType()), tBodyFont, bg, Element.ALIGN_CENTER));
                String st = notBlank(tc.getStatus()) ? tc.getStatus() : s(tc.getResult());
                Color sc = "Aprobado".equals(st) || "Exitoso".equalsIgnoreCase(st) ? C_SUCCESS : "Fallido".equals(st) ? C_DANGER : C_WARNING;
                tt.addCell(tableCell(st, new Font(Font.HELVETICA, 9, Font.BOLD, sc), bg, Element.ALIGN_CENTER));
            }
            doc.add(tt);
        }

        // ── 7. Bugs ──────────────────────────────────────────────────
        addSectionHeading(doc, "7. Bugs Relacionados", sectionFont);
        List<RelatedBug> bugs = orEmpty(r.getRelatedBugs());
        if (bugs.isEmpty()) { doc.add(emptyNote(mutedFont)); }
        else {
            PdfPTable bt = new PdfPTable(new float[]{1.2f, 3.5f, 1.2f});
            bt.setWidthPercentage(100); bt.setSpacingBefore(6); bt.setSpacingAfter(16);
            bt.setHeaderRows(1);
            addTableHeader(bt, new String[]{"Identificador", "Descripción", "Estatus"}, tHeaderFont);
            for (int i = 0; i < bugs.size(); i++) {
                RelatedBug bug = bugs.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                bt.addCell(tableCell(s(bug.getIdentifier()), tBodyFont, bg, Element.ALIGN_LEFT));
                bt.addCell(tableCell(s(bug.getDescription()), tBodyFont, bg, Element.ALIGN_LEFT));
                bt.addCell(tableCell(s(bug.getBugStatus()), tBodyFont, bg, Element.ALIGN_CENTER));
            }
            doc.add(bt);
        }

        // ── 8. Conclusiones ───────────────────────────────────────────
        addSectionHeading(doc, "8. Conclusiones", sectionFont);
        if (notBlank(r.getFinalResult())) {
            addVerdictCard(doc, r.getFinalResult());
        }
        if (notBlank(r.getObservations())) addLabelAndText(doc, "Observaciones:", s(r.getObservations()), labelFont, valueFont, mutedFont);
        if (notBlank(r.getRisks())) addLabelAndText(doc, "Riesgos:", s(r.getRisks()), labelFont, valueFont, mutedFont);
        if (notBlank(r.getRecommendations())) addLabelAndText(doc, "Recomendaciones:", s(r.getRecommendations()), labelFont, valueFont, mutedFont);

        // ── 9. Notas Finales ──────────────────────────────────────────
        if (notBlank(r.getFinalNotes())) {
            addSectionHeading(doc, "9. Notas Finales", sectionFont);
            addBodyText(doc, s(r.getFinalNotes()), valueFont, mutedFont);
        }

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

    // ── Test Cases Only – PDF ──────────────────────────────────────────────

    public byte[] generateTestCasesPdf(RfcTechnicalRecord r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 72, 62);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);

        final String rfcLabel = "Casos de Prueba  —  " + s(r.getRfcNumber());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, C_MUTED);
                    cb.setLineWidth(0.5f); cb.setColorStroke(C_BORDER);
                    cb.moveTo(d.left(), d.bottom() - 4); cb.lineTo(d.right(), d.bottom() - 4); cb.stroke();
                    Phrase pageNum = new Phrase("Página " + w.getPageNumber(), footerFont);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, pageNum, d.right(), d.bottom() - 16, 0);
                    Phrase label = new Phrase(rfcLabel, footerFont);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, label, d.left(), d.bottom() - 16, 0);
                    cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top() + 8); cb.lineTo(d.right(), d.top() + 8); cb.stroke();
                } catch (Exception ignored) {}
            }
        });
        doc.open();

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD, C_PRIMARY);
        Font subSecFont  = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(55, 65, 81));
        Font labelFont   = new Font(Font.HELVETICA, 8.5f, Font.BOLD, new Color(55, 65, 81));
        Font valueFont   = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, C_TEXT);
        Font tHeaderFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
        Font tBodyFont   = new Font(Font.HELVETICA, 9, Font.NORMAL, C_TEXT);
        Font mutedFont   = new Font(Font.HELVETICA, 8.5f, Font.ITALIC, C_MUTED);

        // Cover page
        addCoverPage(doc, "Casos de Prueba", s(r.getRfcNumber()), s(r.getChangeName()),
                s(r.getTesterName()), s(r.getValidationDate()), now);
        doc.newPage();

        // Test Cases
        List<TestCase> tcs = orEmpty(r.getTestCases());
        if (tcs.isEmpty()) {
            doc.add(emptyNote(mutedFont));
        } else {
            for (int idx = 0; idx < tcs.size(); idx++) {
                TestCase tc = tcs.get(idx);
                String tcTitle = s(tc.getCaseId()).isEmpty() ? "Caso " + (idx+1) : s(tc.getCaseId());
                if (notBlank(tc.getCaseName())) tcTitle += " — " + tc.getCaseName();
                addSectionHeading(doc, tcTitle, sectionFont);

                // Info table
                PdfPTable tcInfo = new PdfPTable(new float[]{1.2f, 3f});
                tcInfo.setWidthPercentage(100); tcInfo.setSpacingAfter(8);
                addCompRow(tcInfo, "Prioridad:", s(tc.getPriority()), labelFont, tBodyFont);
                addCompRow(tcInfo, "Tipo:", s(tc.getTestType()), labelFont, tBodyFont);
                addCompRow(tcInfo, "Estado:", s(tc.getStatus() != null ? tc.getStatus() : tc.getResult()), labelFont, tBodyFont);
                if (notBlank(tc.getTesterName())) addCompRow(tcInfo, "Tester:", s(tc.getTesterName()), labelFont, tBodyFont);
                if (notBlank(tc.getExecutionDate())) addCompRow(tcInfo, "Fecha:", s(tc.getExecutionDate()), labelFont, tBodyFont);
                if (notBlank(tc.getObjective())) addCompRow(tcInfo, "Objetivo:", s(tc.getObjective()), labelFont, tBodyFont);
                if (notBlank(tc.getPreconditions())) addCompRow(tcInfo, "Precondiciones:", s(tc.getPreconditions()), labelFont, tBodyFont);
                if (notBlank(tc.getTestData())) addCompRow(tcInfo, "Datos de prueba:", s(tc.getTestData()), labelFont, tBodyFont);
                doc.add(tcInfo);

                // Steps
                List<TestStep> steps = tc.getSteps() != null ? tc.getSteps() : Collections.emptyList();
                if (!steps.isEmpty()) {
                    Paragraph stepsP = new Paragraph("Pasos de Ejecución:", subSecFont);
                    stepsP.setSpacingBefore(4); stepsP.setSpacingAfter(4); doc.add(stepsP);
                    PdfPTable st = new PdfPTable(new float[]{0.5f, 2.5f, 2.5f});
                    st.setWidthPercentage(100); st.setSpacingAfter(8);
                    addTableHeader(st, new String[]{"#", "Acción", "Resultado Esperado"}, tHeaderFont);
                    for (int si = 0; si < steps.size(); si++) {
                        TestStep step = steps.get(si);
                        Color bg = si % 2 == 0 ? Color.WHITE : C_ROW_ALT;
                        st.addCell(tableCell(String.valueOf(step.getStepNumber()), tBodyFont, bg, Element.ALIGN_CENTER));
                        st.addCell(tableCell(s(step.getAction()), tBodyFont, bg, Element.ALIGN_LEFT));
                        st.addCell(tableCell(s(step.getExpectedResult()), tBodyFont, bg, Element.ALIGN_LEFT));
                    }
                    doc.add(st);
                }

                // Result
                if (notBlank(tc.getObtainedResult()) || notBlank(tc.getObservations())) {
                    Paragraph resP = new Paragraph("Resultado:", subSecFont);
                    resP.setSpacingBefore(4); resP.setSpacingAfter(4); doc.add(resP);
                    if (notBlank(tc.getObtainedResult())) addLabelAndText(doc, "Resultado obtenido:", s(tc.getObtainedResult()), labelFont, valueFont, mutedFont);
                    if (notBlank(tc.getObservations())) addLabelAndText(doc, "Observaciones:", s(tc.getObservations()), labelFont, valueFont, mutedFont);
                }

                // Evidences — flow inline, no forced page breaks
                List<TestEvidence> evs = tc.getEvidences() != null ? tc.getEvidences() : Collections.emptyList();
                if (!evs.isEmpty()) {
                    Paragraph evP = new Paragraph("Evidencias (" + evs.size() + "):", subSecFont);
                    evP.setSpacingBefore(10); evP.setSpacingAfter(6); doc.add(evP);
                    addEvidenceGrid(doc, writer, evs, mutedFont);
                }

                if (notBlank(tc.getFinalObservations())) {
                    addLabelAndText(doc, "Observaciones finales:", s(tc.getFinalObservations()), labelFont, valueFont, mutedFont);
                }
            }
        }

        doc.close();
        return bos.toByteArray();
    }

    // ── Test Cases Only – Markdown ─────────────────────────────────────────

    public byte[] generateTestCasesMarkdown(RfcTechnicalRecord r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();

        md.append("# Casos de Prueba\n\n");
        md.append("> Documento de casos de prueba del RFC Técnico Final.\n\n");
        md.append("**Generado el:** ").append(now).append("  \n");
        md.append("**RFC:** ").append(s(r.getRfcNumber())).append("  \n");
        md.append("**Nombre del cambio:** ").append(s(r.getChangeName())).append("  \n");
        md.append("**Fecha de validación:** ").append(s(r.getValidationDate())).append("  \n");
        md.append("**Tester responsable:** ").append(s(r.getTesterName())).append("\n\n");
        md.append("---\n\n");

        List<TestCase> tcs = orEmpty(r.getTestCases());
        if (tcs.isEmpty()) {
            md.append("*Sin casos de prueba registrados.*\n\n");
        } else {
            for (int idx = 0; idx < tcs.size(); idx++) {
                TestCase tc = tcs.get(idx);
                String tcId = notBlank(tc.getCaseId()) ? tc.getCaseId() : "Caso " + (idx + 1);
                String tcName = notBlank(tc.getCaseName()) ? tc.getCaseName() : s(tc.getDescription());
                md.append("## ").append(tcId);
                if (notBlank(tcName)) md.append(" — ").append(tcName);
                md.append("\n\n");

                // Info table
                md.append("| Campo | Valor |\n|-------|-------|\n");
                if (notBlank(tc.getPriority())) mdRow(md, "Prioridad", s(tc.getPriority()));
                if (notBlank(tc.getTestType())) mdRow(md, "Tipo", s(tc.getTestType()));
                String status = notBlank(tc.getStatus()) ? tc.getStatus() : s(tc.getResult());
                mdRow(md, "Estado", status);
                if (notBlank(tc.getTesterName())) mdRow(md, "Tester", s(tc.getTesterName()));
                if (notBlank(tc.getExecutionDate())) mdRow(md, "Fecha", s(tc.getExecutionDate()));
                md.append("\n");

                if (notBlank(tc.getObjective())) md.append("**Objetivo:** ").append(tc.getObjective().trim()).append("\n\n");
                if (notBlank(tc.getPreconditions())) md.append("**Precondiciones:** ").append(tc.getPreconditions().trim()).append("\n\n");
                if (notBlank(tc.getTestData())) md.append("**Datos de prueba:** ").append(tc.getTestData().trim()).append("\n\n");

                // Steps
                List<TestStep> steps = tc.getSteps() != null ? tc.getSteps() : Collections.emptyList();
                if (!steps.isEmpty()) {
                    md.append("### Pasos de Ejecución\n\n");
                    md.append("| # | Acción | Resultado Esperado |\n|---|--------|--------------------|\n");
                    for (TestStep step : steps) {
                        md.append("| ").append(step.getStepNumber())
                          .append(" | ").append(mdCell(s(step.getAction())))
                          .append(" | ").append(mdCell(s(step.getExpectedResult())))
                          .append(" |\n");
                    }
                    md.append("\n");
                }

                // Result
                if (notBlank(tc.getObtainedResult())) md.append("**Resultado obtenido:** ").append(tc.getObtainedResult().trim()).append("\n\n");
                if (notBlank(tc.getObservations())) md.append("**Observaciones:** ").append(tc.getObservations().trim()).append("\n\n");

                // Evidences
                List<TestEvidence> evs = tc.getEvidences() != null ? tc.getEvidences() : Collections.emptyList();
                if (!evs.isEmpty()) {
                    md.append("### Evidencias\n\n");
                    for (int ei = 0; ei < evs.size(); ei++) {
                        TestEvidence ev = evs.get(ei);
                        md.append("- _[Evidencia ").append(ei + 1).append("]_");
                        if (notBlank(ev.getDescription())) md.append(" ").append(ev.getDescription().trim());
                        md.append("\n");
                    }
                    md.append("\n");
                }

                if (notBlank(tc.getFinalObservations())) md.append("**Observaciones finales:** ").append(tc.getFinalObservations().trim()).append("\n\n");
                md.append("---\n\n");
            }
        }

        md.append("*Documento generado el ").append(now).append(" mediante **Release Notifier QA**.*\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── PDF Helper methods ─────────────────────────────────────────────────

    /**
     * Smart evidence grid renderer. Adapts layout based on image count and dimensions.
     * - 1 image: full width
     * - 2 images: side by side if both landscape, otherwise stacked
     * - 3+ images: 2-column grid
     * All images respect page margins, preserve aspect ratio, and avoid page splits.
     */
    private void addEvidenceGrid(Document doc, PdfWriter writer, List<TestEvidence> evidences, Font captionFont)
            throws DocumentException {
        float pageWidth = doc.right() - doc.left();
        float usablePageH = doc.top() - doc.bottom() - 40;

        for (int ei = 0; ei < evidences.size(); ei++) {
            TestEvidence ev = evidences.get(ei);
            if (!notBlank(ev.getImageBase64())) continue;

            try {
                byte[] imgBytes = decodeBase64Image(ev.getImageBase64());
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imgBytes);

                float origW = img.getWidth();
                float origH = img.getHeight();

                // Available space on this page (after label)
                float remaining = writer.getVerticalPosition(false) - doc.bottom() - 50;

                // If less than 25% of page left, start fresh
                if (remaining < usablePageH * 0.25f) {
                    doc.newPage();
                    remaining = usablePageH - 40;
                }

                // Scale image to fit: page width AND remaining height
                float maxH = Math.min(remaining - 20, usablePageH * 0.8f);
                float scale = Math.min(pageWidth / origW, maxH / origH);
                if (scale > 1f) scale = 1f;
                float finalW = origW * scale;
                float finalH = origH * scale;
                img.scaleAbsolute(finalW, finalH);

                // Evidence label
                Font evLabelFont = new Font(Font.HELVETICA, 7.5f, Font.BOLD, C_PRIMARY);
                Paragraph label = new Paragraph("Evidencia " + (ei + 1), evLabelFont);
                label.setSpacingBefore(8);
                label.setSpacingAfter(3);
                doc.add(label);

                // Image in table (required by OpenPDF for proper rendering)
                PdfPTable imgTable = new PdfPTable(1);
                imgTable.setWidthPercentage(100);
                imgTable.setSpacingAfter(4);
                PdfPCell imgCell = new PdfPCell(img, false);
                imgCell.setBorderColor(C_BORDER);
                imgCell.setBorderWidth(0.5f);
                imgCell.setPadding(4);
                imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                imgCell.setBackgroundColor(Color.WHITE);
                imgTable.addCell(imgCell);
                doc.add(imgTable);

                // Caption
                if (notBlank(ev.getDescription())) {
                    Font descFont = new Font(Font.HELVETICA, 8, Font.ITALIC, C_MUTED);
                    Paragraph desc = new Paragraph(ev.getDescription().trim(), descFont);
                    desc.setAlignment(Element.ALIGN_CENTER);
                    desc.setSpacingAfter(12);
                    doc.add(desc);
                } else {
                    Paragraph spacer = new Paragraph(" ");
                    spacer.setSpacingAfter(8);
                    doc.add(spacer);
                }

            } catch (Exception ex) {
                log.warn("No se pudo renderizar evidencia {}: {}", ei + 1, ex.getMessage());
                Paragraph err = new Paragraph("[Evidencia " + (ei + 1) + " — imagen no disponible]", captionFont);
                err.setSpacingAfter(8);
                doc.add(err);
            }
        }
    }

    /** Decodes a base64 data URI or raw base64 string to bytes. */
    private byte[] decodeBase64Image(String b64) {
        if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);
        return Base64.getDecoder().decode(b64.trim());
    }

    /** Estimates the rendered height of the first evidence image for page break decisions. */
    private float estimateFirstEvidenceHeight(List<TestEvidence> evidences, float pageWidth) {
        for (TestEvidence ev : evidences) {
            if (!notBlank(ev.getImageBase64())) continue;
            try {
                byte[] imgBytes = decodeBase64Image(ev.getImageBase64());
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imgBytes);
                float scale = Math.min(pageWidth / img.getWidth(), 450f / img.getHeight());
                if (scale > 1f) scale = 1f;
                return img.getHeight() * scale;
            } catch (Exception e) {
                return 200f; // fallback estimate
            }
        }
        return 100f;
    }

    private void addCoverPage(Document doc, String docType, String rfcNumber, String changeName,
                              String testerName, String validationDate, String generatedAt) throws DocumentException {
        // Navy top band
        PdfPTable topBand = new PdfPTable(1);
        topBand.setWidthPercentage(110);
        PdfPCell bandCell = new PdfPCell();
        bandCell.setBackgroundColor(C_NAVY);
        bandCell.setFixedHeight(5);
        bandCell.setBorder(Rectangle.NO_BORDER);
        topBand.addCell(bandCell);
        doc.add(topBand);

        doc.add(new Paragraph(" "));

        // Logo + branding row
        if (logoBytes != null) {
            try {
                com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoBytes);
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

                Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_PRIMARY);
                Phrase brandPhrase = new Phrase("RELEASE NOTIFIER QA\nEquipo QA & Liberaciones", brandFont);
                PdfPCell brandCell = new PdfPCell(brandPhrase);
                brandCell.setBorder(Rectangle.NO_BORDER);
                brandCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                brandCell.setPaddingLeft(8);
                logoRow.addCell(brandCell);

                doc.add(logoRow);
            } catch (Exception e) {
                // Fallback: text-only branding
                Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_PRIMARY);
                Paragraph brand = new Paragraph("RELEASE NOTIFIER QA  \u00b7  MEGACABLE", brandFont);
                brand.setSpacingAfter(30);
                doc.add(brand);
            }
        } else {
            doc.add(new Paragraph(" "));
            Font brandFont = new Font(Font.HELVETICA, 8.5f, Font.BOLD, C_PRIMARY);
            Paragraph brand = new Paragraph("RELEASE NOTIFIER QA  \u00b7  MEGACABLE", brandFont);
            brand.setSpacingAfter(30);
            doc.add(brand);
        }

        // Document type — large bold
        Font typeFont = new Font(Font.HELVETICA, 30, Font.BOLD, C_NAVY);
        Paragraph typeP = new Paragraph(docType, typeFont);
        typeP.setSpacingAfter(8);
        doc.add(typeP);

        // Blue accent bar
        LineSeparator accent = new LineSeparator(3f, 12f, C_PRIMARY, Element.ALIGN_LEFT, 0);
        Paragraph accentP = new Paragraph(new Chunk(accent));
        accentP.setSpacingAfter(20);
        doc.add(accentP);

        // RFC identifier
        if (notBlank(rfcNumber)) {
            Font rfcFont = new Font(Font.HELVETICA, 17, Font.BOLD, C_TEXT);
            Paragraph rfcP = new Paragraph(rfcNumber, rfcFont);
            rfcP.setSpacingAfter(6);
            doc.add(rfcP);
        }
        if (notBlank(changeName)) {
            Font nameFont = new Font(Font.HELVETICA, 11, Font.NORMAL, C_MUTED);
            Paragraph nameP = new Paragraph(changeName, nameFont);
            nameP.setSpacingAfter(50);
            doc.add(nameP);
        } else {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
        }

        // Metadata grid (2x2)
        Font metaLabel = new Font(Font.HELVETICA, 7, Font.BOLD, C_MUTED);
        Font metaValue = new Font(Font.HELVETICA, 10, Font.NORMAL, C_TEXT);
        PdfPTable meta = new PdfPTable(new float[]{1f, 1f});
        meta.setWidthPercentage(65);
        meta.setHorizontalAlignment(Element.ALIGN_LEFT);
        meta.setSpacingBefore(10);
        addCoverMetaCell(meta, "TESTER RESPONSABLE", testerName, metaLabel, metaValue);
        addCoverMetaCell(meta, "FECHA DE VALIDACI\u00d3N", validationDate, metaLabel, metaValue);
        addCoverMetaCell(meta, "GENERADO", generatedAt, metaLabel, metaValue);
        addCoverMetaCell(meta, "PLATAFORMA", "Release Notifier QA", metaLabel, metaValue);
        doc.add(meta);

        // Footer
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));
        Font footNote = new Font(Font.HELVETICA, 7.5f, Font.ITALIC, C_MUTED);
        Paragraph fn = new Paragraph("Documento generado autom\u00e1ticamente  \u00b7  Megacable  \u00b7  Equipo QA & Liberaciones", footNote);
        fn.setSpacingBefore(30);
        doc.add(fn);
    }

    private void addCoverMetaCell(PdfPTable table, String label, String value, Font lf, Font vf) {
        Phrase content = new Phrase();
        content.add(new Chunk(label + "\n", lf));
        content.add(new Chunk(notBlank(value) ? value : "\u2014", vf));
        PdfPCell cell = new PdfPCell(content);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(C_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(10);
        cell.setPaddingBottom(14);
        cell.setPaddingLeft(0);
        table.addCell(cell);
    }

    private void addCoverRow(PdfPTable table, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPaddingBottom(8); lc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(notBlank(value) ? value : "\u2014", vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPaddingBottom(8); vc.setPaddingLeft(8);
        table.addCell(vc);
    }

    /** Executive summary card — a quick-glance panel shown before the detailed content. */
    private void addExecutiveSummary(Document doc, RfcTechnicalRecord r, Font labelFont, Font valueFont) throws DocumentException {
        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);
        card.setSpacingBefore(4);
        card.setSpacingAfter(20);

        // Inner content table (3 columns: Status | Key Info | Metrics)
        PdfPTable inner = new PdfPTable(new float[]{1.2f, 2f, 1.5f});
        inner.setWidthPercentage(100);

        // Column 1: Result badge
        String result = s(r.getFinalResult());
        boolean cumple = "Cumple".equalsIgnoreCase(result);
        Color badgeBg = result.isEmpty() ? C_WARNING_BG : (cumple ? C_SUCCESS_BG : C_DANGER_BG);
        Color badgeColor = result.isEmpty() ? C_WARNING : (cumple ? C_SUCCESS : C_DANGER);
        String badgeText = result.isEmpty() ? "Pendiente" : result;

        Phrase resultPhrase = new Phrase();
        resultPhrase.add(new Chunk("RESULTADO\n", new Font(Font.HELVETICA, 7, Font.BOLD, C_MUTED)));
        resultPhrase.add(new Chunk(badgeText, new Font(Font.HELVETICA, 14, Font.BOLD, badgeColor)));
        PdfPCell resultCell = new PdfPCell(resultPhrase);
        resultCell.setBackgroundColor(badgeBg);
        resultCell.setBorder(Rectangle.NO_BORDER);
        resultCell.setPadding(14);
        resultCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        inner.addCell(resultCell);

        // Column 2: Key info
        Phrase infoPhrase = new Phrase();
        infoPhrase.add(new Chunk(s(r.getRfcNumber()) + "\n", new Font(Font.HELVETICA, 11, Font.BOLD, C_TEXT)));
        infoPhrase.add(new Chunk(s(r.getChangeName()) + "\n", new Font(Font.HELVETICA, 8.5f, Font.NORMAL, C_MUTED)));
        infoPhrase.add(new Chunk(s(r.getTesterName()), new Font(Font.HELVETICA, 8.5f, Font.NORMAL, C_TEXT)));
        PdfPCell infoCell = new PdfPCell(infoPhrase);
        infoCell.setBackgroundColor(C_CARD_BG);
        infoCell.setBorder(Rectangle.NO_BORDER);
        infoCell.setPadding(14);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        inner.addCell(infoCell);

        // Column 3: Metrics
        int tcCount = r.getTestCases() != null ? r.getTestCases().size() : 0;
        int brCount = r.getBusinessRules() != null ? r.getBusinessRules().size() : 0;
        int bugCount = r.getRelatedBugs() != null ? r.getRelatedBugs().size() : 0;
        Phrase metricsPhrase = new Phrase();
        metricsPhrase.add(new Chunk(tcCount + " casos de prueba\n", new Font(Font.HELVETICA, 8.5f, Font.NORMAL, C_TEXT)));
        metricsPhrase.add(new Chunk(brCount + " reglas de negocio\n", new Font(Font.HELVETICA, 8.5f, Font.NORMAL, C_TEXT)));
        metricsPhrase.add(new Chunk(bugCount + " bugs reportados", new Font(Font.HELVETICA, 8.5f, Font.NORMAL, C_TEXT)));
        PdfPCell metricsCell = new PdfPCell(metricsPhrase);
        metricsCell.setBackgroundColor(C_CARD_BG);
        metricsCell.setBorder(Rectangle.NO_BORDER);
        metricsCell.setPadding(14);
        metricsCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        inner.addCell(metricsCell);

        // Wrap in outer cell with border
        PdfPCell outerCell = new PdfPCell(inner);
        outerCell.setBorderColor(C_BORDER);
        outerCell.setBorderWidth(1f);
        outerCell.setPadding(0);
        card.addCell(outerCell);

        doc.add(card);
    }

    /** Verdict card — a colored banner showing the final result prominently. */
    private void addVerdictCard(Document doc, String finalResult) throws DocumentException {
        boolean cumple = "Cumple".equalsIgnoreCase(finalResult);
        Color bg = cumple ? C_SUCCESS_BG : C_DANGER_BG;
        Color fg = cumple ? C_SUCCESS : C_DANGER;
        String icon = cumple ? "\u2713" : "\u2717";

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);
        card.setSpacingBefore(6);
        card.setSpacingAfter(14);

        Phrase content = new Phrase();
        content.add(new Chunk("RESULTADO DE VALIDACI\u00d3N:  ", new Font(Font.HELVETICA, 9, Font.BOLD, fg)));
        content.add(new Chunk(icon + "  " + finalResult.toUpperCase(), new Font(Font.HELVETICA, 14, Font.BOLD, fg)));

        PdfPCell cell = new PdfPCell(content);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(fg);
        cell.setBorderWidth(1.5f);
        cell.setPadding(14);
        cell.setPaddingLeft(18);
        card.addCell(cell);

        doc.add(card);
    }

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
        // Corporate-style section heading with left accent bar and tinted background
        PdfPTable section = new PdfPTable(new float[]{0.015f, 0.985f});
        section.setWidthPercentage(100);
        section.setSpacingBefore(22);
        section.setSpacingAfter(12);
        section.setKeepTogether(true);

        PdfPCell accentCell = new PdfPCell();
        accentCell.setBackgroundColor(C_PRIMARY);
        accentCell.setBorder(Rectangle.NO_BORDER);
        section.addCell(accentCell);

        PdfPCell textCell = new PdfPCell(new Phrase(text, font));
        textCell.setBackgroundColor(C_PRIMARY_BG);
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setPadding(10);
        textCell.setPaddingLeft(14);
        section.addCell(textCell);

        doc.add(section);
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
        lc.setBorderWidth(0.5f);
        lc.setPadding(8);
        lc.setPaddingLeft(10);
        table.addCell(lc);
        String display = (value == null || value.isBlank()) ? "\u2014" : value;
        PdfPCell vc = new PdfPCell(new Phrase(display, vf));
        vc.setBorderColor(C_BORDER);
        vc.setBorderWidth(0.5f);
        vc.setPadding(8);
        table.addCell(vc);
    }

    private void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h.toUpperCase(), font));
            cell.setBackgroundColor(C_NAVY);
            cell.setPadding(9);
            cell.setPaddingBottom(10);
            cell.setBorderColor(C_NAVY);
            cell.setBorderWidth(0.5f);
            table.addCell(cell);
        }
    }

    private PdfPCell tableCell(String text, Font font, Color bg, int align) {
        String display = (text == null || text.isBlank()) ? "\u2014" : text;
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
