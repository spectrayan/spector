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
package com.spectrayan.spector.synapse.admin.analytics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.GraphStats;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AccountAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AnalyticsScope;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AnalyticsTimeseriesPoint;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AnalyticsTimeseriesResponse;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.ConsolidationAndDreamHealth;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.FleetAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.GrowthStats;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.NamespaceAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.QuotaUtilizationRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.TenantAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.TenantSummaryItem;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.ThroughputAndLatency;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.TierBreakdown;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.TokenUsageRollup;
import com.spectrayan.spector.synapse.admin.tenant.TenantRecord;
import com.spectrayan.spector.synapse.admin.tenant.TenantService;
import com.spectrayan.spector.synapse.admin.tenant.TenantStatus;
import com.spectrayan.spector.synapse.admin.tenant.dto.TenantDto.TenantResponse;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageStats;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.spectrayan.spector.synapse.security.UserAccountStore;
import com.spectrayan.spector.synapse.security.UserRow;

/**
 * Administrative service providing multi-level content-free analytics rollups across
 * fleet ({@code super-admin}), tenant, account, and namespace scopes (ADR-0083 §5.8, ADR-0087, Issue #1058).
 *
 * <p><strong>Content-Free Guarantee</strong>: Reads only numerical telemetry from Micrometer,
 * {@code memory_analytics_snapshot}, {@link TokenUsageTracker}, and structural census counts.
 * Never reads or returns memory text, vectors, tags, or user payloads.</p>
 */
@Service
@DependsOn("flyway")
public class AdminAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsService.class);

    // Blended token pricing defaults (USD per token) for cost estimation
    private static final double INPUT_TOKEN_RATE_USD = 0.000003;     // $3.00 / 1M tokens
    private static final double OUTPUT_TOKEN_RATE_USD = 0.000015;    // $15.00 / 1M tokens
    private static final double EMBEDDING_TOKEN_RATE_USD = 0.0000001; // $0.10 / 1M tokens

    private final JdbcClient jdbc;
    private final TenantService tenantService;
    private final UserAccountStore userAccountStore;
    private final AccountCatalog accountCatalog;
    private final SqlQueryLoader sqlLoader;
    private final SynapseProperties synapseProps;
    private final ObjectProvider<TokenUsageTracker> tokenUsageTrackerProvider;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Autowired
    public AdminAnalyticsService(
            JdbcClient jdbc,
            TenantService tenantService,
            UserAccountStore userAccountStore,
            AccountCatalog accountCatalog,
            SqlQueryLoader sqlLoader,
            @Autowired(required = false) SynapseProperties synapseProps,
            ObjectProvider<TokenUsageTracker> tokenUsageTrackerProvider,
            ObjectProvider<MemoryRegistry> memoryRegistryProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
        this.userAccountStore = Objects.requireNonNull(userAccountStore, "userAccountStore must not be null");
        this.accountCatalog = Objects.requireNonNull(accountCatalog, "accountCatalog must not be null");
        this.sqlLoader = sqlLoader != null ? sqlLoader : new SqlQueryLoader();
        this.synapseProps = synapseProps;
        this.tokenUsageTrackerProvider = tokenUsageTrackerProvider;
        this.memoryRegistryProvider = memoryRegistryProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Computes fleet-wide analytics rollup across all tenants.
     * Restricted strictly to Platform Operators ({@code super-admin}).
     */
    public FleetAnalyticsRollup getFleetAnalytics() {
        String callerId = SecurityUtils.getUserId();
        if (!SecurityUtils.isSuperAdmin()) {
            throw new CrossTenantAccessException(callerId, "fleet-analytics", "all");
        }

        Instant now = Instant.now();
        List<TenantResponse> tenants = tenantService.listTenants(null, false);
        List<NamespaceMetaRow> allNamespaces = loadAllNamespaces(null);

        Map<String, List<NamespaceMetaRow>> nsByTenant = new LinkedHashMap<>();
        for (NamespaceMetaRow ns : allNamespaces) {
            nsByTenant.computeIfAbsent(ns.tenantId(), k -> new ArrayList<>()).add(ns);
        }

        TierBreakdown fleetTiers = TierBreakdown.empty();
        ConsolidationAndDreamHealth fleetConsolidation = ConsolidationAndDreamHealth.empty();
        TokenUsageRollup fleetTokens = TokenUsageRollup.empty();
        long fleetStorageBytes = 0L;
        long fleetQuotaBreaches = 0L;
        int totalAccounts = 0;
        int activeTenants = 0;

        List<NamespaceAnalyticsRollup> allNsRollups = new ArrayList<>();
        List<TenantSummaryItem> tenantSummaries = new ArrayList<>();

        for (TenantResponse tenant : tenants) {
            if (tenant.status() == TenantStatus.ACTIVE) {
                activeTenants++;
            }
            totalAccounts += (int) tenant.accountCount();

            List<NamespaceMetaRow> tenantNsRows = nsByTenant.getOrDefault(tenant.tenantId(), List.of());
            List<NamespaceAnalyticsRollup> tenantNsRollups = tenantNsRows.stream()
                    .map(r -> computeNamespaceRollup(r, now))
                    .toList();
            allNsRollups.addAll(tenantNsRollups);

            TierBreakdown tTiers = TierBreakdown.empty();
            long tStorage = 0L;
            for (NamespaceAnalyticsRollup nr : tenantNsRollups) {
                tTiers = tTiers.add(nr.tiers());
                tStorage += nr.storageBytes();
                fleetConsolidation = fleetConsolidation.add(nr.consolidation());
            }
            fleetTiers = fleetTiers.add(tTiers);
            fleetStorageBytes += tStorage;

            ThroughputAndLatency tThroughput = aggregateThroughput(tenantNsRollups);
            TokenUsageRollup tTokens = computeTenantTokenUsage(tenant.tenantId());
            fleetTokens = fleetTokens.add(tTokens);

            long tBreaches = readQuotaBreaches(tenant.tenantId());
            fleetQuotaBreaches += tBreaches;

            tenantSummaries.add(new TenantSummaryItem(
                    tenant.tenantId(),
                    tenant.name(),
                    tenant.plan(),
                    tenant.status().name(),
                    (int) tenant.accountCount(),
                    tenantNsRollups.size(),
                    tTiers.total(),
                    tStorage,
                    tThroughput.recallOpsPerSec(),
                    tThroughput.rememberOpsPerSec(),
                    tThroughput.latencyP95Ms(),
                    tTokens.totalTokens(),
                    tTokens.estimatedCostUsd(),
                    tBreaches
            ));
        }

        // Include global token usage if global tracker exceeds summed tenant accounts
        TokenUsageRollup globalTokens = computeGlobalTokenUsage();
        if (globalTokens.totalTokens() > fleetTokens.totalTokens()) {
            fleetTokens = globalTokens;
        }

        ThroughputAndLatency fleetThroughput = aggregateThroughput(allNsRollups);
        GrowthStats fleetGrowth = aggregateGrowth(allNsRollups);
        QuotaUtilizationRollup fleetQuotas = new QuotaUtilizationRollup(
                fleetTiers.total(),
                -1L,
                0.0,
                fleetStorageBytes,
                -1L,
                0.0,
                allNsRollups.size(),
                -1L,
                0.0,
                totalAccounts,
                -1L,
                fleetQuotaBreaches
        );

        return new FleetAnalyticsRollup(
                tenants.size(),
                activeTenants,
                totalAccounts,
                allNsRollups.size(),
                fleetStorageBytes,
                fleetTiers,
                fleetGrowth,
                fleetThroughput,
                fleetConsolidation,
                fleetTokens,
                fleetQuotas,
                tenantSummaries,
                now
        );
    }

    /**
     * Computes tenant-level analytics rollup.
     * Tenant {@code admin} can query only their own tenant; cross-tenant queries fail with HTTP 403 ({@code SPE-820-001}).
     */
    public TenantAnalyticsRollup getTenantAnalytics(String requestedTenantId) {
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String targetTenantId = (requestedTenantId != null && !requestedTenantId.isBlank())
                ? requestedTenantId.trim()
                : callerTenant;

        if (!isSuperAdmin && !targetTenantId.equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, "tenant-analytics:" + targetTenantId, targetTenantId);
        }

        Optional<TenantRecord> tenantOpt = tenantService.findTenantRecord(targetTenantId);
        if (tenantOpt.isEmpty() && !"default".equals(targetTenantId)) {
            throw new SynapseNotFoundException("Tenant", targetTenantId);
        }

        Instant now = Instant.now();
        String displayName = tenantOpt.map(TenantRecord::name).orElse("Default Tenant");
        String plan = tenantOpt.map(TenantRecord::plan).orElse("ENTERPRISE");
        String status = tenantOpt.map(t -> t.status().name()).orElse("ACTIVE");

        List<UserRow> tenantUsers = loadTenantUsers(targetTenantId);
        List<NamespaceMetaRow> tenantNamespaces = loadAllNamespaces(targetTenantId);

        Map<String, List<NamespaceMetaRow>> nsByOwner = new LinkedHashMap<>();
        for (NamespaceMetaRow ns : tenantNamespaces) {
            nsByOwner.computeIfAbsent(ns.ownerAccountId(), k -> new ArrayList<>()).add(ns);
        }

        List<NamespaceAnalyticsRollup> nsRollups = tenantNamespaces.stream()
                .map(r -> computeNamespaceRollup(r, now))
                .toList();

        Map<String, NamespaceAnalyticsRollup> rollupByNsId = new LinkedHashMap<>();
        for (NamespaceAnalyticsRollup nr : nsRollups) {
            rollupByNsId.put(nr.namespaceId(), nr);
        }

        List<AccountAnalyticsRollup> accountRollups = new ArrayList<>();
        int humanCount = 0;
        int agentCount = 0;

        for (UserRow u : tenantUsers) {
            String pType = resolvePrincipalType(u.userId());
            if ("AGENT".equalsIgnoreCase(pType)) {
                agentCount++;
            } else {
                humanCount++;
            }
            List<NamespaceMetaRow> ownedRows = nsByOwner.getOrDefault(u.userId(), List.of());
            List<NamespaceAnalyticsRollup> ownedRollups = ownedRows.stream()
                    .map(r -> rollupByNsId.get(r.namespaceId()))
                    .filter(Objects::nonNull)
                    .toList();
            accountRollups.add(buildAccountRollup(u, pType, ownedRollups, now));
        }

        TierBreakdown tiers = TierBreakdown.empty();
        ConsolidationAndDreamHealth consolidation = ConsolidationAndDreamHealth.empty();
        long storageBytes = 0L;
        for (NamespaceAnalyticsRollup nr : nsRollups) {
            tiers = tiers.add(nr.tiers());
            consolidation = consolidation.add(nr.consolidation());
            storageBytes += nr.storageBytes();
        }

        ThroughputAndLatency throughput = aggregateThroughput(nsRollups);
        GrowthStats growth = aggregateGrowth(nsRollups);
        TokenUsageRollup tokenUsage = computeTenantTokenUsage(targetTenantId);

        long maxStorageQuota = tenantOpt.map(TenantRecord::maxStorageBytes).orElse(-1L);
        long maxNamespacesQuota = tenantOpt.map(t -> (long) t.maxNamespaces()).orElse(-1L);
        long maxMemoriesQuota = -1L;
        long maxAccountsQuota = tenantOpt.map(t -> (long) t.maxAccounts()).orElse(-1L);

        QuotaUtilizationRollup quotas = new QuotaUtilizationRollup(
                tiers.total(),
                maxMemoriesQuota,
                utilizationPct(tiers.total(), maxMemoriesQuota),
                storageBytes,
                maxStorageQuota,
                utilizationPct(storageBytes, maxStorageQuota),
                nsRollups.size(),
                maxNamespacesQuota,
                utilizationPct(nsRollups.size(), maxNamespacesQuota),
                tenantUsers.size(),
                maxAccountsQuota,
                readQuotaBreaches(targetTenantId)
        );

        return new TenantAnalyticsRollup(
                targetTenantId,
                displayName,
                plan,
                status,
                tenantUsers.size(),
                humanCount,
                agentCount,
                nsRollups.size(),
                storageBytes,
                tiers,
                growth,
                throughput,
                consolidation,
                tokenUsage,
                quotas,
                accountRollups,
                nsRollups,
                now
        );
    }

    /**
     * Computes account-level analytics rollup across all namespaces owned by {@code accountId}.
     */
    public AccountAnalyticsRollup getAccountAnalytics(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new SynapseNotFoundException("Account", "");
        }
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        UserRow user = userAccountStore.findByUserId(accountId.trim())
                .or(() -> userAccountStore.findByUsername(accountId.trim()))
                .orElseThrow(() -> new SynapseNotFoundException("Account", accountId));

        String accountTenant = (user.tenantId() != null && !user.tenantId().isBlank()) ? user.tenantId() : "default";
        if (!isSuperAdmin && !accountTenant.equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, user.userId(), accountTenant);
        }

        Instant now = Instant.now();
        List<NamespaceMetaRow> ownedNamespaces = loadNamespacesByOwner(user.userId());
        List<NamespaceAnalyticsRollup> nsRollups = ownedNamespaces.stream()
                .map(r -> computeNamespaceRollup(r, now))
                .toList();

        String pType = resolvePrincipalType(user.userId());
        return buildAccountRollup(user, pType, nsRollups, now);
    }

    /**
     * Computes namespace-level analytics rollup for {@code namespaceId}.
     */
    public NamespaceAnalyticsRollup getNamespaceAnalytics(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new NamespaceNotFoundException("");
        }
        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        NamespaceMetaRow row = findNamespaceMeta(namespaceId.trim())
                .orElseThrow(() -> new NamespaceNotFoundException(namespaceId));

        if (!isSuperAdmin && !row.tenantId().equals(callerTenant)) {
            throw new CrossTenantAccessException(callerId, row.namespaceId(), row.tenantId());
        }

        return computeNamespaceRollup(row, Instant.now());
    }

    /**
     * Queries historical time-series rollups from {@code memory_analytics_snapshot}.
     */
    public AnalyticsTimeseriesResponse getTimeseries(
            AnalyticsScope requestedScope,
            String tenantId,
            String accountId,
            String namespaceId,
            Instant from,
            Instant to,
            int limit) {

        String callerId = SecurityUtils.getUserId();
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();
        String callerTenant = resolveCallerTenantId(callerId);

        String normTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : null;
        String normAccount = (accountId != null && !accountId.isBlank()) ? accountId.trim() : null;
        String normNamespace = (namespaceId != null && !namespaceId.isBlank()) ? namespaceId.trim() : null;

        AnalyticsScope scope = requestedScope;
        if (scope == null) {
            if (normNamespace != null) {
                scope = AnalyticsScope.NAMESPACE;
            } else if (normAccount != null) {
                scope = AnalyticsScope.ACCOUNT;
            } else if (normTenant != null) {
                scope = AnalyticsScope.TENANT;
            } else {
                scope = isSuperAdmin ? AnalyticsScope.FLEET : AnalyticsScope.TENANT;
            }
        }

        switch (scope) {
            case FLEET -> {
                if (!isSuperAdmin) {
                    throw new CrossTenantAccessException(callerId, "fleet-timeseries", "all");
                }
            }
            case TENANT -> {
                if (normTenant == null) {
                    normTenant = callerTenant;
                }
                if (!isSuperAdmin && !normTenant.equals(callerTenant)) {
                    throw new CrossTenantAccessException(callerId, "tenant-timeseries:" + normTenant, normTenant);
                }
            }
            case ACCOUNT -> {
                if (normAccount == null) {
                    throw new IllegalArgumentException("accountId is required for ACCOUNT timeseries scope");
                }
                final String targetAccount = normAccount;
                UserRow user = userAccountStore.findByUserId(targetAccount)
                        .or(() -> userAccountStore.findByUsername(targetAccount))
                        .orElseThrow(() -> new SynapseNotFoundException("Account", targetAccount));
                normAccount = user.userId();
                String accTenant = (user.tenantId() != null && !user.tenantId().isBlank()) ? user.tenantId() : "default";
                if (!isSuperAdmin && !accTenant.equals(callerTenant)) {
                    throw new CrossTenantAccessException(callerId, normAccount, accTenant);
                }
                normTenant = accTenant;
            }
            case NAMESPACE -> {
                if (normNamespace == null) {
                    throw new IllegalArgumentException("namespaceId is required for NAMESPACE timeseries scope");
                }
                final String targetNamespace = normNamespace;
                NamespaceMetaRow ns = findNamespaceMeta(targetNamespace)
                        .orElseThrow(() -> new NamespaceNotFoundException(targetNamespace));
                if (!isSuperAdmin && !ns.tenantId().equals(callerTenant)) {
                    throw new CrossTenantAccessException(callerId, normNamespace, ns.tenantId());
                }
                normTenant = ns.tenantId();
                normAccount = ns.ownerAccountId();
            }
        }

        Instant effectiveFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant effectiveTo = to != null ? to : Instant.now().plus(Duration.ofMinutes(5));
        int effectiveLimit = Math.clamp(limit <= 0 ? 100 : limit, 1, 1000);

        List<AnalyticsTimeseriesPoint> points = queryTimeseriesPoints(
                scope, normTenant, normAccount, normNamespace, effectiveFrom, effectiveTo, effectiveLimit);

        return new AnalyticsTimeseriesResponse(
                scope,
                normTenant,
                normAccount,
                normNamespace,
                points,
                points.size(),
                Instant.now()
        );
    }

    // ─────────────────────────── Internal Rollup Helpers ───────────────────────────

    private AccountAnalyticsRollup buildAccountRollup(
            UserRow user,
            String principalType,
            List<NamespaceAnalyticsRollup> ownedRollups,
            Instant now) {

        String accountTenant = (user.tenantId() != null && !user.tenantId().isBlank()) ? user.tenantId() : "default";
        TierBreakdown tiers = TierBreakdown.empty();
        ConsolidationAndDreamHealth consolidation = ConsolidationAndDreamHealth.empty();
        long storageBytes = 0L;

        for (NamespaceAnalyticsRollup nr : ownedRollups) {
            tiers = tiers.add(nr.tiers());
            consolidation = consolidation.add(nr.consolidation());
            storageBytes += nr.storageBytes();
        }

        ThroughputAndLatency throughput = aggregateThroughput(ownedRollups);
        GrowthStats growth = aggregateGrowth(ownedRollups);
        TokenUsageRollup tokenUsage = computeAccountTokenUsage(user.userId());

        long maxNamespacesQuota = -1L;
        long maxMemoriesQuota = -1L;
        try {
            Account catAccount = accountCatalog.getAccount(user.userId());
            if (catAccount != null && catAccount.quotas() != null) {
                maxNamespacesQuota = catAccount.quotas().maxNamespaces();
                maxMemoriesQuota = catAccount.quotas().maxTotalMemories();
            }
        } catch (Exception ignored) {}

        QuotaUtilizationRollup quotas = new QuotaUtilizationRollup(
                tiers.total(),
                maxMemoriesQuota,
                utilizationPct(tiers.total(), maxMemoriesQuota),
                storageBytes,
                -1L,
                0.0,
                ownedRollups.size(),
                maxNamespacesQuota,
                utilizationPct(ownedRollups.size(), maxNamespacesQuota),
                1L,
                1L,
                0L
        );

        return new AccountAnalyticsRollup(
                user.userId(),
                user.username(),
                user.displayName(),
                principalType,
                accountTenant,
                ownedRollups.size(),
                storageBytes,
                tiers,
                growth,
                throughput,
                consolidation,
                tokenUsage,
                quotas,
                ownedRollups,
                now
        );
    }

    private NamespaceAnalyticsRollup computeNamespaceRollup(NamespaceMetaRow row, Instant now) {
        String nsId = row.namespaceId();
        SpectorMemory hotMemory = resolveHotMemory(nsId);
        LatestSnapshotRow latestSnap = queryLatestSnapshot(nsId);

        TierBreakdown tiers;
        long hebbianEdges = 0L;
        long temporalLinks = 0L;
        long entityNodes = 0L;
        long entityEdges = 0L;
        long decayedEngrams = 0L;

        if (hotMemory != null) {
            long total = hotMemory.totalMemories();
            long working = hotMemory.memoryCount(MemoryType.WORKING);
            long episodic = hotMemory.memoryCount(MemoryType.EPISODIC);
            long semantic = hotMemory.memoryCount(MemoryType.SEMANTIC);
            long procedural = hotMemory.memoryCount(MemoryType.PROCEDURAL);
            tiers = new TierBreakdown(total, working, episodic, semantic, procedural);

            try {
                if (hotMemory.admin() != null && hotMemory.admin().graph() != null) {
                    GraphStats gs = hotMemory.admin().graph().graphStats();
                    if (gs != null) {
                        hebbianEdges = gs.hebbianEdges();
                        temporalLinks = gs.temporalLinks();
                        entityNodes = gs.entityNodes();
                        entityEdges = gs.entityEdges();
                    }
                }
                ReflectReport lastReflect = hotMemory.lastReflectReport();
                if (lastReflect != null) {
                    decayedEngrams = lastReflect.tombstonedCount() + lastReflect.temporalPrunedCount();
                }
            } catch (Exception ignored) {}
        } else if (latestSnap != null) {
            tiers = new TierBreakdown(
                    latestSnap.totalCount(),
                    latestSnap.workingCount(),
                    latestSnap.episodicCount(),
                    latestSnap.semanticCount(),
                    latestSnap.proceduralCount()
            );
            hebbianEdges = latestSnap.hebbianEdges();
            temporalLinks = latestSnap.temporalLinks();
            entityNodes = latestSnap.entityNodes();
            entityEdges = latestSnap.entityEdges();
        } else {
            tiers = TierBreakdown.empty();
        }

        long storageBytes = computeNamespaceStorageBytes(row.tenantId(), nsId);
        if (storageBytes == 0L && latestSnap != null && latestSnap.storageBytes() > 0L) {
            storageBytes = latestSnap.storageBytes();
        }

        ThroughputAndLatency throughput = computeNamespaceThroughput(nsId, latestSnap);
        GrowthStats growth = computeNamespaceGrowth(nsId, tiers.total(), now);

        MeterRegistry mr = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
        long meterConsolidations = timerCount(mr, "spector.memory.consolidate", nsId);
        long meterDreams = timerCount(mr, "spector.memory.reflect", nsId);
        long snapConsolidations = latestSnap != null ? latestSnap.totalConsolidations() : 0L;
        long snapDreams = latestSnap != null ? latestSnap.totalDreams() : 0L;

        ConsolidationAndDreamHealth consolidation = new ConsolidationAndDreamHealth(
                Math.max(meterConsolidations, snapConsolidations),
                Math.max(meterDreams, snapDreams),
                decayedEngrams,
                hebbianEdges,
                temporalLinks,
                entityNodes,
                entityEdges
        );

        long maxMemoriesQuota = row.maxMemories() != null ? row.maxMemories() : -1L;
        long maxStorageQuota = row.maxStorageBytes() != null ? row.maxStorageBytes() : -1L;

        QuotaUtilizationRollup quotas = new QuotaUtilizationRollup(
                tiers.total(),
                maxMemoriesQuota,
                utilizationPct(tiers.total(), maxMemoriesQuota),
                storageBytes,
                maxStorageQuota,
                utilizationPct(storageBytes, maxStorageQuota),
                1L,
                1L,
                100.0,
                1L,
                1L,
                0L
        );

        return new NamespaceAnalyticsRollup(
                nsId,
                row.slug(),
                row.displayName(),
                row.tenantId(),
                row.ownerAccountId(),
                row.status().name(),
                row.legalHold(),
                storageBytes,
                tiers,
                growth,
                throughput,
                consolidation,
                quotas,
                now
        );
    }

    private ThroughputAndLatency computeNamespaceThroughput(String namespaceId, LatestSnapshotRow latestSnap) {
        MeterRegistry mr = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;

        long meterRecalls = timerCount(mr, "spector.memory.recall", namespaceId);
        long meterRemembers = timerCount(mr, "spector.memory.remember", namespaceId);
        long meterErrors = errorTimerCount(mr, namespaceId);

        long snapRecalls = latestSnap != null ? latestSnap.totalRecalls() : 0L;
        long snapRemembers = latestSnap != null ? latestSnap.totalRemembers() : 0L;
        long snapErrors = latestSnap != null ? latestSnap.totalErrors() : 0L;

        long recallTotal = Math.max(meterRecalls, snapRecalls);
        long rememberTotal = Math.max(meterRemembers, snapRemembers);
        long errorCount = Math.max(meterErrors, snapErrors);

        double[] rates = queryRecentOpsPerSec(namespaceId);
        double recallOpsPerSec = rates[0];
        double rememberOpsPerSec = rates[1];

        double meanMs = 0.0;
        double p50Ms = 0.0;
        double p95Ms = 0.0;
        double p99Ms = 0.0;

        if (mr != null) {
            var timers = mr.find("spector.memory.recall")
                    .tag("spector.namespace", namespaceId)
                    .timers();
            long countSum = 0L;
            double totalTimeMs = 0.0;
            double maxMs = 0.0;

            for (Timer t : timers) {
                countSum += t.count();
                totalTimeMs += t.totalTime(TimeUnit.MILLISECONDS);
                maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
                HistogramSnapshot snap = t.takeSnapshot();
                for (ValueAtPercentile vp : snap.percentileValues()) {
                    if (Math.abs(vp.percentile() - 0.5) < 0.01) {
                        p50Ms = Math.max(p50Ms, vp.value(TimeUnit.MILLISECONDS));
                    } else if (Math.abs(vp.percentile() - 0.95) < 0.01) {
                        p95Ms = Math.max(p95Ms, vp.value(TimeUnit.MILLISECONDS));
                    } else if (Math.abs(vp.percentile() - 0.99) < 0.01) {
                        p99Ms = Math.max(p99Ms, vp.value(TimeUnit.MILLISECONDS));
                    }
                }
            }
            if (countSum > 0) {
                meanMs = totalTimeMs / countSum;
                if (p50Ms == 0.0) p50Ms = meanMs;
                if (p99Ms == 0.0) p99Ms = Math.max(meanMs, maxMs);
                if (p95Ms == 0.0) p95Ms = meanMs + 0.75 * (p99Ms - meanMs);
            }
        }

        if (meanMs == 0.0 && latestSnap != null && latestSnap.avgLatencyMs() > 0.0) {
            double[] snapLatencies = querySnapshotLatencyPercentiles(namespaceId);
            meanMs = snapLatencies[0];
            p50Ms = snapLatencies[1];
            p95Ms = snapLatencies[2];
            p99Ms = snapLatencies[3];
        }

        long totalOps = recallTotal + rememberTotal;
        double errorRate = totalOps > 0 ? round6((double) errorCount / totalOps) : 0.0;

        return new ThroughputAndLatency(
                rememberTotal,
                recallTotal,
                round4(rememberOpsPerSec),
                round4(recallOpsPerSec),
                round4(p50Ms),
                round4(p95Ms),
                round4(p99Ms),
                round4(meanMs),
                errorCount,
                errorRate
        );
    }

    private ThroughputAndLatency aggregateThroughput(List<NamespaceAnalyticsRollup> rollups) {
        if (rollups == null || rollups.isEmpty()) {
            return ThroughputAndLatency.empty();
        }
        long rememberTotal = 0L;
        long recallTotal = 0L;
        double rememberOpsPerSec = 0.0;
        double recallOpsPerSec = 0.0;
        long errorCount = 0L;
        double weightedMeanNumerator = 0.0;
        long activeRecallNamespaces = 0L;

        List<Double> p50s = new ArrayList<>();
        List<Double> p95s = new ArrayList<>();
        List<Double> p99s = new ArrayList<>();

        for (NamespaceAnalyticsRollup nr : rollups) {
            ThroughputAndLatency tl = nr.throughput();
            if (tl == null) continue;
            rememberTotal += tl.rememberTotal();
            recallTotal += tl.recallTotal();
            rememberOpsPerSec += tl.rememberOpsPerSec();
            recallOpsPerSec += tl.recallOpsPerSec();
            errorCount += tl.errorCount();

            if (tl.latencyMeanMs() > 0.0) {
                long weight = Math.max(1L, tl.recallTotal());
                weightedMeanNumerator += tl.latencyMeanMs() * weight;
                activeRecallNamespaces += weight;
                p50s.add(tl.latencyP50Ms());
                p95s.add(tl.latencyP95Ms());
                p99s.add(tl.latencyP99Ms());
            }
        }

        double meanMs = activeRecallNamespaces > 0 ? (weightedMeanNumerator / activeRecallNamespaces) : 0.0;
        double p50Ms = percentileOfList(p50s, 0.50);
        double p95Ms = percentileOfList(p95s, 0.95);
        double p99Ms = percentileOfList(p99s, 0.99);

        long totalOps = rememberTotal + recallTotal;
        double errorRate = totalOps > 0 ? round6((double) errorCount / totalOps) : 0.0;

        return new ThroughputAndLatency(
                rememberTotal,
                recallTotal,
                round4(rememberOpsPerSec),
                round4(recallOpsPerSec),
                round4(p50Ms),
                round4(p95Ms),
                round4(p99Ms),
                round4(meanMs),
                errorCount,
                errorRate
        );
    }

    private GrowthStats computeNamespaceGrowth(String namespaceId, long currentTotal, Instant now) {
        try {
            long base24h = queryBaselineCountSince(namespaceId, now.minus(Duration.ofHours(24)), currentTotal);
            long base7d = queryBaselineCountSince(namespaceId, now.minus(Duration.ofDays(7)), currentTotal);
            long base30d = queryBaselineCountSince(namespaceId, now.minus(Duration.ofDays(30)), currentTotal);

            long d24h = currentTotal - base24h;
            long d7d = currentTotal - base7d;
            long d30d = currentTotal - base30d;
            double rate24h = base24h > 0 ? round4(((double) d24h / base24h) * 100.0) : 0.0;

            return new GrowthStats(currentTotal, d24h, d7d, d30d, rate24h);
        } catch (Exception e) {
            return new GrowthStats(currentTotal, 0L, 0L, 0L, 0.0);
        }
    }

    private GrowthStats aggregateGrowth(List<NamespaceAnalyticsRollup> rollups) {
        if (rollups == null || rollups.isEmpty()) {
            return GrowthStats.empty();
        }
        long current = 0L;
        long d24h = 0L;
        long d7d = 0L;
        long d30d = 0L;
        for (NamespaceAnalyticsRollup nr : rollups) {
            if (nr.growth() != null) {
                current += nr.growth().currentTotalMemories();
                d24h += nr.growth().delta24hMemories();
                d7d += nr.growth().delta7dMemories();
                d30d += nr.growth().delta30dMemories();
            }
        }
        long base24h = current - d24h;
        double rate24h = base24h > 0 ? round4(((double) d24h / base24h) * 100.0) : 0.0;
        return new GrowthStats(current, d24h, d7d, d30d, rate24h);
    }

    private TokenUsageRollup computeAccountTokenUsage(String userId) {
        TokenUsageTracker tracker = tokenUsageTrackerProvider != null ? tokenUsageTrackerProvider.getIfAvailable() : null;
        if (tracker == null || userId == null || userId.isBlank()) {
            return TokenUsageRollup.empty();
        }
        return toTokenRollup(tracker.getUserStats(userId));
    }

    private TokenUsageRollup computeTenantTokenUsage(String tenantId) {
        TokenUsageTracker tracker = tokenUsageTrackerProvider != null ? tokenUsageTrackerProvider.getIfAvailable() : null;
        if (tracker == null) {
            return TokenUsageRollup.empty();
        }
        List<UserRow> users = loadTenantUsers(tenantId);
        TokenUsageRollup acc = TokenUsageRollup.empty();
        for (UserRow u : users) {
            acc = acc.add(toTokenRollup(tracker.getUserStats(u.userId())));
            if (u.username() != null && !u.username().equals(u.userId())) {
                acc = acc.add(toTokenRollup(tracker.getUserStats(u.username())));
            }
        }
        return acc;
    }

    private TokenUsageRollup computeGlobalTokenUsage() {
        TokenUsageTracker tracker = tokenUsageTrackerProvider != null ? tokenUsageTrackerProvider.getIfAvailable() : null;
        if (tracker == null) {
            return TokenUsageRollup.empty();
        }
        return toTokenRollup(tracker.getGlobalStats());
    }

    private static TokenUsageRollup toTokenRollup(TokenUsageStats stats) {
        if (stats == null || stats.requestCount() == 0) {
            return TokenUsageRollup.empty();
        }
        double cost = (stats.inputTokens() * INPUT_TOKEN_RATE_USD)
                + (stats.outputTokens() * OUTPUT_TOKEN_RATE_USD)
                + (stats.embeddingTokens() * EMBEDDING_TOKEN_RATE_USD);
        return new TokenUsageRollup(
                stats.inputTokens(),
                stats.outputTokens(),
                stats.embeddingTokens(),
                stats.totalTokens(),
                stats.requestCount(),
                round6(cost)
        );
    }

    // ─────────────────────────── SQL & Meter Helpers ───────────────────────────

    private List<AnalyticsTimeseriesPoint> queryTimeseriesPoints(
            AnalyticsScope scope,
            String tenantId,
            String accountId,
            String namespaceId,
            Instant from,
            Instant to,
            int limit) {
        try {
            String baseSql = """
                    SELECT
                        s.snapshot_time,
                        SUM(s.total_count) AS total_count,
                        SUM(s.working_count) AS working_count,
                        SUM(s.episodic_count) AS episodic_count,
                        SUM(s.semantic_count) AS semantic_count,
                        SUM(s.procedural_count) AS procedural_count,
                        SUM(COALESCE(s.storage_bytes, 0)) AS storage_bytes,
                        SUM(s.recall_count) AS recall_count,
                        SUM(s.remember_count) AS remember_count,
                        AVG(s.avg_latency_ms) AS avg_latency_ms,
                        SUM(s.consolidations_run) AS consolidations_run,
                        SUM(COALESCE(s.dream_cycles_run, 0)) AS dream_cycles_run,
                        SUM(COALESCE(s.error_count, 0)) AS error_count,
                        SUM(s.hebbian_edges) AS hebbian_edges,
                        SUM(s.entity_nodes) AS entity_nodes
                    FROM memory_analytics_snapshot s
                    LEFT JOIN namespaces n ON s.namespace_id = n.namespace_id
                    LEFT JOIN users u ON COALESCE(s.account_id, n.owner_account_id) = u.user_id
                    WHERE s.snapshot_time >= :fromTime AND s.snapshot_time <= :toTime
                    """;

            StringBuilder sql = new StringBuilder(baseSql);
            if (scope == AnalyticsScope.NAMESPACE && namespaceId != null) {
                sql.append(" AND s.namespace_id = :namespaceId");
            } else if (scope == AnalyticsScope.ACCOUNT && accountId != null) {
                sql.append(" AND COALESCE(s.account_id, n.owner_account_id) = :accountId");
            } else if (scope == AnalyticsScope.TENANT && tenantId != null) {
                sql.append(" AND COALESCE(NULLIF(s.tenant_id, 'default'), u.tenant_id, s.tenant_id, 'default') = :tenantId");
            }

            sql.append(" GROUP BY s.snapshot_time ORDER BY s.snapshot_time ASC LIMIT :limit");

            var spec = jdbc.sql(sql.toString())
                    .param("fromTime", Timestamp.from(from))
                    .param("toTime", Timestamp.from(to))
                    .param("limit", limit);

            if (scope == AnalyticsScope.NAMESPACE && namespaceId != null) {
                spec = spec.param("namespaceId", namespaceId);
            } else if (scope == AnalyticsScope.ACCOUNT && accountId != null) {
                spec = spec.param("accountId", accountId);
            } else if (scope == AnalyticsScope.TENANT && tenantId != null) {
                spec = spec.param("tenantId", tenantId);
            }

            return spec.query((rs, rowNum) -> new AnalyticsTimeseriesPoint(
                    rs.getTimestamp("snapshot_time").toInstant(),
                    rs.getLong("total_count"),
                    rs.getLong("working_count"),
                    rs.getLong("episodic_count"),
                    rs.getLong("semantic_count"),
                    rs.getLong("procedural_count"),
                    rs.getLong("storage_bytes"),
                    rs.getLong("recall_count"),
                    rs.getLong("remember_count"),
                    round4(rs.getDouble("avg_latency_ms")),
                    rs.getLong("consolidations_run"),
                    rs.getLong("dream_cycles_run"),
                    rs.getLong("error_count"),
                    rs.getLong("hebbian_edges"),
                    rs.getLong("entity_nodes")
            )).list();
        } catch (Exception e) {
            log.warn("[AdminAnalyticsService] Failed to query timeseries points for scope={}: {}", scope, e.getMessage());
            return List.of();
        }
    }

    private LatestSnapshotRow queryLatestSnapshot(String namespaceId) {
        try {
            var latest = jdbc.sql("""
                    SELECT
                        total_count, working_count, episodic_count, semantic_count, procedural_count,
                        COALESCE(storage_bytes, 0) AS storage_bytes,
                        hebbian_edges, temporal_links, entity_nodes, entity_edges, avg_latency_ms
                    FROM memory_analytics_snapshot
                    WHERE namespace_id = :ns
                    ORDER BY snapshot_time DESC
                    LIMIT 1
                    """)
                    .param("ns", namespaceId)
                    .query((rs, rowNum) -> new long[]{
                            rs.getLong("total_count"),
                            rs.getLong("working_count"),
                            rs.getLong("episodic_count"),
                            rs.getLong("semantic_count"),
                            rs.getLong("procedural_count"),
                            rs.getLong("storage_bytes"),
                            rs.getLong("hebbian_edges"),
                            rs.getLong("temporal_links"),
                            rs.getLong("entity_nodes"),
                            rs.getLong("entity_edges"),
                            Double.doubleToLongBits(rs.getDouble("avg_latency_ms"))
                    })
                    .optional();

            if (latest.isEmpty()) {
                return null;
            }

            var totals = jdbc.sql("""
                    SELECT
                        COALESCE(SUM(recall_count), 0) AS recalls,
                        COALESCE(SUM(remember_count), 0) AS remembers,
                        COALESCE(SUM(consolidations_run), 0) AS consolidations,
                        COALESCE(SUM(COALESCE(dream_cycles_run, 0)), 0) AS dreams,
                        COALESCE(SUM(COALESCE(error_count, 0)), 0) AS errors
                    FROM memory_analytics_snapshot
                    WHERE namespace_id = :ns
                    """)
                    .param("ns", namespaceId)
                    .query((rs, rowNum) -> new long[]{
                            rs.getLong("recalls"),
                            rs.getLong("remembers"),
                            rs.getLong("consolidations"),
                            rs.getLong("dreams"),
                            rs.getLong("errors")
                    })
                    .optional()
                    .orElse(new long[]{0, 0, 0, 0, 0});

            long[] l = latest.get();
            return new LatestSnapshotRow(
                    l[0], l[1], l[2], l[3], l[4], l[5],
                    l[6], l[7], l[8], l[9],
                    Double.longBitsToDouble(l[10]),
                    totals[0], totals[1], totals[2], totals[3], totals[4]
            );
        } catch (Exception e) {
            return null;
        }
    }

    private double[] queryRecentOpsPerSec(String namespaceId) {
        try {
            Timestamp since = Timestamp.from(Instant.now().minusSeconds(60));
            return jdbc.sql("""
                    SELECT
                        COALESCE(SUM(recall_count), 0) AS recalls,
                        COALESCE(SUM(remember_count), 0) AS remembers
                    FROM memory_analytics_snapshot
                    WHERE namespace_id = :ns AND snapshot_time >= :since
                    """)
                    .param("ns", namespaceId)
                    .param("since", since)
                    .query((rs, rowNum) -> new double[]{
                            rs.getLong("recalls") / 60.0,
                            rs.getLong("remembers") / 60.0
                    })
                    .optional()
                    .orElse(new double[]{0.0, 0.0});
        } catch (Exception e) {
            return new double[]{0.0, 0.0};
        }
    }

    private double[] querySnapshotLatencyPercentiles(String namespaceId) {
        try {
            List<Double> samples = jdbc.sql("""
                    SELECT avg_latency_ms
                    FROM memory_analytics_snapshot
                    WHERE namespace_id = :ns AND avg_latency_ms > 0
                    ORDER BY snapshot_time DESC
                    LIMIT 100
                    """)
                    .param("ns", namespaceId)
                    .query(Double.class)
                    .list();
            if (samples.isEmpty()) {
                return new double[]{0.0, 0.0, 0.0, 0.0};
            }
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double p50 = percentileOfList(samples, 0.50);
            double p95 = percentileOfList(samples, 0.95);
            double p99 = percentileOfList(samples, 0.99);
            return new double[]{mean, p50, p95, p99};
        } catch (Exception e) {
            return new double[]{0.0, 0.0, 0.0, 0.0};
        }
    }

    private long queryBaselineCountSince(String namespaceId, Instant since, long fallback) {
        return jdbc.sql("""
                SELECT total_count
                FROM memory_analytics_snapshot
                WHERE namespace_id = :ns AND snapshot_time >= :since
                ORDER BY snapshot_time ASC
                LIMIT 1
                """)
                .param("ns", namespaceId)
                .param("since", Timestamp.from(since))
                .query(Long.class)
                .optional()
                .orElse(fallback);
    }

    private List<NamespaceMetaRow> loadAllNamespaces(String tenantIdFilter) {
        List<NamespaceMetaRow> all = jdbc.sql(sqlLoader.load("namespaces/admin/list-namespaces"))
                .query(this::mapNamespaceMeta)
                .list();
        return all.stream()
                .filter(r -> r.status() != NamespaceStatus.TOMBSTONED)
                .filter(r -> tenantIdFilter == null || tenantIdFilter.equals(r.tenantId()))
                .toList();
    }

    private List<NamespaceMetaRow> loadNamespacesByOwner(String ownerAccountId) {
        return loadAllNamespaces(null).stream()
                .filter(r -> ownerAccountId.equals(r.ownerAccountId()))
                .toList();
    }

    private Optional<NamespaceMetaRow> findNamespaceMeta(String namespaceId) {
        return jdbc.sql(sqlLoader.load("namespaces/admin/find-namespace-by-id"))
                .param("namespaceId", namespaceId)
                .query(this::mapNamespaceMeta)
                .optional();
    }

    private List<UserRow> loadTenantUsers(String tenantId) {
        return userAccountStore.listUsers().stream()
                .filter(u -> {
                    String ut = (u.tenantId() != null && !u.tenantId().isBlank()) ? u.tenantId() : "default";
                    return tenantId.equals(ut);
                })
                .toList();
    }

    private String resolvePrincipalType(String accountId) {
        try {
            Account acc = accountCatalog.getAccount(accountId);
            if (acc != null && acc.kind() != null) {
                return acc.kind().name();
            }
        } catch (Exception ignored) {}
        return "HUMAN";
    }

    private SpectorMemory resolveHotMemory(String namespaceId) {
        if (memoryRegistryProvider == null) return null;
        try {
            MemoryRegistry reg = memoryRegistryProvider.getIfAvailable();
            if (reg != null && reg.namespaceResolver() != null) {
                return reg.namespaceResolver().cachedEntries().get(namespaceId);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private long computeNamespaceStorageBytes(String tenantId, String namespaceId) {
        if (synapseProps == null || namespaceId == null || namespaceId.isBlank()) {
            return 0L;
        }
        Path root;
        try {
            root = synapseProps.remembererRoot();
        } catch (Exception e) {
            return 0L;
        }
        if (root == null || !Files.isDirectory(root)) {
            return 0L;
        }
        long total = 0L;
        String effTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        Path tenantedDir = root.resolve("tenants").resolve(effTenant).resolve("namespaces").resolve(namespaceId);
        if (Files.isDirectory(tenantedDir)) {
            total += directorySize(tenantedDir);
        }
        Path flatDir = root.resolve("namespaces").resolve(namespaceId);
        if (Files.isDirectory(flatDir)) {
            total += directorySize(flatDir);
        }
        return total;
    }

    private static long directorySize(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private long readQuotaBreaches(String tenantId) {
        if (meterRegistryProvider == null) return 0L;
        try {
            MeterRegistry mr = meterRegistryProvider.getIfAvailable();
            if (mr == null) return 0L;
            return (long) mr.find("spector.quota.breach.total")
                    .tag("tenant_id", tenantId != null ? tenantId : "default")
                    .counters()
                    .stream()
                    .mapToDouble(Counter::count)
                    .sum();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long timerCount(MeterRegistry registry, String metricName, String namespaceId) {
        if (registry == null) return 0L;
        return registry.find(metricName)
                .tag("spector.namespace", namespaceId)
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private static long errorTimerCount(MeterRegistry registry, String namespaceId) {
        if (registry == null) return 0L;
        long recallErr = registry.find("spector.memory.recall")
                .tag("spector.namespace", namespaceId)
                .tag("spector.status", "ERROR")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
        long rememberErr = registry.find("spector.memory.remember")
                .tag("spector.namespace", namespaceId)
                .tag("spector.status", "ERROR")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
        return recallErr + rememberErr;
    }

    private String resolveCallerTenantId(String callerId) {
        String securityTenant = SecurityUtils.getTenantId();
        if (securityTenant != null && !securityTenant.isBlank() && !"default".equals(securityTenant)) {
            return securityTenant.trim();
        }
        if (callerId != null && !callerId.isBlank() && !"default".equals(callerId)) {
            Optional<UserRow> userRow = userAccountStore.findByUserId(callerId)
                    .or(() -> userAccountStore.findByUsername(callerId));
            if (userRow.isPresent() && userRow.get().tenantId() != null && !userRow.get().tenantId().isBlank()) {
                return userRow.get().tenantId().trim();
            }
        }
        return "default";
    }

    private static double utilizationPct(long used, long quota) {
        if (quota <= 0) return 0.0;
        return round4(((double) used / quota) * 100.0);
    }

    private static double percentileOfList(List<Double> values, double quantile) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(quantile * sorted.size()) - 1;
        idx = Math.clamp(idx, 0, sorted.size() - 1);
        return sorted.get(idx);
    }

    private static double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static double round6(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    private NamespaceMetaRow mapNamespaceMeta(ResultSet rs, int rowNum) throws SQLException {
        String statusStr = rs.getString("status");
        NamespaceStatus status = NamespaceStatus.ACTIVE;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = NamespaceStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        return new NamespaceMetaRow(
                rs.getString("namespace_id"),
                rs.getString("slug"),
                rs.getString("display_name"),
                status,
                rs.getBoolean("legal_hold"),
                rs.getString("tenant_id"),
                rs.getString("owner_account_id"),
                (Integer) rs.getObject("max_memories"),
                (Long) rs.getObject("max_storage_bytes")
        );
    }

    private record NamespaceMetaRow(
            String namespaceId,
            String slug,
            String displayName,
            NamespaceStatus status,
            boolean legalHold,
            String tenantId,
            String ownerAccountId,
            Integer maxMemories,
            Long maxStorageBytes
    ) {}

    private record LatestSnapshotRow(
            long totalCount,
            long workingCount,
            long episodicCount,
            long semanticCount,
            long proceduralCount,
            long storageBytes,
            long hebbianEdges,
            long temporalLinks,
            long entityNodes,
            long entityEdges,
            double avgLatencyMs,
            long totalRecalls,
            long totalRemembers,
            long totalConsolidations,
            long totalDreams,
            long totalErrors
    ) {}
}
