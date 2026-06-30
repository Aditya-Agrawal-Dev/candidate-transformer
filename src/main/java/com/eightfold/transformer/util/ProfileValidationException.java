package com.eightfold.transformer.util;

import com.eightfold.transformer.validation.ValidationError;

import java.util.List;

/** Thrown by the validation layer when {@code on_missing=error} is configured and triggered, or schema validation fails fatally. */
public class ProfileValidationException extends TransformerException {

    private final List<ValidationError> errors;

    public ProfileValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
