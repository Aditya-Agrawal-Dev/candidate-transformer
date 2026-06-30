package com.eightfold.transformer.merge;

import com.eightfold.transformer.model.SourceType;

/**
 * One normalized, already-validated candidate value for a single field, tagged
 * with where it came from. This is the unit the merge engine reasons about -
 * by the time a value becomes a {@code Candidate}, it has already passed
 * normalization and basic validation, so the merge engine never has to worry
 * about malformed data, only about WHICH valid value to trust.
 */
public record Candidate<T>(T value, SourceType source, String sourceId, String extractionMethod) {
}
