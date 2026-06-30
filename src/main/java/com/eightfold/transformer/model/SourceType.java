package com.eightfold.transformer.model;

/**
 * The set of data sources the pipeline understands, ordered (via {@link #priority()})
 * from most to least reliable for conflict resolution purposes.
 *
 * Lower priority number == more trusted when two sources disagree.
 * This ordering is the backbone of the deterministic merge engine.
 */
public enum SourceType {

    RECRUITER_CSV(1, "Human-entered by an internal recruiter; treated as ground truth for contact/role fields."),
    ATS_JSON(2, "System of record fields synced from the ATS; structured but can lag reality."),
    RESUME(3, "Candidate-authored prose; rich but parsed heuristically, so less trusted than structured systems."),
    GITHUB_PROFILE(4, "Public API data; good for skills/links, weak for identity fields."),
    LINKEDIN_PROFILE(5, "Public profile data; self-reported and frequently stale."),
    RECRUITER_NOTES(6, "Free-text scratch notes; useful supplementary signal, lowest structural trust.");

    private final int priority;
    private final String rationale;

    SourceType(int priority, String rationale) {
        this.priority = priority;
        this.rationale = rationale;
    }

    /** Lower value = higher trust. Used as the primary tiebreaker in the merge engine. */
    public int priority() {
        return priority;
    }

    public String rationale() {
        return rationale;
    }

    public boolean isStructured() {
        return this == RECRUITER_CSV || this == ATS_JSON;
    }
}
