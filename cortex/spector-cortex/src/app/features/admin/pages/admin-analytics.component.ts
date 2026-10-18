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

import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, of } from 'rxjs';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import {
  AdminSectionShellComponent,
  formatAdminBytes,
  formatAdminTimestamp,
} from '../admin-console.component';

export type AnalyticsDrillScope = 'FLEET' | 'TENANT' | 'ACCOUNT' | 'NAMESPACE';
export type AnalyticsTimeRangePreset = '24h' | '7d' | '30d' | '90d';

/**
 * Content-free Admin Analytics dashboards (`/admin/analytics`, Issue #1065).
 *
 * <p>Provides hierarchical drill-down (`FLEET` → `TENANT` → `ACCOUNT` → `NAMESPACE`),
 * time-range filtering, aggregate CSV export, and visual telemetry panels for
 * memory tier growth, storage & quota utilization, remember/recall throughput,
 * recall latency percentiles, consolidation & sleep/dream health, error rates,
 * and LLM/embedding token & cost usage (superseding #226).</p>
 *
 * <p><strong>Content-Free Contract</strong>: Every request is served exclusively by
 * `/api/v1/admin/analytics/*`. Zero memory content fields are requested or rendered.</p>
 */
@Component({
  selector: 'cortex-admin-analytics',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    AdminSectionShellComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="insights"
      title="Analytics & Cost Telemetry"
      subtitle="Hierarchical fleet, tenant, account, and namespace observability (strictly content-free)."
    >
      <!-- Drill-Down Breadcrumb & Time-Range / Export Toolbar -->
      <div class="analytics-toolbar" role="toolbar" aria-label="Analytics drill-down and time range controls">
        <nav class="drill-breadcrumb" id="analytics-breadcrumb" aria-label="Analytics scope hierarchy">
          @if (auth.isSuperAdmin()) {
            <button
              type="button"
              class="crumb-btn"
              [class.active]="scope() === 'FLEET'"
              (click)="drillToFleet()"
              id="crumb-fleet-btn"
            >
              <mat-icon>public</mat-icon>
              Fleet
            </button>
          }

          @if (selectedTenantId() || !auth.isSuperAdmin()) {
            @if (auth.isSuperAdmin()) {
              <mat-icon class="crumb-sep">chevron_right</mat-icon>
            }
            <button
              type="button"
              class="crumb-btn"
              [class.active]="scope() === 'TENANT'"
              (click)="drillToTenant(selectedTenantId() || auth.currentUser()?.tenantId)"
              id="crumb-tenant-btn"
            >
              <mat-icon>domain</mat-icon>
              Tenant: {{ selectedTenantId() || rollup()?.tenantId || auth.currentUser()?.tenantId || 'default' }}
            </button>
          }

          @if (selectedAccountId()) {
            <mat-icon class="crumb-sep">chevron_right</mat-icon>
            <button
              type="button"
              class="crumb-btn"
              [class.active]="scope() === 'ACCOUNT'"
              (click)="drillToAccount(selectedAccountId()!)"
              id="crumb-account-btn"
            >
              <mat-icon>person</mat-icon>
              Account: {{ selectedAccountId() }}
            </button>
          }

          @if (selectedNamespaceId()) {
            <mat-icon class="crumb-sep">chevron_right</mat-icon>
            <button
              type="button"
              class="crumb-btn"
              [class.active]="scope() === 'NAMESPACE'"
              (click)="drillToNamespace(selectedNamespaceId()!)"
              id="crumb-namespace-btn"
            >
              <mat-icon>folder_special</mat-icon>
              Namespace: {{ rollup()?.slug || selectedNamespaceId() }}
            </button>
          }
        </nav>

        <div class="toolbar-controls">
          <div class="time-range-group" role="group" aria-label="Time range preset">
            @for (preset of timeRangePresets; track preset.id) {
              <button
                type="button"
                class="range-pill"
                [class.active]="timeRange() === preset.id"
                (click)="setTimeRange(preset.id)"
                [id]="'range-btn-' + preset.id"
              >
                {{ preset.label }}
              </button>
            }
          </div>

          <button
            mat-stroked-button
            type="button"
            (click)="reloadAll()"
            [disabled]="loading()"
            id="analytics-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>

          <button
            mat-flat-button
            color="primary"
            type="button"
            (click)="exportAggregatesCsv()"
            [disabled]="!rollup()"
            id="analytics-export-csv-btn"
          >
            <mat-icon>download</mat-icon>
            Export CSV (Aggregates)
          </button>
        </div>
      </div>

      <!-- Loading / Error / Empty States -->
      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="analytics-loading">
          <mat-spinner diameter="28" />
          <span>Loading {{ scope() }} analytics rollup...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="analytics-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="reloadAll()">Retry</button>
        </div>
      } @else if (!rollup()) {
        <div class="state-box empty-box" role="status" id="analytics-empty">
          <mat-icon>insights</mat-icon>
          <span>No analytics data available for the selected scope.</span>
        </div>
      } @else {
        <!-- Dashboard Grid: 6 Core Content-Free Analytics Panels -->
        <div class="charts-grid">
          <!-- 1. Memory Growth & Cognitive Tier Distribution -->
          <mat-card appearance="outlined" class="chart-card" id="chart-memory-tiers">
            <mat-card-header>
              <mat-icon mat-card-avatar>psychology</mat-icon>
              <mat-card-title>Memory Growth & Tier Distribution</mat-card-title>
              <mat-card-subtitle>
                {{ (rollup()?.tiers?.total ?? 0) | number }} total engrams ·
                24h growth {{ (rollup()?.growth?.growthRate24hPct ?? 0) | number:'1.1-2' }}%
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="growth-deltas">
                <div class="delta-chip">
                  <span class="delta-label">24h Delta</span>
                  <span class="delta-val cortex-mono">+{{ (rollup()?.growth?.delta24hMemories ?? 0) | number }}</span>
                </div>
                <div class="delta-chip">
                  <span class="delta-label">7d Delta</span>
                  <span class="delta-val cortex-mono">+{{ (rollup()?.growth?.delta7dMemories ?? 0) | number }}</span>
                </div>
                <div class="delta-chip">
                  <span class="delta-label">30d Delta</span>
                  <span class="delta-val cortex-mono">+{{ (rollup()?.growth?.delta30dMemories ?? 0) | number }}</span>
                </div>
              </div>

              <div class="tier-bars">
                @for (tier of tierRows(); track tier.key) {
                  <div class="bar-row">
                    <div class="bar-meta">
                      <span class="bar-name">{{ tier.label }}</span>
                      <span class="bar-count cortex-mono">
                        {{ tier.count | number }} ({{ tier.pct | number:'1.0-1' }}%)
                      </span>
                    </div>
                    <div class="bar-track" role="progressbar" [attr.aria-valuenow]="tier.pct" aria-valuemin="0" aria-valuemax="100">
                      <div class="bar-fill" [class]="'tier-' + tier.key" [style.width.%]="tier.pct"></div>
                    </div>
                  </div>
                }
              </div>
            </mat-card-content>
          </mat-card>

          <!-- 2. Storage Footprint & Quota Utilization -->
          <mat-card appearance="outlined" class="chart-card" id="chart-quota-utilization">
            <mat-card-header>
              <mat-icon mat-card-avatar>pie_chart</mat-icon>
              <mat-card-title>Storage & Quota Utilization</mat-card-title>
              <mat-card-subtitle>
                {{ formatBytes(rollup()?.storageBytes ?? 0) }} used ·
                {{ rollup()?.quotas?.quotaBreachCount ?? 0 }} active breach(es)
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="tier-bars">
                <div class="bar-row">
                  <div class="bar-meta">
                    <span class="bar-name">Storage Bytes</span>
                    <span class="bar-count cortex-mono">
                      {{ formatBytes(rollup()?.quotas?.storageBytesUsed ?? rollup()?.storageBytes ?? 0) }} /
                      {{ formatStorageQuota(rollup()?.quotas?.storageBytesQuota) }}
                      ({{ (rollup()?.quotas?.storageUtilizationPct ?? 0) | number:'1.0-1' }}%)
                    </span>
                  </div>
                  <div class="bar-track">
                    <div
                      class="bar-fill quota-fill"
                      [class.quota-warn]="(rollup()?.quotas?.storageUtilizationPct ?? 0) >= 85"
                      [style.width.%]="clampPct(rollup()?.quotas?.storageUtilizationPct)"
                    ></div>
                  </div>
                </div>

                <div class="bar-row">
                  <div class="bar-meta">
                    <span class="bar-name">Memory Count</span>
                    <span class="bar-count cortex-mono">
                      {{ (rollup()?.quotas?.memoryCountUsed ?? rollup()?.tiers?.total ?? 0) | number }} /
                      {{ formatCountQuota(rollup()?.quotas?.memoryCountQuota) }}
                      ({{ (rollup()?.quotas?.memoryUtilizationPct ?? 0) | number:'1.0-1' }}%)
                    </span>
                  </div>
                  <div class="bar-track">
                    <div
                      class="bar-fill quota-fill"
                      [class.quota-warn]="(rollup()?.quotas?.memoryUtilizationPct ?? 0) >= 85"
                      [style.width.%]="clampPct(rollup()?.quotas?.memoryUtilizationPct)"
                    ></div>
                  </div>
                </div>

                <div class="bar-row">
                  <div class="bar-meta">
                    <span class="bar-name">Namespaces</span>
                    <span class="bar-count cortex-mono">
                      {{ rollup()?.quotas?.namespaceCountUsed ?? rollup()?.namespaceCount ?? 0 }} /
                      {{ formatCountQuota(rollup()?.quotas?.namespaceCountQuota) }}
                      ({{ (rollup()?.quotas?.namespaceUtilizationPct ?? 0) | number:'1.0-1' }}%)
                    </span>
                  </div>
                  <div class="bar-track">
                    <div
                      class="bar-fill quota-fill"
                      [class.quota-warn]="(rollup()?.quotas?.namespaceUtilizationPct ?? 0) >= 85"
                      [style.width.%]="clampPct(rollup()?.quotas?.namespaceUtilizationPct)"
                    ></div>
                  </div>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- 3. Remember / Recall Throughput & Error Rate -->
          <mat-card appearance="outlined" class="chart-card" id="chart-throughput-errors">
            <mat-card-header>
              <mat-icon mat-card-avatar>speed</mat-icon>
              <mat-card-title>Throughput & Error Rates</mat-card-title>
              <mat-card-subtitle>
                Write & recall operation rates and operational error ratio
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="metric-tiles">
                <div class="metric-tile">
                  <span class="tile-label">Recall Rate</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.throughput?.recallOpsPerSec ?? 0) | number:'1.2-2' }} ops/s
                  </span>
                  <span class="tile-sub cortex-mono">
                    {{ (rollup()?.throughput?.recallTotal ?? 0) | number }} total
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Remember Rate</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.throughput?.rememberOpsPerSec ?? 0) | number:'1.2-2' }} ops/s
                  </span>
                  <span class="tile-sub cortex-mono">
                    {{ (rollup()?.throughput?.rememberTotal ?? 0) | number }} total
                  </span>
                </div>
                <div class="metric-tile" [class.tile-warn]="(rollup()?.throughput?.errorRate ?? 0) > 0.01">
                  <span class="tile-label">Error Rate</span>
                  <span class="tile-value cortex-mono">
                    {{ ((rollup()?.throughput?.errorRate ?? 0) * 100) | number:'1.2-2' }}%
                  </span>
                  <span class="tile-sub cortex-mono">
                    {{ (rollup()?.throughput?.errorCount ?? 0) | number }} error(s)
                  </span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- 4. Recall Latency Percentiles -->
          <mat-card appearance="outlined" class="chart-card" id="chart-latency-percentiles">
            <mat-card-header>
              <mat-icon mat-card-avatar>timer</mat-icon>
              <mat-card-title>Recall Latency Percentiles</mat-card-title>
              <mat-card-subtitle>
                Fused multi-phase retrieval latency distribution (ms)
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="tier-bars">
                @for (lat of latencyRows(); track lat.label) {
                  <div class="bar-row">
                    <div class="bar-meta">
                      <span class="bar-name">{{ lat.label }}</span>
                      <span class="bar-count cortex-mono">{{ lat.ms | number:'1.2-2' }} ms</span>
                    </div>
                    <div class="bar-track">
                      <div class="bar-fill latency-fill" [style.width.%]="lat.pct"></div>
                    </div>
                  </div>
                }
              </div>
            </mat-card-content>
          </mat-card>

          <!-- 5. Decay, Consolidation & Sleep/Dream Health -->
          <mat-card appearance="outlined" class="chart-card" id="chart-consolidation-dream">
            <mat-card-header>
              <mat-icon mat-card-avatar>bedtime</mat-icon>
              <mat-card-title>Decay, Consolidation & Dream Cycles</mat-card-title>
              <mat-card-subtitle>
                Ebbinghaus decay, episodic-to-semantic consolidation, and associative graph health
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="metric-tiles">
                <div class="metric-tile">
                  <span class="tile-label">Consolidations</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.consolidationsRun ?? 0) | number }}
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Dream Cycles</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.dreamCyclesRun ?? 0) | number }}
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Decayed Engrams</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.decayedEngrams ?? 0) | number }}
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Hebbian Edges</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.hebbianEdges ?? 0) | number }}
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Temporal Links</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.temporalLinks ?? 0) | number }}
                  </span>
                </div>
                <div class="metric-tile">
                  <span class="tile-label">Entity Graph</span>
                  <span class="tile-value cortex-mono">
                    {{ (rollup()?.consolidation?.entityNodes ?? 0) | number }}n /
                    {{ (rollup()?.consolidation?.entityEdges ?? 0) | number }}e
                  </span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- 6. Token & Cost Usage (Supersedes #226) -->
          <mat-card appearance="outlined" class="chart-card" id="chart-token-cost">
            <mat-card-header>
              <mat-icon mat-card-avatar>payments</mat-icon>
              <mat-card-title>Token & Cost Analytics</mat-card-title>
              <mat-card-subtitle>
                Aggregated LLM & embedding token consumption and estimated USD cost
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              @if (rollup()?.tokenUsage; as usage) {
                <div class="metric-tiles">
                  <div class="metric-tile">
                    <span class="tile-label">Estimated Cost</span>
                    <span class="tile-value cortex-mono">
                      \${{ (usage.estimatedCostUsd ?? 0) | number:'1.2-4' }}
                    </span>
                    <span class="tile-sub cortex-mono">
                      {{ (usage.requestCount ?? 0) | number }} requests
                    </span>
                  </div>
                  <div class="metric-tile">
                    <span class="tile-label">Total Tokens</span>
                    <span class="tile-value cortex-mono">
                      {{ (usage.totalTokens ?? 0) | number }}
                    </span>
                    <span class="tile-sub cortex-mono">
                      In: {{ (usage.inputTokens ?? 0) | number }} · Out: {{ (usage.outputTokens ?? 0) | number }}
                    </span>
                  </div>
                  <div class="metric-tile">
                    <span class="tile-label">Embedding Tokens</span>
                    <span class="tile-value cortex-mono">
                      {{ (usage.embeddingTokens ?? 0) | number }}
                    </span>
                    <span class="tile-sub">Vectorization volume</span>
                  </div>
                </div>
              } @else {
                <p class="muted-note">
                  Token and cost usage is aggregated at Account, Tenant, and Fleet levels.
                </p>
              }
            </mat-card-content>
          </mat-card>
        </div>

        <!-- Historical Time-Series Panel -->
        <mat-card appearance="outlined" class="timeseries-card" id="chart-timeseries">
          <mat-card-header>
            <mat-icon mat-card-avatar>timeline</mat-icon>
            <mat-card-title>Historical Time-Series ({{ timeRange() }})</mat-card-title>
            <mat-card-subtitle>
              {{ timeseriesPoints().length }} snapshot point(s) from /api/v1/admin/analytics/timeseries
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (timeseriesPoints().length === 0) {
              <p class="muted-note">No historical snapshots recorded in this window yet.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Historical analytics time-series">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Total Memories</th>
                      <th>Tiers (W/E/S/P)</th>
                      <th>Storage</th>
                      <th>Recalls / Writes</th>
                      <th>Avg Latency</th>
                      <th>Dreams / Consolidations</th>
                      <th>Errors</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (pt of timeseriesPoints(); track pt.timestamp) {
                      <tr>
                        <td class="cortex-mono">{{ formatTimestamp(pt.timestamp) }}</td>
                        <td class="cortex-mono">{{ pt.totalCount | number }}</td>
                        <td class="cortex-mono">
                          {{ pt.workingCount }}/{{ pt.episodicCount }}/{{ pt.semanticCount }}/{{ pt.proceduralCount }}
                        </td>
                        <td class="cortex-mono">{{ formatBytes(pt.storageBytes) }}</td>
                        <td class="cortex-mono">{{ pt.recallCount }} / {{ pt.rememberCount }}</td>
                        <td class="cortex-mono">{{ pt.avgLatencyMs | number:'1.2-2' }} ms</td>
                        <td class="cortex-mono">{{ pt.dreamCyclesRun }} / {{ pt.consolidationsRun }}</td>
                        <td class="cortex-mono">{{ pt.errorCount }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </mat-card-content>
        </mat-card>

        <!-- Hierarchical Child Drill-Down Tables -->
        @if (scope() === 'FLEET' && (rollup()?.tenants?.length ?? 0) > 0) {
          <mat-card appearance="outlined" class="drill-table-card" id="drill-tenants-table">
            <mat-card-header>
              <mat-icon mat-card-avatar>domain</mat-icon>
              <mat-card-title>Drill Down by Tenant ({{ rollup().tenants.length }})</mat-card-title>
              <mat-card-subtitle>Select a tenant to inspect account and namespace analytics</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Fleet tenant drill-down">
                  <thead>
                    <tr>
                      <th>Tenant</th>
                      <th>Plan</th>
                      <th>Accounts</th>
                      <th>Namespaces</th>
                      <th>Memories</th>
                      <th>Storage</th>
                      <th>Throughput</th>
                      <th>Tokens / Cost</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (t of rollup().tenants; track t.tenantId) {
                      <tr>
                        <td>
                          <span class="primary-cell">{{ t.displayName || t.tenantId }}</span>
                          <span class="sub-cell cortex-mono">{{ t.tenantId }}</span>
                        </td>
                        <td><span class="pill-badge">{{ t.plan }}</span></td>
                        <td class="cortex-mono">{{ t.accountCount }}</td>
                        <td class="cortex-mono">{{ t.namespaceCount }}</td>
                        <td class="cortex-mono">{{ t.totalMemories | number }}</td>
                        <td class="cortex-mono">{{ formatBytes(t.storageBytes) }}</td>
                        <td class="cortex-mono">
                          R:{{ t.recallOpsPerSec | number:'1.1-1' }}/s · W:{{ t.rememberOpsPerSec | number:'1.1-1' }}/s
                        </td>
                        <td class="cortex-mono">
                          {{ (t.totalTokens ?? 0) | number }} (\${{ (t.estimatedCostUsd ?? 0) | number:'1.2-4' }})
                        </td>
                        <td>
                          <button
                            mat-stroked-button
                            type="button"
                            class="drill-btn"
                            (click)="drillToTenant(t.tenantId)"
                          >
                            Drill Down
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </mat-card-content>
          </mat-card>
        }

        @if (scope() === 'TENANT' && (rollup()?.accounts?.length ?? 0) > 0) {
          <mat-card appearance="outlined" class="drill-table-card" id="drill-accounts-table">
            <mat-card-header>
              <mat-icon mat-card-avatar>group</mat-icon>
              <mat-card-title>Drill Down by Account ({{ rollup().accounts.length }})</mat-card-title>
              <mat-card-subtitle>Select an account or agent principal to inspect its owned namespaces</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Tenant account drill-down">
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th>Kind</th>
                      <th>Namespaces</th>
                      <th>Memories</th>
                      <th>Storage</th>
                      <th>Tokens / Cost</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (acc of rollup().accounts; track acc.accountId) {
                      <tr>
                        <td>
                          <span class="primary-cell">{{ acc.displayName || acc.username }}</span>
                          <span class="sub-cell cortex-mono">{{ acc.accountId }}</span>
                        </td>
                        <td><span class="pill-badge">{{ acc.principalType }}</span></td>
                        <td class="cortex-mono">{{ acc.namespaceCount }}</td>
                        <td class="cortex-mono">{{ (acc.tiers?.total ?? 0) | number }}</td>
                        <td class="cortex-mono">{{ formatBytes(acc.storageBytes) }}</td>
                        <td class="cortex-mono">
                          {{ (acc.tokenUsage?.totalTokens ?? 0) | number }}
                          (\${{ (acc.tokenUsage?.estimatedCostUsd ?? 0) | number:'1.2-4' }})
                        </td>
                        <td>
                          <button
                            mat-stroked-button
                            type="button"
                            class="drill-btn"
                            (click)="drillToAccount(acc.accountId)"
                          >
                            Drill Down
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </mat-card-content>
          </mat-card>
        }

        @if ((scope() === 'TENANT' || scope() === 'ACCOUNT') && (rollup()?.namespaces?.length ?? 0) > 0) {
          <mat-card appearance="outlined" class="drill-table-card" id="drill-namespaces-table">
            <mat-card-header>
              <mat-icon mat-card-avatar>folder_special</mat-icon>
              <mat-card-title>Drill Down by Namespace ({{ rollup().namespaces.length }})</mat-card-title>
              <mat-card-subtitle>Select a namespace to inspect isolated tier, latency, and consolidation telemetry</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Namespace drill-down">
                  <thead>
                    <tr>
                      <th>Namespace</th>
                      <th>Status</th>
                      <th>Memories</th>
                      <th>Storage</th>
                      <th>p95 Latency</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (ns of rollup().namespaces; track ns.namespaceId) {
                      <tr>
                        <td>
                          <span class="primary-cell">{{ ns.displayName || ns.slug }}</span>
                          <span class="sub-cell cortex-mono">{{ ns.slug }} ({{ ns.namespaceId }})</span>
                        </td>
                        <td><span class="pill-badge">{{ ns.status }}</span></td>
                        <td class="cortex-mono">{{ (ns.tiers?.total ?? 0) | number }}</td>
                        <td class="cortex-mono">{{ formatBytes(ns.storageBytes) }}</td>
                        <td class="cortex-mono">{{ (ns.throughput?.latencyP95Ms ?? 0) | number:'1.2-2' }} ms</td>
                        <td>
                          <button
                            mat-stroked-button
                            type="button"
                            class="drill-btn"
                            (click)="drillToNamespace(ns.namespaceId)"
                          >
                            Drill Down
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </mat-card-content>
          </mat-card>
        }
      }
    </cortex-admin-section-shell>
  `,
  styles: [`
    .analytics-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }
    .drill-breadcrumb {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
    .crumb-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 9999px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.78rem;
      font-weight: 600;
      cursor: pointer;
      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
      &.active {
        background: var(--mat-sys-primary-container);
        color: var(--mat-sys-on-primary-container);
        border-color: var(--mat-sys-primary);
      }
    }
    .crumb-sep {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--mat-sys-outline);
    }
    .toolbar-controls {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .time-range-group {
      display: inline-flex;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      overflow: hidden;
    }
    .range-pill {
      padding: 6px 12px;
      border: none;
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      &.active {
        background: var(--mat-sys-primary);
        color: var(--mat-sys-on-primary);
      }
    }
    .state-box {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
      color: var(--mat-sys-on-surface-variant);
    }
    .error-box {
      border-color: var(--mat-sys-error);
      color: var(--mat-sys-error);
    }
    .charts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
      gap: 16px;
    }
    .chart-card {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
    .growth-deltas {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-bottom: 14px;
    }
    .delta-chip {
      padding: 8px 10px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .delta-label {
      font-size: 0.68rem;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .delta-val {
      font-size: 0.9rem;
      font-weight: 700;
      color: var(--mat-sys-primary);
    }
    .tier-bars {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-top: 8px;
    }
    .bar-row {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .bar-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.78rem;
    }
    .bar-name {
      font-weight: 600;
    }
    .bar-count {
      color: var(--mat-sys-on-surface-variant);
    }
    .bar-track {
      height: 8px;
      border-radius: 9999px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      border-radius: 9999px;
      background: var(--mat-sys-primary);
      transition: width 0.25s ease;
    }
    .bar-fill.quota-warn {
      background: var(--mat-sys-error);
    }
    .metric-tiles {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 10px;
      margin-top: 8px;
    }
    .metric-tile {
      padding: 10px 12px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
      display: flex;
      flex-direction: column;
      gap: 4px;
      &.tile-warn {
        border-color: var(--mat-sys-error);
      }
    }
    .tile-label {
      font-size: 0.7rem;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .tile-value {
      font-size: 1.05rem;
      font-weight: 700;
    }
    .tile-sub {
      font-size: 0.72rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .muted-note {
      font-size: 0.82rem;
      color: var(--mat-sys-on-surface-variant);
      margin: 8px 0 0;
    }
    .admin-table-wrap {
      overflow-x: auto;
      margin-top: 8px;
    }
    .admin-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
      th, td {
        text-align: left;
        padding: 9px 10px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }
      th {
        font-size: 0.7rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--mat-sys-on-surface-variant);
      }
    }
    .primary-cell {
      display: block;
      font-weight: 600;
    }
    .sub-cell {
      display: block;
      font-size: 0.72rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .pill-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
    }
    .drill-btn {
      font-size: 0.75rem;
      height: 28px;
      padding: 0 10px;
    }
  `],
})
export class AdminAnalyticsComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly scope = signal<AnalyticsDrillScope>('FLEET');
  readonly selectedTenantId = signal<string | null>(null);
  readonly selectedAccountId = signal<string | null>(null);
  readonly selectedNamespaceId = signal<string | null>(null);

  readonly timeRange = signal<AnalyticsTimeRangePreset>('24h');
  readonly rollup = signal<any | null>(null);
  readonly timeseriesPoints = signal<any[]>([]);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly lastExportedCsv = signal<string>('');

  readonly timeRangePresets: { id: AnalyticsTimeRangePreset; label: string; hours: number }[] = [
    { id: '24h', label: '24h', hours: 24 },
    { id: '7d', label: '7d', hours: 24 * 7 },
    { id: '30d', label: '30d', hours: 24 * 30 },
    { id: '90d', label: '90d', hours: 24 * 90 },
  ];

  readonly tierRows = computed(() => {
    const t = this.rollup()?.tiers;
    const total = Math.max(1, t?.total || 0);
    const items = [
      { key: 'working', label: 'Working', count: t?.working ?? 0 },
      { key: 'episodic', label: 'Episodic', count: t?.episodic ?? 0 },
      { key: 'semantic', label: 'Semantic', count: t?.semantic ?? 0 },
      { key: 'procedural', label: 'Procedural', count: t?.procedural ?? 0 },
    ];
    return items.map((item) => ({
      ...item,
      pct: t?.total ? Math.min(100, (item.count / total) * 100) : 0,
    }));
  });

  readonly latencyRows = computed(() => {
    const tp = this.rollup()?.throughput;
    const p50 = tp?.latencyP50Ms ?? 0;
    const mean = tp?.latencyMeanMs ?? 0;
    const p95 = tp?.latencyP95Ms ?? 0;
    const p99 = tp?.latencyP99Ms ?? 0;
    const maxLat = Math.max(1, p50, mean, p95, p99);
    return [
      { label: 'p50 Median', ms: p50, pct: Math.min(100, (p50 / maxLat) * 100) },
      { label: 'Mean', ms: mean, pct: Math.min(100, (mean / maxLat) * 100) },
      { label: 'p95 Tail', ms: p95, pct: Math.min(100, (p95 / maxLat) * 100) },
      { label: 'p99 Extreme', ms: p99, pct: Math.min(100, (p99 / maxLat) * 100) },
    ];
  });

  protected readonly formatBytes = formatAdminBytes;
  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    if (this.auth.isSuperAdmin()) {
      this.scope.set('FLEET');
    } else {
      this.scope.set('TENANT');
      this.selectedTenantId.set(this.auth.currentUser()?.tenantId || null);
    }
    this.reloadAll();
  }

  drillToFleet(): void {
    if (!this.auth.isSuperAdmin()) return;
    this.scope.set('FLEET');
    this.selectedTenantId.set(null);
    this.selectedAccountId.set(null);
    this.selectedNamespaceId.set(null);
    this.reloadAll();
  }

  drillToTenant(tenantId?: string | null): void {
    const resolved = tenantId || this.selectedTenantId() || this.auth.currentUser()?.tenantId || 'default';
    this.scope.set('TENANT');
    this.selectedTenantId.set(resolved);
    this.selectedAccountId.set(null);
    this.selectedNamespaceId.set(null);
    this.reloadAll();
  }

  drillToAccount(accountId: string): void {
    this.scope.set('ACCOUNT');
    this.selectedAccountId.set(accountId);
    this.selectedNamespaceId.set(null);
    this.reloadAll();
  }

  drillToNamespace(namespaceId: string): void {
    this.scope.set('NAMESPACE');
    this.selectedNamespaceId.set(namespaceId);
    this.reloadAll();
  }

  setTimeRange(preset: AnalyticsTimeRangePreset): void {
    this.timeRange.set(preset);
    this.loadTimeseries();
  }

  reloadAll(): void {
    this.loadRollup();
    this.loadTimeseries();
  }

  loadRollup(): void {
    this.loading.set(true);
    this.error.set(null);

    const activeScope = this.scope();
    let req$;

    if (activeScope === 'FLEET' && this.auth.isSuperAdmin()) {
      req$ = this.api.getFleetAnalytics();
    } else if (activeScope === 'TENANT') {
      const tid = this.selectedTenantId();
      req$ =
        this.auth.isSuperAdmin() && tid
          ? this.api.getTenantAnalytics(tid)
          : this.api.getCallerTenantAnalytics(tid || undefined);
    } else if (activeScope === 'ACCOUNT' && this.selectedAccountId()) {
      req$ = this.api.getAccountAnalytics(this.selectedAccountId()!);
    } else if (activeScope === 'NAMESPACE' && this.selectedNamespaceId()) {
      req$ = this.api.getNamespaceAnalytics(this.selectedNamespaceId()!);
    } else {
      req$ = this.auth.isSuperAdmin()
        ? this.api.getFleetAnalytics()
        : this.api.getCallerTenantAnalytics();
    }

    req$.subscribe({
      next: (data) => {
        this.rollup.set(data ?? null);
        if (data?.tenantId && !this.selectedTenantId()) {
          this.selectedTenantId.set(data.tenantId);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load analytics rollup.');
        this.loading.set(false);
      },
    });
  }

  loadTimeseries(): void {
    const presetObj =
      this.timeRangePresets.find((p) => p.id === this.timeRange()) ?? this.timeRangePresets[0];
    const now = new Date();
    const fromDate = new Date(now.getTime() - presetObj.hours * 3600 * 1000);

    this.api
      .getAnalyticsTimeseries({
        scope: this.scope(),
        tenantId: this.selectedTenantId() || undefined,
        accountId: this.selectedAccountId() || undefined,
        namespaceId: this.selectedNamespaceId() || undefined,
        from: fromDate.toISOString(),
        to: now.toISOString(),
        limit: 100,
      })
      .pipe(catchError(() => of({ points: [] })))
      .subscribe((res) => {
        this.timeseriesPoints.set(Array.isArray(res?.points) ? res.points : []);
      });
  }

  buildAggregateCsv(): string {
    const r = this.rollup();
    if (!r) return '';

    const lines: string[] = [];
    lines.push(
      'section,scope,target_id,metric,value',
    );
    const targetId =
      this.selectedNamespaceId() ||
      this.selectedAccountId() ||
      this.selectedTenantId() ||
      'fleet';
    const sc = this.scope();

    lines.push(`summary,${sc},${targetId},storage_bytes,${r.storageBytes ?? 0}`);
    lines.push(`summary,${sc},${targetId},total_memories,${r.tiers?.total ?? 0}`);
    lines.push(`summary,${sc},${targetId},working_memories,${r.tiers?.working ?? 0}`);
    lines.push(`summary,${sc},${targetId},episodic_memories,${r.tiers?.episodic ?? 0}`);
    lines.push(`summary,${sc},${targetId},semantic_memories,${r.tiers?.semantic ?? 0}`);
    lines.push(`summary,${sc},${targetId},procedural_memories,${r.tiers?.procedural ?? 0}`);
    lines.push(`summary,${sc},${targetId},recall_ops_per_sec,${r.throughput?.recallOpsPerSec ?? 0}`);
    lines.push(`summary,${sc},${targetId},remember_ops_per_sec,${r.throughput?.rememberOpsPerSec ?? 0}`);
    lines.push(`summary,${sc},${targetId},latency_p50_ms,${r.throughput?.latencyP50Ms ?? 0}`);
    lines.push(`summary,${sc},${targetId},latency_p95_ms,${r.throughput?.latencyP95Ms ?? 0}`);
    lines.push(`summary,${sc},${targetId},latency_p99_ms,${r.throughput?.latencyP99Ms ?? 0}`);
    lines.push(`summary,${sc},${targetId},error_rate,${r.throughput?.errorRate ?? 0}`);
    lines.push(`summary,${sc},${targetId},consolidations_run,${r.consolidation?.consolidationsRun ?? 0}`);
    lines.push(`summary,${sc},${targetId},dream_cycles_run,${r.consolidation?.dreamCyclesRun ?? 0}`);
    lines.push(`summary,${sc},${targetId},total_tokens,${r.tokenUsage?.totalTokens ?? 0}`);
    lines.push(`summary,${sc},${targetId},estimated_cost_usd,${r.tokenUsage?.estimatedCostUsd ?? 0}`);

    for (const pt of this.timeseriesPoints()) {
      lines.push(
        `timeseries,${sc},${targetId},${pt.timestamp},total=${pt.totalCount};bytes=${pt.storageBytes};recalls=${pt.recallCount};writes=${pt.rememberCount};avgLatencyMs=${pt.avgLatencyMs}`,
      );
    }

    return lines.join('\n');
  }

  exportAggregatesCsv(): void {
    const csv = this.buildAggregateCsv();
    if (!csv) return;
    this.lastExportedCsv.set(csv);

    try {
      if (typeof Blob !== 'undefined' && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `spector-analytics-${this.scope().toLowerCase()}-${this.timeRange()}.csv`);
        link.click();
        URL.revokeObjectURL(url);
      }
    } catch {
      // Safe fallback in headless test environments
    }

    this.snack.open('Exported content-free aggregate analytics CSV', 'OK', { duration: 3000 });
  }

  clampPct(val?: number | null): number {
    if (!val || Number.isNaN(val) || val < 0) return 0;
    return Math.min(100, val);
  }

  formatCountQuota(val?: number | null): string {
    if (val === undefined || val === null || val < 0) return 'Unlimited';
    return val.toLocaleString();
  }

  formatStorageQuota(val?: number | null): string {
    if (val === undefined || val === null || val < 0) return 'Unlimited';
    return formatAdminBytes(val);
  }
}
