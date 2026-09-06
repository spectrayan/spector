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

import java.io.Serializable;

public class RememberProperties implements Serializable {

    private String defaultTier = "SEMANTIC";
    
    // Chunking config
    private ChunkProperties chunk = new ChunkProperties();
    
    // File crawler config (feeder)
    private FileCrawlerProperties files = new FileCrawlerProperties();
    
    // ICNU weights
    private IcnuProperties icnu = new IcnuProperties();
    
    // Cognitive write parameters
    private int surpriseWarmup = 10;
    private float flashbulbThreshold = 3.0f;
    private float valenceLearningRate = 0.3f;
    private float deduplicationRadius = 0.05f;
    private long inhibitionTtlMs = 300000L;
    private float inhibitionFloor = 0.1f;
    private float habituationDecayRate = 0.2f;
    private long ltpCooldownMs = 300000L;
    private boolean pinSourceEpisodes = false;
    private int pinnedQuota = 10000;

    public String getDefaultTier() { return defaultTier; }
    public void setDefaultTier(String defaultTier) { this.defaultTier = defaultTier; }
    public String defaultTier() { return defaultTier; }

    public ChunkProperties getChunk() { return chunk; }
    public void setChunk(ChunkProperties chunk) { this.chunk = chunk; }
    public ChunkProperties chunk() { return chunk; }

    public FileCrawlerProperties getFiles() { return files; }
    public void setFiles(FileCrawlerProperties files) { this.files = files; }
    public FileCrawlerProperties files() { return files; }

    public IcnuProperties getIcnu() { return icnu; }
    public void setIcnu(IcnuProperties icnu) { this.icnu = icnu; }
    public IcnuProperties icnu() { return icnu; }

    public int getSurpriseWarmup() { return surpriseWarmup; }
    public void setSurpriseWarmup(int surpriseWarmup) { this.surpriseWarmup = surpriseWarmup; }
    public int surpriseWarmup() { return surpriseWarmup; }

    public float getFlashbulbThreshold() { return flashbulbThreshold; }
    public void setFlashbulbThreshold(float flashbulbThreshold) { this.flashbulbThreshold = flashbulbThreshold; }
    public float flashbulbThreshold() { return flashbulbThreshold; }

    public float getValenceLearningRate() { return valenceLearningRate; }
    public void setValenceLearningRate(float valenceLearningRate) { this.valenceLearningRate = valenceLearningRate; }
    public float valenceLearningRate() { return valenceLearningRate; }

    public float getDeduplicationRadius() { return deduplicationRadius; }
    public void setDeduplicationRadius(float deduplicationRadius) { this.deduplicationRadius = deduplicationRadius; }
    public float deduplicationRadius() { return deduplicationRadius; }

    public long getInhibitionTtlMs() { return inhibitionTtlMs; }
    public void setInhibitionTtlMs(long inhibitionTtlMs) { this.inhibitionTtlMs = inhibitionTtlMs; }
    public long inhibitionTtlMs() { return inhibitionTtlMs; }

    public float getInhibitionFloor() { return inhibitionFloor; }
    public void setInhibitionFloor(float inhibitionFloor) { this.inhibitionFloor = inhibitionFloor; }
    public float inhibitionFloor() { return inhibitionFloor; }

    public float getHabituationDecayRate() { return habituationDecayRate; }
    public void setHabituationDecayRate(float habituationDecayRate) { this.habituationDecayRate = habituationDecayRate; }
    public float habituationDecayRate() { return habituationDecayRate; }

    public long getLtpCooldownMs() { return ltpCooldownMs; }
    public void setLtpCooldownMs(long ltpCooldownMs) { this.ltpCooldownMs = ltpCooldownMs; }
    public long ltpCooldownMs() { return ltpCooldownMs; }

    public boolean isPinSourceEpisodes() { return pinSourceEpisodes; }
    public void setPinSourceEpisodes(boolean pinSourceEpisodes) { this.pinSourceEpisodes = pinSourceEpisodes; }
    public boolean pinSourceEpisodes() { return pinSourceEpisodes; }

    public int getPinnedQuota() { return pinnedQuota; }
    public void setPinnedQuota(int pinnedQuota) { this.pinnedQuota = pinnedQuota; }
    public int pinnedQuota() { return pinnedQuota; }

    public RememberProperties copy() {
        RememberProperties cp = new RememberProperties();
        cp.defaultTier = this.defaultTier;
        cp.chunk = this.chunk != null ? this.chunk.copy() : new ChunkProperties();
        cp.files = this.files != null ? this.files.copy() : new FileCrawlerProperties();
        cp.icnu = this.icnu != null ? this.icnu.copy() : new IcnuProperties();
        cp.surpriseWarmup = this.surpriseWarmup;
        cp.flashbulbThreshold = this.flashbulbThreshold;
        cp.valenceLearningRate = this.valenceLearningRate;
        cp.deduplicationRadius = this.deduplicationRadius;
        cp.inhibitionTtlMs = this.inhibitionTtlMs;
        cp.inhibitionFloor = this.inhibitionFloor;
        cp.habituationDecayRate = this.habituationDecayRate;
        cp.ltpCooldownMs = this.ltpCooldownMs;
        cp.pinSourceEpisodes = this.pinSourceEpisodes;
        cp.pinnedQuota = this.pinnedQuota;
        return cp;
    }

    public static class ChunkProperties implements Serializable {
        private int size = 2500;
        private int overlap = 200;
        private String strategy = "markdown";

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public int size() { return size; }

        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
        public int overlap() { return overlap; }

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String strategy() { return strategy; }

        public ChunkProperties copy() {
            ChunkProperties cp = new ChunkProperties();
            cp.size = this.size;
            cp.overlap = this.overlap;
            cp.strategy = this.strategy;
            return cp;
        }
    }

    public static class FileCrawlerProperties implements Serializable {
        private String rootDirectory = ".";
        private String pattern = "**/*.md";
        private String skipDirs = ".git,.idea,.mvn,target,node_modules,.github";
        private int parallelism = 4;
        private int maxRetries = 3;
        private int retryDelayMs = 2000;

        public String getRootDirectory() { return rootDirectory; }
        public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }
        public String rootDirectory() { return rootDirectory; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String pattern() { return pattern; }

        public String getSkipDirs() { return skipDirs; }
        public void setSkipDirs(String skipDirs) { this.skipDirs = skipDirs; }
        public String skipDirs() { return skipDirs; }

        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int parallelism() { return parallelism; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int maxRetries() { return maxRetries; }

        public int getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(int retryDelayMs) { this.retryDelayMs = retryDelayMs; }
        public int retryDelayMs() { return retryDelayMs; }

        public FileCrawlerProperties copy() {
            FileCrawlerProperties cp = new FileCrawlerProperties();
            cp.rootDirectory = this.rootDirectory;
            cp.pattern = this.pattern;
            cp.skipDirs = this.skipDirs;
            cp.parallelism = this.parallelism;
            cp.maxRetries = this.maxRetries;
            cp.retryDelayMs = this.retryDelayMs;
            return cp;
        }
    }

    public static class IcnuProperties implements Serializable {
        private float threshold = 0.2f;
        private float steepness = 8.0f;
        private float weightInterest = 0.30f;
        private float weightChallenge = 0.10f;
        private float weightNovelty = 0.40f;
        private float weightUrgency = 0.20f;

        public float getThreshold() { return threshold; }
        public void setThreshold(float threshold) { this.threshold = threshold; }
        public float threshold() { return threshold; }

        public float getSteepness() { return steepness; }
        public void setSteepness(float steepness) { this.steepness = steepness; }
        public float steepness() { return steepness; }

        public float getWeightInterest() { return weightInterest; }
        public void setWeightInterest(float weightInterest) { this.weightInterest = weightInterest; }
        public float weightInterest() { return weightInterest; }

        public float getWeightChallenge() { return weightChallenge; }
        public void setWeightChallenge(float weightChallenge) { this.weightChallenge = weightChallenge; }
        public float weightChallenge() { return weightChallenge; }

        public float getWeightNovelty() { return weightNovelty; }
        public void setWeightNovelty(float weightNovelty) { this.weightNovelty = weightNovelty; }
        public float weightNovelty() { return weightNovelty; }

        public float getWeightUrgency() { return weightUrgency; }
        public void setWeightUrgency(float weightUrgency) { this.weightUrgency = weightUrgency; }
        public float weightUrgency() { return weightUrgency; }

        public IcnuProperties copy() {
            IcnuProperties cp = new IcnuProperties();
            cp.threshold = this.threshold;
            cp.steepness = this.steepness;
            cp.weightInterest = this.weightInterest;
            cp.weightChallenge = this.weightChallenge;
            cp.weightNovelty = this.weightNovelty;
            cp.weightUrgency = this.weightUrgency;
            return cp;
        }
    }
}
