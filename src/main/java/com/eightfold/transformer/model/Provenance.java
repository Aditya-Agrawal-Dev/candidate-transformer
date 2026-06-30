package com.eightfold.transformer.model;

/**
 * Tracks exactly where a field's final value came from and how it was produced.
 *
 * @param field            canonical field path (e.g. "emails[0]", "skills[2].name")
 * @param source           the winning source identifier (e.g. "recruiter.csv")
 * @param method           human-readable description of extraction + normalization + merge strategy
 */
public record Provenance(String field, String source, String method) {
}
