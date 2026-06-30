package com.eightfold.transformer.merge;

import com.eightfold.transformer.model.SourceType;

import java.util.Set;

/**
 * Outcome of resolving a single scalar field across multiple sources.
 *
 * @param value               the winning value (may be null if no source had it)
 * @param winningSource       the SourceType that produced the winning value
 * @param winningSourceId     the specific source identifier (e.g. "recruiter.csv")
 * @param contributingSources every distinct source type that independently agreed on this value (consensus set)
 * @param method              human-readable description of how this value was derived
 */
public record MergeResult<T>(T value, SourceType winningSource, String winningSourceId,
                              Set<SourceType> contributingSources, String method) {

    public static <T> MergeResult<T> empty() {
        return new MergeResult<>(null, null, null, Set.of(), "no source provided a usable value");
    }

    public boolean isPresent() {
        return value != null;
    }
}
