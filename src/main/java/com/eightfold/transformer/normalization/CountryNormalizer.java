package com.eightfold.transformer.normalization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes free-text country names / abbreviations into ISO 3166-1 alpha-2 codes.
 * Falls back to {@link Locale} ISO-country lookups for anything not in the manual map.
 */
public final class CountryNormalizer {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("usa", "US");
        ALIASES.put("us", "US");
        ALIASES.put("u.s.a.", "US");
        ALIASES.put("u.s.", "US");
        ALIASES.put("united states", "US");
        ALIASES.put("united states of america", "US");
        ALIASES.put("india", "IN");
        ALIASES.put("bharat", "IN");
        ALIASES.put("uk", "GB");
        ALIASES.put("u.k.", "GB");
        ALIASES.put("united kingdom", "GB");
        ALIASES.put("great britain", "GB");
        ALIASES.put("england", "GB");
        ALIASES.put("canada", "CA");
        ALIASES.put("germany", "DE");
        ALIASES.put("deutschland", "DE");
        ALIASES.put("france", "FR");
        ALIASES.put("australia", "AU");
        ALIASES.put("singapore", "SG");
        ALIASES.put("uae", "AE");
        ALIASES.put("united arab emirates", "AE");
        ALIASES.put("netherlands", "NL");
        ALIASES.put("the netherlands", "NL");
        ALIASES.put("ireland", "IE");
        ALIASES.put("japan", "JP");
        ALIASES.put("china", "CN");
        ALIASES.put("brazil", "BR");
        ALIASES.put("mexico", "MX");

        // Pre-load every ISO country known to the JVM, keyed by lowercase display name.
        for (String isoCountry : Locale.getISOCountries()) {
            Locale locale = new Locale("", isoCountry);
            ALIASES.putIfAbsent(locale.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), isoCountry);
        }
    }

    private CountryNormalizer() {
    }

    /**
     * @return ISO 3166-1 alpha-2 code, or null if it cannot be confidently resolved.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        // Already a valid-looking alpha-2 code.
        if (cleaned.length() == 2 && cleaned.toUpperCase(Locale.ROOT).matches("[A-Z]{2}")) {
            String upper = cleaned.toUpperCase(Locale.ROOT);
            for (String iso : Locale.getISOCountries()) {
                if (iso.equals(upper)) {
                    return upper;
                }
            }
        }
        String key = cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return ALIASES.get(key);
    }
}
