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
import release_mail_generator.model.UatRequest;
import release_mail_generator.model.UatRequirement;
import release_mail_generator.model.UatTestCaseItem;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class UatEmailService {

    private static final Color C_PRIMARY = new Color(37, 99, 235);
    private static final Color C_DARK    = new Color(30, 58, 138);
    private static final Color C_BORDER  = new Color(226, 232, 240);
    private static final Color C_BG_ALT  = new Color(248, 250, 252);
    private static final Color C_TEXT    = new Color(15, 23, 42);
    private static final Color C_MUTED   = new Color(100, 116, 139);

    public String generateEmail(UatRequest r) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Calibri,Arial,sans-serif;font-size:11pt;color:#000;line-height:1.6;\">");
        html.append("<p style=\"margin:0 0 10px 0;\"><b>Hola, buen d\u00eda team:</b></p>");
        html.append("<p style=\"margin:0 0 14px 0;\">")
            .append("Solicito de su apoyo con la revisi\u00f3n y VoBo del ")
            .append("<b>").append(esc(s(r.getRfcNumber()))).append(" \u2013 ")
            .append(esc(s(r.getRfcName()))).append("</b>");
        if (notBlank(r.getModuleName()))
            html.append(", en el m\u00f3dulo de <b>").append(esc(r.getModuleName())).append("</b>");
        html.append(".");
        if (notBlank(r.getReleaseJira()))
            html.append(" Release: <a href=\"").append(esc(r.getReleaseJira()))
                .append("\" style=\"color:#0563C1;\">").append(esc(r.getReleaseJira())).append("</a>");
        List<String> atts = filtered(r.getAttachmentNames());
        if (!atts.isEmpty())
            html.append("<br>Adjunto: <b>").append(esc(String.join(", ", atts))).append("</b>");
        html.append("</p>");

        if (notBlank(r.getAlcance())) {
            html.append(sHdr("\uD83D\uDDD2\uFE0F", "Alcance de la validaci\u00f3n"));
            html.append("<p style=\"margin:0 0 12px 0;\">").append(esc(r.getAlcance()).replace("\n","<br>")).append("</p>");
        }

        List<String> sc = filtered(r.getScenarios());
        if (!sc.isEmpty()) {
            html.append(sHdr("\uD83D\uDD35", "Escenarios a validar"));
            html.append("<p style=\"margin:0 0 6px 0;\">Se solicita su apoyo para validar:</p>");
            html.append("<ul style=\"margin:0 0 12px 0;padding-left:24px;\">");
            sc.forEach(s -> html.append("<li style=\"margin-bottom:4px;\">").append(esc(s)).append("</li>"));
            html.append("</ul>");
        }

        List<UatRequirement> td = orEmpty(r.getTestData()).stream()
                .filter(d -> d != null && (notBlank(d.getSucursal()) || notBlank(d.getSuscriptor()))).toList();
        if (!td.isEmpty()) {
            html.append(sHdr("\u26A0\uFE0F", "Nota \u2014 Datos para la validaci\u00f3n"));
            html.append("<p style=\"margin:0 0 8px 0;\">Para la validaci\u00f3n, se proporcionan los siguientes datos:</p>");
            html.append("<ul style=\"margin:0 0 12px 0;padding-left:24px;\">");
            for (UatRequirement row : td) {
                html.append("<li style=\"margin-bottom:5px;\">");
                if (notBlank(row.getSucursal()))    html.append("<b>Sucursal:</b> ").append(esc(row.getSucursal())).append(" &nbsp;");
                if (notBlank(row.getSuscriptor()))  html.append("<b>Suscriptor:</b> ").append(esc(row.getSuscriptor())).append(" &nbsp;");
                if (notBlank(row.getServicio()))    html.append("<b>Servicio:</b> ").append(esc(row.getServicio()));
                if (notBlank(row.getObservaciones())) html.append("<br><span style=\"color:#555;\">").append(esc(row.getObservaciones())).append("</span>");
                html.append("</li>");
            }
            html.append("</ul>");
        }

        List<UatTestCaseItem> vc = orEmpty(r.getValidatedCases()).stream()
                .filter(c -> c != null && (notBlank(c.getSucursal()) || notBlank(c.getSuscriptor()))).toList();
        if (!vc.isEmpty()) {
            html.append(sHdr("\u2705", "Casos validados"));
            html.append("<table style=\"border-collapse:collapse;width:100%;font-size:10pt;margin-bottom:12px;\">");
            html.append("<thead><tr style=\"background:#2563eb;color:white;\">");
            for (String h : new String[]{"Sucursal","Suscriptor","Servicio","Resultado"})
                html.append("<th style=\"padding:7px 10px;text-align:left;border:1px solid #1d4ed8;font-weight:600;\">").append(h).append("</th>");
            html.append("</tr></thead><tbody>");
            for (int i = 0; i < vc.size(); i++) {
                UatTestCaseItem c = vc.get(i);
                String bg = i % 2 == 0 ? "white" : "#f8fafc";
                html.append("<tr style=\"background:").append(bg).append(";\">");
                for (String v : new String[]{c.getSucursal(),c.getSuscriptor(),c.getServicio(),c.getResultado()})
                    html.append("<td style=\"padding:6px 10px;border:1px solid #e2e8f0;vertical-align:top;\">").append(esc(s(v))).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }

        List<String> steps = filtered(r.getExecutionSteps());
        if (!steps.isEmpty()) {
            html.append(sHdr("\u2699\uFE0F", "Pasos de ejecuci\u00f3n"));
            html.append("<ol style=\"margin:0 0 12px 0;padding-left:24px;\">");
            steps.forEach(st -> html.append("<li style=\"margin-bottom:6px;\">").append(esc(st)).append("</li>"));
            html.append("</ol>");
        }

        html.append("<p style=\"margin:14px 0 4px 0;\">Quedamos al pendiente de cualquier duda, comentario o casos adicionales que requieran validar.</p>");
        html.append("<p style=\"margin:6px 0 0 0;\"><b>Muchas gracias.</b></p>");
        html.append("</div>");
        return html.toString();
    }

    private String sHdr(String emoji, String title) {
        return "<p style=\"margin:18px 0 8px 0;padding:8px 14px;background:#eff6ff;"
             + "border-left:4px solid #2563eb;font-size:11.5pt;font-weight:bold;color:#1e3a8a;\">"
             + emoji + "&nbsp;" + title + "</p>";
    }

    public byte[] generatePdf(UatRequest r) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 72, 62);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        final String rfcLabel = "VoBo UAT  \u2014  " + s(r.getRfcNumber()) + " \u2013 " + s(r.getRfcName());
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = new Font(Font.HELVETICA, 8, Font.NORMAL, C_MUTED);
                    cb.setLineWidth(0.5f); cb.setColorStroke(C_BORDER);
                    cb.moveTo(d.left(), d.bottom()-4); cb.lineTo(d.right(), d.bottom()-4); cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("P\u00e1gina "+w.getPageNumber(), f), d.right(), d.bottom()-16, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,  new Phrase(rfcLabel, f), d.left(), d.bottom()-16, 0);
                    cb.setColorStroke(C_PRIMARY);
                    cb.moveTo(d.left(), d.top()+8); cb.lineTo(d.right(), d.top()+8); cb.stroke();
                } catch (Exception ignored) {}
            }
        });
        doc.open();
        Font titleF = new Font(Font.HELVETICA,20,Font.BOLD,C_TEXT);
        Font subF   = new Font(Font.HELVETICA,10,Font.ITALIC,C_MUTED);
        Font secF   = new Font(Font.HELVETICA,12,Font.BOLD,C_PRIMARY);
        Font lblF   = new Font(Font.HELVETICA, 9,Font.BOLD,new Color(55,65,81));
        Font valF   = new Font(Font.HELVETICA,10,Font.NORMAL,C_TEXT);
        Font thF    = new Font(Font.HELVETICA, 9,Font.BOLD,Color.WHITE);
        Font tbF    = new Font(Font.HELVETICA, 9,Font.NORMAL,C_TEXT);
        Font mutF   = new Font(Font.HELVETICA, 9,Font.ITALIC,C_MUTED);

        Paragraph t = new Paragraph("VoBo UAT \u2013 Solicitud de Validaci\u00f3n", titleF);
        t.setAlignment(Element.ALIGN_CENTER); t.setSpacingAfter(4); doc.add(t);
        Paragraph sub = new Paragraph(s(r.getRfcNumber())+" \u2013 "+s(r.getRfcName()), subF);
        sub.setAlignment(Element.ALIGN_CENTER); sub.setSpacingAfter(2); doc.add(sub);
        hrule(doc, C_PRIMARY, 1.5f);

        addSec(doc, "1. Informaci\u00f3n General", secF);
        PdfPTable info = new PdfPTable(new float[]{1.2f,2.5f,1.2f,2.5f});
        info.setWidthPercentage(100); info.setSpacingBefore(4); info.setSpacingAfter(12);
        addIP(info,"RFC:",s(r.getRfcNumber()),lblF,valF);
        addIP(info,"Nombre:",s(r.getRfcName()),lblF,valF);
        addIP(info,"M\u00f3dulo:",s(r.getModuleName()),lblF,valF);
        addIP(info,"Ambiente:",s(r.getEnvironment()),lblF,valF);
        if (notBlank(r.getReleaseJira())) addIP(info,"Jira:",s(r.getReleaseJira()),lblF,valF);
        doc.add(info);

        if (notBlank(r.getAlcance())) { addSec(doc,"2. Alcance",secF); addBT(doc,r.getAlcance(),valF,mutF); }

        List<String> sc = filtered(r.getScenarios());
        if (!sc.isEmpty()) {
            addSec(doc,"3. Escenarios",secF);
            sc.forEach(item -> { try { Paragraph p=new Paragraph("\u2022 "+item,valF); p.setIndentationLeft(12); p.setSpacingAfter(3); doc.add(p); } catch(Exception e){} });
        }

        List<UatRequirement> td = orEmpty(r.getTestData()).stream()
                .filter(d->d!=null&&(notBlank(d.getSucursal())||notBlank(d.getSuscriptor()))).toList();
        if (!td.isEmpty()) {
            addSec(doc,"4. Datos para Pruebas",secF);
            PdfPTable tt=new PdfPTable(new float[]{1.2f,1.5f,1.8f,2.5f});
            tt.setWidthPercentage(100); tt.setSpacingBefore(4); tt.setSpacingAfter(12);
            addTH(tt,new String[]{"Sucursal","Suscriptor","Servicio","Observaciones"},thF);
            for(int i=0;i<td.size();i++){UatRequirement row=td.get(i);Color bg=i%2==0?Color.WHITE:C_BG_ALT;tt.addCell(tc(s(row.getSucursal()),tbF,bg));tt.addCell(tc(s(row.getSuscriptor()),tbF,bg));tt.addCell(tc(s(row.getServicio()),tbF,bg));tt.addCell(tc(s(row.getObservaciones()),tbF,bg));}
            doc.add(tt);
        }

        List<UatTestCaseItem> vc = orEmpty(r.getValidatedCases()).stream()
                .filter(c->c!=null&&(notBlank(c.getSucursal())||notBlank(c.getSuscriptor()))).toList();
        if (!vc.isEmpty()) {
            addSec(doc,"5. Casos Validados",secF);
            PdfPTable ct=new PdfPTable(new float[]{1.2f,1.5f,1.8f,1.5f});
            ct.setWidthPercentage(100); ct.setSpacingBefore(4); ct.setSpacingAfter(12);
            addTH(ct,new String[]{"Sucursal","Suscriptor","Servicio","Resultado"},thF);
            for(int i=0;i<vc.size();i++){UatTestCaseItem c=vc.get(i);Color bg=i%2==0?Color.WHITE:C_BG_ALT;ct.addCell(tc(s(c.getSucursal()),tbF,bg));ct.addCell(tc(s(c.getSuscriptor()),tbF,bg));ct.addCell(tc(s(c.getServicio()),tbF,bg));ct.addCell(tc(s(c.getResultado()),tbF,bg));}
            doc.add(ct);
        }

        List<String> steps = filtered(r.getExecutionSteps());
        if (!steps.isEmpty()) {
            addSec(doc,"6. Pasos de Ejecuci\u00f3n",secF);
            for(int i=0;i<steps.size();i++){try{Paragraph p=new Paragraph((i+1)+". "+steps.get(i),valF);p.setIndentationLeft(12);p.setSpacingAfter(4);doc.add(p);}catch(Exception e){}}
        }

        doc.close();
        return bos.toByteArray();
    }

    public byte[] generateMarkdown(UatRequest r) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        StringBuilder md = new StringBuilder();
        md.append("# VoBo UAT \u2013 ").append(s(r.getRfcNumber())).append(" \u2013 ").append(s(r.getRfcName())).append("\n\n");
        md.append("> Generado el ").append(now).append("\n\n");
        md.append("| Campo | Valor |\n|-------|-------|\n");
        mdRow(md,"RFC",s(r.getRfcNumber())); mdRow(md,"Nombre",s(r.getRfcName())); mdRow(md,"M\u00f3dulo",s(r.getModuleName())); mdRow(md,"Ambiente",s(r.getEnvironment()));
        if (notBlank(r.getReleaseJira())) mdRow(md,"Jira",s(r.getReleaseJira()));
        md.append("\n---\n\n");
        if (notBlank(r.getAlcance())) md.append("## Alcance\n\n").append(r.getAlcance().trim()).append("\n\n---\n\n");
        List<String> sc=filtered(r.getScenarios());
        md.append("## Escenarios\n\n"); if(sc.isEmpty()) md.append("_Sin escenarios._\n\n"); else {sc.forEach(s->md.append("- ").append(s.trim()).append("\n")); md.append("\n");}
        md.append("---\n\n");
        List<UatRequirement> td=orEmpty(r.getTestData()).stream().filter(d->d!=null&&(notBlank(d.getSucursal())||notBlank(d.getSuscriptor()))).toList();
        md.append("## Datos para Pruebas\n\n"); if(td.isEmpty()) md.append("_Sin datos._\n\n"); else {md.append("| Sucursal | Suscriptor | Servicio | Observaciones |\n|----------|------------|----------|---------------|\n"); td.forEach(row->md.append("| ").append(mc(s(row.getSucursal()))).append(" | ").append(mc(s(row.getSuscriptor()))).append(" | ").append(mc(s(row.getServicio()))).append(" | ").append(mc(s(row.getObservaciones()))).append(" |\n")); md.append("\n");}
        md.append("---\n\n");
        List<UatTestCaseItem> vc=orEmpty(r.getValidatedCases()).stream().filter(c->c!=null&&(notBlank(c.getSucursal())||notBlank(c.getSuscriptor()))).toList();
        md.append("## Casos Validados\n\n"); if(vc.isEmpty()) md.append("_Sin casos._\n\n"); else {md.append("| Sucursal | Suscriptor | Servicio | Resultado |\n|----------|------------|----------|-----------|\n"); vc.forEach(c->md.append("| ").append(mc(s(c.getSucursal()))).append(" | ").append(mc(s(c.getSuscriptor()))).append(" | ").append(mc(s(c.getServicio()))).append(" | ").append(mc(s(c.getResultado()))).append(" |\n")); md.append("\n");}
        md.append("---\n\n");
        List<String> steps=filtered(r.getExecutionSteps());
        md.append("## Pasos de Ejecuci\u00f3n\n\n"); if(steps.isEmpty()) md.append("_Sin pasos._\n\n"); else {for(int i=0;i<steps.size();i++) md.append((i+1)+". ").append(steps.get(i).trim()).append("\n"); md.append("\n");}
        md.append("\n---\n\n_Generado el ").append(now).append(" mediante **Release Notifier QA**._\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    // helpers
    private String s(String v){return v!=null&&!v.isBlank()?v.trim():"";}
    private boolean notBlank(String v){return v!=null&&!v.isBlank();}
    private <T> List<T> orEmpty(List<T> l){return l!=null?l:Collections.emptyList();}
    private List<String> filtered(List<String> l){return orEmpty(l).stream().filter(x->x!=null&&!x.isBlank()).toList();}
    private String esc(String v){if(v==null||v.isBlank())return "";return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private String mc(String v){if(v==null||v.isBlank())return "\u2014";return v.trim().replace("|","\\|").replace("\r","").replace("\n"," ");}
    private void mdRow(StringBuilder sb,String l,String v){sb.append("| ").append(mc(l)).append(" | ").append(mc(v)).append(" |\n");}
    private void hrule(Document doc,Color color,float lw) throws DocumentException{LineSeparator ls=new LineSeparator(lw,100f,color,Element.ALIGN_CENTER,-2f);Paragraph p=new Paragraph(new Chunk(ls));p.setSpacingBefore(4);p.setSpacingAfter(12);doc.add(p);}
    private void addSec(Document doc,String text,Font font) throws DocumentException{Paragraph p=new Paragraph(text,font);p.setSpacingBefore(16);p.setSpacingAfter(0);doc.add(p);LineSeparator ls=new LineSeparator(1.5f,100f,C_PRIMARY,Element.ALIGN_LEFT,-4f);Paragraph line=new Paragraph(new Chunk(ls));line.setSpacingBefore(2);line.setSpacingAfter(8);doc.add(line);}
    private void addBT(Document doc,String text,Font vf,Font mf) throws DocumentException{boolean e=text==null||text.isBlank();Paragraph p=new Paragraph(e?"\u2014":text,e?mf:vf);p.setSpacingBefore(2);p.setSpacingAfter(8);doc.add(p);}
    private void addIP(PdfPTable t,String l,String v,Font lf,Font vf){PdfPCell lc=new PdfPCell(new Phrase(l,lf));lc.setBorder(Rectangle.NO_BORDER);lc.setPaddingBottom(7);t.addCell(lc);PdfPCell vc=new PdfPCell(new Phrase(v.isBlank()?"\u2014":v,vf));vc.setBorder(Rectangle.NO_BORDER);vc.setPaddingBottom(7);t.addCell(vc);}
    private void addTH(PdfPTable t,String[] headers,Font font){for(String h:headers){PdfPCell cell=new PdfPCell(new Phrase(h,font));cell.setBackgroundColor(C_PRIMARY);cell.setPadding(8);cell.setBorderColor(C_DARK);t.addCell(cell);}}
    private PdfPCell tc(String text,Font font,Color bg){PdfPCell cell=new PdfPCell(new Phrase(text.isBlank()?"\u2014":text,font));cell.setBackgroundColor(bg);cell.setPadding(7);cell.setBorderColor(C_BORDER);return cell;}
}