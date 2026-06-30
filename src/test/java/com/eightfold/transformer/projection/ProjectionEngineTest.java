package com.eightfold.transformer.projection;

import com.eightfold.transformer.config.FieldSpec;
import com.eightfold.transformer.config.OutputConfig;
import com.eightfold.transformer.model.*;
import com.eightfold.transformer.util.ProfileValidationException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectionEngineTest {

    private final ProjectionEngine engine = new ProjectionEngine();

    private CandidateProfile sampleProfile() {
        return new CandidateProfile(
                "cand_1", "Priya Sharma",
                List.of("priya.sharma@gmail.com"), List.of("+14155550198"),
                new Location("San Francisco", "CA", "US"),
                new Links("https://www.linkedin.com/in/priyasharma-eng", "https://github.com/psharma-dev", null, List.of()),
                "Staff Backend Engineer", 8.0,
                List.of(new Skill("Java", 0.95, List.of("resume.pdf", "ats.json"))),
                List.of(new Experience("Initech", "Staff Backend Engineer", "2021-03", "present", null)),
                List.of(new Education("IIT Bombay", "B.Tech", "CS", 2018)),
                List.of(new Provenance("full_name", "recruiter.csv", "csv column mapping")),
                0.87
        );
    }

    @Test
    void defaultSchemaIncludesEveryCanonicalField() {
        ObjectNode result = engine.project(sampleProfile(), OutputConfig.defaultSchema());
        assertThat(result.get("candidate_id").asText()).isEqualTo("cand_1");
        assertThat(result.get("full_name").asText()).isEqualTo("Priya Sharma");
        assertThat(result.get("emails").isArray()).isTrue();
        assertThat(result.get("location").get("country").asText()).isEqualTo("US");
        assertThat(result.get("skills").get(0).get("confidence").asDouble()).isEqualTo(0.95);
        assertThat(result.has("provenance")).isTrue();
        assertThat(result.has("overall_confidence")).isTrue();
    }

    @Test
    void customConfigSelectsAndRenamesFields() {
        OutputConfig config = new OutputConfig(
                List.of(
                        new FieldSpec("full_name", "full_name", "string", true, null),
                        new FieldSpec("primary_email", "emails[0]", "string", true, null),
                        new FieldSpec("phone", "phones[0]", "string", false, "E164")
                ),
                true, false, "null"
        );
        ObjectNode result = engine.project(sampleProfile(), config);
        assertThat(result.get("full_name").asText()).isEqualTo("Priya Sharma");
        assertThat(result.get("primary_email").asText()).isEqualTo("priya.sharma@gmail.com");
        assertThat(result.get("phone").asText()).isEqualTo("+14155550198");
        assertThat(result.has("provenance")).isFalse();
        assertThat(result.has("overall_confidence")).isTrue();
    }

    @Test
    void onMissingNullIncludesNullValue() {
        OutputConfig config = new OutputConfig(
                List.of(new FieldSpec("middle_name", "middle_name", "string", false, null)),
                false, false, "null"
        );
        ObjectNode result = engine.project(sampleProfile(), config);
        assertThat(result.has("middle_name")).isTrue();
        assertThat(result.get("middle_name").isNull()).isTrue();
    }

    @Test
    void onMissingOmitDropsTheKey() {
        OutputConfig config = new OutputConfig(
                List.of(new FieldSpec("middle_name", "middle_name", "string", false, null)),
                false, false, "omit"
        );
        ObjectNode result = engine.project(sampleProfile(), config);
        assertThat(result.has("middle_name")).isFalse();
    }

    @Test
    void onMissingErrorThrowsForRequiredMissingField() {
        OutputConfig config = new OutputConfig(
                List.of(new FieldSpec("middle_name", "middle_name", "string", true, null)),
                false, false, "error"
        );
        assertThrows(ProfileValidationException.class, () -> engine.project(sampleProfile(), config));
    }

    @Test
    void nestedDottedPathBuildsNestedObject() {
        OutputConfig config = new OutputConfig(
                List.of(new FieldSpec("contact.linkedin", "links.linkedin", "string", false, null)),
                false, false, "null"
        );
        ObjectNode result = engine.project(sampleProfile(), config);
        assertThat(result.get("contact").get("linkedin").asText()).isEqualTo("https://www.linkedin.com/in/priyasharma-eng");
    }

    @Test
    void skillsWildcardSubfieldProjectsListOfNames() {
        OutputConfig config = new OutputConfig(
                List.of(new FieldSpec("skill_names", "skills[].name", "string[]", false, null)),
                false, false, "null"
        );
        ObjectNode result = engine.project(sampleProfile(), config);
        assertThat(result.get("skill_names").get(0).asText()).isEqualTo("Java");
    }
}
