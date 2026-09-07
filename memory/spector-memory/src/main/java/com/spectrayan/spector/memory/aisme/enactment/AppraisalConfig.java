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
package com.spectrayan.spector.memory.aisme.enactment;

import com.spectrayan.spector.memory.model.enactment.AgencyAttribution;

import java.util.*;

/**
 * Configuration parameters for the Cognitive Appraisal Engine (ADR-0032).
 *
 * <p>Provides fast immutable lookup maps for keyword-driven valence, arousal, dominance,
 * agency attribution, and normative violation rules.</p>
 */
public record AppraisalConfig(
        Map<String, Float> valenceKeywords,
        Map<String, Float> arousalKeywords,
        float defaultArousal,
        Map<String, Float> dominanceKeywords,
        float defaultDominance,
        float baselineArousalFactor,
        Map<String, AgencyAttribution> agencyKeywords,
        AgencyAttribution defaultAgency,
        List<NormativeRule> normativeRules,
        String defaultPrimaryConcern,
        float sdeDt
) {

    public static final float DEFAULT_AROUSAL = 0.2f;
    public static final float DEFAULT_DOMINANCE = 0.3f;
    public static final float DEFAULT_BASELINE_AROUSAL_FACTOR = 0.2f;
    public static final AgencyAttribution DEFAULT_AGENCY = AgencyAttribution.CIRCUMSTANTIAL;
    public static final String DEFAULT_PRIMARY_CONCERN = "operational_stability";
    public static final float DEFAULT_SDE_DT = 0.1f;

    public AppraisalConfig {
        Map<String, Float> vMap = new LinkedHashMap<>();
        if (valenceKeywords != null) {
            valenceKeywords.forEach((k, v) -> vMap.put(k.toLowerCase(Locale.ROOT), v));
        }
        valenceKeywords = Collections.unmodifiableMap(vMap);

        Map<String, Float> aMap = new LinkedHashMap<>();
        if (arousalKeywords != null) {
            arousalKeywords.forEach((k, v) -> aMap.put(k.toLowerCase(Locale.ROOT), v));
        }
        arousalKeywords = Collections.unmodifiableMap(aMap);

        Map<String, Float> dMap = new LinkedHashMap<>();
        if (dominanceKeywords != null) {
            dominanceKeywords.forEach((k, v) -> dMap.put(k.toLowerCase(Locale.ROOT), v));
        }
        dominanceKeywords = Collections.unmodifiableMap(dMap);

        Map<String, AgencyAttribution> agMap = new LinkedHashMap<>();
        if (agencyKeywords != null) {
            agencyKeywords.forEach((k, v) -> agMap.put(k.toLowerCase(Locale.ROOT), v));
        }
        agencyKeywords = Collections.unmodifiableMap(agMap);

        normativeRules = normativeRules != null ? List.copyOf(normativeRules) : List.of();
        defaultAgency = (defaultAgency != null) ? defaultAgency : DEFAULT_AGENCY;
        defaultPrimaryConcern = (defaultPrimaryConcern != null && !defaultPrimaryConcern.isBlank())
                ? defaultPrimaryConcern
                : DEFAULT_PRIMARY_CONCERN;
    }

    public static AppraisalConfig defaultConfig() {
        Map<String, Float> valence = new LinkedHashMap<>();
        valence.put("outage", -0.6f);
        valence.put("breach", -0.6f);
        valence.put("fail", -0.6f);
        valence.put("corrupt", -0.6f);
        valence.put("error", -0.6f);
        valence.put("success", 0.5f);
        valence.put("resolved", 0.5f);
        valence.put("optimize", 0.5f);
        valence.put("speedup", 0.5f);

        Map<String, Float> arousal = new LinkedHashMap<>();
        arousal.put("critical", 0.85f);
        arousal.put("urgent", 0.85f);
        arousal.put("immediately", 0.85f);
        arousal.put("p0", 0.85f);
        arousal.put("emergency", 0.85f);
        arousal.put("investigate", 0.4f);
        arousal.put("audit", 0.4f);
        arousal.put("review", 0.4f);

        Map<String, Float> dominance = new LinkedHashMap<>();
        dominance.put("unknown", -0.4f);
        dominance.put("unprecedented", -0.4f);
        dominance.put("unreproducible", -0.4f);

        Map<String, AgencyAttribution> agency = new LinkedHashMap<>();
        agency.put("our bug", AgencyAttribution.SELF);
        agency.put("my mistake", AgencyAttribution.SELF);
        agency.put("i broke", AgencyAttribution.SELF);
        agency.put("we deployed", AgencyAttribution.SELF);
        agency.put("attacker", AgencyAttribution.OTHER_ADVERSARY);
        agency.put("breach", AgencyAttribution.OTHER_ADVERSARY);
        agency.put("malicious", AgencyAttribution.OTHER_ADVERSARY);
        agency.put("client", AgencyAttribution.OTHER_BENIGN);
        agency.put("third-party", AgencyAttribution.OTHER_BENIGN);
        agency.put("vendor", AgencyAttribution.OTHER_BENIGN);
        agency.put("user", AgencyAttribution.OTHER_BENIGN);

        List<NormativeRule> rules = List.of(
                NormativeRule.of("safety", "bypass auth", "safety_violation"),
                NormativeRule.of("integrity", "skip audit", "integrity_violation")
        );

        return new AppraisalConfig(
                valence,
                arousal,
                DEFAULT_AROUSAL,
                dominance,
                DEFAULT_DOMINANCE,
                DEFAULT_BASELINE_AROUSAL_FACTOR,
                agency,
                DEFAULT_AGENCY,
                rules,
                DEFAULT_PRIMARY_CONCERN,
                DEFAULT_SDE_DT
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Float> valenceKeywords = new LinkedHashMap<>();
        private final Map<String, Float> arousalKeywords = new LinkedHashMap<>();
        private float defaultArousal = DEFAULT_AROUSAL;
        private final Map<String, Float> dominanceKeywords = new LinkedHashMap<>();
        private float defaultDominance = DEFAULT_DOMINANCE;
        private float baselineArousalFactor = DEFAULT_BASELINE_AROUSAL_FACTOR;
        private final Map<String, AgencyAttribution> agencyKeywords = new LinkedHashMap<>();
        private AgencyAttribution defaultAgency = DEFAULT_AGENCY;
        private final List<NormativeRule> normativeRules = new ArrayList<>();
        private String defaultPrimaryConcern = DEFAULT_PRIMARY_CONCERN;
        private float sdeDt = DEFAULT_SDE_DT;

        public Builder() {
            AppraisalConfig def = AppraisalConfig.defaultConfig();
            this.valenceKeywords.putAll(def.valenceKeywords());
            this.arousalKeywords.putAll(def.arousalKeywords());
            this.defaultArousal = def.defaultArousal();
            this.dominanceKeywords.putAll(def.dominanceKeywords());
            this.defaultDominance = def.defaultDominance();
            this.baselineArousalFactor = def.baselineArousalFactor();
            this.agencyKeywords.putAll(def.agencyKeywords());
            this.defaultAgency = def.defaultAgency();
            this.normativeRules.addAll(def.normativeRules());
            this.defaultPrimaryConcern = def.defaultPrimaryConcern();
            this.sdeDt = def.sdeDt();
        }

        public Builder valenceKeyword(String keyword, float weight) {
            this.valenceKeywords.put(keyword, weight);
            return this;
        }

        public Builder valenceKeywords(Map<String, Float> keywords) {
            if (keywords != null) {
                this.valenceKeywords.putAll(keywords);
            }
            return this;
        }

        public Builder arousalKeyword(String keyword, float delta) {
            this.arousalKeywords.put(keyword, delta);
            return this;
        }

        public Builder arousalKeywords(Map<String, Float> keywords) {
            if (keywords != null) {
                this.arousalKeywords.putAll(keywords);
            }
            return this;
        }

        public Builder defaultArousal(float defaultArousal) {
            this.defaultArousal = defaultArousal;
            return this;
        }

        public Builder dominanceKeyword(String keyword, float delta) {
            this.dominanceKeywords.put(keyword, delta);
            return this;
        }

        public Builder dominanceKeywords(Map<String, Float> keywords) {
            if (keywords != null) {
                this.dominanceKeywords.putAll(keywords);
            }
            return this;
        }

        public Builder defaultDominance(float defaultDominance) {
            this.defaultDominance = defaultDominance;
            return this;
        }

        public Builder baselineArousalFactor(float factor) {
            this.baselineArousalFactor = factor;
            return this;
        }

        public Builder agencyKeyword(String keyword, AgencyAttribution agency) {
            this.agencyKeywords.put(keyword, agency);
            return this;
        }

        public Builder agencyKeywords(Map<String, AgencyAttribution> keywords) {
            if (keywords != null) {
                this.agencyKeywords.putAll(keywords);
            }
            return this;
        }

        public Builder defaultAgency(AgencyAttribution defaultAgency) {
            this.defaultAgency = defaultAgency;
            return this;
        }

        public Builder addNormativeRule(NormativeRule rule) {
            if (rule != null) {
                this.normativeRules.add(rule);
            }
            return this;
        }

        public Builder normativeRules(List<NormativeRule> rules) {
            this.normativeRules.clear();
            if (rules != null) {
                this.normativeRules.addAll(rules);
            }
            return this;
        }

        public Builder defaultPrimaryConcern(String concern) {
            this.defaultPrimaryConcern = concern;
            return this;
        }

        public Builder sdeDt(float dt) {
            this.sdeDt = dt;
            return this;
        }

        public AppraisalConfig build() {
            return new AppraisalConfig(
                    valenceKeywords,
                    arousalKeywords,
                    defaultArousal,
                    dominanceKeywords,
                    defaultDominance,
                    baselineArousalFactor,
                    agencyKeywords,
                    defaultAgency,
                    normativeRules,
                    defaultPrimaryConcern,
                    sdeDt
            );
        }
    }
}
