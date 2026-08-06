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

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;
import java.util.Objects;

/**
 * SPECTRUM adaptive vector index configuration parameters.
 */
public class SpectrumProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final SpectrumProperties DEFAULTS = new SpectrumProperties(
            SpectorPropertyConstants.DEFAULT_SPECTRUM_N_CENTROIDS,
            SpectorPropertyConstants.DEFAULT_SPECTRUM_N_PROBE,
            SpectorPropertyConstants.DEFAULT_SPECTRUM_SHARD_THRESHOLD,
            SpectorPropertyConstants.DEFAULT_SPECTRUM_OVERSAMPLING_FACTOR,
            SpectorPropertyConstants.DEFAULT_SPECTRUM_KMEANS_ITERATIONS
    );

    private int nCentroids;
    private int nProbe;
    private int shardThreshold;
    private int oversamplingFactor;
    private int kmeansIterations;

    public SpectrumProperties() {
        this(DEFAULTS.nCentroids, DEFAULTS.nProbe, DEFAULTS.shardThreshold,
                DEFAULTS.oversamplingFactor, DEFAULTS.kmeansIterations);
    }

    public SpectrumProperties(int nCentroids, int nProbe, int shardThreshold,
                              int oversamplingFactor, int kmeansIterations) {
        this.nCentroids = nCentroids;
        this.nProbe = nProbe;
        this.shardThreshold = shardThreshold;
        this.oversamplingFactor = oversamplingFactor;
        this.kmeansIterations = kmeansIterations;
    }

    public static SpectrumProperties from(SpectorProperties props) {
        if (props == null) return DEFAULTS;
        return new SpectrumProperties(
                props.getInt(SpectorPropertyConstants.SPECTRUM_N_CENTROIDS, DEFAULTS.nCentroids),
                props.getInt(SpectorPropertyConstants.SPECTRUM_N_PROBE, DEFAULTS.nProbe),
                props.getInt(SpectorPropertyConstants.SPECTRUM_SHARD_THRESHOLD, DEFAULTS.shardThreshold),
                props.getInt(SpectorPropertyConstants.SPECTRUM_OVERSAMPLING_FACTOR, DEFAULTS.oversamplingFactor),
                props.getInt(SpectorPropertyConstants.SPECTRUM_KMEANS_ITERATIONS, DEFAULTS.kmeansIterations)
        );
    }

    public int getNCentroids() { return nCentroids; }
    public void setNCentroids(int nCentroids) { this.nCentroids = nCentroids; }
    public int nCentroids() { return nCentroids; }

    public int getNProbe() { return nProbe; }
    public void setNProbe(int nProbe) { this.nProbe = nProbe; }
    public int nProbe() { return nProbe; }

    public int getShardThreshold() { return shardThreshold; }
    public void setShardThreshold(int shardThreshold) { this.shardThreshold = shardThreshold; }
    public int shardThreshold() { return shardThreshold; }

    public int getOversamplingFactor() { return oversamplingFactor; }
    public void setOversamplingFactor(int oversamplingFactor) { this.oversamplingFactor = oversamplingFactor; }
    public int oversamplingFactor() { return oversamplingFactor; }

    public int getKmeansIterations() { return kmeansIterations; }
    public void setKmeansIterations(int kmeansIterations) { this.kmeansIterations = kmeansIterations; }
    public int kmeansIterations() { return kmeansIterations; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpectrumProperties that = (SpectrumProperties) o;
        return nCentroids == that.nCentroids && nProbe == that.nProbe
                && shardThreshold == that.shardThreshold
                && oversamplingFactor == that.oversamplingFactor
                && kmeansIterations == that.kmeansIterations;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nCentroids, nProbe, shardThreshold, oversamplingFactor, kmeansIterations);
    }

    @Override
    public String toString() {
        return "SpectrumProperties{" +
                "nCentroids=" + nCentroids +
                ", nProbe=" + nProbe +
                ", shardThreshold=" + shardThreshold +
                ", oversamplingFactor=" + oversamplingFactor +
                ", kmeansIterations=" + kmeansIterations +
                '}';
    }
}
