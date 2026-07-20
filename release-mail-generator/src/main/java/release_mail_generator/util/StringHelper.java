package release_mail_generator.util;

/**
 * Shared string utility methods — eliminates duplication across services.
 */
public final class StringHelper {

    private StringHelper() {}

    /** Returns trimmed string or empty string if null/blank. */
    public static String clean(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : "";
    }

    /** Returns true if string is non-null and non-blank. */
    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Returns the string if non-blank, otherwise the fallback. */
    public static String nvl(String s, String fallback) {
        return notBlank(s) ? s.trim() : fallback;
    }

    /** HTML-escapes &, <, >, and " for safe embedding in HTML attributes/content. */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
