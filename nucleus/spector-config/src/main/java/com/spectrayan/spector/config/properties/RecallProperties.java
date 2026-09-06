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

public class RecallProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    // Top-level recall settings
    private String scoringMode = "COGNITIVE";
    private String scoreFusionMode = "MULTIPLICATIVE";
    private float strictnessCoefficient = 1.0f;
    private boolean traceEnabled = false;
    private String mode = "LEARN";  // LEARN, SEARCH, etc.
    private String engine = "pathway"; // pathway, direct, auto, etc.
    private int maxReplayEvents = 100000;
    private boolean includeContradictions = false;
    
    // Sub-property objects
    private MmrProperties mmr = new MmrProperties();
    private TextSearchProperties textSearch = new TextSearchProperties();
    private RerankerProperties reranker = new RerankerProperties();
    private LateralProperties lateral = new LateralProperties();
    private AutoProfileProperties autoProfile = new AutoProfileProperties();
    private ValenceAlignmentProperties valenceAlignment = new ValenceAlignmentProperties();
    
    // Default constructor
    public RecallProperties() {}

    public String getScoringMode() { return scoringMode; }
    public void setScoringMode(String scoringMode) { this.scoringMode = scoringMode; }
    public String scoringMode() { return scoringMode; }

    public String getScoreFusionMode() { return scoreFusionMode; }
    public void setScoreFusionMode(String scoreFusionMode) { this.scoreFusionMode = scoreFusionMode; }
    public String scoreFusionMode() { return scoreFusionMode; }

    public float getStrictnessCoefficient() { return strictnessCoefficient; }
    public void setStrictnessCoefficient(float strictnessCoefficient) { this.strictnessCoefficient = strictnessCoefficient; }
    public float strictnessCoefficient() { return strictnessCoefficient; }

    public boolean isTraceEnabled() { return traceEnabled; }
    public void setTraceEnabled(boolean traceEnabled) { this.traceEnabled = traceEnabled; }
    public boolean traceEnabled() { return traceEnabled; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String mode() { return mode; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String engine() { return engine; }

    public int getMaxReplayEvents() { return maxReplayEvents; }
    public void setMaxReplayEvents(int maxReplayEvents) { this.maxReplayEvents = maxReplayEvents; }
    public int maxReplayEvents() { return maxReplayEvents; }

    public boolean isIncludeContradictions() { return includeContradictions; }
    public void setIncludeContradictions(boolean includeContradictions) { this.includeContradictions = includeContradictions; }
    public boolean includeContradictions() { return includeContradictions; }

    public MmrProperties getMmr() { return mmr; }
    public void setMmr(MmrProperties mmr) { this.mmr = mmr; }
    public MmrProperties mmr() { return mmr; }

    public TextSearchProperties getTextSearch() { return textSearch; }
    public void setTextSearch(TextSearchProperties textSearch) { this.textSearch = textSearch; }
    public TextSearchProperties textSearch() { return textSearch; }

    public RerankerProperties getReranker() { return reranker; }
    public void setReranker(RerankerProperties reranker) { this.reranker = reranker; }
    public RerankerProperties reranker() { return reranker; }

    public LateralProperties getLateral() { return lateral; }
    public void setLateral(LateralProperties lateral) { this.lateral = lateral; }
    public LateralProperties lateral() { return lateral; }

    public AutoProfileProperties getAutoProfile() { return autoProfile; }
    public void setAutoProfile(AutoProfileProperties autoProfile) { this.autoProfile = autoProfile; }
    public AutoProfileProperties autoProfile() { return autoProfile; }

    public ValenceAlignmentProperties getValenceAlignment() { return valenceAlignment; }
    public void setValenceAlignment(ValenceAlignmentProperties valenceAlignment) { this.valenceAlignment = valenceAlignment; }
    public ValenceAlignmentProperties valenceAlignment() { return valenceAlignment; }

    public RecallProperties copy() {
        RecallProperties cp = new RecallProperties();
        cp.scoringMode = this.scoringMode;
        cp.scoreFusionMode = this.scoreFusionMode;
        cp.strictnessCoefficient = this.strictnessCoefficient;
        cp.traceEnabled = this.traceEnabled;
        cp.mode = this.mode;
        cp.engine = this.engine;
        cp.maxReplayEvents = this.maxReplayEvents;
        cp.includeContradictions = this.includeContradictions;
        cp.mmr = this.mmr != null ? this.mmr.copy() : new MmrProperties();
        cp.textSearch = this.textSearch != null ? this.textSearch.copy() : new TextSearchProperties();
        cp.reranker = this.reranker != null ? this.reranker.copy() : new RerankerProperties();
        cp.lateral = this.lateral != null ? this.lateral.copy() : new LateralProperties();
        cp.autoProfile = this.autoProfile != null ? this.autoProfile.copy() : new AutoProfileProperties();
        cp.valenceAlignment = this.valenceAlignment != null ? this.valenceAlignment.copy() : new ValenceAlignmentProperties();
        return cp;
    }

    // Inner classes:
    public static class MmrProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = false;
        private float lambda = 0.5f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public float getLambda() { return lambda; }
        public void setLambda(float lambda) { this.lambda = lambda; }
        public float lambda() { return lambda; }

        public MmrProperties copy() {
            MmrProperties cp = new MmrProperties();
            cp.enabled = this.enabled;
            cp.lambda = this.lambda;
            return cp;
        }
    }
    
    public static class TextSearchProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = true;
        private String mode = "HYBRID";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String mode() { return mode; }

        public TextSearchProperties copy() {
            TextSearchProperties cp = new TextSearchProperties();
            cp.enabled = this.enabled;
            cp.mode = this.mode;
            return cp;
        }
    }
    
    public static class RerankerProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = false;
        private int depth = 50;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public int getDepth() { return depth; }
        public void setDepth(int depth) { this.depth = depth; }
        public int depth() { return depth; }

        public RerankerProperties copy() {
            RerankerProperties cp = new RerankerProperties();
            cp.enabled = this.enabled;
            cp.depth = this.depth;
            return cp;
        }
    }
    
    public static class LateralProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = false;
        private float distanceThreshold = 1.2f;
        private float minTagOverlap = 0.5f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public float getDistanceThreshold() { return distanceThreshold; }
        public void setDistanceThreshold(float distanceThreshold) { this.distanceThreshold = distanceThreshold; }
        public float distanceThreshold() { return distanceThreshold; }

        public float getMinTagOverlap() { return minTagOverlap; }
        public void setMinTagOverlap(float minTagOverlap) { this.minTagOverlap = minTagOverlap; }
        public float minTagOverlap() { return minTagOverlap; }

        public LateralProperties copy() {
            LateralProperties cp = new LateralProperties();
            cp.enabled = this.enabled;
            cp.distanceThreshold = this.distanceThreshold;
            cp.minTagOverlap = this.minTagOverlap;
            return cp;
        }
    }
    
    public static class AutoProfileProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public AutoProfileProperties copy() {
            AutoProfileProperties cp = new AutoProfileProperties();
            cp.enabled = this.enabled;
            return cp;
        }
    }
    
    public static class ValenceAlignmentProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean enabled() { return enabled; }

        public ValenceAlignmentProperties copy() {
            ValenceAlignmentProperties cp = new ValenceAlignmentProperties();
            cp.enabled = this.enabled;
            return cp;
        }
    }
}
