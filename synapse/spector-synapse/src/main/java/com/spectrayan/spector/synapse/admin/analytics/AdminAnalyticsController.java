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

import java.time.Instant;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AccountAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AnalyticsScope;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.AnalyticsTimeseriesResponse;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.FleetAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.NamespaceAnalyticsRollup;
import com.spectrayan.spector.synapse.admin.analytics.dto.AdminAnalyticsDto.TenantAnalyticsRollup;

/**
 * Administrative REST controller exposing aggregated, strictly content-free analytics rollups
 * at {@code /api/v1/admin/analytics} across fleet, tenant, account, and namespace levels
 * (ADR-0083 §5.8, ADR-0087, Issue #1058).
 *
 * <p><strong>Authorization Invariants</strong>:
 * <ul>
 *   <li>{@code super-admin} (Platform Operator): may query fleet-wide rollups and any tenant, account, or namespace.</li>
 *   <li>{@code admin} (Tenant Admin): may query only their own tenant and accounts/namespaces within their tenant;
 *       cross-tenant or fleet queries fail with HTTP 403 Forbidden ({@code SPE-820-001}).</li>
 * </ul>
 * </p>
 *
 * <p><strong>Content-Free Contract</strong>: Zero memory content fields are returned on any endpoint.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin', 'ADMIN', 'SUPER_ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
    }

    /**
     * Returns fleet-wide analytics rollup across all tenants.
     * Restricted to {@code super-admin}; tenant {@code admin} receives HTTP 403 ({@code SPE-820-001}).
     */
    @GetMapping("/fleet")
    public ResponseEntity<FleetAnalyticsRollup> getFleetAnalytics() {
        return ResponseEntity.ok(analyticsService.getFleetAnalytics());
    }

    /**
     * Returns tenant-level analytics rollup for the caller's tenant (or {@code ?tenantId=} for {@code super-admin}).
     */
    @GetMapping("/tenant")
    public ResponseEntity<TenantAnalyticsRollup> getCallerTenantAnalytics(
            @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(analyticsService.getTenantAnalytics(tenantId));
    }

    /**
     * Returns tenant-level analytics rollup for a specific {@code tenantId}.
     * Tenant {@code admin} calling for another tenant receives HTTP 403 ({@code SPE-820-001}).
     */
    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<TenantAnalyticsRollup> getTenantAnalytics(
            @PathVariable String tenantId) {
        return ResponseEntity.ok(analyticsService.getTenantAnalytics(tenantId));
    }

    /**
     * Returns account-level analytics rollup across all namespaces owned by {@code accountId}.
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountAnalyticsRollup> getAccountAnalytics(
            @PathVariable String accountId) {
        return ResponseEntity.ok(analyticsService.getAccountAnalytics(accountId));
    }

    /**
     * Returns namespace-level analytics rollup and quota utilization for {@code namespaceId}.
     */
    @GetMapping("/namespaces/{namespaceId}")
    public ResponseEntity<NamespaceAnalyticsRollup> getNamespaceAnalytics(
            @PathVariable String namespaceId) {
        return ResponseEntity.ok(analyticsService.getNamespaceAnalytics(namespaceId));
    }

    /**
     * Returns historical time-series rollups from {@code memory_analytics_snapshot}
     * aggregated at {@code FLEET}, {@code TENANT}, {@code ACCOUNT}, or {@code NAMESPACE} scope.
     */
    @GetMapping("/timeseries")
    public ResponseEntity<AnalyticsTimeseriesResponse> getTimeseries(
            @RequestParam(required = false) AnalyticsScope scope,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String namespaceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(analyticsService.getTimeseries(
                scope, tenantId, accountId, namespaceId, from, to, limit));
    }
}
