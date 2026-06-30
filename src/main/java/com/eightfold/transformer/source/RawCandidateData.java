package com.eightfold.transformer.source;

import com.eightfold.transformer.model.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate representation produced by every {@code SourceParser}.
 * <p>
 * This is deliberately source-agnostic and pre-normalization: it just captures
 * "what raw signal did this source contain, and how did we find it". The
 * normalization layer turns these raw values into canonical types; the merge
 * engine then reconciles raw values from multiple sources of this shape.
 * <p>
 * Every collection defaults to empty (never null) so downstream code never has
 * to null-check. A missing/unknown field is represented by an empty list or a
 * null scalar RawValue - we never invent data to fill gaps.
 */
public final class RawCandidateData {

    private final SourceType sourceType;
    private final String sourceIdentifier; // e.g. "recruiter.csv", "resume.pdf"

    private RawValue<String> fullName;
    private final List<RawValue<String>> emails = new ArrayList<>();
    private final List<RawValue<String>> phones = new ArrayList<>();
    private RawValue<String> city;
    private RawValue<String> region;
    private RawValue<String> country;
    private RawValue<String> linkedin;
    private RawValue<String> github;
    private RawValue<String> portfolio;
    private final List<RawValue<String>> otherLinks = new ArrayList<>();
    private RawValue<String> headline;
    private RawValue<Double> yearsExperience;
    private final List<RawValue<String>> skills = new ArrayList<>();
    private final List<RawValue<RawExperience>> experience = new ArrayList<>();
    private final List<RawValue<RawEducation>> education = new ArrayList<>();

    /** True if this source failed to parse at all (malformed/garbage input). */
    private boolean malformed = false;
    private String malformedReason;

    public RawCandidateData(SourceType sourceType, String sourceIdentifier) {
        this.sourceType = sourceType;
        this.sourceIdentifier = sourceIdentifier;
    }

    public void markMalformed(String reason) {
        this.malformed = true;
        this.malformedReason = reason;
    }

    public boolean isMalformed() {
        return malformed;
    }

    public String malformedReason() {
        return malformedReason;
    }

    public SourceType sourceType() {
        return sourceType;
    }

    public String sourceIdentifier() {
        return sourceIdentifier;
    }

    public RawValue<String> fullName() {
        return fullName;
    }

    public void setFullName(RawValue<String> fullName) {
        this.fullName = fullName;
    }

    public List<RawValue<String>> emails() {
        return emails;
    }

    public void addEmail(RawValue<String> email) {
        if (email != null && email.value() != null && !email.value().isBlank()) {
            emails.add(email);
        }
    }

    public List<RawValue<String>> phones() {
        return phones;
    }

    public void addPhone(RawValue<String> phone) {
        if (phone != null && phone.value() != null && !phone.value().isBlank()) {
            phones.add(phone);
        }
    }

    public RawValue<String> city() {
        return city;
    }

    public void setCity(RawValue<String> city) {
        this.city = city;
    }

    public RawValue<String> region() {
        return region;
    }

    public void setRegion(RawValue<String> region) {
        this.region = region;
    }

    public RawValue<String> country() {
        return country;
    }

    public void setCountry(RawValue<String> country) {
        this.country = country;
    }

    public RawValue<String> linkedin() {
        return linkedin;
    }

    public void setLinkedin(RawValue<String> linkedin) {
        this.linkedin = linkedin;
    }

    public RawValue<String> github() {
        return github;
    }

    public void setGithub(RawValue<String> github) {
        this.github = github;
    }

    public RawValue<String> portfolio() {
        return portfolio;
    }

    public void setPortfolio(RawValue<String> portfolio) {
        this.portfolio = portfolio;
    }

    public List<RawValue<String>> otherLinks() {
        return otherLinks;
    }

    public void addOtherLink(RawValue<String> link) {
        if (link != null && link.value() != null && !link.value().isBlank()) {
            otherLinks.add(link);
        }
    }

    public RawValue<String> headline() {
        return headline;
    }

    public void setHeadline(RawValue<String> headline) {
        this.headline = headline;
    }

    public RawValue<Double> yearsExperience() {
        return yearsExperience;
    }

    public void setYearsExperience(RawValue<Double> yearsExperience) {
        this.yearsExperience = yearsExperience;
    }

    public List<RawValue<String>> skills() {
        return skills;
    }

    public void addSkill(RawValue<String> skill) {
        if (skill != null && skill.value() != null && !skill.value().isBlank()) {
            skills.add(skill);
        }
    }

    public List<RawValue<RawExperience>> experience() {
        return experience;
    }

    public void addExperience(RawValue<RawExperience> exp) {
        if (exp != null && exp.value() != null) {
            experience.add(exp);
        }
    }

    public List<RawValue<RawEducation>> education() {
        return education;
    }

    public void addEducation(RawValue<RawEducation> edu) {
        if (edu != null && edu.value() != null) {
            education.add(edu);
        }
    }
}
