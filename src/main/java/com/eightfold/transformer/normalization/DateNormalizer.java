package com.eightfold.transformer.normalization;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a wide variety of date representations into "YYYY-MM".
 * Special-cases "present" / "current" / "now" (case-insensitive) as the literal "present".
 */
public final class DateNormalizer {

    private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})-(\\d{1,2})$");
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");
    private static final Pattern SLASH_MDY = Pattern.compile("^(\\d{1,2})/(\\d{4})$"); // MM/YYYY
    private static final Pattern FULL_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

    private static final DateTimeFormatter MONTH_YEAR_TEXT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_FULL_YEAR_TEXT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private DateNormalizer() {
    }

    /**
     * @return "YYYY-MM", the literal "present", or null if the date cannot be
     *         confidently parsed (never guesses a date).
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("present") || lower.equals("current") || lower.equals("now")
                || lower.equals("ongoing") || lower.equals("till date") || lower.equals("till now")) {
            return "present";
        }

        Matcher m;

        m = YEAR_MONTH.matcher(trimmed);
        if (m.matches()) {
            int month = Integer.parseInt(m.group(2));
            if (month < 1 || month > 12) return null;
            return String.format("%s-%02d", m.group(1), month);
        }

        m = FULL_DATE.matcher(trimmed);
        if (m.matches()) {
            return m.group(1) + "-" + m.group(2);
        }

        m = SLASH_MDY.matcher(trimmed);
        if (m.matches()) {
            int month = Integer.parseInt(m.group(1));
            if (month < 1 || month > 12) return null;
            return String.format("%s-%02d", m.group(2), month);
        }

        m = YEAR_ONLY.matcher(trimmed);
        if (m.matches()) {
            return m.group(1) + "-01";
        }

        try {
            YearMonth ym = YearMonth.parse(trimmed, MONTH_YEAR_TEXT);
            return ym.toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            YearMonth ym = YearMonth.parse(trimmed, MONTH_FULL_YEAR_TEXT);
            return ym.toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        return null;
    }
}
