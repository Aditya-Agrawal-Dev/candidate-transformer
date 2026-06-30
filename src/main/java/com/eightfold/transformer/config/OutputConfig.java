package com.eightfold.transformer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserialized form of a runtime output-projection config, exactly matching the
 * assignment's example config shape:
 * <pre>
 * {
 *   "fields": [ { "path": ..., "from": ..., "type": ..., "required": ..., "normalize": ... } ],
 *   "include_confidence": true,
 *   "include_provenance": false,
 *   "on_missing": "null" | "omit" | "error"
 * }
 * </pre>
 */
public record OutputConfig(
        @JsonProperty("fields") List<FieldSpec> fields,
        @JsonProperty("include_confidence") boolean includeConfidence,
        @JsonProperty("include_provenance") boolean includeProvenance,
        @JsonProperty("on_missing") String onMissing
) {
    @JsonCreator
    public OutputConfig {
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (onMissing == null || onMissing.isBlank()) {
            onMissing = "null";
        }
    }

    public enum MissingPolicy { NULL, OMIT, ERROR }

    public MissingPolicy missingPolicy() {
        return switch (onMissing.toLowerCase()) {
            case "omit" -> MissingPolicy.OMIT;
            case "error" -> MissingPolicy.ERROR;
            default -> MissingPolicy.NULL;
        };
    }

    /** The full, unfiltered canonical schema: every field, confidence + provenance included, nulls preserved. */
    public static OutputConfig defaultSchema() {
        return new OutputConfig(List.of(), true, true, "null");
    }
}
