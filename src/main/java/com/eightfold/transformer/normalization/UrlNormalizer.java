package com.eightfold.transformer.normalization;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes free-form URLs, with special-case canonicalization for LinkedIn and
 * GitHub profile URLs (consistent scheme, host, no tracking params, no trailing slash).
 */
public final class UrlNormalizer {

    private static final Pattern LINKEDIN_HANDLE =
            Pattern.compile("linkedin\\.com/in/([a-zA-Z0-9\\-_%]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_HANDLE =
            Pattern.compile("github\\.com/([a-zA-Z0-9\\-]+)/?$", Pattern.CASE_INSENSITIVE);

    private UrlNormalizer() {
    }

    /** General-purpose normalization: trims, lowercases scheme/host, strips trailing slash and query/fragment noise where safe. */
    public static String normalizeGeneric(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = "https://" + url;
        }
        url = url.replaceAll("/+$", "");
        return url;
    }

    public static String normalizeLinkedIn(String raw) {
        String generic = normalizeGeneric(raw);
        if (generic == null) {
            return null;
        }
        Matcher m = LINKEDIN_HANDLE.matcher(generic);
        if (m.find()) {
            String handle = m.group(1).toLowerCase(Locale.ROOT);
            return "https://www.linkedin.com/in/" + handle;
        }
        return null;
    }

    public static String normalizeGitHub(String raw) {
        String generic = normalizeGeneric(raw);
        if (generic == null) {
            return null;
        }
        Matcher m = GITHUB_HANDLE.matcher(generic);
        if (m.find()) {
            String handle = m.group(1).toLowerCase(Locale.ROOT);
            return "https://github.com/" + handle;
        }
        return null;
    }
}
