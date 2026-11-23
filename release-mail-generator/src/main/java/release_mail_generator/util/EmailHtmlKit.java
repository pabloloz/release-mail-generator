package release_mail_generator.util;

/**
 * Bloques HTML reutilizables para los correos (liberación y RDL).
 *
 * Pensado para Outlook/Gmail: solo tablas, estilos en línea y colores sólidos (bgcolor); sin flex, grid
 * ni dependencias externas. Las clases "rm-*" no afectan al correo: solo permiten que la vista previa del
 * modo oscuro invierta los fondos claros. Todos los parámetros de texto deben llegar YA escapados.
 */
public final class EmailHtmlKit {

    private EmailHtmlKit() {}

    public static final String NAVY = "#12306b";
    public static final String BLUE = "#2563eb";
    public static final String PURPLE = "#7030A0";
    public static final String MUTED = "#64748b";
    public static final String INK = "#0f172a";

    public static final String ICON_ROCKET = "\uD83D\uDE80";
    public static final String ICON_CHART = "\uD83D\uDCCA";
    public static final String ICON_BOX = "\uD83D\uDCE6";
    public static final String ICON_DESKTOP = "\uD83D\uDDA5\uFE0F";
    public static final String ICON_PUZZLE = "\uD83E\uDDE9";
    public static final String ICON_GEAR = "\u2699\uFE0F";
    public static final String ICON_SNOW = "\u2744\uFE0F";
    public static final String ICON_SCRIPT = "\uD83E\uDDFE";
    public static final String ICON_DB = "\uD83D\uDDC4\uFE0F";
    public static final String ICON_FILE = "\uD83D\uDCC4";
    public static final String ICON_FOLDER = "\uD83D\uDCC2";
    public static final String ICON_TICKET = "\uD83C\uDFAB";
    public static final String ICON_PIN = "\uD83D\uDCCC";
    public static final String ICON_BRANCH = "\uD83C\uDF3F";
    public static final String ICON_LINK = "\uD83D\uDD17";
    public static final String ICON_CALENDAR = "\uD83D\uDCC5";
    public static final String ICON_TAG = "\uD83C\uDFF7\uFE0F";
    public static final String ICON_BACK = "\u21A9\uFE0F";
    public static final String ICON_WARN = "\u26A0\uFE0F";
    public static final String ICON_SERVER = "\uD83D\uDDA7";
    public static final String ICON_IMAGE = "\uD83D\uDDBC\uFE0F";

    private static final String TABLE = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"";

    /** Contenedor general: tipografía base y ancho máximo centrado. */
    public static String wrap(String inner) {
        return "<div style=\"font-family: Calibri, 'Segoe UI', Arial, sans-serif; font-size: 11pt;"
            + " color: #1e293b; line-height: 1.5;\">"
            + TABLE + " width=\"100%\" style=\"max-width: 680px; width: 100%;\"><tr><td>"
            + inner
            + "</td></tr></table></div>";
    }

    /** Banda de título azul marino con icono, título y subtítulo opcional. */
    public static String header(String eyebrow, String icon, String title, String subtitle) {
        StringBuilder sb = new StringBuilder();
        sb.append(TABLE).append(" width=\"100%\" bgcolor=\"").append(NAVY).append("\"")
          .append(" style=\"background-color: ").append(NAVY).append("; border-radius: 10px;\">")
          .append("<tr><td style=\"padding: 20px 24px;\">")
          .append("<div style=\"font-size: 9pt; letter-spacing: 1.5px; color: #93c5fd; font-weight: bold;\">")
          .append(eyebrow).append("</div>")
          .append("<div style=\"font-size: 19pt; font-weight: bold; color: #ffffff; line-height: 1.25;")
          .append(" padding-top: 4px;\">").append(icon).append("&nbsp; ").append(title).append("</div>");
        if (subtitle != null && !subtitle.isEmpty()) {
            sb.append("<div style=\"font-size: 11pt; color: #dbeafe; padding-top: 4px;\">")
              .append(subtitle).append("</div>");
        }
        sb.append("</td></tr></table>");
        return sb.append(spacer(14)).toString();
    }

    /** Tira de datos clave (etiqueta + valor) con columnas de igual ancho. Cada par: {etiqueta, valor}. */
    public static String facts(java.util.List<String[]> pairs) {
        if (pairs.isEmpty()) return "";
        int width = 100 / pairs.size();
        StringBuilder sb = new StringBuilder();
        sb.append(TABLE).append(" width=\"100%\" class=\"rm-surface\" bgcolor=\"#f1f5f9\"")
          .append(" style=\"background-color: #f1f5f9; border: 1px solid #e2e8f0;\"><tr>");
        for (int i = 0; i < pairs.size(); i++) {
            String[] p = pairs.get(i);
            sb.append("<td width=\"").append(width).append("%\" valign=\"top\" style=\"padding: 10px 14px;")
              .append(i > 0 ? " border-left: 1px solid #e2e8f0;" : "").append("\">")
              .append("<div style=\"font-size: 8.5pt; color: ").append(MUTED).append(";\">").append(p[0]).append("</div>")
              .append("<div style=\"font-size: 12pt; font-weight: bold; color: ").append(INK).append(";\">")
              .append(p[1]).append("</div></td>");
        }
        sb.append("</tr></table>");
        return sb.append(spacer(14)).toString();
    }

    /** Tarjeta con barra de acento a la izquierda, icono + título y contenido libre. */
    public static String card(String icon, String title, String accent, String body) {
        return TABLE + " width=\"100%\" class=\"rm-card\" bgcolor=\"#ffffff\""
            + " style=\"background-color: #ffffff; border: 1px solid #e2e8f0;\"><tr>"
            + "<td width=\"5\" bgcolor=\"" + accent + "\" style=\"width: 5px; background-color: " + accent
            + "; font-size: 1px; line-height: 1px;\">&nbsp;</td>"
            + "<td style=\"padding: 12px 16px;\">"
            + "<div style=\"font-size: 12pt; font-weight: bold; color: " + INK + ";\">"
            + icon + "&nbsp; " + title + "</div>"
            + (body.isEmpty() ? "" : "<div style=\"padding-top: 6px;\">" + body + "</div>")
            + "</td></tr></table>" + spacer(12);
    }

    /** Etiqueta de apoyo (gris) seguida de un valor en la siguiente línea. */
    public static String labeled(String label, String valueHtml) {
        return "<div style=\"font-size: 9pt; color: " + MUTED + "; padding-top: 4px;\">" + label + "</div>"
            + "<div style=\"word-break: break-all;\">" + valueHtml + "</div>";
    }

    /** Píldora de color para ver de un vistazo módulos, tickets o versiones. */
    public static String pill(String text, String bg, String fg) {
        return "<span class=\"rm-pill\" style=\"background-color: " + bg + "; color: " + fg + ";"
            + " padding: 2px 9px; border-radius: 10px; font-size: 9.5pt; font-weight: bold;\">" + text + "</span>";
    }

    /** Botón de llamada a la acción (tabla con bgcolor para que Outlook lo respete). */
    public static String button(String hrefEscaped, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
            + "<td bgcolor=\"" + BLUE + "\" style=\"background-color: " + BLUE + "; border-radius: 6px;"
            + " padding: 10px 20px;\">"
            + "<a class=\"rm-btn\" href=\"" + hrefEscaped + "\" style=\"color: #ffffff; font-weight: bold;"
            + " text-decoration: none; font-size: 11pt;\">" + label + "</a>"
            + "</td></tr></table>";
    }

    /** Banda de título para separar cada reporte cuando hay varios (la clase rdl-hdr la usa el modo oscuro). */
    public static String banner(String textHtml) {
        return TABLE + " width=\"100%\"><tr><td class=\"rdl-hdr\" bgcolor=\"#eff6ff\""
            + " style=\"background-color: #eff6ff; border-left: 4px solid " + BLUE + "; padding: 8px 14px;"
            + " font-size: 12.5pt; color: " + INK + ";\"><b>" + textHtml + "</b></td></tr></table>" + spacer(10);
    }

    public static String spacer(int px) {
        return "<div style=\"height: " + px + "px; line-height: " + px + "px; font-size: 1px;\">&nbsp;</div>";
    }

    /** Línea con icono, útil para listas (scripts, cambios, ramas). */
    public static String iconLine(String icon, String contentHtml) {
        return "<div style=\"padding: 2px 0;\">" + icon + "&nbsp; " + contentHtml + "</div>";
    }

    /** Nombre técnico (SP, script) en morado, como en el diseño manual. */
    public static String code(String textEscaped) {
        return "<span class=\"rm-code\" style=\"color: " + PURPLE + "; font-family: Consolas, 'Courier New', monospace;"
            + " font-weight: bold;\">" + textEscaped + "</span>";
    }
}
