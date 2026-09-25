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
package com.spectrayan.spector.batch.importing;

import com.spectrayan.spector.batch.SpectorBundleManifest;
import com.spectrayan.spector.batch.exporting.ExportEdgeRecord;
import com.spectrayan.spector.batch.exporting.ExportFactRecord;
import com.spectrayan.spector.batch.exporting.ExportHyperedgeRecord;
import com.spectrayan.spector.batch.exporting.ExportNodeRecord;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.index.IndexReconcileReport;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Core importer service that parses SMB bundle members and ingests cognitive state,
 * vectors, associative Hebbian edges, typed hyperedges, and temporal facts into
 * {@link SpectorMemory} (ADR-0045, memory-portability R2.1 - R2.7).
 */
@Component
public class SpectorMemoryImporter {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryImporter.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Parses chunked node records and ordinal-aligned vectors from the staging directory
     * and ingests them into the target memory store (R2.1, R2.6).
     *
     * <p>Enforces idempotency by checking memory existence before ingestion (R2.2).</p>
     *
     * @param stagingDir    unpacked bundle staging directory
     * @param memory        target SpectorMemory instance
     * @param allowReembed  true to re-embed memories using the target store's embedding provider
     * @return node import results containing count of newly ingested records
     * @throws IOException on I/O or parsing error
     */
    public NodeImportResult importNodesAndVectors(
            Path stagingDir,
            SpectorMemory memory,
            boolean allowReembed
    ) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(memory, "memory must not be null");

        Path manifestPath = stagingDir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalStateException("SMB bundle staging directory missing manifest.json: " + stagingDir);
        }

        SpectorBundleManifest manifest = SpectorBundleManifest.fromJson(
                Files.readString(manifestPath, StandardCharsets.UTF_8)
        );
        int dims = manifest.embedding().dimensions();

        Path nodesDir = stagingDir.resolve("nodes");
        Path vectorsDir = stagingDir.resolve("vectors");
        if (!Files.exists(nodesDir) || !Files.isDirectory(nodesDir)) {
            throw new IllegalStateException("SMB bundle staging directory missing nodes directory: " + nodesDir);
        }

        List<Path> nodeChunks;
        try (var stream = Files.list(nodesDir)) {
            nodeChunks = stream.filter(p -> p.getFileName().toString().matches("chunk-\\d{5}\\.jsonl"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }

        long importedCount = 0;
        long skippedCount = 0;

        for (Path chunkPath : nodeChunks) {
            String chunkFileName = chunkPath.getFileName().toString();
            String vecFileName = chunkFileName.replace(".jsonl", ".bin");
            Path vecPath = vectorsDir.resolve(vecFileName);

            ByteBuffer vecBuf = null;
            if (Files.exists(vecPath) && Files.size(vecPath) > 0) {
                byte[] vecBytes = Files.readAllBytes(vecPath);
                vecBuf = ByteBuffer.wrap(vecBytes).order(ByteOrder.LITTLE_ENDIAN);
            }

            try (BufferedReader reader = Files.newBufferedReader(chunkPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    ExportNodeRecord rec = MAPPER.readValue(line, ExportNodeRecord.class);

                    float[] vector = null;
                    if (vecBuf != null && dims > 0 && vecBuf.remaining() >= dims * Float.BYTES) {
                        vector = new float[dims];
                        for (int d = 0; d < dims; d++) {
                            vector[d] = vecBuf.getFloat();
                        }
                    } else if (dims > 0) {
                        vector = new float[dims];
                    }

                    // Idempotency: skip if already present in target memory (R2.2)
                    if (memory.inspect(rec.id()) != null) {
                        log.debug("[SpectorMemoryImporter] Skipping existing record '{}'", rec.id());
                        skippedCount++;
                        continue;
                    }

                    MemoryType tier = MemoryType.SEMANTIC;
                    if (rec.tier() != null) {
                        try {
                            tier = MemoryType.valueOf(rec.tier());
                        } catch (IllegalArgumentException ignored) {}
                    }

                    MemorySource source = MemorySource.USER_STATED;
                    if (rec.source() != null) {
                        try {
                            source = MemorySource.valueOf(rec.source());
                        } catch (IllegalArgumentException ignored) {}
                    }

                    String[] tags = rec.tags() != null ? rec.tags().toArray(new String[0]) : new String[0];

                    RememberHints hints = null;
                    if (rec.importance() > 0 || rec.valence() != 0 || rec.arousal() != 0) {
                        hints = new RememberHints(
                                rec.importance(),
                                (rec.valence() + 128) / 256.0f,
                                (rec.arousal() + 128) / 256.0f
                        );
                    }

                    RememberContext context = RememberContext.builder()
                            .overrideTimestampMs(rec.timestampMs())
                            .metadata(rec.metadata())
                            .hints(hints)
                            .build();

                    if (allowReembed) {
                        memory.remember(rec.id(), rec.text(), tier, source, context, tags);
                    } else {
                        memory.admin().rememberPathway().ingestCognitive(
                                rec.id(), rec.text(), vector, tier, tags, source, context
                        );
                    }
                    importedCount++;
                }
            }
        }

        log.info("[SpectorMemoryImporter] Imported {} nodes (skipped {} duplicates, dims={}) across {} chunks",
                importedCount, skippedCount, dims, nodeChunks.size());
        return new NodeImportResult(importedCount, skippedCount, dims);
    }

    /**
     * Reconstructs Hebbian edges, typed hyperedges, and temporal facts from {@code graph/} bundle members (R2.3).
     *
     * <p>Endpoint memory IDs are resolved to newly-assigned graph slots in the target store.</p>
     *
     * @param stagingDir destination staging directory
     * @param memory     target SpectorMemory instance
     * @return counts of newly imported graph elements
     * @throws IOException on I/O failure
     */
    public GraphImportResult importGraph(Path stagingDir, SpectorMemory memory) throws IOException {
        Objects.requireNonNull(stagingDir, "stagingDir must not be null");
        Objects.requireNonNull(memory, "memory must not be null");

        Path graphDir = stagingDir.resolve("graph");
        if (!Files.exists(graphDir) || !Files.isDirectory(graphDir)) {
            return new GraphImportResult(0, 0, 0);
        }

        SpectorMemoryAdmin admin = memory.admin();
        MemoryIndex index = admin.index();

        long importedEdges = importHebbianEdges(graphDir.resolve("edges.jsonl"), admin, index);
        long importedHyperedges = importHyperedges(graphDir.resolve("hyperedges.jsonl"), admin, index);
        long importedFacts = importTemporalFacts(graphDir.resolve("facts.jsonl"), memory);

        log.info("[SpectorMemoryImporter] Imported graph: edges={}, hyperedges={}, facts={}",
                importedEdges, importedHyperedges, importedFacts);
        return new GraphImportResult(importedEdges, importedHyperedges, importedFacts);
    }

    private long importHebbianEdges(Path edgesFile, SpectorMemoryAdmin admin, MemoryIndex index) throws IOException {
        if (!Files.exists(edgesFile) || admin.graph() == null) {
            return 0;
        }

        HebbianGraphBase rawHebbian = admin.graph().rawHebbianGraph();
        if (rawHebbian == null || index == null) {
            return 0;
        }

        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(edgesFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                ExportEdgeRecord edge = MAPPER.readValue(line, ExportEdgeRecord.class);
                Integer slotA = index.slotOf(edge.sourceId());
                Integer slotB = index.slotOf(edge.targetId());
                if (slotA != null && slotB != null) {
                    // Idempotency: verify edge does not already exist
                    List<HebbianEdge> neighbors = rawHebbian.neighbors(slotA);
                    boolean exists = neighbors != null && neighbors.stream().anyMatch(n -> n.neighborIndex() == slotB);
                    if (!exists) {
                        rawHebbian.strengthen(slotA, slotB, edge.weight());
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long importHyperedges(Path hyperedgesFile, SpectorMemoryAdmin admin, MemoryIndex index) throws IOException {
        if (!Files.exists(hyperedgesFile)) {
            return 0;
        }

        HyperEntityGraphMemory hyperGraph = admin.hyperEntityGraph();
        EntityDirectory entityDir = admin.entityDirectory();
        if (hyperGraph == null || entityDir == null || index == null) {
            return 0;
        }

        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(hyperedgesFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                ExportHyperedgeRecord hedge = MAPPER.readValue(line, ExportHyperedgeRecord.class);

                int memIdx = -1;
                if (hedge.memoryId() != null) {
                    Integer slot = index.slotOf(hedge.memoryId());
                    if (slot != null) {
                        memIdx = slot;
                    }
                }

                if (hedge.vertices() == null || hedge.vertices().size() < 2) {
                    continue;
                }

                int vCount = hedge.vertices().size();
                int[] vertexEntities = new int[vCount];
                int[] vertexRoles = new int[vCount];
                boolean valid = true;

                for (int i = 0; i < vCount; i++) {
                    ExportHyperedgeRecord.ExportHyperedgeVertex v = hedge.vertices().get(i);
                    int eId = entityDir.intern(v.entity(), v.entityType());
                    if (eId < 0) {
                        valid = false;
                        break;
                    }
                    vertexEntities[i] = eId;
                    vertexRoles[i] = parseRoleId(v.role(), v.roleId());
                }
                if (!valid) {
                    continue;
                }

                int type = parseRoleType(hedge.type());

                // Idempotency check: verify if identical hyperedge already exists
                boolean exists = false;
                List<HyperEntityGraphMemory.HyperEdge> existingEdges = hyperGraph.findHyperedgesForEntity(vertexEntities[0]);
                if (existingEdges != null) {
                    for (HyperEntityGraphMemory.HyperEdge ex : existingEdges) {
                        if (ex.type() == type && ex.memoryIdx() == memIdx && ex.vertices().size() == vCount) {
                            boolean allMatch = true;
                            for (int k = 0; k < vCount; k++) {
                                if (ex.vertices().get(k).entityId() != vertexEntities[k]) {
                                    allMatch = false;
                                    break;
                                }
                            }
                            if (allMatch) {
                                exists = true;
                                break;
                            }
                        }
                    }
                }

                if (!exists) {
                    int edgeId = hyperGraph.addHyperedge(
                            vertexEntities, vertexRoles, type, hedge.weight(), memIdx, hedge.timestamp()
                    );
                    if (edgeId >= 0) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long importTemporalFacts(Path factsFile, SpectorMemory memory) throws IOException {
        if (!Files.exists(factsFile)) {
            return 0;
        }

        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(factsFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                ExportFactRecord fact = MAPPER.readValue(line, ExportFactRecord.class);
                memory.assertFact(
                        fact.subject(),
                        fact.predicate(),
                        fact.object(),
                        fact.validFrom(),
                        fact.validTo(),
                        fact.confidence(),
                        true
                );
                count++;
            }
        }
        return count;
    }

    /**
     * Reconciles derived indexes (BM25, Entity Reverse, SPLADE) per ADR-0082 (R2.4).
     *
     * @param memory target SpectorMemory instance
     * @return index reconciliation report, or null if engine not present
     */
    public IndexReconcileReport reconcileIndexes(SpectorMemory memory) {
        Objects.requireNonNull(memory, "memory must not be null");
        if (memory.indexReconcileEngine() != null) {
            IndexReconcileReport report = memory.indexReconcileEngine().reconcile();
            log.info("[SpectorMemoryImporter] Index reconciliation finished: {}", report);
            return report;
        }
        return null;
    }

    public static int parseRoleType(String typeStr) {
        if ("TYPE_CONTRADICTS".equalsIgnoreCase(typeStr)) {
            return HyperEntityGraphMemory.TYPE_CONTRADICTS;
        }
        if ("TYPE_SUPERSEDES".equalsIgnoreCase(typeStr)) {
            return HyperEntityGraphMemory.TYPE_SUPERSEDES;
        }
        if ("TYPE_CONSTRAINS".equalsIgnoreCase(typeStr)) {
            return HyperEntityGraphMemory.TYPE_CONSTRAINS;
        }
        if ("TYPE_RELATIONSHIP".equalsIgnoreCase(typeStr)) {
            return HyperEntityGraphMemory.TYPE_RELATIONSHIP;
        }
        if (typeStr != null && typeStr.startsWith("TYPE_")) {
            try {
                return Integer.parseInt(typeStr.substring(5));
            } catch (NumberFormatException ignored) {}
        }
        return HyperEntityGraphMemory.TYPE_RELATIONSHIP;
    }

    public static int parseRoleId(String roleStr, int fallbackId) {
        if (fallbackId > 0) {
            return fallbackId;
        }
        if (roleStr == null) {
            return HyperEntityGraphMemory.ROLE_UNSPECIFIED;
        }
        return switch (roleStr.toUpperCase(Locale.ROOT)) {
            case "SUBJECT" -> HyperEntityGraphMemory.ROLE_SUBJECT;
            case "OBJECT" -> HyperEntityGraphMemory.ROLE_OBJECT;
            case "CONTEXT" -> HyperEntityGraphMemory.ROLE_CONTEXT;
            case "INSTRUMENT" -> HyperEntityGraphMemory.ROLE_INSTRUMENT;
            case "CORRECTOR" -> HyperEntityGraphMemory.ROLE_CORRECTOR;
            case "CORRECTED" -> HyperEntityGraphMemory.ROLE_CORRECTED;
            case "DERIVED_FROM" -> HyperEntityGraphMemory.ROLE_DERIVED_FROM;
            default -> HyperEntityGraphMemory.ROLE_UNSPECIFIED;
        };
    }

    public record NodeImportResult(long importedRecords, long skippedDuplicates, int dimensions) {}

    public record GraphImportResult(long importedEdges, long importedHyperedges, long importedFacts) {}
}
