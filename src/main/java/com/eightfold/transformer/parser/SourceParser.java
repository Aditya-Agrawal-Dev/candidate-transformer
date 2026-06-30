package com.eightfold.transformer.parser;

import com.eightfold.transformer.source.RawCandidateData;

import java.nio.file.Path;

/**
 * Strategy interface for turning one raw input file into a {@link RawCandidateData}.
 * Implementations must NEVER throw for malformed content - they catch their own
 * parsing failures and return a RawCandidateData marked malformed via
 * {@link RawCandidateData#markMalformed(String)} instead, so a single bad source
 * never crashes the whole pipeline run.
 */
public interface SourceParser {

    /** @return true if this parser can handle the given file (by extension/content sniff). */
    boolean supports(Path file);

    /** Parses the file into a raw, pre-normalization candidate data bag. Never throws. */
    RawCandidateData parse(Path file);
}
