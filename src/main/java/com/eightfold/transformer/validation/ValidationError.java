package com.eightfold.transformer.validation;

/**
 * A single validation finding.
 *
 * @param field    the field path the error relates to
 * @param message  human-readable description
 * @param severity ERROR (blocks output if on_missing=error / required-field violation)
 *                 or WARNING (surfaced but does not block output)
 */
public record ValidationError(String field, String message, Severity severity) {
    public enum Severity { ERROR, WARNING }

    public static ValidationError error(String field, String message) {
        return new ValidationError(field, message, Severity.ERROR);
    }

    public static ValidationError warning(String field, String message) {
        return new ValidationError(field, message, Severity.WARNING);
    }
}
