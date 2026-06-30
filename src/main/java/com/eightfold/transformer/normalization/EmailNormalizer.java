package com.eightfold.transformer.normalization;

import java.util.regex.Pattern;

/**
 * Normalizes and validates email addresses.
 * Rules: lowercase, trim, collapse internal whitespace removal, validate shape.
 */
public final class EmailNormalizer {

    // Pragmatic RFC-5322-ish pattern; good enough to reject garbage without being a full grammar.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    private EmailNormalizer() {
    }

    /**
     * @return the normalized, lowercase, trimmed email, or {@code null} if the
     *         input is blank or not a structurally valid email (never invents data).
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase().replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return isValid(cleaned) ? cleaned : null;
    }

    public static boolean isValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
