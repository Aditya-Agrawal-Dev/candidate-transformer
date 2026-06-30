package com.eightfold.transformer.model;

import java.util.List;

/**
 * Canonical set of candidate links.
 *
 * @param linkedin  normalized LinkedIn profile URL, or null
 * @param github    normalized GitHub profile URL, or null
 * @param portfolio normalized personal site/portfolio URL, or null
 * @param other     any other URLs found (deduplicated)
 */
public record Links(String linkedin, String github, String portfolio, List<String> other) {

    public static final Links EMPTY = new Links(null, null, null, List.of());

    public Links {
        other = other == null ? List.of() : List.copyOf(other);
    }
}
