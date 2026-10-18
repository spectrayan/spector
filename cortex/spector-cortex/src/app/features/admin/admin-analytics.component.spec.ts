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

import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import { AdminAnalyticsComponent } from './pages/admin-analytics.component';

describe('AdminAnalyticsComponent (#1065)', () => {
  let isSuperAdminSignal = signal(true);
  let tenantIdSignal = signal('acme');
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let apiMock: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    isSuperAdminSignal = signal(true);
    tenantIdSignal = signal('acme');
    snackBarMock = { open: vi.fn() };

    apiMock = {
      getFleetAnalytics: vi.fn().mockReturnValue(
        of({
          tenantCount: 2,
          activeTenantCount: 2,
          accountCount: 6,
          namespaceCount: 12,
          storageBytes: 10485760,
          tiers: {
            total: 4800,
            working: 400,
            episodic: 1600,
            semantic: 2000,
            procedural: 800,
          },
          growth: {
            currentTotalMemories: 4800,
            delta24hMemories: 120,
            delta7dMemories: 640,
            delta30dMemories: 2100,
            growthRate24hPct: 2.56,
          },
          throughput: {
            rememberTotal: 5000,
            recallTotal: 18000,
            rememberOpsPerSec: 4.2,
            recallOpsPerSec: 15.8,
            latencyP50Ms: 2.1,
            latencyMeanMs: 3.4,
            latencyP95Ms: 8.5,
            latencyP99Ms: 14.2,
            errorCount: 3,
            errorRate: 0.001,
          },
          consolidation: {
            consolidationsRun: 42,
            dreamCyclesRun: 14,
            decayedEngrams: 310,
            hebbianEdges: 9200,
            temporalLinks: 4100,
            entityNodes: 650,
            entityEdges: 1850,
          },
          tokenUsage: {
            inputTokens: 120000,
            outputTokens: 45000,
            embeddingTokens: 310000,
            totalTokens: 475000,
            requestCount: 890,
            estimatedCostUsd: 1.425,
          },
          quotas: {
            memoryCountUsed: 4800,
            memoryCountQuota: 100000,
            memoryUtilizationPct: 4.8,
            storageBytesUsed: 10485760,
            storageBytesQuota: 1073741824,
            storageUtilizationPct: 0.98,
            namespaceCountUsed: 12,
            namespaceCountQuota: 100,
            namespaceUtilizationPct: 12.0,
            accountCountUsed: 6,
            accountCountQuota: 50,
            quotaBreachCount: 0,
          },
          tenants: [
            {
              tenantId: 'acme',
              displayName: 'Acme Corp',
              plan: 'ENTERPRISE',
              status: 'ACTIVE',
              accountCount: 4,
              namespaceCount: 8,
              totalMemories: 3200,
              storageBytes: 8388608,
              recallOpsPerSec: 11.2,
              rememberOpsPerSec: 3.1,
              latencyP95Ms: 7.8,
              totalTokens: 320000,
              estimatedCostUsd: 0.96,
              quotaBreachCount: 0,
            },
          ],
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
      getCallerTenantAnalytics: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          displayName: 'Acme Corp',
          plan: 'ENTERPRISE',
          status: 'ACTIVE',
          accountCount: 4,
          humanCount: 2,
          agentCount: 2,
          namespaceCount: 8,
          storageBytes: 8388608,
          tiers: {
            total: 3200,
            working: 200,
            episodic: 1100,
            semantic: 1400,
            procedural: 500,
          },
          growth: {
            currentTotalMemories: 3200,
            delta24hMemories: 80,
            delta7dMemories: 400,
            delta30dMemories: 1500,
            growthRate24hPct: 2.5,
          },
          throughput: {
            rememberTotal: 3400,
            recallTotal: 12000,
            rememberOpsPerSec: 3.1,
            recallOpsPerSec: 11.2,
            latencyP50Ms: 1.9,
            latencyMeanMs: 3.0,
            latencyP95Ms: 7.8,
            latencyP99Ms: 12.4,
            errorCount: 1,
            errorRate: 0.0005,
          },
          consolidation: {
            consolidationsRun: 28,
            dreamCyclesRun: 9,
            decayedEngrams: 190,
            hebbianEdges: 6400,
            temporalLinks: 2900,
            entityNodes: 420,
            entityEdges: 1100,
          },
          tokenUsage: {
            inputTokens: 80000,
            outputTokens: 30000,
            embeddingTokens: 210000,
            totalTokens: 320000,
            requestCount: 600,
            estimatedCostUsd: 0.96,
          },
          quotas: {
            memoryCountUsed: 3200,
            memoryCountQuota: 50000,
            memoryUtilizationPct: 6.4,
            storageBytesUsed: 8388608,
            storageBytesQuota: 536870912,
            storageUtilizationPct: 1.56,
            namespaceCountUsed: 8,
            namespaceCountQuota: 50,
            namespaceUtilizationPct: 16.0,
            accountCountUsed: 4,
            accountCountQuota: 25,
            quotaBreachCount: 0,
          },
          accounts: [
            {
              accountId: 'acc-1',
              username: 'alice',
              displayName: 'Alice Operator',
              principalType: 'HUMAN',
              tenantId: 'acme',
              namespaceCount: 3,
              storageBytes: 4194304,
              tiers: { total: 1500, working: 100, episodic: 500, semantic: 700, procedural: 200 },
              tokenUsage: { totalTokens: 150000, estimatedCostUsd: 0.45 },
            },
          ],
          namespaces: [
            {
              namespaceId: 'ns-1',
              slug: 'research-core',
              displayName: 'Research Core',
              tenantId: 'acme',
              ownerAccountId: 'acc-1',
              status: 'ACTIVE',
              legalHold: false,
              storageBytes: 2097152,
              tiers: { total: 900, working: 50, episodic: 300, semantic: 450, procedural: 100 },
              throughput: { latencyP95Ms: 6.4 },
            },
          ],
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
      getTenantAnalytics: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          displayName: 'Acme Corp',
          plan: 'ENTERPRISE',
          status: 'ACTIVE',
          accountCount: 4,
          humanCount: 2,
          agentCount: 2,
          namespaceCount: 8,
          storageBytes: 8388608,
          tiers: {
            total: 3200,
            working: 200,
            episodic: 1100,
            semantic: 1400,
            procedural: 500,
          },
          growth: {
            currentTotalMemories: 3200,
            delta24hMemories: 80,
            delta7dMemories: 400,
            delta30dMemories: 1500,
            growthRate24hPct: 2.5,
          },
          throughput: {
            rememberTotal: 3400,
            recallTotal: 12000,
            rememberOpsPerSec: 3.1,
            recallOpsPerSec: 11.2,
            latencyP50Ms: 1.9,
            latencyMeanMs: 3.0,
            latencyP95Ms: 7.8,
            latencyP99Ms: 12.4,
            errorCount: 1,
            errorRate: 0.0005,
          },
          consolidation: {
            consolidationsRun: 28,
            dreamCyclesRun: 9,
            decayedEngrams: 190,
            hebbianEdges: 6400,
            temporalLinks: 2900,
            entityNodes: 420,
            entityEdges: 1100,
          },
          tokenUsage: {
            inputTokens: 80000,
            outputTokens: 30000,
            embeddingTokens: 210000,
            totalTokens: 320000,
            requestCount: 600,
            estimatedCostUsd: 0.96,
          },
          quotas: {
            memoryCountUsed: 3200,
            memoryCountQuota: 50000,
            memoryUtilizationPct: 6.4,
            storageBytesUsed: 8388608,
            storageBytesQuota: 536870912,
            storageUtilizationPct: 1.56,
            namespaceCountUsed: 8,
            namespaceCountQuota: 50,
            namespaceUtilizationPct: 16.0,
            accountCountUsed: 4,
            accountCountQuota: 25,
            quotaBreachCount: 0,
          },
          accounts: [
            {
              accountId: 'acc-1',
              username: 'alice',
              displayName: 'Alice Operator',
              principalType: 'HUMAN',
              tenantId: 'acme',
              namespaceCount: 3,
              storageBytes: 4194304,
              tiers: { total: 1500, working: 100, episodic: 500, semantic: 700, procedural: 200 },
              tokenUsage: { totalTokens: 150000, estimatedCostUsd: 0.45 },
            },
          ],
          namespaces: [
            {
              namespaceId: 'ns-1',
              slug: 'research-core',
              displayName: 'Research Core',
              tenantId: 'acme',
              ownerAccountId: 'acc-1',
              status: 'ACTIVE',
              legalHold: false,
              storageBytes: 2097152,
              tiers: { total: 900, working: 50, episodic: 300, semantic: 450, procedural: 100 },
              throughput: { latencyP95Ms: 6.4 },
            },
          ],
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
      getAccountAnalytics: vi.fn().mockReturnValue(
        of({
          accountId: 'acc-1',
          username: 'alice',
          displayName: 'Alice Operator',
          principalType: 'HUMAN',
          tenantId: 'acme',
          namespaceCount: 1,
          storageBytes: 2097152,
          tiers: { total: 900, working: 50, episodic: 300, semantic: 450, procedural: 100 },
          growth: {
            currentTotalMemories: 900,
            delta24hMemories: 25,
            delta7dMemories: 120,
            delta30dMemories: 450,
            growthRate24hPct: 2.8,
          },
          throughput: {
            rememberTotal: 900,
            recallTotal: 3500,
            rememberOpsPerSec: 1.1,
            recallOpsPerSec: 4.5,
            latencyP50Ms: 1.8,
            latencyMeanMs: 2.7,
            latencyP95Ms: 6.4,
            latencyP99Ms: 9.9,
            errorCount: 0,
            errorRate: 0.0,
          },
          consolidation: {
            consolidationsRun: 10,
            dreamCyclesRun: 4,
            decayedEngrams: 40,
            hebbianEdges: 1800,
            temporalLinks: 800,
            entityNodes: 120,
            entityEdges: 340,
          },
          tokenUsage: {
            inputTokens: 40000,
            outputTokens: 15000,
            embeddingTokens: 95000,
            totalTokens: 150000,
            requestCount: 220,
            estimatedCostUsd: 0.45,
          },
          quotas: {
            memoryCountUsed: 900,
            memoryCountQuota: 10000,
            memoryUtilizationPct: 9.0,
            storageBytesUsed: 2097152,
            storageBytesQuota: 104857600,
            storageUtilizationPct: 2.0,
            namespaceCountUsed: 1,
            namespaceCountQuota: 10,
            namespaceUtilizationPct: 10.0,
            accountCountUsed: 1,
            accountCountQuota: 1,
            quotaBreachCount: 0,
          },
          namespaces: [
            {
              namespaceId: 'ns-1',
              slug: 'research-core',
              displayName: 'Research Core',
              tenantId: 'acme',
              ownerAccountId: 'acc-1',
              status: 'ACTIVE',
              legalHold: false,
              storageBytes: 2097152,
              tiers: { total: 900, working: 50, episodic: 300, semantic: 450, procedural: 100 },
              throughput: { latencyP95Ms: 6.4 },
            },
          ],
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
      getNamespaceAnalytics: vi.fn().mockReturnValue(
        of({
          namespaceId: 'ns-1',
          slug: 'research-core',
          displayName: 'Research Core',
          tenantId: 'acme',
          ownerAccountId: 'acc-1',
          status: 'ACTIVE',
          legalHold: false,
          storageBytes: 2097152,
          tiers: { total: 900, working: 50, episodic: 300, semantic: 450, procedural: 100 },
          growth: {
            currentTotalMemories: 900,
            delta24hMemories: 25,
            delta7dMemories: 120,
            delta30dMemories: 450,
            growthRate24hPct: 2.8,
          },
          throughput: {
            rememberTotal: 900,
            recallTotal: 3500,
            rememberOpsPerSec: 1.1,
            recallOpsPerSec: 4.5,
            latencyP50Ms: 1.8,
            latencyMeanMs: 2.7,
            latencyP95Ms: 6.4,
            latencyP99Ms: 9.9,
            errorCount: 0,
            errorRate: 0.0,
          },
          consolidation: {
            consolidationsRun: 10,
            dreamCyclesRun: 4,
            decayedEngrams: 40,
            hebbianEdges: 1800,
            temporalLinks: 800,
            entityNodes: 120,
            entityEdges: 340,
          },
          quotas: {
            memoryCountUsed: 900,
            memoryCountQuota: 10000,
            memoryUtilizationPct: 9.0,
            storageBytesUsed: 2097152,
            storageBytesQuota: 104857600,
            storageUtilizationPct: 2.0,
            namespaceCountUsed: 1,
            namespaceCountQuota: 1,
            namespaceUtilizationPct: 100.0,
            accountCountUsed: 1,
            accountCountQuota: 1,
            quotaBreachCount: 0,
          },
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
      getAnalyticsTimeseries: vi.fn().mockReturnValue(
        of({
          scope: 'FLEET',
          points: [
            {
              timestamp: '2026-10-18T11:00:00Z',
              totalCount: 4750,
              workingCount: 390,
              episodicCount: 1580,
              semanticCount: 1980,
              proceduralCount: 800,
              storageBytes: 10240000,
              recallCount: 850,
              rememberCount: 120,
              avgLatencyMs: 3.2,
              consolidationsRun: 2,
              dreamCyclesRun: 1,
              errorCount: 0,
              hebbianEdges: 9100,
              entityNodes: 645,
            },
          ],
          totalPoints: 1,
          generatedAt: '2026-10-18T12:00:00Z',
        }),
      ),
    };

    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, AdminAnalyticsComponent],
      providers: [
        { provide: SynapseApiService, useValue: apiMock },
        {
          provide: AuthService,
          useValue: {
            isSuperAdmin: isSuperAdminSignal,
            currentUser: signal({
              userId: 'usr-1',
              username: 'admin',
              tenantId: tenantIdSignal(),
              roles: ['admin'],
              scopes: [],
            }),
          },
        },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
  });

  it('loads fleet analytics and timeseries for super-admin and renders all 6 telemetry panels', () => {
    const fixture = TestBed.createComponent(AdminAnalyticsComponent);
    fixture.detectChanges();

    expect(apiMock['getFleetAnalytics']).toHaveBeenCalled();
    expect(apiMock['getAnalyticsTimeseries']).toHaveBeenCalledWith(
      expect.objectContaining({ scope: 'FLEET', limit: 100 }),
    );

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('#chart-memory-tiers')?.textContent).toContain('4,800 total engrams');
    expect(el.querySelector('#chart-quota-utilization')?.textContent).toContain('Storage Bytes');
    expect(el.querySelector('#chart-throughput-errors')?.textContent).toContain('15.80 ops/s');
    expect(el.querySelector('#chart-latency-percentiles')?.textContent).toContain('8.50 ms');
    expect(el.querySelector('#chart-consolidation-dream')?.textContent).toContain('9,200');
    expect(el.querySelector('#chart-token-cost')?.textContent).toContain('$1.425');
    expect(el.querySelector('#chart-timeseries')?.textContent).toContain('4,750');
    expect(el.querySelector('#drill-tenants-table')?.textContent).toContain('Acme Corp');
  });

  it('supports full hierarchical drill-down: FLEET -> TENANT -> ACCOUNT -> NAMESPACE', () => {
    const fixture = TestBed.createComponent(AdminAnalyticsComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    // 1. Drill to TENANT
    comp.drillToTenant('acme');
    fixture.detectChanges();
    expect(comp.scope()).toBe('TENANT');
    expect(apiMock['getTenantAnalytics']).toHaveBeenCalledWith('acme');
    expect(fixture.nativeElement.querySelector('#drill-accounts-table')?.textContent).toContain(
      'Alice Operator',
    );

    // 2. Drill to ACCOUNT
    comp.drillToAccount('acc-1');
    fixture.detectChanges();
    expect(comp.scope()).toBe('ACCOUNT');
    expect(apiMock['getAccountAnalytics']).toHaveBeenCalledWith('acc-1');
    expect(fixture.nativeElement.querySelector('#drill-namespaces-table')?.textContent).toContain(
      'research-core',
    );

    // 3. Drill to NAMESPACE
    comp.drillToNamespace('ns-1');
    fixture.detectChanges();
    expect(comp.scope()).toBe('NAMESPACE');
    expect(apiMock['getNamespaceAnalytics']).toHaveBeenCalledWith('ns-1');
    expect(fixture.nativeElement.querySelector('#analytics-breadcrumb')?.textContent).toContain(
      'Namespace: research-core',
    );
  });

  it('defaults to TENANT scope when operator is a tenant admin (non-super-admin)', () => {
    isSuperAdminSignal.set(false);
    const fixture = TestBed.createComponent(AdminAnalyticsComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    expect(comp.scope()).toBe('TENANT');
    expect(apiMock['getCallerTenantAnalytics']).toHaveBeenCalledWith('acme');
    expect(apiMock['getFleetAnalytics']).not.toHaveBeenCalled();
  });

  it('updates timeseries query when time range preset changes and exports content-free aggregate CSV', () => {
    const fixture = TestBed.createComponent(AdminAnalyticsComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.setTimeRange('30d');
    expect(comp.timeRange()).toBe('30d');
    expect(apiMock['getAnalyticsTimeseries']).toHaveBeenCalledTimes(2);

    comp.exportAggregatesCsv();
    const csv = comp.lastExportedCsv();
    expect(csv).toContain('section,scope,target_id,metric,value');
    expect(csv).toContain('summary,FLEET,fleet,total_memories,4800');
    expect(csv).toContain('summary,FLEET,fleet,estimated_cost_usd,1.425');
    expect(csv).toContain('timeseries,FLEET,fleet,2026-10-18T11:00:00Z');
  });
});
