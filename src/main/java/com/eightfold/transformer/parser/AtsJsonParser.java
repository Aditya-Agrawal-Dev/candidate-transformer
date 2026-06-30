package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.ExtractionMethod;
import com.eightfold.transformer.model.SourceType;
import com.eightfold.transformer.source.RawCandidateData;
import com.eightfold.transformer.source.RawEducation;
import com.eightfold.transformer.source.RawExperience;
import com.eightfold.transformer.source.RawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Parses the ATS JSON export. The ATS uses its OWN field-naming convention that
 * does not match our canonical model (e.g. {@code summaryHeadline} instead of
 * {@code headline}, {@code mobile} instead of {@code phone}) - this parser is
 * exactly where that field-name translation happens.
 * <p>
 * Expected (illustrative) shape:
 * <pre>
 * {
 *   "candidate": {
 *     "fullName": "...",
 *     "contact": { "emailAddress": "...", "mobile": "..." },
 *     "address": { "town": "...", "state": "...", "nation": "..." },
 *     "summaryHeadline": "...",
 *     "totalExperienceYears": 5.5,
 *     "skillSet": ["Java", "spring-boot"],
 *     "workHistory": [ { "employer":"...", "role":"...", "from":"...", "to":"...", "details":"..." } ],
 *     "academics": [ { "school":"...", "qualification":"...", "major":"...", "graduationYear":2020 } ],
 *     "socialLinks": { "linkedIn":"...", "gitHub":"...", "website":"..." }
 *   }
 * }
 * </pre>
 * Any missing/renamed/extra field is tolerated; this parser never throws on
 * structurally-odd-but-parseable JSON, only on outright invalid JSON syntax.
 */
public final class AtsJsonParser implements SourceParser {

    private static final Logger log = LoggerFactory.getLogger(AtsJsonParser.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(Path file) {
        return file.toString().toLowerCase().endsWith(".json");
    }

    @Override
    public RawCandidateData parse(Path file) {
        String sourceId = file.getFileName().toString();
        RawCandidateData data = new RawCandidateData(SourceType.ATS_JSON, sourceId);
        String method = ExtractionMethod.JSON_FIELD_MAPPING.toString();

        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(file));
        } catch (IOException e) {
            log.warn("Malformed ATS JSON in {}: {}", file, e.getMessage());
            data.markMalformed("Invalid JSON syntax: " + e.getMessage());
            return data;
        }

        JsonNode candidate = root.path("candidate");
        if (candidate.isMissingNode() || candidate.isNull()) {
            // Tolerate a flat (non-nested) shape too.
            candidate = root;
        }
        if (candidate.isMissingNode() || !candidate.fields().hasNext()) {
            data.markMalformed("ATS JSON has no recognizable 'candidate' content");
            return data;
        }

        text(candidate, "fullName", "full_name", "name").ifPresent(v -> data.setFullName(new RawValue<>(v, method)));

        JsonNode contact = candidate.path("contact");
        text(contact, "emailAddress", "email").ifPresent(v -> data.addEmail(new RawValue<>(v, method)));
        text(contact, "mobile", "phone", "phoneNumber").ifPresent(v -> data.addPhone(new RawValue<>(v, method)));
        // Also tolerate top-level contact fields.
        text(candidate, "email", "emailAddress").ifPresent(v -> data.addEmail(new RawValue<>(v, method)));
        text(candidate, "phone", "mobile", "phoneNumber").ifPresent(v -> data.addPhone(new RawValue<>(v, method)));

        JsonNode address = candidate.path("address");
        text(address, "town", "city").ifPresent(v -> data.setCity(new RawValue<>(v, method)));
        text(address, "state", "region").ifPresent(v -> data.setRegion(new RawValue<>(v, method)));
        text(address, "nation", "country").ifPresent(v -> data.setCountry(new RawValue<>(v, method)));

        text(candidate, "summaryHeadline", "headline", "title").ifPresent(v -> data.setHeadline(new RawValue<>(v, method)));

        JsonNode yearsNode = firstNonMissing(candidate, "totalExperienceYears", "yearsExperience", "years_experience");
        if (yearsNode != null && yearsNode.isNumber()) {
            data.setYearsExperience(new RawValue<>(yearsNode.asDouble(), method));
        }

        JsonNode skillSet = firstNonMissing(candidate, "skillSet", "skills");
        if (skillSet != null && skillSet.isArray()) {
            for (JsonNode skillNode : skillSet) {
                if (skillNode.isTextual() && !skillNode.asText().isBlank()) {
                    data.addSkill(new RawValue<>(skillNode.asText(), method));
                }
            }
        }

        JsonNode workHistory = firstNonMissing(candidate, "workHistory", "experience");
        if (workHistory != null && workHistory.isArray()) {
            for (JsonNode job : workHistory) {
                String employer = textOrNull(job, "employer", "company");
                String role = textOrNull(job, "role", "title");
                String from = textOrNull(job, "from", "start");
                String to = textOrNull(job, "to", "end");
                String details = textOrNull(job, "details", "summary", "description");
                if (employer != null || role != null) {
                    data.addExperience(new RawValue<>(new RawExperience(employer, role, from, to, details), method));
                }
            }
        }

        JsonNode academics = firstNonMissing(candidate, "academics", "education");
        if (academics != null && academics.isArray()) {
            for (JsonNode edu : academics) {
                String school = textOrNull(edu, "school", "institution");
                String qualification = textOrNull(edu, "qualification", "degree");
                String major = textOrNull(edu, "major", "field");
                String gradYear = textOrNull(edu, "graduationYear", "end_year", "endYear");
                if (school != null || qualification != null) {
                    data.addEducation(new RawValue<>(new RawEducation(school, qualification, major, gradYear), method));
                }
            }
        }

        JsonNode socialLinks = firstNonMissing(candidate, "socialLinks", "links");
        if (socialLinks != null) {
            text(socialLinks, "linkedIn", "linkedin").ifPresent(v -> data.setLinkedin(new RawValue<>(v, method)));
            text(socialLinks, "gitHub", "github").ifPresent(v -> data.setGithub(new RawValue<>(v, method)));
            text(socialLinks, "website", "portfolio").ifPresent(v -> data.setPortfolio(new RawValue<>(v, method)));
        }

        return data;
    }

    private JsonNode firstNonMissing(JsonNode parent, String... names) {
        for (String name : names) {
            JsonNode n = parent.path(name);
            if (!n.isMissingNode() && !n.isNull()) {
                return n;
            }
        }
        return null;
    }

    private java.util.Optional<String> text(JsonNode parent, String... names) {
        return java.util.Optional.ofNullable(textOrNull(parent, names));
    }

    private String textOrNull(JsonNode parent, String... names) {
        if (parent == null) return null;
        for (String name : names) {
            JsonNode n = parent.path(name);
            if (n.isTextual() && !n.asText().isBlank()) {
                return n.asText().trim();
            }
            if (n.isNumber()) {
                return n.asText();
            }
        }
        return null;
    }
}
