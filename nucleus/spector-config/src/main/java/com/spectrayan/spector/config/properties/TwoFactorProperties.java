/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Two-Factor Memory (Bjork &amp; Bjork).
 */
public class TwoFactorProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private float sGain = DEFAULT_MEMORY_TWOFACTOR_S_GAIN;
    private float sMax = DEFAULT_MEMORY_TWOFACTOR_S_MAX;
    private float sExponent = DEFAULT_MEMORY_TWOFACTOR_S_EXPONENT;

    public TwoFactorProperties() {}

    public TwoFactorProperties(float sGain, float sMax, float sExponent, boolean enabled) {
        this.sGain = sGain;
        this.sMax = sMax;
        this.sExponent = sExponent;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public float getSGain() { return sGain; }
    public void setSGain(float sGain) { this.sGain = sGain; }

    public float getSMax() { return sMax; }
    public void setSMax(float sMax) { this.sMax = sMax; }

    public float getSExponent() { return sExponent; }
    public void setSExponent(float sExponent) { this.sExponent = sExponent; }

    // ─────────────── Record-Style Accessors & Fluent API ───────────────

    public float sGain() { return getSGain(); }
    public float sMax() { return getSMax(); }
    public float sExponent() { return getSExponent(); }
    public boolean enabled() { return isEnabled(); }

    public TwoFactorProperties sGain(float sGain) { setSGain(sGain); return this; }
    public TwoFactorProperties sMax(float sMax) { setSMax(sMax); return this; }
    public TwoFactorProperties sExponent(float sExponent) { setSExponent(sExponent); return this; }
    public TwoFactorProperties enabled(boolean enabled) { setEnabled(enabled); return this; }

    public static final TwoFactorProperties DEFAULT = new TwoFactorProperties();
    public static final TwoFactorProperties DISABLED = new TwoFactorProperties(
            DEFAULT_MEMORY_TWOFACTOR_S_GAIN, DEFAULT_MEMORY_TWOFACTOR_S_MAX, DEFAULT_MEMORY_TWOFACTOR_S_EXPONENT, false);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TwoFactorProperties that = (TwoFactorProperties) o;
        return enabled == that.enabled &&
                Float.compare(that.sGain, sGain) == 0 &&
                Float.compare(that.sMax, sMax) == 0 &&
                Float.compare(that.sExponent, sExponent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, sGain, sMax, sExponent);
    }

    public TwoFactorProperties copy() {
        return new TwoFactorProperties(sGain, sMax, sExponent, enabled);
    }
}
