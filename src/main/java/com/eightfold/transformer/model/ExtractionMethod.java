package com.eightfold.transformer.model;

/**
 * Describes HOW a value was extracted/derived from its source, for provenance.
 */
public enum ExtractionMethod {
    CSV_COLUMN_MAPPING,
    JSON_FIELD_MAPPING,
    REGEX_EXTRACTION,
    HEADER_SECTION_HEURISTIC,
    KEYWORD_EXTRACTION,
    FREE_TEXT_NER_HEURISTIC,
    CANONICAL_MAPPING,
    MERGE_RESOLUTION,
    DERIVED_COMPUTATION
}
