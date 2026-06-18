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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MigrationPipelineTest {

    private Path tempDir;
    private MigrationPipeline pipeline;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("migration-test-");
        pipeline = new MigrationPipeline(List.of(
                new V1_0_to_V1_1_EncryptionMarker(),
                new V1_1_to_V2_0_AnalyticsAndSharding()));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
    }

    private Path createV1Namespace(String name) throws IOException {
        Path nsDir = tempDir.resolve(name);
        Files.createDirectories(nsDir);
        Files.createDirectories(nsDir.resolve("global"));
        Files.writeString(nsDir.resolve("namespace.json"),
                "{\"id\": \"" + name + "\"}");
        // No manifest.json = V1.0.0
        return nsDir;
    }

    @Test
    @DisplayName("No manifest means V1.0.0")
    void noManifestMeansV1() throws IOException {
        Path nsDir = createV1Namespace("old-ns");
        SchemaVersion v = pipeline.readSchemaVersion(nsDir);
        assertThat(v).isEqualTo(SchemaVersion.V1_0_0);
    }

    @Test
    @DisplayName("Reads version from existing manifest")
    void readsManifest() throws IOException {
        Path nsDir = createV1Namespace("versioned-ns");
        Files.writeString(nsDir.resolve(MigrationPipeline.MANIFEST_FILE),
                "{\"schemaVersion\": \"2.0.0\"}");

        SchemaVersion v = pipeline.readSchemaVersion(nsDir);
        assertThat(v).isEqualTo(SchemaVersion.V2_0_0);
    }

    @Test
    @DisplayName("Full migration chain: V1.0.0 -> V2.0.0")
    void fullMigration() throws IOException {
        Path nsDir = createV1Namespace("migrate-me");

        boolean migrated = pipeline.migrateIfNeeded(nsDir);

        assertThat(migrated).isTrue();

        // Verify V1.0 -> V1.1: encryption marker
        assertThat(Files.exists(nsDir.resolve(".encryption-ready"))).isTrue();

        // Verify V1.1 -> V2.0: analytics dir + shard marker
        assertThat(Files.exists(nsDir.resolve("analytics"))).isTrue();
        assertThat(Files.exists(nsDir.resolve(".shard-compatible"))).isTrue();

        // Verify manifest was written with V2.0.0
        SchemaVersion after = pipeline.readSchemaVersion(nsDir);
        assertThat(after).isEqualTo(SchemaVersion.V2_0_0);

        // Verify migration history
        String manifest = Files.readString(nsDir.resolve(MigrationPipeline.MANIFEST_FILE));
        assertThat(manifest).contains("\"from\": \"1.0.0\"");
        assertThat(manifest).contains("\"to\": \"1.1.0\"");
        assertThat(manifest).contains("\"to\": \"2.0.0\"");
    }

    @Test
    @DisplayName("Already at CURRENT: no migration")
    void alreadyCurrent() throws IOException {
        Path nsDir = createV1Namespace("current-ns");
        Files.writeString(nsDir.resolve(MigrationPipeline.MANIFEST_FILE),
                "{\"schemaVersion\": \"2.0.0\"}");

        boolean migrated = pipeline.migrateIfNeeded(nsDir);
        assertThat(migrated).isFalse();
    }

    @Test
    @DisplayName("Partial migration: V1.1.0 -> V2.0.0 (skip V1.0 step)")
    void partialMigration() throws IOException {
        Path nsDir = createV1Namespace("partial-ns");
        Files.writeString(nsDir.resolve(MigrationPipeline.MANIFEST_FILE),
                "{\"schemaVersion\": \"1.1.0\"}");

        boolean migrated = pipeline.migrateIfNeeded(nsDir);

        assertThat(migrated).isTrue();

        // Only V1.1->V2.0 should have run
        assertThat(Files.exists(nsDir.resolve("analytics"))).isTrue();
        assertThat(Files.exists(nsDir.resolve(".shard-compatible"))).isTrue();

        // Encryption marker should NOT exist (V1.0 step was skipped)
        assertThat(Files.exists(nsDir.resolve(".encryption-ready"))).isFalse();

        SchemaVersion after = pipeline.readSchemaVersion(nsDir);
        assertThat(after).isEqualTo(SchemaVersion.V2_0_0);
    }

    @Test
    @DisplayName("Idempotent: running twice is safe")
    void idempotent() throws IOException {
        Path nsDir = createV1Namespace("idempotent-ns");

        pipeline.migrateIfNeeded(nsDir);
        boolean second = pipeline.migrateIfNeeded(nsDir);

        assertThat(second).isFalse(); // no migration needed the second time
    }

    @Test
    @DisplayName("findMigrationChain returns correct chain")
    void findChain() {
        List<NamespaceMigration> chain =
                pipeline.findMigrationChain(SchemaVersion.V1_0_0, SchemaVersion.V2_0_0);

        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).fromVersion()).isEqualTo(SchemaVersion.V1_0_0);
        assertThat(chain.get(0).toVersion()).isEqualTo(SchemaVersion.V1_1_0);
        assertThat(chain.get(1).fromVersion()).isEqualTo(SchemaVersion.V1_1_0);
        assertThat(chain.get(1).toVersion()).isEqualTo(SchemaVersion.V2_0_0);
    }

    @Test
    @DisplayName("Migration count and target version")
    void metadata() {
        assertThat(pipeline.migrationCount()).isEqualTo(2);
        assertThat(pipeline.targetVersion()).isEqualTo(SchemaVersion.CURRENT);
    }
}
