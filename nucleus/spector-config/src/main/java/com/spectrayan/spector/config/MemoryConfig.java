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
 * Canonical configuration POJO for Spector Cognitive Memory.
 */
public class MemoryConfig {

    private boolean enabled = true;
    private int maxMemories = 0;
    private String persistenceMode = "DISK";
    private String persistencePath;
    private int dimensions = 768;
    private int capacity = 100_000;

    private boolean spladeEnabled = false;
    private boolean colbertEnabled = false;
    private boolean bundleMode = false;

    private DecayConfig decay = new DecayConfig();
    private ConsolidationConfig consolidation = new ConsolidationConfig();

    public MemoryConfig() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxMemories() { return maxMemories; }
    public void setMaxMemories(int maxMemories) { this.maxMemories = maxMemories; }

    public String getPersistenceMode() { return persistenceMode; }
    public void setPersistenceMode(String persistenceMode) { this.persistenceMode = persistenceMode; }

    public String getPersistencePath() { return persistencePath; }
    public void setPersistencePath(String persistencePath) { this.persistencePath = persistencePath; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public boolean isSpladeEnabled() { return spladeEnabled; }
    public void setSpladeEnabled(boolean spladeEnabled) { this.spladeEnabled = spladeEnabled; }

    public boolean isColbertEnabled() { return colbertEnabled; }
    public void setColbertEnabled(boolean colbertEnabled) { this.colbertEnabled = colbertEnabled; }

    public boolean isBundleMode() { return bundleMode; }
    public void setBundleMode(boolean bundleMode) { this.bundleMode = bundleMode; }

    public DecayConfig getDecay() { return decay; }
    public void setDecay(DecayConfig decay) { this.decay = decay; }

    public ConsolidationConfig getConsolidation() { return consolidation; }
    public void setConsolidation(ConsolidationConfig consolidation) { this.consolidation = consolidation; }

    // Record-style accessors for backward compatibility
    public boolean enabled() { return isEnabled(); }
    public int maxMemories() { return getMaxMemories(); }
    public String persistenceMode() { return getPersistenceMode(); }
    public String persistencePath() { return getPersistencePath(); }
    public int dimensions() { return getDimensions(); }
    public int capacity() { return getCapacity(); }
    public boolean spladeEnabled() { return isSpladeEnabled(); }
    public boolean colbertEnabled() { return isColbertEnabled(); }
    public boolean bundleMode() { return isBundleMode(); }
    public DecayConfig decay() { return getDecay(); }
    public ConsolidationConfig consolidation() { return getConsolidation(); }
}
