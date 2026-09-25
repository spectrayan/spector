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
package com.spectrayan.spector.batch.exporting;

import com.spectrayan.spector.batch.SpectorBundleCodec;
import com.spectrayan.spector.batch.SpectorBundleManifest;
import com.spectrayan.spector.batch.SpectorBundleManifest.BundleCounts;
import com.spectrayan.spector.batch.SpectorBundleManifest.EmbeddingDescriptor;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.TemporalFact;
import com.spectrayan.spector.kernel.store.TemporalFactsMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core exporter service that extracts real cognitive state, vectors, Hebbian graph edges,
 * typed hyperedges, and temporal facts from {@link SpectorMemory} into staging bundle members
 * conforming to ADR-0045 (memory-portability R1.1 - R1.8).
 */
@Component
public class SpectorMemoryExporter {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryExporter.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Streams memory nodes and ordinal-aligned vectors into chunked files within the staging directory.
     *
     * @param stagingDir destination staging directory
     * @param memory     source SpectorMemory instance
     * @param scope      scoping and chunking parameters
     * @param dimsHint   optional vector dimensionality hint (&le; 0 to infer)
     * @return export result containing record count and resolved vector dimensionality
     * @throws IOException on I/O failure
     */
    public NodeExportResult exportNodesAndVectors(
            Path stagingDir,
            SpectorMemory memory,
            ExportScope scope,
            int dimsHint
    ) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        Path nodesDir = stagingDir.resolve("nodes");
        Path vectorsDir = stagingDir.resolve("vectors");
        Files.createDirectories(nodesDir);
        Files.createDirectories(vectorsDir);

        SpectorMemoryAdmin admin = memory.admin();
        MemoryIndex index = admin.index();

        List<String> candidateIds = index.orderedIds();
        if (candidateIds == null || candidateIds.isEmpty()) {
            candidateIds = new ArrayList<>(index.locationMap().keySet());
        }

        List<CognitiveRecord> matchingRecords = new ArrayList<>();
        for (String id : candidateIds) {
            CognitiveRecord record = memory.inspect(id);
            if (record == null || record.isPurged()) {
                continue;
            }
            if (!scope.includeTombstones() && record.isTombstoned()) {
                continue;
            }
            if (scope.tier() != null && record.memoryType() != scope.tier()) {
                continue;
            }
            if (scope.createdFrom() != null && record.timestampMs() < scope.createdFrom()) {
                continue;
            }
            if (scope.createdTo() != null && record.timestampMs() > scope.createdTo()) {
                continue;
            }
            matchingRecords.add(record);
        }

        // Total stable order: (created_at ASC, id ASC)
        matchingRecords.sort(Comparator.comparingLong(CognitiveRecord::timestampMs)
                .thenComparing(CognitiveRecord::id));

        int resolvedDims = resolveDimensions(admin.quantizer(), matchingRecords, dimsHint);
        int chunkSize = scope.recordsPerChunk();
        int totalRecords = matchingRecords.size();

        if (totalRecords == 0) {
            // Write 0-entry initial chunk
            Path emptyChunkNode = nodesDir.resolve("chunk-00001.jsonl");
            Files.writeString(emptyChunkNode, "", StandardCharsets.UTF_8);
            if (resolvedDims > 0) {
                Path emptyChunkVec = vectorsDir.resolve("chunk-00001.bin");
                Files.write(emptyChunkVec, new byte[0]);
            }
            log.info("[SpectorMemoryExporter] Exported 0 records for namespace='{}'", scope.namespace());
            return new NodeExportResult(0, resolvedDims);
        }

        int chunkIndex = 1;
        for (int start = 0; start < totalRecords; start += chunkSize) {
            int end = Math.min(start + chunkSize, totalRecords);
            List<CognitiveRecord> chunkRecords = matchingRecords.subList(start, end);

            String chunkNodeName = String.format("chunk-%05d.jsonl", chunkIndex);
            String chunkVecName = String.format("chunk-%05d.bin", chunkIndex);
            Path nodeFile = nodesDir.resolve(chunkNodeName);
            Path vecFile = vectorsDir.resolve(chunkVecName);

            writeNodeChunk(nodeFile, chunkRecords);
            if (resolvedDims > 0) {
                writeVectorChunk(vecFile, chunkRecords, resolvedDims, admin.quantizer());
            }

            chunkIndex++;
        }

        log.info("[SpectorMemoryExporter] Exported {} records (dims={}) across {} chunks for namespace='{}'",
                totalRecords, resolvedDims, chunkIndex - 1, scope.namespace());
        return new NodeExportResult(totalRecords, resolvedDims);
    }

    private void writeNodeChunk(Path nodeFile, List<CognitiveRecord> records) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(nodeFile, StandardCharsets.UTF_8)) {
            for (CognitiveRecord rec : records) {
                ExportNodeRecord nodeRecord = ExportNodeRecord.from(rec);
                writer.write(MAPPER.writeValueAsString(nodeRecord));
                writer.newLine();
            }
        }
    }

    private void writeVectorChunk(
            Path vecFile,
            List<CognitiveRecord> records,
            int dims,
            ScalarQuantizer quantizer
    ) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(vecFile.toFile());
             FileChannel channel = fos.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(records.size() * dims * Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);

            for (CognitiveRecord rec : records) {
                float[] vec = extractFloatVector(rec, dims, quantizer);
                for (float f : vec) {
                    buffer.putFloat(f);
                }
            }
            buffer.flip();
            channel.write(buffer);
        }
    }

    private float[] extractFloatVector(CognitiveRecord rec, int dims, ScalarQuantizer quantizer) {
        float[] result = new float[dims];
        byte[] quantized = rec.quantizedVector();
        if (quantized == null || quantized.length == 0) {
            return result;
        }

        if (quantizer != null && quantizer.dimensions() == quantized.length) {
            return quantizer.decode(quantized);
        } else if (quantizer != null && quantizer.mins() != null && quantizer.scales() != null) {
            float[] mins = quantizer.mins();
            float[] scales = quantizer.scales();
            int len = Math.min(dims, Math.min(quantized.length, mins.length));
            for (int i = 0; i < len; i++) {
                int q = Byte.toUnsignedInt(quantized[i]);
                result[i] = mins[i] + q * scales[i];
            }
            return result;
        } else if (quantized.length == dims * Float.BYTES) {
            ByteBuffer wrap = ByteBuffer.wrap(quantized).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < dims; i++) {
                result[i] = wrap.getFloat();
            }
            return result;
        } else {
            int len = Math.min(dims, quantized.length);
            for (int i = 0; i < len; i++) {
                result[i] = (Byte.toUnsignedInt(quantized[i]) - 128) / 128.0f;
            }
            return result;
        }
    }

    private int resolveDimensions(ScalarQuantizer quantizer, List<CognitiveRecord> records, int dimsHint) {
        if (dimsHint > 0) {
            return dimsHint;
        }
        if (quantizer != null && quantizer.dimensions() > 0) {
            return quantizer.dimensions();
        }
        for (CognitiveRecord rec : records) {
            if (rec.quantizedVector() != null && rec.quantizedVector().length > 0) {
                return rec.quantizedVector().length;
            }
        }
        return 0;
    }

    /**
     * Exports real Hebbian graph edges, typed hyperedges, and temporal facts into {@code graph/} bundle members.
     *
     * @param stagingDir destination staging directory
     * @param memory     source SpectorMemory instance
     * @return counts of exported edges, hyperedges, and facts
     * @throws IOException on I/O failure
     */
    public GraphExportResult exportGraph(Path stagingDir, SpectorMemory memory) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(memory, "memory must not be null");

        Path graphDir = stagingDir.resolve("graph");
        Files.createDirectories(graphDir);

        SpectorMemoryAdmin admin = memory.admin();
        MemoryIndex index = admin.index();

        long edgeCount = exportHebbianEdges(graphDir.resolve("edges.jsonl"), admin, index);
        long hyperedgeCount = exportHyperedges(graphDir.resolve("hyperedges.jsonl"), admin, index);
        long factCount = exportTemporalFacts(graphDir.resolve("facts.jsonl"), admin);

        log.info("[SpectorMemoryExporter] Exported graph: edges={}, hyperedges={}, facts={}",
                edgeCount, hyperedgeCount, factCount);
        return new GraphExportResult(edgeCount, hyperedgeCount, factCount);
    }

    private long exportHebbianEdges(Path edgesFile, SpectorMemoryAdmin admin, MemoryIndex index) throws IOException {
        long count = 0;
        HebbianGraphBase hebbian = admin.graph() != null ? admin.graph().rawHebbianGraph() : null;

        try (BufferedWriter writer = Files.newBufferedWriter(edgesFile, StandardCharsets.UTF_8)) {
            if (hebbian != null) {
                int capacity = hebbian.capacity();
                for (int slotA = 0; slotA < capacity; slotA++) {
                    String sourceId = index.idAt(slotA);
                    if (sourceId == null) {
                        continue;
                    }
                    List<HebbianEdge> neighbors = hebbian.neighbors(slotA);
                    if (neighbors == null || neighbors.isEmpty()) {
                        continue;
                    }
                    for (HebbianEdge edge : neighbors) {
                        String targetId = index.idAt(edge.neighborIndex());
                        if (targetId == null) {
                            continue;
                        }
                        // Canonical undirected edge: emit once where sourceId < targetId
                        if (sourceId.compareTo(targetId) < 0) {
                            ExportEdgeRecord rec = new ExportEdgeRecord(
                                    sourceId,
                                    targetId,
                                    "HEBBIAN",
                                    edge.weight(),
                                    edge.bridgeScore()
                            );
                            writer.write(MAPPER.writeValueAsString(rec));
                            writer.newLine();
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private long exportHyperedges(Path hyperedgesFile, SpectorMemoryAdmin admin, MemoryIndex index) throws IOException {
        long count = 0;
        HyperEntityGraphMemory hyperGraph = admin.hyperEntityGraph();
        EntityDirectory entityDir = admin.entityDirectory();

        try (BufferedWriter writer = Files.newBufferedWriter(hyperedgesFile, StandardCharsets.UTF_8)) {
            if (hyperGraph != null && hyperGraph.totalHyperedges() > 0) {
                int totalTarget = hyperGraph.totalHyperedges();
                int capacity = hyperGraph.capacity();
                for (int edgeId = 0; edgeId < capacity && count < totalTarget; edgeId++) {
                    HyperEntityGraphMemory.HyperEdge hedge = hyperGraph.getHyperedge(edgeId);
                    if (hedge == null) {
                        continue;
                    }

                    String memoryId = hedge.memoryIdx() >= 0 ? index.idAt(hedge.memoryIdx()) : null;
                    String typeName = roleTypeToString(hedge.type());

                    List<ExportHyperedgeRecord.ExportHyperedgeVertex> vertices = new ArrayList<>();
                    if (hedge.vertices() != null) {
                        for (HyperEntityGraphMemory.HyperEdgeVertex v : hedge.vertices()) {
                            String entity = entityDir != null ? entityDir.entityName(v.entityId()) : String.valueOf(v.entityId());
                            String entityType = entityDir != null ? entityDir.entityType(v.entityId()) : "CONCEPT";
                            String role = roleNameToString(v.roleId());
                            vertices.add(new ExportHyperedgeRecord.ExportHyperedgeVertex(entity, entityType, role, v.roleId()));
                        }
                    }

                    ExportHyperedgeRecord rec = new ExportHyperedgeRecord(
                            hedge.edgeId(),
                            typeName,
                            hedge.weight(),
                            memoryId,
                            hedge.timestamp(),
                            vertices
                    );
                    writer.write(MAPPER.writeValueAsString(rec));
                    writer.newLine();
                    count++;
                }
            }
        }
        return count;
    }

    private long exportTemporalFacts(Path factsFile, SpectorMemoryAdmin admin) throws IOException {
        long count = 0;
        TemporalKnowledgeGraph tkg = admin.temporalKnowledgeGraph();
        EntityDirectory entityDir = admin.entityDirectory();

        try (BufferedWriter writer = Files.newBufferedWriter(factsFile, StandardCharsets.UTF_8)) {
            if (tkg != null && tkg.backing() != null) {
                List<TemporalFactsMemory.FactLogEntry> factEntries = tkg.backing().replayAllFacts();
                if (factEntries != null) {
                    for (TemporalFactsMemory.FactLogEntry entry : factEntries) {
                        TemporalFact tf = entry.fact();
                        if (tf == null) {
                            continue;
                        }

                        String subject = entityDir != null && tf.subjectEntityId() >= 0
                                ? entityDir.entityName(tf.subjectEntityId())
                                : String.valueOf(tf.subjectEntityId());
                        String predicate = tkg.predicateRegistry() != null && tf.predicateId() >= 0
                                ? tkg.predicateRegistry().nameOf(tf.predicateId())
                                : String.valueOf(tf.predicateId());
                        String object = entityDir != null && tf.objectEntityId() >= 0
                                ? entityDir.entityName(tf.objectEntityId())
                                : String.valueOf(tf.objectEntityId());

                        ExportFactRecord rec = new ExportFactRecord(
                                tf.factId(),
                                subject != null ? subject : String.valueOf(tf.subjectEntityId()),
                                predicate != null ? predicate : String.valueOf(tf.predicateId()),
                                object != null ? object : String.valueOf(tf.objectEntityId()),
                                tf.validFrom(),
                                tf.validTo(),
                                tf.txTime(),
                                tf.confidence(),
                                tf.retractsFactId(),
                                tf.flags()
                        );
                        writer.write(MAPPER.writeValueAsString(rec));
                        writer.newLine();
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private String roleTypeToString(int type) {
        return switch (type) {
            case HyperEntityGraphMemory.TYPE_RELATIONSHIP -> "TYPE_RELATIONSHIP";
            case HyperEntityGraphMemory.TYPE_CONTRADICTS -> "TYPE_CONTRADICTS";
            case HyperEntityGraphMemory.TYPE_SUPERSEDES -> "TYPE_SUPERSEDES";
            case HyperEntityGraphMemory.TYPE_CONSTRAINS -> "TYPE_CONSTRAINS";
            default -> "TYPE_" + type;
        };
    }

    private String roleNameToString(int roleId) {
        return switch (roleId) {
            case HyperEntityGraphMemory.ROLE_SUBJECT -> "SUBJECT";
            case HyperEntityGraphMemory.ROLE_OBJECT -> "OBJECT";
            case HyperEntityGraphMemory.ROLE_CONTEXT -> "CONTEXT";
            case HyperEntityGraphMemory.ROLE_INSTRUMENT -> "INSTRUMENT";
            case HyperEntityGraphMemory.ROLE_CORRECTOR -> "CORRECTOR";
            case HyperEntityGraphMemory.ROLE_CORRECTED -> "CORRECTED";
            case HyperEntityGraphMemory.ROLE_DERIVED_FROM -> "DERIVED_FROM";
            default -> "UNSPECIFIED";
        };
    }

    /**
     * Builds and writes {@code manifest.json} with exact entity counts, embedding descriptor,
     * and computed SHA-256 member checksums (ADR-0045, memory-portability R3.1, R3.2).
     *
     * <p>A validator must never stamp its own output; this method writes the authoritative manifest
     * directly without a subsequent {@code "verified": true} rewrite step (R1.8, V5).</p>
     */
    public SpectorBundleManifest writeManifest(
            Path stagingDir,
            SpectorBundleCodec codec,
            String namespaceId,
            String embeddingModel,
            int dimensions,
            String quantizerName,
            BundleCounts counts
    ) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(codec, "codec must not be null");

        Map<String, String> checksums = codec.computeMemberChecksums(stagingDir);

        EmbeddingDescriptor embedding = new EmbeddingDescriptor(
                embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel : "unspecified",
                dimensions,
                quantizerName != null && !quantizerName.isBlank() ? quantizerName : "NONE"
        );

        SpectorBundleManifest manifest = new SpectorBundleManifest(
                SpectorBundleManifest.CURRENT_SCHEMA_VERSION,
                namespaceId != null && !namespaceId.isBlank() ? namespaceId : "default",
                SpectorBundleManifest.DEFAULT_BUILD_VERSION,
                Instant.now().toString(),
                embedding,
                counts,
                checksums
        );

        Path manifestPath = stagingDir.resolve("manifest.json");
        Files.writeString(manifestPath, manifest.toJson(), StandardCharsets.UTF_8);
        log.info("[SpectorMemoryExporter] Authoritative manifest written: records={}, edges={}, hyperedges={}, facts={}",
                counts.records(), counts.edges(), counts.hyperedges(), counts.facts());
        return manifest;
    }

    /**
     * Validates the exported staging directory against the source memory and manifest checksums,
     * strictly verifying parity without stamping the output as verified (R1.8).
     */
    public void validateExport(
            Path stagingDir,
            SpectorBundleCodec codec,
            BundleCounts expectedCounts
    ) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(codec, "codec must not be null");

        Path manifestPath = stagingDir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new SpectorValidationException(ErrorCode.CONFIG_FILE_NOT_FOUND, "manifest.json does not exist in staging");
        }

        SpectorBundleManifest manifest;
        try (var is = Files.newInputStream(manifestPath)) {
            manifest = SpectorBundleManifest.fromJson(is);
        }

        // Verify member SHA-256 checksums
        if (manifest.checksums() != null) {
            for (Map.Entry<String, String> entry : manifest.checksums().entrySet()) {
                Path member = stagingDir.resolve(entry.getKey());
                if (!Files.exists(member)) {
                    throw new SpectorValidationException(
                            ErrorCode.CONFIG_FILE_NOT_FOUND,
                            "Missing staging member recorded in manifest: " + entry.getKey());
                }
                String actualHash = SpectorBundleCodec.computeSha256(member);
                if (!actualHash.equalsIgnoreCase(entry.getValue())) {
                    throw new SpectorValidationException(
                            ErrorCode.ARGUMENT_INVALID,
                            "memberChecksum",
                            String.format("Checksum mismatch for %s: expected %s but got %s",
                                    entry.getKey(), entry.getValue(), actualHash));
                }
            }
        }

        // Verify counts parity
        if (expectedCounts != null) {
            BundleCounts counts = manifest.counts();
            if (counts.records() != expectedCounts.records()
                    || counts.edges() != expectedCounts.edges()
                    || counts.hyperedges() != expectedCounts.hyperedges()
                    || counts.facts() != expectedCounts.facts()) {
                throw new SpectorValidationException(
                        ErrorCode.ARGUMENT_INVALID,
                        "counts",
                        String.format("Manifest counts %s do not match expected export counts %s", counts, expectedCounts));
            }
        }
        log.info("[SpectorMemoryExporter] Export validation verified parity successfully.");
    }

    public record NodeExportResult(long totalRecords, int dimensions) {}

    public record GraphExportResult(long edges, long hyperedges, long facts) {}
}
