package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    @Test
    void normalizesLinkedInUrls() {
        assertThat(UrlNormalizer.normalizeLinkedIn("linkedin.com/in/priyasharma-eng"))
                .isEqualTo("https://www.linkedin.com/in/priyasharma-eng");
        assertThat(UrlNormalizer.normalizeLinkedIn("https://www.linkedin.com/in/Priya-Sharma/"))
                .isEqualTo("https://www.linkedin.com/in/priya-sharma");
    }

    @Test
    void normalizesGitHubUrls() {
        assertThat(UrlNormalizer.normalizeGitHub("github.com/psharma-dev"))
                .isEqualTo("https://github.com/psharma-dev");
    }

    @Test
    void nonMatchingUrlReturnsNull() {
        assertThat(UrlNormalizer.normalizeLinkedIn("https://example.com")).isNull();
    }
}
