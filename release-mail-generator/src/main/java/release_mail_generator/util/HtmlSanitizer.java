package release_mail_generator.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizador HTML por lista blanca para el contenido que viene del editor enriquecido.
 * Solo se conservan etiquetas y atributos conocidos; todo lo demás se elimina o se escapa.
 * Sin dependencias externas.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "b", "strong", "i", "em", "u", "s", "strike", "del", "span", "div", "font",
            "ul", "ol", "li", "a", "table", "thead", "tbody", "tr", "td", "th",
            "code", "pre", "blockquote", "h1", "h2", "h3", "h4", "sub", "sup", "hr", "img");

    private static final Set<String> VOID_TAGS = Set.of("br", "img", "hr");

    /** Etiquetas cuyo contenido completo se descarta (no solo la etiqueta). */
    private static final Set<String> DROP_WITH_CONTENT = Set.of(
            "script", "style", "iframe", "object", "embed", "form", "textarea", "title",
            "noscript", "template", "svg", "math", "applet", "frameset", "select", "button");

    private static final Set<String> STYLE_PROPS = Set.of(
            "color", "background-color", "font-size", "font-weight", "font-style", "font-family",
            "text-decoration", "text-align", "line-height", "margin", "margin-top", "margin-bottom",
            "margin-left", "margin-right", "padding", "padding-top", "padding-bottom", "padding-left",
            "padding-right", "border", "border-top", "border-bottom", "border-left", "border-right",
            "border-collapse", "width", "height", "max-width", "white-space", "vertical-align");

    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*)>");
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,8});");
    private static final Pattern DATA_IMAGE = Pattern.compile(
            "^data:image/(?:png|jpe?g|gif|webp);base64,[A-Za-z0-9+/=\\s]+$");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,4}%?$");
    private static final Pattern COLOR = Pattern.compile("^(#[0-9a-fA-F]{3,8}|[a-zA-Z]{3,20})$");
    private static final Pattern SAFE_STYLE_VALUE = Pattern.compile("^[-#%.,'\\w\\s()]+$");

    /** Devuelve HTML seguro: solo etiquetas/atributos permitidos, bien balanceado. */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) return "";
        StringBuilder out = new StringBuilder(html.length());
        Deque<String> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(html);
        int pos = 0;
        while (pos < html.length()) {
            int lt = html.indexOf('<', pos);
            if (lt < 0) { appendText(out, html.substring(pos)); break; }
            if (html.startsWith("<!--", lt)) {
                appendText(out, html.substring(pos, lt));
                int end = html.indexOf("-->", lt + 4);
                pos = end < 0 ? html.length() : end + 3;
                continue;
            }
            m.region(lt, html.length());
            if (!m.lookingAt()) {
                appendText(out, html.substring(pos, lt + 1));
                pos = lt + 1;
                continue;
            }
            appendText(out, html.substring(pos, lt));
            pos = m.end();
            boolean closing = !m.group(1).isEmpty();
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (DROP_WITH_CONTENT.contains(name)) {
                if (!closing) pos = skipUntilClose(html, pos, name);
                continue;
            }
            if (!ALLOWED_TAGS.contains(name)) continue;
            if (closing) {
                if (!VOID_TAGS.contains(name) && open.contains(name)) {
                    while (!open.isEmpty()) {
                        String top = open.pop();
                        out.append("</").append(top).append('>');
                        if (top.equals(name)) break;
                    }
                }
                continue;
            }
            String attrs = buildAttributes(name, m.group(3));
            if (name.equals("img") && !attrs.contains(" src=\"")) continue;
            out.append('<').append(name).append(attrs).append('>');
            if (!VOID_TAGS.contains(name)) open.push(name);
        }
        while (!open.isEmpty()) out.append("</").append(open.pop()).append('>');
        return out.toString();
    }

    /** Devuelve el data URI si es una imagen raster segura; vacío en caso contrario. */
    public static String safeImageDataUri(String uri) {
        if (uri == null) return "";
        String v = uri.strip();
        return DATA_IMAGE.matcher(v).matches() ? v.replaceAll("\\s+", "") : "";
    }

    private static int skipUntilClose(String html, int from, String name) {
        Matcher close = Pattern.compile("</\\s*" + name + "\\s*>", Pattern.CASE_INSENSITIVE).matcher(html);
        return close.find(from) ? close.end() : html.length();
    }

    private static void appendText(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '&') {
                Matcher e = ENTITY.matcher(text);
                e.region(i, text.length());
                out.append(e.lookingAt() ? "&" : "&amp;");
            } else out.append(c);
        }
    }

    private static String buildAttributes(String tag, String raw) {
        StringBuilder sb = new StringBuilder();
        Matcher a = ATTR.matcher(raw == null ? "" : raw);
        while (a.find()) {
            String key = a.group(1).toLowerCase(Locale.ROOT);
            String value = a.group(2) != null ? a.group(2) : a.group(3) != null ? a.group(3) : a.group(4);
            if (value == null) continue;
            String decoded = decodeEntities(value);
            String safe = safeAttribute(tag, key, decoded);
            if (safe != null) sb.append(' ').append(key).append("=\"").append(escapeAttr(safe)).append('"');
        }
        if (tag.equals("a") && sb.indexOf(" href=\"") >= 0) sb.append(" rel=\"noopener noreferrer\"");
        return sb.toString();
    }

    private static String safeAttribute(String tag, String key, String value) {
        switch (key) {
            case "style":
                String style = filterStyle(value);
                return style.isEmpty() ? null : style;
            case "href":
                if (!tag.equals("a")) return null;
                return safeUrl(value);
            case "src":
                if (!tag.equals("img")) return null;
                String img = safeImageDataUri(value);
                return img.isEmpty() ? null : img;
            case "alt": case "title":
                return (tag.equals("img") || tag.equals("a")) ? value : null;
            case "width": case "height":
                return DIGITS.matcher(value.trim()).matches() && (tag.equals("img") || tag.equals("td")
                        || tag.equals("th") || tag.equals("table")) ? value.trim() : null;
            case "colspan": case "rowspan":
                return (tag.equals("td") || tag.equals("th")) && value.matches("^[0-9]{1,2}$") ? value : null;
            case "align": case "valign":
                return value.matches("^(?i)(left|right|center|top|middle|bottom)$") ? value : null;
            case "color":
                return tag.equals("font") && COLOR.matcher(value.trim()).matches() ? value.trim() : null;
            case "size":
                return tag.equals("font") && value.trim().matches("^[1-7]$") ? value.trim() : null;
            case "face":
                return tag.equals("font") && value.matches("^[-\\w\\s,]+$") ? value : null;
            case "start":
                return tag.equals("ol") && value.trim().matches("^[0-9]{1,4}$") ? value.trim() : null;
            default:
                return null;
        }
    }

    /** Solo http, https, mailto y tel. Descarta javascript:, data:, vbscript:, etc. */
    private static String safeUrl(String value) {
        String cleaned = value.replaceAll("[\\p{Cntrl}\\s]+", "");
        String lower = cleaned.toLowerCase(Locale.ROOT);
        boolean ok = lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("mailto:") || lower.startsWith("tel:");
        return ok ? cleaned : null;
    }

    private static String filterStyle(String css) {
        StringBuilder out = new StringBuilder();
        for (String decl : css.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) continue;
            String prop = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String val = decl.substring(colon + 1).trim();
            String lowerVal = val.toLowerCase(Locale.ROOT);
            if (!STYLE_PROPS.contains(prop) || val.isEmpty()) continue;
            if (lowerVal.contains("url(") || lowerVal.contains("expression") || lowerVal.contains("javascript")
                    || lowerVal.contains("@import") || lowerVal.contains("\\") || !SAFE_STYLE_VALUE.matcher(val).matches()) continue;
            out.append(prop).append(':').append(val).append(';');
        }
        return out.toString();
    }

    private static String decodeEntities(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String body = m.group(1);
            String rep;
            try {
                if (body.startsWith("#x") || body.startsWith("#X")) rep = new String(Character.toChars(Integer.parseInt(body.substring(2), 16)));
                else if (body.startsWith("#")) rep = new String(Character.toChars(Integer.parseInt(body.substring(1))));
                else rep = switch (body.toLowerCase(Locale.ROOT)) {
                    case "amp" -> "&"; case "lt" -> "<"; case "gt" -> ">"; case "quot" -> "\""; case "apos" -> "'";
                    case "colon" -> ":"; case "tab" -> "\t"; case "newline" -> "\n"; case "nbsp" -> " ";
                    default -> m.group();
                };
            } catch (IllegalArgumentException ex) { rep = ""; }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
