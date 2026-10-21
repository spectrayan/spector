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
package com.spectrayan.spector.mcp.tools.memory;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * memory_forget followed by memory_purge on the same id, against a real disk-backed store.
 *
 * <p>Regression test: purge used to look only in the live index, which forget had already removed the id
 * from, so it answered "No memory found" and the text stayed on disk.</p>
 */
@DisplayName("memory_forget then memory_purge (disk-backed)")
class MemoryForgetThenPurgeIntegrationTest {

    private static final String SECRET = "Forget-then-purge-over-MCP-secret-5d1e";

    @Test
    @DisplayName("purge after forget returns a receipt and the text is gone from every file")
    void forgetThenPurge_erasesTextAndReturnsReceipt(@TempDir Path dir) throws Exception {
        String purgeText;
        try (SpectorMemory memory = diskMemory(dir)) {
            memory.remember("mcp-fp", SECRET, MemoryType.SEMANTIC, "t");

            String forgetText = text(new MemoryForgetTool(memory).execute(Map.of("memory_id", "mcp-fp")));
            assertThat(forgetText).contains("has been forgotten");

            purgeText = text(new MemoryPurgeTool(memory).execute(Map.of("memory_id", "mcp-fp")));
        }

        assertThat(purgeText)
                .doesNotContain("No memory found")
                .contains("was PURGED")
                .contains("memory_id: mcp-fp")
                .contains("purged_at: ")
                .contains("state_before: forgotten (tombstoned)")
                .contains("text_destroyed: true")
                .contains(SECRET.length() + " text bytes overwritten with zeros");

        List<Path> files;
        try (var walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        assertThat(files).isNotEmpty();
        for (Path f : files) {
            assertThat(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1))
                    .as("text must not survive in %s", f)
                    .doesNotContain(SECRET);
        }
    }

    private static SpectorMemory diskMemory(Path dir) {
        EmbeddingProvider embed = new HashEmbedder();
        var props = new MemoryProperties()
                .setDimensions(embed.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        return DefaultSpectorMemory.builder(props)
                .embeddingProvider(embed)
                .namespaceId("mcp-forget-purge")
                .persistence(dir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();
    }

    private static String text(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isFalse();
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    /** Deterministic, offline embedder: a seeded Gaussian vector per text, L2-normalised. */
    private static final class HashEmbedder implements EmbeddingProvider {
        private static final int DIMS = 64;

        @Override
        public EmbeddingResult embed(String text) {
            Random rng = new Random(text.hashCode() * 31L + text.length());
            float[] v = new float[DIMS];
            float sumSq = 0f;
            for (int i = 0; i < DIMS; i++) {
                v[i] = (float) rng.nextGaussian();
                sumSq += v[i] * v[i];
            }
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < DIMS; i++) {
                v[i] /= norm;
            }
            return new EmbeddingResult(v, text.split("\\s+").length, "hash-64");
        }

        @Override
        public int dimensions() {
            return DIMS;
        }

        @Override
        public String modelName() {
            return "hash-64";
        }
    }
}
