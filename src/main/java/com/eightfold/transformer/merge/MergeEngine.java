package com.eightfold.transformer.merge;

import com.eightfold.transformer.confidence.ConfidenceEngine;
import com.eightfold.transformer.model.*;
import com.eightfold.transformer.normalization.*;
import com.eightfold.transformer.provenance.ProvenanceTracker;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawEducation;
import com.eightfold.transformer.source.RawExperience;
import com.eightfold.transformer.source.RawValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates normalization + deterministic conflict resolution + confidence
 * scoring + provenance tracking across every source, producing one canonical
 * {@link CandidateProfile}.
 * <p>
 * This is the heart of the transformation engine. It deliberately does NOT do
 * any I/O or projection - it consumes already-parsed {@link RawCandidateData}
 * and produces the canonical model, full stop. Single Responsibility.
 */
public final class MergeEngine {

    private static final Logger log = LoggerFactory.getLogger(MergeEngine.class);

    private final ScalarFieldMerger scalarMerger = new ScalarFieldMerger();
    private final ConfidenceEngine confidenceEngine = new ConfidenceEngine();

    public CandidateProfile merge(String candidateId, List<RawCandidateData> allSources) {
        List<RawCandidateData> sources = allSources.stream()
                .filter(s -> !s.isMalformed())
                .collect(Collectors.toList());

        for (RawCandidateData s : allSources) {
            if (s.isMalformed()) {
                log.warn("Skipping malformed source {} ({}): {}", s.sourceIdentifier(), s.sourceType(), s.malformedReason());
            }
        }

        ProvenanceTracker provenance = new ProvenanceTracker();
        Map<String, Double> fieldConfidences = new LinkedHashMap<>();

        // ---- full_name ----
        List<Candidate<String>> nameCandidates = new ArrayList<>();
        for (RawCandidateData s : sources) {
            if (s.fullName() != null) {
                String norm = NameNormalizer.normalize(s.fullName().value());
                if (norm != null) {
                    nameCandidates.add(new Candidate<>(norm, s.sourceType(), s.sourceIdentifier(), s.fullName().method()));
                }
            }
        }
        MergeResult<String> fullName = scalarMerger.merge(nameCandidates);
        recordScalar(provenance, fieldConfidences, "full_name", fullName);

        // ---- emails ---- (union, deduped, each tracked for confidence purposes)
        Map<String, Set<SourceType>> emailSources = new LinkedHashMap<>();
        for (RawCandidateData s : sources) {
            for (RawValue<String> rv : s.emails()) {
                String norm = EmailNormalizer.normalize(rv.value());
                if (norm != null) {
                    emailSources.computeIfAbsent(norm, k -> new LinkedHashSet<>()).add(s.sourceType());
                }
            }
        }
        List<String> emails = new ArrayList<>(emailSources.keySet());
        double emailFieldConfidence = emails.isEmpty() ? 0.0
                : emailSources.values().stream()
                    .mapToDouble(set -> confidenceEngine.fieldConfidence(bestSource(set), set))
                    .max().orElse(0.0);
        if (!emails.isEmpty()) {
            fieldConfidences.put("emails", emailFieldConfidence);
            provenance.record("emails", describeSources(emailSources.values()), "union of normalized, deduplicated emails across all sources");
        }

        // ---- phones ----
        Map<String, Set<SourceType>> phoneSources = new LinkedHashMap<>();
        for (RawCandidateData s : sources) {
            for (RawValue<String> rv : s.phones()) {
                String norm = PhoneNormalizer.normalize(rv.value());
                if (norm != null) {
                    phoneSources.computeIfAbsent(norm, k -> new LinkedHashSet<>()).add(s.sourceType());
                }
            }
        }
        List<String> phones = new ArrayList<>(phoneSources.keySet());
        double phoneFieldConfidence = phones.isEmpty() ? 0.0
                : phoneSources.values().stream()
                    .mapToDouble(set -> confidenceEngine.fieldConfidence(bestSource(set), set))
                    .max().orElse(0.0);
        if (!phones.isEmpty()) {
            fieldConfidences.put("phones", phoneFieldConfidence);
            provenance.record("phones", describeSources(phoneSources.values()), "union of E.164-normalized, deduplicated phones across all sources");
        }

        // ---- location ----
        MergeResult<String> city = scalarMerger.merge(collect(sources, RawCandidateData::city, v -> v));
        MergeResult<String> region = scalarMerger.merge(collect(sources, RawCandidateData::region, v -> v));
        MergeResult<String> country = scalarMerger.merge(collect(sources, RawCandidateData::country, CountryNormalizer::normalize));
        recordScalar(provenance, fieldConfidences, "location.city", city);
        recordScalar(provenance, fieldConfidences, "location.region", region);
        recordScalar(provenance, fieldConfidences, "location.country", country);
        Location location = new Location(city.value(), region.value(), country.value());

        // ---- links ----
        MergeResult<String> linkedin = scalarMerger.merge(collect(sources, RawCandidateData::linkedin, UrlNormalizer::normalizeLinkedIn));
        MergeResult<String> github = scalarMerger.merge(collect(sources, RawCandidateData::github, UrlNormalizer::normalizeGitHub));
        MergeResult<String> portfolio = scalarMerger.merge(collect(sources, RawCandidateData::portfolio, UrlNormalizer::normalizeGeneric));
        recordScalar(provenance, fieldConfidences, "links.linkedin", linkedin);
        recordScalar(provenance, fieldConfidences, "links.github", github);
        recordScalar(provenance, fieldConfidences, "links.portfolio", portfolio);

        Set<String> otherLinks = new LinkedHashSet<>();
        for (RawCandidateData s : sources) {
            for (RawValue<String> rv : s.otherLinks()) {
                String norm = UrlNormalizer.normalizeGeneric(rv.value());
                if (norm != null) {
                    otherLinks.add(norm);
                }
            }
        }
        Links links = new Links(linkedin.value(), github.value(), portfolio.value(), new ArrayList<>(otherLinks));

        // ---- headline ----
        MergeResult<String> headline = scalarMerger.merge(collect(sources, RawCandidateData::headline, this::cleanText));
        recordScalar(provenance, fieldConfidences, "headline", headline);

        // ---- years_experience ----
        List<Candidate<Double>> yearsCandidates = new ArrayList<>();
        for (RawCandidateData s : sources) {
            if (s.yearsExperience() != null && s.yearsExperience().value() != null) {
                double rounded = Math.round(s.yearsExperience().value() * 2.0) / 2.0; // nearest 0.5 for grouping
                yearsCandidates.add(new Candidate<>(rounded, s.sourceType(), s.sourceIdentifier(), s.yearsExperience().method()));
            }
        }
        MergeResult<Double> years = scalarMerger.merge(yearsCandidates);
        recordScalar(provenance, fieldConfidences, "years_experience", years);

        // ---- skills ----
        Map<String, Set<SourceType>> skillSources = new LinkedHashMap<>();
        Map<String, Set<String>> skillSourceIds = new LinkedHashMap<>();
        for (RawCandidateData s : sources) {
            for (RawValue<String> rv : s.skills()) {
                String canonical = SkillNormalizer.normalize(rv.value());
                if (canonical != null) {
                    skillSources.computeIfAbsent(canonical, k -> new LinkedHashSet<>()).add(s.sourceType());
                    skillSourceIds.computeIfAbsent(canonical, k -> new LinkedHashSet<>()).add(s.sourceIdentifier());
                }
            }
        }
        List<Skill> skills = skillSources.entrySet().stream()
                .map(e -> new Skill(e.getKey(), confidenceEngine.skillConfidence(e.getValue()),
                        new ArrayList<>(skillSourceIds.get(e.getKey()))))
                .sorted(Comparator.comparing(Skill::name))
                .collect(Collectors.toList());
        if (!skills.isEmpty()) {
            double avgSkillConfidence = skills.stream().mapToDouble(Skill::confidence).average().orElse(0.0);
            fieldConfidences.put("skills", avgSkillConfidence);
            for (Skill skill : skills) {
                provenance.record("skills[\"" + skill.name() + "\"]", String.join(", ", skill.sources()),
                        "canonicalized via skill-aliases mapping; confidence from " + skill.sources().size() + " corroborating source(s)");
            }
        }

        // ---- experience ----
        List<Experience> experience = mergeExperience(sources, provenance);
        if (!experience.isEmpty()) {
            fieldConfidences.put("experience", experience.size() > 1 ? 0.85 : 0.7);
        }

        // ---- education ----
        List<Education> education = mergeEducation(sources, provenance);
        if (!education.isEmpty()) {
            fieldConfidences.put("education", education.size() > 1 ? 0.85 : 0.7);
        }

        Map<String, Double> weights = Map.ofEntries(
                Map.entry("full_name", 1.5),
                Map.entry("emails", 1.5),
                Map.entry("phones", 1.0),
                Map.entry("skills", 1.5),
                Map.entry("experience", 1.2),
                Map.entry("education", 0.8),
                Map.entry("headline", 0.6),
                Map.entry("years_experience", 0.6),
                Map.entry("location.city", 0.4),
                Map.entry("location.region", 0.3),
                Map.entry("location.country", 0.4),
                Map.entry("links.linkedin", 0.3),
                Map.entry("links.github", 0.3),
                Map.entry("links.portfolio", 0.2)
        );
        double overall = confidenceEngine.overallConfidence(fieldConfidences, weights);

        return new CandidateProfile(
                candidateId,
                fullName.value(),
                emails,
                phones,
                location,
                links,
                headline.value(),
                years.value(),
                skills,
                experience,
                education,
                provenance.entries(),
                overall
        );
    }

    private SourceType bestSource(Set<SourceType> sources) {
        return sources.stream().min(Comparator.comparingInt(SourceType::priority)).orElseThrow();
    }

    private String describeSources(Collection<Set<SourceType>> sourceSets) {
        Set<SourceType> all = new TreeSet<>(Comparator.comparingInt(SourceType::priority));
        sourceSets.forEach(all::addAll);
        return all.stream().map(Enum::toString).collect(Collectors.joining(", "));
    }

    private String cleanText(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Candidate<String>> collect(List<RawCandidateData> sources,
                                             java.util.function.Function<RawCandidateData, RawValue<String>> extractor,
                                             java.util.function.Function<String, String> normalizer) {
        List<Candidate<String>> result = new ArrayList<>();
        for (RawCandidateData s : sources) {
            RawValue<String> rv = extractor.apply(s);
            if (rv != null) {
                String norm = normalizer.apply(rv.value());
                if (norm != null) {
                    result.add(new Candidate<>(norm, s.sourceType(), s.sourceIdentifier(), rv.method()));
                }
            }
        }
        return result;
    }

    private void recordScalar(ProvenanceTracker provenance, Map<String, Double> fieldConfidences,
                               String fieldName, MergeResult<?> result) {
        if (!result.isPresent()) {
            return;
        }
        fieldConfidences.put(fieldName, confidenceEngine.fieldConfidence(result.winningSource(), result.contributingSources()));
        provenance.record(fieldName, result.winningSourceId(), result.method());
    }

    private List<Experience> mergeExperience(List<RawCandidateData> sources, ProvenanceTracker provenance) {
        // key -> best entry seen so far, with completeness score and originating source priority
        record Scored(Experience exp, int completeness, int priority, String sourceId) {
        }
        Map<String, Scored> best = new LinkedHashMap<>();

        for (RawCandidateData s : sources) {
            for (RawValue<RawExperience> rv : s.experience()) {
                RawExperience raw = rv.value();
                String company = cleanText(raw.company());
                String title = cleanText(raw.title());
                if (company == null && title == null) continue;
                String start = DateNormalizer.normalize(raw.start());
                String end = DateNormalizer.normalize(raw.end());
                String summary = cleanText(raw.summary());

                Experience exp = new Experience(company, title, start, end, summary);
                String key = (company == null ? "" : company.toLowerCase()) + "|" + (title == null ? "" : title.toLowerCase());

                int completeness = (company != null ? 1 : 0) + (title != null ? 1 : 0)
                        + (start != null ? 1 : 0) + (end != null ? 1 : 0) + (summary != null ? 1 : 0);

                Scored candidate = new Scored(exp, completeness, s.sourceType().priority(), s.sourceIdentifier());
                Scored existing = best.get(key);
                if (existing == null
                        || candidate.completeness() > existing.completeness()
                        || (candidate.completeness() == existing.completeness() && candidate.priority() < existing.priority())) {
                    best.put(key, candidate);
                }
            }
        }

        List<Experience> result = best.values().stream()
                .map(Scored::exp)
                .sorted(Comparator.comparing(Experience::sortKey).reversed())
                .collect(Collectors.toList());

        for (var entry : best.entrySet()) {
            Scored scored = entry.getValue();
            provenance.record("experience[" + scored.exp().company() + "/" + scored.exp().title() + "]",
                    scored.sourceId(), "deduplicated by (company,title); most complete entry kept (completeness score " + scored.completeness() + ")");
        }
        return result;
    }

    private List<Education> mergeEducation(List<RawCandidateData> sources, ProvenanceTracker provenance) {
        record Scored(Education edu, int completeness, int priority, String sourceId) {
        }
        Map<String, Scored> best = new LinkedHashMap<>();

        for (RawCandidateData s : sources) {
            for (RawValue<RawEducation> rv : s.education()) {
                RawEducation raw = rv.value();
                String institution = cleanText(raw.institution());
                String degree = cleanText(raw.degree());
                if (institution == null && degree == null) continue;
                String field = cleanText(raw.field());
                Integer endYear = parseYear(raw.endYear());

                Education edu = new Education(institution, degree, field, endYear);
                String key = (institution == null ? "" : institution.toLowerCase()) + "|" + (degree == null ? "" : degree.toLowerCase());

                int completeness = (institution != null ? 1 : 0) + (degree != null ? 1 : 0)
                        + (field != null ? 1 : 0) + (endYear != null ? 1 : 0);

                Scored candidate = new Scored(edu, completeness, s.sourceType().priority(), s.sourceIdentifier());
                Scored existing = best.get(key);
                if (existing == null
                        || candidate.completeness() > existing.completeness()
                        || (candidate.completeness() == existing.completeness() && candidate.priority() < existing.priority())) {
                    best.put(key, candidate);
                }
            }
        }

        List<Education> result = best.values().stream()
                .map(Scored::edu)
                .sorted(Comparator.comparingInt(Education::sortKey).reversed())
                .collect(Collectors.toList());

        for (var entry : best.entrySet()) {
            Scored scored = entry.getValue();
            provenance.record("education[" + scored.edu().institution() + "]",
                    scored.sourceId(), "deduplicated by (institution,degree); most complete entry kept (completeness score " + scored.completeness() + ")");
        }
        return result;
    }

    private Integer parseYear(String raw) {
        if (raw == null) return null;
        try {
            String digits = raw.trim().replaceAll("[^0-9]", "");
            if (digits.length() < 4) return null;
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
