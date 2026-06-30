package com.eightfold.transformer.validation;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.model.Education;
import com.eightfold.transformer.model.Experience;
import com.eightfold.transformer.model.Skill;
import com.eightfold.transformer.normalization.EmailNormalizer;
import com.eightfold.transformer.normalization.PhoneNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates a fully-merged {@link CandidateProfile} for internal consistency
 * BEFORE it is handed to the projection layer. This catches normalization bugs
 * and merge-engine bugs early, independent of whatever output config is used.
 */
public final class ProfileValidator {

    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}|present)$");
    private static final Pattern ISO_COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public List<ValidationError> validate(CandidateProfile profile) {
        List<ValidationError> errors = new ArrayList<>();

        if (profile.candidateId() == null || profile.candidateId().isBlank()) {
            errors.add(ValidationError.error("candidate_id", "candidate_id is missing"));
        }

        if (profile.fullName() == null || profile.fullName().isBlank()) {
            errors.add(ValidationError.warning("full_name", "full_name could not be resolved from any source"));
        }

        for (String email : profile.emails()) {
            if (!EmailNormalizer.isValid(email)) {
                errors.add(ValidationError.error("emails", "invalid email after normalization: " + email));
            }
        }
        if (profile.emails().isEmpty()) {
            errors.add(ValidationError.warning("emails", "no valid email found in any source"));
        }

        for (String phone : profile.phones()) {
            if (!PhoneNormalizer.isValidE164(phone)) {
                errors.add(ValidationError.error("phones", "phone not in valid E.164 form: " + phone));
            }
        }

        String country = profile.location() != null ? profile.location().country() : null;
        if (country != null && !ISO_COUNTRY.matcher(country).matches()) {
            errors.add(ValidationError.error("location.country", "country is not a valid ISO 3166-1 alpha-2 code: " + country));
        }

        for (Experience exp : profile.experience()) {
            validateDate(errors, "experience.start", exp.start());
            validateDate(errors, "experience.end", exp.end());
            if (exp.company() == null && exp.title() == null) {
                errors.add(ValidationError.warning("experience", "experience entry has neither company nor title"));
            }
        }

        for (Education edu : profile.education()) {
            if (edu.endYear() != null && (edu.endYear() < 1950 || edu.endYear() > 2100)) {
                errors.add(ValidationError.error("education.end_year", "implausible education end_year: " + edu.endYear()));
            }
        }

        for (Skill skill : profile.skills()) {
            if (skill.name() == null || skill.name().isBlank()) {
                errors.add(ValidationError.error("skills", "skill entry with blank canonical name"));
            }
            if (skill.confidence() < 0.0 || skill.confidence() > 1.0) {
                errors.add(ValidationError.error("skills.confidence", "skill confidence out of [0,1] range: " + skill.confidence()));
            }
        }

        if (profile.overallConfidence() < 0.0 || profile.overallConfidence() > 1.0) {
            errors.add(ValidationError.error("overall_confidence", "overall_confidence out of [0,1] range"));
        }

        return errors;
    }

    private void validateDate(List<ValidationError> errors, String field, String date) {
        if (date == null) {
            return;
        }
        if (!DATE_PATTERN.matcher(date.toLowerCase(Locale.ROOT)).matches()) {
            errors.add(ValidationError.error(field, "date not in YYYY-MM or 'present' form: " + date));
        }
    }

    public boolean hasBlockingErrors(List<ValidationError> errors) {
        return errors.stream().anyMatch(e -> e.severity() == ValidationError.Severity.ERROR);
    }
}
