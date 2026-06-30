package com.eightfold.transformer.cli;

import com.eightfold.transformer.application.PipelineInput;
import com.eightfold.transformer.application.PipelineResult;
import com.eightfold.transformer.application.TransformerPipeline;
import com.eightfold.transformer.util.ConfigurationException;
import com.eightfold.transformer.util.ProfileValidationException;
import com.eightfold.transformer.validation.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point.
 * <pre>
 * java -jar transformer.jar \
 *   --csv recruiter.csv \
 *   --ats-json ats.json \
 *   --resume resume.pdf \
 *   --notes recruiter_notes.txt \
 *   --config custom.json \
 *   --out output.json \
 *   --candidate-id cand_123
 * </pre>
 * {@code --resume} and {@code --notes} may each be repeated for multiple files.
 * Only {@code --csv}/{@code --ats-json} (structured) and {@code --resume}/{@code --notes}
 * (unstructured) need at least one present between them; any single flag may be omitted.
 */
public final class TransformerCli {

    public static void main(String[] args) {
        int exitCode = new TransformerCli().run(args, System.out, System.err);
        System.exit(exitCode);
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage(out);
            return args.length == 0 ? 1 : 0;
        }

        PipelineInput input = new PipelineInput();
        Path outputFile = null;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--csv" -> input.recruiterCsv(requirePath(args, ++i, "--csv"));
                    case "--ats-json", "--ats" -> input.atsJson(requirePath(args, ++i, "--ats-json"));
                    case "--resume" -> input.addResume(requirePath(args, ++i, "--resume"));
                    case "--notes" -> input.addRecruiterNotes(requirePath(args, ++i, "--notes"));
                    case "--config" -> input.configPath(requirePath(args, ++i, "--config"));
                    case "--out" -> outputFile = requirePath(args, ++i, "--out");
                    case "--candidate-id" -> input.candidateId(requireArg(args, ++i, "--candidate-id"));
                    default -> {
                        err.println("Unknown argument: " + arg);
                        printUsage(err);
                        return 1;
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            printUsage(err);
            return 1;
        }

        if (input.recruiterCsv() == null && input.atsJson() == null) {
            err.println("Warning: no structured source provided (--csv or --ats-json). Proceeding with unstructured sources only.");
        }
        if (input.resumes().isEmpty() && input.recruiterNotes().isEmpty()) {
            err.println("Warning: no unstructured source provided (--resume or --notes). Proceeding with structured sources only.");
        }

        TransformerPipeline pipeline = new TransformerPipeline();
        PipelineResult result;
        try {
            result = pipeline.run(input);
        } catch (ProfileValidationException e) {
            err.println("Validation failed (on_missing=error): " + e.getMessage());
            for (ValidationError ve : e.errors()) {
                err.println("  - [" + ve.field() + "] " + ve.message());
            }
            return 2;
        } catch (ConfigurationException e) {
            err.println("Configuration error: " + e.getMessage());
            return 3;
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json;
        try {
            json = mapper.writeValueAsString(result.projectedOutput());
        } catch (Exception e) {
            err.println("Failed to serialize output: " + e.getMessage());
            return 4;
        }

        if (!result.skippedSources().isEmpty()) {
            err.println("Skipped " + result.skippedSources().size() + " source(s):");
            for (String s : result.skippedSources()) {
                err.println("  - " + s);
            }
        }
        long blockingErrors = result.validationErrors().stream()
                .filter(v -> v.severity() == ValidationError.Severity.ERROR).count();
        long warnings = result.validationErrors().stream()
                .filter(v -> v.severity() == ValidationError.Severity.WARNING).count();
        if (blockingErrors > 0 || warnings > 0) {
            err.println("Validation: " + blockingErrors + " error(s), " + warnings + " warning(s).");
            for (ValidationError v : result.validationErrors()) {
                err.println("  [" + v.severity() + "] " + v.field() + ": " + v.message());
            }
        }

        if (outputFile != null) {
            try {
                Files.writeString(outputFile, json, StandardCharsets.UTF_8);
                err.println("Wrote output to " + outputFile.toAbsolutePath());
            } catch (Exception e) {
                err.println("Failed to write output file: " + e.getMessage());
                return 5;
            }
        }

        out.println(json);
        return 0;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (a.equals(flag)) return true;
        }
        return false;
    }

    private Path requirePath(String[] args, int index, String flagName) {
        return Path.of(requireArg(args, index, flagName));
    }

    private String requireArg(String[] args, int index, String flagName) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flagName + " requires a value");
        }
        return args[index];
    }

    private void printUsage(PrintStream out) {
        out.println("""
                Multi-Source Candidate Data Transformer

                Usage:
                  java -jar transformer.jar [options]

                Options:
                  --csv <path>           Recruiter CSV export (structured)
                  --ats-json <path>      ATS JSON blob (structured)
                  --resume <path>        Resume file: .pdf, .docx, or .txt (unstructured, repeatable)
                  --notes <path>         Recruiter notes .txt (unstructured, repeatable)
                  --config <path>        Runtime output-projection config JSON (optional)
                  --out <path>           Write the output JSON to a file (optional; always printed to stdout too)
                  --candidate-id <id>    Override the generated candidate_id
                  --help, -h             Show this message

                At least one structured source (--csv or --ats-json) AND one unstructured
                source (--resume or --notes) is recommended for a useful merge, but any
                combination - including a single source - will run without crashing.

                Example:
                  java -jar transformer.jar --csv sample-data/recruiter.csv \\
                      --ats-json sample-data/ats.json \\
                      --resume sample-data/resume.pdf \\
                      --notes sample-data/recruiter_notes.txt \\
                      --config sample-data/custom-config.json \\
                      --out output.json
                """);
    }
}
