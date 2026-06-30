package com.eightfold.transformer.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Canonicalizes free-text skill tokens (e.g. "js", "ReactJS", "postgres") into a
 * single canonical display name (e.g. "JavaScript", "React", "PostgreSQL") using
 * an alias table loaded from {@code skill-aliases.json} on the classpath.
 * <p>
 * Unknown skills are not discarded: they are title-cased and passed through, since
 * "never invent data" means we don't fabricate a canonical mapping that doesn't
 * exist, but we also shouldn't silently drop a skill the candidate genuinely listed.
 */
public final class SkillNormalizer {

    private static final Map<String, String> ALIAS_TO_CANONICAL = new HashMap<>();

    static {
        try (InputStream is = SkillNormalizer.class.getResourceAsStream("/skill-aliases.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, String> raw = mapper.readValue(is, Map.class);
                for (Map.Entry<String, String> e : raw.entrySet()) {
                    ALIAS_TO_CANONICAL.put(normalizeKey(e.getKey()), e.getValue());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill-aliases.json from classpath", e);
        }
    }

    private SkillNormalizer() {
    }

    private static String normalizeKey(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", " ").trim();
    }

    /**
     * @return the canonical skill name. Never null/blank for non-blank input: falls
     *         back to a whitespace-normalized, title-cased version of the input if
     *         no canonical alias is known.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String key = normalizeKey(trimmed);
        String canonical = ALIAS_TO_CANONICAL.get(key);
        if (canonical != null) {
            return canonical;
        }
        return titleCase(trimmed.replaceAll("\\s+", " ").trim());
    }

    private static String titleCase(String s) {
        // Preserve all-caps acronyms of length <= 4 (e.g. "AWS", "SQL", "C++").
        if (s.equals(s.toUpperCase(Locale.ROOT)) && s.length() <= 4) {
            return s;
        }
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (part.equals(part.toUpperCase(Locale.ROOT)) && part.length() <= 4) {
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
