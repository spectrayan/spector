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
package com.spectrayan.spector.config;

/**
 * Memory decay configuration properties.
 */
public class DecayProperties {
    private double minThreshold = 0.1;
    private double baselineHalfLifeDays = 30.0;

    public DecayProperties() {}

    public DecayProperties(double minThreshold, double baselineHalfLifeDays) {
        if (minThreshold >= 0.0 && minThreshold <= 1.0) {
            this.minThreshold = minThreshold;
        }
        if (baselineHalfLifeDays > 0.0) {
            this.baselineHalfLifeDays = baselineHalfLifeDays;
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
}
