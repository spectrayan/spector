/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opens bundle files frozen <em>before</em> the engram layout unification renames and asserts they
 * still read correctly.
 *
 * <h3>Why a committed binary fixture</h3>
 *
 * <p>{@code PersistedIdentityPinTest} pins the numeric constants, but both sides of a round-trip test
 * change together — if the writer and the reader are edited in the same commit, a format break stays
 * invisible. Only bytes produced by an <em>older</em> build can catch that. These fixtures are those
 * bytes.</p>
 *
 * <p>Committed gzipped: bundle regions are pre-sized and mostly zero, so they compress by orders of
 * magnitude. The fixture is deliberately built with tiny capacities — it exists to exercise the
 * <b>directory encoding and region identity</b>, not realistic data volume.</p>
 *
 * <h3>Regenerating</h3>
 *
 * <p>{@code @EnabledIfSystemProperty} rather than {@code @Disabled}: JUnit honours {@code @Disabled}
 * even when a test is selected explicitly, so a disabled generator could never be run.</p>
 *
 * <p>Do <b>not</b> regenerate to make a failure go away — that destroys the only pre-rename reference
 * and defeats the test. Regenerate only when the format changes deliberately, alongside a
 * {@code BundleMigrationCli} migration and a {@code schemaVersion} bump:</p>
 *
 * <pre>{@code
 * mvn test -pl memory/spector-memory -Dtest=BundleFormatCompatibilityTest#regenerateFixtures \
 *     -Dspector.fixture.regenerate=true
 * }</pre>
 *
 * @see com.spectrayan.spector.memory.kernel.PersistedIdentityPinTest
 * @see <a href="https://github.com/spectrayan/spector/issues/732">#732</a>
 */
@DisplayName("Bundle format compatibility (pre-rename fixtures)")
class BundleFormatCompatibilityTest {

    private static final String FIXTURE_DIR = "bundle-compat/v4-pre-rename";
    private static final String PARTITION_GZ = FIXTURE_DIR + "/partition.bundle.gz";
    private static final String RUNTIME_GZ = FIXTURE_DIR + "/runtime.bundle.gz";

    // ── Fixture parameters. Must match what regenerateFixtures() used, or the assertions below
    //    describe a different bundle than the one on disk. ──
    private static final int SEM_CAP = 4;
    private static final int PROC_CAP = 4;
    private static final long EPISODIC_BYTES = 4096;
    private static final long TEXT_BYTES = 4096;
    private static final int DIMS = 8;
    /** Sentinel record counts stamped into each region's prologue, so we can prove we read real bytes. */
    private static final long SEMANTIC_COUNT = 3;
    private static final long PROCEDURAL_COUNT = 2;

    private static final CognitiveRecordLayout COG = new CognitiveRecordLayout(DIMS);
    private static final TextBlobLayout TEXT = new TextBlobLayout();

    /** Region set of the frozen runtime fixture, mirroring {@link #runtimeSpecs()}. */
    private static final List<RegionId> RUNTIME_REGIONS =
            List.of(RegionId.WORKING, RegionId.CONTINUITY, RegionId.BM25);

    private static List<RegionSizeSpec> runtimeSpecs() {
        return List.of(
                new RegionSizeSpec(RegionId.WORKING,
                        RegionPreamble.PREAMBLE_BYTES + (long) SEM_CAP * COG.recordStride(),
                        SEM_CAP, COG.recordStride(), COG.layoutId(), COG.schemaVersion(), false),
                new RegionSizeSpec(RegionId.CONTINUITY,
                        ContinuityLayout.DATA_START + (long) 8 * ContinuityLayout.RECORD_STRIDE,
                        8, ContinuityLayout.RECORD_STRIDE,
                        ContinuityLayout.LAYOUT_ID, ContinuityLayout.SCHEMA_VERSION, false),
                // Growable, so the fixture also pins how FLAG_GROWABLE is encoded in the directory.
                new RegionSizeSpec(RegionId.BM25, 4096, 1, 0, 0x42494458, 1, true));
    }

    // ── The actual guardrail ──

    @Test
    @DisplayName("A pre-rename partition bundle still opens with all regions intact")
    void preRenamePartitionBundleOpens() throws IOException {
        Path bundle = materialise(PARTITION_GZ, "partition.bundle");

        try (PartitionBundle reopened = PartitionBundle.Init.open(bundle)) {
            assertThat(reopened.isNew())
                    .as("fixture must be recognised as an existing bundle, not initialised fresh").isFalse();
            assertThat(reopened.directory().bundleMagic())
                    .as("partition bundle magic").isEqualTo(BundleSubHeader.MAGIC_PARTITION);
            assertThat(reopened.directory().liveRegionCount())
                    .as("SEMANTIC, EPISODIC, PROCEDURAL, TEXT, AUDIT").isEqualTo(5);

            for (RegionId region : List.of(RegionId.SEMANTIC, RegionId.EPISODIC,
                    RegionId.PROCEDURAL, RegionId.TEXT, RegionId.STRENGTH)) {
                assertThat(reopened.hasRegion(region))
                        .as("region %s (id %d) must be present", region.name(), region.id()).isTrue();
                assertThat(reopened.regionSegment(region))
                        .as("region %s must be mappable", region.name()).isNotNull();
            }

            // Prove we are reading real persisted bytes, not a freshly initialised bundle.
            MemorySegment sem = reopened.regionSegment(RegionId.SEMANTIC);
            assertThat(RegionPreamble.isValid(sem, 0))
                    .as("SEMANTIC prologue must pass magic + CRC").isTrue();
            assertThat(RegionPreamble.readCount(sem, 0))
                    .as("record count stamped when the fixture was frozen").isEqualTo(SEMANTIC_COUNT);
            assertThat(RegionPreamble.readLayoutId(sem, 0))
                    .as("SEMANTIC layoutId 'COG\\0'").isEqualTo(COG.layoutId());
            assertThat(RegionPreamble.readRecordStride(sem, 0))
                    .as("64B header + 8B quantized vector").isEqualTo(COG.recordStride());

            MemorySegment proc = reopened.regionSegment(RegionId.PROCEDURAL);
            assertThat(RegionPreamble.readCount(proc, 0)).isEqualTo(PROCEDURAL_COUNT);

            // Directory entries must still describe non-overlapping, in-bounds regions.
            RegionEntry audit = reopened.directory().findRegion(RegionId.STRENGTH);
            assertThat(audit).as("AUDIT entry must survive").isNotNull();
            assertThat(audit.stride())
                    .as("audit stride must still be 96B").isEqualTo(StrengthLayout.INSTANCE.recordStride());
            assertThat(audit.layoutId())
                    .as("audit layoutId 'AUDT' must not be re-spelled")
                    .isEqualTo(StrengthLayout.INSTANCE.layoutId());
        } finally {
            Files.deleteIfExists(bundle);
        }
    }

    @Test
    @DisplayName("A pre-rename runtime bundle still opens with region flags intact")
    void preRenameRuntimeBundleOpens() throws IOException {
        Path bundle = materialise(RUNTIME_GZ, "runtime.bundle");

        try (RuntimeBundle reopened = RuntimeBundle.Init.open(bundle)) {
            assertThat(reopened.isNew()).isFalse();
            assertThat(reopened.directory().bundleMagic())
                    .as("runtime bundle magic").isEqualTo(BundleSubHeader.MAGIC_RUNTIME);
            assertThat(reopened.directory().liveRegionCount()).isEqualTo(RUNTIME_REGIONS.size());

            for (RegionId region : RUNTIME_REGIONS) {
                assertThat(reopened.hasRegion(region))
                        .as("runtime region %s (id %d)", region.name(), region.id()).isTrue();
            }

            // FLAG_GROWABLE encoding is part of the on-disk directory format.
            RegionEntry bm25 = reopened.directory().findRegion(RegionId.BM25);
            assertThat(bm25).isNotNull();
            assertThat(bm25.isGrowable())
                    .as("BM25 was frozen as growable; FLAG_GROWABLE must decode the same way").isTrue();
            assertThat(reopened.directory().findRegion(RegionId.CONTINUITY).isGrowable())
                    .as("CONTINUITY was frozen as non-growable").isFalse();
        } finally {
            Files.deleteIfExists(bundle);
        }
    }

    // ── Fixture plumbing ──

    /** Decompresses a fixture into a temp file, since the bundle APIs mmap a real path. */
    private static Path materialise(String resource, String fileName) throws IOException {
        Path target = Files.createTempDirectory("bundle-compat").resolve(fileName);
        try (InputStream raw = BundleFormatCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(raw).as("fixture resource %s must be on the test classpath", resource).isNotNull();
            try (GZIPInputStream gz = new GZIPInputStream(raw)) {
                Files.copy(gz, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    /**
     * Writes the fixtures into {@code src/test/resources}. Guarded by a system property so a normal
     * test run can never silently overwrite the pre-rename reference bytes.
     */
    @Test
    @EnabledIfSystemProperty(named = "spector.fixture.regenerate", matches = "true",
            disabledReason = "Generator — opt in with -Dspector.fixture.regenerate=true. See class javadoc.")
    @DisplayName("Regenerate the frozen bundle fixtures")
    void regenerateFixtures() throws IOException {
        Path resources = Paths.get("src", "test", "resources", FIXTURE_DIR);
        Files.createDirectories(resources);
        Path work = Files.createTempDirectory("bundle-fixture-gen");

        Path partition = work.resolve("partition.bundle");
        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                partition, SEM_CAP, EPISODIC_BYTES, PROC_CAP, TEXT_BYTES, DIMS,
                COG.layoutId(), COG.schemaVersion(), TEXT.layoutId(), TEXT.schemaVersion())) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(bundle.regionSegment(RegionId.SEMANTIC), 0, 1, MemoryShape.RECORD, 1,
                    SEM_CAP, SEMANTIC_COUNT, COG.recordStride(), COG.layoutId(), now, now);
            RegionPreamble.write(bundle.regionSegment(RegionId.PROCEDURAL), 0, 1, MemoryShape.RECORD, 1,
                    PROC_CAP, PROCEDURAL_COUNT, COG.recordStride(), COG.layoutId(), now, now);
        }

        Path runtime = work.resolve("runtime.bundle");
        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(runtime, runtimeSpecs())) {
            assertThat(bundle.isNew()).isTrue();
        }

        gzip(partition, resources.resolve("partition.bundle.gz"));
        gzip(runtime, resources.resolve("runtime.bundle.gz"));
    }

    private static void gzip(Path source, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             GZIPOutputStream gz = new GZIPOutputStream(out)) {
            Files.copy(source, gz);
        }
        System.out.printf("fixture %s: %d bytes raw -> %d bytes gzipped%n",
                target.getFileName(), Files.size(source), Files.size(target));
    }
}
