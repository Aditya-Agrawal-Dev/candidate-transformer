package com.eightfold.transformer.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * All inputs for one pipeline run: zero or more files of each source role, plus
 * an optional candidate id and an optional output-projection config path.
 * Any source group may be empty - a missing source must never crash the run.
 */
public final class PipelineInput {

    private Path recruiterCsv;
    private Path atsJson;
    private final List<Path> resumes = new ArrayList<>();
    private final List<Path> recruiterNotes = new ArrayList<>();
    private Path configPath;
    private String candidateId;

    public Path recruiterCsv() {
        return recruiterCsv;
    }

    public PipelineInput recruiterCsv(Path path) {
        this.recruiterCsv = path;
        return this;
    }

    public Path atsJson() {
        return atsJson;
    }

    public PipelineInput atsJson(Path path) {
        this.atsJson = path;
        return this;
    }

    public List<Path> resumes() {
        return resumes;
    }

    public PipelineInput addResume(Path path) {
        resumes.add(path);
        return this;
    }

    public List<Path> recruiterNotes() {
        return recruiterNotes;
    }

    public PipelineInput addRecruiterNotes(Path path) {
        recruiterNotes.add(path);
        return this;
    }

    public Path configPath() {
        return configPath;
    }

    public PipelineInput configPath(Path path) {
        this.configPath = path;
        return this;
    }

    public String candidateId() {
        return candidateId;
    }

    public PipelineInput candidateId(String id) {
        this.candidateId = id;
        return this;
    }
}
