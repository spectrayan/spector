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
import java.util.Objects;

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
        
        // Linguistics & Prosody (Phase 1 & Phase 2)
        IdiolectProfile idiolect,
        VocalProsodyDNA vocalProsody,
        
        // Embodied Kinesics & Avatar Dynamics (Phase 3)
        EmbodiedKinesicsDNA embodiedKinesics,

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
        if (idiolect == null) idiolect = IdiolectProfile.NEUTRAL;
        if (vocalProsody == null) vocalProsody = VocalProsodyDNA.NEUTRAL;
        if (embodiedKinesics == null) embodiedKinesics = EmbodiedKinesicsDNA.NEUTRAL;

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
     * Backward-compatible 19-argument constructor without idiolect and vocalProsody.
     */
    public PersonaContext(
            String about,
            String occupation,
            List<Education> education,
            String nationality,
            List<String> languages,
            CulturalIdentity culturalIdentity,
            BigFiveTraits bigFive,
            EmotionalIntelligence emotionalIntelligence,
            StressResponse stressResponse,
            List<String> values,
            List<String> fears,
            List<String> aspirations,
            CommunicationStyle communicationStyle,
            PersonalityModifiers modifiers,
            float[] aboutEmbedding,
            float[] occupationEmbedding,
            float[] educationEmbedding,
            float[] valuesEmbedding,
            float[] aspirationsEmbedding
    ) {
        this(about, occupation, education, nationality, languages, culturalIdentity,
                bigFive, emotionalIntelligence, stressResponse, values, fears, aspirations,
                communicationStyle, IdiolectProfile.NEUTRAL, VocalProsodyDNA.NEUTRAL, EmbodiedKinesicsDNA.NEUTRAL,
                modifiers, aboutEmbedding, occupationEmbedding, educationEmbedding,
                valuesEmbedding, aspirationsEmbedding);
    }

    /**
     * Backward-compatible 21-argument constructor with idiolect and vocalProsody, but no embodiedKinesics.
     */
    public PersonaContext(
            String about,
            String occupation,
            List<Education> education,
            String nationality,
            List<String> languages,
            CulturalIdentity culturalIdentity,
            BigFiveTraits bigFive,
            EmotionalIntelligence emotionalIntelligence,
            StressResponse stressResponse,
            List<String> values,
            List<String> fears,
            List<String> aspirations,
            CommunicationStyle communicationStyle,
            IdiolectProfile idiolect,
            VocalProsodyDNA vocalProsody,
            PersonalityModifiers modifiers,
            float[] aboutEmbedding,
            float[] occupationEmbedding,
            float[] educationEmbedding,
            float[] valuesEmbedding,
            float[] aspirationsEmbedding
    ) {
        this(about, occupation, education, nationality, languages, culturalIdentity,
                bigFive, emotionalIntelligence, stressResponse, values, fears, aspirations,
                communicationStyle, idiolect, vocalProsody, EmbodiedKinesicsDNA.NEUTRAL,
                modifiers, aboutEmbedding, occupationEmbedding, educationEmbedding,
                valuesEmbedding, aspirationsEmbedding);
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
            IdiolectProfile.NEUTRAL, VocalProsodyDNA.NEUTRAL, EmbodiedKinesicsDNA.NEUTRAL,
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
                || !aspirations.isEmpty()
                || idiolect.isPresent()
                || vocalProsody.isPresent()
                || embodiedKinesics != EmbodiedKinesicsDNA.NEUTRAL;
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
        private IdiolectProfile idiolect = IdiolectProfile.NEUTRAL;
        private VocalProsodyDNA vocalProsody = VocalProsodyDNA.NEUTRAL;
        private EmbodiedKinesicsDNA embodiedKinesics = EmbodiedKinesicsDNA.NEUTRAL;
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
        
        /** Sets idiolect profile. */
        public Builder idiolect(IdiolectProfile idiolect) {
            this.idiolect = idiolect;
            return this;
        }

        /** Sets idiolect profile (alias). */
        public Builder idiolectProfile(IdiolectProfile idiolect) {
            return idiolect(idiolect);
        }
        
        /** Sets vocal prosody DNA. */
        public Builder vocalProsody(VocalProsodyDNA vocalProsody) {
            this.vocalProsody = vocalProsody;
            return this;
        }
        
        /** Sets embodied kinesics DNA. */
        public Builder embodiedKinesics(EmbodiedKinesicsDNA embodiedKinesics) {
            this.embodiedKinesics = embodiedKinesics;
            return this;
        }
        
        /** Sets embodied kinesics DNA (alias). */
        public Builder kinesics(EmbodiedKinesicsDNA kinesics) {
            return embodiedKinesics(kinesics);
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
                    idiolect, vocalProsody, embodiedKinesics,
                    effectiveModifiers,
                    aboutEmbedding, occupationEmbedding, educationEmbedding,
                    valuesEmbedding, aspirationsEmbedding);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonaContext other)) return false;
        return Objects.equals(about, other.about)
                && Objects.equals(occupation, other.occupation)
                && Objects.equals(education, other.education)
                && Objects.equals(nationality, other.nationality)
                && Objects.equals(languages, other.languages)
                && Objects.equals(culturalIdentity, other.culturalIdentity)
                && Objects.equals(bigFive, other.bigFive)
                && Objects.equals(emotionalIntelligence, other.emotionalIntelligence)
                && stressResponse == other.stressResponse
                && Objects.equals(values, other.values)
                && Objects.equals(fears, other.fears)
                && Objects.equals(aspirations, other.aspirations)
                && communicationStyle == other.communicationStyle
                && Objects.equals(idiolect, other.idiolect)
                && Objects.equals(vocalProsody, other.vocalProsody)
                && Objects.equals(embodiedKinesics, other.embodiedKinesics)
                && Objects.equals(modifiers, other.modifiers)
                && Arrays.equals(aboutEmbedding, other.aboutEmbedding)
                && Arrays.equals(occupationEmbedding, other.occupationEmbedding)
                && Arrays.equals(educationEmbedding, other.educationEmbedding)
                && Arrays.equals(valuesEmbedding, other.valuesEmbedding)
                && Arrays.equals(aspirationsEmbedding, other.aspirationsEmbedding);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(about, occupation, education, nationality, languages,
                culturalIdentity, bigFive, emotionalIntelligence, stressResponse,
                values, fears, aspirations, communicationStyle, idiolect, vocalProsody, embodiedKinesics, modifiers);
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
                + ", idiolect=" + (idiolect.isPresent() ? "PRESENT" : "NONE")
                + ", vocalProsody=" + (vocalProsody.isPresent() ? "PRESENT" : "NONE")
                + ", embeddings=" + hasEmbeddings()
                + "]";
    }
}
