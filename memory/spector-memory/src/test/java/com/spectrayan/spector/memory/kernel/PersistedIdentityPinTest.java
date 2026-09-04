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
package com.spectrayan.spector.memory.kernel;

import com.spectrayan.spector.memory.cortex.insula.InsularLayout;
import com.spectrayan.spector.memory.kernel.bundle.BundleLayout;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import com.spectrayan.spector.memory.kernel.layout.AuditRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout;
import com.spectrayan.spector.memory.kernel.layout.EntityLayout;
import com.spectrayan.spector.memory.kernel.layout.EpisodicLogLayout;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.memory.kernel.layout.IdBlobLayout;
import com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;
import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.memory.kernel.layout.TemporalLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.kernel.layout.WalRecordLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins every value that Spector writes to disk as a <em>numeric</em> identity.
 *
 * <h3>Why this test exists</h3>
 *
 * <p>The engram layout unification (spec {@code engram-layout-unification}) renames a large number of
 * kernel types — {@code MemoryHeader} to {@code RegionPreamble}, {@code CognitiveRecordLayout} to
 * {@code EngramLayout}, {@code AuditRecordLayout} to {@code StrengthLayout}, and so on. Those renames
 * are safe <b>only because</b> the on-disk format never references a Java class name: region identity
 * is a {@code short} from {@link RegionId#id()}, store and layout identity is an {@code int}
 * {@code layoutId}, and the file prologue is identified by a magic number.</p>
 *
 * <p>That invariant was previously undocumented and unasserted. Without it, a well-intentioned commit
 * tidying up magic numbers could re-spell {@code AuditRecordLayout.LAYOUT_ID} from {@code 'AUDT'} to
 * {@code 'STRN'} while renaming the class and silently invalidate every bundle on disk.</p>
 *
 * <h3>How to react when this test fails</h3>
 *
 * <p>A failure here is <b>not</b> a test that needs updating. It means a persisted constant changed,
 * which is a storage-format break. Either revert the constant, or treat it as a deliberate format
 * revision requiring a migration in {@code BundleMigrationCli} and a bump of the affected
 * {@code RegionEntry.schemaVersion}.</p>
 *
 * <p>Renaming a class while leaving its {@code LAYOUT_ID} intact is expected and will not fail here.
 * Only the numbers are pinned; the identifiers are free to change.</p>
 *
 * @see RegionId
 * @see MemoryHeader
 */
@DisplayName("Persisted identity pins (storage-format guardrail)")
class PersistedIdentityPinTest {

    @Nested
    @DisplayName("Region identity")
    class RegionIdentity {

        /**
         * Region ids are persisted by {@code RegionEntry.write} as
         * {@code (short) entry.regionId().id()} and restored via {@link RegionId#fromId(int)}.
         * Changing any number below orphans the corresponding region in every existing bundle.
         */
        @Test
        @DisplayName("Every RegionId numeric id is pinned")
        void regionIdNumbersArePinned() {
            Map<RegionId, Integer> pinned = new LinkedHashMap<>();
            // ── Partition bundle regions (id < 10) ──
            pinned.put(RegionId.SEMANTIC, 0);
            pinned.put(RegionId.EPISODIC, 1);
            pinned.put(RegionId.PROCEDURAL, 2);
            pinned.put(RegionId.TEXT, 3);
            pinned.put(RegionId.AUDIT, 4);
            // ── Runtime bundle regions (id >= 10) ──
            pinned.put(RegionId.WORKING, 10);
            pinned.put(RegionId.COACTIVATION, 11);
            pinned.put(RegionId.INDEX_MIDX, 12);
            pinned.put(RegionId.INDEX_IDPL, 13);
            pinned.put(RegionId.HEBBIAN, 14);
            pinned.put(RegionId.TEMPORAL_CHAIN, 15);
            pinned.put(RegionId.TEMPORAL_FACTS, 16);
            pinned.put(RegionId.ENTITY_DIRECTORY, 17);
            pinned.put(RegionId.ENTITY_NAMES, 18);
            pinned.put(RegionId.HYPERGRAPH, 19);
            pinned.put(RegionId.ENTITY_TYPES, 20);
            pinned.put(RegionId.RELATION_TYPES, 21);
            pinned.put(RegionId.BM25, 22);
            pinned.put(RegionId.CHECKPOINT, 23);
            pinned.put(RegionId.INSULA, 24);
            pinned.put(RegionId.CONTINUITY, 25);
            pinned.put(RegionId.PROVENANCE_LOG, 26);

            pinned.forEach((region, expectedId) ->
                    assertThat(region.id())
                            .as("RegionId.%s must keep persisted id %d", region.name(), expectedId)
                            .isEqualTo(expectedId));
        }

        /**
         * Guards against a new region being added without a pin above. If this fails, add the new
         * constant to {@link #regionIdNumbersArePinned()} rather than bumping the number here.
         */
        @Test
        @DisplayName("No RegionId constant is unpinned")
        void regionIdSetIsComplete() {
            assertThat(RegionId.values())
                    .as("a new RegionId was added — pin its numeric id in regionIdNumbersArePinned()")
                    .hasSize(22);
        }

        @Test
        @DisplayName("RegionId.fromId round-trips every constant")
        void fromIdRoundTrips() {
            for (RegionId region : RegionId.values()) {
                assertThat(RegionId.fromId(region.id()))
                        .as("RegionId.fromId(%d) must resolve back to %s", region.id(), region.name())
                        .isSameAs(region);
            }
        }

        /**
         * The partition/runtime split is derived from the numeric id, not from an explicit flag, so
         * the boundary at 10 is itself a persisted contract.
         */
        @Test
        @DisplayName("Partition/runtime boundary stays at id 10")
        void partitionRuntimeBoundaryIsPinned() {
            for (RegionId region : RegionId.values()) {
                if (region.id() < 10) {
                    assertThat(region.isPartitionRegion())
                            .as("%s (id %d) must be a partition region", region.name(), region.id())
                            .isTrue();
                    assertThat(region.isRuntimeRegion()).isFalse();
                } else {
                    assertThat(region.isRuntimeRegion())
                            .as("%s (id %d) must be a runtime region", region.name(), region.id())
                            .isTrue();
                    assertThat(region.isPartitionRegion()).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("Layout identity")
    class LayoutIdentity {

        /**
         * {@code layoutId} is persisted into {@code RegionEntry.layoutId} and into the
         * {@link MemoryHeader} prologue. The four-character mnemonics are decoded for human display by
         * {@code SpectorInspectCli.decodeLayoutId}, which is why a rename may legitimately leave the
         * mnemonic looking stale relative to the class name.
         */
        @Test
        @DisplayName("Every layoutId is pinned")
        void layoutIdsArePinned() {
            Map<String, String> actual = new LinkedHashMap<>();
            actual.put("CognitiveRecordLayout", hex(new CognitiveRecordLayout(100).layoutId()));
            actual.put("AuditRecordLayout", hex(AuditRecordLayout.INSTANCE.layoutId()));
            actual.put("EpisodicLogLayout", hex(EpisodicLogLayout.INSTANCE.layoutId()));
            actual.put("ContinuityLayout", hex(ContinuityLayout.SINGLETON.layoutId()));
            actual.put("InsularLayout", hex(InsularLayout.SINGLETON.layoutId()));
            actual.put("BundleLayout", hex(BundleLayout.LAYOUT_ID));
            actual.put("TextBlobLayout", hex(new TextBlobLayout().layoutId()));
            actual.put("IdBlobLayout", hex(new IdBlobLayout().layoutId()));
            actual.put("IndexEntryLayout", hex(new IndexEntryLayout().layoutId()));
            actual.put("CoActivationLayout", hex(new CoActivationLayout().layoutId()));
            actual.put("HebbianLayout", hex(new HebbianLayout().layoutId()));
            actual.put("EntityLayout", hex(new EntityLayout().layoutId()));
            actual.put("EntityDirectoryLayout", hex(new EntityDirectoryLayout().layoutId()));
            actual.put("HyperEntityLayout", hex(new HyperEntityLayout().layoutId()));
            actual.put("TemporalLayout", hex(new TemporalLayout().layoutId()));
            actual.put("TemporalFactLayout", hex(new TemporalFactLayout().layoutId()));
            actual.put("RegistryLayout", hex(new RegistryLayout().layoutId()));
            actual.put("WalRecordLayout", hex(new WalRecordLayout().layoutId()));

            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("CognitiveRecordLayout", "0x434F4700 'COG\\0'");
            expected.put("AuditRecordLayout", "0x41554454 'AUDT'");
            expected.put("EpisodicLogLayout", "0x4550494C 'EPIL'");
            expected.put("ContinuityLayout", "0x434F4E54 'CONT'");
            expected.put("InsularLayout", "0x494E534C 'INSL'");
            expected.put("BundleLayout", "0x42554E44 'BUND'");
            expected.put("TextBlobLayout", "0x54585442 'TXTB'");
            expected.put("IdBlobLayout", "0x4944504C 'IDPL'");
            expected.put("IndexEntryLayout", "0x4D494458 'MIDX'");
            expected.put("CoActivationLayout", "0x434F4158 'COAX'");
            expected.put("HebbianLayout", "0x48435352 'HCSR'");
            expected.put("EntityLayout", "0x45474D4D 'EGMM'");
            expected.put("EntityDirectoryLayout", "0x45444952 'EDIR'");
            expected.put("HyperEntityLayout", "0x48594547 'HYEG'");
            expected.put("TemporalLayout", "0x54504348 'TPCH'");
            expected.put("TemporalFactLayout", "0x54464354 'TFCT'");
            expected.put("RegistryLayout", "0x52454700 'REG\\0'");
            expected.put("WalRecordLayout", "0x57414C47 'WALG'");

            assertThat(actual)
                    .as("a layoutId changed — this is a storage-format break, not a stale test. "
                            + "Revert the constant, or treat it as a deliberate format revision needing "
                            + "a BundleMigrationCli migration and a schemaVersion bump.")
                    .containsExactlyInAnyOrderEntriesOf(expected);
        }

        /**
         * Renders a layout id as hex plus its four-character ASCII mnemonic, so a failure reads
         * {@code 0x41554454 'AUDT'} rather than {@code 1398035024}. The mnemonic is the form used by
         * {@code SpectorInspectCli} and by every layout's source comment.
         */
        private static String hex(int layoutId) {
            StringBuilder mnemonic = new StringBuilder(6).append('\'');
            for (int shift = 24; shift >= 0; shift -= 8) {
                int b = (layoutId >>> shift) & 0xFF;
                mnemonic.append(b == 0 ? "\\0" : String.valueOf((char) b));
            }
            return "0x%08X %s'".formatted(layoutId, mnemonic);
        }

        /**
         * Layout ids must be unique: {@code SpectorInspectCli} and the migration tooling resolve a
         * region's decoder from this number alone.
         */
        @Test
        @DisplayName("Layout ids are mutually unique")
        void layoutIdsAreUnique() {
            assertThat(new int[]{
                    0x434F4700, 0x41554454, 0x4550494C, 0x434F4E54, 0x494E534C, 0x42554E44,
                    0x54585442, 0x4944504C, 0x4D494458, 0x434F4158, 0x48435352, 0x45474D4D,
                    0x45444952, 0x48594547, 0x54504348, 0x54464354, 0x52454700, 0x57414C47
            }).doesNotHaveDuplicates();
        }

        /**
         * Record stride is persisted in both {@code RegionEntry.stride} and
         * {@link MemoryHeader}. A stride change relocates every record in an existing region, so it is
         * a format break even when the layout id is unchanged.
         *
         * <p>Requirement R9.2 of the spec forbids growing the engram stride without an explicit
         * decision; this test is where that decision becomes visible.</p>
         */
        @Test
        @DisplayName("Fixed record strides are pinned")
        void recordStridesArePinned() {
            assertThat(AuditRecordLayout.INSTANCE.recordStride())
                    .as("strength/audit record stride").isEqualTo(96);
            assertThat(ContinuityLayout.SINGLETON.recordStride())
                    .as("continuity record stride").isEqualTo(32);
            assertThat(new IndexEntryLayout().recordStride())
                    .as("MIDX entry stride").isEqualTo(48);
            assertThat(new TemporalLayout().recordStride())
                    .as("temporal chain stride").isEqualTo(16);
            assertThat(new TemporalFactLayout().recordStride())
                    .as("temporal fact stride").isEqualTo(64);
            assertThat(new HyperEntityLayout().recordStride())
                    .as("hyperedge stride").isEqualTo(32);
            assertThat(new EntityLayout().recordStride())
                    .as("entity node stride").isEqualTo(64);
            assertThat(new EntityDirectoryLayout().recordStride())
                    .as("entity directory node stride").isEqualTo(64);
            assertThat(new HebbianLayout().recordStride())
                    .as("hebbian edge stride").isEqualTo(12);
            assertThat(BundleLayout.REGION_ENTRY_STRIDE)
                    .as("bundle directory entry stride").isEqualTo(64);

            // Variable-length regions advertise stride 0 and must keep doing so: a non-zero value
            // would make the bundle directory claim a fixed stride the data does not have.
            assertThat(new TextBlobLayout().recordStride()).as("text blobs are variable-length").isZero();
            assertThat(new IdBlobLayout().recordStride()).as("id blobs are variable-length").isZero();
            assertThat(EpisodicLogLayout.INSTANCE.recordStride())
                    .as("episode records are variable-length").isZero();
        }

        /**
         * The engram stride is {@code encodingHeaderBytes + quantizedVecBytes}. Pinning the formula
         * (rather than a single number) keeps the 64-byte header contract explicit while allowing the
         * embedding dimension to vary per deployment.
         */
        @Test
        @DisplayName("Engram stride is 64-byte header plus quantized vector")
        void engramStrideFormulaIsPinned() {
            assertThat(new CognitiveRecordLayout(100).stride())
                    .as("64B encoding header + 100B quantized vector").isEqualTo(164);
            assertThat(new CognitiveRecordLayout(768).stride()).isEqualTo(64 + 768);
            assertThat(new CognitiveRecordLayout(100).vectorOffset(0))
                    .as("vector payload starts immediately after the 64-byte header").isEqualTo(64);
        }
    }

    @Nested
    @DisplayName("File prologue identity")
    class ProloguePins {

        /**
         * {@code 'SMKM'} identifies every Spector Memory Kernel region prologue. This is the constant
         * that survives the {@code MemoryHeader} to {@code RegionPreamble} rename unchanged.
         */
        @Test
        @DisplayName("SMKM magic and 64-byte prologue size are pinned")
        void prologueIsPinned() {
            assertThat(MemoryHeader.MAGIC)
                    .as("'SMKM' region prologue magic").isEqualTo(0x534D4B4D);
            assertThat(MemoryHeader.HEADER_BYTES)
                    .as("region prologue occupies exactly one cache line").isEqualTo(64);
        }
    }
}
