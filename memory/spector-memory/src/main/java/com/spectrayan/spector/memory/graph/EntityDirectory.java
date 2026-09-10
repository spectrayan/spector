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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-level entity directory subclass providing semantic entity resolution,
 * Levenshtein fuzzy matching, embedding similarity, and LLM adjudication.
 *
 * <p>Contains zero {@code java.lang.foreign.*} imports — off-heap layouts,
 * segments, arenas, and mmap persistence are fully sealed in
 * {@link com.spectrayan.spector.kernel.graph.EntityDirectory}.</p>
 */
public class EntityDirectory extends com.spectrayan.spector.kernel.graph.EntityDirectory {

    private static final Logger log = LoggerFactory.getLogger(EntityDirectory.class);

    private static final ThreadLocal<int[]> LEV_PREV = ThreadLocal.withInitial(() -> new int[256]);
    private static final ThreadLocal<int[]> LEV_CURR = ThreadLocal.withInitial(() -> new int[256]);

    public EntityDirectory(int entityCapacity, TypeRegistryMemory entityTypeRegistry) {
        super(entityCapacity, entityTypeRegistry);
    }

    public EntityDirectory(Path filePath, int entityCapacity, TypeRegistryMemory entityTypeRegistry) {
        super(filePath, entityCapacity, entityTypeRegistry);
    }

    protected EntityDirectory(RegionRef entityDirRef,
                              RegionRef entityNamesRef,
                              int entityCapacity, TypeRegistryMemory entityTypeRegistry,
                              Path bundlePath, boolean isNew) {
        super(entityDirRef, entityNamesRef, entityCapacity, entityTypeRegistry, bundlePath, isNew);
    }

    public static EntityDirectory fromRegionRefs(
            RegionRef entityDirRef,
            RegionRef entityNamesRef,
            int entityCapacity, TypeRegistryMemory entityTypeRegistry,
            Path bundlePath, boolean isNew) {
        com.spectrayan.spector.kernel.graph.EntityDirectory kDir =
                com.spectrayan.spector.kernel.graph.EntityDirectory.fromRegionRefs(
                        entityDirRef, entityNamesRef, entityCapacity, entityTypeRegistry, bundlePath, isNew);
        return new EntityDirectory(entityDirRef, entityNamesRef, kDir.capacity(), entityTypeRegistry, bundlePath, isNew);
    }

    public static EntityDirectory load(Path filePath, int defaultEntityCap,
                                       TypeRegistryMemory entityTypeRegistry) {
        return load(filePath, defaultEntityCap, entityTypeRegistry, (DataEncryptor) null);
    }

    public static EntityDirectory load(Path filePath, int defaultEntityCap,
                                       TypeRegistryMemory entityTypeRegistry, DataEncryptor encryptor) {
        com.spectrayan.spector.kernel.graph.EntityDirectory kDir =
                com.spectrayan.spector.kernel.graph.EntityDirectory.load(filePath, defaultEntityCap, entityTypeRegistry, encryptor);
        EntityDirectory dir = new EntityDirectory(filePath, defaultEntityCap, entityTypeRegistry);
        dir.nameIndexInternal().putAll(kDir.nameIndexInternal());
        if (encryptor != null) {
            dir.setDataEncryptor(encryptor);
        }
        return dir;
    }

    /**
     * Merges entities with similar names using Levenshtein distance (identity-level dedup).
     *
     * @param maxEditDistance maximum Levenshtein distance for merge
     * @param typeNormalizer  optional type normalizer for cross-type compatibility
     * @return number of entities merged
     */
    public int mergeSimilarEntities(int maxEditDistance, TypeNormalizer typeNormalizer) {
        if (maxEditDistance <= 0 || entityCount() < 2) return 0;
        Set<Integer> merged = new HashSet<>();
        int mergeCount = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(nameIndexInternal().entrySet());
        for (int i = 0; i < entries.size(); i++) {
            if (merged.contains(entries.get(i).getValue())) continue;
            String nameA = entries.get(i).getKey();
            int idA = entries.get(i).getValue();
            for (int j = i + 1; j < entries.size(); j++) {
                if (merged.contains(entries.get(j).getValue())) continue;
                String nameB = entries.get(j).getKey();
                int idB = entries.get(j).getValue();
                String typeA = entityType(idA);
                String typeB = entityType(idB);
                if (typeNormalizer != null) {
                    if (!typeNormalizer.areMergeCompatible(typeA, typeB)) continue;
                } else {
                    if (!typeA.equals(typeB)) continue;
                }
                int dist = levenshteinDistance(nameA, nameB);
                if (dist > 0 && dist <= maxEditDistance) {
                    int canonical = nameA.length() <= nameB.length() ? idA : idB;
                    int duplicate = canonical == idA ? idB : idA;
                    mergeEntity(duplicate, canonical);
                    merged.add(duplicate);
                    mergeCount++;
                }
            }
        }
        if (mergeCount > 0) {
            log.info("EntityDirectory merged {} similar entities", mergeCount);
        }
        return mergeCount;
    }

    /**
     * Merges entities using embeddings and LLM adjudication.
     */
    public int mergeSimilarEntities(EmbeddingProvider embedder, LlmProvider adjudicator,
                                    float cosineThreshold, boolean shadowMode,
                                    TypeNormalizer typeNormalizer) {
        if (embedder == null || adjudicator == null || entityCount() < 2) return 0;

        LlmEntityAdjudicator llmAdjudicator = new LlmEntityAdjudicator(adjudicator);
        Set<Integer> merged = new HashSet<>();
        int mergeCount = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(nameIndexInternal().entrySet());

        List<String> namesToEmbed = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            namesToEmbed.add(entry.getKey());
        }

        float[][] embeddings = null;
        try {
            List<com.spectrayan.spector.provider.embedding.EmbeddingResult> results = embedder.embedBatch(namesToEmbed);
            embeddings = new float[results.size()][];
            for (int i = 0; i < results.size(); i++) {
                embeddings[i] = results.get(i).vector();
            }
        } catch (Exception e) {
            log.warn("Failed to embed entity names for resolution", e);
            return 0;
        }

        for (int i = 0; i < entries.size(); i++) {
            int idA = entries.get(i).getValue();
            if (merged.contains(idA)) continue;
            String nameA = entries.get(i).getKey();
            String typeA = entityType(idA);

            for (int j = i + 1; j < entries.size(); j++) {
                int idB = entries.get(j).getValue();
                if (merged.contains(idB)) continue;
                String typeB = entityType(idB);
                if (!typeA.equals(typeB)) continue;

                float sim = cosineSimilarity(embeddings[i], embeddings[j]);
                if (sim >= cosineThreshold) {
                    String nameB = entries.get(j).getKey();

                    if (shadowMode) {
                        log.info("[EntityResolution:Shadow] Proposed merge: entityIdA={}, entityIdB={}, typeA={}, typeB={}, sim={}",
                                idA, idB, typeA, typeB, sim);
                        continue;
                    }

                    var result = llmAdjudicator.adjudicate(nameA, typeA, nameB, typeB, List.of());
                    if (result.shouldMerge()) {
                        int canonical = nameA.length() <= nameB.length() ? idA : idB;
                        int duplicate = canonical == idA ? idB : idA;
                        mergeEntity(duplicate, canonical);
                        merged.add(duplicate);
                        mergeCount++;
                        log.info("Entity resolution merged duplicate entity ID {} into canonical entity ID {}", duplicate, canonical);
                    }
                }
            }
        }
        return mergeCount;
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f) return 0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public static int levenshteinDistance(String a, String b) {
        int lenA = a.length(), lenB = b.length();
        if (lenA == 0) return lenB;
        if (lenB == 0) return lenA;
        if (Math.abs(lenA - lenB) > 5) return Math.abs(lenA - lenB);
        int[] prev = LEV_PREV.get();
        int[] curr = LEV_CURR.get();
        if (prev.length <= lenB) {
            prev = new int[lenB + 1];
            curr = new int[lenB + 1];
            LEV_PREV.set(prev);
            LEV_CURR.set(curr);
        }
        for (int j = 0; j <= lenB; j++) prev[j] = j;
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[lenB];
    }
}
