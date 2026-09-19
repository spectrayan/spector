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
package com.spectrayan.spector.synapse.config.service;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ConfigFieldDescriptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.spectrayan.spector.synapse.config.model.ConfigFieldDescriptor.*;

/**
 * Registry of configuration field metadata and runtime mutability classifications (ADR-0085).
 * Generates descriptors hydrated directly from the active {@link SpectorProperties} snapshot.
 */
@Service
public class ConfigSchemaRegistry {

    private final SpectorProperties configSnapshot;

    @Autowired
    public ConfigSchemaRegistry(ConfigResolutionService resolutionService) {
        this(resolutionService != null ? resolutionService.configSnapshot() : null);
    }

    public ConfigSchemaRegistry(SpectorProperties configSnapshot) {
        this.configSnapshot = configSnapshot != null ? configSnapshot : SpectorProperties.builder().build();
    }

    public ConfigSchemaRegistry() {
        this((SpectorProperties) null);
    }

    /**
     * Returns the list of field descriptors for the given category.
     */
    public List<ConfigFieldDescriptor> getSchema(ConfigCategory category) {
        return switch (category) {
            case MEMORY -> memorySchema();
            case RECALL, RAG -> recallSchema();
            case HNSW -> hnswSchema();
            case SPECTRUM -> spectrumSchema();
            case LLM_PROVIDER -> llmSchema();
            case EMBEDDING_PROVIDER -> embeddingSchema();
            case INGESTION -> ingestionSchema();
            case MULTIMODAL -> multimodalSchema();
            case TELEMETRY -> telemetrySchema();
            case CONCURRENCY -> concurrencySchema();
            case SALIENCE -> salienceSchema();
            case SOUL -> soulSchema();
        };
    }

    /**
     * Determines the applyMode ("LIVE", "POLICY", "REBUILD", "BOOT") for a given category and key.
     * Fails closed to "BOOT" if unmapped.
     */
    public String getApplyMode(ConfigCategory category, String key) {
        List<ConfigFieldDescriptor> fields = getSchema(category);
        for (ConfigFieldDescriptor field : fields) {
            if (field.key().equalsIgnoreCase(key)) {
                return field.applyMode();
            }
        }
        return "BOOT";
    }

    private List<ConfigFieldDescriptor> memorySchema() {
        var rem = configSnapshot.memory() != null ? configSnapshot.memory().getRemember() : null;
        var graph = configSnapshot.memory() != null ? configSnapshot.memory().getGraph() : null;
        var hebbian = graph != null ? graph.getHebbian() : null;
        var decay = configSnapshot.memory() != null ? configSnapshot.memory().getDecay() : null;
        var circadian = configSnapshot.memory() != null ? configSnapshot.memory().getCircadian() : null;
        var vacuum = configSnapshot.memory() != null ? configSnapshot.memory().getVacuum() : null;

        return List.of(
                bool("decay-enabled", decay == null || decay.getMinThreshold() > 0.0,
                        "Enable power-law memory decay", "LIVE"),
                number("surprise-warmup", rem != null ? rem.getSurpriseWarmup() : 10,
                        "Surprise warmup query count", "LIVE", 0, 100, 1),
                number("flashbulb-threshold", rem != null ? rem.getFlashbulbThreshold() : 3.0f,
                        "Salience threshold for flashbulb permanence", "LIVE", 0.0, 10.0, 0.1),
                number("valence-learning-rate", rem != null ? rem.getValenceLearningRate() : 0.3f,
                        "Rate of emotional valence adaptation", "LIVE", 0.0, 1.0, 0.05),
                number("deduplication-radius", rem != null ? rem.getDeduplicationRadius() : 0.05f,
                        "Vector distance radius for near-duplicate rejection", "LIVE", 0.0, 1.0, 0.01),
                number("inhibition-ttl-ms", rem != null ? rem.getInhibitionTtlMs() : 300000L,
                        "Latent inhibition suppression TTL in milliseconds", "LIVE", 1000, 3600000, 1000),
                number("habituation-decay-rate", rem != null ? rem.getHabituationDecayRate() : 0.2f,
                        "Habituation decay factor for repeated non-salient events", "LIVE", 0.0, 1.0, 0.05),
                number("ltp-cooldown-ms", rem != null ? rem.getLtpCooldownMs() : 300000L,
                        "Long-term potentiation cooldown window in milliseconds", "LIVE", 1000, 3600000, 1000),
                number("hebbian-max-degree", hebbian != null ? hebbian.getMaxDegree() : 24,
                        "Maximum associative fan-out degree per memory engram", "LIVE", 1, 128, 1),
                number("hebbian-decay-factor", hebbian != null ? (float) hebbian.getDecayFactor() : 0.9f,
                        "Synaptic weight decay factor per consolidation cycle", "LIVE", 0.0, 1.0, 0.05),
                select("graph-expansion-mode", graph != null && graph.getExpansionMode() != null ? graph.getExpansionMode() : "GATED",
                        "Associative graph traversal expansion policy", "LIVE", List.of("OFF", "GATED", "GREEDY")),
                number("circadian-volume-trigger", circadian != null ? circadian.getVolumeTrigger() : 100,
                        "Engram count trigger to schedule offline circadian consolidation", "LIVE", 10, 10000, 10),
                number("vacuum-threshold", vacuum != null ? (float) vacuum.getThreshold() : 0.20f,
                        "Dead memory ratio threshold triggering background vacuum compaction", "LIVE", 0.0, 1.0, 0.05),
                number("capacity", configSnapshot.memory() != null ? configSnapshot.memory().getCapacity() : 100000,
                        "Total memory engram capacity", "BOOT", 1000, 10000000, 1000),
                select("persistence-mode", configSnapshot.memory() != null && configSnapshot.memory().getPersistenceMode() != null ? configSnapshot.memory().getPersistenceMode().name().toLowerCase() : "mmap",
                        "Memory persistence storage mode", "BOOT", List.of("mmap", "off_heap", "in_memory")),
                number("dimensions", configSnapshot.memory() != null ? configSnapshot.memory().getDimensions() : 384,
                        "Vector dimensionality", "BOOT", 32, 4096, 1)
        );
    }

    private List<ConfigFieldDescriptor> recallSchema() {
        var recall = configSnapshot.memory() != null ? configSnapshot.memory().getRecall() : null;
        var text = recall != null ? recall.getTextSearch() : null;
        var mmr = recall != null ? recall.getMmr() : null;
        var lat = recall != null ? recall.getLateral() : null;
        var val = recall != null ? recall.getValenceAlignment() : null;

        return List.of(
                select("scoring-mode", recall != null && recall.getScoringMode() != null ? recall.getScoringMode() : "COGNITIVE",
                        "Fused cognitive scoring algorithm mode", "POLICY", List.of("COGNITIVE", "COSINE", "HYBRID")),
                select("score-fusion-mode", recall != null && recall.getScoreFusionMode() != null ? recall.getScoreFusionMode() : "MULTIPLICATIVE",
                        "Multi-factor score combination strategy", "POLICY", List.of("MULTIPLICATIVE", "HARMONIC", "WEIGHTED_SUM")),
                number("strictness-coefficient", recall != null ? recall.getStrictnessCoefficient() : 1.0f,
                        "Scoring filter strictness exponent", "POLICY", 0.1, 5.0, 0.1),
                select("mode", recall != null && recall.getMode() != null ? recall.getMode() : "LEARN",
                        "Default operational recall mode", "POLICY", List.of("LEARN", "SEARCH", "STRICT")),
                select("engine", recall != null && recall.getEngine() != null ? recall.getEngine() : "pathway",
                        "Cognitive query execution engine", "POLICY", List.of("pathway", "direct", "auto")),
                select("text-search.mode", text != null && text.getMode() != null ? text.getMode() : "BM25",
                        "Hybrid lexical-semantic text retrieval mode", "POLICY", List.of("OFF", "BM25", "SPLADE", "COLBERT")),
                number("text-search.gamma", 0.3,
                        "Lexical BM25 weight relative to vector score", "POLICY", 0.0, 1.0, 0.05),
                bool("mmr.enabled", mmr != null && mmr.isEnabled(),
                        "Maximal Marginal Relevance diversity reranking", "POLICY"),
                number("mmr.lambda", mmr != null ? (double) mmr.getLambda() : 0.5,
                        "MMR relevance vs diversity balance", "POLICY", 0.0, 1.0, 0.05),
                bool("lateral.mode", lat != null && lat.isEnabled(),
                        "Neurodivergent lateral divergent thinking recall", "POLICY"),
                number("lateral.distance-threshold", lat != null ? (double) lat.getDistanceThreshold() : 0.7,
                        "Maximum distance threshold for lateral exploration", "POLICY", 0.1, 1.0, 0.05),
                number("lateral.max-results", 5,
                        "Max lateral divergent results", "POLICY", 1, 50, 1),
                bool("valence-alignment.enabled", val != null && val.isEnabled(),
                        "Valence congruence state-dependent recall", "POLICY"),
                bool("trace-enabled", recall != null && recall.isTraceEnabled(),
                        "Detailed cognitive scoring execution tracing", "POLICY"),
                number("top-k", SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K,
                        "Default candidate retrieval limit", "POLICY", 1, 100, 1),
                number("similarity-threshold", configSnapshot.memory() != null ? (double) configSnapshot.memory().getGraphExpansionThreshold() : 0.7,
                        "Minimum semantic similarity threshold", "POLICY", 0.0, 1.0, 0.05)
        );
    }

    private List<ConfigFieldDescriptor> hnswSchema() {
        var hnsw = configSnapshot.hnsw();
        return List.of(
                number("ef-search", hnsw != null ? hnsw.getEfSearch() : 64,
                        "Query-time dynamic candidate exploration list size", "LIVE", 8, 512, 8),
                number("m", hnsw != null ? hnsw.getM() : 16,
                        "Number of bidirectional links per vector node", "REBUILD", 4, 64, 4),
                number("ef-construction", hnsw != null ? hnsw.getEfConstruction() : 128,
                        "Index construction candidate exploration depth", "REBUILD", 16, 512, 16)
        );
    }

    private List<ConfigFieldDescriptor> spectrumSchema() {
        var spec = configSnapshot.spectrum();
        return List.of(
                number("n-probe", spec != null ? spec.getNProbe() : 8,
                        "Centroid clusters to probe during retrieval", "LIVE", 1, 64, 1),
                number("oversampling-factor", spec != null ? spec.getOversamplingFactor() : 2.0,
                        "Candidate oversampling multiplier before reranking", "LIVE", 1.0, 10.0, 0.5),
                number("n-centroids", spec != null ? spec.getNCentroids() : 256,
                        "Number of k-means Voronoi partitioning centroids", "REBUILD", 16, 4096, 16),
                number("shard-threshold", spec != null ? spec.getShardThreshold() : 100000,
                        "Vector count before sharding clusters", "REBUILD", 1000, 10000000, 1000),
                number("kmeans-iterations", spec != null ? spec.getKmeansIterations() : 20,
                        "Maximum k-means centroid clustering iterations", "REBUILD", 5, 100, 1)
        );
    }

    private List<ConfigFieldDescriptor> llmSchema() {
        var gen = configSnapshot.provider() != null ? configSnapshot.provider().getGeneration() : null;
        double temp = 0.7;
        if (gen != null && gen.getProperties() != null && gen.getProperties().containsKey("temperature")) {
            try {
                temp = Double.parseDouble(gen.getProperties().get("temperature"));
            } catch (NumberFormatException ignored) {}
        }
        return List.of(
                select("provider", gen != null && gen.getType() != null ? gen.getType() : "ollama",
                        "Primary LLM chat generation provider", "POLICY",
                        List.of("ollama", "google", "openai", "anthropic", "custom")),
                of("model", gen != null && gen.getModel() != null ? gen.getModel() : "llama3.2",
                        "string", "Generation model name", "POLICY"),
                of("base-url", gen != null && gen.getBaseUrl() != null ? gen.getBaseUrl() : "http://localhost:11434",
                        "string", "API endpoint base URL", "POLICY"),
                number("temperature", temp, "Generation temperature", "POLICY", 0.0, 2.0, 0.05),
                secret("api-key", gen != null && gen.getApiKey() != null ? gen.getApiKey() : "",
                        "Provider API key or bearer credential", "POLICY"),
                number("timeout", 30,
                        "Request timeout in seconds", "POLICY", 1, 300, 1),
                of("fallback-model", "", "string", "Fallback model name on rate-limit or error", "POLICY")
        );
    }

    private List<ConfigFieldDescriptor> embeddingSchema() {
        var emb = configSnapshot.provider() != null ? configSnapshot.provider().getEmbedding() : null;
        return List.of(
                select("provider", emb != null && emb.getType() != null ? emb.getType() : "onnx",
                        "Primary text embedding provider", "POLICY",
                        List.of("onnx", "ollama", "google", "openai", "huggingface")),
                of("model", emb != null && emb.getModel() != null ? emb.getModel() : "all-minilm-l6-v2-q",
                        "string", "Embedding model name", "POLICY"),
                of("base-url", emb != null && emb.getBaseUrl() != null ? emb.getBaseUrl() : "http://localhost:11434",
                        "string", "API endpoint base URL", "POLICY"),
                number("dimensions", configSnapshot.memory() != null ? configSnapshot.memory().getDimensions() : 384,
                        "Vector embedding dimensionality", "REBUILD", 32, 4096, 1),
                number("batch-size", emb != null ? emb.getBatchSize() : 32,
                        "Max batch embedding request size", "POLICY", 1, 256, 1),
                number("max-retries", emb != null ? emb.getMaxRetries() : 3,
                        "Maximum retry attempts on provider failure", "POLICY", 0, 10, 1),
                bool("cache.enabled", emb == null || emb.isCacheEnabled(),
                        "Enable embedding vector response caching", "POLICY")
        );
    }

    private List<ConfigFieldDescriptor> ingestionSchema() {
        var chunk = configSnapshot.memory() != null && configSnapshot.memory().getRemember() != null
                ? configSnapshot.memory().getRemember().getChunk() : null;
        var ing = configSnapshot.ingestion();
        return List.of(
                number("chunk-size", chunk != null && chunk.getSize() > 0 ? chunk.getSize() : 2500,
                        "Maximum chunk size in characters", "LIVE", 100, 10000, 50),
                number("chunk-overlap", chunk != null && chunk.getOverlap() >= 0 ? chunk.getOverlap() : 200,
                        "Overlapping character count between chunks", "LIVE", 0, 2000, 10),
                bool("parent-child-linking", false,
                        "Enable parent-child chunk mapping for documents", "LIVE"),
                of("file-pattern", ing != null && ing.getFilePattern() != null ? ing.getFilePattern() : "**/*.md,**/*.txt",
                        "string", "Glob patterns for file ingestion", "LIVE"),
                of("skip-dirs", ing != null && ing.getSkipDirs() != null ? ing.getSkipDirs() : ".git,node_modules,target",
                        "string", "Comma-separated directory names to ignore", "LIVE"),
                number("parallelism", ing != null ? ing.getParallelism() : 4,
                        "Parallel ingestion worker thread count", "POLICY", 1, 32, 1)
        );
    }

    private List<ConfigFieldDescriptor> multimodalSchema() {
        var mm = configSnapshot.multimodal();
        return List.of(
                bool("enabled", mm != null && mm.isEnabled(),
                        "Enable multimodal sensory ingest and cross-modal recall", "POLICY"),
                of("vision-model", mm != null && mm.getVisionModel() != null ? mm.getVisionModel() : "clip-vit-base-patch32",
                        "string", "Vision encoder model name", "POLICY"),
                of("audio-model", mm != null && mm.getAudioModel() != null ? mm.getAudioModel() : "whisper-tiny",
                        "string", "Audio transcription model name", "POLICY"),
                number("keyframe-interval-seconds", 5,
                        "Video keyframe extraction interval in seconds", "POLICY", 1, 60, 1),
                select("asset-store.type", mm != null && mm.getAssetStoreType() != null ? mm.getAssetStoreType() : "local",
                        "Multimodal binary asset storage backend", "BOOT", List.of("local", "s3", "blob"))
        );
    }

    private List<ConfigFieldDescriptor> telemetrySchema() {
        var tel = configSnapshot.telemetry();
        return List.of(
                number("sample-ratio", tel != null ? tel.getQuerySampleRate() : 1.0,
                        "Tracing and observation sampling ratio (0.0 to 1.0)", "LIVE", 0.0, 1.0, 0.05),
                bool("enabled", tel == null || tel.isEnabled(),
                        "Enable OpenTelemetry and Micrometer monitoring", "BOOT"),
                select("exporter", "prometheus",
                        "Metrics and traces exporter format", "BOOT", List.of("prometheus", "otlp", "logging")),
                bool("tracing-enabled", true,
                        "Enable distributed span tracing across cognitive queries", "BOOT")
        );
    }

    private List<ConfigFieldDescriptor> concurrencySchema() {
        var conc = configSnapshot.concurrency();
        return List.of(
                bool("virtual-threads.enabled", conc == null || conc.isStructured(),
                        "Enable JDK Virtual Threads for IO tasks", "BOOT"),
                number("pool-size", 16,
                        "Worker thread pool size for background tasks", "BOOT", 2, 256, 2)
        );
    }

    private List<ConfigFieldDescriptor> salienceSchema() {
        return List.of(
                number("alpha", 0.6, "Salience alpha blending parameter", "POLICY", 0.0, 1.0, 0.05),
                number("beta", 0.4, "Salience beta novelty parameter", "POLICY", 0.0, 1.0, 0.05)
        );
    }

    private List<ConfigFieldDescriptor> soulSchema() {
        return List.of(
                of("archetype", "Analyst", "string", "Cognitive agent archetype / persona", "POLICY"),
                of("description", "", "string", "Agent persona identity definition", "POLICY")
        );
    }
}
