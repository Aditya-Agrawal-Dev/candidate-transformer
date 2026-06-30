package com.eightfold.transformer.model;

import java.util.List;

/**
 * A canonicalized skill with a per-field confidence score and the list of sources
 * that corroborated it.
 *
 * @param name       canonical skill name (e.g. "JavaScript", not "js")
 * @param confidence 0.0-1.0 confidence that the candidate genuinely has this skill
 * @param sources    distinct source identifiers (e.g. "recruiter.csv", "resume.pdf")
 *                   that mentioned this skill
 */
public record Skill(String name, double confidence, List<String> sources) {

    public Skill {
        sources = sources == null ? List.of() : List.copyOf(sources);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
}
