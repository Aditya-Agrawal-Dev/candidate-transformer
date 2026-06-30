package com.eightfold.transformer.projection;

import com.eightfold.transformer.model.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves a canonical field path (e.g. {@code "emails[0]"}, {@code "skills[].name"},
 * {@code "location.city"}) against a {@link CandidateProfile} and returns the raw
 * Java value (String/Double/List/etc). This is the ONLY place that knows how to
 * walk the canonical model by string path, which is what makes the projection
 * layer fully independent of (and decoupled from) the canonical model's Java
 * shape: add a config-only "from" path and it just works, no code changes.
 */
public final class CanonicalPathResolver {

    private static final Pattern INDEXED = Pattern.compile("^([a-zA-Z_]+)\\[(\\d+)]$");
    private static final Pattern WILDCARD_SUBFIELD = Pattern.compile("^([a-zA-Z_]+)\\[]\\.([a-zA-Z_]+)$");
    private static final Pattern WILDCARD = Pattern.compile("^([a-zA-Z_]+)\\[]$");

    /** @return the resolved value: a scalar (String/Double/Integer/Double), a List, or null if not present. */
    public Object resolve(CandidateProfile profile, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        Matcher wildcardSub = WILDCARD_SUBFIELD.matcher(path);
        if (wildcardSub.matches()) {
            return resolveListOfSubfield(profile, wildcardSub.group(1), wildcardSub.group(2));
        }

        Matcher wildcard = WILDCARD.matcher(path);
        if (wildcard.matches()) {
            return resolveWholeList(profile, wildcard.group(1));
        }

        Matcher indexed = INDEXED.matcher(path);
        if (indexed.matches()) {
            List<?> list = (List<?>) resolveWholeList(profile, indexed.group(1));
            int idx = Integer.parseInt(indexed.group(2));
            return (list != null && idx < list.size()) ? list.get(idx) : null;
        }

        return resolveDotted(profile, path);
    }

    private Object resolveWholeList(CandidateProfile profile, String name) {
        return switch (name) {
            case "emails" -> profile.emails();
            case "phones" -> profile.phones();
            case "skills" -> profile.skills();
            case "experience" -> profile.experience();
            case "education" -> profile.education();
            case "provenance" -> profile.provenance();
            case "other" -> profile.links() != null ? profile.links().other() : List.of();
            default -> null;
        };
    }

    private Object resolveListOfSubfield(CandidateProfile profile, String listName, String subfield) {
        return switch (listName) {
            case "skills" -> profile.skills().stream().map(s -> resolveSkillSubfield(s, subfield)).collect(Collectors.toList());
            case "experience" -> profile.experience().stream().map(e -> resolveExperienceSubfield(e, subfield)).collect(Collectors.toList());
            case "education" -> profile.education().stream().map(e -> resolveEducationSubfield(e, subfield)).collect(Collectors.toList());
            case "provenance" -> profile.provenance().stream().map(p -> resolveProvenanceSubfield(p, subfield)).collect(Collectors.toList());
            default -> null;
        };
    }

    private Object resolveSkillSubfield(Skill s, String field) {
        return switch (field) {
            case "name" -> s.name();
            case "confidence" -> s.confidence();
            case "sources" -> s.sources();
            default -> null;
        };
    }

    private Object resolveExperienceSubfield(Experience e, String field) {
        return switch (field) {
            case "company" -> e.company();
            case "title" -> e.title();
            case "start" -> e.start();
            case "end" -> e.end();
            case "summary" -> e.summary();
            default -> null;
        };
    }

    private Object resolveEducationSubfield(Education e, String field) {
        return switch (field) {
            case "institution" -> e.institution();
            case "degree" -> e.degree();
            case "field" -> e.field();
            case "end_year" -> e.endYear();
            default -> null;
        };
    }

    private Object resolveProvenanceSubfield(Provenance p, String field) {
        return switch (field) {
            case "field" -> p.field();
            case "source" -> p.source();
            case "method" -> p.method();
            default -> null;
        };
    }

    private Object resolveDotted(CandidateProfile profile, String path) {
        return switch (path) {
            case "candidate_id" -> profile.candidateId();
            case "full_name" -> profile.fullName();
            case "headline" -> profile.headline();
            case "years_experience" -> profile.yearsExperience();
            case "overall_confidence" -> profile.overallConfidence();
            case "emails" -> profile.emails();
            case "phones" -> profile.phones();
            case "skills" -> profile.skills();
            case "experience" -> profile.experience();
            case "education" -> profile.education();
            case "provenance" -> profile.provenance();
            case "location" -> profile.location();
            case "location.city" -> profile.location() != null ? profile.location().city() : null;
            case "location.region" -> profile.location() != null ? profile.location().region() : null;
            case "location.country" -> profile.location() != null ? profile.location().country() : null;
            case "links" -> profile.links();
            case "links.linkedin" -> profile.links() != null ? profile.links().linkedin() : null;
            case "links.github" -> profile.links() != null ? profile.links().github() : null;
            case "links.portfolio" -> profile.links() != null ? profile.links().portfolio() : null;
            case "links.other" -> profile.links() != null ? profile.links().other() : List.of();
            default -> null;
        };
    }
}
