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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Temperature and adaptive retrieval configuration for cognitive recall.
 *
 * <p>Modulates the breadth and sharpness of associative retrieval. When adaptive
 * temperature is enabled, query-side surprise (prediction error) dynamically scales
 * the temperature: high surprise expands retrieval breadth ($T &gt; 1.0$), while
 * familiar queries focus retrieval ($T \le 1.0$).</p>
 *
 * @param adaptiveTemperature            whether adaptive temperature modulation is enabled
 * @param baseTemperature                baseline temperature (default: 1.0)
 * @param temperatureSurpriseCoefficient scaling coefficient \(\kappa\) for surprise (default: 0.15)
 * @param minTemperature                 minimum clamping bound (default: 0.1)
 * @param maxTemperature                 maximum clamping bound (default: 5.0)
 */
public record TemperatureOptions(
        boolean adaptiveTemperature,
        float baseTemperature,
        float temperatureSurpriseCoefficient,
        float minTemperature,
        float maxTemperature
) {

    /** Default balanced temperature options. */
    public static final TemperatureOptions DEFAULT = new TemperatureOptions(
            SpectorPropertyConstants.DEFAULT_RECALL_ADAPTIVE_TEMPERATURE_ENABLED,
            SpectorPropertyConstants.DEFAULT_RECALL_BASE_TEMPERATURE,
            SpectorPropertyConstants.DEFAULT_RECALL_TEMPERATURE_SURPRISE_COEFFICIENT,
            SpectorPropertyConstants.DEFAULT_RECALL_MIN_TEMPERATURE,
            SpectorPropertyConstants.DEFAULT_RECALL_MAX_TEMPERATURE
    );

    /**
     * Computes the effective temperature given a query surprise z-score.
     *
     * <p>Formula: \(T = \operatorname{clamp}(T_{\text{base}} \times (1 + \kappa \times \max(0, z_{\text{surprise}})), T_{\text{min}}, T_{\text{max}})\).</p>
     *
     * @param zSurprise query-side surprise z-score
     * @return clamped effective temperature
     */
    public float computeEffective(double zSurprise) {
        if (!adaptiveTemperature) {
            return Math.clamp(baseTemperature, minTemperature, maxTemperature);
        }
        double positiveSurprise = Math.max(0.0, zSurprise);
        float effective = (float) (baseTemperature * (1.0 + temperatureSurpriseCoefficient * positiveSurprise));
        return Math.clamp(effective, minTemperature, maxTemperature);
    }
}
