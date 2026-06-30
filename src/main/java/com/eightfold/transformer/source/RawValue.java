package com.eightfold.transformer.source;

/**
 * A single raw, not-yet-normalized value pulled out of a source document,
 * tagged with the extraction technique used to find it. This is the atomic
 * unit the normalization and merge layers consume.
 *
 * @param value  the raw string value as found in the source (never null - absence
 *               is represented by simply not adding a RawValue, not by a null value)
 * @param method human-readable extraction method, used for provenance
 */
public record RawValue<T>(T value, String method) {
    public static <T> RawValue<T> of(T value, String method) {
        return new RawValue<>(value, method);
    }
}
