package com.eightfold.transformer.projection;

import com.eightfold.transformer.normalization.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Re-applies a config-requested normalization at projection time. Canonical
 * values are already normalized, so for the canonical normalize hints this is
 * idempotent - but it keeps the projection layer honest/self-describing (a
 * config that says {@code "normalize": "E164"} is GUARANTEED that shape on
 * output, independent of what the canonical model happens to store today).
 */
public final class NormalizationReapplier {

    private NormalizationReapplier() {
    }

    @SuppressWarnings("unchecked")
    public static Object apply(Object value, String normalizeHint) {
        if (value == null || normalizeHint == null) {
            return value;
        }
        return switch (normalizeHint.toUpperCase()) {
            case "E164" -> applyScalarOrList(value, v -> PhoneNormalizer.normalize((String) v));
            case "CANONICAL" -> applyScalarOrList(value, v -> SkillNormalizer.normalize((String) v));
            case "ISO3166" -> applyScalarOrList(value, v -> CountryNormalizer.normalize((String) v));
            case "YYYY-MM" -> applyScalarOrList(value, v -> DateNormalizer.normalize((String) v));
            case "LOWERCASE_TRIM" -> applyScalarOrList(value, v -> EmailNormalizer.normalize((String) v));
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    private static Object applyScalarOrList(Object value, java.util.function.Function<Object, Object> fn) {
        if (value instanceof List<?> list) {
            return list.stream().map(fn).collect(Collectors.toList());
        }
        return fn.apply(value);
    }
}
