package com.eightfold.transformer.source;

/** Unparsed/lightly-parsed education entry straight out of a source. */
public record RawEducation(String institution, String degree, String field, String endYear) {
}
