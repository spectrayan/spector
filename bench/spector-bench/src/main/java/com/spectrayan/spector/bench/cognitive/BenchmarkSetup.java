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
package com.spectrayan.spector.bench.cognitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityType;
import com.spectrayan.spector.memory.graph.RelationType;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

/**
 * Bootstraps a {@link SpectorMemory} instance populated with the benchmark corpus.
 *
 * <p>Configures graphs (Hebbian, Temporal, Entity) from dataset definitions.
 * Uses {@code RecallMode.OBSERVE} semantics  --  the memory instance is read-only
 * after setup completes, preventing side effects during benchmark queries.</p>
 *
 * <p>Implements {@link AutoCloseable} to ensure off-heap resources (Arena-backed
 * MemorySegments in HebbianGraph, TemporalChain, EntityGraph) are properly released.</p>
 */
public final class BenchmarkSetup implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkSetup.class);

    private SpectorMemory memory;
    private Map<String, Integer> idToSlot;

    /**
     * Creates a fully-populated memory instance from the dataset.
     *
     * <p>Ingests all corpus records using the provided embedding provider,
     * then loads Hebbian edges, temporal chains, and entity relations from
     * the dataset definitions.</p>
     *
     * @param dataset  the loaded and validated benchmark dataset
     * @param embedder the embedding provider for vectorizing corpus text
     * @return a fully configured SpectorMemory instance ready for benchmarking
     */
    public SpectorMemory createMemoryInstance(DatasetLoader.LoadedDataset dataset,
                                              EmbeddingProvider embedder) {
        return createMemoryInstance(dataset, embedder, null, null);
    }

    public SpectorMemory createMemoryInstance(DatasetLoader.LoadedDataset dataset,
                                              EmbeddingProvider embedder,
                                              Path datasetDir) {
        return createMemoryInstance(dataset, embedder, datasetDir, null);
    }

    public SpectorMemory createMemoryInstance(DatasetLoader.LoadedDataset dataset,
                                              EmbeddingProvider embedder,
                                              Path datasetDir,
                                              com.spectrayan.spector.memory.aisme.config.AismeConfig aismeConfig) {
        List<BenchmarkCorpusRecord> corpus = dataset.corpus();
        int corpusSize = corpus.size();

        log.info("Creating benchmark memory instance: {} corpus records, {} dimensions",
                corpusSize, embedder.dimensions());

        // Build map from memory ID to ExtractedEntity list
        final java.util.Map<String, java.util.List<com.spectrayan.spector.memory.graph.ExtractedEntity>> memoryEntitiesMap = new java.util.HashMap<>();
        final java.util.Map<String, java.util.List<com.spectrayan.spector.memory.graph.ExtractedEntity>> textEntitiesMap = new java.util.HashMap<>();

        // Group relations by source memory ID
        java.util.Map<String, java.util.List<com.spectrayan.spector.bench.cognitive.model.EntityRelation>> relationsByMemory = new java.util.HashMap<>();
        for (com.spectrayan.spector.bench.cognitive.model.EntityRelation rel : dataset.entityRelations()) {
            for (String memoryId : rel.sourceMemoryIds()) {
                relationsByMemory.computeIfAbsent(memoryId, k -> new java.util.ArrayList<>()).add(rel);
            }
        }

        // Map from entity name to type
        java.util.Map<String, String> entityTypes = new java.util.HashMap<>();
        for (BenchmarkCorpusRecord record : corpus) {
            if (record.entityMentions() != null) {
                for (com.spectrayan.spector.bench.cognitive.model.EntityMention mention : record.entityMentions()) {
                    entityTypes.put(mention.name(), mention.type());
                }
            }
        }
        for (com.spectrayan.spector.bench.cognitive.model.EntityRelation rel : dataset.entityRelations()) {
            entityTypes.put(rel.fromEntity().name(), rel.fromEntity().type());
            entityTypes.put(rel.toEntity().name(), rel.toEntity().type());
        }

        // For each corpus record, build the list of ExtractedEntity
        for (BenchmarkCorpusRecord record : corpus) {
            java.util.List<com.spectrayan.spector.bench.cognitive.model.EntityRelation> relsForMemory =
                    relationsByMemory.getOrDefault(record.id(), java.util.List.of());

            // Collect all unique entity names involved in this memory
            java.util.Set<String> entitiesInMemory = new java.util.LinkedHashSet<>();
            if (record.entityMentions() != null) {
                for (com.spectrayan.spector.bench.cognitive.model.EntityMention mention : record.entityMentions()) {
                    entitiesInMemory.add(mention.name());
                }
            }
            for (var rel : relsForMemory) {
                entitiesInMemory.add(rel.fromEntity().name());
                entitiesInMemory.add(rel.toEntity().name());
            }

            java.util.List<com.spectrayan.spector.memory.graph.ExtractedEntity> extractedList = new java.util.ArrayList<>();
            for (String entityName : entitiesInMemory) {
                String type = entityTypes.getOrDefault(entityName, "OTHER");

                // Find all relations originating from this entity in this memory
                java.util.List<com.spectrayan.spector.memory.graph.EntityRelation> targetRelations = new java.util.ArrayList<>();
                for (var rel : relsForMemory) {
                    if (rel.fromEntity().name().equals(entityName)) {
                        targetRelations.add(new com.spectrayan.spector.memory.graph.EntityRelation(
                                rel.toEntity().name(),
                                rel.relationType()
                        ));
                    }
                }
                extractedList.add(new com.spectrayan.spector.memory.graph.ExtractedEntity(
                        entityName,
                        type,
                        targetRelations
                ));
            }

            memoryEntitiesMap.put(record.id(), extractedList);
            textEntitiesMap.put(record.text(), extractedList);
        }

        com.spectrayan.spector.memory.graph.EntityExtractor customExtractor = new com.spectrayan.spector.memory.graph.EntityExtractor() {
            @Override
            public java.util.List<com.spectrayan.spector.memory.graph.ExtractedEntity> extract(String id, String text) {
                java.util.List<com.spectrayan.spector.memory.graph.ExtractedEntity> res = null;
                if (id != null) {
                    res = memoryEntitiesMap.get(id);
                }
                if ((res == null || res.isEmpty()) && text != null) {
                    res = textEntitiesMap.get(text);
                    if (res == null || res.isEmpty()) {
                        res = textEntitiesMap.get(text.trim());
                    }
                    if (res == null || res.isEmpty() && (text.startsWith("user: ") || text.startsWith("assistant: "))) {
                        String stripped = text.substring(text.indexOf(':') + 1).trim();
                        res = textEntitiesMap.get(stripped);
                    }
                }
                return res != null ? res : java.util.List.of();
            }
            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        com.spectrayan.spector.memory.SpectorMemoryBuilder builder = DefaultSpectorMemory.builder()
                .bundleMode(true)
                .usePathwayEngine(Boolean.parseBoolean(System.getProperty("spector.pathway.enabled", System.getProperty("usePathwayEngine", "true"))))
                .dimensions(embedder.dimensions())
                .embeddingProvider(embedder)
                .workingCapacity(Math.max(50, corpusSize / 10))
                .episodicPartitionCapacity(corpusSize + 100)
                .semanticCapacity(corpusSize + 100)
                .proceduralCapacity(Math.max(50, corpusSize / 5))
                .hebbianGraphCapacity(corpusSize + 100)
                .temporalChainCapacity(corpusSize + 100)
                .entityGraphCapacity(Math.max(50_000, corpusSize * 5))
                .entityExtractionMode(EntityExtractionMode.CUSTOM)
                .entityExtractor(customExtractor)
                .entityExtractionQueueCapacity(corpusSize + 1000);

        if (aismeConfig != null) {
            builder.aismeConfig(aismeConfig);
        } else {
            builder.aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig.builder()
                    .enabled(true)
                    .globalWorkspaceCapacity(100)
                    .build());
        }


        float threshold = 0.40f;
        String thresholdStr = System.getProperty("spector.benchmark.graphExpansionThreshold");
        if (thresholdStr == null || thresholdStr.isBlank()) {
            thresholdStr = System.getProperty("graphExpansionThreshold");
        }
        if (thresholdStr != null && !thresholdStr.isBlank()) {
            try {
                threshold = Float.parseFloat(thresholdStr);
            } catch (NumberFormatException ignored) {}
        }
        com.spectrayan.spector.memory.pipeline.GraphExpansionMode expansionMode = com.spectrayan.spector.memory.pipeline.GraphExpansionMode.resolve();
        log.info("Instantiating GraphScoringPolicy with threshold={}, mode={}", threshold, expansionMode);
        com.spectrayan.spector.memory.pipeline.GraphScoringPolicy scoringPolicy = new com.spectrayan.spector.memory.pipeline.GraphScoringPolicy(
                0.3f,   // causalBoostWeight
                0.3f,   // hebbianBoostFactor
                0.8f,   // temporalForwardFactor
                0.7f,   // temporalBackwardFactor
                0.25f,  // entityHopAttenuation
                3,      // hebbianMaxDepth  (match resolveDefault — deeper cross-session associations)
                5,      // temporalMaxHops  (match resolveDefault — wider session coverage)
                2,      // entityMaxHops
                threshold,
                expansionMode
        );
        builder.graphScoringPolicy(scoringPolicy);

        // Resolve persistence parameters
        boolean useDisk = Boolean.parseBoolean(System.getProperty("spector.benchmark.persistence", "true"));
        Path persistencePath = null;
        if (useDisk) {
            String sysPropPath = System.getProperty("spector.memory.persistence-path");
            if (sysPropPath != null && !sysPropPath.isBlank()) {
                persistencePath = Path.of(sysPropPath);
            }
            if (persistencePath == null && datasetDir != null) {
                Path datasetConfig = datasetDir.resolve("spector-bench.yml");
                if (Files.exists(datasetConfig)) {
                    log.info("Loaded dataset configuration from {}", datasetConfig);
                }
                persistencePath = datasetDir.resolve("ingested-memory");
            }
            if (persistencePath == null) {
                Path configFile = Path.of("spector-bench.yml");
                if (Files.exists(configFile)) {
                    try {
                        var props = SpectorProperties.load(configFile);
                        var defaults = SpectorConfigFactory.memoryDefaults(props);
                        persistencePath = defaults.persistencePath() != null ? Path.of(defaults.persistencePath()) : null;
                    } catch (Exception e) {
                        // ignore config loading errors
                    }
                }
            }
        }

        if (persistencePath != null) {
            builder.persistenceMode(MemoryPersistenceMode.DISK)
                   .persistence(persistencePath);
            log.info("Disk persistence enabled. Path: {}", persistencePath);
        } else {
            builder.persistenceMode(MemoryPersistenceMode.IN_MEMORY);
            log.info("In-memory persistence mode active.");
        }

        memory = builder.build();

        // Ingest all corpus records or load from persistent store
        Map<String, Integer> idToSlot = new LinkedHashMap<>(corpusSize);
        boolean isDiskLoaded = false;
        if (memory.totalMemories() > 0) {
            isDiskLoaded = true;
            log.info("Discovered pre-ingested memories on disk. Reconstructing slot mappings.");
            for (BenchmarkCorpusRecord record : corpus) {
                var loc = memory.admin().index().locate(record.id());
                if (loc != null) {
                    int slotIdx = offsetToRecordIndex(loc, memory);
                    idToSlot.put(record.id(), slotIdx);
                } else {
                    log.warn("Record {} is in corpus but not found in pre-ingested memory index!", record.id());
                }
            }
            log.info("Reconstructed {} slot mappings from disk", idToSlot.size());
        } else {
            int slot = 0;
            for (BenchmarkCorpusRecord record : corpus) {
                try {
                    IngestionHints hints = new IngestionHints(
                            record.interest(), record.challenge(), record.urgency(),
                            record.valence(),
                            (byte) record.arousal()
                    );

                    // Use IngestionContext to pass the corpus record's original timestamp
                    // into the cognitive header, preserving temporal accuracy for decay and
                    // temporal chain ordering across the 180-day benchmark span.
                    var context = com.spectrayan.spector.memory.model.IngestionContext.builder()
                            .hints(hints)
                            .overrideTimestampMs(record.timestampMs())
                            .build();

                    memory.remember(
                            record.id(),
                            record.text(),
                            record.memoryType(),
                            MemorySource.OBSERVED,
                            context,
                            record.synapticTags().toArray(String[]::new)
                    );

                    idToSlot.put(record.id(), slot);
                    slot++;
                } catch (Exception e) {
                    log.warn("Failed to ingest corpus record '{}': {}", record.id(), e.getMessage());
                }
            }
            log.info("Ingested {} of {} corpus records", idToSlot.size(), corpusSize);
        }

        // Store for external access (subsystem contribution detection)
        this.idToSlot = idToSlot;

        // Load graph structures from dataset definitions (null-safe: subsystems may be unconfigured)
        if (!isDiskLoaded) {
            var graph = memory.admin().graph();
            var hg = graph != null ? graph.rawHebbianGraph() : null;
            var tc = graph != null ? graph.rawTemporalChain() : null;
            var hyper = graph != null ? graph.rawHyperEntityGraph() : null;

            if (hg != null) {
                loadHebbianEdges(hg, dataset.hebbianEdges(), idToSlot);
            } else {
                log.warn("HebbianGraph is null  --  skipping {} edge definitions", dataset.hebbianEdges().size());
            }
            if (tc != null) {
                loadTemporalChains(tc, dataset.temporalChains(), idToSlot);
            } else {
                log.warn("TemporalChain is null  --  skipping {} chain definitions", dataset.temporalChains().size());
            }
            if (memory.admin().entityDirectory() != null && hyper != null) {
                loadEntityGraph(memory.admin().entityDirectory(), hyper, dataset.entityRelations(), idToSlot);
            } else {
                log.warn("EntityGraph is null  --  skipping {} entity relation definitions", dataset.entityRelations().size());
            }
            log.info("Benchmark memory setup complete: hebbian edges={}, temporal chains={}, entity relations={}",
                    dataset.hebbianEdges().size(), dataset.temporalChains().size(),
                    dataset.entityRelations().size());
        } else {
            log.info("Loaded pre-ingested graph structures from disk. Skipping graph ingestion.");
        }

        // Wire salience profile from persona.json if present
        if (dataset.persona() != null && dataset.persona().hasSalienceProfile()) {
            memory.setSalienceProfile(dataset.persona().salienceProfile());
            log.info("Salience profile applied from persona '{}': interests={}, disinterests={}, persona={}",
                    dataset.persona().name(),
                    dataset.persona().salienceProfile().interests().size(),
                    dataset.persona().salienceProfile().disinterests().size(),
                    dataset.persona().salienceProfile().hasPersona());
        }

        return memory;
    }

    private static int offsetToRecordIndex(com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation loc, SpectorMemory memory) {
        return loc.graphSlot();
    }

    /**
     * Populates the HebbianGraph from hebbian_edges.jsonl definitions.
     *
     * <p>Creates bidirectional weighted edges between memory slots. Edges referencing
     * memory IDs not present in the idToSlot mapping are silently skipped with a
     * logged warning (per Requirement 5.5).</p>
     *
     * @param graph    the Hebbian graph to populate
     * @param edges    edge definitions from the dataset
     * @param idToSlot mapping from corpus record IDs to their slot indices
     */
    void loadHebbianEdges(HebbianGraphBase graph, List<HebbianEdgeDef> edges,
                          Map<String, Integer> idToSlot) {
        int loaded = 0;
        int skipped = 0;

        for (HebbianEdgeDef edge : edges) {
            Integer slotA = idToSlot.get(edge.memoryIdA());
            Integer slotB = idToSlot.get(edge.memoryIdB());

            if (slotA == null || slotB == null) {
                skipped++;
                log.warn("Skipping Hebbian edge: missing ID(s)  --  A='{}' ({}), B='{}' ({})",
                        edge.memoryIdA(), slotA != null ? "found" : "MISSING",
                        edge.memoryIdB(), slotB != null ? "found" : "MISSING");
                continue;
            }

            // Strengthen the edge with weight derived from co-activation count.
            // HebbianGraph.strengthen() already creates bidirectional edges.
            float weight = (float) edge.coActivationCount();
            graph.strengthen(slotA, slotB, weight);
            loaded++;
        }

        // Reciprocal Neocortical-Hippocampal Synaptic Bridge:
        // Connect each consolidated semantic fact to its corresponding episodic conversation turns
        int consolidationBridges = 0;
        Map<String, List<Integer>> turnsBySessionPrefix = new HashMap<>();
        for (Map.Entry<String, Integer> entry : idToSlot.entrySet()) {
            String memId = entry.getKey();
            if (!memId.startsWith("fact_")) {
                // e.g. "conv_41_D32_11" -> key "conv_41_sess_32"
                int dIdx = memId.indexOf("_D");
                if (dIdx > 0) {
                    int lastUnder = memId.lastIndexOf('_');
                    if (lastUnder > dIdx) {
                        String prefix = memId.substring(0, dIdx) + "_sess_" + memId.substring(dIdx + 2, lastUnder);
                        turnsBySessionPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(entry.getValue());
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : idToSlot.entrySet()) {
            String memId = entry.getKey();
            if (memId.startsWith("fact_")) {
                // e.g. "fact_conv_41_sess_32_1" -> session prefix "conv_41_sess_32"
                int lastUnder = memId.lastIndexOf('_');
                if (lastUnder > 5) {
                    String sessionKey = memId.substring(5, lastUnder);
                    List<Integer> turnSlots = turnsBySessionPrefix.get(sessionKey);
                    if (turnSlots != null) {
                        int factSlot = entry.getValue();
                        for (int turnSlot : turnSlots) {
                            graph.strengthen(factSlot, turnSlot, 5.0f);
                            consolidationBridges++;
                        }
                    }
                }
            }
        }

        log.info("Loaded {} Hebbian edges ({} skipped) + {} consolidation bridges",
                loaded, skipped, consolidationBridges);
    }

    /**
     * Populates the TemporalChain from temporal_chains.jsonl definitions.
     *
     * <p>Establishes doubly-linked lists for each session chain in the specified order.
     * Each consecutive pair of memory IDs in a chain's orderedMemoryIds is linked,
     * forming a forward/backward traversable chain.</p>
     *
     * @param chain    the temporal chain to populate
     * @param chains   chain definitions from the dataset
     * @param idToSlot mapping from corpus record IDs to their slot indices
     */
    void loadTemporalChains(TemporalChainMemory chain, List<TemporalChainDef> chains,
                            Map<String, Integer> idToSlot) {
        int linkedCount = 0;
        int skipped = 0;

        for (TemporalChainDef chainDef : chains) {
            List<String> orderedIds = chainDef.orderedMemoryIds();
            int sessionHash = chainDef.sessionId().hashCode();

            Integer previousSlot = null;
            for (String memoryId : orderedIds) {
                Integer currentSlot = idToSlot.get(memoryId);
                if (currentSlot == null) {
                    log.warn("Skipping temporal link: memory ID '{}' not found in session '{}'",
                            memoryId, chainDef.sessionId());
                    skipped++;
                    previousSlot = null; // break the chain at missing nodes
                    continue;
                }

                if (previousSlot != null) {
                    // link(currentIdx, previousIdx, sessionId) creates bidirectional links
                    chain.link(currentSlot, previousSlot, sessionHash);
                    linkedCount++;
                }
                previousSlot = currentSlot;
            }
        }

        log.info("Loaded {} temporal links across {} chains ({} skipped)",
                linkedCount, chains.size(), skipped);
    }

    /**
     * Populates the EntityGraph from entities.jsonl definitions.
     *
     * <p>Constructs typed entity nodes and typed edges matching specified relation types.
     * Links entities to their source memory indices based on the sourceMemoryIds field.</p>
     *
     * @param graph     the entity graph to populate
     * @param relations entity relation definitions from the dataset
     * @param corpus    the corpus records (used for entity mention  ->  memory linking)
     */
    void loadEntityGraph(EntityDirectory dir, HyperEntityGraphMemory hyper, List<EntityRelation> relations,
                         Map<String, Integer> idToSlot) {
        int relationsLoaded = 0;

        for (EntityRelation relation : relations) {
            int fromEntityId = dir.intern(relation.fromEntity().name(), relation.fromEntity().type());
            if (fromEntityId < 0) {
                log.warn("Failed to add from-entity '{}' to graph", relation.fromEntity().name());
                continue;
            }

            int toEntityId = dir.intern(relation.toEntity().name(), relation.toEntity().type());
            if (toEntityId < 0) {
                log.warn("Failed to add to-entity '{}' to graph", relation.toEntity().name());
                continue;
            }

            // Link entities to their source memories
            for (String memoryId : relation.sourceMemoryIds()) {
                Integer memIdx = idToSlot.get(memoryId);
                if (memIdx != null) {
                    dir.linkEntityToMemory(fromEntityId, memIdx);
                    dir.linkEntityToMemory(toEntityId, memIdx);
                    
                    hyper.addHyperedge(
                        new int[]{fromEntityId, toEntityId},
                        new int[]{1, 1},
                        1,
                        1.0f,
                        memIdx,
                        System.currentTimeMillis()
                    );
                    relationsLoaded++;
                } else {
                    log.warn("Entity relation source memory '{}' not found in corpus", memoryId);
                }
            }
        }

        log.info("Loaded {} entity relations into graph (entities={})",
                relationsLoaded, dir.entityCount());
    }

    /**
     * Releases all off-heap resources held by the SpectorMemory instance.

     *
     * <p>Closes the underlying SpectorMemory which in turn releases Arena-backed
     * MemorySegments for HebbianGraph, TemporalChain, EntityGraph, tier stores,
     * and other subsystems.</p>
     */
    @Override
    public void close() {
        if (memory != null) {
            log.info("Closing benchmark memory instance");
            memory.close();
            memory = null;
        }
    }

    /**
     * Returns the currently active SpectorMemory instance, or null if not yet created or closed.
     */
    public SpectorMemory memory() {
        return memory;
    }

    /**
     * Returns the mapping from corpus record IDs to their slot indices in the
     * off-heap structures (HebbianGraph, TemporalChain).
     *
     * <p>This mapping is populated during {@link #createMemoryInstance} and is
     * needed by {@link ContributingSubsystem#detect} for graph reachability checks.</p>
     *
     * @return unmodifiable view of the ID-to-slot mapping, or empty map if not yet created
     */
    public Map<String, Integer> idToSlot() {
        return idToSlot != null ? java.util.Collections.unmodifiableMap(idToSlot) : Map.of();
    }
}
