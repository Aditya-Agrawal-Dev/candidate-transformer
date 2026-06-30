package com.eightfold.transformer.validation;

import com.eightfold.transformer.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileValidatorTest {

    private final ProfileValidator validator = new ProfileValidator();

    @Test
    void validProfileProducesNoErrors() {
        CandidateProfile profile = new CandidateProfile(
                "cand_1", "Priya Sharma",
                List.of("priya.sharma@gmail.com"), List.of("+14155550198"),
                new Location("San Francisco", "CA", "US"),
                Links.EMPTY, "Staff Backend Engineer", 8.0,
                List.of(new Skill("Java", 0.9, List.of("resume.pdf"))),
                List.of(new Experience("Initech", "Staff Backend Engineer", "2021-03", "present", null)),
                List.of(new Education("IIT Bombay", "B.Tech", "CS", 2018)),
                List.of(),
                0.85
        );
        List<ValidationError> errors = validator.validate(profile);
        assertThat(validator.hasBlockingErrors(errors)).isFalse();
    }

    @Test
    void invalidPhoneProducesBlockingError() {
        CandidateProfile profile = baseProfileWithPhones(List.of("not-a-phone"));
        List<ValidationError> errors = validator.validate(profile);
        assertThat(validator.hasBlockingErrors(errors)).isTrue();
    }

    @Test
    void invalidCountryCodeProducesBlockingError() {
        CandidateProfile profile = new CandidateProfile(
                "cand_2", "Test", List.of(), List.of(),
                new Location(null, null, "United States"), Links.EMPTY, null, null,
                List.of(), List.of(), List.of(), List.of(), 0.5
        );
        List<ValidationError> errors = validator.validate(profile);
        assertThat(validator.hasBlockingErrors(errors)).isTrue();
    }

    @Test
    void missingCandidateIdIsBlocking() {
        CandidateProfile profile = new CandidateProfile(
                null, "Test", List.of(), List.of(), Location.EMPTY, Links.EMPTY, null, null,
                List.of(), List.of(), List.of(), List.of(), 0.5
        );
        List<ValidationError> errors = validator.validate(profile);
        assertThat(validator.hasBlockingErrors(errors)).isTrue();
    }

    @Test
    void missingEmailIsWarningNotError() {
        CandidateProfile profile = new CandidateProfile(
                "cand_3", "Test", List.of(), List.of(), Location.EMPTY, Links.EMPTY, null, null,
                List.of(), List.of(), List.of(), List.of(), 0.5
        );
        List<ValidationError> errors = validator.validate(profile);
        boolean onlyWarnings = errors.stream().allMatch(e -> e.severity() == ValidationError.Severity.WARNING);
        assertThat(onlyWarnings).isTrue();
    }

    private CandidateProfile baseProfileWithPhones(List<String> phones) {
        return new CandidateProfile(
                "cand_x", "Test", List.of(), phones, Location.EMPTY, Links.EMPTY, null, null,
                List.of(), List.of(), List.of(), List.of(), 0.5
        );
    }
}
