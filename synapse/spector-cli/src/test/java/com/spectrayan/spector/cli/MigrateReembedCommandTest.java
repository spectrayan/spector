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

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.persist.StoreEmbeddingIdentity;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * CLI tests for {@code spector migrate reembed} (Issue #1160).
 */
class MigrateReembedCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void cliMigrateReembed_dryRunAndLiveRoundTrip() {
        Path fromDir = tempDir.resolve("store-v1");
        Path toDir = tempDir.resolve("store-v2");

        EmbeddingProvider oldEmbedder = createEmbedder("embed-v1", 64);
        EmbeddingProvider newEmbedder = createEmbedder("embed-v2", 128);

        SpectorProperties sourceProps = SpectorProperties.builder().build().withDimensions(64);
        sourceProps.memory().setEntityExtractionParallelism(0);

        CognitiveRecord expectedLive;
        try (SpectorMemory source = SpectorMemory.builder(sourceProps)
                .persistence(fromDir)
                .namespaceId("prod")
                .embeddingProvider(oldEmbedder)
                .build()) {

            source.remember(
                    "mem-live-1",
                    "Zero-copy Panama FFM slabs require 64-byte cache-line alignment",
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.9f, 0.8f, 0.85f, (byte) 80, (byte) 70))
                            .overrideTimestampMs(1_710_000_000_000L)
                            .metadata(Map.of("module", "spector-kernel"))
                            .build(),
                    "panama", "simd"
            );

            source.remember(
                    "mem-tombstoned-1",
                    "Obsolete note that was forgotten before migration",
                    MemoryType.SEMANTIC,
                    MemorySource.OBSERVED,
                    "obsolete"
            );
            source.forget("mem-tombstoned-1");

            source.remember(
                    "mem-purged-1",
                    "Sensitive token that was purged before migration",
                    MemoryType.SEMANTIC,
                    MemorySource.OBSERVED,
                    "sensitive"
            );
            source.purge("mem-purged-1");

            expectedLive = source.inspect("mem-live-1");
        }

        // Verify MigrateCommand is registered under root `spector` command
        CommandLine.Command rootAnnotation = SpectorCtl.class.getAnnotation(CommandLine.Command.class);
        assertThat(rootAnnotation).isNotNull();
        assertThat(rootAnnotation.subcommands()).contains(MigrateCommand.class);

        // 1. Run `spector migrate reembed --from ... --to ... --namespace prod --dry-run`
        MigrateCommand.ReembedSubcommand reembedSub = new MigrateCommand.ReembedSubcommand(newEmbedder);
        CommandLine cmd = buildCliWithReembed(reembedSub);
        StringWriter dryOut = new StringWriter();
        cmd.setOut(new PrintWriter(dryOut));

        int dryExit = cmd.execute("reembed",
                "--from", fromDir.toString(),
                "--to", toDir.toString(),
                "--namespace", "prod",
                "--dry-run");

        assertThat(dryExit).isZero();
        assertThat(dryOut.toString())
                .contains("dry-run=true")
                .contains("copied=1")
                .contains("tombstoned=1")
                .contains("purged=1")
                .contains("fields-not-carried-over:");
        assertThat(toDir).doesNotExist();

        // 2. Run live `spector migrate reembed --from ... --to ... --namespace prod`
        StringWriter liveOut = new StringWriter();
        cmd.setOut(new PrintWriter(liveOut));

        int liveExit = cmd.execute("reembed",
                "--from", fromDir.toString(),
                "--to", toDir.toString(),
                "--namespace", "prod");

        assertThat(liveExit).isZero();
        assertThat(liveOut.toString())
                .contains("dry-run=false")
                .contains("source-embedder=embed-v1 (64d)")
                .contains("target-embedder=embed-v2 (128d)")
                .contains("copied=1")
                .contains("tombstoned=1")
                .contains("purged=1")
                .contains("fields-not-carried-over:");

        StoreEmbeddingIdentity.RecordedIdentity targetIdentity = StoreEmbeddingIdentity.read(toDir).orElseThrow();
        assertThat(targetIdentity.model()).isEqualTo("embed-v2");
        assertThat(targetIdentity.dimensions()).isEqualTo(128);

        // 3. Reopen target store with newEmbedder and verify fields + recall
        SpectorProperties targetProps = SpectorProperties.builder().build().withDimensions(128);
        targetProps.memory().setEntityExtractionParallelism(0);

        try (SpectorMemory target = SpectorMemory.builder(targetProps)
                .persistence(toDir)
                .namespaceId("prod")
                .embeddingProvider(newEmbedder)
                .build()) {

            CognitiveRecord migrated = target.inspect("mem-live-1");
            assertThat(migrated).isNotNull();
            assertThat(migrated.id()).isEqualTo("mem-live-1");
            assertThat(migrated.text()).isEqualTo(expectedLive.text());
            assertThat(migrated.tags()).containsExactly("panama", "simd");
            assertThat(migrated.valence()).isEqualTo(expectedLive.valence());
            assertThat(migrated.arousal()).isEqualTo(expectedLive.arousal());
            assertThat(migrated.importance()).isCloseTo(expectedLive.importance(), within(1e-5f));
            assertThat(migrated.source()).isEqualTo(MemorySource.USER_STATED);
            assertThat(migrated.timestampMs()).isEqualTo(1_710_000_000_000L);
            assertThat(migrated.metadata()).containsEntry("module", "spector-kernel");

            assertThat(target.inspect("mem-tombstoned-1")).isNull();
            assertThat(target.inspect("mem-purged-1")).isNull();

            List<CognitiveResult> recalled = target.recall(
                    "Panama FFM slabs 64-byte cache-line alignment",
                    RecallOptions.builder().topK(5).build());
            assertThat(recalled)
                    .extracting(CognitiveResult::id)
                    .containsExactly("mem-live-1");
        }
    }

    private static CommandLine buildCliWithReembed(MigrateCommand.ReembedSubcommand reembedSubcommand) {
        CommandLine.IFactory factory = new CommandLine.IFactory() {
            private final CommandLine.IFactory delegate = CommandLine.defaultFactory();

            @Override
            @SuppressWarnings("unchecked")
            public <K> K create(Class<K> cls) throws Exception {
                if (cls == MigrateCommand.ReembedSubcommand.class) {
                    return (K) reembedSubcommand;
                }
                return delegate.create(cls);
            }
        };
        return new CommandLine(new MigrateCommand(), factory);
    }

    private static EmbeddingProvider createEmbedder(String modelName, int dimensions) {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                float[] vec = new float[dimensions];
                String[] words = text.toLowerCase().split("[^a-z0-9]+");
                for (String w : words) {
                    if (w.isEmpty()) continue;
                    vec[Math.floorMod(w.hashCode(), dimensions)] += 1.0f;
                }
                float norm = 0f;
                for (float v : vec) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 0f) {
                    for (int i = 0; i < dimensions; i++) vec[i] /= norm;
                } else {
                    vec[0] = 1.0f;
                }
                return EmbeddingResult.of(vec, modelName);
            }

            @Override
            public int dimensions() {
                return dimensions;
            }

            @Override
            public String modelName() {
                return modelName;
            }
        };
    }
}
