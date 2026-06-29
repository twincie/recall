package dev.recall;

public class SlugUtil {
    private static final int MAX_SLUG_LENGTH = 40;

    public static String makeSlug(String text) {
        return makeSlug(text, MAX_SLUG_LENGTH);
    }

    public static String makeSlug(String text, int maxLength) {
        if (text == null || text.isBlank()) return "untitled";
        String slug = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) return "untitled";
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug;
    }
}
