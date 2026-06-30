package com.eightfold.transformer.application;

import com.eightfold.transformer.config.OutputConfig;
import com.eightfold.transformer.merge.MergeEngine;
import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.parser.ParserFactory;
import com.eightfold.transformer.parser.SourceParser;
import com.eightfold.transformer.projection.ProjectionEngine;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.util.ConfigurationException;
import com.eightfold.transformer.validation.ProfileValidator;
import com.eightfold.transformer.validation.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransformerPipeline {

    private static final Logger log = LoggerFactory.getLogger(TransformerPipeline.class);

    private final ParserFactory parserFactory = new ParserFactory();
    private final MergeEngine mergeEngine = new MergeEngine();
    private final ProfileValidator validator = new ProfileValidator();
    private final ProjectionEngine projectionEngine = new ProjectionEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    public PipelineResult run(PipelineInput input) {
        List<RawCandidateData> rawSources = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        safeParse(input.recruiterCsv(), parserFactory.forRecruiterCsv(), rawSources, skipped);
        safeParse(input.atsJson(), parserFactory.forAtsJson(), rawSources, skipped);
        for (Path resume : input.resumes()) {
            safeParse(resume, parserFactory.forResume(), rawSources, skipped);
        }
        for (Path notes : input.recruiterNotes()) {
            safeParse(notes, parserFactory.forRecruiterNotes(), rawSources, skipped);
        }

        String candidateId = input.candidateId() != null && !input.candidateId().isBlank()
                ? input.candidateId()
                : deterministicCandidateId(rawSources);

        CandidateProfile profile = mergeEngine.merge(candidateId, rawSources);

        List<ValidationError> validationErrors = validator.validate(profile);
        for (ValidationError e : validationErrors) {
            if (e.severity() == ValidationError.Severity.ERROR) {
                log.warn("Validation ERROR: [{}] {}", e.field(), e.message());
            } else {
                log.info("Validation warning: [{}] {}", e.field(), e.message());
            }
        }

        OutputConfig config = loadConfig(input.configPath());
        ObjectNode projected = projectionEngine.project(profile, config);

        return new PipelineResult(profile, projected, validationErrors, skipped);
    }

    private void safeParse(Path file, SourceParser parser, List<RawCandidateData> sink, List<String> skipped) {
        if (file == null) {
            return;
        }
        if (!Files.exists(file)) {
            log.warn("Input file does not exist, skipping: {}", file);
            skipped.add(file.toString() + " (does not exist)");
            return;
        }
        try {
            RawCandidateData data = parser.parse(file);
            if (data.isMalformed()) {
                log.warn("Source {} parsed but is malformed: {}", file, data.malformedReason());
                skipped.add(file.toString() + " (" + data.malformedReason() + ")");
            } else {
                sink.add(data);
            }
        } catch (Exception e) {
            // Defense in depth: even if a parser implementation has a bug and throws,
            // the pipeline must never crash because of one bad source.
            log.error("Unexpected error parsing {}, skipping source: {}", file, e.getMessage(), e);
            skipped.add(file.toString() + " (unexpected parser error: " + e.getMessage() + ")");
        }
    }

    private OutputConfig loadConfig(Path configPath) {
        if (configPath == null) {
            return OutputConfig.defaultSchema();
        }
        try {
            String json = Files.readString(configPath);
            return mapper.readValue(json, OutputConfig.class);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load/parse output config at " + configPath + ": " + e.getMessage(), e);
        }
    }

    /** Deterministic candidate id derived from the first available identifying signal, falling back to a random UUID. */
    private String deterministicCandidateId(List<RawCandidateData> rawSources) {
        for (RawCandidateData s : rawSources) {
            if (!s.emails().isEmpty()) {
                return "cand_" + Integer.toHexString(s.emails().get(0).value().toLowerCase().hashCode());
            }
        }
        for (RawCandidateData s : rawSources) {
            if (s.fullName() != null) {
                return "cand_" + Integer.toHexString(s.fullName().value().toLowerCase().hashCode());
            }
        }
        return "cand_" + UUID.randomUUID();
    }
}
