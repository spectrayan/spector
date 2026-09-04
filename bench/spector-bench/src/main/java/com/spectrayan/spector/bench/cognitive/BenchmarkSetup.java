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

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityType;
import com.spectrayan.spector.memory.graph.RelationType;
import com.spectrayan.spector.memory.graph.hebbian.CoActivationMemory;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;

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

        Path datasetConfig = datasetDir != null ? datasetDir.resolve("spector-bench.yml") : null;
        SpectorProperties datasetProps = null;
        if (datasetConfig != null && Files.exists(datasetConfig)) {
            try {
                datasetProps = SpectorProperties.load(datasetConfig);
                log.info("Loaded dataset configuration from {}", datasetConfig);
            } catch (Exception e) {
                log.warn("Failed to load dataset config from {}: {}", datasetConfig, e.getMessage());
            }
        }

        MemoryProperties memoryProperties = datasetProps != null ?
                SpectorConfigFactory.memoryProperties(datasetProps) : new MemoryProperties();

        // Scale capacity defaults to corpus size if not explicitly configured in properties
        if (memoryProperties.getCapacity() <= 0 || memoryProperties.getCapacity() == SpectorPropertyConstants.DEFAULT_MEMORY_CAPACITY) {
            memoryProperties.setCapacity(corpusSize + 100);
        }
        if (memoryProperties.getCoactivationPairCapacity() == SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_PAIR_CAPACITY) {
            memoryProperties.setCoactivationPairCapacity(Math.max(50_000, corpusSize * 50));
        }
        if (memoryProperties.getCoactivationEdgeCapacity() == SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_EDGE_CAPACITY) {
            memoryProperties.setCoactivationEdgeCapacity(Math.max(50_000, corpusSize * 50));
        }

        com.spectrayan.spector.memory.SpectorMemoryBuilder builder =
                com.spectrayan.spector.memory.config.SpectorMemoryConfigurator.builder(datasetProps)
                .fromProperties(memoryProperties)
                .bundleMode(true)
                .usePathwayEngine(Boolean.parseBoolean(System.getProperty("spector.pathway.enabled", System.getProperty("usePathwayEngine", "true"))))
                .dimensions(embedder.dimensions())
                .embeddingProvider(embedder)
                .workingCapacity(Math.max(50, corpusSize / 10))
                .episodicPartitionCapacity(corpusSize + 100)
                .proceduralCapacity(Math.max(50, corpusSize / 5));

        // Resolve Entity Extraction Mode from configuration
        EntityExtractionMode extractionMode = EntityExtractionMode.CUSTOM;
        if (datasetProps != null) {
            String modeStr = datasetProps.getString("spector.benchmark.extraction.mode",
                             datasetProps.getString("extraction.mode",
                             datasetProps.getString("spector.benchmark.extraction.provider",
                             datasetProps.getString("extraction.provider", null))));
            if ("NONE".equalsIgnoreCase(modeStr)) {
                extractionMode = EntityExtractionMode.NONE;
            } else if ("LLM".equalsIgnoreCase(modeStr) || "OLLAMA".equalsIgnoreCase(modeStr)
                    || "GOOGLE".equalsIgnoreCase(modeStr) || "GEMINI".equalsIgnoreCase(modeStr)) {
                extractionMode = EntityExtractionMode.LLM;
            }
        }

        builder.entityExtractionMode(extractionMode);
        if (extractionMode == EntityExtractionMode.CUSTOM) {
            builder.entityExtractor(customExtractor);
        }

        if (aismeConfig != null) {
            builder.aismeConfig(aismeConfig);
        } else if (memoryProperties.getAisme() != null) {
            builder.aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig.fromProperties(memoryProperties.getAisme()));
        }

        float threshold = memoryProperties.getGraphExpansionThreshold();
        String thresholdStr = System.getProperty("spector.memory.graphExpansionThreshold",
                System.getProperty("spector.benchmark.graphExpansionThreshold",
                System.getProperty("graphExpansionThreshold")));
        if (thresholdStr != null && !thresholdStr.isBlank()) {
            try {
                threshold = Float.parseFloat(thresholdStr);
            } catch (NumberFormatException ignored) {}
        } else if (datasetProps != null) {
            threshold = (float) datasetProps.getDouble("spector.benchmark.retrieval.graph-expansion-threshold",
                        datasetProps.getDouble("retrieval.graph_expansion_threshold",
                        datasetProps.getDouble("graph_expansion_threshold", threshold)));
        }

        com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode expansionMode = com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.resolve();
        if (memoryProperties.getGraphExpansionMode() != null && !memoryProperties.getGraphExpansionMode().isBlank()) {
            try {
                expansionMode = com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.valueOf(
                        memoryProperties.getGraphExpansionMode().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        } else if (datasetProps != null) {
            String expModeStr = datasetProps.getString("spector.benchmark.retrieval.graph-expansion-mode",
                                datasetProps.getString("retrieval.graph-expansion-mode",
                                datasetProps.getString("spector.benchmark.retrieval.graph_expansion_mode",
                                datasetProps.getString("graph-expansion-mode",
                                datasetProps.getString("graph_expansion_mode", null)))));
            if (expModeStr != null && !expModeStr.isBlank()) {
                try {
                    expansionMode = com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.valueOf(expModeStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        boolean aismeEnabled = memoryProperties.getAisme() != null && memoryProperties.getAisme().isEnabled();
        log.info("Instantiating GraphScoringPolicy with threshold={}, mode={}, aismeEnabled={}", threshold, expansionMode, aismeEnabled);
        com.spectrayan.spector.memory.pathway.pipeline.GraphScoringPolicy scoringPolicy = new com.spectrayan.spector.memory.pathway.pipeline.GraphScoringPolicy(
                0.3f,   // causalBoostWeight
                0.45f,  // hebbianBoostFactor (tuned for cross-session associative recall)
                0.85f,  // temporalForwardFactor
                0.75f,  // temporalBackwardFactor
                0.25f,  // entityHopAttenuation
                4,      // hebbianMaxDepth (tuned for deeper cross-session associations)
                5,      // temporalMaxHops  (match resolveDefault — wider session coverage)
                2,      // entityMaxHops
                threshold,
                expansionMode
        );
        builder.graphScoringPolicy(scoringPolicy);

        if (dataset.persona() != null) {
            com.spectrayan.spector.memory.model.SalienceProfile salienceProfile =
                    buildSalienceProfileFromPersona(dataset.persona(), embedder);
            if (salienceProfile != null) {
                builder.salienceProfile(salienceProfile);
                log.info("Configured Persona-Informed SalienceProfile from dataset persona (interests: {}, occupation: '{}')",
                        dataset.persona().interests(), dataset.persona().occupation());
            }
        }

        // Resolve persistence parameters
        boolean useDisk = memoryProperties.getPersistenceMode() == com.spectrayan.spector.config.model.PersistenceMode.DISK
                || Boolean.parseBoolean(System.getProperty("spector.benchmark.persistence", "true"));
        String persistenceModeStr = datasetProps != null ? datasetProps.getString("spector.memory.persistence-mode", null) : null;
        if ("EPHEMERAL".equalsIgnoreCase(System.getProperty("spector.memory.persistence-mode", persistenceModeStr))) {
            useDisk = false;
        } else if ("DISK".equalsIgnoreCase(System.getProperty("spector.memory.persistence-mode", persistenceModeStr))) {
            useDisk = true;
        }
        Path persistencePath = null;
        if (useDisk) {
            String sysPropPath = System.getProperty("spector.memory.persistence-path");
            if (sysPropPath != null && !sysPropPath.isBlank()) {
                persistencePath = Path.of(sysPropPath);
            }
            if (persistencePath == null && datasetDir != null) {
                if (datasetConfig != null && Files.exists(datasetConfig)) {
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

        boolean forceReingest = Boolean.parseBoolean(System.getProperty("spector.benchmark.forceReingest",
                System.getProperty("forceReingest", "false")));
        if (forceReingest && persistencePath != null && Files.exists(persistencePath)) {
            log.info("Force reingestion enabled; purging existing persisted memory at {}", persistencePath);
            try (var s = Files.walk(persistencePath)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception e) {
                log.warn("Failed to clean persistence path {}: {}", persistencePath, e.getMessage());
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

        // Map memory ID -> Extracted Entity Names from entity relations for synaptic tag transduction
        Map<String, Set<String>> memoryIdToEntityTags = new HashMap<>();
        if (dataset.entityRelations() != null) {
            for (com.spectrayan.spector.bench.cognitive.model.EntityRelation rel : dataset.entityRelations()) {
                if (rel.sourceMemoryIds() != null) {
                    for (String memId : rel.sourceMemoryIds()) {
                        Set<String> entTags = memoryIdToEntityTags.computeIfAbsent(memId, k -> new HashSet<>());
                        if (rel.fromEntity() != null && rel.fromEntity().name() != null && !rel.fromEntity().name().isBlank()) {
                            entTags.add(rel.fromEntity().name().toLowerCase().trim());
                        }
                        if (rel.toEntity() != null && rel.toEntity().name() != null && !rel.toEntity().name().isBlank()) {
                            entTags.add(rel.toEntity().name().toLowerCase().trim());
                        }
                    }
                }
            }
        }

        Map<String, Set<String>> memIdToEffectiveTags = new HashMap<>();
        for (BenchmarkCorpusRecord record : corpus) {
            Set<String> tags = new LinkedHashSet<>();
            if (record.synapticTags() != null) {
                for (String t : record.synapticTags()) {
                    if (t != null && !t.isBlank()) {
                        tags.add(t.toLowerCase().trim());
                    }
                }
            }
            Set<String> entTags = memoryIdToEntityTags.get(record.id());
            if (entTags != null) {
                tags.addAll(entTags);
            }
            memIdToEffectiveTags.put(record.id(), tags);
        }

        if (memory.totalMemories() > 0) {
            isDiskLoaded = true;
            log.info("Discovered pre-ingested memories on disk. Reconstructing slot mappings.");
            for (BenchmarkCorpusRecord record : corpus) {
                var loc = memory.admin().index().locate(record.id());
                if (loc != null) {
                    idToSlot.put(record.id(), loc.graphSlot());
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

                    Set<String> effectiveTags = memIdToEffectiveTags.getOrDefault(record.id(), Set.of());

                    MemorySource source = MemorySource.OBSERVED;
                    if (record.text() != null) {
                        if (record.text().startsWith("user:") || record.text().startsWith("User:")) {
                            source = MemorySource.USER_STATED;
                        } else if (record.text().startsWith("assistant:") || record.text().startsWith("Assistant:")) {
                            source = MemorySource.INFERRED;
                        }
                    }

                    memory.remember(
                            record.id(),
                            record.text(),
                            record.memoryType(),
                            source,
                            context,
                            effectiveTags.toArray(String[]::new)
                    );

                    idToSlot.put(record.id(), slot);
                    slot++;
                } catch (Exception e) {
                    log.warn("Failed to ingest corpus record '{}': {}", record.id(), e.getMessage());
                }
            }
            log.info("Ingested {} of {} corpus records", idToSlot.size(), corpusSize);
            // Reconstruct true slot mappings from index after ingestion
            idToSlot.clear();
            for (BenchmarkCorpusRecord record : corpus) {
                var loc = memory.admin().index().locate(record.id());
                if (loc != null) {
                    idToSlot.put(record.id(), loc.graphSlot());
                }
            }
        }

        // Store for external access (subsystem contribution detection)
        this.idToSlot = idToSlot;

        // Load graph structures from dataset definitions (null-safe: subsystems may be unconfigured)
        if (!isDiskLoaded) {
            var graph = memory.admin().graph();
            var hg = graph != null ? graph.rawHebbianGraph() : null;
            var tc = graph != null ? graph.rawTemporalChain() : null;
            var hyper = graph != null ? graph.rawHyperEntityGraph() : null;
            var coact = memory.admin().coActivation();

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
                loadEntityGraph(memory.admin().entityDirectory(), hyper, memory.admin().temporalKnowledgeGraph(), dataset.entityRelations(), idToSlot, dataset.corpus());
            } else {
                log.warn("EntityGraph is null  --  skipping {} entity relation definitions", dataset.entityRelations().size());
            }
            if (coact != null) {
                loadCoActivationMemory(coact, dataset, idToSlot, memIdToEffectiveTags);
            } else {
                log.warn("CoActivationMemory is null  --  skipping co-activation ingestion");
            }
            log.info("Benchmark memory setup complete: hebbian edges={}, temporal chains={}, entity relations={}",
                    dataset.hebbianEdges().size(), dataset.temporalChains().size(),
                    dataset.entityRelations().size());
        } else {
            var coact = memory.admin().coActivation();
            if (coact != null) {
                loadCoActivationMemory(coact, dataset, idToSlot, memIdToEffectiveTags);
            }
            log.info("Loaded pre-ingested graph structures from disk. Populated co-activation tables.");
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

    private static int offsetToRecordIndex(com.spectrayan.spector.memory.cortex.index.MemoryIndex.MemoryLocation loc, SpectorMemory memory) {
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
    /**
     * Populates the EntityGraph from entities.jsonl definitions.
     *
     * <p>Constructs typed entity nodes and typed edges matching specified relation types.
     * Links entities to their source memory indices and populates the TemporalKnowledgeGraph.</p>
     *
     * @param dir       the entity directory to populate
     * @param hyper     the hyperentity graph memory
     * @param tkg       the temporal knowledge graph (nullable)
     * @param relations entity relation definitions from the dataset
     * @param idToSlot  mapping from corpus record IDs to their slot indices
     * @param corpus    the corpus records (used for timestamp mapping)
     */
    void loadEntityGraph(EntityDirectory dir, HyperEntityGraphMemory hyper,
                         com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph tkg,
                         List<EntityRelation> relations,
                         Map<String, Integer> idToSlot,
                         List<BenchmarkCorpusRecord> corpus) {
        int relationsLoaded = 0;
        int tkgFactsLoaded = 0;

        Map<String, Long> corpusTimestamps = new HashMap<>();
        if (corpus != null) {
            for (BenchmarkCorpusRecord r : corpus) {
                if (r.id() != null && r.timestampMs() > 0) {
                    corpusTimestamps.put(r.id(), r.timestampMs());
                }
            }
        }

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
            long earliestTimestamp = 0L;
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
                Long ts = corpusTimestamps.get(memoryId);
                if (ts != null && (earliestTimestamp == 0L || ts < earliestTimestamp)) {
                    earliestTimestamp = ts;
                }
            }

            // Populate TemporalKnowledgeGraph with Bi-Temporal facts
            if (tkg != null && relation.relationType() != null && !relation.relationType().isBlank()) {
                try {
                    tkg.assertFact(
                            fromEntityId,
                            relation.relationType(),
                            toEntityId,
                            -1L, (short) 0,
                            earliestTimestamp,
                            Long.MAX_VALUE,
                            1.0f,
                            false
                    );
                    tkgFactsLoaded++;
                } catch (Exception e) {
                    log.debug("Failed to assert temporal fact for relation '{}': {}", relation.relationType(), e.getMessage());
                }
            }
        }

        log.info("Loaded {} entity relations (hyperedges={}) and {} bitemporal facts into TKG (entities={})",
                relations.size(), relationsLoaded, tkgFactsLoaded, dir.entityCount());
    }

    /**
     * Populates CoActivationMemory from dataset definitions:
     * 1. Undirected tag co-occurrence pairs from Hebbian edges.
     * 2. Directed STDP causal transitions from Temporal chains.
     * 3. Reciprocal Neocortical semantic-to-episodic tag bridges.
     * 4. Rebuilds the Tag -> Memory Slot Inverted Index for Cross-Capture Graph traversal.
     */
    void loadCoActivationMemory(CoActivationMemory coact, DatasetLoader.LoadedDataset dataset,
                                Map<String, Integer> idToSlot,
                                Map<String, Set<String>> memIdToTags) {
        if (coact == null) return;

        int pairsLoaded = 0;
        int stdpLoaded = 0;

        // 1. Undirected Co-Activation Pairs from Hebbian Edges
        if (dataset.hebbianEdges() != null) {
            for (HebbianEdgeDef edge : dataset.hebbianEdges()) {
                Set<String> tagsA = memIdToTags.get(edge.memoryIdA());
                Set<String> tagsB = memIdToTags.get(edge.memoryIdB());
                if (tagsA != null && tagsB != null && !tagsA.isEmpty() && !tagsB.isEmpty()) {
                    int count = Math.clamp(edge.coActivationCount(), 1, 20);
                    for (String tA : tagsA) {
                        for (String tB : tagsB) {
                            if (!tA.equalsIgnoreCase(tB)) {
                                for (int c = 0; c < count; c++) {
                                    coact.recordCoActivation(tA, tB);
                                }
                                pairsLoaded++;
                            }
                        }
                    }
                }
            }
        }

        // 2. Directed STDP Plasticity Transitions from Temporal Chains
        if (dataset.temporalChains() != null && dataset.corpus() != null) {
            Map<String, BenchmarkCorpusRecord> corpusById = dataset.corpus().stream()
                    .collect(Collectors.toMap(BenchmarkCorpusRecord::id, r -> r, (a, b) -> a));

            for (TemporalChainDef chain : dataset.temporalChains()) {
                List<String> orderedIds = chain.orderedMemoryIds();
                for (int i = 0; i < orderedIds.size() - 1; i++) {
                    String idBefore = orderedIds.get(i);
                    String idAfter = orderedIds.get(i + 1);
                    BenchmarkCorpusRecord recBefore = corpusById.get(idBefore);
                    BenchmarkCorpusRecord recAfter = corpusById.get(idAfter);
                    if (recBefore != null && recAfter != null) {
                        Set<String> tagsBefore = memIdToTags.get(idBefore);
                        Set<String> tagsAfter = memIdToTags.get(idAfter);
                        long timeBefore = recBefore.timestampMs();
                        long timeAfter = recAfter.timestampMs();
                        if (tagsBefore != null && tagsAfter != null && timeAfter >= timeBefore) {
                            for (String tBefore : tagsBefore) {
                                for (String tAfter : tagsAfter) {
                                    if (!tBefore.equalsIgnoreCase(tAfter)) {
                                        coact.recordSequentialActivation(tBefore, tAfter, timeBefore, timeAfter);
                                        stdpLoaded++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Reciprocal Neocortical Semantic-to-Episodic Tag Bridge
        int bridges = 0;
        if (dataset.corpus() != null) {
            for (BenchmarkCorpusRecord record : dataset.corpus()) {
                if (record.id().startsWith("fact_")) {
                    int lastUnder = record.id().lastIndexOf('_');
                    if (lastUnder > 5) {
                        String sessionKey = record.id().substring(5, lastUnder);
                        for (BenchmarkCorpusRecord turnRec : dataset.corpus()) {
                            if (!turnRec.id().startsWith("fact_") && turnRec.id().contains("_D")) {
                                int dIdx = turnRec.id().indexOf("_D");
                                int turnLastUnder = turnRec.id().lastIndexOf('_');
                                if (dIdx > 0 && turnLastUnder > dIdx) {
                                    String turnSess = turnRec.id().substring(0, dIdx) + "_sess_" + turnRec.id().substring(dIdx + 2, turnLastUnder);
                                    if (sessionKey.equals(turnSess)) {
                                        Set<String> factTags = memIdToTags.get(record.id());
                                        Set<String> turnTags = memIdToTags.get(turnRec.id());
                                        if (factTags != null && turnTags != null) {
                                            for (String ft : factTags) {
                                                for (String tt : turnTags) {
                                                    if (!ft.equalsIgnoreCase(tt)) {
                                                        coact.recordCoActivation(ft, tt);
                                                        bridges++;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Rebuild Tag -> Memory Slot Inverted Index for Cross-Capture Graph Traversal
        if (dataset.corpus() != null) {
            Map<Integer, java.util.Collection<String>> slotToTags = new HashMap<>();
            for (BenchmarkCorpusRecord record : dataset.corpus()) {
                Integer slot = idToSlot.get(record.id());
                if (slot != null) {
                    Set<String> tags = memIdToTags.get(record.id());
                    if (tags != null && !tags.isEmpty()) {
                        slotToTags.put(slot, tags);
                    }
                }
            }
            coact.rebuildInvertedIndex(slotToTags);
        }

        log.info("CoActivationMemory loaded: pairs={}, STDP edges={}, inverted index tags={}, bridges={}",
                coact.pairCount(), coact.edgeCount(), coact.invertedIndexTagCount(), bridges);
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

    private com.spectrayan.spector.memory.model.SalienceProfile buildSalienceProfileFromPersona(
            com.spectrayan.spector.bench.cognitive.model.PersonaDef persona,
            EmbeddingProvider embedder) {
        if (persona == null) return null;

        var profileBuilder = com.spectrayan.spector.memory.model.SalienceProfile.builder();

        // 1. Add semantic interest domains with pre-computed embeddings
        if (persona.interests() != null) {
            for (String interest : persona.interests()) {
                if (interest != null && !interest.isBlank()) {
                    try {
                        float[] interestVec = embedder.embed(interest).vector();
                        profileBuilder.interest(new com.spectrayan.spector.memory.model.InterestDomain(
                                interest,
                                com.spectrayan.spector.memory.model.InterestLevel.HIGH,
                                interestVec
                        ));
                    } catch (Exception e) {
                        log.warn("Failed to embed persona interest '{}': {}", interest, e.getMessage());
                    }
                }
            }
        }

        // 2. Build PersonaContext for mPFC self-relevance scoring
        var personaCtxBuilder = com.spectrayan.spector.memory.model.PersonaContext.builder();
        if (persona.lifeContext() != null && !persona.lifeContext().isBlank()) {
            personaCtxBuilder.about(persona.lifeContext());
            try {
                personaCtxBuilder.aboutEmbedding(embedder.embed(persona.lifeContext()).vector());
            } catch (Exception ignored) {}
        }
        if (persona.occupation() != null && !persona.occupation().isBlank()) {
            personaCtxBuilder.occupation(persona.occupation());
            try {
                personaCtxBuilder.occupationEmbedding(embedder.embed(persona.occupation()).vector());
            } catch (Exception ignored) {}
        }
        if (persona.personalityTraits() != null && !persona.personalityTraits().isEmpty()) {
            String traitsText = String.join(", ", persona.personalityTraits());
            try {
                personaCtxBuilder.valuesEmbedding(embedder.embed(traitsText).vector());
            } catch (Exception ignored) {}
        }

        profileBuilder.persona(personaCtxBuilder.build());
        return profileBuilder.build();
    }
}
