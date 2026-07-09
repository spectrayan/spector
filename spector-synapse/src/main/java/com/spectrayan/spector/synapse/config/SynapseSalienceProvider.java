/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.PersonalityModifiers;
import com.spectrayan.spector.memory.model.SalienceProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * OSS implementation of {@link SalienceProfileProvider}.
 *
 * <h3>Design: User-Salience-Driven Memory</h3>
 * <p>In the OSS synapse module, there is no multi-tenant hierarchy. This provider
 * manages a single user's salience profile, derived from the user's
 * {@link PersonaContext} (their "soul"). The user's persona influences:</p>
 * <ul>
 *   <li><b>Importance modulation</b> — self-relevance boost for memories matching
 *       the user's occupation, values, aspirations</li>
 *   <li><b>Valence/arousal modulation</b> — personality-based emotional adjustment</li>
 *   <li><b>ICNU weight overrides</b> — if the user's profile specifies custom weights</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <p>The profile starts as {@link SalienceProfile#NEUTRAL} and is updated when
 * the user's persona is loaded or modified via {@link #updateUserPersona}.
 * The update is thread-safe (uses {@link ReentrantLock}).</p>
 *
 * <h3>Embedding Computation</h3>
 * <p>When a user persona is set, this provider computes embeddings for the
 * persona's text fields (occupation, values, aspirations, etc.) using the
 * configured {@link EmbeddingProvider}. These embeddings enable semantic
 * self-relevance matching during memory ingestion.</p>
 *
 * @see SalienceProfile#computeSelfRelevanceBoost(float[])
 * @see SalienceProfile#modulateValence(byte)
 * @see PersonaContext
 */
@Component
public class SynapseSalienceProvider implements SalienceProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(SynapseSalienceProvider.class);

    private final EmbeddingProvider embeddingProvider;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile SalienceProfile currentProfile = SalienceProfile.NEUTRAL;

    public SynapseSalienceProvider(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
        log.info("[SynapseSalience] Provider initialized (profile=NEUTRAL, awaiting user persona)");
    }

    @Override
    public SalienceProfile effectiveProfile() {
        return currentProfile;
    }

    /**
     * Updates the user salience profile from the user's persona context.
     *
     * <p>Computes embeddings for text-based persona fields and builds a
     * {@link SalienceProfile} with the persona set for self-relevance scoring
     * and personality-based valence/arousal modulation.</p>
     *
     * <p>Thread-safe — uses {@link ReentrantLock} to prevent concurrent updates.</p>
     *
     * @param persona the user's persona context (null resets to NEUTRAL)
     */
    public void updateUserPersona(PersonaContext persona) {
        if (persona == null || !persona.isPresent()) {
            lock.lock();
            try {
                currentProfile = SalienceProfile.NEUTRAL;
                log.info("[SynapseSalience] User persona cleared — profile reset to NEUTRAL");
            } finally {
                lock.unlock();
            }
            return;
        }

        lock.lock();
        try {
            // Compute embeddings for persona fields if not already present
            PersonaContext enriched = computePersonaEmbeddings(persona);

            // Derive personality modifiers if not already computed
            PersonalityModifiers modifiers = enriched.modifiers() != null
                    && !enriched.modifiers().isNeutral()
                    ? enriched.modifiers()
                    : PersonalityModifiers.derive(
                            enriched.bigFive(),
                            enriched.emotionalIntelligence(),
                            enriched.stressResponse());

            // Rebuild persona with computed modifiers if they were derived
            if (modifiers != enriched.modifiers()) {
                enriched = new PersonaContext(
                        enriched.about(),
                        enriched.occupation(), enriched.education(),
                        enriched.nationality(), enriched.languages(),
                        enriched.culturalIdentity(),
                        enriched.bigFive(), enriched.emotionalIntelligence(),
                        enriched.stressResponse(),
                        enriched.values(), enriched.fears(), enriched.aspirations(),
                        enriched.communicationStyle(),
                        modifiers,
                        enriched.aboutEmbedding(),
                        enriched.occupationEmbedding(),
                        enriched.educationEmbedding(),
                        enriched.valuesEmbedding(),
                        enriched.aspirationsEmbedding());
            }

            currentProfile = SalienceProfile.builder()
                    .persona(enriched)
                    .build();

            log.info("[SynapseSalience] User persona applied — occupation={}, hasEmbeddings={}, " +
                            "selfRelevanceWeight={}, valenceAmplification={}",
                    enriched.occupation(),
                    enriched.hasEmbeddings(),
                    modifiers.selfRelevanceWeight(),
                    modifiers.valenceAmplification());
        } catch (Exception e) {
            log.error("[SynapseSalience] Failed to update user persona: {}", e.getMessage(), e);
            // Keep the current profile on failure — don't reset to NEUTRAL
        } finally {
            lock.unlock();
        }
    }

    /**
     * Computes embeddings for persona text fields if missing.
     *
     * <p>Uses the configured {@link EmbeddingProvider} to embed:
     * occupation, concatenated education degrees, concatenated values,
     * and concatenated aspirations. Embeddings are L2-normalized by the
     * embedding provider.</p>
     */
    private PersonaContext computePersonaEmbeddings(PersonaContext persona) {
        if (persona.hasEmbeddings()) {
            log.debug("[SynapseSalience] Persona already has embeddings — skipping computation");
            return persona;
        }
        if (embeddingProvider == null) {
            log.warn("[SynapseSalience] No embedding provider — self-relevance scoring disabled");
            return persona;
        }

        float[] occupationEmbed = embedText(persona.occupation());
        float[] educationEmbed = embedText(joinEducation(persona));
        float[] valuesEmbed = embedText(String.join(", ", persona.values()));
        float[] aspirationsEmbed = embedText(String.join(", ", persona.aspirations()));

        return new PersonaContext(
                persona.about(),
                persona.occupation(), persona.education(),
                persona.nationality(), persona.languages(),
                persona.culturalIdentity(),
                persona.bigFive(), persona.emotionalIntelligence(),
                persona.stressResponse(),
                persona.values(), persona.fears(), persona.aspirations(),
                persona.communicationStyle(),
                persona.modifiers(),
                persona.aboutEmbedding(), // keep existing about embedding if present
                occupationEmbed != null ? occupationEmbed : persona.occupationEmbedding(),
                educationEmbed != null ? educationEmbed : persona.educationEmbedding(),
                valuesEmbed != null ? valuesEmbed : persona.valuesEmbedding(),
                aspirationsEmbed != null ? aspirationsEmbed : persona.aspirationsEmbedding());
    }

    /**
     * Embeds a text string. Returns null for blank/empty text.
     */
    private float[] embedText(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return embeddingProvider.embed(text).vector();
        } catch (Exception e) {
            log.warn("[SynapseSalience] Embedding failed for text '{}...': {}",
                    text.substring(0, Math.min(30, text.length())), e.getMessage());
            return null;
        }
    }

    /**
     * Joins education entries into a single string for embedding.
     */
    private static String joinEducation(PersonaContext persona) {
        if (persona.education().isEmpty()) return "";
        var sb = new StringBuilder();
        for (var edu : persona.education()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(edu.toString());
        }
        return sb.toString();
    }
}
