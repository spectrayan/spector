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
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import {
  AdminSectionShellComponent,
  formatAdminTimestamp,
} from '../admin-console.component';

/**
 * Admin Audit Log Viewer (`/admin/audit`, Issue #1066, ADR-0087).
 *
 * <p>Queries and verifies the tamper-evident, SHA-256 hash-chained administrative
 * audit stream at `/api/v1/admin/audit` with multi-dimensional filtering and
 * content-free CSV export.</p>
 */
@Component({
  selector: 'cortex-admin-audit',
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
      icon="fact_check"
      title="Tamper-Evident Audit Log"
      subtitle="Append-only SHA-256 hash-chained administrative and security event stream (zero memory content)."
    >
      <!-- Hash-Chain Verification Banner -->
      @if (chainVerification(); as verify) {
        <div
          class="chain-banner"
          [class.chain-valid]="verify.valid !== false"
          [class.chain-invalid]="verify.valid === false"
          role="status"
          id="audit-chain-banner"
        >
          <mat-icon>{{ verify.valid !== false ? 'verified' : 'gpp_bad' }}</mat-icon>
          <div class="chain-text">
            <strong>
              {{ verify.valid !== false ? 'SHA-256 Hash Chain Verified' : 'Hash Chain Integrity Violation Detected' }}
            </strong>
            <span>
              Checked {{ verify.checkedEvents ?? verify.totalEvents ?? events().length }} event(s)
              @if (verify.headHash || verify.lastHash) {
                · Head SHA-256: <code class="cortex-mono">{{ verify.headHash || verify.lastHash }}</code>
              }
            </span>
          </div>
        </div>
      }

      <!-- Filter & Actions Toolbar -->
      <mat-card appearance="outlined" class="filter-card">
        <mat-card-content>
          <div class="filter-grid" role="search" aria-label="Audit log filters">
            @if (auth.isSuperAdmin()) {
              <div class="field-group">
                <label for="audit-filter-tenant">Tenant ID</label>
                <input
                  id="audit-filter-tenant"
                  type="text"
                  class="admin-input"
                  [(ngModel)]="filters.tenantId"
                  placeholder="All tenants"
                />
              </div>
            }

            <div class="field-group">
              <label for="audit-filter-actor">Actor ID</label>
              <input
                id="audit-filter-actor"
                type="text"
                class="admin-input"
                [(ngModel)]="filters.actorId"
                placeholder="e.g. admin"
              />
            </div>

            <div class="field-group">
              <label for="audit-filter-action">Action</label>
              <input
                id="audit-filter-action"
                type="text"
                class="admin-input"
                [(ngModel)]="filters.action"
                placeholder="e.g. NAMESPACE_ERASE"
              />
            </div>

            <div class="field-group">
              <label for="audit-filter-target-type">Target Type</label>
              <input
                id="audit-filter-target-type"
                type="text"
                class="admin-input"
                [(ngModel)]="filters.targetType"
                placeholder="TENANT / ACCOUNT / NAMESPACE"
              />
            </div>

            <div class="field-group">
              <label for="audit-filter-namespace">Namespace ID</label>
              <input
                id="audit-filter-namespace"
                type="text"
                class="admin-input"
                [(ngModel)]="filters.namespaceId"
                placeholder="Namespace filter"
              />
            </div>

            <div class="field-group">
              <label for="audit-filter-outcome">Outcome</label>
              <select
                id="audit-filter-outcome"
                class="admin-select"
                [(ngModel)]="filters.outcome"
              >
                <option value="">All Outcomes</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="DENIED">DENIED</option>
                <option value="FAILURE">FAILURE</option>
              </select>
            </div>
          </div>

          <div class="toolbar-actions">
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="loadAuditEvents()"
              id="audit-apply-filters-btn"
            >
              <mat-icon>filter_list</mat-icon>
              Apply Filters
            </button>
            <button
              mat-stroked-button
              type="button"
              (click)="resetFilters()"
              id="audit-reset-filters-btn"
            >
              Reset
            </button>
            <button
              mat-stroked-button
              type="button"
              (click)="verifyHashChain()"
              id="audit-verify-chain-btn"
            >
              <mat-icon>verified_user</mat-icon>
              Verify SHA-256 Chain
            </button>
            <button
              mat-stroked-button
              type="button"
              (click)="exportAuditCsv()"
              [disabled]="events().length === 0"
              id="audit-export-csv-btn"
            >
              <mat-icon>download</mat-icon>
              Export CSV
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Loading / Error / Empty / Table States -->
      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="audit-loading">
          <mat-spinner diameter="28" />
          <span>Loading audit trail...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="audit-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="loadAuditEvents()">Retry</button>
        </div>
      } @else if (events().length === 0) {
        <div class="state-box empty-box" role="status" id="audit-empty">
          <mat-icon>fact_check</mat-icon>
          <span>No audit events match the active filters.</span>
        </div>
      } @else {
        <mat-card appearance="outlined">
          <mat-card-content>
            <div class="admin-table-wrap">
              <table class="admin-table" aria-label="Administrative audit log events" id="audit-events-table">
                <thead>
                  <tr>
                    <th>Audit ID</th>
                    <th>Timestamp</th>
                    <th>Tenant</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Target</th>
                    <th>Outcome</th>
                    <th>Entry SHA-256</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ev of events(); track ev.id || ev.auditId || $index) {
                    <tr>
                      <td class="cortex-mono">{{ ev.id || ev.auditId }}</td>
                      <td class="cortex-mono">{{ formatTimestamp(ev.createdAt || ev.timestamp) }}</td>
                      <td class="cortex-mono">{{ ev.tenantId || 'default' }}</td>
                      <td>
                        <span class="primary-cell">{{ ev.actorAccountId || ev.actorId || 'system' }}</span>
                        @if (ev.actorRole) {
                          <span class="sub-cell cortex-mono">{{ ev.actorRole }}</span>
                        }
                      </td>
                      <td><span class="pill-badge cortex-mono">{{ ev.action }}</span></td>
                      <td>
                        <span class="primary-cell">{{ ev.targetType || '—' }}</span>
                        <span class="sub-cell cortex-mono">{{ ev.targetId || ev.namespaceId || '' }}</span>
                      </td>
                      <td>
                        <span
                          class="outcome-pill"
                          [class.outcome-success]="(ev.outcome || 'SUCCESS') === 'SUCCESS'"
                          [class.outcome-denied]="ev.outcome === 'DENIED' || ev.outcome === 'FAILURE'"
                        >
                          {{ ev.outcome || 'SUCCESS' }}
                        </span>
                      </td>
                      <td class="cortex-mono hash-cell" [title]="ev.entryHash || ev.hash || ''">
                        {{ formatHashPreview(ev.entryHash || ev.hash) }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </mat-card-content>
        </mat-card>
      }
    </cortex-admin-section-shell>
  `,
  styles: [`
    .chain-banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 10px;
      font-size: 0.85rem;
      .chain-text {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
    }
    .chain-valid {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .chain-invalid {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .filter-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: 14px;
    }
    .field-group {
      display: flex;
      flex-direction: column;
      gap: 4px;
      label {
        font-size: 0.72rem;
        font-weight: 600;
        text-transform: uppercase;
        color: var(--mat-sys-on-surface-variant);
      }
    }
    .admin-input, .admin-select {
      height: 34px;
      padding: 0 10px;
      border-radius: 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.82rem;
    }
    .toolbar-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
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
    .admin-table-wrap {
      overflow-x: auto;
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
    .pill-badge, .outcome-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
    }
    .outcome-pill.outcome-success {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .outcome-pill.outcome-denied {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .hash-cell {
      font-size: 0.74rem;
      color: var(--mat-sys-on-surface-variant);
    }
  `],
})
export class AdminAuditComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly events = signal<any[]>([]);
  readonly chainVerification = signal<any | null>(null);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly lastExportedCsv = signal<string>('');

  filters: {
    tenantId: string;
    actorId: string;
    action: string;
    targetType: string;
    namespaceId: string;
    outcome: '' | 'SUCCESS' | 'DENIED' | 'FAILURE';
    limit: number;
  } = {
    tenantId: '',
    actorId: '',
    action: '',
    targetType: '',
    namespaceId: '',
    outcome: '',
    limit: 100,
  };

  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadAuditEvents();
  }

  loadAuditEvents(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .queryAdminAuditEvents({
        tenantId: this.filters.tenantId.trim() || undefined,
        actorId: this.filters.actorId.trim() || undefined,
        action: this.filters.action.trim() || undefined,
        targetType: this.filters.targetType.trim() || undefined,
        namespaceId: this.filters.namespaceId.trim() || undefined,
        outcome: this.filters.outcome || undefined,
        limit: this.filters.limit,
      })
      .subscribe({
        next: (list) => {
          this.events.set(Array.isArray(list) ? list : []);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.message || err?.message || 'Failed to load audit events.');
          this.loading.set(false);
        },
      });
  }

  resetFilters(): void {
    this.filters = {
      tenantId: '',
      actorId: '',
      action: '',
      targetType: '',
      namespaceId: '',
      outcome: '',
      limit: 100,
    };
    this.loadAuditEvents();
  }

  verifyHashChain(): void {
    const tid = this.filters.tenantId.trim() || undefined;
    this.api.verifyAdminAuditChain(tid).subscribe({
      next: (res) => {
        this.chainVerification.set(res);
        this.snack.open(
          res?.valid !== false
            ? 'Cryptographic SHA-256 audit chain verified'
            : 'Audit chain verification failed!',
          'OK',
          { duration: 3500 },
        );
      },
      error: (err) => {
        this.snack.open(err?.error?.message || 'Failed to verify audit chain', 'Dismiss', {
          duration: 4000,
        });
      },
    });
  }

  buildAuditCsv(): string {
    const lines = [
      'audit_id,timestamp,tenant_id,actor_id,action,target_type,target_id,namespace_id,outcome,entry_hash',
    ];
    for (const ev of this.events()) {
      const id = ev.id || ev.auditId || '';
      const ts = ev.createdAt || ev.timestamp || '';
      const tid = ev.tenantId || '';
      const actor = ev.actorAccountId || ev.actorId || '';
      const action = ev.action || '';
      const tType = ev.targetType || '';
      const tId = ev.targetId || '';
      const nsId = ev.namespaceId || '';
      const outcome = ev.outcome || 'SUCCESS';
      const hash = ev.entryHash || ev.hash || '';
      lines.push(`${id},${ts},${tid},${actor},${action},${tType},${tId},${nsId},${outcome},${hash}`);
    }
    return lines.join('\n');
  }

  exportAuditCsv(): void {
    const csv = this.buildAuditCsv();
    this.lastExportedCsv.set(csv);

    try {
      if (typeof Blob !== 'undefined' && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `spector-admin-audit-${Date.now()}.csv`);
        link.click();
        URL.revokeObjectURL(url);
      }
    } catch {
      // Safe fallback in headless test environments
    }

    this.snack.open('Exported audit log CSV', 'OK', { duration: 3000 });
  }

  formatHashPreview(hash?: string | null): string {
    if (!hash) return '—';
    return hash.length > 14 ? `${hash.slice(0, 10)}…${hash.slice(-4)}` : hash;
  }
}
