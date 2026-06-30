package com.eightfold.transformer.normalization;

import java.util.Locale;

/**
 * Normalizes human names: collapse whitespace, trim, fix obviously-wrong all-caps /
 * all-lowercase casing while preserving legitimate mixed-case names (e.g. "McDonald",
 * "O'Brien", "van der Berg") untouched.
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        // If the name has mixed case already (not all upper, not all lower), trust it as-is.
        boolean allUpper = trimmed.equals(trimmed.toUpperCase(Locale.ROOT));
        boolean allLower = trimmed.equals(trimmed.toLowerCase(Locale.ROOT));
        if (!allUpper && !allLower) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : trimmed.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '\'') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
