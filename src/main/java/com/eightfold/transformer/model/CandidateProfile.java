package com.eightfold.transformer.model;

import java.util.List;

/**
 * The canonical, merged candidate profile. This is the single internal source of
 * truth produced by the pipeline; all runtime-configurable output is PROJECTED
 * from this immutable record, never mutated in place.
 */
public record CandidateProfile(
        String candidateId,
        String fullName,
        List<String> emails,
        List<String> phones,
        Location location,
        Links links,
        String headline,
        Double yearsExperience,
        List<Skill> skills,
        List<Experience> experience,
        List<Education> education,
        List<Provenance> provenance,
        double overallConfidence
) {
    public CandidateProfile {
        emails = emails == null ? List.of() : List.copyOf(emails);
        phones = phones == null ? List.of() : List.copyOf(phones);
        skills = skills == null ? List.of() : List.copyOf(skills);
        experience = experience == null ? List.of() : List.copyOf(experience);
        education = education == null ? List.of() : List.copyOf(education);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
        location = location == null ? Location.EMPTY : location;
        links = links == null ? Links.EMPTY : links;
        overallConfidence = Math.max(0.0, Math.min(1.0, overallConfidence));
    }
}
