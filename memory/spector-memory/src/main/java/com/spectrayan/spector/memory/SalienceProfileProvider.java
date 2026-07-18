/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.model.SalienceProfile;

/**
 * Service Provider Interface for salience profile resolution.
 *
 * <h3>Design</h3>
 * <p>The core engine is <b>scope-blind</b>. It receives a pre-merged
 * {@link SalienceProfile} through this interface and uses it for importance
 * modulation at ingestion and scoring at recall. The core has no knowledge
 * of tenants, agents, users, or merge policies.</p>
 *
 * <h3>Enterprise Implementation</h3>
 * <p>The enterprise layer implements this interface to resolve the effective
 * salience profile by loading tenant → agent → user profiles and merging
 * them according to the tenant's {@code OverridePolicy}.</p>
 *
 * <h3>OSS / Default</h3>
 * <p>In OSS mode (no enterprise layer), use {@link #noop()} which returns
 * {@link SalienceProfile#NEUTRAL} — no importance modulation.</p>
 *
 * @see SalienceProfile
 */
public interface SalienceProfileProvider {

    /**
     * Returns the effective (pre-merged) salience profile for the current context.
     *
     * <p>The implementation is responsible for resolving the correct profile
     * based on the current request context (tenant, agent, user). The core
     * engine calls this during ingestion and recall.</p>
     *
     * @return the effective salience profile (never null)
     */
    SalienceProfile effectiveProfile();

    /**
     * Returns a no-op provider that always returns {@link SalienceProfile#NEUTRAL}.
     *
     * <p>Use for OSS deployments, tests, and backward compatibility.</p>
     */
    static SalienceProfileProvider noop() {
        return () -> SalienceProfile.NEUTRAL;
    }
}
