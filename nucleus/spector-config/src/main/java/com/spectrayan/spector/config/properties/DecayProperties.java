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

/**
 * Memory decay configuration properties.
 */
public class DecayProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int BUCKET_COUNT = 12;

    private double minThreshold = DEFAULT_DECAY_MIN_IMPORTANCE;
    private double baselineHalfLifeDays = DEFAULT_DECAY_HALF_LIFE_DAYS;
    private float exponent = DEFAULT_MEMORY_DECAY_EXPONENT;
    private float floor = DEFAULT_MEMORY_DECAY_FLOOR;
    private float[] buckets;

    public static final DecayProperties DEFAULT = new DecayProperties(DEFAULT_MEMORY_DECAY_EXPONENT, DEFAULT_MEMORY_DECAY_FLOOR, null);
    public static final DecayProperties SLOW_FORGET = new DecayProperties(0.08f, 0.15f, null);
    public static final DecayProperties FAST_FORGET = new DecayProperties(0.30f, 0.05f, null);

    public static DecayProperties from(DecayProperties p) {
        return p != null ? p.copy() : DEFAULT;
    }

    public DecayProperties() {}

    public DecayProperties(double minThreshold, double baselineHalfLifeDays) {
        if (minThreshold >= 0.0 && minThreshold <= 1.0) {
            this.minThreshold = minThreshold;
        }
        if (baselineHalfLifeDays > 0.0) {
            this.baselineHalfLifeDays = baselineHalfLifeDays;
        }
    }

    public DecayProperties(float exponent, float floor, float[] buckets) {
        this.exponent = exponent;
        this.floor = floor;
        if (buckets != null) {
            this.buckets = buckets.clone();
        } else {
            this.buckets = computeBuckets(exponent, floor);
        }
    }

    public double getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(double minThreshold) {
        if (minThreshold >= 0.0 && minThreshold <= 1.0) {
            this.minThreshold = minThreshold;
        }
    }

    public double getBaselineHalfLifeDays() {
        return baselineHalfLifeDays;
    }

    public void setBaselineHalfLifeDays(double baselineHalfLifeDays) {
        if (baselineHalfLifeDays > 0.0) {
            this.baselineHalfLifeDays = baselineHalfLifeDays;
        }
    }

    public float getExponent() { return exponent; }
    public void setExponent(float exponent) {
        this.exponent = exponent;
        this.buckets = computeBuckets(this.exponent, this.floor);
    }

    public float getFloor() { return floor; }
    public void setFloor(float floor) {
        this.floor = floor;
        this.buckets = computeBuckets(this.exponent, this.floor);
    }

    public float[] getBuckets() { return buckets(); }
    public void setBuckets(float[] buckets) {
        this.buckets = buckets != null ? buckets.clone() : computeBuckets(exponent, floor);
    }

    public double minThreshold() { return getMinThreshold(); }
    public double baselineHalfLifeDays() { return getBaselineHalfLifeDays(); }
    public float exponent() { return getExponent(); }
    public float floor() { return getFloor(); }
    public float[] buckets() {
        if (buckets == null) {
            buckets = computeBuckets(exponent, floor);
        }
        return buckets;
    }

    public static float[] computeBuckets(float d, float floor) {
        final double[] midpointHours = {
                0.5,      // 0–1 hours
                3.5,      // 1–6 hours
                15.0,     // 6–24 hours
                48.0,     // 1–3 days
                120.0,    // 3–7 days
                420.0,    // 1–4 weeks
                1440.0,   // 1–3 months
                3240.0,   // 3–6 months
                6480.0,   // 6–12 months
                13140.0,  // 1–2 years
                30660.0,  // 2–5 years
                61320.0   // 5+ years
        };

        float[] result = new float[BUCKET_COUNT];
        double base = Math.pow(midpointHours[0], -d);
        for (int i = 0; i < BUCKET_COUNT; i++) {
            double raw = Math.pow(midpointHours[i], -d) / base;
            result[i] = Math.max(floor, (float) raw);
        }
        result[0] = 1.00f;
        return result;
    }

    public DecayProperties copy() {
        DecayProperties cp = new DecayProperties();
        cp.setMinThreshold(this.minThreshold);
        cp.setBaselineHalfLifeDays(this.baselineHalfLifeDays);
        cp.setExponent(this.exponent);
        cp.setFloor(this.floor);
        cp.buckets = this.buckets != null ? this.buckets.clone() : null;
        return cp;
    }
}
