package release_mail_generator.util;

/**
 * Shared utility for sanitizing strings for use in filenames.
 */
public final class FileNameUtils {

    private FileNameUtils() {}

    /**
     * Sanitizes a string for safe use as a filename.
     * Removes all characters except alphanumeric, dots, hyphens, and underscores.
     */
    public static String sanitize(String input, String fallback) {
        if (input == null || input.isBlank()) return fallback != null ? fallback : "unnamed";
        return input.trim().replaceAll("[^a-zA-Z0-9.\\-_]", "-").replaceAll("-{2,}", "-");
    }

    public static String sanitize(String input) {
        return sanitize(input, "unnamed");
    }
}
