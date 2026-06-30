package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawEducation;
import com.eightfold.transformer.source.RawExperience;
import com.eightfold.transformer.source.RawValue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared regex/keyword-based extraction heuristics used by both the resume
 * parser and the recruiter-notes parser, since both work over unstructured
 * prose. Centralizing this avoids duplicating fragile regex logic in two places.
 * <p>
 * These are intentionally heuristic (Template Method-ish helpers, not a full NLP
 * pipeline) - good enough to pull structured signal out of prose without ever
 * inventing values that aren't actually present in the text.
 */
public final class FreeTextHeuristics {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d{1,3}[\\s.-]?)?(\\(?\\d{3}\\)?[\\s.-]?)\\d{3}[\\s.-]?\\d{4}");
    private static final Pattern LINKEDIN = Pattern.compile("(https?://)?(www\\.)?linkedin\\.com/in/[A-Za-z0-9\\-_%]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB = Pattern.compile("(https?://)?(www\\.)?github\\.com/[A-Za-z0-9\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_URL = Pattern.compile("(https?://)[\\w.\\-/#?=&%]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEARS_EXP = Pattern.compile("(\\d+(?:\\.\\d+)?)\\+?\\s*years?\\s*(of)?\\s*experience", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KNOWN_SKILL_TOKENS = Set.of(
            "java", "python", "javascript", "typescript", "js", "ts", "react", "reactjs", "angular", "vue",
            "node", "nodejs", "spring", "springboot", "spring boot", "django", "flask", "postgres", "postgresql",
            "mysql", "mongodb", "mongo", "redis", "kafka", "docker", "kubernetes", "k8s", "aws", "gcp", "azure",
            "git", "github", "ci/cd", "cicd", "rest", "graphql", "grpc", "machine learning", "ml", "deep learning",
            "tensorflow", "pytorch", "pandas", "numpy", "sql", "nosql", "html", "css", "sass", "tailwind",
            "c++", "cpp", "c plus plus", "c#", "csharp", "go", "golang", "linux", "terraform", "ansible", "jenkins",
            "microservices", "system design", "data structures", "algorithms", "junit", "selenium", "jira", "scrum", "agile"
    );

    private FreeTextHeuristics() {
    }

    public static void extractContactInfo(RawCandidateData data, String text, ExtractionMethod method) {
        Matcher emailMatcher = EMAIL.matcher(text);
        Set<String> seenEmails = new LinkedHashSet<>();
        while (emailMatcher.find()) {
            String found = emailMatcher.group();
            if (seenEmails.add(found.toLowerCase())) {
                data.addEmail(new RawValue<>(found, method + " (email regex)"));
            }
        }

        Matcher phoneMatcher = PHONE.matcher(text);
        Set<String> seenPhones = new LinkedHashSet<>();
        while (phoneMatcher.find()) {
            String found = phoneMatcher.group().trim();
            // Avoid false positives like plain 7-digit numbers / years embedded in dates.
            String digitsOnly = found.replaceAll("[^0-9]", "");
            if (digitsOnly.length() >= 10 && seenPhones.add(digitsOnly)) {
                data.addPhone(new RawValue<>(found, method + " (phone regex)"));
            }
        }

        Matcher linkedinMatcher = LINKEDIN.matcher(text);
        if (linkedinMatcher.find()) {
            data.setLinkedin(new RawValue<>(linkedinMatcher.group(), method + " (linkedin regex)"));
        }

        Matcher githubMatcher = GITHUB.matcher(text);
        if (githubMatcher.find()) {
            data.setGithub(new RawValue<>(githubMatcher.group(), method + " (github regex)"));
        }

        Matcher urlMatcher = GENERIC_URL.matcher(text);
        Set<String> seenUrls = new LinkedHashSet<>();
        while (urlMatcher.find()) {
            String url = urlMatcher.group();
            if (url.toLowerCase().contains("linkedin.com") || url.toLowerCase().contains("github.com")) {
                continue;
            }
            if (seenUrls.add(url)) {
                data.addOtherLink(new RawValue<>(url, method + " (url regex)"));
            }
        }

        Matcher yearsMatcher = YEARS_EXP.matcher(text);
        if (yearsMatcher.find()) {
            try {
                data.setYearsExperience(new RawValue<>(Double.parseDouble(yearsMatcher.group(1)), method + " (years-of-experience regex)"));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    /** Scans free text for known skill keywords (word-boundary, case-insensitive). */
    public static void extractSkills(RawCandidateData data, String text, ExtractionMethod method) {
        String lower = text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (String token : KNOWN_SKILL_TOKENS) {
            String pattern = "(?<![a-zA-Z0-9])" + Pattern.quote(token) + "(?![a-zA-Z0-9])";
            if (Pattern.compile(pattern).matcher(lower).find()) {
                found.add(token);
            }
        }
        for (String token : found) {
            data.addSkill(new RawValue<>(token, method + " (skill keyword match)"));
        }
    }

    /**
     * Extracts a "SKILLS" section if explicitly labeled (e.g. "Skills: Java, Python, React"),
     * splitting on commas/pipes/bullets. This catches skills outside the fixed keyword list too.
     */
    public static void extractLabeledSkillsSection(RawCandidateData data, String text, ExtractionMethod method) {
        Pattern sectionPattern = Pattern.compile(
                "(?i)skills?\\s*[:\\-]\\s*(.+)");
        Matcher m = sectionPattern.matcher(text);
        while (m.find()) {
            String line = m.group(1);
            // Stop at end of line / next section header.
            int newline = line.indexOf('\n');
            if (newline >= 0) {
                line = line.substring(0, newline);
            }
            for (String token : line.split("[,|•·]")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty() && trimmed.length() < 40) {
                    data.addSkill(new RawValue<>(trimmed, method + " (labeled skills section)"));
                }
            }
        }
    }

    /** Best-effort candidate name guess: first non-blank line that looks like a personal name (2-4 capitalized words, no digits/@/punctuation-heavy). */
    public static String guessName(String text) {
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.length() > 60) continue;
            if (line.contains("@") || line.matches(".*\\d.*")) continue;
            String[] words = line.split("\\s+");
            if (words.length < 2 || words.length > 4) continue;
            boolean looksLikeName = true;
            for (String w : words) {
                if (w.isEmpty() || !Character.isUpperCase(w.charAt(0))) {
                    looksLikeName = false;
                    break;
                }
            }
            if (looksLikeName) {
                return line;
            }
        }
        return null;
    }

    public static List<String> knownSkillTokens() {
        return List.copyOf(KNOWN_SKILL_TOKENS);
    }
}
