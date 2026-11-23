package release_mail_generator.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import release_mail_generator.model.UatBlock;
import release_mail_generator.model.UatRequest;
import release_mail_generator.util.HtmlSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Service
public class UatEmailService {

    private static final Logger log = LoggerFactory.getLogger(UatEmailService.class);

    private static final Color C_PRIMARY = new Color(37, 99, 235);
    private static final Color C_DARK    = new Color(30, 58, 138);
    private static final Color C_BORDER  = new Color(226, 232, 240);
    private static final Color C_BG_REQ  = new Color(239, 246, 255);
    private static final Color C_TEXT    = new Color(15, 23, 42);
    private static final Color C_MUTED   = new Color(100, 116, 139);

    @Autowired
    private ResourceLoader resourceLoader;

    /** Carga step{n}.png/jpg/gif desde classpath:/static/images/uat/ y retorna data URI base64. */
    private String loadStepImage(int n) {
        for (String ext : new String[]{"png", "jpg", "jpeg", "gif"}) {
            try {
                Resource res = resourceLoader.getResource(
                        "classpath:/static/images/uat/step" + n + "." + ext);
                if (res.exists()) {
                    byte[] bytes = res.getInputStream().readAllBytes();
                    String mime = (ext.equals("jpg") || ext.equals("jpeg")) ? "jpeg" : ext;
                    return "data:image/" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Max image width in email (px). Images wider are scaled down; smaller are left as-is. */
    private static final int EMAIL_IMG_MAX_WIDTH = 580;

    /** Builds an Outlook-compatible img tag with explicit width/height, left-aligned with margins. */
    private String emailImgTag(String dataUri, int maxW) {
        if (dataUri == null || dataUri.isBlank() || !dataUri.startsWith("data:image/")) return "";
        try {
            int commaIdx = dataUri.indexOf(',');
            if (commaIdx < 0) return "";
            byte[] bytes = java.util.Base64.getDecoder().decode(dataUri.substring(commaIdx + 1));
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (bi != null) {
                int w = bi.getWidth(), h = bi.getHeight();
                // Scale down if wider than max, never scale up
                if (w > maxW) {
                    h = Math.round((float) h * maxW / w);
                    w = maxW;
                }
                return "<img src=\"" + dataUri + "\" width=\"" + w + "\" height=\"" + h
                     + "\" style=\"display:block;margin:4px 0;border:1px solid #d1d5db;\">";
            }
        } catch (Exception ignored) {}
        // Fallback: no decoded dimensions, use style only
        return "<img src=\"" + dataUri + "\" style=\"display:block;margin:4px 0;"
             + "max-width:" + maxW + "px;height:auto;border:1px solid #d1d5db;\">";
    }

    public String generateEmail(UatRequest r) {
        if (UatMailModel.isStructured(r)) return renderHtml(UatMailModel.from(r, this::loadStepImage));
        return generateLegacyEmail(r);
    }

    /** Asunto del correo: el capturado, o "VoBo UAT – RFC {n} – {nombre}". */
    public String subjectFor(UatRequest r) {
        return UatMailModel.from(r, null).subject();
    }

    /** Resultado de la vista previa en vivo (no guarda historial). */
    public record Preview(String html, String text, String subject, List<String> warnings) {}

    public Preview preview(UatRequest r) {
        UatMailModel m = UatMailModel.from(r, this::loadStepImage);
        return new Preview(generateEmail(r), renderText(m), m.subject(), warnings(r, m));
    }

    /** Avisos suaves: nunca bloquean la generación. */
    private List<String> warnings(UatRequest r, UatMailModel m) {
        List<String> w = new java.util.ArrayList<>();
        String rfc = m.rfcLabel().replaceFirst("^RFC\\s*", "");
        if (rfc.isEmpty()) w.add("Falta el n\u00famero de RFC.");
        else if (!rfc.matches("\\d+")) w.add("El n\u00famero de RFC deber\u00eda ser num\u00e9rico.");
        if (m.rfcName().isEmpty()) w.add("Falta el nombre del cambio.");
        boolean hasReqs = !m.requirements().isEmpty() || !m.legacyRequirements().isEmpty() || !m.requirementsIntro().isEmpty()
                || !m.legacyRequirementImages().isEmpty();
        if (!hasReqs) w.add("No hay requerimientos.");
        return w;
    }

    public String generateEmailPlainText(UatRequest r) {
        return renderText(UatMailModel.from(r, this::loadStepImage));
    }

    private static final String FONT = "font-family:'Segoe UI',Calibri,Arial,sans-serif;font-size:13px;line-height:1.5;";

    private String p(String margin, String inner) {
        return "<p style=\"margin:" + margin + ";" + FONT + "\">" + inner + "</p>";
    }

    private String multiline(String text) {
        return esc(text.replace("\r\n", "\n").replace("\r", "\n").replaceAll("\n{3,}", "\n\n")).replace("\n", "<br>");
    }

    private String imgP(String dataUri, int maxW, String margin) {
        String tag = emailImgTag(dataUri, maxW);
        return tag.isEmpty() ? "" : "<p style=\"margin:" + margin + ";\">" + tag + "</p>";
    }

    private String renderHtml(UatMailModel m) {
        StringBuilder h = new StringBuilder();
        h.append(p("0 0 8px 0", esc(m.greeting())));

        StringBuilder intro = new StringBuilder("Solicito su apoyo para validar el VoBo del <b>");
        intro.append(esc(m.rfcLabel()));
        if (!m.rfcName().isEmpty()) intro.append(" &ndash; ").append(esc(m.rfcName()));
        intro.append("</b>");
        if (m.dateText() != null) intro.append(", programado para el ").append(esc(m.dateText()));
        intro.append(".");
        if (!m.attachment().isEmpty()) intro.append("<br>").append(esc(m.attachment()));
        h.append(p("0 0 8px 0", intro.toString()));
        if (!m.relatedRfcs().isEmpty())
            h.append(p("0 0 8px 0", "RFCs relacionados: " + esc(String.join(", ", m.relatedRfcs())) + "."));

        boolean hasReqs = !m.requirements().isEmpty() || !m.requirementsIntro().isEmpty()
                || !m.legacyRequirements().isEmpty() || !m.legacyRequirementImages().isEmpty();
        if (hasReqs) {
            h.append(p("16px 0 4px 0", "<b>Requerimientos:</b>"));
            if (!m.requirementsIntro().isEmpty()) h.append(p("0 0 8px 0", multiline(m.requirementsIntro())));
            if (!m.legacyRequirements().isEmpty()) h.append(p("0 0 8px 0", textToHtml(m.legacyRequirements())));
            for (String img : m.legacyRequirementImages()) h.append(imgP(img, EMAIL_IMG_MAX_WIDTH, "4px 0 10px 0"));
            for (UatMailModel.Req q : m.requirements()) {
                String title = q.title().isEmpty() ? q.number() + "." : q.number() + ". " + esc(q.title());
                h.append(p("10px 0 2px 0", "<b>" + title + "</b>"));
                if (!q.description().isEmpty()) h.append(p("0 0 3px 18px", multiline(q.description())));
                for (String b : q.bullets()) h.append(p("0 0 2px 18px", "&bull; " + esc(b)));
                if (!q.image().isEmpty()) h.append(imgP(q.image(), EMAIL_IMG_MAX_WIDTH - 18, "6px 0 10px 18px"));
            }
        }

        if (!m.examples().isEmpty() || !m.legacyBlocks().isEmpty()) {
            h.append(p("16px 0 4px 0", "<b>Ejemplos de validaci&oacute;n:</b>"));
            for (UatMailModel.Example e : m.examples()) {
                if (e.heading() != null) h.append(p("8px 0 2px 0", "<b>" + esc(e.heading()) + "</b>"));
                if (!e.pairs().isEmpty()) {
                    StringBuilder lines = new StringBuilder();
                    for (UatMailModel.KeyValue kv : e.pairs()) {
                        if (lines.length() > 0) lines.append("<br>");
                        if (kv.key().isEmpty()) lines.append(esc(kv.value()));
                        else if (kv.value().isEmpty()) lines.append(esc(kv.key()));
                        else lines.append(esc(kv.key())).append(": ").append(esc(kv.value()));
                    }
                    h.append(p("0 0 4px 0", lines.toString()));
                }
                if (!e.image().isEmpty()) h.append(imgP(e.image(), EMAIL_IMG_MAX_WIDTH, "6px 0 10px 0"));
            }
            for (UatBlock b : m.legacyBlocks()) {
                if (notBlank(b.getTexto())) h.append(p("0 0 8px 0", textToHtml(b.getTexto())));
                h.append(imgP(safeImgSrc(b.getImagenBase64()), EMAIL_IMG_MAX_WIDTH, "4px 0 10px 0"));
            }
        }

        if (!m.note().isEmpty()) {
            h.append(p("16px 0 4px 0", "<b>NOTA:</b>"));
            h.append(p("0 0 8px 0", textToHtml(m.note())));
        }

        h.append(p("16px 0 6px 0", "<b>Favor de validar con el m&oacute;dulo " + esc(m.module()) + ".</b>"));
        for (UatMailModel.Step s : m.steps()) {
            h.append(p("0 0 4px 0", s.number() + ". " + esc(s.text())));
            if (!s.image().isEmpty()) h.append(imgP(s.image(), s.maxWidth(), "4px 0 10px 18px"));
        }

        h.append(p("16px 0 0 0", esc(m.closing())));
        return h.toString();
    }

    private String renderText(UatMailModel m) {
        StringBuilder t = new StringBuilder();
        t.append(m.greeting()).append("\n\n");
        t.append("Solicito su apoyo para validar el VoBo del ").append(m.rfcLabel());
        if (!m.rfcName().isEmpty()) t.append(" \u2013 ").append(m.rfcName());
        if (m.dateText() != null) t.append(", programado para el ").append(m.dateText());
        t.append(".\n");
        if (!m.attachment().isEmpty()) t.append(m.attachment()).append("\n");
        if (!m.relatedRfcs().isEmpty()) t.append("\nRFCs relacionados: ").append(String.join(", ", m.relatedRfcs())).append(".\n");

        boolean hasReqs = !m.requirements().isEmpty() || !m.requirementsIntro().isEmpty() || !m.legacyRequirements().isEmpty();
        if (hasReqs) {
            t.append("\nRequerimientos:\n");
            if (!m.requirementsIntro().isEmpty()) t.append(m.requirementsIntro()).append("\n");
            if (!m.legacyRequirements().isEmpty()) t.append(htmlToText(m.legacyRequirements())).append("\n");
            for (UatMailModel.Req q : m.requirements()) {
                t.append("\n").append(q.number()).append(".");
                if (!q.title().isEmpty()) t.append(" ").append(q.title());
                t.append("\n");
                if (!q.description().isEmpty()) t.append("   ").append(q.description().replace("\n", "\n   ")).append("\n");
                for (String b : q.bullets()) t.append("   * ").append(b).append("\n");
            }
        }
        if (!m.examples().isEmpty() || !m.legacyBlocks().isEmpty()) {
            t.append("\nEjemplos de validaci\u00f3n:\n");
            for (UatMailModel.Example e : m.examples()) {
                if (e.heading() != null) t.append(e.heading()).append("\n");
                for (UatMailModel.KeyValue kv : e.pairs()) {
                    if (kv.key().isEmpty()) t.append(kv.value());
                    else if (kv.value().isEmpty()) t.append(kv.key());
                    else t.append(kv.key()).append(": ").append(kv.value());
                    t.append("\n");
                }
            }
            for (UatBlock b : m.legacyBlocks()) if (notBlank(b.getTexto())) t.append(htmlToText(b.getTexto())).append("\n");
        }
        if (!m.note().isEmpty()) t.append("\nNOTA:\n").append(htmlToText(m.note())).append("\n");
        t.append("\nFavor de validar con el m\u00f3dulo ").append(m.module()).append(".\n");
        for (UatMailModel.Step s : m.steps()) t.append(s.number()).append(". ").append(s.text()).append("\n");
        t.append("\n").append(m.closing()).append("\n");
        return t.toString();
    }

    /** Convierte el HTML del editor (ya acotado) a texto plano para las versiones sin formato. */
    private String htmlToText(String html) {
        if (html == null) return "";
        String s = HtmlSanitizer.sanitize(html)
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>", "\n")
                .replaceAll("(?i)<li[^>]*>", "* ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&amp;", "&");
        return s.replaceAll("\n{3,}", "\n\n").strip();
    }

    public String generateLegacyEmail(UatRequest r) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Calibri,Arial,sans-serif;font-size:11pt;color:#000;line-height:1.65;\">");

        // Saludo
        String saludo = notBlank(r.getSaludo()) ? r.getSaludo() : "Buenas tardes.";
        html.append("<p style=\"margin:0 0 14px 0;\"><b>").append(esc(saludo)).append("</b></p>");

        // Intro
        html.append("<p style=\"margin:0 0 16px 0;\">")
            .append("Solicito su apoyo para validar el VoBo del <b>")
            .append(esc(s(r.getRfcNumber()))).append(" &ndash; ")
            .append(esc(s(r.getRfcName()))).append("</b>.");
        if (notBlank(r.getAdjunto()))
            html.append("<br>").append(esc(r.getAdjunto()));
        html.append("</p>");

        // Requerimientos (caja con borde azul)
        List<String> reqImages = r.getRequerimientosImagenes() != null && !r.getRequerimientosImagenes().isEmpty()
                ? r.getRequerimientosImagenes()
                : (notBlank(r.getRequerimientosImagen()) ? List.of(r.getRequerimientosImagen()) : List.of());
        boolean hasReqImages = !reqImages.isEmpty();
        if (notBlank(r.getRequerimientos()) || hasReqImages) {
            html.append("<p style=\"margin:0 0 4px 0;\"><b>Requerimientos:</b></p>");
            if (notBlank(r.getRequerimientos())) {
                html.append("<div style=\"border:1px solid #c7d2fe;border-radius:6px;padding:12px 16px;"
                          + "background:#f0f4ff;margin-bottom:" + (hasReqImages ? "10px" : "16px") + ";font-size:10.5pt;\">")
                    .append(textToHtml(r.getRequerimientos()))
                    .append("</div>");
            }
            for (String imgUri : reqImages) {
                String safeSrc = safeImgSrc(imgUri);
                if (!safeSrc.isEmpty()) {
                    html.append("<p style=\"margin:0 0 12px 0;\">").append(emailImgTag(safeSrc, EMAIL_IMG_MAX_WIDTH)).append("</p>");
                }
            }
        }

        // Bloques libres (texto + imagen de evidencia)
        List<UatBlock> blocks = orEmpty(r.getBlocks()).stream()
                .filter(b -> notBlank(b.getTexto()) || notBlank(b.getImagenBase64()))
                .collect(java.util.stream.Collectors.toList());
        if (!blocks.isEmpty()) {
            html.append("<p style=\"margin:16px 0 6px 0;\"><b>Ejemplos de validaci&oacute;n:</b></p>");
        }
        for (UatBlock block : blocks) {
            if (notBlank(block.getTexto())) {
                html.append("<p style=\"margin:0 0 10px 0;\">")
                    .append(textToHtml(block.getTexto()))
                    .append("</p>");
            }
            if (notBlank(block.getImagenBase64())) {
                String safeSrc = safeImgSrc(block.getImagenBase64());
                if (!safeSrc.isEmpty()) {
                    html.append("<p style=\"margin:0 0 16px 0;\">").append(emailImgTag(safeSrc, EMAIL_IMG_MAX_WIDTH)).append("</p>");
                }
            }
        }

        // NOTA
        if (notBlank(r.getNota())) {
            html.append("<p style=\"margin:16px 0 6px 0;\"><b>NOTA:</b></p>")
                .append("<p style=\"margin:0 0 14px 0;\">")
                .append(textToHtml(r.getNota()))
                .append("</p>");
        }

        // Instrucciones fijas — imágenes pre-cargadas desde /static/images/uat/
        html.append("<p style=\"margin:18px 0 4px 0;\"><b>Favor de Validar con el m&oacute;dulo Servicios_UAT.</b></p>")
            .append("<ol style=\"margin:0 0 14px 0;padding-left:28px;\">");
        appendStep(html, "Entrar a la carpeta M\u00f3dulos SFYC UAT",       loadStepImage(1), 420);
        appendStep(html, "Ejecutar el m\u00f3dulo Servicios_UAT",            loadStepImage(2), 260);
        appendStep(html, "En el campo Servidor seleccionar MEGANG-384",    loadStepImage(3), 260);
        html.append("</ol>");

        // Cierre
        String cierre = notBlank(r.getCierre()) ? r.getCierre() : "Quedo pendiente por cualquier duda o comentario.";
        html.append("<p style=\"margin:18px 0 0 0;\">").append(esc(cierre)).append("</p>");

        html.append("</div>");
        return html.toString();
    }

    // â”€â”€ PDF â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public byte[] generatePdf(UatRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 72, 62);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        final String headerLabel = "VoBo UAT  \u2014  " + s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 8, Font.NORMAL, C_MUTED);
                    cb.setLineWidth(0.5f); cb.setColorStroke(C_BORDER);
                    cb.moveTo(d.left(), d.bottom()-4); cb.lineTo(d.right(), d.bottom()-4); cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("P\u00e1gina "+w.getPageNumber(), f), d.right(), d.bottom()-16, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,  new Phrase(headerLabel, f), d.left(), d.bottom()-16, 0);
                    cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top()+8); cb.lineTo(d.right(), d.top()+8); cb.stroke();
                } catch (Exception ignored) {}
            }
        });
        doc.open();

        Font titleF = new Font(Font.HELVETICA, 20, Font.BOLD,   C_TEXT);
        Font subF   = new Font(Font.HELVETICA, 10, Font.ITALIC, C_MUTED);
        Font bodyF  = new Font(Font.HELVETICA, 10, Font.NORMAL, C_TEXT);
        Font boldF  = new Font(Font.HELVETICA, 10, Font.BOLD,   C_TEXT);
        Font noteF  = new Font(Font.HELVETICA, 10, Font.BOLD,   new Color(220, 38, 38));
        Font mutF   = new Font(Font.HELVETICA,  9, Font.ITALIC, C_MUTED);

        // TÃ­tulo
        Paragraph title = new Paragraph("VoBo UAT \u2013 Solicitud de Validaci\u00f3n", titleF);
        title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(4); doc.add(title);
        Paragraph sub = new Paragraph(s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName()), subF);
        sub.setAlignment(Element.ALIGN_CENTER); sub.setSpacingAfter(2); doc.add(sub);
        hrule(doc, C_PRIMARY, 1.5f);

        if (UatMailModel.isStructured(r)) {
            writePdfStructured(doc, writer, UatMailModel.from(r, this::loadStepImage), boldF, bodyF);
            doc.close();
            return bos.toByteArray();
        }

        String saludo = notBlank(r.getSaludo()) ? r.getSaludo() : "Buenas tardes.";
        addPara(doc, saludo, boldF, 14);

        // Intro
        String intro = "Solicito su apoyo para validar el VoBo del " + s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName()) + ".";
        addPara(doc, intro, bodyF, 4);
        if (notBlank(r.getAdjunto()))
            addPara(doc, r.getAdjunto(), bodyF, 12);

        // Requerimientos
        if (notBlank(r.getRequerimientos()) || notBlank(r.getRequerimientosImagen())) {
            addPara(doc, "Requerimientos:", boldF, 12);
            if (notBlank(r.getRequerimientos())) {
                Paragraph reqP = new Paragraph(r.getRequerimientos().trim(), bodyF);
                reqP.setSpacingAfter(notBlank(r.getRequerimientosImagen()) ? 4 : 12);
                reqP.setIndentationLeft(12);
                doc.add(reqP);
            }
            if (notBlank(r.getRequerimientosImagen())) {
                float pageW = doc.right() - doc.left();
                addSmartImage(doc, writer, r.getRequerimientosImagen(), pageW, 320, 12);
            }
        }

        // Bloques
        List<UatBlock> pdfBlocks = orEmpty(r.getBlocks());
        if (!pdfBlocks.isEmpty()) {
            addPara(doc, "Ejemplos de validaci\u00f3n:", boldF, 8);
        }
        int evidenciaNum = 1;
        for (UatBlock block : pdfBlocks) {
            if (notBlank(block.getTexto())) {
                addPara(doc, block.getTexto().trim(), bodyF, 8);
            }
            if (notBlank(block.getImagenBase64())) {
                float pageW = doc.right() - doc.left();
                addSmartImage(doc, writer, block.getImagenBase64(), pageW, 360, 0);
                evidenciaNum++;
            }
        }

        // NOTA
        if (notBlank(r.getNota())) {
            addPara(doc, "NOTA:", noteF, 16);
            addPara(doc, r.getNota().trim(), bodyF, 12);
        }

        // Instrucciones fijas — imágenes pre-cargadas desde /static/images/uat/
        addPara(doc, "Favor de Validar con el m\u00f3dulo Servicios_UAT.", boldF, 4);
        String[] stepTexts  = {"Entrar a la carpeta M\u00f3dulos SFYC UAT", "Ejecutar el m\u00f3dulo Servicios_UAT", "En el campo Servidor seleccionar MEGANG-384"};
        String[] stepImages = {loadStepImage(1), loadStepImage(2), loadStepImage(3)};
        for (int i = 0; i < 3; i++) {
            Paragraph sp = new Paragraph((i + 1) + ". " + stepTexts[i], bodyF);
            sp.setIndentationLeft(20); sp.setSpacingAfter(notBlank(stepImages[i]) ? 4 : 3); doc.add(sp);
            if (notBlank(stepImages[i])) {
                float pageW = doc.right() - doc.left();
                addSmartImage(doc, writer, stepImages[i], pageW, 280, 30);
            }
        }

        // Cierre
        String cierre = notBlank(r.getCierre()) ? r.getCierre() : "Quedo pendiente por cualquier duda o comentario.";
        addPara(doc, cierre, bodyF, 0);

        doc.close();
        return bos.toByteArray();
    }

    // â”€â”€ Markdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public byte[] generateMarkdown(UatRequest r) {
        if (UatMailModel.isStructured(r)) return renderMarkdown(UatMailModel.from(r, this::loadStepImage));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();
        md.append("# VoBo UAT \u2013 ").append(s(r.getRfcNumber())).append(" \u2013 ").append(s(r.getRfcName())).append("\n\n");
        md.append("> Generado el ").append(now).append("\n\n");

        String saludo = notBlank(r.getSaludo()) ? r.getSaludo() : "Buenas tardes.";
        md.append("**").append(saludo).append("**\n\n");

        md.append("Solicito su apoyo para validar el VoBo del **")
          .append(s(r.getRfcNumber())).append(" \u2013 ").append(s(r.getRfcName())).append("**.");
        if (notBlank(r.getAdjunto()))
            md.append("  \n").append(r.getAdjunto().trim());
        md.append("\n\n");

        if (notBlank(r.getRequerimientos()) || notBlank(r.getRequerimientosImagen())) {
            md.append("**Requerimientos:**\n\n");
            if (notBlank(r.getRequerimientos())) {
                md.append("> ").append(r.getRequerimientos().trim().replace("\n", "\n> ")).append("\n\n");
            }
            if (notBlank(r.getRequerimientosImagen())) md.append("_[Imagen de requerimientos adjunta]_\n\n");
        }

        List<UatBlock> mdBlocks = orEmpty(r.getBlocks());
        if (!mdBlocks.isEmpty()) {
            md.append("**Ejemplos de validaci\u00f3n:**\n\n");
        }
        int evidenciaNum = 1;
        for (UatBlock block : mdBlocks) {
            if (notBlank(block.getTexto()))
                md.append(block.getTexto().trim()).append("\n\n");
            if (notBlank(block.getImagenBase64()))
                md.append("_[Imagen de evidencia ").append(evidenciaNum++).append("]_\n\n");
        }

        if (notBlank(r.getNota())) {
            md.append("**NOTA:**\n\n").append(r.getNota().trim()).append("\n\n");
        }

        md.append("**Favor de Validar con el m\u00f3dulo Servicios_UAT.**\n\n");
        String[] mdSteps    = {"Entrar a la carpeta M\u00f3dulos SFYC UAT", "Ejecutar el m\u00f3dulo Servicios_UAT", "En el campo Servidor seleccionar MEGANG-384"};
        String[] mdStepImgs = {loadStepImage(1), loadStepImage(2), loadStepImage(3)};
        for (int i = 0; i < 3; i++) {
            md.append(i + 1).append(". ").append(mdSteps[i]).append("\n");
            if (notBlank(mdStepImgs[i])) md.append("  _[Imagen del paso ").append(i + 1).append("]_\n");
        }
        md.append("\n");

        String cierre = notBlank(r.getCierre()) ? r.getCierre() : "Quedo pendiente por cualquier duda o comentario.";
        md.append(cierre).append("\n\n");
        md.append("\n---\n\n_Generado el ").append(now).append(" mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void writePdfStructured(Document doc, PdfWriter writer, UatMailModel m, Font boldF, Font bodyF)
            throws DocumentException {
        float pageW = doc.right() - doc.left();
        addPara(doc, m.greeting(), bodyF, 10);
        StringBuilder intro = new StringBuilder("Solicito su apoyo para validar el VoBo del ").append(m.rfcLabel());
        if (!m.rfcName().isEmpty()) intro.append(" \u2013 ").append(m.rfcName());
        if (m.dateText() != null) intro.append(", programado para el ").append(m.dateText());
        intro.append(".");
        if (!m.attachment().isEmpty()) intro.append("\n").append(m.attachment());
        addPara(doc, intro.toString(), bodyF, 8);
        if (!m.relatedRfcs().isEmpty()) addPara(doc, "RFCs relacionados: " + String.join(", ", m.relatedRfcs()) + ".", bodyF, 8);

        boolean hasReqs = !m.requirements().isEmpty() || !m.requirementsIntro().isEmpty()
                || !m.legacyRequirements().isEmpty() || !m.legacyRequirementImages().isEmpty();
        if (hasReqs) {
            addPara(doc, "Requerimientos:", boldF, 6);
            if (!m.requirementsIntro().isEmpty()) addPara(doc, m.requirementsIntro(), bodyF, 8);
            if (!m.legacyRequirements().isEmpty()) addPara(doc, htmlToText(m.legacyRequirements()), bodyF, 8);
            for (String img : m.legacyRequirementImages()) addSmartImage(doc, writer, img, pageW, 320, 0);
            for (UatMailModel.Req q : m.requirements()) {
                addPara(doc, q.number() + "." + (q.title().isEmpty() ? "" : " " + q.title()), boldF, 2);
                if (!q.description().isEmpty()) {
                    Paragraph d = new Paragraph(q.description(), bodyF);
                    d.setIndentationLeft(14); d.setSpacingAfter(2); doc.add(d);
                }
                for (String b : q.bullets()) {
                    Paragraph bp = new Paragraph("\u2022 " + b, bodyF);
                    bp.setIndentationLeft(14); bp.setSpacingAfter(1); doc.add(bp);
                }
                if (!q.image().isEmpty()) addSmartImage(doc, writer, q.image(), pageW, 320, 14);
                else addPara(doc, "", bodyF, 4);
            }
        }

        if (!m.examples().isEmpty() || !m.legacyBlocks().isEmpty()) {
            addPara(doc, "Ejemplos de validaci\u00f3n:", boldF, 6);
            for (UatMailModel.Example e : m.examples()) {
                if (e.heading() != null) addPara(doc, e.heading(), boldF, 2);
                StringBuilder lines = new StringBuilder();
                for (UatMailModel.KeyValue kv : e.pairs()) {
                    if (lines.length() > 0) lines.append("\n");
                    lines.append(kv.key().isEmpty() ? kv.value() : kv.value().isEmpty() ? kv.key() : kv.key() + ": " + kv.value());
                }
                if (lines.length() > 0) addPara(doc, lines.toString(), bodyF, 6);
                if (!e.image().isEmpty()) addSmartImage(doc, writer, e.image(), pageW, 320, 0);
            }
            for (UatBlock b : m.legacyBlocks()) {
                if (notBlank(b.getTexto())) addPara(doc, htmlToText(b.getTexto()), bodyF, 6);
                String img = safeImgSrc(b.getImagenBase64());
                if (!img.isEmpty()) addSmartImage(doc, writer, img, pageW, 320, 0);
            }
        }

        if (!m.note().isEmpty()) {
            addPara(doc, "NOTA:", boldF, 4);
            addPara(doc, htmlToText(m.note()), bodyF, 8);
        }

        addPara(doc, "Favor de validar con el m\u00f3dulo " + m.module() + ".", boldF, 6);
        for (UatMailModel.Step s : m.steps()) {
            addPara(doc, s.number() + ". " + s.text(), bodyF, 3);
            if (!s.image().isEmpty()) addSmartImage(doc, writer, s.image(), pageW, 200, 14);
        }
        addPara(doc, m.closing(), bodyF, 0);
    }

    private byte[] renderMarkdown(UatMailModel m) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();
        md.append("# ").append(m.subject()).append("\n\n> Generado el ").append(now).append("\n\n");
        md.append(renderText(m).replaceAll("(?m)^(Requerimientos:|Ejemplos de validaci\u00f3n:|NOTA:|Favor de validar con el m\u00f3dulo .*)$", "**$1**")
                .replace("\n", "  \n"));
        md.append("\n---\n\n_Generado el ").append(now).append(" mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendStep(StringBuilder html, String text, String imageB64, int maxWidthPx) {
        html.append("<li style=\"margin-bottom:8px;\">").append(text);
        if (notBlank(imageB64)) {
            html.append("<br><img src=\"").append(imageB64)
                .append("\" style=\"max-width:").append(maxWidthPx).append("px;height:auto;"
                      + "margin-top:6px;display:block;\">" );
        }
        html.append("</li>");
    }

    private String s(String v)          { return v != null && !v.isBlank() ? v.trim() : ""; }
    private boolean notBlank(String v)  { return v != null && !v.isBlank(); }
    private <T> List<T> orEmpty(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private String esc(String v) {
        if (v == null || v.isBlank()) return "";
        return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    /**
     * Converts text to email-safe HTML.
     * If the input already contains HTML tags (from a rich editor), it passes through
     * with only dangerous tags stripped. Otherwise, escapes and converts newlines.
     */
    private String textToHtml(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.strip();
        // Detect if content is already HTML (contains common formatting tags)
        if (t.contains("<ul>") || t.contains("<ol>") || t.contains("<li>")
                || t.contains("<b>") || t.contains("<strong>") || t.contains("<em>")
                || t.contains("<br>") || t.contains("<p>") || t.contains("<br/>")) {
            // Already HTML — lista blanca de etiquetas y atributos
            return HtmlSanitizer.sanitize(t);
        }
        // Plain text — escape and convert newlines
        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replaceAll("\n{3,}", "\n\n");
        t = esc(t);
        return t.replace("\n", "<br>");
    }

    /** Solo imágenes raster en data URI válidas; evita inyección vía el atributo src. */
    private String safeImgSrc(String v) {
        return HtmlSanitizer.safeImageDataUri(v);
    }
    private void addPara(Document doc, String text, Font font, float spacingAfter) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(spacingAfter);
        doc.add(p);
    }
    private void hrule(Document doc, Color color, float lw) throws DocumentException {
        LineSeparator ls = new LineSeparator(lw, 100f, color, Element.ALIGN_CENTER, -2f);
        Paragraph p = new Paragraph(new Chunk(ls));
        p.setSpacingBefore(4); p.setSpacingAfter(12);
        doc.add(p);
    }
    /** Smart image scaling: fits within available page width and max height, preserves aspect ratio. */
    private void addSmartImage(Document doc, PdfWriter writer, String base64, float maxW, float maxH, float indent) {
        try {
            String b64 = base64;
            if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);
            byte[] imgBytes = Base64.getDecoder().decode(b64.trim());
            Image img = Image.getInstance(imgBytes);
            float origW = img.getWidth(), origH = img.getHeight();
            float availW = maxW - indent;
            float scale = Math.min(availW / origW, maxH / origH);
            if (scale > 1f) scale = 1f;
            img.scaleAbsolute(origW * scale, origH * scale);
            // Page break if not enough space
            float spaceNeeded = origH * scale + 20;
            float vertPos = writer.getVerticalPosition(false);
            if (vertPos - spaceNeeded < doc.bottom() + 20) doc.newPage();
            if (indent > 0) img.setIndentationLeft(indent);
            img.setSpacingAfter(10);
            doc.add(img);
        } catch (Exception ex) {
            log.warn("No se pudo incrustar imagen en PDF: {}", ex.getMessage());
            try { addPara(doc, "[Imagen no disponible]",
                    new Font(Font.HELVETICA, 9, Font.ITALIC, C_MUTED), 8); } catch (Exception ignored) {}
        }
    }
}
