/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.cli;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for build metadata loading and its fallback behaviour.
 */
class BuildInfoTest {

    // ─────────────── clean(): normalisation and fallback ───────────────

    @Test
    void clean_nullValue_returnsUnknown() {
        assertThat(BuildInfo.clean(null)).isEqualTo(BuildInfo.UNKNOWN);
    }

    @Test
    void clean_emptyValue_returnsUnknown() {
        assertThat(BuildInfo.clean("")).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.clean("   ")).isEqualTo(BuildInfo.UNKNOWN);
    }

    @Test
    void clean_unresolvedPlaceholder_returnsUnknown() {
        assertThat(BuildInfo.clean("${git.commit.id.abbrev}")).isEqualTo(BuildInfo.UNKNOWN);
    }

    @Test
    void clean_paddedPlaceholder_returnsUnknown() {
        // Guards the ordering inside clean(): strip() must run before startsWith().
        assertThat(BuildInfo.clean("  ${git.branch}  ")).isEqualTo(BuildInfo.UNKNOWN);
    }

    @Test
    void clean_paddedValue_returnsTrimmedValue() {
        assertThat(BuildInfo.clean("  147070a  ")).isEqualTo("147070a");
    }

    // ─────────────── fromProperties(): the two metadata sources ───────────────

    @Test
    void fromProperties_populated_returnsValues() {
        var props = new Properties();
        props.setProperty("version", "0.1.0");
        props.setProperty("buildTime", "2026-07-21T10:28:21Z");
        props.setProperty("commit", "abc1234");
        props.setProperty("branch", "main");

        var info = BuildInfo.fromProperties(props);

        assertThat(info.version()).isEqualTo("0.1.0");
        assertThat(info.buildTime()).isEqualTo("2026-07-21T10:28:21Z");
        assertThat(info.commit()).isEqualTo("abc1234");
        assertThat(info.branch()).isEqualTo("main");
    }

    @Test
    void fromProperties_emptyProperties_returnsUnknownForBuildFields() {
        var info = BuildInfo.fromProperties(new Properties());

        assertThat(info.version()).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.buildTime()).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.commit()).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.branch()).isEqualTo(BuildInfo.UNKNOWN);
    }

    @Test
    void fromProperties_emptyProperties_stillResolvesRuntimeFields() {
        // Java and OS details come from system properties, not the filtered
        // resource — they stay available even with no build metadata at all.
        var info = BuildInfo.fromProperties(new Properties());

        assertThat(info.javaVersion()).isNotEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.javaVendor()).isNotEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.osName()).isNotEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.osArch()).isNotEqualTo(BuildInfo.UNKNOWN);
    }

    // ─────────────── load(): the filtered resource reaches the classpath ───────────────

    @Test
    void load_readsFilteredBuildMetadata() {
        var info = BuildInfo.load();

        assertThat(info.version()).isNotEqualTo(BuildInfo.UNKNOWN);
        assertThat(info.buildTime()).isNotEqualTo(BuildInfo.UNKNOWN);
    }
}
