package com.eightfold.transformer.merge;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.model.SourceType;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MergeEngineTest {

    private final MergeEngine engine = new MergeEngine();

    @Test
    void mergesConflictingEmailsAndPhonesAcrossSources() {
        RawCandidateData csv = new RawCandidateData(SourceType.RECRUITER_CSV, "recruiter.csv");
        csv.setFullName(new RawValue<>("Priya Sharma", "csv"));
        csv.addEmail(new RawValue<>("Priya.Sharma@gmail.com", "csv"));
        csv.addPhone(new RawValue<>("415-555-0198", "csv"));

        RawCandidateData resume = new RawCandidateData(SourceType.RESUME, "resume.pdf");
        resume.addEmail(new RawValue<>("priya.sharma@gmail.com", "resume")); // same email, different case
        resume.addEmail(new RawValue<>("p.sharma@personal.com", "resume")); // an extra distinct email
        resume.addSkill(new RawValue<>("js", "resume"));
        resume.addSkill(new RawValue<>("springboot", "resume"));

        CandidateProfile profile = engine.merge("cand_1", List.of(csv, resume));

        assertThat(profile.emails()).containsExactlyInAnyOrder("priya.sharma@gmail.com", "p.sharma@personal.com");
        assertThat(profile.phones()).containsExactly("+14155550198");
        assertThat(profile.fullName()).isEqualTo("Priya Sharma");
        assertThat(profile.skills()).extracting("name").containsExactlyInAnyOrder("JavaScript", "Spring Boot");
        assertThat(profile.overallConfidence()).isBetween(0.0, 1.0);
        assertThat(profile.provenance()).isNotEmpty();
    }

    @Test
    void malformedSourceIsSimplyExcludedNotCrashing() {
        RawCandidateData malformed = new RawCandidateData(SourceType.ATS_JSON, "ats.json");
        malformed.markMalformed("invalid json");

        RawCandidateData csv = new RawCandidateData(SourceType.RECRUITER_CSV, "recruiter.csv");
        csv.setFullName(new RawValue<>("Test Candidate", "csv"));

        CandidateProfile profile = engine.merge("cand_2", List.of(malformed, csv));

        assertThat(profile.fullName()).isEqualTo("Test Candidate");
    }

    @Test
    void noSourcesProducesEmptyButValidProfile() {
        CandidateProfile profile = engine.merge("cand_3", List.of());
        assertThat(profile.fullName()).isNull();
        assertThat(profile.emails()).isEmpty();
        assertThat(profile.overallConfidence()).isEqualTo(0.0);
    }

    @Test
    void skillConfidenceIncreasesWithMoreCorroboratingSources() {
        RawCandidateData csv = new RawCandidateData(SourceType.RECRUITER_CSV, "recruiter.csv");
        RawCandidateData ats = new RawCandidateData(SourceType.ATS_JSON, "ats.json");
        RawCandidateData resume = new RawCandidateData(SourceType.RESUME, "resume.pdf");

        // "java" mentioned by all three sources - should end up with very high confidence.
        csv.addSkill(new RawValue<>("java", "csv"));
        ats.addSkill(new RawValue<>("java", "ats"));
        resume.addSkill(new RawValue<>("java", "resume"));

        // "docker" mentioned only by resume - should end up with lower confidence.
        resume.addSkill(new RawValue<>("docker", "resume"));

        CandidateProfile profile = engine.merge("cand_4", List.of(csv, ats, resume));

        double javaConfidence = profile.skills().stream().filter(s -> s.name().equals("Java")).findFirst().orElseThrow().confidence();
        double dockerConfidence = profile.skills().stream().filter(s -> s.name().equals("Docker")).findFirst().orElseThrow().confidence();

        assertThat(javaConfidence).isGreaterThan(dockerConfidence);
    }
}
