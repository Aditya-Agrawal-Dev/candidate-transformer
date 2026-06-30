package com.eightfold.transformer.merge;

import com.eightfold.transformer.model.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScalarFieldMergerTest {

    private final ScalarFieldMerger merger = new ScalarFieldMerger();

    @Test
    void emptyCandidatesProduceEmptyResult() {
        MergeResult<String> result = merger.merge(List.of());
        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void singleCandidateWins() {
        MergeResult<String> result = merger.merge(List.of(
                new Candidate<>("Priya Sharma", SourceType.RESUME, "resume.pdf", "regex")
        ));
        assertThat(result.value()).isEqualTo("Priya Sharma");
        assertThat(result.winningSource()).isEqualTo(SourceType.RESUME);
    }

    @Test
    void consensusBeatsSingleHigherPrioritySource() {
        // Two lower-priority sources (resume + notes) agree on a value; one higher-priority
        // source (csv) disagrees alone. Consensus (2 sources) should win over a lone CSV value.
        MergeResult<String> result = merger.merge(List.of(
                new Candidate<>("San Francisco", SourceType.RESUME, "resume.pdf", "m"),
                new Candidate<>("San Francisco", SourceType.RECRUITER_NOTES, "notes.txt", "m"),
                new Candidate<>("Austin", SourceType.RECRUITER_CSV, "recruiter.csv", "m")
        ));
        assertThat(result.value()).isEqualTo("San Francisco");
        assertThat(result.contributingSources()).hasSize(2);
    }

    @Test
    void sourceReliabilityBreaksConsensusTies() {
        // No consensus (every source disagrees) - the most trusted single source wins.
        MergeResult<String> result = merger.merge(List.of(
                new Candidate<>("Austin", SourceType.RECRUITER_NOTES, "notes.txt", "m"),
                new Candidate<>("San Francisco", SourceType.RECRUITER_CSV, "recruiter.csv", "m"),
                new Candidate<>("Boston", SourceType.RESUME, "resume.pdf", "m")
        ));
        assertThat(result.value()).isEqualTo("San Francisco");
        assertThat(result.winningSource()).isEqualTo(SourceType.RECRUITER_CSV);
    }

    @Test
    void isDeterministicAcrossRepeatedRuns() {
        List<Candidate<String>> candidates = List.of(
                new Candidate<>("Austin", SourceType.RECRUITER_NOTES, "notes.txt", "m"),
                new Candidate<>("San Francisco", SourceType.RECRUITER_CSV, "recruiter.csv", "m")
        );
        MergeResult<String> first = merger.merge(candidates);
        MergeResult<String> second = merger.merge(candidates);
        assertThat(first.value()).isEqualTo(second.value());
        assertThat(first.winningSourceId()).isEqualTo(second.winningSourceId());
    }
}
