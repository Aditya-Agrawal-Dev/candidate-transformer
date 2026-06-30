package com.eightfold.transformer.projection;

import com.eightfold.transformer.config.FieldSpec;
import com.eightfold.transformer.config.OutputConfig;
import com.eightfold.transformer.model.*;
import com.eightfold.transformer.util.ProfileValidationException;
import com.eightfold.transformer.validation.ValidationError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Projects the canonical {@link CandidateProfile} into a runtime-configurable
 * output shape. This layer is fully decoupled from the canonical model: it only
 * knows how to (a) ask {@link CanonicalPathResolver} for a value at a path, and
 * (b) write that value into an arbitrarily-shaped output tree. No code changes
 * are required to reshape output - only the JSON config changes.
 */
public final class ProjectionEngine {

    private final CanonicalPathResolver resolver = new CanonicalPathResolver();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNodeFactory nf = JsonNodeFactory.instance;

    /**
     * @throws ProfileValidationException if {@code on_missing=error} and a required
     *                                     field could not be resolved
     */
    public ObjectNode project(CandidateProfile profile, OutputConfig config) {
        if (config.fields().isEmpty()) {
            return projectDefaultSchema(profile, config);
        }
        return projectCustom(profile, config);
    }

    // ---------------------------------------------------------------- default

    private ObjectNode projectDefaultSchema(CandidateProfile profile, OutputConfig config) {
        ObjectNode root = nf.objectNode();
        root.put("candidate_id", profile.candidateId());
        root.put("full_name", profile.fullName());
        root.set("emails", toArray(profile.emails()));
        root.set("phones", toArray(profile.phones()));

        ObjectNode location = nf.objectNode();
        Location loc = profile.location();
        location.put("city", loc != null ? loc.city() : null);
        location.put("region", loc != null ? loc.region() : null);
        location.put("country", loc != null ? loc.country() : null);
        root.set("location", location);

        ObjectNode links = nf.objectNode();
        Links l = profile.links();
        links.put("linkedin", l != null ? l.linkedin() : null);
        links.put("github", l != null ? l.github() : null);
        links.put("portfolio", l != null ? l.portfolio() : null);
        links.set("other", toArray(l != null ? l.other() : List.of()));
        root.set("links", links);

        root.put("headline", profile.headline());
        if (profile.yearsExperience() != null) {
            root.put("years_experience", profile.yearsExperience());
        } else {
            root.putNull("years_experience");
        }

        ArrayNode skills = nf.arrayNode();
        for (Skill s : profile.skills()) {
            ObjectNode node = nf.objectNode();
            node.put("name", s.name());
            node.put("confidence", round(s.confidence()));
            node.set("sources", toArray(s.sources()));
            skills.add(node);
        }
        root.set("skills", skills);

        ArrayNode experience = nf.arrayNode();
        for (Experience e : profile.experience()) {
            ObjectNode node = nf.objectNode();
            node.put("company", e.company());
            node.put("title", e.title());
            node.put("start", e.start());
            node.put("end", e.end());
            node.put("summary", e.summary());
            experience.add(node);
        }
        root.set("experience", experience);

        ArrayNode education = nf.arrayNode();
        for (Education e : profile.education()) {
            ObjectNode node = nf.objectNode();
            node.put("institution", e.institution());
            node.put("degree", e.degree());
            node.put("field", e.field());
            if (e.endYear() != null) {
                node.put("end_year", e.endYear());
            } else {
                node.putNull("end_year");
            }
            education.add(node);
        }
        root.set("education", education);

        if (config.includeProvenance()) {
            ArrayNode provenance = nf.arrayNode();
            for (Provenance p : profile.provenance()) {
                ObjectNode node = nf.objectNode();
                node.put("field", p.field());
                node.put("source", p.source());
                node.put("method", p.method());
                provenance.add(node);
            }
            root.set("provenance", provenance);
        }

        if (config.includeConfidence()) {
            root.put("overall_confidence", round(profile.overallConfidence()));
        }

        return root;
    }

    // ----------------------------------------------------------------- custom

    private ObjectNode projectCustom(CandidateProfile profile, OutputConfig config) {
        ObjectNode root = nf.objectNode();
        List<ValidationError> missingRequired = new ArrayList<>();

        for (FieldSpec spec : config.fields()) {
            Object resolved = resolver.resolve(profile, spec.from());
            if (spec.normalize() != null) {
                resolved = NormalizationReapplier.apply(resolved, spec.normalize());
            }

            boolean isMissing = resolved == null
                    || (resolved instanceof List<?> list && list.isEmpty())
                    || (resolved instanceof String str && str.isBlank());

            if (isMissing) {
                if (spec.required()) {
                    missingRequired.add(ValidationError.error(spec.path(),
                            "required field could not be resolved from path '" + spec.from() + "'"));
                }
                switch (config.missingPolicy()) {
                    case OMIT -> { continue; }
                    case ERROR -> {
                        if (spec.required()) {
                            continue; // will throw below after collecting all errors
                        }
                        setNested(root, spec.path(), nullNode());
                    }
                    case NULL -> setNested(root, spec.path(), nullNode());
                }
            } else {
                setNested(root, spec.path(), toJsonNode(resolved));
            }
        }

        if (!missingRequired.isEmpty() && config.missingPolicy() == OutputConfig.MissingPolicy.ERROR) {
            throw new ProfileValidationException(
                    "Projection failed: " + missingRequired.size() + " required field(s) missing and on_missing=error",
                    missingRequired);
        }

        if (config.includeConfidence() && !root.has("overall_confidence")) {
            root.put("overall_confidence", round(profile.overallConfidence()));
        }
        if (config.includeProvenance() && !root.has("provenance")) {
            ArrayNode provenance = nf.arrayNode();
            for (Provenance p : profile.provenance()) {
                ObjectNode node = nf.objectNode();
                node.put("field", p.field());
                node.put("source", p.source());
                node.put("method", p.method());
                provenance.add(node);
            }
            root.set("provenance", provenance);
        }

        return root;
    }

    private JsonNode nullNode() {
        return nf.nullNode();
    }

    private void setNested(ObjectNode root, String dottedPath, JsonNode value) {
        String[] parts = dottedPath.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode existing = current.get(parts[i]);
            if (existing == null || !existing.isObject()) {
                ObjectNode child = nf.objectNode();
                current.set(parts[i], child);
                current = child;
            } else {
                current = (ObjectNode) existing;
            }
        }
        current.set(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return nf.nullNode();
        }
        if (value instanceof String s) {
            return nf.textNode(s);
        }
        if (value instanceof Double d) {
            return nf.numberNode(round(d));
        }
        if (value instanceof Integer i) {
            return nf.numberNode(i);
        }
        if (value instanceof Boolean b) {
            return nf.booleanNode(b);
        }
        if (value instanceof List<?> list) {
            ArrayNode arr = nf.arrayNode();
            for (Object item : list) {
                arr.add(toJsonNode(item));
            }
            return arr;
        }
        // Fallback: let Jackson reflect on records (Skill, Experience, etc.)
        return mapper.valueToTree(value);
    }

    private ArrayNode toArray(List<String> values) {
        ArrayNode arr = nf.arrayNode();
        if (values != null) {
            values.forEach(arr::add);
        }
        return arr;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
