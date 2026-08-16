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
package com.spectrayan.spector.synapse.provider.usage;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable aggregated token usage statistics stored within Spring Cache.
 *
 * @param inputTokens     total prompt/input tokens
 * @param outputTokens    total completion/output tokens
 * @param embeddingTokens total embedding vector tokens
 * @param totalTokens     grand total tokens
 * @param requestCount    number of operations/requests aggregated
 * @param firstRecorded   timestamp of the earliest recorded event in this bucket
 * @param lastRecorded    timestamp of the most recent recorded event in this bucket
 */
public record TokenUsageStats(
        long inputTokens,
        long outputTokens,
        long embeddingTokens,
        long totalTokens,
        long requestCount,
        Instant firstRecorded,
        Instant lastRecorded
) implements Serializable {

    public TokenUsageStats {
        firstRecorded = firstRecorded != null ? firstRecorded : Instant.now();
        lastRecorded = lastRecorded != null ? lastRecorded : Instant.now();
    }

    /**
     * Creates an empty stats snapshot.
     */
    public static TokenUsageStats empty() {
        Instant now = Instant.now();
        return new TokenUsageStats(0, 0, 0, 0, 0, now, now);
    }

    /**
     * Accumulates a new {@link TokenUsageEvent} into this stats record.
     *
     * @param event the token usage event to add
     * @return updated stats record
     */
    public TokenUsageStats accumulate(TokenUsageEvent event) {
        if (event == null) {
            return this;
        }
        Instant first = this.requestCount == 0 ? event.timestamp() :
                (this.firstRecorded.isBefore(event.timestamp()) ? this.firstRecorded : event.timestamp());
        Instant last = this.lastRecorded.isAfter(event.timestamp()) ? this.lastRecorded : event.timestamp();

        return new TokenUsageStats(
                this.inputTokens + event.inputTokens(),
                this.outputTokens + event.outputTokens(),
                this.embeddingTokens + event.embeddingTokens(),
                this.totalTokens + event.totalTokens(),
                this.requestCount + 1,
                first,
                last
        );
    }

    /**
     * Merges another {@link TokenUsageStats} into this one.
     */
    public TokenUsageStats merge(TokenUsageStats other) {
        if (other == null || other.requestCount == 0) {
            return this;
        }
        if (this.requestCount == 0) {
            return other;
        }
        Instant first = this.firstRecorded.isBefore(other.firstRecorded) ? this.firstRecorded : other.firstRecorded;
        Instant last = this.lastRecorded.isAfter(other.lastRecorded) ? this.lastRecorded : other.lastRecorded;

        return new TokenUsageStats(
                this.inputTokens + other.inputTokens,
                this.outputTokens + other.outputTokens,
                this.embeddingTokens + other.embeddingTokens,
                this.totalTokens + other.totalTokens,
                this.requestCount + other.requestCount,
                first,
                last
        );
    }
}
