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
package com.spectrayan.spector.bench.scale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes exact percentile distributions (p50, p95, p99) and arithmetic averages for benchmark latencies.
 */
public final class PercentileTracker {

    /**
     * Tolerance epsilon to prevent IEEE 754 floating-point representation drift
     * from causing ceiling jumps on exact integer multiples (e.g. 0.07 * 100 = 7.000000000000001).
     */
    private static final double EPSILON = 1e-9;

    private final List<Double> values = new ArrayList<>();

    public void record(double value) {
        values.add(value);
    }

    public int count() {
        return values.size();
    }

    public double p50() {
        return percentile(0.50);
    }

    public double p95() {
        return percentile(0.95);
    }

    public double p99() {
        return percentile(0.99);
    }

    public double min() {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = sortedCopy();
        return sorted.get(0);
    }

    public double max() {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = sortedCopy();
        return sorted.get(sorted.size() - 1);
    }

    public double avg() {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    public double percentile(double rank) {
        if (values.isEmpty()) return 0.0;
        if (Double.isNaN(rank) || rank <= 0.0) return min();
        if (rank >= 1.0) return max();

        List<Double> sorted = sortedCopy();
        int index = (int) Math.ceil((rank * sorted.size()) - EPSILON) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;
        return sorted.get(index);
    }

    private List<Double> sortedCopy() {
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }
}
