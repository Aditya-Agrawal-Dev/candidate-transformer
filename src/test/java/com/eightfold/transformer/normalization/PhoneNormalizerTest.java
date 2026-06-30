package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {

    @Test
    void normalizesUsNumberVariants() {
        assertThat(PhoneNormalizer.normalize("(415) 555-0198", "US")).isEqualTo("+14155550198");
        assertThat(PhoneNormalizer.normalize("415-555-0198", "US")).isEqualTo("+14155550198");
        assertThat(PhoneNormalizer.normalize("+1 415 555 0198", "US")).isEqualTo("+14155550198");
    }

    @Test
    void rejectsInvalidPhone() {
        assertThat(PhoneNormalizer.normalize("123", "US")).isNull();
        assertThat(PhoneNormalizer.normalize("not a phone", "US")).isNull();
    }

    @Test
    void nullReturnsNull() {
        assertThat(PhoneNormalizer.normalize(null)).isNull();
    }

    @Test
    void e164ValidatorAcceptsOnlyE164() {
        assertThat(PhoneNormalizer.isValidE164("+14155550198")).isTrue();
        assertThat(PhoneNormalizer.isValidE164("4155550198")).isFalse();
        assertThat(PhoneNormalizer.isValidE164(null)).isFalse();
    }
}
