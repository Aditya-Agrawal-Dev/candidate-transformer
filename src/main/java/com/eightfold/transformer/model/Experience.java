package com.eightfold.transformer.model;

/**
 * A single work-experience entry.
 *
 * @param company company name
 * @param title   job title
 * @param start   normalized "YYYY-MM" or null
 * @param end     normalized "YYYY-MM", "present" (lowercase literal), or null
 * @param summary free-text summary/bullets, or null
 */
public record Experience(String company, String title, String start, String end, String summary) {

    /** Natural reverse-chronological ordering key: most recent / current role first. */
    public String sortKey() {
        if ("present".equalsIgnoreCase(end)) {
            return "9999-99";
        }
        return end != null ? end : (start != null ? start : "0000-00");
    }
}
