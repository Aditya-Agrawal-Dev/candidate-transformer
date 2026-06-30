package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void lowercasesAndTrims() {
        assertThat(EmailNormalizer.normalize("  Priya.Sharma@GMAIL.com  ")).isEqualTo("priya.sharma@gmail.com");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThat(EmailNormalizer.normalize("not-an-email")).isNull();
        assertThat(EmailNormalizer.normalize("missing@domain")).isNull();
        assertThat(EmailNormalizer.normalize("@nodomain.com")).isNull();
    }

    @Test
    void nullAndBlankReturnNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
        assertThat(EmailNormalizer.normalize("   ")).isNull();
    }
}
