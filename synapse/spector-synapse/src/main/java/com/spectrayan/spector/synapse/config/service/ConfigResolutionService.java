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

import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ConfigOverridePolicy;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the effective configuration for a given tenant/user by merging
 * the hierarchical override chain: system → tenant → user.
 */
@Service
public class ConfigResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ConfigResolutionService.class);

    private final ConfigRepository repository;
    private volatile ConfigOverridePolicy policy;
    private final com.spectrayan.spector.config.SpectorProperties configSnapshot;

    public ConfigResolutionService(ConfigRepository repository) {
        this.repository = repository;
        this.policy = ConfigOverridePolicy.DEFAULT;
        this.configSnapshot = com.spectrayan.spector.config.SpectorProperties.load();
        log.info("ConfigResolutionService initialized");
    }

    public void setPolicy(ConfigOverridePolicy policy) {
        this.policy = policy != null ? policy : ConfigOverridePolicy.DEFAULT;
    }

    public ConfigOverridePolicy policy() {
        return policy;
    }

    public com.spectrayan.spector.config.SpectorProperties configSnapshot() {
        return configSnapshot;
    }

    /**
     * Resolves the effective configuration for a category, merging
     * system → tenant → user overrides.
     */
    public Map<String, Object> resolve(String tenantId, String userId,
                                        ConfigCategory category) {
        Map<String, Object> effective = new LinkedHashMap<>(systemDefaults(category));

        // Tenant overrides
        if (tenantId != null && !tenantId.isBlank()
                && policy.isTenantOverridable(category)) {
            repository.get("tenant:" + tenantId, category).ifPresent(tenantConfig -> {
                mergeOverrides(effective, tenantConfig.values());
                log.trace("[Resolve] Applied tenant override for {}: {} keys",
                        category.key(), tenantConfig.values().size());
            });
        }

        // User overrides
        if (userId != null && !userId.isBlank() && tenantId != null
                && policy.isUserOverridable(category)) {
            String userScope = "user:" + tenantId + ":" + userId;
            repository.get(userScope, category).ifPresent(userConfig -> {
                mergeOverrides(effective, userConfig.values());
                log.trace("[Resolve] Applied user override for {}: {} keys",
                        category.key(), userConfig.values().size());
            });
        }

        return effective;
    }

    /**
     * Resolves with source annotations for UI override badges.
     */
    public Map<String, AnnotatedValue> resolveAnnotated(String tenantId, String userId,
                                                         ConfigCategory category) {
        Map<String, Object> systemDefaults = systemDefaults(category);
        Map<String, AnnotatedValue> annotated = new LinkedHashMap<>();
        systemDefaults.forEach((k, v) -> annotated.put(k, new AnnotatedValue(v, "system")));

        if (tenantId != null && !tenantId.isBlank()
                && policy.isTenantOverridable(category)) {
            repository.get("tenant:" + tenantId, category).ifPresent(tenantConfig ->
                    tenantConfig.values().forEach((k, v) -> {
                        if (v != null) annotated.put(k, new AnnotatedValue(v, "tenant"));
                    })
            );
        }

        if (userId != null && !userId.isBlank() && tenantId != null
                && policy.isUserOverridable(category)) {
            repository.get("user:" + tenantId + ":" + userId, category).ifPresent(userConfig ->
                    userConfig.values().forEach((k, v) -> {
                        if (v != null) annotated.put(k, new AnnotatedValue(v, "user"));
                    })
            );
        }

        return annotated;
    }

    /**
     * Saves a scoped config override after validating the override policy.
     */
    public void saveOverride(ScopedConfig config) {
        String scopeLevel = config.scopeLevel();
        if (!policy.isOverridable(scopeLevel, config.category())) {
            throw new IllegalArgumentException(String.format(
                    "Scope '%s' is not allowed to override category '%s' by current policy",
                    scopeLevel, config.category().key()));
        }
        repository.save(config);
    }

    /**
     * Removes a scoped config override.
     */
    public boolean removeOverride(String scope, ConfigCategory category) {
        return repository.delete(scope, category);
    }

    // ── System Defaults (projected from SpectorProperties aggregate snapshot) ──

    private Map<String, Object> systemDefaults(ConfigCategory category) {
        return switch (category) {
            case MEMORY -> memoryDefaults();
            case RECALL -> recallDefaults();
            case RAG -> ragDefaults();
            case HNSW -> hnswDefaults();
            case SPECTRUM -> spectrumDefaults();
            case LLM_PROVIDER -> llmDefaults();
            case EMBEDDING_PROVIDER -> embeddingDefaults();
            case INGESTION -> ingestionDefaults();
            case MULTIMODAL -> multimodalDefaults();
            case TELEMETRY -> telemetryDefaults();
            case CONCURRENCY -> concurrencyDefaults();
            case SALIENCE -> salienceDefaults();
            case SOUL -> soulDefaults();
        };
    }

    private Map<String, Object> memoryDefaults() {
        var rem = configSnapshot.memory() != null ? configSnapshot.memory().getRemember() : null;
        var graph = configSnapshot.memory() != null ? configSnapshot.memory().getGraph() : null;
        var hebbian = graph != null ? graph.getHebbian() : null;
        var decay = configSnapshot.memory() != null ? configSnapshot.memory().getDecay() : null;
        var circadian = configSnapshot.memory() != null ? configSnapshot.memory().getCircadian() : null;
        var vacuum = configSnapshot.memory() != null ? configSnapshot.memory().getVacuum() : null;

        var map = new LinkedHashMap<String, Object>();
        map.put("decay-enabled", decay == null || decay.getMinThreshold() > 0.0);
        map.put("surprise-warmup", rem != null ? rem.getSurpriseWarmup() : 10);
        map.put("flashbulb-threshold", rem != null ? (double) rem.getFlashbulbThreshold() : 3.0);
        map.put("valence-learning-rate", rem != null ? (double) rem.getValenceLearningRate() : 0.3);
        map.put("deduplication-radius", rem != null ? (double) rem.getDeduplicationRadius() : 0.05);
        map.put("inhibition-ttl-ms", rem != null ? rem.getInhibitionTtlMs() : 300000L);
        map.put("habituation-decay-rate", rem != null ? (double) rem.getHabituationDecayRate() : 0.2);
        map.put("ltp-cooldown-ms", rem != null ? rem.getLtpCooldownMs() : 300000L);
        map.put("hebbian-max-degree", hebbian != null ? hebbian.getMaxDegree() : 24);
        map.put("hebbian-decay-factor", hebbian != null ? (double) hebbian.getDecayFactor() : 0.9);
        map.put("graph-expansion-mode", graph != null && graph.getExpansionMode() != null ? graph.getExpansionMode() : "GATED");
        map.put("circadian-volume-trigger", circadian != null ? circadian.getVolumeTrigger() : 100);
        map.put("vacuum-threshold", vacuum != null ? (double) vacuum.getThreshold() : 0.20);
        map.put("capacity", configSnapshot.memory() != null ? configSnapshot.memory().getCapacity() : 100000);
        map.put("persistence-mode", configSnapshot.memory() != null && configSnapshot.memory().getPersistenceMode() != null
                ? configSnapshot.memory().getPersistenceMode().name().toLowerCase() : "mmap");
        map.put("dimensions", configSnapshot.memory() != null ? configSnapshot.memory().getDimensions() : 384);
        return map;
    }

    private Map<String, Object> recallDefaults() {
        var recall = configSnapshot.memory() != null ? configSnapshot.memory().getRecall() : null;
        var text = recall != null ? recall.getTextSearch() : null;
        var mmr = recall != null ? recall.getMmr() : null;
        var lat = recall != null ? recall.getLateral() : null;
        var val = recall != null ? recall.getValenceAlignment() : null;

        var map = new LinkedHashMap<String, Object>();
        map.put("scoring-mode", recall != null && recall.getScoringMode() != null ? recall.getScoringMode() : "COGNITIVE");
        map.put("score-fusion-mode", recall != null && recall.getScoreFusionMode() != null ? recall.getScoreFusionMode() : "MULTIPLICATIVE");
        map.put("strictness-coefficient", recall != null ? (double) recall.getStrictnessCoefficient() : 1.0);
        map.put("mode", recall != null && recall.getMode() != null ? recall.getMode() : "LEARN");
        map.put("engine", recall != null && recall.getEngine() != null ? recall.getEngine() : "pathway");
        map.put("text-search.mode", text != null && text.getMode() != null ? text.getMode() : "BM25");
        map.put("text-search.gamma", 0.3);
        map.put("mmr.enabled", mmr != null && mmr.isEnabled());
        map.put("mmr.lambda", mmr != null ? (double) mmr.getLambda() : 0.5);
        map.put("lateral.mode", lat != null && lat.isEnabled());
        map.put("lateral.distance-threshold", lat != null ? (double) lat.getDistanceThreshold() : 0.7);
        map.put("lateral.max-results", 5);
        map.put("valence-alignment.enabled", val != null && val.isEnabled());
        map.put("trace-enabled", recall != null && recall.isTraceEnabled());
        map.put("top-k", com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K);
        map.put("similarity-threshold", configSnapshot.memory() != null ? (double) configSnapshot.memory().getGraphExpansionThreshold() : 0.7);
        return map;
    }

    private Map<String, Object> hnswDefaults() {
        var hnsw = configSnapshot.hnsw();
        var map = new LinkedHashMap<String, Object>();
        map.put("ef-search", hnsw != null ? hnsw.getEfSearch() : 64);
        map.put("m", hnsw != null ? hnsw.getM() : 16);
        map.put("ef-construction", hnsw != null ? hnsw.getEfConstruction() : 128);
        return map;
    }

    private Map<String, Object> spectrumDefaults() {
        var spec = configSnapshot.spectrum();
        var map = new LinkedHashMap<String, Object>();
        map.put("n-probe", spec != null ? spec.getNProbe() : 8);
        map.put("oversampling-factor", spec != null ? spec.getOversamplingFactor() : 2.0);
        map.put("n-centroids", spec != null ? spec.getNCentroids() : 256);
        map.put("shard-threshold", spec != null ? spec.getShardThreshold() : 100000);
        map.put("kmeans-iterations", spec != null ? spec.getKmeansIterations() : 20);
        return map;
    }

    private Map<String, Object> embeddingDefaults() {
        var emb = configSnapshot.provider() != null ? configSnapshot.provider().getEmbedding() : null;
        var map = new LinkedHashMap<String, Object>();
        map.put("provider", emb != null && emb.getType() != null ? emb.getType() : "onnx");
        map.put("model", emb != null && emb.getModel() != null ? emb.getModel() : "all-minilm-l6-v2-q");
        map.put("base-url", emb != null && emb.getBaseUrl() != null ? emb.getBaseUrl() : "http://localhost:11434");
        map.put("dimensions", configSnapshot.memory() != null ? configSnapshot.memory().getDimensions() : 384);
        map.put("batch-size", emb != null ? emb.getBatchSize() : 32);
        map.put("max-retries", emb != null ? emb.getMaxRetries() : 3);
        map.put("cache.enabled", emb == null || emb.isCacheEnabled());
        return map;
    }

    private Map<String, Object> multimodalDefaults() {
        var mm = configSnapshot.multimodal();
        var map = new LinkedHashMap<String, Object>();
        map.put("enabled", mm != null && mm.isEnabled());
        map.put("vision-model", mm != null && mm.getVisionModel() != null ? mm.getVisionModel() : "clip-vit-base-patch32");
        map.put("audio-model", mm != null && mm.getAudioModel() != null ? mm.getAudioModel() : "whisper-tiny");
        map.put("keyframe-interval-seconds", 5);
        map.put("asset-store.type", mm != null && mm.getAssetStoreType() != null ? mm.getAssetStoreType() : "local");
        return map;
    }

    private Map<String, Object> telemetryDefaults() {
        var tel = configSnapshot.telemetry();
        var map = new LinkedHashMap<String, Object>();
        map.put("sample-ratio", tel != null ? tel.getQuerySampleRate() : 1.0);
        map.put("enabled", tel == null || tel.isEnabled());
        map.put("exporter", "prometheus");
        map.put("tracing-enabled", true);
        return map;
    }

    private Map<String, Object> concurrencyDefaults() {
        var conc = configSnapshot.concurrency();
        var map = new LinkedHashMap<String, Object>();
        map.put("virtual-threads.enabled", conc == null || conc.isStructured());
        map.put("pool-size", 16);
        return map;
    }

    private Map<String, Object> salienceDefaults() {
        var map = new LinkedHashMap<String, Object>();
        map.put("alpha", 0.6);
        map.put("beta", 0.4);
        return map;
    }

    private Map<String, Object> soulDefaults() {
        var map = new LinkedHashMap<String, Object>();
        map.put("archetype", "Analyst");
        map.put("description", "");
        return map;
    }

    private Map<String, Object> llmDefaults() {
        var gen = configSnapshot.provider() != null ? configSnapshot.provider().getGeneration() : null;
        var map = new LinkedHashMap<String, Object>();
        map.put("provider", gen != null && gen.getType() != null ? gen.getType() : "ollama");
        map.put("model", gen != null && gen.getModel() != null ? gen.getModel() : "llama3.2");
        map.put("base-url", gen != null && gen.getBaseUrl() != null ? gen.getBaseUrl() : "http://localhost:11434");
        double temp = 0.7;
        if (gen != null && gen.getProperties() != null && gen.getProperties().containsKey("temperature")) {
            try {
                temp = Double.parseDouble(gen.getProperties().get("temperature"));
            } catch (NumberFormatException ignored) {}
        }
        map.put("temperature", temp);
        map.put("api-key", gen != null && gen.getApiKey() != null ? gen.getApiKey() : "");
        return map;
    }

    private Map<String, Object> ingestionDefaults() {
        var chunk = configSnapshot.memory() != null && configSnapshot.memory().getRemember() != null
                ? configSnapshot.memory().getRemember().getChunk() : null;
        var ing = configSnapshot.ingestion();
        var map = new LinkedHashMap<String, Object>();
        map.put("chunk-size", chunk != null && chunk.getSize() > 0 ? chunk.getSize() : 2500);
        map.put("chunk-overlap", chunk != null && chunk.getOverlap() >= 0 ? chunk.getOverlap() : 200);
        map.put("parent-child-linking", false);
        map.put("file-pattern", ing != null && ing.getFilePattern() != null ? ing.getFilePattern() : "**/*.md,**/*.txt");
        map.put("skip-dirs", ing != null && ing.getSkipDirs() != null ? ing.getSkipDirs() : ".git,node_modules,target");
        map.put("parallelism", ing != null ? ing.getParallelism() : 4);
        return map;
    }

    private Map<String, Object> ragDefaults() {
        int topK = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K;
        float similarityThreshold = configSnapshot.memory() != null ? configSnapshot.memory().getGraphExpansionThreshold() : 0.7f;
        var map = new LinkedHashMap<String, Object>();
        map.put("top-k", topK);
        map.put("similarity-threshold", (double) similarityThreshold);
        return map;
    }

    @SuppressWarnings("unchecked")
    private void mergeOverrides(Map<String, Object> effective, Map<String, Object> overrides) {
        overrides.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nestedOverrides && effective.get(key) instanceof Map<?, ?> nestedEffective) {
                Map<String, Object> mergedNested = new LinkedHashMap<>((Map<String, Object>) nestedEffective);
                mergeOverrides(mergedNested, (Map<String, Object>) nestedOverrides);
                effective.put(key, mergedNested);
            } else if (value != null) {
                effective.put(key, value);
            }
        });
    }

    /** Configuration value with source annotation for UI override badges. */
    public record AnnotatedValue(Object value, String source) {}
}
