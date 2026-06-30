package com.eightfold.transformer.confidence;

import com.eightfold.transformer.model.SourceType;

import java.util.Set;

/**
 * Centralizes every confidence-scoring rule in the system. Two kinds of scores:
 * <ul>
 *   <li>FIELD confidence - how sure we are about one scalar field's winning value</li>
 *   <li>SKILL confidence - how sure we are the candidate actually has a given skill</li>
 * </ul>
 * Rules (deliberately simple, deterministic, and explainable - see design doc):
 * <ul>
 *   <li>Base confidence = trust level of the winning source</li>
 *   <li>+0.08 per additional distinct corroborating source (consensus), capped</li>
 *   <li>Resume-only signal is capped at a medium ceiling (resumes are self-reported prose)</li>
 *   <li>Malformed/invalid contributions never enter the pool, so they only ever
 *       reduce confidence indirectly (by removing a corroborating source)</li>
 * </ul>
 */
public final class ConfidenceEngine {

    private static final double MAX_CONFIDENCE = 0.99;
    private static final double CONSENSUS_BONUS_PER_EXTRA_SOURCE = 0.08;
    private static final double RESUME_ONLY_CEILING = 0.70;

    /** Base trust level per source, used as the floor for any value that source contributes. */
    public double baseConfidence(SourceType source) {
        return switch (source) {
            case RECRUITER_CSV -> 0.95;
            case ATS_JSON -> 0.90;
            case RESUME -> 0.65;
            case GITHUB_PROFILE -> 0.60;
            case LINKEDIN_PROFILE -> 0.55;
            case RECRUITER_NOTES -> 0.45;
        };
    }

    /**
     * Confidence for a single scalar field's winning value.
     *
     * @param winningSource     the source that won the merge for this field
     * @param contributingSources all distinct sources that produced this exact normalized value (consensus set)
     */
    public double fieldConfidence(SourceType winningSource, Set<SourceType> contributingSources) {
        double base = baseConfidence(winningSource);
        int extraCorroboration = Math.max(0, contributingSources.size() - 1);
        double score = base + (extraCorroboration * CONSENSUS_BONUS_PER_EXTRA_SOURCE);

        boolean resumeOnly = contributingSources.size() == 1 && contributingSources.contains(SourceType.RESUME);
        if (resumeOnly) {
            score = Math.min(score, RESUME_ONLY_CEILING);
        }
        return Math.min(MAX_CONFIDENCE, score);
    }

    /**
     * Confidence that a skill is genuine, based on how many distinct sources mention it
     * and whether any of those sources is structured (CSV/ATS), which is a much stronger
     * signal than a single resume keyword hit.
     */
    public double skillConfidence(Set<SourceType> sources) {
        double base = 0.40;
        int n = sources.size();
        double score = base + (n - 1) * 0.18;
        boolean hasStructuredCorroboration = sources.stream().anyMatch(SourceType::isStructured);
        if (hasStructuredCorroboration) {
            score += 0.15;
        }
        if (n == 1 && sources.contains(SourceType.RESUME)) {
            score = Math.min(score, 0.55);
        }
        return Math.max(0.0, Math.min(MAX_CONFIDENCE, score));
    }

    /**
     * Overall profile confidence: a weighted average across the fields that matter
     * most for downstream matching/search, weighted toward identity + skills since
     * those drive most product use cases. Missing fields simply don't contribute
     * (rather than being scored as zero), so a profile that's missing low-weight
     * fields like education isn't unfairly punished.
     */
    public double overallConfidence(java.util.Map<String, Double> presentFieldConfidences,
                                     java.util.Map<String, Double> fieldWeights) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (var entry : presentFieldConfidences.entrySet()) {
            double weight = fieldWeights.getOrDefault(entry.getKey(), 1.0);
            weightedSum += entry.getValue() * weight;
            totalWeight += weight;
        }
        if (totalWeight == 0.0) {
            return 0.0;
        }
        return Math.min(MAX_CONFIDENCE, weightedSum / totalWeight);
    }
}
