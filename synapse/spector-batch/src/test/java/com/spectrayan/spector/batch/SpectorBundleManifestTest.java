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
package com.spectrayan.spector.batch;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpectorBundleManifest} and {@link SpectorBundleCodec}'s manifest-first read path,
 * checksum verification, and pre-write refusal guarantees (ADR-0045, memory-portability R3, R6, V6).
 */
@DisplayName("SpectorBundleManifest & Codec Pre-Write Validation")
class SpectorBundleManifestTest {

    private final SpectorBundleCodec codec = new SpectorBundleCodec();

    @Nested
    @DisplayName("Manifest Schema & Serialization")
    class ManifestSchemaTests {

        @Test
        @DisplayName("Serializing and deserializing SpectorBundleManifest preserves all fields")
        void roundTripSerializationPreservesAllFields() {
            SpectorBundleManifest.EmbeddingDescriptor embedding =
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536, "NONE");
            SpectorBundleManifest.BundleCounts counts =
                    new SpectorBundleManifest.BundleCounts(10_000L, 5_000L, 250L, 50L);
            Map<String, String> checksums = Map.of(
                    "nodes/chunk-00001.jsonl", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "vectors/chunk-00001.bin", "b3a1c2d3e4f5061728394a5b6c7d8e9f0123456789abcdef0123456789abcdef"
            );

            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "3.0.0",
                    "tenant_ns_42",
                    "0.1.0-alpha.2",
                    "2026-09-24T18:00:00Z",
                    embedding,
                    counts,
                    checksums
            );

            String json = manifest.toJson();
            assertThat(json).contains("\"schemaVersion\" : \"3.0.0\"");
            assertThat(json).contains("\"namespaceId\" : \"tenant_ns_42\"");
            assertThat(json).contains("\"model\" : \"text-embedding-3-small\"");
            assertThat(json).contains("\"dimensions\" : 1536");
            assertThat(json).contains("\"records\" : 10000");
            assertThat(json).contains("\"hyperedges\" : 250");
            assertThat(json).contains("\"facts\" : 50");

            SpectorBundleManifest deserialized = SpectorBundleManifest.fromJson(json);
            assertThat(deserialized.schemaVersion()).isEqualTo("3.0.0");
            assertThat(deserialized.namespaceId()).isEqualTo("tenant_ns_42");
            assertThat(deserialized.sourceBuildVersion()).isEqualTo("0.1.0-alpha.2");
            assertThat(deserialized.embedding().model()).isEqualTo("text-embedding-3-small");
            assertThat(deserialized.embedding().dimensions()).isEqualTo(1536);
            assertThat(deserialized.embedding().quantizer()).isEqualTo("NONE");
            assertThat(deserialized.counts().records()).isEqualTo(10_000L);
            assertThat(deserialized.counts().edges()).isEqualTo(5_000L);
            assertThat(deserialized.counts().hyperedges()).isEqualTo(250L);
            assertThat(deserialized.counts().facts()).isEqualTo(50L);
            assertThat(deserialized.checksums()).isEqualTo(checksums);
        }

        @Test
        @DisplayName("Compatibility check succeeds when model and dimensions match")
        void compatibilityCheckMatches() {
            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "test_ns",
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536),
                    new SpectorBundleManifest.BundleCounts(100L, 50L),
                    Map.of()
            );

            // Exactly matches
            manifest.validateCompatibility("text-embedding-3-small", 1536, false);
            // Case-insensitive model match
            manifest.validateCompatibility("TEXT-EMBEDDING-3-SMALL", 1536, false);
            // Unconstrained target
            manifest.validateCompatibility(null, 0, false);
        }

        @Test
        @DisplayName("Compatibility check throws on model mismatch without reembed")
        void compatibilityCheckModelMismatchThrows() {
            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "test_ns",
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536),
                    new SpectorBundleManifest.BundleCounts(100L, 50L),
                    Map.of()
            );

            assertThatThrownBy(() -> manifest.validateCompatibility("all-minilm-l6-v2", 1536, false))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("does not match target store model")
                    .hasMessageContaining("Use --reembed to migrate");

            // Succeeds when allowReembed is true
            manifest.validateCompatibility("all-minilm-l6-v2", 384, true);
        }

        @Test
        @DisplayName("Compatibility check throws on dimension mismatch without reembed")
        void compatibilityCheckDimensionMismatchThrows() {
            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "test_ns",
                    new SpectorBundleManifest.EmbeddingDescriptor("custom-model", 768),
                    new SpectorBundleManifest.BundleCounts(100L, 50L),
                    Map.of()
            );

            assertThatThrownBy(() -> manifest.validateCompatibility("custom-model", 1536, false))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Expected 1536 dimensions but received 768");
        }

        @Test
        @DisplayName("Compatibility check throws on unsupported schema version")
        void compatibilityCheckUnsupportedSchemaThrows() {
            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "1.0.0",
                    "test_ns",
                    "0.1.0-alpha.2",
                    "2026-09-24T18:00:00Z",
                    new SpectorBundleManifest.EmbeddingDescriptor("model", 128),
                    new SpectorBundleManifest.BundleCounts(0, 0),
                    Map.of()
            );

            assertThatThrownBy(() -> manifest.validateCompatibility(null, 0, false))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Incompatible bundle schema version '1.0.0'");
        }
    }

    @Nested
    @DisplayName("Manifest-First Read Path & Checksum Verification")
    class ManifestFirstAndChecksumTests {

        @Test
        @DisplayName("readManifest parses manifest directly from archive without extracting files")
        void readManifestWithoutExtracting(@TempDir Path tempDir) throws Exception {
            Path staging = tempDir.resolve("staging");
            Files.createDirectories(staging.resolve("nodes"));
            Files.createDirectories(staging.resolve("vectors"));

            SpectorBundleManifest expectedManifest = new SpectorBundleManifest(
                    "test_namespace",
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536),
                    new SpectorBundleManifest.BundleCounts(2L, 0L),
                    Map.of("nodes/chunk-00001.jsonl", "dummyhash")
            );

            Files.writeString(staging.resolve("manifest.json"), expectedManifest.toJson());
            Files.writeString(staging.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"m1\"}\n{\"id\":\"m2\"}\n");
            Files.write(staging.resolve("vectors").resolve("chunk-00001.bin"), new byte[1536 * 4 * 2]);

            Path bundle = tempDir.resolve("test.smb");
            codec.packageBundle(staging, bundle);

            // Read manifest directly
            SpectorBundleManifest read = codec.readManifest(bundle);
            assertThat(read.namespaceId()).isEqualTo("test_namespace");
            assertThat(read.embedding().model()).isEqualTo("text-embedding-3-small");
            assertThat(read.embedding().dimensions()).isEqualTo(1536);
            assertThat(read.counts().records()).isEqualTo(2L);
        }

        @Test
        @DisplayName("verifyChecksums verifies SHA-256 integrity and detects corrupted members")
        void verifyChecksumsDetectsTamperedMember(@TempDir Path tempDir) throws Exception {
            Path staging = tempDir.resolve("staging");
            Files.createDirectories(staging.resolve("nodes"));
            Path nodeFile = staging.resolve("nodes").resolve("chunk-00001.jsonl");
            Files.writeString(nodeFile, "{\"id\":\"m1\",\"text\":\"intact content\"}\n");

            Map<String, String> memberChecksums = SpectorBundleCodec.computeMemberChecksums(staging);
            assertThat(memberChecksums).containsKey("nodes/chunk-00001.jsonl");

            SpectorBundleManifest manifest = new SpectorBundleManifest(
                    "test_ns",
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536),
                    new SpectorBundleManifest.BundleCounts(1L, 0L),
                    memberChecksums
            );
            Files.writeString(staging.resolve("manifest.json"), manifest.toJson());

            Path bundle = tempDir.resolve("verified.smb");
            codec.packageBundle(staging, bundle);

            // Checksums match on intact bundle
            codec.verifyChecksums(bundle, manifest);

            // Now create a tampered manifest with incorrect hash for nodes
            SpectorBundleManifest tamperedManifest = new SpectorBundleManifest(
                    "test_ns",
                    new SpectorBundleManifest.EmbeddingDescriptor("text-embedding-3-small", 1536),
                    new SpectorBundleManifest.BundleCounts(1L, 0L),
                    Map.of("nodes/chunk-00001.jsonl", "0000000000000000000000000000000000000000000000000000000000000000")
            );

            assertThatThrownBy(() -> codec.verifyChecksums(bundle, tamperedManifest))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining(ErrorCode.RECORD_CRC_CORRUPTED.id())
                    .hasMessageContaining("nodes/chunk-00001.jsonl");
        }
    }

    @Nested
    @DisplayName("Pre-Write Refusal on Corrupt or Truncated Bundle (V6)")
    class PreWriteRefusalTests {

        @Test
        @DisplayName("Corrupt bundle is refused before creating target directory or writing files (V6)")
        void corruptBundleRefusedBeforeAnyWrite(@TempDir Path tempDir) throws Exception {
            Path corruptBundle = tempDir.resolve("corrupt.smb");
            // 64 bytes of random noise (invalid ZIP)
            Files.write(corruptBundle, new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0});

            Path targetDir = tempDir.resolve("target-staging-dir");

            // readManifest must refuse
            assertThatThrownBy(() -> codec.readManifest(corruptBundle))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining(ErrorCode.FILE_FORMAT_INVALID.id())
                    .hasMessageContaining("Corrupt or truncated");

            // unpackBundle must refuse before creating targetDir
            assertThatThrownBy(() -> codec.unpackBundle(corruptBundle, targetDir))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining(ErrorCode.FILE_FORMAT_INVALID.id())
                    .hasMessageContaining("Corrupt or truncated");

            // V6 Invariant: targetDir was NEVER created
            assertThat(Files.exists(targetDir))
                    .as("target directory must NOT be created when bundle is corrupt")
                    .isFalse();
        }

        @Test
        @DisplayName("Truncated bundle is refused before creating target directory or writing files (V6)")
        void truncatedBundleRefusedBeforeAnyWrite(@TempDir Path tempDir) throws Exception {
            // Build a valid bundle first
            Path staging = tempDir.resolve("staging");
            Files.createDirectories(staging.resolve("nodes"));
            Files.writeString(staging.resolve("manifest.json"), "{\"schemaVersion\":\"3.0.0\"}");
            Files.writeString(staging.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"a\"}\n");

            Path validBundle = tempDir.resolve("valid.smb");
            codec.packageBundle(staging, validBundle);

            // Truncate the bundle to half its bytes
            byte[] validBytes = Files.readAllBytes(validBundle);
            byte[] truncatedBytes = new byte[validBytes.length / 2];
            System.arraycopy(validBytes, 0, truncatedBytes, 0, truncatedBytes.length);

            Path truncatedBundle = tempDir.resolve("truncated.smb");
            Files.write(truncatedBundle, truncatedBytes);

            Path targetDir = tempDir.resolve("truncated-target");

            assertThatThrownBy(() -> codec.readManifest(truncatedBundle))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining(ErrorCode.FILE_FORMAT_INVALID.id());

            assertThatThrownBy(() -> codec.unpackBundle(truncatedBundle, targetDir))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining(ErrorCode.FILE_FORMAT_INVALID.id());

            // V6 Invariant: targetDir was NEVER created
            assertThat(Files.exists(targetDir))
                    .as("target directory must NOT be created when bundle is truncated")
                    .isFalse();
        }

        @Test
        @DisplayName("Bundle missing manifest.json is refused by readManifest")
        void missingManifestRefused(@TempDir Path tempDir) throws Exception {
            Path bundleWithoutManifest = tempDir.resolve("no-manifest.smb");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bundleWithoutManifest))) {
                zos.putNextEntry(new ZipEntry("nodes/chunk-00001.jsonl"));
                zos.write("{\"id\":\"m1\"}\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            assertThatThrownBy(() -> codec.readManifest(bundleWithoutManifest))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("missing manifest.json");
        }

        @Test
        @DisplayName("Bundle with corrupt manifest JSON is refused by readManifest")
        void malformedManifestJsonRefused(@TempDir Path tempDir) throws Exception {
            Path bundleWithBadJson = tempDir.resolve("bad-json.smb");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bundleWithBadJson))) {
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write("{not valid json...".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            assertThatThrownBy(() -> codec.readManifest(bundleWithBadJson))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Failed to parse SpectorBundleManifest from stream");
        }
    }
}
