package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryNormalizerTest {

    @Test
    void mapsCommonAliases() {
        assertThat(CountryNormalizer.normalize("USA")).isEqualTo("US");
        assertThat(CountryNormalizer.normalize("United States")).isEqualTo("US");
        assertThat(CountryNormalizer.normalize("India")).isEqualTo("IN");
        assertThat(CountryNormalizer.normalize("UK")).isEqualTo("GB");
    }

    @Test
    void unresolvableReturnsNull() {
        assertThat(CountryNormalizer.normalize("Narnia")).isNull();
    }

    @Test
    void nullReturnsNull() {
        assertThat(CountryNormalizer.normalize(null)).isNull();
    }
}
