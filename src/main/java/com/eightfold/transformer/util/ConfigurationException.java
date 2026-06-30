package com.eightfold.transformer.util;

/** Thrown when the runtime projection config itself is invalid (bad JSON, unknown field path, etc). */
public class ConfigurationException extends TransformerException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
