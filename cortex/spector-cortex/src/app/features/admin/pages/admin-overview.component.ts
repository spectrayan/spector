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
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import {
  AdminSectionShellComponent,
  formatAdminBytes,
  formatAdminTimestamp,
} from '../admin-console.component';

/**
 * Admin Console Overview page (`/admin/overview`, Issue #1064).
 *
 * Displays fleet-wide health cards for Platform Operators (`super-admin`) or
 * tenant-scoped health cards for Tenant Admins (`admin`), including tenants,
 * accounts, namespaces, storage bytes, ops/sec throughput, error rate, and quota alerts.
 * Strictly content-free: never displays raw memory text, embeddings, or secrets.
 */
@Component({
  selector: 'cortex-admin-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
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
      icon="space_dashboard"
      title="Admin Console Overview"
      [subtitle]="
        auth.isSuperAdmin()
          ? 'Fleet-wide operational health, throughput, and quota telemetry (zero memory content).'
          : 'Tenant operational health, throughput, and quota telemetry (zero memory content).'
      "
    >
      <div class="overview-toolbar" role="toolbar" aria-label="Overview actions">
        <span class="scope-pill">
          <mat-icon>{{ auth.isSuperAdmin() ? 'public' : 'domain' }}</mat-icon>
          {{ auth.isSuperAdmin() ? 'Scope: Fleet Operator' : 'Scope: Tenant (' + (rollup()?.tenantId || 'default') + ')' }}
        </span>
        <button
          mat-stroked-button
          type="button"
          (click)="loadOverview()"
          [disabled]="loading()"
          aria-label="Refresh overview telemetry"
          id="admin-overview-refresh-btn"
        >
          <mat-icon>refresh</mat-icon>
          Refresh
        </button>
      </div>

      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="admin-overview-loading">
          <mat-spinner diameter="28" />
          <span>Loading operational health rollup...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="admin-overview-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="loadOverview()">Retry</button>
        </div>
      } @else if (!rollup()) {
        <div class="state-box empty-box" role="status" id="admin-overview-empty">
          <mat-icon>analytics</mat-icon>
          <span>No administrative telemetry available yet.</span>
        </div>
      } @else {
        @if (hasQuotaAlert()) {
          <div class="quota-alert-banner" role="alert" id="admin-overview-quota-alert">
            <mat-icon>warning_amber</mat-icon>
            <div class="alert-text">
              <strong>Quota Alert:</strong>
              {{ rollup()?.quotas?.quotaBreachCount || 0 }} quota breach(es) recorded ·
              Storage utilization {{ (rollup()?.quotas?.storageUtilizationPct || 0) | number:'1.0-1' }}% ·
              Namespace utilization {{ (rollup()?.quotas?.namespaceUtilizationPct || 0) | number:'1.0-1' }}%
            </div>
            <a mat-button routerLink="/admin/namespaces">Inspect Quotas</a>
          </div>
        }

        <!-- Health KPI Cards -->
        <div class="kpi-grid" role="list" aria-label="Operational health indicators">
          @if (auth.isSuperAdmin() || rollup()?.tenantCount !== undefined) {
            <mat-card appearance="outlined" class="kpi-card" role="listitem" id="kpi-tenants">
              <div class="kpi-top">
                <span class="kpi-label">Tenants</span>
                <mat-icon class="kpi-icon">domain</mat-icon>
              </div>
              <div class="kpi-value cortex-mono">{{ rollup()?.tenantCount ?? 1 }}</div>
              <div class="kpi-meta">
                {{ rollup()?.activeTenantCount ?? 1 }} active in fleet
              </div>
            </mat-card>
          }

          <mat-card appearance="outlined" class="kpi-card" role="listitem" id="kpi-accounts">
            <div class="kpi-top">
              <span class="kpi-label">Accounts & Agents</span>
              <mat-icon class="kpi-icon">group</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">{{ rollup()?.accountCount ?? 0 }}</div>
            <div class="kpi-meta">
              @if (rollup()?.humanCount !== undefined || rollup()?.agentCount !== undefined) {
                {{ rollup()?.humanCount ?? 0 }} human · {{ rollup()?.agentCount ?? 0 }} agent
              } @else {
                Provisioned principals
              }
            </div>
          </mat-card>

          <mat-card appearance="outlined" class="kpi-card" role="listitem" id="kpi-namespaces">
            <div class="kpi-top">
              <span class="kpi-label">Namespaces</span>
              <mat-icon class="kpi-icon">folder_special</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">{{ rollup()?.namespaceCount ?? 0 }}</div>
            <div class="kpi-meta">
              {{ (rollup()?.tiers?.total ?? 0) | number }} total engrams (count-only)
            </div>
          </mat-card>

          <mat-card appearance="outlined" class="kpi-card" role="listitem" id="kpi-storage">
            <div class="kpi-top">
              <span class="kpi-label">Storage Footprint</span>
              <mat-icon class="kpi-icon">storage</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">{{ formatBytes(rollup()?.storageBytes ?? 0) }}</div>
            <div class="kpi-meta">
              W:{{ rollup()?.tiers?.working ?? 0 }} · E:{{ rollup()?.tiers?.episodic ?? 0 }} ·
              S:{{ rollup()?.tiers?.semantic ?? 0 }} · P:{{ rollup()?.tiers?.procedural ?? 0 }}
            </div>
          </mat-card>

          <mat-card appearance="outlined" class="kpi-card" role="listitem" id="kpi-ops">
            <div class="kpi-top">
              <span class="kpi-label">Throughput (Ops/sec)</span>
              <mat-icon class="kpi-icon">speed</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">{{ totalOpsPerSec() | number:'1.2-2' }}/s</div>
            <div class="kpi-meta">
              Recall {{ (rollup()?.throughput?.recallOpsPerSec ?? 0) | number:'1.2-2' }}/s ·
              Write {{ (rollup()?.throughput?.rememberOpsPerSec ?? 0) | number:'1.2-2' }}/s ·
              p95 {{ (rollup()?.throughput?.latencyP95Ms ?? 0) | number:'1.1-1' }}ms
            </div>
          </mat-card>

          <mat-card
            appearance="outlined"
            class="kpi-card"
            [class.kpi-warn]="(rollup()?.throughput?.errorRate ?? 0) > 0.01"
            role="listitem"
            id="kpi-error-rate"
          >
            <div class="kpi-top">
              <span class="kpi-label">Error Rate</span>
              <mat-icon class="kpi-icon">health_and_safety</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">
              {{ ((rollup()?.throughput?.errorRate ?? 0) * 100) | number:'1.2-2' }}%
            </div>
            <div class="kpi-meta">
              {{ rollup()?.throughput?.errorCount ?? 0 }} total operational errors
            </div>
          </mat-card>

          <mat-card
            appearance="outlined"
            class="kpi-card"
            [class.kpi-warn]="hasQuotaAlert()"
            role="listitem"
            id="kpi-quota-alerts"
          >
            <div class="kpi-top">
              <span class="kpi-label">Quota Alerts</span>
              <mat-icon class="kpi-icon">gpp_maybe</mat-icon>
            </div>
            <div class="kpi-value cortex-mono">{{ rollup()?.quotas?.quotaBreachCount ?? 0 }}</div>
            <div class="kpi-meta">
              Storage {{ (rollup()?.quotas?.storageUtilizationPct ?? 0) | number:'1.0-1' }}% ·
              Memories {{ (rollup()?.quotas?.memoryUtilizationPct ?? 0) | number:'1.0-1' }}%
            </div>
          </mat-card>
        </div>

        @if (auth.isSuperAdmin() && (rollup()?.tenants?.length ?? 0) > 0) {
          <mat-card appearance="outlined" class="fleet-table-card">
            <mat-card-header>
              <mat-icon mat-card-avatar>domain</mat-icon>
              <mat-card-title>Fleet Tenant Summary</mat-card-title>
              <mat-card-subtitle>Aggregate resource and throughput telemetry by tenant</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Fleet tenant health summary">
                  <thead>
                    <tr>
                      <th>Tenant</th>
                      <th>Plan</th>
                      <th>Status</th>
                      <th>Accounts</th>
                      <th>Namespaces</th>
                      <th>Memories</th>
                      <th>Storage</th>
                      <th>Quota Breaches</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (t of rollup().tenants; track t.tenantId) {
                      <tr>
                        <td>
                          <span class="primary-cell">{{ t.displayName || t.tenantId }}</span>
                          <span class="sub-cell cortex-mono">{{ t.tenantId }}</span>
                        </td>
                        <td><span class="pill-badge">{{ t.plan || 'ENTERPRISE' }}</span></td>
                        <td>
                          <span class="status-pill" [class.active]="t.status === 'ACTIVE'">
                            {{ t.status || 'ACTIVE' }}
                          </span>
                        </td>
                        <td class="cortex-mono">{{ t.accountCount }}</td>
                        <td class="cortex-mono">{{ t.namespaceCount }}</td>
                        <td class="cortex-mono">{{ t.totalMemories | number }}</td>
                        <td class="cortex-mono">{{ formatBytes(t.storageBytes) }}</td>
                        <td class="cortex-mono">{{ t.quotaBreachCount }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </mat-card-content>
          </mat-card>
        }
      }

      <!-- Quick Navigation Section -->
      <h2 class="sub-heading">Administration Modules</h2>
      <div class="overview-grid" role="navigation" aria-label="Admin Console modules">
        @if (auth.isSuperAdmin()) {
          <mat-card appearance="outlined" class="overview-card" routerLink="/admin/tenants" id="overview-link-tenants">
            <mat-card-header>
              <mat-icon mat-card-avatar>domain</mat-icon>
              <mat-card-title>Tenants</mat-card-title>
              <mat-card-subtitle>Platform operator tenant lifecycle, BYOK status & break-glass policy</mat-card-subtitle>
            </mat-card-header>
          </mat-card>
        }
        <mat-card appearance="outlined" class="overview-card" routerLink="/admin/accounts" id="overview-link-accounts">
          <mat-card-header>
            <mat-icon mat-card-avatar>group</mat-icon>
            <mat-card-title>Accounts & Agents</mat-card-title>
            <mat-card-subtitle>Manage users, agent principals, and credential oversight</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card appearance="outlined" class="overview-card" routerLink="/admin/namespaces" id="overview-link-namespaces">
          <mat-card-header>
            <mat-icon mat-card-avatar>folder_special</mat-icon>
            <mat-card-title>Namespaces</mat-card-title>
            <mat-card-subtitle>Quotas, archival, legal hold, ownership transfer, and verified erasure</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card appearance="outlined" class="overview-card" routerLink="/admin/analytics" id="overview-link-analytics">
          <mat-card-header>
            <mat-icon mat-card-avatar>insights</mat-icon>
            <mat-card-title>Analytics</mat-card-title>
            <mat-card-subtitle>Content-free fleet, tenant, and namespace telemetry</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card appearance="outlined" class="overview-card" routerLink="/admin/infra" id="overview-link-infra">
          <mat-card-header>
            <mat-icon mat-card-avatar>dns</mat-icon>
            <mat-card-title>Infrastructure & Benchmarks</mat-card-title>
            <mat-card-subtitle>Providers, cluster health, tasks, cache, and benchmarks</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card appearance="outlined" class="overview-card" routerLink="/admin/audit" id="overview-link-audit">
          <mat-card-header>
            <mat-icon mat-card-avatar>fact_check</mat-icon>
            <mat-card-title>Audit Log</mat-card-title>
            <mat-card-subtitle>Append-only administrative and security event stream</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>
    </cortex-admin-section-shell>
  `,
  styles: [`
    .overview-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }
    .scope-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.8rem;
      font-weight: 600;
      padding: 4px 12px;
      border-radius: 9999px;
      background: var(--mat-sys-surface-container-high);
      border: 1px solid var(--mat-sys-outline-variant);
      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: var(--mat-sys-primary);
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
    .quota-alert-banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 12px;
      background: color-mix(in srgb, var(--mat-sys-error-container) 70%, transparent);
      color: var(--mat-sys-on-error-container);
      border: 1px solid var(--mat-sys-error);
      .alert-text {
        flex: 1;
        font-size: 0.85rem;
      }
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 14px;
    }
    .kpi-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      &.kpi-warn {
        border-color: var(--mat-sys-error);
      }
    }
    .kpi-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .kpi-label {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--mat-sys-on-surface-variant);
    }
    .kpi-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--mat-sys-primary);
    }
    .kpi-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--mat-sys-on-surface);
    }
    .kpi-meta {
      font-size: 0.75rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .sub-heading {
      font-size: 1rem;
      font-weight: 600;
      margin: 8px 0 0;
      color: var(--mat-sys-on-surface);
    }
    .overview-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }
    .overview-card {
      cursor: pointer;
      transition: border-color 0.2s ease, transform 0.2s ease;
      &:hover {
        border-color: var(--mat-sys-primary);
        transform: translateY(-2px);
      }
    }
    .admin-table-wrap {
      overflow-x: auto;
    }
    .admin-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;
      th, td {
        text-align: left;
        padding: 10px 12px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }
      th {
        font-size: 0.72rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
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
    .pill-badge, .status-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
    }
    .status-pill.active {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
  `],
})
export class AdminOverviewComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);

  readonly rollup = signal<any | null>(null);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);

  readonly totalOpsPerSec = computed(() => {
    const r = this.rollup();
    if (!r?.throughput) return 0;
    return (r.throughput.recallOpsPerSec || 0) + (r.throughput.rememberOpsPerSec || 0);
  });

  readonly hasQuotaAlert = computed(() => {
    const q = this.rollup()?.quotas;
    if (!q) return false;
    return (
      (q.quotaBreachCount ?? 0) > 0 ||
      (q.storageUtilizationPct ?? 0) >= 85 ||
      (q.memoryUtilizationPct ?? 0) >= 85 ||
      (q.namespaceUtilizationPct ?? 0) >= 85
    );
  });

  protected readonly formatBytes = formatAdminBytes;
  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadOverview();
  }

  loadOverview(): void {
    this.loading.set(true);
    this.error.set(null);

    const request$ = this.auth.isSuperAdmin()
      ? this.api.getFleetAnalytics()
      : this.api.getCallerTenantAnalytics();

    request$.subscribe({
      next: (data) => {
        this.rollup.set(data ?? null);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load operational health telemetry.');
        this.loading.set(false);
      },
    });
  }
}
