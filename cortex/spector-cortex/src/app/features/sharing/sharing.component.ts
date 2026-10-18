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
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, of } from 'rxjs';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { CortexStateService } from '@core/services/cortex-state.service';
import { AuthService } from '@core/services/auth.service';
import {
  ShareNamespaceDialogComponent,
  GrantItem,
} from '../admin/components/share-namespace-dialog/share-namespace-dialog.component';

/**
 * User Workspace Namespace Sharing, Access & Audit Transparency Page (`/sharing`, Issue #1067).
 *
 * <p>Allows namespace owners and `ADMIN` grantees to:
 * <ul>
 *   <li>Inspect "Who can access this namespace" (owner + active grants across roles `OWNER`, `ADMIN`, `WRITER`, `READER`).</li>
 *   <li>Grant or revoke access for human and agent principals inline or via {@link ShareNamespaceDialogComponent}.</li>
 *   <li>Inspect append-only audit events targeting the current namespace, including Break-Glass access history.</li>
 * </ul>
 * </p>
 */
@Component({
  selector: 'cortex-sharing',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sharing-page">
      <header class="sharing-header">
        <div class="title-group">
          <mat-icon class="title-icon">share</mat-icon>
          <div>
            <h1 class="page-title">
              Namespace Sharing &amp; Access:
              <code class="ns-badge">{{ state.currentNamespace() }}</code>
            </h1>
            <p class="page-subtitle">
              Manage who can access this namespace (OWNER / ADMIN / WRITER / READER), agent principals, and audit &amp; break-glass transparency logs.
            </p>
          </div>
        </div>
        <div class="header-actions">
          <button
            mat-stroked-button
            type="button"
            (click)="reloadAll()"
            id="sharing-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
          <button
            mat-flat-button
            color="primary"
            type="button"
            (click)="openShareDialog()"
            id="sharing-open-dialog-btn"
          >
            <mat-icon>person_add</mat-icon>
            Share Namespace Dialog
          </button>
        </div>
      </header>

      <!-- Break-Glass Transparency Alert Banner (shown when break-glass audit events exist) -->
      @if (breakGlassEvents().length > 0) {
        <div
          class="break-glass-history-banner"
          role="alert"
          id="sharing-break-glass-history-banner"
        >
          <mat-icon>emergency</mat-icon>
          <div class="banner-text">
            <strong>
              Break-Glass Emergency Access Recorded ({{ breakGlassEvents().length }} event(s))
            </strong>
            <span>
              Emergency read-only administrative break-glass activity has been logged on namespace
              <code class="cortex-mono">{{ state.currentNamespace() }}</code>. Review the audit trail below for actor, ticket, and timestamp details.
            </span>
          </div>
          <button
            mat-stroked-button
            type="button"
            (click)="showBreakGlassOnly.set(!showBreakGlassOnly())"
            id="sharing-toggle-bg-filter-btn"
          >
            {{ showBreakGlassOnly() ? 'Show All Audit Events' : 'Filter Break-Glass Events' }}
          </button>
        </div>
      }

      <!-- Namespace Summary Metadata Strip -->
      <div class="meta-strip" id="sharing-namespace-summary">
        <div class="meta-chip">
          <span class="meta-label">Namespace</span>
          <strong class="cortex-mono">{{ namespaceDetails()?.slug || state.currentNamespace() }}</strong>
        </div>
        <div class="meta-chip">
          <span class="meta-label">Owner Account</span>
          <strong class="cortex-mono">{{ ownerAccountId() }}</strong>
        </div>
        <div class="meta-chip">
          <span class="meta-label">Type</span>
          <strong class="cortex-mono">{{ namespaceDetails()?.type || 'PERSONAL' }}</strong>
        </div>
        <div class="meta-chip">
          <span class="meta-label">Active Grants</span>
          <strong class="cortex-mono">{{ grants().length }}</strong>
        </div>
        <div class="meta-chip">
          <span class="meta-label">Legal Hold</span>
          <strong class="cortex-mono">{{ namespaceDetails()?.legalHold ? 'ACTIVE' : 'None' }}</strong>
        </div>
      </div>

      <!-- Grant Access Form (Human & Agent Principals) -->
      <mat-card appearance="outlined" id="sharing-grant-form-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>group_add</mat-icon>
          <mat-card-title>Grant Namespace Access (Human or Agent Principal)</mat-card-title>
          <mat-card-subtitle>
            Assign READER (recall only), WRITER (recall + remember/reinforce/forget), or ADMIN (manage grants) permissions
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="grant-form-grid">
            <div class="field-group">
              <label for="sharing-grantee-input">Principal Account ID / Username *</label>
              <input
                id="sharing-grantee-input"
                type="text"
                class="sharing-input"
                [(ngModel)]="newGranteeId"
                placeholder="e.g. agent-researcher or 0HD7XJ9A2B001"
              />
            </div>

            <div class="field-group">
              <label for="sharing-principal-kind">Principal Type</label>
              <select
                id="sharing-principal-kind"
                class="sharing-select"
                [(ngModel)]="newPrincipalKind"
              >
                <option value="HUMAN">Human Collaborator</option>
                <option value="AGENT">Agent Principal</option>
              </select>
            </div>

            <div class="field-group">
              <label for="sharing-role-select">Grant Role</label>
              <select
                id="sharing-role-select"
                class="sharing-select"
                [(ngModel)]="newRole"
              >
                <option value="READER">READER (Recall only)</option>
                <option value="WRITER">WRITER (Remember, Recall, Reinforce)</option>
                <option value="ADMIN">ADMIN (Full + Manage Grants)</option>
              </select>
            </div>

            <div class="field-group">
              <label for="sharing-expiry-select">Expiration</label>
              <select
                id="sharing-expiry-select"
                class="sharing-select"
                [(ngModel)]="expiryOption"
              >
                <option value="never">Never expires</option>
                <option value="1h">1 Hour</option>
                <option value="1d">1 Day</option>
                <option value="7d">7 Days</option>
                <option value="30d">30 Days</option>
              </select>
            </div>

            <div class="field-actions">
              <button
                mat-flat-button
                color="primary"
                type="button"
                (click)="createGrant()"
                [disabled]="!newGranteeId.trim() || submitting()"
                id="sharing-submit-grant-btn"
              >
                <mat-icon>check</mat-icon>
                Grant Access
              </button>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Who Can Access This Namespace Table -->
      <mat-card appearance="outlined" id="sharing-access-section">
        <mat-card-header>
          <mat-icon mat-card-avatar>verified_user</mat-icon>
          <mat-card-title>
            Who Can Access This Namespace ({{ 1 + grants().length }})
          </mat-card-title>
          <mat-card-subtitle>
            Complete access control list including namespace owner, human collaborators, and agent principals
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="table-wrap">
            <table class="sharing-table" aria-label="Who can access this namespace" id="sharing-grants-table">
              <thead>
                <tr>
                  <th>Principal</th>
                  <th>Role</th>
                  <th>Granted By</th>
                  <th>Granted At</th>
                  <th>Expires</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <!-- Implicit Owner Row -->
                <tr class="owner-row">
                  <td class="cortex-mono">
                    <strong>{{ ownerAccountId() }}</strong>
                    <span class="sub-badge">Namespace Owner</span>
                  </td>
                  <td>
                    <span class="role-pill role-owner">OWNER</span>
                  </td>
                  <td class="cortex-mono">catalog</td>
                  <td class="cortex-mono">—</td>
                  <td class="cortex-mono">Never</td>
                  <td>
                    <span class="muted-label">Primary Owner</span>
                  </td>
                </tr>

                <!-- Explicit Grant Rows -->
                @for (grant of grants(); track grant.grantId) {
                  <tr>
                    <td class="cortex-mono">
                      <strong>{{ grant.granteeAccountId }}</strong>
                      @if (isLikelyAgentPrincipal(grant.granteeAccountId)) {
                        <span class="sub-badge agent-badge">AGENT</span>
                      }
                    </td>
                    <td>
                      <span
                        class="role-pill"
                        [class.role-owner]="grant.role === 'OWNER'"
                        [class.role-admin]="grant.role === 'ADMIN'"
                        [class.role-writer]="grant.role === 'WRITER'"
                        [class.role-reader]="grant.role === 'READER'"
                      >
                        {{ grant.role }}
                      </span>
                    </td>
                    <td class="cortex-mono">{{ grant.grantedBy || 'owner' }}</td>
                    <td class="cortex-mono">{{ formatTimestamp(grant.grantedAt) }}</td>
                    <td class="cortex-mono">{{ grant.expiresAt ? formatTimestamp(grant.expiresAt) : 'Never' }}</td>
                    <td>
                      @if (grant.role !== 'OWNER') {
                        <button
                          mat-stroked-button
                          color="warn"
                          type="button"
                          class="revoke-btn"
                          [disabled]="submitting()"
                          (click)="revokeGrant(grant.grantId)"
                        >
                          Revoke
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Namespace Audit & Break-Glass History -->
      <mat-card appearance="outlined" id="sharing-audit-section">
        <mat-card-header>
          <mat-icon mat-card-avatar>fact_check</mat-icon>
          <mat-card-title>
            Namespace Audit &amp; Break-Glass History ({{ filteredAuditEvents().length }})
          </mat-card-title>
          <mat-card-subtitle>
            Tamper-evident log of grants, quota updates, legal-hold changes, and break-glass sessions targeting this namespace
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="audit-filter-bar">
            <label class="checkbox-label">
              <input
                type="checkbox"
                [ngModel]="showBreakGlassOnly()"
                (ngModelChange)="showBreakGlassOnly.set($event)"
                id="sharing-break-glass-only-checkbox"
              />
              <span>Show Break-Glass events only ({{ breakGlassEvents().length }})</span>
            </label>
          </div>

          @if (filteredAuditEvents().length === 0) {
            <p class="muted-note" id="sharing-audit-empty">
              No audit events recorded for this namespace matching the current filter.
            </p>
          } @else {
            <div class="table-wrap">
              <table class="sharing-table" aria-label="Namespace audit history" id="sharing-audit-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Target</th>
                    <th>Outcome</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ev of filteredAuditEvents(); track ev.id || ev.auditId || $index) {
                    <tr [class.break-glass-row]="isBreakGlassEvent(ev)">
                      <td class="cortex-mono">{{ formatTimestamp(ev.createdAt || ev.timestamp) }}</td>
                      <td>
                        <span
                          class="role-pill"
                          [class.role-break-glass]="isBreakGlassEvent(ev)"
                        >
                          {{ ev.action }}
                        </span>
                      </td>
                      <td class="cortex-mono">{{ ev.actorAccountId || ev.actorId || 'system' }}</td>
                      <td class="cortex-mono">{{ ev.targetType }}:{{ ev.targetId || state.currentNamespace() }}</td>
                      <td class="cortex-mono">{{ ev.outcome || 'SUCCESS' }}</td>
                      <td class="cortex-mono details-cell">{{ formatAuditDetails(ev.details) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
    .sharing-page {
      padding: 28px 32px;
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .sharing-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }
    .title-group {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .title-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      color: var(--mat-sys-primary);
    }
    .page-title {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .ns-badge {
      font-size: 0.95rem;
      padding: 2px 10px;
      border-radius: 6px;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .page-subtitle {
      margin: 4px 0 0;
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .header-actions {
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .break-glass-history-banner {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 18px;
      border-radius: 10px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      border: 1px solid var(--mat-sys-error);
      flex-wrap: wrap;
      .banner-text {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 2px;
        font-size: 0.82rem;
      }
    }
    .meta-strip {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 12px;
    }
    .meta-chip {
      padding: 12px 14px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .meta-label {
      font-size: 0.7rem;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .grant-form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 12px;
      align-items: end;
      margin-top: 8px;
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
    .sharing-input, .sharing-select {
      height: 36px;
      padding: 0 10px;
      border-radius: 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.84rem;
    }
    .table-wrap {
      overflow-x: auto;
    }
    .sharing-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
      th, td {
        text-align: left;
        padding: 10px 12px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }
      th {
        font-size: 0.7rem;
        text-transform: uppercase;
        color: var(--mat-sys-on-surface-variant);
      }
    }
    .sub-badge {
      display: inline-block;
      margin-left: 8px;
      padding: 1px 6px;
      border-radius: 4px;
      font-size: 0.68rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
    }
    .agent-badge {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .role-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
    }
    .role-owner, .role-admin {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .role-writer {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .role-break-glass {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .break-glass-row {
      background: rgba(220, 38, 38, 0.05);
    }
    .revoke-btn {
      height: 28px;
      font-size: 0.74rem;
      padding: 0 10px;
    }
    .audit-filter-bar {
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .checkbox-label {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-size: 0.82rem;
      cursor: pointer;
    }
    .details-cell {
      font-size: 0.74rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .muted-note, .muted-label {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.8rem;
    }
  `],
})
export class SharingComponent implements OnInit {
  protected readonly state = inject(CortexStateService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly namespaceDetails = signal<any | null>(null);
  readonly grants = signal<GrantItem[]>([]);
  readonly auditEvents = signal<any[]>([]);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly showBreakGlassOnly = signal(false);

  newGranteeId = '';
  newPrincipalKind: 'HUMAN' | 'AGENT' = 'HUMAN';
  newRole: 'READER' | 'WRITER' | 'ADMIN' = 'READER';
  expiryOption: 'never' | '1h' | '1d' | '7d' | '30d' = 'never';

  private lastLoadedNamespace: string | null = null;

  readonly ownerAccountId = computed(() => {
    return (
      this.namespaceDetails()?.ownerAccountId ||
      this.state.currentNamespaceItem()?.ownerAccountId ||
      this.auth.currentUser()?.userId ||
      'default'
    );
  });

  readonly breakGlassEvents = computed(() =>
    this.auditEvents().filter((ev) => this.isBreakGlassEvent(ev)),
  );

  readonly filteredAuditEvents = computed(() => {
    const all = this.auditEvents();
    if (!this.showBreakGlassOnly()) {
      return all;
    }
    return all.filter((ev) => this.isBreakGlassEvent(ev));
  });

  constructor() {
    effect(() => {
      const ns = this.state.currentNamespace();
      if (this.lastLoadedNamespace !== null && this.lastLoadedNamespace !== ns) {
        this.lastLoadedNamespace = ns;
        this.reloadAll();
      }
    });
  }

  ngOnInit(): void {
    this.lastLoadedNamespace = this.state.currentNamespace();
    this.reloadAll();
  }

  reloadAll(): void {
    const slug = this.state.currentNamespace();
    this.loading.set(true);

    this.api
      .getNamespace(slug)
      .pipe(catchError(() => of(null)))
      .subscribe((ns) => {
        if (ns) {
          this.namespaceDetails.set(ns);
        }
      });

    this.api
      .listNamespaceGrants(slug)
      .pipe(catchError(() => of([])))
      .subscribe((list) => {
        const normalized = (Array.isArray(list) ? list : []).map((g: any) => ({
          ...g,
          granteeAccountId: g.granteeAccountId || g.principalId || '',
        }));
        this.grants.set(normalized);
        this.loading.set(false);
      });

    this.api
      .listNamespaceAuditEvents(slug, 50)
      .pipe(catchError(() => of([])))
      .subscribe((events) => {
        this.auditEvents.set(Array.isArray(events) ? events : []);
      });
  }

  createGrant(): void {
    const grantee = this.newGranteeId.trim();
    if (!grantee) return;

    this.submitting.set(true);
    let expiresAt: string | undefined;
    const now = Date.now();
    if (this.expiryOption === '1h') {
      expiresAt = new Date(now + 3600 * 1000).toISOString();
    } else if (this.expiryOption === '1d') {
      expiresAt = new Date(now + 86400 * 1000).toISOString();
    } else if (this.expiryOption === '7d') {
      expiresAt = new Date(now + 7 * 86400 * 1000).toISOString();
    } else if (this.expiryOption === '30d') {
      expiresAt = new Date(now + 30 * 86400 * 1000).toISOString();
    }

    const slug = this.state.currentNamespace();
    this.api
      .createNamespaceGrant(slug, {
        granteeAccountId: grantee,
        role: this.newRole,
        expiresAt,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.newGranteeId = '';
          this.snackBar.open(`Granted ${this.newRole} access to ${grantee}`, 'OK', {
            duration: 3000,
          });
          this.reloadAll();
        },
        error: (err) => {
          this.submitting.set(false);
          this.snackBar.open(
            err?.error?.message || err?.message || 'Failed to create grant',
            'Dismiss',
            { duration: 4000 },
          );
        },
      });
  }

  revokeGrant(grantId: string): void {
    const slug = this.state.currentNamespace();
    this.submitting.set(true);
    this.api.revokeNamespaceGrant(slug, grantId).subscribe({
      next: () => {
        this.submitting.set(false);
        this.snackBar.open('Grant revoked', 'OK', { duration: 2500 });
        this.reloadAll();
      },
      error: (err) => {
        this.submitting.set(false);
        this.snackBar.open(
          err?.error?.message || err?.message || 'Failed to revoke grant',
          'Dismiss',
          { duration: 4000 },
        );
      },
    });
  }

  openShareDialog(): void {
    const slug = this.state.currentNamespace();
    const ref = this.dialog.open(ShareNamespaceDialogComponent, {
      width: '680px',
      data: {
        slug,
        namespaceId: this.namespaceDetails()?.namespaceId,
      },
    });
    ref.afterClosed().subscribe(() => {
      this.reloadAll();
    });
  }

  isBreakGlassEvent(ev: any): boolean {
    const action = String(ev?.action || '').toUpperCase();
    const targetType = String(ev?.targetType || '').toUpperCase();
    return action.includes('BREAK_GLASS') || targetType.includes('BREAK_GLASS');
  }

  isLikelyAgentPrincipal(principalId: string): boolean {
    const lower = (principalId || '').toLowerCase();
    return lower.startsWith('agent-') || lower.includes('agent') || lower.startsWith('bot-');
  }

  formatTimestamp(iso?: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
  }

  formatAuditDetails(details: any): string {
    if (!details || typeof details !== 'object') return '—';
    const entries = Object.entries(details);
    if (entries.length === 0) return '—';
    return entries.map(([k, v]) => `${k}=${v}`).join(', ');
  }
}
