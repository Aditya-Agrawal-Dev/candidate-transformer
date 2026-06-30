package com.eightfold.transformer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a runtime projection config's {@code "fields"} array.
 *
 * @param path      the OUTPUT key/path to emit (dot-notation allowed for nested output, e.g. "contact.email")
 * @param from      the CANONICAL source path to read from (e.g. "emails[0]", "skills[].name");
 *                  defaults to {@code path} when omitted, matching the assignment's example config
 * @param type      declared output type, used for validation ("string", "number", "boolean",
 *                  "string[]", "number[]", "object[]")
 * @param required  if true and the resolved value is missing, triggers {@code on_missing} handling
 * @param normalize optional normalization hint re-applied at projection time
 *                  ("E164", "canonical", "ISO3166", "YYYY-MM"); values are already
 *                  normalized canonically, so this mostly guards against drift if the
 *                  canonical model ever changes, and lets a config request a DIFFERENT
 *                  normalization (e.g. raw original format) in the future
 */
public record FieldSpec(
        @JsonProperty("path") String path,
        @JsonProperty("from") String from,
        @JsonProperty("type") String type,
        @JsonProperty("required") boolean required,
        @JsonProperty("normalize") String normalize
) {
    @JsonCreator
    public FieldSpec {
        if (from == null || from.isBlank()) {
            from = path;
        }
    }
}
