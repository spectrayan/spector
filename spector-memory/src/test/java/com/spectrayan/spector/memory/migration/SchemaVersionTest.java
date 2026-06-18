/*
 * Copyright 2026 Spectrayan
 */
package com.spectrayan.spector.memory.migration;

import com.spectrayan.spector.memory.migration.migrations.V1_0_to_V1_1_EncryptionMarker;
import com.spectrayan.spector.memory.migration.migrations.V1_1_to_V2_0_AnalyticsAndSharding;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SchemaVersionTest {

    @Test
    @DisplayName("Parse valid version string")
    void parseValid() {
        var v = SchemaVersion.parse("2.1.3");
        assertThat(v.major()).isEqualTo(2);
        assertThat(v.minor()).isEqualTo(1);
        assertThat(v.patch()).isEqualTo(3);
    }

    @Test
    @DisplayName("Parse null/blank returns V1.0.0")
    void parseNullReturnsV1() {
        assertThat(SchemaVersion.parse(null)).isEqualTo(SchemaVersion.V1_0_0);
        assertThat(SchemaVersion.parse("")).isEqualTo(SchemaVersion.V1_0_0);
    }

    @Test
    @DisplayName("Parse invalid throws")
    void parseInvalid() {
        assertThatThrownBy(() -> SchemaVersion.parse("1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Comparison ordering")
    void comparison() {
        assertThat(SchemaVersion.V1_0_0.isOlderThan(SchemaVersion.V1_1_0)).isTrue();
        assertThat(SchemaVersion.V1_1_0.isOlderThan(SchemaVersion.V2_0_0)).isTrue();
        assertThat(SchemaVersion.V2_0_0.isOlderThan(SchemaVersion.V1_0_0)).isFalse();
        assertThat(SchemaVersion.V1_0_0.isOlderThan(SchemaVersion.V1_0_0)).isFalse();
    }

    @Test
    @DisplayName("needsMigration detects stale versions")
    void needsMigration() {
        assertThat(SchemaVersion.V1_0_0.needsMigration(SchemaVersion.V2_0_0)).isTrue();
        assertThat(SchemaVersion.V2_0_0.needsMigration(SchemaVersion.V2_0_0)).isFalse();
    }

    @Test
    @DisplayName("toString produces dot-separated format")
    void toStringFormat() {
        assertThat(SchemaVersion.V2_0_0.toString()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("CURRENT is V2.0.0")
    void currentVersion() {
        assertThat(SchemaVersion.CURRENT).isEqualTo(SchemaVersion.V2_0_0);
    }
}
