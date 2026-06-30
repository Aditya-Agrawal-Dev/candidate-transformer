package com.eightfold.transformer.util;

/**
 * Thrown internally by a parser when a source is malformed beyond salvage.
 * Callers (the pipeline orchestrator) catch this and degrade gracefully by
 * marking the source as malformed rather than letting it crash the run.
 */
public class SourceParsingException extends TransformerException {
    public SourceParsingException(String message) {
        super(message);
    }

    public SourceParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
