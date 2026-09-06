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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.config.properties.TwoFactorProperties;

/**
 * Configuration for the Two-Factor Memory model (Bjork &amp; Bjork, 1992).
 *
 * @deprecated Use {@link TwoFactorProperties} directly from {@code spector-config}.
 *             This compatibility subclass will be removed in a future release.
 * @since 1.4.0
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public class TwoFactorConfig extends TwoFactorProperties {

    public static final TwoFactorConfig DEFAULT = new TwoFactorConfig();
    public static final TwoFactorConfig DISABLED = new TwoFactorConfig(0.0f, 0.0f, 0.0f, false);

    public TwoFactorConfig() {
        super();
    }

    public TwoFactorConfig(float sGain, float sMax, float sExponent, boolean enabled) {
        super(sGain, sMax, sExponent, enabled);
    }

    /**
     * Compatibility bridge from {@link TwoFactorProperties}.
     */
    public static TwoFactorConfig from(TwoFactorProperties props) {
        if (props == null) {
            return DEFAULT;
        }
        if (props instanceof TwoFactorConfig tfc) {
            return tfc;
        }
        return new TwoFactorConfig(props.getSGain(), props.getSMax(), props.getSExponent(), props.isEnabled());
    }
}
