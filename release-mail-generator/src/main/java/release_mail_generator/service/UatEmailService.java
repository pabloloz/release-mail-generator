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

    // â”€â”€ Correo HTML â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public String generateEmail(UatRequest r) {
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
        if (notBlank(r.getRequerimientos()) || notBlank(r.getRequerimientosImagen())) {
            html.append("<p style=\"margin:0 0 4px 0;\"><b>Requerimientos:</b></p>");
            if (notBlank(r.getRequerimientos())) {
                html.append("<div style=\"border:1px solid #c7d2fe;border-radius:6px;padding:12px 16px;"
                          + "background:#f0f4ff;margin-bottom:" + (notBlank(r.getRequerimientosImagen()) ? "10px" : "16px") + ";font-size:10.5pt;\">")
                    .append(esc(r.getRequerimientos()).replace("\n", "<br>"))
                    .append("</div>");
            }
            if (notBlank(r.getRequerimientosImagen())) {
                String safeSrc = safeImgSrc(r.getRequerimientosImagen());
                if (!safeSrc.isEmpty()) {
                html.append("<p style=\"margin:0 0 16px 0;\">")
                    .append("<img src=\"").append(safeSrc)
                    .append("\" style=\"max-width:420px;height:auto;display:block;\">")
                    .append("</p>");
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
                    .append(esc(block.getTexto()).replace("\n", "<br>"))
                    .append("</p>");
            }
            if (notBlank(block.getImagenBase64())) {
                String safeSrc = safeImgSrc(block.getImagenBase64());
                if (!safeSrc.isEmpty()) {
                html.append("<p style=\"margin:0 0 16px 0;\">")
                    .append("<img src=\"").append(safeSrc)
                    .append("\" style=\"max-width:420px;height:auto;display:block;\">")
                    .append("</p>");
                }
            }
        }

        // NOTA
        if (notBlank(r.getNota())) {
            html.append("<p style=\"margin:16px 0 6px 0;\"><b>NOTA:</b></p>")
                .append("<p style=\"margin:0 0 14px 0;\">")
                .append(esc(r.getNota()).replace("\n", "<br>"))
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
    /** Only allow data:image/ URIs to prevent XSS via img src. */
    private String safeImgSrc(String v) {
        if (v == null || v.isBlank()) return "";
        return v.startsWith("data:image/") ? v : "";
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
