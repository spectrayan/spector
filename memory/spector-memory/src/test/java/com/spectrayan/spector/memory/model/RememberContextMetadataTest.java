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
package com.spectrayan.spector.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RememberContext} metadata map support.
 */
@DisplayName("RememberContext Metadata")
class RememberContextMetadataTest {

    // ══════════════════════════════════════════════════════════════
    // BASIC METADATA MAP
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Metadata Map Basics")
    class MetadataBasics {

        @Test
        @DisplayName("EMPTY context has no metadata")
        void emptyContextHasNoMetadata() {
            assertFalse(RememberContext.EMPTY.hasMetadata());
            assertTrue(RememberContext.EMPTY.metadata().isEmpty());
        }

        @Test
        @DisplayName("Builder creates context with metadata map")
        void builderWithMetadata() {
            var ctx = RememberContext.builder()
                    .metadata(Map.of("key1", "val1", "key2", "val2"))
                    .build();

            assertTrue(ctx.hasMetadata());
            assertEquals("val1", ctx.metadata().get("key1"));
            assertEquals("val2", ctx.metadata().get("key2"));
            assertEquals(2, ctx.metadata().size());
        }

        @Test
        @DisplayName("Builder adds individual metadata entries")
        void builderWithIndividualEntries() {
            var ctx = RememberContext.builder()
                    .metadata("key1", "val1")
                    .metadata("key2", "val2")
                    .build();

            assertTrue(ctx.hasMetadata());
            assertEquals("val1", ctx.metadata().get("key1"));
            assertEquals("val2", ctx.metadata().get("key2"));
        }

        @Test
        @DisplayName("Metadata map is unmodifiable after construction")
        void metadataIsUnmodifiable() {
            var ctx = RememberContext.builder()
                    .metadata(Map.of("key1", "val1"))
                    .build();

            assertThrows(UnsupportedOperationException.class,
                    () -> ctx.metadata().put("key2", "val2"),
                    "Metadata should be unmodifiable");
        }

        @Test
        @DisplayName("Metadata map is defensive copy — mutations to source don't affect context")
        void metadataIsDefensiveCopy() {
            Map<String, String> source = new HashMap<>();
            source.put("key1", "val1");

            var ctx = RememberContext.builder()
                    .metadata(source)
                    .build();

            source.put("key2", "injected");
            assertFalse(ctx.metadata().containsKey("key2"),
                    "Mutating the source map should not affect the context");
        }

        @Test
        @DisplayName("Null metadata key is ignored in builder")
        void nullKeyIgnored() {
            var ctx = RememberContext.builder()
                    .metadata(null, "val")
                    .build();

            assertFalse(ctx.hasMetadata());
        }

        @Test
        @DisplayName("Null metadata map results in empty metadata")
        void nullMetadataMapIsEmpty() {
            var ctx = RememberContext.builder()
                    .metadata((Map<String, String>) null)
                    .build();

            assertFalse(ctx.hasMetadata());
            assertTrue(ctx.metadata().isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MODALITY CONVENIENCE METHODS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Modality Convenience")
    class ModalityConvenience {

        @Test
        @DisplayName("sourceModality() extracts modality from metadata")
        void sourceModalityFromMetadata() {
            var ctx = RememberContext.builder()
                    .sourceModality(SourceModality.IMAGE)
                    .build();

            assertEquals(SourceModality.IMAGE, ctx.sourceModality());
            assertTrue(ctx.hasMetadata());
            assertEquals("IMAGE", ctx.metadata().get(SourceModality.METADATA_KEY));
        }

        @Test
        @DisplayName("sourceUri() extracts URI from metadata")
        void sourceUriFromMetadata() {
            var ctx = RememberContext.builder()
                    .sourceUri("file:///photos/cat.jpg")
                    .build();

            assertEquals("file:///photos/cat.jpg", ctx.sourceUri());
            assertEquals("file:///photos/cat.jpg",
                    ctx.metadata().get(SourceModality.URI_KEY));
        }

        @Test
        @DisplayName("Combined modality + URI in builder")
        void combinedModalityAndUri() {
            var ctx = RememberContext.builder()
                    .sourceModality(SourceModality.IMAGE)
                    .sourceUri("file:///photos/cat.jpg")
                    .metadata("photographer", "Jane")
                    .build();

            assertEquals(SourceModality.IMAGE, ctx.sourceModality());
            assertEquals("file:///photos/cat.jpg", ctx.sourceUri());
            assertEquals("Jane", ctx.metadata().get("photographer"));
            assertEquals(3, ctx.metadata().size());
        }

        @Test
        @DisplayName("sourceModality() returns null when not in metadata")
        void sourceModalityReturnsNullWhenAbsent() {
            var ctx = RememberContext.builder()
                    .metadata("key", "val")
                    .build();

            assertNull(ctx.sourceModality());
        }

        @Test
        @DisplayName("sourceUri() returns null when not in metadata")
        void sourceUriReturnsNullWhenAbsent() {
            assertNull(RememberContext.EMPTY.sourceUri());
        }

        @Test
        @DisplayName("Null modality is ignored in builder")
        void nullModalityIgnored() {
            var ctx = RememberContext.builder()
                    .sourceModality(null)
                    .build();

            assertFalse(ctx.hasMetadata());
        }

        @Test
        @DisplayName("Blank URI is ignored in builder")
        void blankUriIgnored() {
            var ctx = RememberContext.builder()
                    .sourceUri("")
                    .build();

            assertFalse(ctx.hasMetadata());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BACKWARD COMPATIBILITY
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompat {

        @Test
        @DisplayName("4-arg constructor produces empty metadata")
        void fourArgConstructor() {
            var ctx = new RememberContext(null, null, null, null);
            assertFalse(ctx.hasMetadata());
            assertTrue(ctx.metadata().isEmpty());
        }

        @Test
        @DisplayName("5-arg constructor produces empty metadata")
        void fiveArgConstructor() {
            var ctx = new RememberContext(null, null, null, null, 12345L);
            assertFalse(ctx.hasMetadata());
            assertEquals(12345L, ctx.overrideTimestampMs());
        }

        @Test
        @DisplayName("Builder without metadata produces empty metadata")
        void builderWithoutMetadata() {
            var ctx = RememberContext.builder().build();
            assertFalse(ctx.hasMetadata());
            assertTrue(ctx.metadata().isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty string values are stored")
        void emptyStringValues() {
            var ctx = RememberContext.builder()
                    .metadata("key", "")
                    .build();

            assertTrue(ctx.hasMetadata());
            assertEquals("", ctx.metadata().get("key"));
        }

        @Test
        @DisplayName("Large metadata map is preserved")
        void largeMetadataMap() {
            Map<String, String> large = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                large.put("key" + i, "value" + i);
            }
            var ctx = RememberContext.builder().metadata(large).build();
            assertEquals(100, ctx.metadata().size());
        }

        @Test
        @DisplayName("Metadata coexists with other context fields")
        void metadataWithOtherFields() {
            var ctx = RememberContext.builder()
                    .overrideTimestampMs(99999L)
                    .sourceModality(SourceModality.AUDIO)
                    .build();

            assertTrue(ctx.hasTimestampOverride());
            assertEquals(99999L, ctx.overrideTimestampMs());
            assertEquals(SourceModality.AUDIO, ctx.sourceModality());
        }

        @Test
        @DisplayName("sourceModality fromName handles unknown modality strings")
        void unknownModalityString() {
            var ctx = RememberContext.builder()
                    .metadata(SourceModality.METADATA_KEY, "HOLOGRAM")
                    .build();

            // Should default to TEXT for unknown modality names
            assertEquals(SourceModality.TEXT, ctx.sourceModality());
        }
    }
}
