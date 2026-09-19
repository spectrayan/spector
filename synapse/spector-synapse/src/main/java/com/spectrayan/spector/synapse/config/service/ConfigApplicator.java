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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.config.model.LiveMemoryPatch;
import com.spectrayan.spector.config.properties.RecallProperties;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Applies resolved configuration to running subsystems at runtime.
 * Dispatches typed hooks directly to target {@link SpectorMemory} instances,
 * provider registries, and salience services per ADR-0085.
 */
@Service
public class ConfigApplicator {

    private static final Logger log = LoggerFactory.getLogger(ConfigApplicator.class);

    private final ProviderRegistry providerRegistry;
    private final ObjectProvider<SpectorMemory> spectorMemoryProvider;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;
    private final ObjectProvider<SynapseSalienceProvider> salienceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ConfigSchemaRegistry schemaRegistry;

    public ConfigApplicator(ProviderRegistry providerRegistry,
                            ObjectProvider<SpectorMemory> spectorMemoryProvider,
                            ObjectProvider<MemoryRegistry> memoryRegistryProvider,
                            ObjectProvider<SynapseSalienceProvider> salienceProvider,
                            ObjectProvider<ObjectMapper> objectMapperProvider,
                            ConfigSchemaRegistry schemaRegistry) {
        this.providerRegistry = providerRegistry;
        this.spectorMemoryProvider = spectorMemoryProvider;
        this.memoryRegistryProvider = memoryRegistryProvider;
        this.salienceProvider = salienceProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.schemaRegistry = schemaRegistry;
        log.info("ConfigApplicator initialized with dynamic typed dispatch");
    }

    /**
     * Applies configuration immediately to running subsystems and returns the apply status.
     *
     * @return one of {@code "applied"}, {@code "persisted_pending_open"},
     *         {@code "persisted_rebuild_required"}, or {@code "persisted_reboot_required"}
     */
    public String apply(String tenantId, String userId,
                        ConfigCategory category, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "applied";
        }

        // 1. Check if any changed key requires REBUILD or BOOT
        if (schemaRegistry != null) {
            for (String key : values.keySet()) {
                String mode = schemaRegistry.getApplyMode(category, key);
                if ("REBUILD".equalsIgnoreCase(mode)) {
                    log.info("[ConfigApplicator] Configuration key '{}' in category '{}' requires index rebuild",
                            key, category.key());
                    return "persisted_rebuild_required";
                }
            }
            for (String key : values.keySet()) {
                String mode = schemaRegistry.getApplyMode(category, key);
                if ("BOOT".equalsIgnoreCase(mode)) {
                    log.info("[ConfigApplicator] Configuration key '{}' in category '{}' requires process reboot",
                            key, category.key());
                    return "persisted_reboot_required";
                }
            }
        }

        // 2. Resolve target memory instance
        MemoryRegistry registry = memoryRegistryProvider != null ? memoryRegistryProvider.getIfAvailable() : null;
        SpectorMemory target = null;
        if (registry != null) {
            try {
                target = registry.resolveFor(userId);
            } catch (Exception e) {
                log.warn("[ConfigApplicator] MemoryRegistry failed to resolve memory for user '{}': {}", userId, e.getMessage());
            }
        }
        if (target == null && spectorMemoryProvider != null) {
            target = spectorMemoryProvider.getIfAvailable();
        }

        // 3. Dispatch typed hooks to target memory instance
        if (target != null) {
            applyToMemory(target, category, values);

            // If tenant-level broadcast, also apply to all other cached instances of this tenant
            if (tenantId != null && !tenantId.isBlank() && registry != null) {
                for (SpectorMemory cached : registry.cachedInstances()) {
                    if (cached != target) {
                        applyToMemory(cached, category, values);
                    }
                }
            }
        }

        // 4. Dispatch external subsystems
        switch (category) {
            case LLM_PROVIDER -> applyLlmProvider(values);
            case SALIENCE -> applySalience(values);
            case SOUL -> applySoul(userId, values);
            default -> {}
        }

        if (target == null && (category == ConfigCategory.MEMORY || category == ConfigCategory.RECALL
                || category == ConfigCategory.HNSW || category == ConfigCategory.INGESTION)) {
            log.info("[ConfigApplicator] Target memory instance not yet loaded for user='{}'; persisted pending open",
                    userId);
            return "persisted_pending_open";
        }

        log.info("[ConfigApplicator] Applied {} configuration dynamically for tenant='{}', user='{}'",
                category.key(), tenantId, userId);
        return "applied";
    }

    /**
     * Applies configuration directly to a specific {@link SpectorMemory} instance.
     * Invoked dynamically on configuration save and by {@link ConfigBootstrapper} on namespace open.
     */
    public void applyToMemory(SpectorMemory memory, ConfigCategory category, Map<String, Object> values) {
        if (memory == null || values == null || values.isEmpty()) return;

        switch (category) {
            case MEMORY -> applyMemory(memory, values);
            case RECALL, RAG -> applyRecall(memory, values);
            case HNSW -> applyHnsw(memory, values);
            case INGESTION -> applyIngestion(memory, values);
            default -> log.debug("[ConfigApplicator] Category {} does not target SpectorMemory directly", category.key());
        }
    }

    public int pendingCount() {
        return 0;
    }

    public void drainPending() {
        // No-op: execution is synchronous
    }

    private void applyMemory(SpectorMemory memory, Map<String, Object> values) {
        LiveMemoryPatch.Builder b = LiveMemoryPatch.builder();

        if (values.containsKey("decay-enabled")) b.decayEnabled(boolVal(values, "decay-enabled", true));
        if (values.containsKey("surprise-warmup")) b.surpriseWarmup(intVal(values, "surprise-warmup", 10));
        if (values.containsKey("flashbulb-threshold")) b.flashbulbThreshold(floatVal(values, "flashbulb-threshold", 3.0f));
        if (values.containsKey("valence-learning-rate")) b.valenceLearningRate(floatVal(values, "valence-learning-rate", 0.3f));
        if (values.containsKey("deduplication-radius")) b.deduplicationRadius(floatVal(values, "deduplication-radius", 0.05f));
        if (values.containsKey("inhibition-ttl-ms")) b.inhibitionTtlMs(longVal(values, "inhibition-ttl-ms", 300000L));
        if (values.containsKey("habituation-decay-rate")) b.habituationDecayRate(floatVal(values, "habituation-decay-rate", 0.2f));
        if (values.containsKey("ltp-cooldown-ms")) b.ltpCooldownMs(longVal(values, "ltp-cooldown-ms", 300000L));
        if (values.containsKey("hebbian-max-degree")) b.hebbianMaxDegree(intVal(values, "hebbian-max-degree", 24));
        if (values.containsKey("hebbian-decay-factor")) b.hebbianDecayFactor(floatVal(values, "hebbian-decay-factor", 0.9f));
        if (values.containsKey("graph-expansion-mode")) b.graphExpansionMode(stringVal(values, "graph-expansion-mode", "GATED"));
        if (values.containsKey("circadian-volume-trigger")) b.circadianVolumeTrigger(intVal(values, "circadian-volume-trigger", 100));
        if (values.containsKey("vacuum-threshold")) b.vacuumThreshold(floatVal(values, "vacuum-threshold", 0.20f));

        LiveMemoryPatch patch = b.build();
        memory.applyLiveMemoryPatch(patch);
        log.info("[ConfigApplicator] Applied LiveMemoryPatch to memory instance: {}", patch);
    }

    private void applyRecall(SpectorMemory memory, Map<String, Object> values) {
        RecallProperties recallProps = new RecallProperties();
        if (values.containsKey("scoring-mode")) recallProps.setScoringMode(stringVal(values, "scoring-mode", "COGNITIVE"));
        if (values.containsKey("score-fusion-mode")) recallProps.setScoreFusionMode(stringVal(values, "score-fusion-mode", "MULTIPLICATIVE"));
        if (values.containsKey("strictness-coefficient")) recallProps.setStrictnessCoefficient(floatVal(values, "strictness-coefficient", 1.0f));
        if (values.containsKey("mode")) recallProps.setMode(stringVal(values, "mode", "LEARN"));
        if (values.containsKey("engine")) recallProps.setEngine(stringVal(values, "engine", "pathway"));
        if (values.containsKey("trace-enabled")) recallProps.setTraceEnabled(boolVal(values, "trace-enabled", false));
        if (values.containsKey("text-search.mode")) {
            recallProps.getTextSearch().setMode(stringVal(values, "text-search.mode", "BM25"));
        }
        if (values.containsKey("mmr.enabled")) recallProps.getMmr().setEnabled(boolVal(values, "mmr.enabled", false));
        if (values.containsKey("mmr.lambda")) recallProps.getMmr().setLambda(floatVal(values, "mmr.lambda", 0.5f));
        if (values.containsKey("lateral.mode")) recallProps.getLateral().setEnabled(boolVal(values, "lateral.mode", false));
        if (values.containsKey("lateral.distance-threshold")) recallProps.getLateral().setDistanceThreshold(floatVal(values, "lateral.distance-threshold", 0.7f));
        if (values.containsKey("valence-alignment.enabled")) recallProps.getValenceAlignment().setEnabled(boolVal(values, "valence-alignment.enabled", false));

        RecallOptions recallOptions = RecallOptions.from(recallProps);
        var b = recallOptions.toBuilder();
        if (values.containsKey("top-k")) {
            b.topK(intVal(values, "top-k", recallOptions.topK()));
        }
        if (values.containsKey("text-search.gamma")) {
            b.gamma(floatVal(values, "text-search.gamma", recallOptions.gamma()));
        }
        if (values.containsKey("lateral.max-results")) {
            b.lateralMaxResults(intVal(values, "lateral.max-results", 5));
        }
        RecallOptions updatedOptions = b.build();
        memory.updateRecallOptions(updatedOptions);
        log.info("[ConfigApplicator] Applied RecallOptions to memory instance (topK={}, scoringMode={})",
                updatedOptions.topK(), recallProps.getScoringMode());
    }

    private void applyHnsw(SpectorMemory memory, Map<String, Object> values) {
        if (values.containsKey("ef-search")) {
            int efSearch = intVal(values, "ef-search", 64);
            memory.updateHnswEfSearch(efSearch);
            log.info("[ConfigApplicator] Applied HNSW ef-search: {}", efSearch);
        }
    }

    private void applyIngestion(SpectorMemory memory, Map<String, Object> values) {
        int chunkSize = intVal(values, "chunk-size", 2500);
        int overlap = intVal(values, "chunk-overlap", 200);
        boolean parentChild = boolVal(values, "parent-child-linking", false);

        var chunkConfig = new ChunkConfig(
                chunkSize, overlap, "text/markdown", null, true, true, false, parentChild);
        memory.updateChunkConfig(chunkConfig);
        log.info("[ConfigApplicator] Ingestion config updated on SpectorMemory: chunk-size={}, overlap={}, parent-child={}",
                chunkSize, overlap, parentChild);
    }

    private void applyLlmProvider(Map<String, Object> values) {
        String providerName = stringVal(values, "provider", null);
        if (providerName == null) return;

        if (providerRegistry.generationProviderNames().contains(providerName)) {
            providerRegistry.activateGeneration(providerName);
            log.info("[ConfigApplicator] Activated LLM provider: {}", providerName);
            return;
        }

        String model = stringVal(values, "model", "default");
        String apiKey = stringVal(values, "api-key", "");
        String baseUrl = stringVal(values, "base-url", null);

        for (ProviderFactory factory : ServiceLoader.load(ProviderFactory.class)) {
            if (factory.name().equalsIgnoreCase(providerName) && factory.supportsGeneration()) {
                var config = new ProviderConfig(providerName, "generation", model,
                        apiKey, baseUrl, 0, extractProperties(values));
                factory.createGenerationProvider(config).ifPresent(provider -> {
                    providerRegistry.registerGeneration(providerName, provider);
                    providerRegistry.activateGeneration(providerName);
                    log.info("[ConfigApplicator] Created and activated LLM provider: {} (model={})",
                            providerName, model);
                });
                return;
            }
        }
        log.warn("[ConfigApplicator] No factory found for LLM provider: {}", providerName);
    }

    @SuppressWarnings("unchecked")
    private void applySalience(Map<String, Object> values) {
        SynapseSalienceProvider provider = salienceProvider.getIfAvailable();
        if (provider == null || values == null || values.isEmpty()) return;

        List<SynapseSalienceProvider.InterestEntry> interestEntries = parseInterests((List<Map<String, Object>>) values.get("interestsList"));
        List<SynapseSalienceProvider.InterestEntry> disinterestEntries = parseInterests((List<Map<String, Object>>) values.get("disinterestsList"));
        provider.updateInterests(interestEntries, disinterestEntries);

        Map<String, Object> icnuMap = (Map<String, Object>) values.get("icnuWeights");
        IcnuWeights icnu = null;
        if (icnuMap != null) {
            float interest = floatVal(icnuMap, "interest", 0.25f);
            float challenge = floatVal(icnuMap, "challenge", 0.25f);
            float novelty = floatVal(icnuMap, "novelty", 0.25f);
            float urgency = floatVal(icnuMap, "urgency", 0.25f);
            icnu = new IcnuWeights(interest, challenge, novelty, urgency);
        }
        Float alpha = values.get("alpha") != null ? ((Number) values.get("alpha")).floatValue() : null;
        Float beta = values.get("beta") != null ? ((Number) values.get("beta")).floatValue() : null;

        provider.updateScoringWeights(icnu, alpha, beta);
        log.info("[ConfigApplicator] Applied Salience config: alpha={}, beta={}", alpha, beta);
    }

    private void applySoul(String userId, Map<String, Object> values) {
        SynapseSalienceProvider provider = salienceProvider.getIfAvailable();
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        if (provider == null || mapper == null || values == null || values.isEmpty()) return;

        if (userId != null && !userId.isBlank()) {
            try {
                PersonaContext persona = mapper.convertValue(values, PersonaContext.class);
                provider.updateUserPersona(persona);
                log.info("[ConfigApplicator] Applied User Soul (PersonaContext) to salience provider for user: {}", userId);
            } catch (Exception e) {
                log.warn("[ConfigApplicator] Failed to parse User Soul (PersonaContext): {}", e.getMessage());
            }
        }
    }

    private List<SynapseSalienceProvider.InterestEntry> parseInterests(List<Map<String, Object>> list) {
        if (list == null) return List.of();
        List<SynapseSalienceProvider.InterestEntry> result = new ArrayList<>();
        for (var map : list) {
            String topic = (String) map.get("topic");
            String lvlStr = (String) map.get("level");
            if (topic != null && lvlStr != null) {
                try {
                    com.spectrayan.spector.memory.model.InterestLevel lvl =
                            com.spectrayan.spector.memory.model.InterestLevel.valueOf(lvlStr.toUpperCase());
                    result.add(new SynapseSalienceProvider.InterestEntry(topic, lvl));
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static String stringVal(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseInteger(v.toString()).orElse(def);
        }
        return def;
    }

    private static long longVal(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseLongOrDefault(v.toString(), def);
        }
        return def;
    }

    private static double doubleVal(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseDouble(v.toString()).orElse(def);
        }
        return def;
    }

    private static float floatVal(Map<String, Object> map, String key, float def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.floatValue();
        if (v != null) {
            return com.spectrayan.spector.commons.ParseUtils.parseDouble(v.toString())
                    .map(Double::floatValue)
                    .orElse(def);
        }
        return def;
    }

    private static boolean boolVal(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return def;
    }

    private static Map<String, String> extractProperties(Map<String, Object> values) {
        var props = new LinkedHashMap<String, String>();
        values.forEach((k, v) -> {
            if (v != null && !k.equals("provider") && !k.equals("model")
                    && !k.equals("api-key") && !k.equals("base-url")
                    && !k.equals("dimensions")) {
                props.put(k, v.toString());
            }
        });
        return props;
    }
}
