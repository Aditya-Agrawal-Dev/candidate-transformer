package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateNormalizerTest {

    @Test
    void normalizesVariousFormats() {
        assertThat(DateNormalizer.normalize("2021-03")).isEqualTo("2021-03");
        assertThat(DateNormalizer.normalize("2021-03-15")).isEqualTo("2021-03");
        assertThat(DateNormalizer.normalize("03/2021")).isEqualTo("2021-03");
        assertThat(DateNormalizer.normalize("2021")).isEqualTo("2021-01");
        assertThat(DateNormalizer.normalize("Mar 2021")).isEqualTo("2021-03");
        assertThat(DateNormalizer.normalize("March 2021")).isEqualTo("2021-03");
    }

    @Test
    void normalizesPresentSynonyms() {
        assertThat(DateNormalizer.normalize("Present")).isEqualTo("present");
        assertThat(DateNormalizer.normalize("current")).isEqualTo("present");
        assertThat(DateNormalizer.normalize("Now")).isEqualTo("present");
    }

    @Test
    void rejectsGarbageDates() {
        assertThat(DateNormalizer.normalize("not a date")).isNull();
        assertThat(DateNormalizer.normalize("13/2021")).isNull(); // invalid month
    }

    @Test
    void nullReturnsNull() {
        assertThat(DateNormalizer.normalize(null)).isNull();
    }
}
