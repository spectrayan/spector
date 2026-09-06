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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpectorCliProfileRoutingTest {

    @Test
    void determineProfile_nullOrEmptyArgs_returnsRemoteProfile() {
        assertThat(SpectorCliApplication.determineProfile(null)).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[0])).isEqualTo("cli-remote");
    }

    @Test
    void determineProfile_helpOrVersionFlags_returnsRemoteProfile() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"--help"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"-h"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"--version"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"-V"})).isEqualTo("cli-remote");
    }

    @Test
    void determineProfile_remoteSubcommands_returnsRemoteProfile() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"status"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"search", "query"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"recall", "query"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"index", "list"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"ingest", "--file", "doc.txt"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"remember", "--file", "doc.txt"})).isEqualTo("cli-remote");
    }

    @Test
    void determineProfile_mcpSubcommand_returnsEmbeddedProfile() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"mcp"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"mcp", "--dims", "384"})).isEqualTo("cli-embedded");
    }

    @Test
    void determineProfile_memorySubcommands_returnsEmbeddedProfile() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "status"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "recall", "test"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "remember", "--id", "1", "--text", "fact"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "export", "--offline", "--output", "out.smb"})).isEqualTo("cli-embedded");
        // But help on memory subcommand remains remote for instant response
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "--help"})).isEqualTo("cli-remote");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"memory", "status", "-h"})).isEqualTo("cli-remote");
    }

    @Test
    void determineProfile_localBatchIngestAndRemember_returnsEmbeddedProfile() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"ingest", "--root", "/data/docs"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"remember", "--root", "/data/docs"})).isEqualTo("cli-embedded");
        // Config-driven local batch (no --content or --file)
        assertThat(SpectorCliApplication.determineProfile(new String[]{"remember", "--config", "spector.yml"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"ingest", "--config", "spector.yml"})).isEqualTo("cli-embedded");
    }

    @Test
    void determineProfile_explicitProfileFlags_respected() {
        assertThat(SpectorCliApplication.determineProfile(new String[]{"status", "--profile=cli-embedded"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"status", "--profile", "cli-embedded"})).isEqualTo("cli-embedded");
        assertThat(SpectorCliApplication.determineProfile(new String[]{"mcp", "--profile=cli-remote"})).isEqualTo("cli-remote");
    }
}
