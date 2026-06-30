package com.eightfold.transformer.model;

/**
 * A single education entry.
 *
 * @param institution school/university name
 * @param degree      degree name (e.g. "B.Tech", "M.S.")
 * @param field       field of study, or null
 * @param endYear     graduation year as an Integer, or null if unknown
 */
public record Education(String institution, String degree, String field, Integer endYear) {

    public int sortKey() {
        return endYear != null ? endYear : -1;
    }
}
