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
package com.spectrayan.spector.synapse.replication;

import com.spectrayan.spector.cluster.routing.RoutingKey;

/**
 * Writer for the Redis `:hint` key reserved in Phase 2 and written in Phase 3 (Req R10.5).
 *
 * <p>Key format: {@code rt:ns:{cell:tenant:namespace}:hint}.
 * Informs gateways and readers of the latest replicated snapshot freshness without querying owner.</p>
 */
@FunctionalInterface
public interface ReplicationHintWriter {

    /**
     * Writes the replica freshness hint.
     *
     * @param key routing key
     * @param appliedHwm latest high-water mark replicated
     * @param timestampMs epoch millisecond timestamp of the snapshot
     * @param epoch cluster ownership generation/epoch
     */
    void writeHint(RoutingKey key, long appliedHwm, long timestampMs, long epoch);

    /**
     * Computes the canonical Redis key for the hint (Req R10.5).
     *
     * @param key routing key
     * @return Redis hint key string
     */
    static String hintKeyOf(RoutingKey key) {
        return key.redisHashKey() + ":hint";
    }

    /**
     * No-op hint writer when distributed cache is absent.
     */
    static ReplicationHintWriter noop() {
        return (key, hwm, ts, epoch) -> {};
    }
}
