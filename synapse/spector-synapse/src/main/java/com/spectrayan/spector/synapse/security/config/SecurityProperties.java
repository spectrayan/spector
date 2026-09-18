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
package com.spectrayan.spector.synapse.security.config;

import com.spectrayan.spector.synapse.security.pii.PiiLevel;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy;

import java.io.Serializable;

/**
 * Security configuration bound under {@code spector.security.*}.
 *
 * <p>Hosts prompt-injection detection ({@code spector.security.injection.*}),
 * PII redaction ({@code spector.security.pii.*}), and tool access policy
 * ({@code spector.security.tool-access.*}). Nested under {@link
 * com.spectrayan.spector.synapse.config.SynapseProperties} so existing
 * {@code @ConfigurationProperties(prefix = "spector")} binding picks it up.</p>
 */
public class SecurityProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private InjectionProperties injection = new InjectionProperties();
    private PiiProperties pii = new PiiProperties();
    private ToolAccessProperties toolAccess = new ToolAccessProperties();

    public InjectionProperties getInjection() {
        return injection;
    }

    public void setInjection(InjectionProperties injection) {
        if (injection != null) {
            this.injection = injection;
        }
    }

    public PiiProperties getPii() {
        return pii;
    }

    public void setPii(PiiProperties pii) {
        if (pii != null) {
            this.pii = pii;
        }
    }

    public ToolAccessProperties getToolAccess() {
        return toolAccess;
    }

    public void setToolAccess(ToolAccessProperties toolAccess) {
        if (toolAccess != null) {
            this.toolAccess = toolAccess;
        }
    }

    /**
     * Prompt-injection detection settings.
     *
     * <ul>
     *   <li>{@code enabled} — master switch; when {@code false}, shield is a no-op</li>
     *   <li>{@code mode} — {@code BLOCK} rejects, {@code WARN} logs only, {@code OFF} disables</li>
     * </ul>
     */
    public static class InjectionProperties implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Master enable flag (also gated by {@link Mode#OFF}). */
        private boolean enabled = true;

        /** Enforcement mode. Default {@link Mode#WARN} for safe rollout. */
        private Mode mode = Mode.WARN;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            if (mode != null) {
                this.mode = mode;
            }
        }

        /** Effective when both {@code enabled} and mode is not {@link Mode#OFF}. */
        public boolean isActive() {
            return enabled && mode != Mode.OFF;
        }

        public boolean isBlock() {
            return isActive() && mode == Mode.BLOCK;
        }

        public boolean isWarn() {
            return isActive() && mode == Mode.WARN;
        }
    }

    /**
     * PII detection and redaction settings ({@code spector.security.pii.*}).
     *
     * <ul>
     *   <li>{@code enabled} — master switch; when {@code false}, interceptor is a no-op</li>
     *   <li>{@code level} — {@code RELAXED} / {@code MODERATE} / {@code STRICT}</li>
     * </ul>
     */
    public static class PiiProperties implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Master enable flag. Default {@code true} for safe rollout with MODERATE level. */
        private boolean enabled = true;

        /** Detection sensitivity. Default {@link PiiLevel#MODERATE}. */
        private PiiLevel level = PiiLevel.MODERATE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public PiiLevel getLevel() {
            return level;
        }

        public void setLevel(PiiLevel level) {
            if (level != null) {
                this.level = level;
            }
        }

        public boolean isActive() {
            return enabled;
        }
    }

    /**
     * Per-agent tool authorization ({@code spector.security.tool-access.*}).
     *
     * <ul>
     *   <li>{@code enabled} — master switch; when {@code false}, all registered tools pass policy</li>
     *   <li>{@code default-when-no-entry} — {@code DENY_ALL} / {@code ALLOW_ALL} when no agent entry
     *       matches (overrides YAML {@code default} when set)</li>
     * </ul>
     *
     * <p>Agent allow/deny lists load from classpath {@code security/tool-access.yml} (ADR-0035).</p>
     */
    public static class ToolAccessProperties implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Master enable flag. Default {@code true}. */
        private boolean enabled = true;

        /**
         * Override for YAML {@code default} when no agent entry matches.
         * {@code null} means use the classpath YAML value.
         */
        private ToolAccessPolicy.DefaultMode defaultWhenNoEntry;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public ToolAccessPolicy.DefaultMode getDefaultWhenNoEntry() {
            return defaultWhenNoEntry;
        }

        public void setDefaultWhenNoEntry(ToolAccessPolicy.DefaultMode defaultWhenNoEntry) {
            this.defaultWhenNoEntry = defaultWhenNoEntry;
        }

        public boolean isActive() {
            return enabled;
        }
    }

    /** Injection enforcement mode. */
    public enum Mode {
        /** Reject detected injections with an error. */
        BLOCK,
        /** Log detections but allow content through. */
        WARN,
        /** Disable detection regardless of {@code enabled}. */
        OFF
    }
}
