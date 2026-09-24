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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Portable manifest record describing a Spector Memory Bundle (.smb archive) layout,
 * embedding model provenance, entity counts, and per-member SHA-256 integrity checksums
 * (ADR-0045, memory-portability R3.1, R3.2).
 *
 * <p>The manifest is the authoritative descriptor of bundle contents. Dimensions and
 * embedding identities are recorded here rather than baked into file naming conventions.</p>
 */
public record SpectorBundleManifest(
        String schemaVersion,
        String namespaceId,
        String sourceBuildVersion,
        String exportTimestamp,
        EmbeddingDescriptor embedding,
        BundleCounts counts,
        Map<String, String> checksums
) {

    public static final String CURRENT_SCHEMA_VERSION = "3.0.0";
    public static final String DEFAULT_BUILD_VERSION = "0.1.0-alpha.2";

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public SpectorBundleManifest {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (namespaceId == null || namespaceId.isBlank()) {
            namespaceId = "default";
        }
        if (sourceBuildVersion == null || sourceBuildVersion.isBlank()) {
            sourceBuildVersion = DEFAULT_BUILD_VERSION;
        }
        if (exportTimestamp == null || exportTimestamp.isBlank()) {
            exportTimestamp = Instant.now().toString();
        }
        if (embedding == null) {
            embedding = new EmbeddingDescriptor("unspecified", 0, "NONE");
        }
        if (counts == null) {
            counts = new BundleCounts(0L, 0L, 0L, 0L);
        }
        if (checksums == null) {
            checksums = Map.of();
        } else {
            checksums = Collections.unmodifiableMap(new TreeMap<>(checksums));
        }
    }

    /**
     * Primary convenience constructor for new bundle creation.
     */
    public SpectorBundleManifest(
            String namespaceId,
            EmbeddingDescriptor embedding,
            BundleCounts counts,
            Map<String, String> checksums
    ) {
        this(CURRENT_SCHEMA_VERSION, namespaceId, DEFAULT_BUILD_VERSION, Instant.now().toString(), embedding, counts, checksums);
    }

    /**
     * Describes the embedding model, dimensionality, and quantization used for vectors in the bundle.
     */
    public record EmbeddingDescriptor(
            String model,
            int dimensions,
            String quantizer
    ) {
        public EmbeddingDescriptor {
            if (model == null || model.isBlank()) {
                model = "unspecified";
            }
            if (quantizer == null || quantizer.isBlank()) {
                quantizer = "NONE";
            }
        }

        public EmbeddingDescriptor(String model, int dimensions) {
            this(model, dimensions, "NONE");
        }
    }

    /**
     * Record and edge counts within the bundle.
     */
    public record BundleCounts(
            long records,
            long edges,
            long hyperedges,
            long facts
    ) {
        public BundleCounts {
            if (records < 0) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "records", records);
            }
            if (edges < 0) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "edges", edges);
            }
            if (hyperedges < 0) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "hyperedges", hyperedges);
            }
            if (facts < 0) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "facts", facts);
            }
        }

        public BundleCounts(long records, long edges) {
            this(records, edges, 0L, 0L);
        }

        public BundleCounts(long records, long edges, long hyperedges) {
            this(records, edges, hyperedges, 0L);
        }
    }

    /**
     * Verifies compatibility with the target memory store before unpacking or writing (R3.3, V6).
     *
     * @param targetModel target namespace embedding model, or null if unconstrained
     * @param targetDimensions target namespace vector dimensions, or 0 if unconstrained
     * @param allowReembed true if re-embedding is allowed to reconcile model mismatches
     * @throws SpectorValidationException if the bundle is incompatible
     */
    public void validateCompatibility(String targetModel, int targetDimensions, boolean allowReembed) {
        if (schemaVersion != null && !schemaVersion.startsWith("3.") && !schemaVersion.startsWith("2.")) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Incompatible bundle schema version '" + schemaVersion + "'. Supported versions: 3.x"
            );
        }

        if (targetModel != null && !targetModel.isBlank()) {
            if (embedding == null || embedding.model() == null || "unspecified".equalsIgnoreCase(embedding.model())) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID,
                        "embedding.model",
                        "Bundle manifest does not specify embedding model; target namespace requires: " + targetModel
                );
            }
            if (!allowReembed && !targetModel.equalsIgnoreCase(embedding.model())) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID,
                        "embedding.model",
                        "Bundle embedding model '" + embedding.model()
                                + "' does not match target store model '" + targetModel
                                + "'. Use --reembed to migrate."
                );
            }
        }

        if (!allowReembed && targetDimensions > 0 && embedding != null && embedding.dimensions() > 0) {
            if (embedding.dimensions() != targetDimensions) {
                throw new SpectorValidationException(
                        ErrorCode.DIMENSIONS_MISMATCH,
                        targetDimensions,
                        embedding.dimensions()
                );
            }
        }
    }

    /**
     * Serializes this manifest to formatted JSON.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SpectorBundleManifest to JSON", e);
        }
    }

    /**
     * Deserializes a SpectorBundleManifest from JSON string.
     */
    public static SpectorBundleManifest fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        try {
            return MAPPER.readValue(json, SpectorBundleManifest.class);
        } catch (Exception e) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Failed to parse SpectorBundleManifest from JSON: " + e.getMessage()
            );
        }
    }

    /**
     * Deserializes a SpectorBundleManifest from an InputStream.
     */
    public static SpectorBundleManifest fromJson(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try {
            return MAPPER.readValue(inputStream, SpectorBundleManifest.class);
        } catch (Exception e) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Failed to parse SpectorBundleManifest from stream: " + e.getMessage()
            );
        }
    }
}
