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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;

import picocli.CommandLine;

import java.nio.file.Path;

class McpCommandTest {

    @Test
    @DisplayName("Picocli parses all declared flags into McpCommand fields")
    void testPicocliFlagParsing() {
        McpCommand cmd = new McpCommand(null, null, null);
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs(
                "--dims", "384",
                "--capacity", "5000",
                "--data-dir", "/tmp/spector-mcp-test",
                "--namespace", "test-ns",
                "--ollama-url", "http://127.0.0.1:11434",
                "--ollama-model", "nomic-embed-text",
                "--mode", "openclaw",
                "--config", "spector.yml",
                "--profile", "dev"
        );

        assertThat(cmd.getDims()).isEqualTo(384);
        assertThat(cmd.getCapacity()).isEqualTo(5000);
        assertThat(cmd.getDataDir()).isEqualTo("/tmp/spector-mcp-test");
        assertThat(cmd.getNamespace()).isEqualTo("test-ns");
        assertThat(cmd.getOllamaUrl()).isEqualTo("http://127.0.0.1:11434");
        assertThat(cmd.getOllamaModel()).isEqualTo("nomic-embed-text");
        assertThat(cmd.getMode()).isEqualTo("openclaw");
        assertThat(cmd.getConfigFile()).isEqualTo("spector.yml");
        assertThat(cmd.getProfile()).isEqualTo("dev");
        assertThat(cmd.hasCustomFlags()).isTrue();
    }

    @Test
    @DisplayName("Passing --data-dir and --dims customizes the resolved SpectorMemory")
    void testResolveMemoryHonorsCustomFlags(@TempDir Path tempDir) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SpectorMemory> memProv = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingProvider> embProv = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SpectorConfigProperties> cfgProv = mock(ObjectProvider.class);

        // Active default memory that should be bypassed when custom flags are given
        SpectorMemory defaultMemory = mock(SpectorMemory.class);
        EmbeddingProvider mockEmbedder = mock(EmbeddingProvider.class);
        when(mockEmbedder.dimensions()).thenReturn(384);
        when(embProv.getIfAvailable()).thenReturn(mockEmbedder);

        McpCommand cmd = new McpCommand(memProv, embProv, cfgProv);
        cmd.setDims(384);
        cmd.setDataDir(tempDir.toAbsolutePath().toString());

        SpectorMemory resolved = cmd.resolveMemory();

        assertThat(resolved).isNotNull();
        assertThat(resolved).isNotSameAs(defaultMemory);
        assertThat(resolved).isInstanceOf(DefaultSpectorMemory.class);

        assertThat(cmd.getLastResolvedProperties()).isNotNull();
        assertThat(cmd.getLastResolvedProperties().getDimensions()).isEqualTo(384);
        assertThat(cmd.getLastResolvedProperties().getPersistencePath()).isEqualTo(tempDir.toAbsolutePath().toString());
        resolved.close();
    }
}
