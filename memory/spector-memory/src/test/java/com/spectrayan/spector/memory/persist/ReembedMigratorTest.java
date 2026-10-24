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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Round-trip tests for {@link ReembedMigrator} (Issue #1160).
 */
class ReembedMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void migrate_roundTripPreservesAllLiveFieldsSkipsTombstonedAndPurgedAndRecallsEveryCopiedRecord() throws Exception {
        Path sourceDir = tempDir.resolve("source-store-64d");
        Path targetDir = tempDir.resolve("target-store-128d");

        EmbeddingProvider sourceEmbedder = deterministicEmbedder("model-alpha-64d", 64);
        EmbeddingProvider targetEmbedder = deterministicEmbedder("model-beta-128d", 128);

        CognitiveRecord origSem;
        CognitiveRecord origWork;
        CognitiveRecord origProc;
        CognitiveRecord origEpi;

        SpectorProperties sourceProps = SpectorProperties.builder().build().withDimensions(64);
        sourceProps.memory().setEntityExtractionParallelism(0);

        try (SpectorMemory source = SpectorMemory.builder(sourceProps)
                .persistence(sourceDir)
                .namespaceId("team-alpha")
                .embeddingProvider(sourceEmbedder)
                .build()) {

            source.remember(
                    "rec-sem-1",
                    "PostgreSQL B-tree indexes accelerate equality and range queries on high-cardinality columns",
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.9f, 0.7f, 0.8f, (byte) 75, (byte) 60))
                            .overrideTimestampMs(1_700_000_100_000L)
                            .metadata(Map.of("domain", "storage", "ticket", "1160"))
                            .build(),
                    "database", "postgres"
            );

            source.remember(
                    "rec-work-1",
                    "Active sprint goal is completing the offline re-embed store migration CLI",
                    MemoryType.WORKING,
                    MemorySource.OBSERVED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.8f, 0.6f, 0.95f, (byte) 50, (byte) 85))
                            .overrideTimestampMs(1_700_000_200_000L)
                            .metadata(Map.of("priority", "p0"))
                            .build(),
                    "sprint", "cli"
            );

            source.remember(
                    "rec-proc-1",
                    "Always run mvn license:check and kernel boundary tests before opening a pull request",
                    MemoryType.PROCEDURAL,
                    MemorySource.PROCEDURAL,
                    RememberContext.builder()
                            .hints(new RememberHints(0.7f, 0.5f, 0.6f, (byte) 40, (byte) 30))
                            .overrideTimestampMs(1_700_000_300_000L)
                            .metadata(Map.of("runbook", "release"))
                            .build(),
                    "workflow", "ci"
            );

            source.remember(
                    "rec-epi-1",
                    "Operator noted that switching from 64d to 128d embeddings requires re-embedding text",
                    MemoryType.EPISODIC,
                    MemorySource.OBSERVED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.85f, 0.75f, 0.9f, (byte) -45, (byte) 70))
                            .overrideTimestampMs(1_700_000_400_000L)
                            .metadata(Map.of("session", "ops-42"))
                            .build(),
                    "incident", "embeddings"
            );

            // Ingest a record and forget (tombstone) it
            source.remember(
                    "rec-forgotten",
                    "Temporary staging credential note that was forgotten by the user",
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.3f, 0.2f, 0.1f, (byte) -20, (byte) 40))
                            .overrideTimestampMs(1_700_000_500_000L)
                            .build(),
                    "staging", "secret"
            );
            assertThat(source.forgetWithResult("rec-forgotten").found()).isTrue();

            // Ingest a record and physically purge it
            source.remember(
                    "rec-purged",
                    "Redacted PII record that was permanently purged from the slab",
                    MemoryType.SEMANTIC,
                    MemorySource.USER_STATED,
                    RememberContext.builder()
                            .hints(new RememberHints(0.1f, 0.1f, 0.1f, (byte) -90, (byte) 90))
                            .overrideTimestampMs(1_700_000_600_000L)
                            .build(),
                    "pii", "purged"
            );
            assertThat(source.purge("rec-purged").found()).isTrue();

            origSem = source.inspect("rec-sem-1");
            origWork = source.inspect("rec-work-1");
            origProc = source.inspect("rec-proc-1");
            origEpi = source.inspect("rec-epi-1");
        }

        assertThat(origSem).isNotNull();
        assertThat(origWork).isNotNull();
        assertThat(origProc).isNotNull();
        assertThat(origEpi).isNotNull();

        // Snapshot every file in sourceDir to verify source is never modified
        Map<String, String> sourceChecksumsBefore = snapshotDirectoryChecksums(sourceDir);

        // 1. Dry-run: reports counts without writing targetDir and without modifying sourceDir
        ReembedMigrator.Result dryRunResult = ReembedMigrator.migrate(
                sourceDir, targetDir, "team-alpha", true, targetEmbedder, null, null);

        assertThat(dryRunResult.dryRun()).isTrue();
        assertThat(dryRunResult.copiedCount()).isEqualTo(4);
        assertThat(dryRunResult.skippedByReason().get(ReembedMigrator.REASON_TOMBSTONED)).isEqualTo(1);
        assertThat(dryRunResult.skippedByReason().get(ReembedMigrator.REASON_PURGED)).isEqualTo(1);
        assertThat(dryRunResult.fieldsNotCarriedOver())
                .isNotEmpty()
                .anyMatch(s -> s.contains("ICNU"));
        assertThat(targetDir).doesNotExist();
        assertThat(snapshotDirectoryChecksums(sourceDir)).isEqualTo(sourceChecksumsBefore);

        // 2. Live migration: copies live records, re-embeds with 128d model, skips tombstoned & purged
        ReembedMigrator.Result liveResult = ReembedMigrator.migrate(
                sourceDir, targetDir, "team-alpha", false, targetEmbedder, null, null);

        assertThat(liveResult.dryRun()).isFalse();
        assertThat(liveResult.copiedCount()).isEqualTo(4);
        assertThat(liveResult.skippedByReason().get(ReembedMigrator.REASON_TOMBSTONED)).isEqualTo(1);
        assertThat(liveResult.skippedByReason().get(ReembedMigrator.REASON_PURGED)).isEqualTo(1);
        assertThat(liveResult.sourceIdentity()).isNotNull();
        assertThat(liveResult.sourceIdentity().model()).isEqualTo("model-alpha-64d");
        assertThat(liveResult.sourceIdentity().dimensions()).isEqualTo(64);
        assertThat(liveResult.targetIdentity().model()).isEqualTo("model-beta-128d");
        assertThat(liveResult.targetIdentity().dimensions()).isEqualTo(128);

        // Source directory must remain byte-for-byte untouched after live migration
        assertThat(snapshotDirectoryChecksums(sourceDir)).isEqualTo(sourceChecksumsBefore);

        // Target embedding fingerprint must be persisted in targetDir/namespace.json
        StoreEmbeddingIdentity.RecordedIdentity persistedTargetIdentity = StoreEmbeddingIdentity.read(targetDir).orElseThrow();
        assertThat(persistedTargetIdentity.model()).isEqualTo("model-beta-128d");
        assertThat(persistedTargetIdentity.dimensions()).isEqualTo(128);

        // 3. Reopen target store with the 128d embedder and verify all fields + recall
        SpectorProperties targetProps = SpectorProperties.builder().build().withDimensions(128);
        targetProps.memory().setEntityExtractionParallelism(0);

        try (SpectorMemory target = SpectorMemory.builder(targetProps)
                .persistence(targetDir)
                .namespaceId("team-alpha")
                .embeddingProvider(targetEmbedder)
                .build()) {

            assertRecordPreserved(target.inspect("rec-sem-1"), origSem, 128);
            assertRecordPreserved(target.inspect("rec-work-1"), origWork, 128);
            assertRecordPreserved(target.inspect("rec-proc-1"), origProc, 128);
            assertRecordPreserved(target.inspect("rec-epi-1"), origEpi, 128);

            // Forgotten and purged records must never be resurrected
            assertThat(target.inspect("rec-forgotten")).isNull();
            assertThat(target.inspect("rec-purged")).isNull();

            // Recall must find every copied record in the target store
            RecallOptions recallAll = RecallOptions.builder()
                    .topK(10)
                    .memoryTypes(MemoryType.WORKING, MemoryType.EPISODIC, MemoryType.SEMANTIC, MemoryType.PROCEDURAL)
                    .build();

            assertRecalled(target, "PostgreSQL B-tree indexes high-cardinality columns", "rec-sem-1", recallAll);
            assertRecalled(target, "Active sprint goal offline re-embed store migration CLI", "rec-work-1", recallAll);
            assertRecalled(target, "Always run mvn license:check kernel boundary tests", "rec-proc-1", recallAll);
            assertRecalled(target, "switching from 64d to 128d embeddings requires re-embedding", "rec-epi-1", recallAll);

            List<CognitiveResult> secretSearch = target.recall("Temporary staging credential Redacted PII", recallAll);
            Set<String> recalledIds = secretSearch.stream().map(CognitiveResult::id).collect(Collectors.toSet());
            assertThat(recalledIds)
                    .doesNotContain("rec-forgotten", "rec-purged");
        }

        // 4. Attempting to migrate into an already-populated target directory must fail fast
        assertThatThrownBy(() -> ReembedMigrator.migrate(
                sourceDir, targetDir, "team-alpha", false, targetEmbedder, null, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("already contains a non-empty Spector store");

        // 5. Attempting to migrate into the same source directory must fail fast
        assertThatThrownBy(() -> ReembedMigrator.migrate(
                sourceDir, sourceDir, "team-alpha", false, targetEmbedder, null, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void migrate_resolvesNamespaceChildDirectoryWhenGivenMultiNamespaceRoot() {
        Path fromRoot = tempDir.resolve("multi-root-from");
        Path toRoot = tempDir.resolve("multi-root-to");
        Path nsSourceDir = fromRoot.resolve("billing");

        EmbeddingProvider sourceEmbedder = deterministicEmbedder("model-64", 64);
        EmbeddingProvider targetEmbedder = deterministicEmbedder("model-96", 96);

        SpectorProperties sourceProps = SpectorProperties.builder().build().withDimensions(64);
        sourceProps.memory().setEntityExtractionParallelism(0);

        try (SpectorMemory source = SpectorMemory.builder(sourceProps)
                .persistence(nsSourceDir)
                .namespaceId("billing")
                .embeddingProvider(sourceEmbedder)
                .build()) {
            source.remember("inv-1", "Invoice settlement ledger entry", MemoryType.SEMANTIC, MemorySource.OBSERVED, "finance");
        }

        ReembedMigrator.Result result = ReembedMigrator.migrate(
                fromRoot, toRoot, "billing", false, targetEmbedder, null, null);

        assertThat(result.copiedCount()).isEqualTo(1);
        assertThat(result.sourceDir()).isEqualTo(nsSourceDir);
        assertThat(result.targetDir()).isEqualTo(toRoot.resolve("billing"));
        assertThat(StoreEmbeddingIdentity.read(toRoot.resolve("billing"))).isPresent();
    }

    private static void assertRecordPreserved(CognitiveRecord actual, CognitiveRecord expected, int expectedVecBytes) {
        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.text()).isEqualTo(expected.text());
        assertThat(actual.memoryType()).isEqualTo(expected.memoryType());
        assertThat(actual.tags()).containsExactly(expected.tags());
        assertThat(actual.valence()).isEqualTo(expected.valence());
        assertThat(actual.arousal()).isEqualTo(expected.arousal());
        assertThat(actual.importance()).isCloseTo(expected.importance(), within(1e-5f));
        assertThat(actual.source()).isEqualTo(expected.source());
        assertThat(actual.timestampMs()).isEqualTo(expected.timestampMs());
        assertThat(actual.metadata()).isEqualTo(expected.metadata());
        if (actual.memoryType() != MemoryType.EPISODIC) {
            assertThat(actual.quantizedVector()).hasSize(expectedVecBytes);
        }
    }

    private static void assertRecalled(SpectorMemory memory, String query, String expectedId, RecallOptions options) {
        List<CognitiveResult> results = memory.recall(query, options);
        assertThat(results)
                .extracting(CognitiveResult::id)
                .contains(expectedId);
    }

    static EmbeddingProvider deterministicEmbedder(String modelName, int dimensions) {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                float[] vec = new float[dimensions];
                String[] tokens = text.toLowerCase().split("[^a-z0-9]+");
                for (String token : tokens) {
                    if (token.isEmpty()) continue;
                    int h = token.hashCode();
                    int idx = Math.floorMod(h, dimensions);
                    vec[idx] += 1.0f;
                    int idx2 = Math.floorMod(h * 31 + 7, dimensions);
                    vec[idx2] += 0.5f;
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

    private static Map<String, String> snapshotDirectoryChecksums(Path root) throws Exception {
        Map<String, String> map = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = root.relativize(p).toString();
                if (rel.equals(StoreDirectoryLock.LOCK_FILE)) {
                    continue;
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = Files.readAllBytes(p);
                map.put(rel, HexFormat.of().formatHex(md.digest(bytes)));
            }
        }
        return map;
    }
}
