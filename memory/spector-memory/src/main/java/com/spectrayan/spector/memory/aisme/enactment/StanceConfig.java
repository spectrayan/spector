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

import com.spectrayan.spector.memory.aisme.policy.PolicyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration parameters for Stance Resolution, Hopfield Attractor convergence, and Policy Selection (ADR-0032).
 */
public record StanceConfig(
        float hopfieldBeta,
        float highUrgencyThreshold,
        int evidencedGroundingThreshold,
        List<PolicyPlaybook> crisisPlaybooks,
        List<PolicyPlaybook> routinePlaybooks,
        PolicyPlaybook defaultPlaybook
) {

    public static final float DEFAULT_HOPFIELD_BETA = 2.0f;
    public static final float DEFAULT_HIGH_URGENCY_THRESHOLD = 0.7f;
    public static final int DEFAULT_EVIDENCED_GROUNDING_THRESHOLD = 2;

    public StanceConfig {
        crisisPlaybooks = crisisPlaybooks != null ? List.copyOf(crisisPlaybooks) : List.of();
        routinePlaybooks = routinePlaybooks != null ? List.copyOf(routinePlaybooks) : List.of();
        Objects.requireNonNull(defaultPlaybook, "defaultPlaybook must not be null");
    }

    public static StanceConfig defaultConfig() {
        List<PolicyPlaybook> crisis = List.of(
                PolicyPlaybook.of(PolicyType.PRAGMATIC_EXPLOITATION),
                PolicyPlaybook.of(PolicyType.CLARIFYING_INTERACTION)
        );
        List<PolicyPlaybook> routine = List.of(
                PolicyPlaybook.of(PolicyType.EPISTEMIC_EXPLORATION),
                PolicyPlaybook.of(PolicyType.PROCEDURAL_CRYSTALLIZATION),
                PolicyPlaybook.of(PolicyType.PRAGMATIC_EXPLOITATION)
        );
        PolicyPlaybook def = new PolicyPlaybook(
                "DEFAULT",
                PolicyType.EPISTEMIC_EXPLORATION,
                new float[]{0.0f, 0.2f, 0.3f},
                new float[]{1.0f, 1.0f, 1.0f}
        );
        return new StanceConfig(
                DEFAULT_HOPFIELD_BETA,
                DEFAULT_HIGH_URGENCY_THRESHOLD,
                DEFAULT_EVIDENCED_GROUNDING_THRESHOLD,
                crisis,
                routine,
                def
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float hopfieldBeta = DEFAULT_HOPFIELD_BETA;
        private float highUrgencyThreshold = DEFAULT_HIGH_URGENCY_THRESHOLD;
        private int evidencedGroundingThreshold = DEFAULT_EVIDENCED_GROUNDING_THRESHOLD;
        private final List<PolicyPlaybook> crisisPlaybooks = new ArrayList<>();
        private final List<PolicyPlaybook> routinePlaybooks = new ArrayList<>();
        private PolicyPlaybook defaultPlaybook;

        public Builder() {
            StanceConfig def = StanceConfig.defaultConfig();
            this.hopfieldBeta = def.hopfieldBeta();
            this.highUrgencyThreshold = def.highUrgencyThreshold();
            this.evidencedGroundingThreshold = def.evidencedGroundingThreshold();
            this.crisisPlaybooks.addAll(def.crisisPlaybooks());
            this.routinePlaybooks.addAll(def.routinePlaybooks());
            this.defaultPlaybook = def.defaultPlaybook();
        }

        public Builder hopfieldBeta(float beta) {
            this.hopfieldBeta = beta;
            return this;
        }

        public Builder highUrgencyThreshold(float threshold) {
            this.highUrgencyThreshold = threshold;
            return this;
        }

        public Builder evidencedGroundingThreshold(int threshold) {
            this.evidencedGroundingThreshold = threshold;
            return this;
        }

        public Builder addCrisisPlaybook(PolicyPlaybook playbook) {
            if (playbook != null) {
                this.crisisPlaybooks.add(playbook);
            }
            return this;
        }

        public Builder crisisPlaybooks(List<PolicyPlaybook> playbooks) {
            this.crisisPlaybooks.clear();
            if (playbooks != null) {
                this.crisisPlaybooks.addAll(playbooks);
            }
            return this;
        }

        public Builder addRoutinePlaybook(PolicyPlaybook playbook) {
            if (playbook != null) {
                this.routinePlaybooks.add(playbook);
            }
            return this;
        }

        public Builder routinePlaybooks(List<PolicyPlaybook> playbooks) {
            this.routinePlaybooks.clear();
            if (playbooks != null) {
                this.routinePlaybooks.addAll(playbooks);
            }
            return this;
        }

        public Builder defaultPlaybook(PolicyPlaybook playbook) {
            if (playbook != null) {
                this.defaultPlaybook = playbook;
            }
            return this;
        }

        public StanceConfig build() {
            return new StanceConfig(
                    hopfieldBeta,
                    highUrgencyThreshold,
                    evidencedGroundingThreshold,
                    crisisPlaybooks,
                    routinePlaybooks,
                    defaultPlaybook
            );
        }
    }
}
