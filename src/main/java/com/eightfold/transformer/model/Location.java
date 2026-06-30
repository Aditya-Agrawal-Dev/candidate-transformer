package com.eightfold.transformer.model;

/**
 * Canonical location. Any component may be null if unknown - never invented.
 *
 * @param city    free-text city name, trimmed/title-cased
 * @param region  state/province, trimmed
 * @param country ISO 3166-1 alpha-2 code (e.g. "US", "IN"), or null if it could not
 *                be confidently resolved
 */
public record Location(String city, String region, String country) {

    public static final Location EMPTY = new Location(null, null, null);

    public boolean isEmpty() {
        return city == null && region == null && country == null;
    }
}
