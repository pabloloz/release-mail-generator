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
import org.springframework.stereotype.Service;

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

    private static final Color C_PRIMARY = new Color(37, 99, 235);
    private static final Color C_DARK    = new Color(30, 58, 138);
    private static final Color C_BORDER  = new Color(226, 232, 240);
    private static final Color C_BG_REQ  = new Color(239, 246, 255);
    private static final Color C_TEXT    = new Color(15, 23, 42);
    private static final Color C_MUTED   = new Color(100, 116, 139);

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
        if (notBlank(r.getRequerimientos())) {
            html.append("<p style=\"margin:0 0 4px 0;\"><b>Requerimientos:</b></p>")
                .append("<div style=\"border:1px solid #c7d2fe;border-radius:6px;padding:12px 16px;"
                      + "background:#f0f4ff;margin-bottom:16px;font-size:10.5pt;\">")
                .append(esc(r.getRequerimientos()).replace("\n", "<br>"))
                .append("</div>");
        }

        // Bloques libres (texto + imagen de evidencia)
        for (UatBlock block : orEmpty(r.getBlocks())) {
            if (notBlank(block.getTexto())) {
                html.append("<p style=\"margin:0 0 10px 0;\">")
                    .append(esc(block.getTexto()).replace("\n", "<br>"))
                    .append("</p>");
            }
            if (notBlank(block.getImagenBase64())) {
                html.append("<p style=\"margin:0 0 16px 0;\">")
                    .append("<img src=\"").append(block.getImagenBase64())
                    .append("\" style=\"max-width:100%;border:1px solid #e2e8f0;border-radius:4px;\">")
                    .append("</p>");
            }
        }

        // NOTA
        if (notBlank(r.getNota())) {
            html.append("<p style=\"margin:16px 0 6px 0;\"><b>NOTA:</b></p>")
                .append("<p style=\"margin:0 0 14px 0;\">")
                .append(esc(r.getNota()).replace("\n", "<br>"))
                .append("</p>");
        }

        // Instrucciones fijas de validación
        html.append("<p style=\"margin:18px 0 4px 0;\"><b>Favor de Validar con el m&oacute;dulo Servicios_UAT.</b></p>")
            .append("<ol style=\"margin:0 0 14px 0;padding-left:28px;\">")
            .append("<li style=\"margin-bottom:3px;\">Entrar a la carpeta M&oacute;dulos SFYC UAT</li>")
            .append("<li style=\"margin-bottom:3px;\">Ejecutar el m&oacute;dulo Servicios_UAT</li>")
            .append("<li style=\"margin-bottom:3px;\">En el campo Servidor seleccionar MEGANG-384</li>")
            .append("</ol>");

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
        if (notBlank(r.getRequerimientos())) {
            addPara(doc, "Requerimientos:", boldF, 12);
            Paragraph reqP = new Paragraph(r.getRequerimientos().trim(), bodyF);
            reqP.setSpacingAfter(12);
            reqP.setIndentationLeft(12);
            doc.add(reqP);
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
                try {
                    String b64 = block.getImagenBase64();
                    if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);
                    byte[] imgBytes = Base64.getDecoder().decode(b64.trim());
                    Image img = Image.getInstance(imgBytes);
                    img.scaleToFit(450, 320);
                    img.setSpacingBefore(6);
                    img.setSpacingAfter(12);
                    doc.add(img);
                } catch (Exception ex) {
                    addPara(doc, "[Imagen de evidencia " + evidenciaNum + " no disponible en PDF]", mutF, 8);
                }
                evidenciaNum++;
            }
        }

        // NOTA
        if (notBlank(r.getNota())) {
            addPara(doc, "NOTA:", noteF, 16);
            addPara(doc, r.getNota().trim(), bodyF, 12);
        }

        // Instrucciones fijas
        addPara(doc, "Favor de Validar con el m\u00f3dulo Servicios_UAT.", boldF, 4);
        for (String step : new String[]{"Entrar a la carpeta M\u00f3dulos SFYC UAT","Ejecutar el m\u00f3dulo Servicios_UAT","En el campo Servidor seleccionar MEGANG-384"}) {
            Paragraph sp = new Paragraph(step, bodyF);
            sp.setIndentationLeft(20); sp.setSpacingAfter(3); doc.add(sp);
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

        if (notBlank(r.getRequerimientos())) {
            md.append("**Requerimientos:**\n\n");
            md.append("> ").append(r.getRequerimientos().trim().replace("\n", "\n> ")).append("\n\n");
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

        md.append("**Favor de Validar con el m\u00f3dulo Servicios_UAT.**\n\n")
          .append("1. Entrar a la carpeta M\u00f3dulos SFYC UAT\n")
          .append("2. Ejecutar el m\u00f3dulo Servicios_UAT\n")
          .append("3. En el campo Servidor seleccionar MEGANG-384\n\n");

        String cierre = notBlank(r.getCierre()) ? r.getCierre() : "Quedo pendiente por cualquier duda o comentario.";
        md.append(cierre).append("\n\n");
        md.append("\n---\n\n_Generado el ").append(now).append(" mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String s(String v)          { return v != null && !v.isBlank() ? v.trim() : ""; }
    private boolean notBlank(String v)  { return v != null && !v.isBlank(); }
    private <T> List<T> orEmpty(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private String esc(String v) {
        if (v == null || v.isBlank()) return "";
        return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
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
}
