package com.eightfold.transformer.confidence;

import com.eightfold.transformer.model.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceEngineTest {

    private final ConfidenceEngine engine = new ConfidenceEngine();

    @Test
    void csvFieldConfidenceIsHighestSingleSource() {
        double csvOnly = engine.fieldConfidence(SourceType.RECRUITER_CSV, Set.of(SourceType.RECRUITER_CSV));
        double resumeOnly = engine.fieldConfidence(SourceType.RESUME, Set.of(SourceType.RESUME));
        assertThat(csvOnly).isGreaterThan(resumeOnly);
    }

    @Test
    void resumeOnlyFieldConfidenceIsCappedAtMedium() {
        double resumeOnly = engine.fieldConfidence(SourceType.RESUME, Set.of(SourceType.RESUME));
        assertThat(resumeOnly).isLessThanOrEqualTo(0.70);
    }

    @Test
    void corroborationAcrossSourcesIncreasesConfidence() {
        double single = engine.fieldConfidence(SourceType.RECRUITER_CSV, Set.of(SourceType.RECRUITER_CSV));
        double corroborated = engine.fieldConfidence(SourceType.RECRUITER_CSV, Set.of(SourceType.RECRUITER_CSV, SourceType.ATS_JSON, SourceType.RESUME));
        assertThat(corroborated).isGreaterThan(single);
    }

    @Test
    void skillConfidenceRewardsStructuredCorroboration() {
        double resumeOnly = engine.skillConfidence(Set.of(SourceType.RESUME));
        double resumeAndCsv = engine.skillConfidence(Set.of(SourceType.RESUME, SourceType.RECRUITER_CSV));
        assertThat(resumeAndCsv).isGreaterThan(resumeOnly);
    }

    @Test
    void overallConfidenceWeightsFieldsAndIgnoresMissingOnes() {
        Map<String, Double> present = Map.of("full_name", 0.9, "skills", 0.5);
        Map<String, Double> weights = Map.of("full_name", 1.5, "skills", 1.5, "education", 0.8);
        double overall = engine.overallConfidence(present, weights);
        assertThat(overall).isBetween(0.0, 1.0);
        assertThat(overall).isEqualTo((0.9 * 1.5 + 0.5 * 1.5) / (1.5 + 1.5), org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void overallConfidenceWithNoFieldsIsZero() {
        assertThat(engine.overallConfidence(Map.of(), Map.of())).isEqualTo(0.0);
    }
}
