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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;

/**
 * Bind-time salience overlay (ADR-0029 §2.5.6). Domain focus becomes extra
 * {@link InterestDomain}s at MEDIUM; ICNU itself is unchanged.
 */
public final class NamespaceBiasApplier {

    private NamespaceBiasApplier() {
    }

    public static SalienceProfile apply(SalienceProfile base, NamespaceBias bias) {
        if (bias == null || bias == NamespaceBias.EMPTY) {
            return base;
        }
        boolean hasDomainFocus = bias.domainFocus() != null && !bias.domainFocus().isEmpty();
        boolean hasTagWeights = bias.tagWeights() != null && !bias.tagWeights().isEmpty();
        if (!hasDomainFocus && !hasTagWeights) {
            return base;
        }
        SalienceProfile src = base != null ? base : SalienceProfile.NEUTRAL;
        SalienceProfile.Builder builder = SalienceProfile.builder()
                .icnuWeights(src.icnuWeights())
                .alpha(src.alpha() != null ? src.alpha() : 0f)
                .beta(src.beta() != null ? src.beta() : 0f)
                .flashbulbThreshold(src.flashbulbThreshold())
                .recencyWeight(src.recencyWeight())
                .similarityThreshold(src.similarityThreshold())
                .persona(src.persona())
                .agentRelevanceBoost(src.agentRelevanceBoost());
        if (src.defaultProfile() != null) {
            builder.defaultProfile(src.defaultProfile());
        }
        for (InterestDomain domain : src.interests()) {
            builder.interest(domain);
        }
        for (InterestDomain domain : src.disinterests()) {
            builder.disinterest(domain);
        }
        if (hasDomainFocus) {
            for (String domain : bias.domainFocus()) {
                if (domain != null && !domain.isBlank()) {
                    builder.interest(domain.trim(), InterestLevel.MEDIUM);
                }
            }
        }
        if (hasTagWeights) {
            for (java.util.Map.Entry<String, Float> entry : bias.tagWeights().entrySet()) {
                String tag = entry.getKey();
                Float weight = entry.getValue();
                if (tag != null && !tag.isBlank() && weight != null) {
                    if (weight <= 0.0f) {
                        builder.disinterest(tag.trim(), InterestLevel.HIGH);
                    } else if (weight >= 1.5f) {
                        builder.interest(tag.trim(), InterestLevel.HIGH);
                    } else if (weight >= 1.0f) {
                        builder.interest(tag.trim(), InterestLevel.MEDIUM);
                    } else {
                        builder.interest(tag.trim(), InterestLevel.LOW);
                    }
                }
            }
        }
        return builder.build();
    }
}
