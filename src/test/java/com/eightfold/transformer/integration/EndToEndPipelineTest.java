package com.eightfold.transformer.integration;

import com.eightfold.transformer.application.PipelineInput;
import com.eightfold.transformer.application.PipelineResult;
import com.eightfold.transformer.application.TransformerPipeline;
import com.eightfold.transformer.util.ConfigurationException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EndToEndPipelineTest {

    private static final Path SAMPLE_DIR = Path.of("sample-data");

    private final TransformerPipeline pipeline = new TransformerPipeline();

    @Test
    void runsEndToEndOnAllSampleSourcesWithDefaultSchema() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("recruiter.csv"))
                .atsJson(SAMPLE_DIR.resolve("ats.json"))
                .addResume(SAMPLE_DIR.resolve("resume.pdf"))
                .addRecruiterNotes(SAMPLE_DIR.resolve("recruiter_notes.txt"));

        PipelineResult result = pipeline.run(input);

        assertThat(result.canonicalProfile().fullName()).isNotBlank();
        assertThat(result.canonicalProfile().emails()).isNotEmpty();
        assertThat(result.canonicalProfile().skills()).isNotEmpty();
        assertThat(result.canonicalProfile().provenance()).isNotEmpty();
        assertThat(result.canonicalProfile().overallConfidence()).isGreaterThan(0.0);

        ObjectNode output = result.projectedOutput();
        assertThat(output.has("candidate_id")).isTrue();
        assertThat(output.has("skills")).isTrue();
    }

    @Test
    void runsEndToEndWithCustomConfig() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("recruiter.csv"))
                .atsJson(SAMPLE_DIR.resolve("ats.json"))
                .addResume(SAMPLE_DIR.resolve("resume.pdf"))
                .configPath(SAMPLE_DIR.resolve("custom-config.json"));

        PipelineResult result = pipeline.run(input);
        ObjectNode output = result.projectedOutput();

        assertThat(output.has("full_name")).isTrue();
        assertThat(output.has("primary_email")).isTrue();
        assertThat(output.has("provenance")).isFalse(); // include_provenance=false in custom-config.json
    }

    @Test
    void docxResumeParsesSuccessfully() {
        PipelineInput input = new PipelineInput()
                .addResume(SAMPLE_DIR.resolve("resume.docx"));

        PipelineResult result = assertDoesNotThrow(() -> pipeline.run(input));
        assertThat(result.canonicalProfile().fullName()).isNotBlank();
    }

    @Test
    void malformedCsvIsSkippedWithoutCrashing() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("malformed_recruiter.csv"))
                .atsJson(SAMPLE_DIR.resolve("ats.json"));

        PipelineResult result = assertDoesNotThrow(() -> pipeline.run(input));
        assertThat(result.skippedSources()).isNotEmpty();
        // ATS JSON still contributed - full name should still resolve.
        assertThat(result.canonicalProfile().fullName()).isNotBlank();
    }

    @Test
    void malformedAtsJsonIsSkippedWithoutCrashing() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("recruiter.csv"))
                .atsJson(SAMPLE_DIR.resolve("malformed_ats.json"));

        PipelineResult result = assertDoesNotThrow(() -> pipeline.run(input));
        assertThat(result.skippedSources()).isNotEmpty();
        assertThat(result.canonicalProfile().fullName()).isNotBlank();
    }

    @Test
    void missingSourceFileDoesNotCrash() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("does_not_exist.csv"))
                .atsJson(SAMPLE_DIR.resolve("ats.json"));

        PipelineResult result = assertDoesNotThrow(() -> pipeline.run(input));
        assertThat(result.skippedSources()).anyMatch(s -> s.contains("does_not_exist.csv"));
        assertThat(result.canonicalProfile().fullName()).isNotBlank();
    }

    @Test
    void emptyNotesFileDoesNotCrash() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("recruiter.csv"))
                .addRecruiterNotes(SAMPLE_DIR.resolve("empty_notes.txt"));

        assertDoesNotThrow(() -> pipeline.run(input));
    }

    @Test
    void noSourcesAtAllProducesEmptyProfileWithoutCrashing() {
        PipelineResult result = assertDoesNotThrow(() -> pipeline.run(new PipelineInput()));
        assertThat(result.canonicalProfile().fullName()).isNull();
        assertThat(result.canonicalProfile().overallConfidence()).isEqualTo(0.0);
    }

    @Test
    void invalidConfigPathThrowsConfigurationException() {
        PipelineInput input = new PipelineInput()
                .recruiterCsv(SAMPLE_DIR.resolve("recruiter.csv"))
                .configPath(SAMPLE_DIR.resolve("does_not_exist_config.json"));

        org.junit.jupiter.api.Assertions.assertThrows(ConfigurationException.class, () -> pipeline.run(input));
    }

    @Test
    void strictConfigOnMissingErrorThrowsWhenRequiredFieldMissing() {
        // Only a notes file (no name/email reliably resolvable) + a strict config requiring full_name/email.
        PipelineInput input = new PipelineInput()
                .addRecruiterNotes(SAMPLE_DIR.resolve("empty_notes.txt"))
                .configPath(SAMPLE_DIR.resolve("custom-config-strict.json"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.eightfold.transformer.util.ProfileValidationException.class,
                () -> pipeline.run(input));
    }
}
