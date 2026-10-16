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
package com.spectrayan.spector.synapse.admin.analytics.dto;

import java.time.Instant;
import java.util.List;

/**
 * Strictly content-free Data Transfer Objects for multi-level administrative analytics rollups
 * at {@code /api/v1/admin/analytics} (ADR-0083 §5.8, ADR-0087, Issue #1058).
 *
 * <p><strong>Content-Free Invariant</strong>: None of the records in this class may declare
 * any memory content fields ({@code text}, {@code vector}, {@code quantizedVector}, {@code payload},
 * {@code tags}, {@code metadata}, {@code rawSecret}, {@code secret}, {@code ciphertext}).</p>
 */
public final class AdminAnalyticsDto {

    private AdminAnalyticsDto() {}

    /**
     * Rollup aggregation scope hierarchy.
     */
    public enum AnalyticsScope {
        FLEET,
        TENANT,
        ACCOUNT,
        NAMESPACE
    }

    /**
     * Memory count breakdown by cognitive tier.
     */
    public record TierBreakdown(
            long total,
            long working,
            long episodic,
            long semantic,
            long procedural
    ) {
        public static TierBreakdown empty() {
            return new TierBreakdown(0L, 0L, 0L, 0L, 0L);
        }

        public TierBreakdown add(TierBreakdown other) {
            if (other == null) return this;
            return new TierBreakdown(
                    this.total + other.total,
                    this.working + other.working,
                    this.episodic + other.episodic,
                    this.semantic + other.semantic,
                    this.procedural + other.procedural
            );
        }
    }

    /**
     * Memory corpus growth deltas over rolling windows.
     */
    public record GrowthStats(
            long currentTotalMemories,
            long delta24hMemories,
            long delta7dMemories,
            long delta30dMemories,
            double growthRate24hPct
    ) {
        public static GrowthStats empty() {
            return new GrowthStats(0L, 0L, 0L, 0L, 0.0);
        }
    }

    /**
     * Throughput (ops/sec), latency percentiles (p50/p95/p99/mean), and error rate telemetry.
     */
    public record ThroughputAndLatency(
            long rememberTotal,
            long recallTotal,
            double rememberOpsPerSec,
            double recallOpsPerSec,
            double latencyP50Ms,
            double latencyP95Ms,
            double latencyP99Ms,
            double latencyMeanMs,
            long errorCount,
            double errorRate
    ) {
        public static ThroughputAndLatency empty() {
            return new ThroughputAndLatency(0L, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0);
        }
    }

    /**
     * Consolidation, sleep/dream reflection cycles, decay, and cognitive graph structure counts.
     */
    public record ConsolidationAndDreamHealth(
            long consolidationsRun,
            long dreamCyclesRun,
            long decayedEngrams,
            long hebbianEdges,
            long temporalLinks,
            long entityNodes,
            long entityEdges
    ) {
        public static ConsolidationAndDreamHealth empty() {
            return new ConsolidationAndDreamHealth(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        public ConsolidationAndDreamHealth add(ConsolidationAndDreamHealth other) {
            if (other == null) return this;
            return new ConsolidationAndDreamHealth(
                    this.consolidationsRun + other.consolidationsRun,
                    this.dreamCyclesRun + other.dreamCyclesRun,
                    this.decayedEngrams + other.decayedEngrams,
                    this.hebbianEdges + other.hebbianEdges,
                    this.temporalLinks + other.temporalLinks,
                    this.entityNodes + other.entityNodes,
                    this.entityEdges + other.entityEdges
            );
        }
    }

    /**
     * Aggregated LLM and embedding token usage and estimated cost in USD.
     */
    public record TokenUsageRollup(
            long inputTokens,
            long outputTokens,
            long embeddingTokens,
            long totalTokens,
            long requestCount,
            double estimatedCostUsd
    ) {
        public static TokenUsageRollup empty() {
            return new TokenUsageRollup(0L, 0L, 0L, 0L, 0L, 0.0);
        }

        public TokenUsageRollup add(TokenUsageRollup other) {
            if (other == null) return this;
            return new TokenUsageRollup(
                    this.inputTokens + other.inputTokens,
                    this.outputTokens + other.outputTokens,
                    this.embeddingTokens + other.embeddingTokens,
                    this.totalTokens + other.totalTokens,
                    this.requestCount + other.requestCount,
                    Math.round((this.estimatedCostUsd + other.estimatedCostUsd) * 1_000_000.0) / 1_000_000.0
            );
        }
    }

    /**
     * Quota utilization and breach counters across memories, storage bytes, namespaces, and accounts.
     */
    public record QuotaUtilizationRollup(
            long memoryCountUsed,
            long memoryCountQuota,
            double memoryUtilizationPct,
            long storageBytesUsed,
            long storageBytesQuota,
            double storageUtilizationPct,
            long namespaceCountUsed,
            long namespaceCountQuota,
            double namespaceUtilizationPct,
            long accountCountUsed,
            long accountCountQuota,
            long quotaBreachCount
    ) {
        public static QuotaUtilizationRollup empty() {
            return new QuotaUtilizationRollup(0L, -1L, 0.0, 0L, -1L, 0.0, 0L, -1L, 0.0, 0L, -1L, 0L);
        }
    }

    /**
     * Per-namespace analytics rollup.
     */
    public record NamespaceAnalyticsRollup(
            String namespaceId,
            String slug,
            String displayName,
            String tenantId,
            String ownerAccountId,
            String status,
            boolean legalHold,
            long storageBytes,
            TierBreakdown tiers,
            GrowthStats growth,
            ThroughputAndLatency throughput,
            ConsolidationAndDreamHealth consolidation,
            QuotaUtilizationRollup quotas,
            Instant generatedAt
    ) {}

    /**
     * Per-account analytics rollup across all owned namespaces.
     */
    public record AccountAnalyticsRollup(
            String accountId,
            String username,
            String displayName,
            String principalType,
            String tenantId,
            int namespaceCount,
            long storageBytes,
            TierBreakdown tiers,
            GrowthStats growth,
            ThroughputAndLatency throughput,
            ConsolidationAndDreamHealth consolidation,
            TokenUsageRollup tokenUsage,
            QuotaUtilizationRollup quotas,
            List<NamespaceAnalyticsRollup> namespaces,
            Instant generatedAt
    ) {}

    /**
     * Per-tenant analytics rollup across all accounts and namespaces in a tenant.
     */
    public record TenantAnalyticsRollup(
            String tenantId,
            String displayName,
            String plan,
            String status,
            int accountCount,
            int humanCount,
            int agentCount,
            int namespaceCount,
            long storageBytes,
            TierBreakdown tiers,
            GrowthStats growth,
            ThroughputAndLatency throughput,
            ConsolidationAndDreamHealth consolidation,
            TokenUsageRollup tokenUsage,
            QuotaUtilizationRollup quotas,
            List<AccountAnalyticsRollup> accounts,
            List<NamespaceAnalyticsRollup> namespaces,
            Instant generatedAt
    ) {}

    /**
     * Compact per-tenant summary row included in fleet-wide analytics.
     */
    public record TenantSummaryItem(
            String tenantId,
            String displayName,
            String plan,
            String status,
            int accountCount,
            int namespaceCount,
            long totalMemories,
            long storageBytes,
            double recallOpsPerSec,
            double rememberOpsPerSec,
            double latencyP95Ms,
            long totalTokens,
            double estimatedCostUsd,
            long quotaBreachCount
    ) {}

    /**
     * Fleet-wide analytics rollup for Platform Operators ({@code super-admin}).
     */
    public record FleetAnalyticsRollup(
            int tenantCount,
            int activeTenantCount,
            int accountCount,
            int namespaceCount,
            long storageBytes,
            TierBreakdown tiers,
            GrowthStats growth,
            ThroughputAndLatency throughput,
            ConsolidationAndDreamHealth consolidation,
            TokenUsageRollup tokenUsage,
            QuotaUtilizationRollup quotas,
            List<TenantSummaryItem> tenants,
            Instant generatedAt
    ) {}

    /**
     * Single time-series data point from {@code memory_analytics_snapshot}.
     */
    public record AnalyticsTimeseriesPoint(
            Instant timestamp,
            long totalCount,
            long workingCount,
            long episodicCount,
            long semanticCount,
            long proceduralCount,
            long storageBytes,
            long recallCount,
            long rememberCount,
            double avgLatencyMs,
            long consolidationsRun,
            long dreamCyclesRun,
            long errorCount,
            long hebbianEdges,
            long entityNodes
    ) {}

    /**
     * Historical time-series response for fleet, tenant, account, or namespace analytics.
     */
    public record AnalyticsTimeseriesResponse(
            AnalyticsScope scope,
            String tenantId,
            String accountId,
            String namespaceId,
            List<AnalyticsTimeseriesPoint> points,
            int totalPoints,
            Instant generatedAt
    ) {}
}
