package com.eightfold.transformer.provenance;

import com.eightfold.transformer.model.Provenance;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates {@link Provenance} entries during the merge pass. Kept as its own
 * small component (rather than inlined in the merge engine) so provenance
 * recording is a single, auditable responsibility - any future change to HOW we
 * describe provenance only touches this class.
 */
public final class ProvenanceTracker {

    private final List<Provenance> entries = new ArrayList<>();

    public void record(String field, String source, String method) {
        entries.add(new Provenance(field, source, method));
    }

    public List<Provenance> entries() {
        return List.copyOf(entries);
    }
}
