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
 * HNSW vector index configuration parameters.
 */
public class HnswProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final HnswProperties DEFAULTS = new HnswProperties(
            SpectorPropertyConstants.DEFAULT_HNSW_M,
            SpectorPropertyConstants.DEFAULT_HNSW_EF_CONSTRUCTION,
            SpectorPropertyConstants.DEFAULT_HNSW_EF_SEARCH
    );

    public static final HnswProperties DEFAULT = DEFAULTS;

    private int m;
    private int efConstruction;
    private int efSearch;
    private int maxLevel0Connections;
    private double levelMultiplier;

    public HnswProperties() {
        this(DEFAULTS.m, DEFAULTS.efConstruction, DEFAULTS.efSearch);
    }

    public HnswProperties(int m, int efConstruction, int efSearch) {
        this(m, efConstruction, efSearch, 2 * m, 1.0 / Math.log(m));
    }

    public HnswProperties(int m, int efConstruction, int efSearch,
                          int maxLevel0Connections, double levelMultiplier) {
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.maxLevel0Connections = maxLevel0Connections > 0 ? maxLevel0Connections : 2 * m;
        this.levelMultiplier = levelMultiplier > 0 ? levelMultiplier : 1.0 / Math.log(m);
    }

    public static HnswProperties from(SpectorProperties props) {
        if (props == null) return DEFAULTS;
        return new HnswProperties(
                props.getInt(SpectorPropertyConstants.HNSW_M, DEFAULTS.m),
                props.getInt(SpectorPropertyConstants.HNSW_EF_CONSTRUCTION, DEFAULTS.efConstruction),
                props.getInt(SpectorPropertyConstants.HNSW_EF_SEARCH, DEFAULTS.efSearch)
        );
    }

    public int getM() { return m; }
    public void setM(int m) {
        this.m = m;
        this.maxLevel0Connections = 2 * m;
        this.levelMultiplier = 1.0 / Math.log(m);
    }
    public int m() { return m; }

    public int getEfConstruction() { return efConstruction; }
    public void setEfConstruction(int efConstruction) { this.efConstruction = efConstruction; }
    public int efConstruction() { return efConstruction; }

    public int getEfSearch() { return efSearch; }
    public void setEfSearch(int efSearch) { this.efSearch = efSearch; }
    public int efSearch() { return efSearch; }

    public int getMaxLevel0Connections() { return maxLevel0Connections; }
    public void setMaxLevel0Connections(int maxLevel0Connections) { this.maxLevel0Connections = maxLevel0Connections; }
    public int maxLevel0Connections() { return maxLevel0Connections; }

    public double getLevelMultiplier() { return levelMultiplier; }
    public void setLevelMultiplier(double levelMultiplier) { this.levelMultiplier = levelMultiplier; }
    public double levelMultiplier() { return levelMultiplier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HnswProperties that = (HnswProperties) o;
        return m == that.m && efConstruction == that.efConstruction &&
                efSearch == that.efSearch && maxLevel0Connections == that.maxLevel0Connections &&
                Double.compare(that.levelMultiplier, levelMultiplier) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m, efConstruction, efSearch, maxLevel0Connections, levelMultiplier);
    }

    @Override
    public String toString() {
        return "HnswProperties{" +
                "m=" + m +
                ", efConstruction=" + efConstruction +
                ", efSearch=" + efSearch +
                ", maxLevel0Connections=" + maxLevel0Connections +
                ", levelMultiplier=" + levelMultiplier +
                '}';
    }
}
