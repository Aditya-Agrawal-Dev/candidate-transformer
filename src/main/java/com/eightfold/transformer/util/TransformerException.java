package com.eightfold.transformer.util;

/** Root of the application's checked-by-convention exception hierarchy. */
public class TransformerException extends RuntimeException {
    public TransformerException(String message) {
        super(message);
    }

    public TransformerException(String message, Throwable cause) {
        super(message, cause);
    }
}
