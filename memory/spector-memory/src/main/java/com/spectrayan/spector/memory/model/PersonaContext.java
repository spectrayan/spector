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
package com.spectrayan.spector.memory.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central persona context — aggregates identity, personality, cultural identity,
 * and derived scoring modifiers.
 *
 * <h3>Biological Analog: Medial Prefrontal Cortex (mPFC)</h3>
 * <p>The brain's mPFC + Default Mode Network maintain a persistent self-model that
 * continuously evaluates incoming stimuli against personal identity. When information
 * is processed in relation to the self (the Self-Reference Effect), encoding is
 * 2-3× more effective. PersonaContext is the computational analog of this self-model —
 * it provides the mPFC self-reference pathway that Spector's scoring pipeline was
 * previously missing.</p>
 *
 * <h3>Architecture</h3>
 * <p>PersonaContext is set at the <b>user scope only</b> — tenant and agent levels
 * cannot set persona data (it's personal). It is stored in the enterprise layer
 * and injected into {@link SalienceProfile} during profile resolution via
 * {@code TenantSalienceResolver}.</p>
 *
 * <h3>Schema Origins</h3>
 * <ul>
 *   <li>Identity fields → {@code consciousness/identity/Persona.yaml}</li>
 *   <li>Personality fields → {@code consciousness/personality/PersonalityTraits.yaml}</li>
 *   <li>Cultural identity → NEW (not yet in consciousness repo)</li>
 *   <li>Scoring modifiers → Spector-specific (derived via {@link PersonalityModifiers#derive})</li>
 * </ul>
 *
 * <h3>Embedding Strategy</h3>
 * <p>At profile-save time, the enterprise layer computes embeddings for
 * text-based fields (occupation, education degrees, values, aspirations,
 * cultural identity). These embeddings are used at ingestion time for
 * self-relevance matching via cosine similarity in
 * {@link SalienceProfile#computeSelfRelevanceBoost}.</p>
 *
 * @param about               free-text self-reflection / bio ("Tell about yourself")
 * @param occupation          occupation text (e.g., "Software Engineer")
 * @param education           educational background entries
 * @param nationality         nationality (e.g., "American")
 * @param languages           languages spoken
 * @param culturalIdentity    self-described cultural/racial/spiritual identity
 * @param bigFive             Big Five personality traits (OCEAN)
 * @param emotionalIntelligence Goleman's five EQ dimensions
 * @param stressResponse      stress response archetype
 * @param values              core personal values (e.g., ["Honesty", "Family"])
 * @param fears               personal fears (e.g., ["Heights", "Public speaking"])
 * @param aspirations         goals and aspirations (e.g., ["Start a business"])
 * @param communicationStyle  communication style archetype
 * @param modifiers           derived scoring modifiers (computed at save time)
 * @param aboutEmbedding      pre-computed embedding of about/bio text
 * @param occupationEmbedding pre-computed embedding of occupation text
 * @param educationEmbedding  pre-computed embedding of education degrees (concatenated)
 * @param valuesEmbedding     pre-computed embedding of values (concatenated)
 * @param aspirationsEmbedding pre-computed embedding of aspirations (concatenated)
 */
public record PersonaContext(
        // Self-reflection
        String about,

        // Identity
        String occupation,
        List<Education> education,
        String nationality,
        List<String> languages,

        // Cultural Identity
        CulturalIdentity culturalIdentity,

        // Personality
        BigFiveTraits bigFive,
        EmotionalIntelligence emotionalIntelligence,
        StressResponse stressResponse,
        List<String> values,
        List<String> fears,
        List<String> aspirations,

        // Social
        CommunicationStyle communicationStyle,

        // Derived scoring modifiers
        PersonalityModifiers modifiers,

        // Pre-computed embeddings
        float[] aboutEmbedding,
        float[] occupationEmbedding,
        float[] educationEmbedding,
        float[] valuesEmbedding,
        float[] aspirationsEmbedding
) {

    /**
     * Compact constructor — enforces immutability and safe defaults.
     */
    public PersonaContext {
        education = education != null
                ? Collections.unmodifiableList(education) : List.of();
        languages = languages != null
                ? Collections.unmodifiableList(languages) : List.of();
        values = values != null
                ? Collections.unmodifiableList(values) : List.of();
        fears = fears != null
                ? Collections.unmodifiableList(fears) : List.of();
        aspirations = aspirations != null
                ? Collections.unmodifiableList(aspirations) : List.of();

        if (culturalIdentity == null) culturalIdentity = CulturalIdentity.NONE;
        if (bigFive == null) bigFive = BigFiveTraits.NEUTRAL;
        if (emotionalIntelligence == null) emotionalIntelligence = EmotionalIntelligence.NEUTRAL;
        if (stressResponse == null) stressResponse = StressResponse.ADAPTIVE;
        if (modifiers == null) modifiers = PersonalityModifiers.NEUTRAL;

        // Defensive copy of embeddings
        if (aboutEmbedding != null) {
            aboutEmbedding = Arrays.copyOf(aboutEmbedding, aboutEmbedding.length);
        }
        if (occupationEmbedding != null) {
            occupationEmbedding = Arrays.copyOf(occupationEmbedding, occupationEmbedding.length);
        }
        if (educationEmbedding != null) {
            educationEmbedding = Arrays.copyOf(educationEmbedding, educationEmbedding.length);
        }
        if (valuesEmbedding != null) {
            valuesEmbedding = Arrays.copyOf(valuesEmbedding, valuesEmbedding.length);
        }
        if (aspirationsEmbedding != null) {
            aspirationsEmbedding = Arrays.copyOf(aspirationsEmbedding, aspirationsEmbedding.length);
        }
    }

    /**
     * No persona set — produces no scoring effect (full backward compatibility).
     */
    public static final PersonaContext NONE = new PersonaContext(
            null,
            null, List.of(), null, List.of(),
            CulturalIdentity.NONE,
            BigFiveTraits.NEUTRAL, EmotionalIntelligence.NEUTRAL,
            StressResponse.ADAPTIVE,
            List.of(), List.of(), List.of(),
            null,
            PersonalityModifiers.NEUTRAL,
            null, null, null, null, null);

    /**
     * Returns true if this persona has any meaningful identity data.
     * A persona with only NEUTRAL personality traits and no identity fields
     * is effectively absent.
     */
    public boolean isPresent() {
        return (about != null && !about.isBlank())
                || (occupation != null && !occupation.isBlank())
                || !education.isEmpty()
                || culturalIdentity.isPresent()
                || !bigFive.isNeutral()
                || !emotionalIntelligence.isNeutral()
                || stressResponse != StressResponse.ADAPTIVE
                || !values.isEmpty()
                || !aspirations.isEmpty();
    }

    /**
     * Returns true if this persona has any pre-computed embeddings
     * for self-relevance matching.
     */
    public boolean hasEmbeddings() {
        return (aboutEmbedding != null && aboutEmbedding.length > 0)
                || (occupationEmbedding != null && occupationEmbedding.length > 0)
                || (educationEmbedding != null && educationEmbedding.length > 0)
                || (valuesEmbedding != null && valuesEmbedding.length > 0)
                || (aspirationsEmbedding != null && aspirationsEmbedding.length > 0)
                || culturalIdentity.hasEmbedding();
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PersonaContext}.
     */
    public static final class Builder {
        private String about;
        private String occupation;
        private java.util.List<Education> education = new java.util.ArrayList<>();
        private String nationality;
        private java.util.List<String> languages = new java.util.ArrayList<>();
        private CulturalIdentity culturalIdentity;
        private BigFiveTraits bigFive;
        private EmotionalIntelligence emotionalIntelligence;
        private StressResponse stressResponse;
        private java.util.List<String> values = new java.util.ArrayList<>();
        private java.util.List<String> fears = new java.util.ArrayList<>();
        private java.util.List<String> aspirations = new java.util.ArrayList<>();
        private CommunicationStyle communicationStyle;
        private PersonalityModifiers modifiers;
        private float[] aboutEmbedding;
        private float[] occupationEmbedding;
        private float[] educationEmbedding;
        private float[] valuesEmbedding;
        private float[] aspirationsEmbedding;

        /** Sets the about/bio text. */
        public Builder about(String about) {
            this.about = about;
            return this;
        }

        /** Sets the occupation. */
        public Builder occupation(String occupation) {
            this.occupation = occupation;
            return this;
        }

        /** Adds an education entry. */
        public Builder education(Education entry) {
            this.education.add(entry);
            return this;
        }

        /** Sets education list. */
        public Builder education(java.util.List<Education> education) {
            this.education = new java.util.ArrayList<>(education);
            return this;
        }

        /** Sets nationality. */
        public Builder nationality(String nationality) {
            this.nationality = nationality;
            return this;
        }

        /** Sets languages. */
        public Builder languages(java.util.List<String> languages) {
            this.languages = new java.util.ArrayList<>(languages);
            return this;
        }

        /** Sets cultural identity. */
        public Builder culturalIdentity(CulturalIdentity culturalIdentity) {
            this.culturalIdentity = culturalIdentity;
            return this;
        }

        /** Sets Big Five traits. */
        public Builder bigFive(BigFiveTraits bigFive) {
            this.bigFive = bigFive;
            return this;
        }

        /** Sets emotional intelligence. */
        public Builder emotionalIntelligence(EmotionalIntelligence eq) {
            this.emotionalIntelligence = eq;
            return this;
        }

        /** Sets stress response archetype. */
        public Builder stressResponse(StressResponse stressResponse) {
            this.stressResponse = stressResponse;
            return this;
        }

        /** Adds a core value. */
        public Builder value(String value) {
            this.values.add(value);
            return this;
        }

        /** Sets core values. */
        public Builder values(java.util.List<String> values) {
            this.values = new java.util.ArrayList<>(values);
            return this;
        }

        /** Adds a fear. */
        public Builder fear(String fear) {
            this.fears.add(fear);
            return this;
        }

        /** Sets fears. */
        public Builder fears(java.util.List<String> fears) {
            this.fears = new java.util.ArrayList<>(fears);
            return this;
        }

        /** Adds an aspiration. */
        public Builder aspiration(String aspiration) {
            this.aspirations.add(aspiration);
            return this;
        }

        /** Sets aspirations. */
        public Builder aspirations(java.util.List<String> aspirations) {
            this.aspirations = new java.util.ArrayList<>(aspirations);
            return this;
        }

        /** Sets communication style. */
        public Builder communicationStyle(CommunicationStyle style) {
            this.communicationStyle = style;
            return this;
        }

        /** Sets pre-computed scoring modifiers (bypasses derive). */
        public Builder modifiers(PersonalityModifiers modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        /** Sets about/bio embedding. */
        public Builder aboutEmbedding(float[] embedding) {
            this.aboutEmbedding = embedding;
            return this;
        }

        /** Sets occupation embedding. */
        public Builder occupationEmbedding(float[] embedding) {
            this.occupationEmbedding = embedding;
            return this;
        }

        /** Sets education embedding. */
        public Builder educationEmbedding(float[] embedding) {
            this.educationEmbedding = embedding;
            return this;
        }

        /** Sets values embedding. */
        public Builder valuesEmbedding(float[] embedding) {
            this.valuesEmbedding = embedding;
            return this;
        }

        /** Sets aspirations embedding. */
        public Builder aspirationsEmbedding(float[] embedding) {
            this.aspirationsEmbedding = embedding;
            return this;
        }

        /**
         * Builds the PersonaContext.
         *
         * <p>If {@code modifiers} is not set explicitly, derives them
         * from bigFive, emotionalIntelligence, and stressResponse.</p>
         */
        public PersonaContext build() {
            PersonalityModifiers effectiveModifiers = modifiers != null
                    ? modifiers
                    : PersonalityModifiers.derive(bigFive, emotionalIntelligence, stressResponse);

            return new PersonaContext(
                    about,
                    occupation, education, nationality, languages,
                    culturalIdentity,
                    bigFive, emotionalIntelligence, stressResponse,
                    values, fears, aspirations,
                    communicationStyle,
                    effectiveModifiers,
                    aboutEmbedding, occupationEmbedding, educationEmbedding,
                    valuesEmbedding, aspirationsEmbedding);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonaContext other)) return false;
        return java.util.Objects.equals(about, other.about)
                && java.util.Objects.equals(occupation, other.occupation)
                && java.util.Objects.equals(education, other.education)
                && java.util.Objects.equals(nationality, other.nationality)
                && java.util.Objects.equals(languages, other.languages)
                && java.util.Objects.equals(culturalIdentity, other.culturalIdentity)
                && java.util.Objects.equals(bigFive, other.bigFive)
                && java.util.Objects.equals(emotionalIntelligence, other.emotionalIntelligence)
                && stressResponse == other.stressResponse
                && java.util.Objects.equals(values, other.values)
                && java.util.Objects.equals(fears, other.fears)
                && java.util.Objects.equals(aspirations, other.aspirations)
                && communicationStyle == other.communicationStyle
                && java.util.Objects.equals(modifiers, other.modifiers)
                && Arrays.equals(aboutEmbedding, other.aboutEmbedding)
                && Arrays.equals(occupationEmbedding, other.occupationEmbedding)
                && Arrays.equals(educationEmbedding, other.educationEmbedding)
                && Arrays.equals(valuesEmbedding, other.valuesEmbedding)
                && Arrays.equals(aspirationsEmbedding, other.aspirationsEmbedding);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(about, occupation, education, nationality, languages,
                culturalIdentity, bigFive, emotionalIntelligence, stressResponse,
                values, fears, aspirations, communicationStyle, modifiers);
        result = 31 * result + Arrays.hashCode(aboutEmbedding);
        result = 31 * result + Arrays.hashCode(occupationEmbedding);
        result = 31 * result + Arrays.hashCode(educationEmbedding);
        result = 31 * result + Arrays.hashCode(valuesEmbedding);
        result = 31 * result + Arrays.hashCode(aspirationsEmbedding);
        return result;
    }

    @Override
    public String toString() {
        return "PersonaContext[about=" + (about != null ? about.length() + " chars" : "null")
                + ", occupation=" + occupation
                + ", education=" + education.size() + " entries"
                + ", bigFive=" + (bigFive.isNeutral() ? "NEUTRAL" : bigFive)
                + ", eq=" + (emotionalIntelligence.isNeutral() ? "NEUTRAL" : emotionalIntelligence)
                + ", stress=" + stressResponse
                + ", culture=" + (culturalIdentity.isPresent() ? culturalIdentity.primaryCulture() : "NONE")
                + ", embeddings=" + hasEmbeddings()
                + "]";
    }
}
