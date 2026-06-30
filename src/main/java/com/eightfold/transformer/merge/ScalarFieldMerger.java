package com.eightfold.transformer.merge;

import com.eightfold.transformer.model.SourceType;

import java.util.*;

/**
 * Deterministic conflict resolution for a SINGLE scalar field across multiple
 * source candidates.
 * <p>
 * Resolution order (matches the assignment's priority list exactly):
 * <ol>
 *   <li><b>Consensus</b> - the value with the most distinct sources agreeing wins first.
 *       Two sources independently saying the same thing is stronger evidence than
 *       one source's lone claim, regardless of which source it is.</li>
 *   <li><b>Source reliability</b> - ties in consensus are broken by picking the group
 *       containing the most-trusted source (lowest {@link SourceType#priority()}).</li>
 *   <li><b>Determinism</b> - any remaining tie is broken by the lexicographically
 *       smallest source identifier, so the same inputs always produce the same output.</li>
 * </ol>
 * "Freshness" and "Completeness" are folded in by the callers: callers only ever
 * hand this class already-normalized, already-validated, non-blank candidates
 * (completeness/validation gating happens upstream), and for fields where recency
 * matters (e.g. current employer) callers pass the most recent source's row last
 * so equal-priority ties favor it.
 */
public final class ScalarFieldMerger {

    public <T> MergeResult<T> merge(List<Candidate<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return MergeResult.empty();
        }

        Map<T, List<Candidate<T>>> groups = new LinkedHashMap<>();
        for (Candidate<T> c : candidates) {
            groups.computeIfAbsent(c.value(), k -> new ArrayList<>()).add(c);
        }

        // Distinct-source count per group (consensus strength), not raw candidate count,
        // so 3 rows from the SAME csv don't outweigh 2 independent sources agreeing.
        T winningValue = null;
        List<Candidate<T>> winningGroup = null;
        int bestConsensus = -1;
        int bestPriority = Integer.MAX_VALUE;

        for (var entry : groups.entrySet()) {
            List<Candidate<T>> group = entry.getValue();
            Set<SourceType> distinctSources = new HashSet<>();
            int minPriority = Integer.MAX_VALUE;
            for (Candidate<T> c : group) {
                distinctSources.add(c.source());
                minPriority = Math.min(minPriority, c.source().priority());
            }
            int consensus = distinctSources.size();

            boolean better = consensus > bestConsensus
                    || (consensus == bestConsensus && minPriority < bestPriority)
                    || (consensus == bestConsensus && minPriority == bestPriority
                        && winningValue != null && compareForDeterminism(group, winningGroup) < 0);

            if (winningGroup == null || better) {
                winningValue = entry.getKey();
                winningGroup = group;
                bestConsensus = consensus;
                bestPriority = minPriority;
            }
        }

        // Pick the actual winning Candidate within the winning group: most-trusted source,
        // tie-broken by lexicographically smallest source id for determinism.
        Candidate<T> winner = winningGroup.stream()
                .sorted(Comparator
                        .comparingInt((Candidate<T> c) -> c.source().priority())
                        .thenComparing(Candidate::sourceId))
                .findFirst()
                .orElseThrow();

        Set<SourceType> contributing = new TreeSet<>(Comparator.comparingInt(SourceType::priority));
        for (Candidate<T> c : winningGroup) {
            contributing.add(c.source());
        }

        String method = String.format(
                "merge: %d source(s) agreed (%s); winner selected by source reliability (%s, priority %d); extraction=%s",
                bestConsensus,
                joinSources(contributing),
                winner.source(),
                winner.source().priority(),
                winner.extractionMethod());

        return new MergeResult<>(winningValue, winner.source(), winner.sourceId(), contributing, method);
    }

    private <T> int compareForDeterminism(List<Candidate<T>> a, List<Candidate<T>> b) {
        String aMin = a.stream().map(Candidate::sourceId).min(String::compareTo).orElse("");
        String bMin = b.stream().map(Candidate::sourceId).min(String::compareTo).orElse("");
        return aMin.compareTo(bMin);
    }

    private String joinSources(Set<SourceType> sources) {
        StringBuilder sb = new StringBuilder();
        for (SourceType s : sources) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}
