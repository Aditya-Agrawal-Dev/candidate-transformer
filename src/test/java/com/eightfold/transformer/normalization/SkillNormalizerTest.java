package com.eightfold.transformer.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNormalizerTest {

    @Test
    void mapsKnownAliases() {
        assertThat(SkillNormalizer.normalize("js")).isEqualTo("JavaScript");
        assertThat(SkillNormalizer.normalize("springboot")).isEqualTo("Spring Boot");
        assertThat(SkillNormalizer.normalize("postgres")).isEqualTo("PostgreSQL");
        assertThat(SkillNormalizer.normalize("c plus plus")).isEqualTo("C++");
        assertThat(SkillNormalizer.normalize("machine learning")).isEqualTo("Machine Learning");
    }

    @Test
    void isCaseAndWhitespaceInsensitive() {
        assertThat(SkillNormalizer.normalize("  Spring_Boot ")).isEqualTo("Spring Boot");
        assertThat(SkillNormalizer.normalize("POSTGRES")).isEqualTo("PostgreSQL");
    }

    @Test
    void unknownSkillIsTitleCasedNotDropped() {
        assertThat(SkillNormalizer.normalize("snowflake")).isEqualTo("Snowflake");
    }

    @Test
    void nullAndBlankReturnNull() {
        assertThat(SkillNormalizer.normalize(null)).isNull();
        assertThat(SkillNormalizer.normalize("   ")).isNull();
    }
}
