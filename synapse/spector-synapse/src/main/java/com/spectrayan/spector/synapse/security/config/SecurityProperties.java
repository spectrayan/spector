/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security.config;

import java.io.Serializable;

/**
 * Security configuration bound under {@code spector.security.*}.
 *
 * <p>Currently hosts prompt-injection detection settings
 * ({@code spector.security.injection.*}). Nested under {@link
 * com.spectrayan.spector.synapse.config.SynapseProperties} so existing
 * {@code @ConfigurationProperties(prefix = "spector")} binding picks it up.</p>
 */
public class SecurityProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private InjectionProperties injection = new InjectionProperties();

    public InjectionProperties getInjection() {
        return injection;
    }

    public void setInjection(InjectionProperties injection) {
        if (injection != null) {
            this.injection = injection;
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
