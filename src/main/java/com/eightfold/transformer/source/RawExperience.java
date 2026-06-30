package com.eightfold.transformer.source;

/** Unparsed/lightly-parsed work experience entry straight out of a source. */
public record RawExperience(String company, String title, String start, String end, String summary) {
}
