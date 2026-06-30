package com.eightfold.transformer.application;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.validation.ValidationError;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Everything a caller needs after running the pipeline once: the canonical
 * profile (for programmatic use/tests), the projected output JSON (what
 * actually gets printed/written), and any validation findings.
 */
public record PipelineResult(
        CandidateProfile canonicalProfile,
        ObjectNode projectedOutput,
        List<ValidationError> validationErrors,
        List<String> skippedSources
) {
    public boolean hasBlockingErrors() {
        return validationErrors.stream().anyMatch(e -> e.severity() == ValidationError.Severity.ERROR);
    }
}
