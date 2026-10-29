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
package com.spectrayan.spector.synapse.idempotency;

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;

/**
 * Configuration properties for server-side HTTP {@code Idempotency-Key} deduplication.
 *
 * <p>Prefix: {@code spector.idempotency.*}</p>
 */
public class IdempotencyProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = SpectorPropertyConstants.DEFAULT_IDEMPOTENCY_ENABLED;
    private long ttlSeconds = SpectorPropertyConstants.DEFAULT_IDEMPOTENCY_TTL_SECONDS;
    private int maxEntries = SpectorPropertyConstants.DEFAULT_IDEMPOTENCY_MAX_ENTRIES;

    public IdempotencyProperties() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }
}
