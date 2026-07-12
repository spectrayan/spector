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
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.*;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Targeted diagnostic test for modality bit encoding through the full pipeline.
 */
@DisplayName("🔬 Diagnostic: Modality Encoding Pipeline")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModalityDiagnosticE2ETest extends AbstractE2ETest {

    private static final Logger log = LoggerFactory.getLogger(ModalityDiagnosticE2ETest.class);

    @Test
    @Order(1)
    @DisplayName("1. Flag encoding: withSourceModality round-trip")
    void flagEncoding_roundTrip() {
        for (SourceModality m : SourceModality.values()) {
            byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
            flags = SynapticHeaderConstants.withSourceModality(flags, m.ordinal());
            int extracted = SynapticHeaderConstants.sourceModalityOrdinal(flags);
            SourceModality decoded = SourceModality.fromOrdinal(extracted);

            log.info("  {} → flags=0x{} → ordinal={} → {}",
                    m, Integer.toHexString(flags & 0xFF), extracted, decoded);

            assertThat(decoded)
                    .as("Round-trip for %s (flags=0x%02X, ordinal=%d)", m, flags & 0xFF, extracted)
                    .isEqualTo(m);
        }
        log.info("✅ All 4 modalities encode/decode correctly in flags byte");
    }

    @Test
    @Order(2)
    @DisplayName("2. Ingest IMAGE via IngestionContext → recall → check modality")
    void ingestWithContext_imageModality() {
        IngestionContext ctx = IngestionContext.builder()
                .sourceModality(SourceModality.IMAGE)
                .sourceUri("file:///test/diagnostic.png")
                .metadata("test", "diagnostic")
                .build();

        log.info("IngestionContext: modality={}, uri={}, metadata={}",
                ctx.sourceModality(), ctx.sourceUri(), ctx.metadata());

        String id = memory.remember(
                "Diagnostic test: a bright red firetruck parked in front of a building.",
                MemoryType.EPISODIC, MemorySource.OBSERVED, ctx, "diagnostic").join();

        log.info("Ingested with id={}", id);

        // Recall with generous topK
        List<CognitiveResult> results = memory.recall("red firetruck diagnostic",
                RecallOptions.builder().topK(20).build());

        log.info("Recall returned {} results", results.size());
        for (CognitiveResult r : results) {
            log.info("  [{}] {} | modality={} | multimodal={} | text={}",
                    r.score(), r.id(), r.sourceModality(), r.isMultimodal(),
                    r.text().substring(0, Math.min(60, r.text().length())));
        }

        CognitiveResult target = results.stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElse(null);

        assertThat(target)
                .as("Diagnostic memory should be in results")
                .isNotNull();

        log.info("Target result: modality={}, multimodal={}, metadata={}",
                target.sourceModality(), target.isMultimodal(), target.metadata());

        assertThat(target.sourceModality())
                .as("Should be IMAGE, not TEXT")
                .isEqualTo(SourceModality.IMAGE);
    }

    @Test
    @Order(3)
    @DisplayName("3. Ingest TEXT (no context) → recall → check defaults to TEXT")
    void ingestWithoutContext_defaultsToText() {
        memory.remember("diag-text-01",
                "Diagnostic: plain text memory about software testing.",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "diagnostic").join();

        List<CognitiveResult> results = memory.recall("software testing diagnostic",
                RecallOptions.builder().topK(10).build());

        CognitiveResult target = results.stream()
                .filter(r -> r.id().equals("diag-text-01"))
                .findFirst()
                .orElse(null);

        assertThat(target).isNotNull();
        log.info("Text memory: modality={}, multimodal={}", target.sourceModality(), target.isMultimodal());

        assertThat(target.sourceModality())
                .as("Text-only should default to TEXT")
                .isEqualTo(SourceModality.TEXT);
    }

    @Test
    @Order(4)
    @DisplayName("4. Inspect index directly for modality metadata")
    void inspectIndex_metadataPresent() {
        IngestionContext ctx = IngestionContext.builder()
                .sourceModality(SourceModality.AUDIO)
                .metadata("key1", "val1")
                .build();

        String id = memory.remember(
                "Diagnostic audio: meeting recording about project deadlines.",
                MemoryType.EPISODIC, MemorySource.OBSERVED, ctx, "diagnostic").join();

        // Check index directly
        var admin = memory.admin();
        var index = admin.index();

        var metadata = index.metadata(id);
        log.info("Index metadata for '{}': {}", id, metadata);

        assertThat(metadata)
                .as("Index should store metadata map")
                .containsKey("key1");

        // Check flags via tier router
        var loc = index.locate(id);
        assertThat(loc).isNotNull();

        var tierRouter = admin.tierRouter();
        var layout = tierRouter.layoutFor(loc.type());
        var segment = tierRouter.segmentFor(loc.type());
        byte flags = layout.readFlags(segment, loc.offset());

        int modalityOrdinal = SynapticHeaderConstants.sourceModalityOrdinal(flags);
        SourceModality readModality = SourceModality.fromOrdinal(modalityOrdinal);
        log.info("Raw flags=0x{}, modality ordinal={}, decoded={}",
                Integer.toHexString(flags & 0xFF), modalityOrdinal, readModality);

        assertThat(readModality)
                .as("Flags should encode AUDIO modality")
                .isEqualTo(SourceModality.AUDIO);
    }
}
