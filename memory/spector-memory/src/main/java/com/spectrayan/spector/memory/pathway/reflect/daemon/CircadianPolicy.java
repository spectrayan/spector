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
package com.spectrayan.spector.memory.pathway.reflect.daemon;

import com.spectrayan.spector.config.properties.CircadianProperties;

import java.time.Duration;

/**
 * Configuration for the {@link ReflectDaemon}'s sleep cycle triggers.
 *
 * @deprecated Use {@link CircadianProperties} directly from {@code spector-config}.
 *             This compatibility subclass will be removed in a future release.
 * @since 1.4.0
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public class CircadianPolicy extends CircadianProperties {

    public static final CircadianPolicy DEFAULT = new CircadianPolicy();

    public CircadianPolicy() {
        super();
    }

    public CircadianPolicy(int volumeTrigger, Duration timeTrigger, float tombstoneThreshold,
                           float decayPruneThreshold, float interferenceThreshold, float interferenceDecayFactor) {
        super();
        setVolumeTrigger(volumeTrigger);
        setTimeTrigger(timeTrigger);
        setTombstoneThreshold(tombstoneThreshold);
        setDecayPruneThreshold(decayPruneThreshold);
        setInterferenceThreshold(interferenceThreshold);
        setInterferenceDecayFactor(interferenceDecayFactor);
    }

    public static CircadianPolicy from(CircadianProperties props) {
        if (props == null) {
            return DEFAULT;
        }
        if (props instanceof CircadianPolicy cp) {
            return cp;
        }
        return new CircadianPolicy(
                props.getVolumeTrigger(),
                props.timeTrigger(),
                props.getTombstoneThreshold(),
                props.getDecayPruneThreshold(),
                props.getInterferenceThreshold(),
                props.getInterferenceDecayFactor()
        );
    }

    public static CircadianProperties.Builder builder() {
        return CircadianProperties.builder();
    }
}
