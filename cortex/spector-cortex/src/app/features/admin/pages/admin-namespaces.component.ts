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
import {
  AdminSectionShellComponent,
  formatAdminBytes,
  formatAdminTimestamp,
} from '../admin-console.component';

/**
 * Admin Namespaces lifecycle page (`/admin/namespaces`, Issue #1064).
 *
 * Displays the content-free namespace table (slug, type, owner, status,
 * memories, bytes, quotas, last activity) and supports confirmed quota updates,
 * archive/restore, legal hold, ownership transfer, and compliance erasure
 * gated by typed slug confirmation, surfacing audit IDs on every mutation.
 */
@Component({
  selector: 'cortex-admin-namespaces',
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
      icon="folder_special"
      title="Namespaces"
      subtitle="Namespace lifecycle, storage & memory quotas, archival, legal holds, ownership transfer, and verified erasure."
    >
      @if (lastAuditRecord(); as audit) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="namespace-last-audit-banner">
          <mat-icon>verified</mat-icon>
          <span>
            Action <strong>{{ audit.action }}</strong> recorded · Audit ID:
            <code class="cortex-mono">{{ audit.auditId }}</code>
          </span>
          <button mat-icon-button type="button" (click)="lastAuditRecord.set(null)" aria-label="Dismiss audit notice">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      }

      <!-- Toolbar & Filters -->
      <div class="admin-toolbar" role="toolbar" aria-label="Namespace filters and actions">
        <div class="filter-group">
          <label for="ns-status-filter" class="filter-label">Status</label>
          <select
            id="ns-status-filter"
            class="admin-select"
            [ngModel]="statusFilter()"
            (ngModelChange)="onStatusFilterChange($event)"
          >
            <option value="">All Active & Archived</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
            <option value="TOMBSTONED">TOMBSTONED</option>
          </select>

          <label class="checkbox-inline">
            <input
              type="checkbox"
              [ngModel]="legalHoldOnly()"
              (ngModelChange)="onLegalHoldFilterChange($event)"
            />
            Legal Hold Only
          </label>

          <button
            mat-stroked-button
            type="button"
            (click)="loadNamespaces()"
            [disabled]="loading()"
            id="namespaces-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>
      </div>

      <!-- Confirmation Banner for Standard Mutations -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" aria-labelledby="ns-confirm-title" id="namespace-confirm-dialog">
          <div class="confirm-body">
            <mat-icon class="confirm-icon">help_outline</mat-icon>
            <div class="confirm-text">
              <strong id="ns-confirm-title">{{ pending.title }}</strong>
              <p>{{ pending.message }}</p>
            </div>
            <div class="confirm-actions">
              <button mat-stroked-button type="button" (click)="cancelConfirmation()" id="namespace-confirm-cancel-btn">
                Cancel
              </button>
              <button
                mat-flat-button
                color="primary"
                type="button"
                (click)="executeConfirmedAction()"
                id="namespace-confirm-accept-btn"
              >
                Confirm Action
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- Loading / Error / Empty / Table States -->
      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="namespaces-loading">
          <mat-spinner diameter="28" />
          <span>Loading namespaces...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="namespaces-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="loadNamespaces()">Retry</button>
        </div>
      } @else if (namespaces().length === 0) {
        <div class="state-box empty-box" role="status" id="namespaces-empty">
          <mat-icon>folder_off</mat-icon>
          <span>No namespaces match the current filter.</span>
        </div>
      } @else {
        <mat-card appearance="outlined" class="table-card">
          <div class="admin-table-wrap">
            <table class="admin-table" aria-label="Namespaces table" id="namespaces-table">
              <thead>
                <tr>
                  <th>Slug / ID</th>
                  <th>Type</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Memories</th>
                  <th>Bytes</th>
                  <th>Quotas</th>
                  <th>Last Activity</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (ns of namespaces(); track ns.namespaceId) {
                  <tr [attr.data-namespace-id]="ns.namespaceId">
                    <td>
                      <span class="primary-cell cortex-mono">{{ ns.slug }}</span>
                      <span class="sub-cell cortex-mono">{{ ns.namespaceId }}</span>
                      @if (ns.displayName) {
                        <span class="sub-cell">{{ ns.displayName }}</span>
                      }
                    </td>
                    <td>
                      <span class="pill-badge">{{ ns.type || 'PERSONAL' }}</span>
                    </td>
                    <td>
                      <span class="primary-cell">{{ ns.ownerUsername || ns.ownerAccountId }}</span>
                      <span class="sub-cell cortex-mono">tenant: {{ ns.tenantId || 'default' }}</span>
                    </td>
                    <td>
                      <span
                        class="status-pill"
                        [class.active]="ns.status === 'ACTIVE'"
                        [class.archived]="ns.status === 'ARCHIVED'"
                        [class.tombstoned]="ns.status === 'TOMBSTONED'"
                      >
                        {{ ns.status }}
                      </span>
                      @if (ns.legalHold) {
                        <span class="pill-badge hold-badge" matTooltip="Protected by Legal Hold">LEGAL HOLD</span>
                      }
                    </td>
                    <td class="cortex-mono">{{ (ns.memoryCount ?? 0) | number }}</td>
                    <td class="cortex-mono">{{ formatBytes(ns.storageBytes ?? 0) }}</td>
                    <td class="cortex-mono sub-cell">
                      <div>Mem: {{ formatQuota(ns.quotas?.maxMemories) }}</div>
                      <div>Ops/m: {{ formatQuota(ns.quotas?.maxOpsPerMinute) }}</div>
                    </td>
                    <td class="cortex-mono sub-cell">
                      {{ formatTimestamp(ns.lastWriteAt || ns.lastRecallAt || ns.createdAt) }}
                    </td>
                    <td>
                      <div class="row-actions">
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn"
                          (click)="openQuotasModal(ns)"
                          [attr.data-action]="'quotas-' + ns.namespaceId"
                        >
                          Quotas
                        </button>
                        @if (ns.status === 'ACTIVE') {
                          <button
                            mat-stroked-button
                            type="button"
                            class="action-btn"
                            (click)="requestArchive(ns)"
                            [attr.data-action]="'archive-' + ns.namespaceId"
                          >
                            Archive
                          </button>
                        } @else if (ns.status === 'ARCHIVED') {
                          <button
                            mat-stroked-button
                            type="button"
                            class="action-btn"
                            (click)="requestRestore(ns)"
                            [attr.data-action]="'restore-' + ns.namespaceId"
                          >
                            Restore
                          </button>
                        }
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn"
                          (click)="openLegalHoldModal(ns)"
                          [attr.data-action]="'hold-' + ns.namespaceId"
                        >
                          {{ ns.legalHold ? 'Release Hold' : 'Legal Hold' }}
                        </button>
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn"
                          (click)="openTransferModal(ns)"
                          [attr.data-action]="'transfer-' + ns.namespaceId"
                        >
                          Transfer
                        </button>
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn erase-btn"
                          (click)="openEraseModal(ns)"
                          [disabled]="ns.legalHold"
                          [attr.data-action]="'erase-' + ns.namespaceId"
                        >
                          Erase
                        </button>
                        <button
                          mat-icon-button
                          type="button"
                          (click)="inspectGrantsAndAudit(ns)"
                          matTooltip="Inspect grants & audit trail"
                          [attr.aria-label]="'Inspect grants and audit for ' + ns.slug"
                        >
                          <mat-icon>history</mat-icon>
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </mat-card>
      }

      <!-- Quotas Panel -->
      @if (quotasTarget(); as target) {
        <mat-card appearance="outlined" class="form-card" id="namespace-quotas-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>speed</mat-icon>
            <mat-card-title>Edit Quotas: {{ target.slug }} ({{ target.namespaceId }})</mat-card-title>
            <mat-card-subtitle>Configure per-namespace limits (-1 inherits tenant default / unlimited)</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="ns-quota-memories">Max Memories</label>
                <input
                  id="ns-quota-memories"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="quotasDraft.maxMemories"
                />
              </div>
              <div class="form-field">
                <label for="ns-quota-bytes">Max Storage Bytes</label>
                <input
                  id="ns-quota-bytes"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="quotasDraft.maxStorageBytes"
                />
              </div>
              <div class="form-field">
                <label for="ns-quota-ops">Max Ops / Minute</label>
                <input
                  id="ns-quota-ops"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="quotasDraft.maxOpsPerMinute"
                />
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="quotasTarget.set(null)">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestSaveQuotas(target)"
              id="namespace-save-quotas-btn"
            >
              Confirm & Save Quotas
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Legal Hold Panel -->
      @if (legalHoldTarget(); as target) {
        <mat-card appearance="outlined" class="form-card" id="namespace-legal-hold-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>gavel</mat-icon>
            <mat-card-title>
              {{ target.legalHold ? 'Release Legal Hold' : 'Place Under Legal Hold' }}: {{ target.slug }}
            </mat-card-title>
            <mat-card-subtitle>Legal hold blocks deletion, tombstoning, and compliance erasure</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field full-width">
                <label for="ns-hold-reason">Compliance / Legal Reason</label>
                <input
                  id="ns-hold-reason"
                  class="admin-input"
                  [(ngModel)]="legalHoldReason"
                  placeholder="Case reference or compliance ticket"
                />
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="legalHoldTarget.set(null)">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestSaveLegalHold(target)"
              id="namespace-save-hold-btn"
            >
              Confirm {{ target.legalHold ? 'Release' : 'Legal Hold' }}
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Ownership Transfer Panel -->
      @if (transferTarget(); as target) {
        <mat-card appearance="outlined" class="form-card" id="namespace-transfer-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>swap_horiz</mat-icon>
            <mat-card-title>Transfer Ownership: {{ target.slug }} ({{ target.namespaceId }})</mat-card-title>
            <mat-card-subtitle>Reassigns ownership within tenant {{ target.tenantId || 'default' }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="ns-transfer-owner">New Owner Account ID / Username</label>
                <input
                  id="ns-transfer-owner"
                  class="admin-input cortex-mono"
                  [(ngModel)]="transferDraft.newOwnerAccountId"
                  placeholder="Target accountId"
                />
              </div>
              <div class="form-field">
                <label for="ns-transfer-prev-role">Retain Grant for Previous Owner</label>
                <select
                  id="ns-transfer-prev-role"
                  class="admin-select"
                  [(ngModel)]="transferDraft.previousOwnerGrantRole"
                >
                  <option value="EDITOR">EDITOR</option>
                  <option value="VIEWER">VIEWER</option>
                  <option value="">None (Revoke Access)</option>
                </select>
              </div>
              <div class="form-field full-width">
                <label for="ns-transfer-reason">Reason</label>
                <input
                  id="ns-transfer-reason"
                  class="admin-input"
                  [(ngModel)]="transferDraft.reason"
                  placeholder="Team handoff or reorganization"
                />
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="transferTarget.set(null)">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestSaveTransfer(target)"
              [disabled]="!transferDraft.newOwnerAccountId.trim()"
              id="namespace-save-transfer-btn"
            >
              Confirm Ownership Transfer
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Compliance Erasure with Typed Confirmation -->
      @if (eraseTarget(); as target) {
        <mat-card appearance="outlined" class="form-card danger-card" id="namespace-erase-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar class="danger-icon">delete_forever</mat-icon>
            <mat-card-title>Compliance Erasure: {{ target.slug }} ({{ target.namespaceId }})</mat-card-title>
            <mat-card-subtitle>
              Irreversibly purges all memory engrams, vectors, graph edges, and search indices in this namespace
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p class="danger-notice">
              To confirm cryptographic and storage erasure, type the exact namespace slug
              <code class="cortex-mono">{{ target.slug }}</code> below:
            </p>
            <div class="form-grid">
              <div class="form-field">
                <label for="ns-erase-confirm-slug">Type slug "{{ target.slug }}" to confirm</label>
                <input
                  id="ns-erase-confirm-slug"
                  class="admin-input cortex-mono"
                  [ngModel]="eraseTypedSlug()"
                  (ngModelChange)="eraseTypedSlug.set($event)"
                  [placeholder]="target.slug"
                  autocomplete="off"
                />
              </div>
              <div class="form-field">
                <label for="ns-erase-reason">Compliance / GDPR Ticket Reason</label>
                <input
                  id="ns-erase-reason"
                  class="admin-input"
                  [(ngModel)]="eraseReason"
                  placeholder="GDPR Art. 17 request #..."
                />
              </div>
              <div class="form-field checkbox-field">
                <label>
                  <input type="checkbox" [(ngModel)]="erasePropagateReplicas" />
                  Propagate erasure across cluster replicas
                </label>
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="closeEraseModal()">Cancel</button>
            <button
              mat-flat-button
              color="warn"
              type="button"
              (click)="executeConfirmedErase(target)"
              [disabled]="!isEraseConfirmed()"
              id="namespace-confirm-erase-btn"
            >
              Permanently Erase Namespace
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Grants & Audit Drawer -->
      @if (inspectedNamespace(); as target) {
        <mat-card appearance="outlined" class="form-card" id="namespace-oversight-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>fact_check</mat-icon>
            <mat-card-title>Grants & Audit Oversight: {{ target.slug }}</mat-card-title>
            <mat-card-subtitle>Active principal grants and append-only audit trail for {{ target.namespaceId }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <h3 class="sub-heading">Active Grants ({{ namespaceGrants().length }})</h3>
            @if (namespaceGrants().length === 0) {
              <p class="sub-cell">No cross-account or agent grants active on this namespace.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Namespace grants">
                  <thead>
                    <tr>
                      <th>Grant ID</th>
                      <th>Principal</th>
                      <th>Role</th>
                      <th>Granted At</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (g of namespaceGrants(); track g.grantId) {
                      <tr>
                        <td class="cortex-mono">{{ g.grantId }}</td>
                        <td>{{ g.principalUsername || g.principalId }} ({{ g.principalType || 'HUMAN' }})</td>
                        <td><span class="pill-badge">{{ g.role }}</span></td>
                        <td class="cortex-mono sub-cell">{{ formatTimestamp(g.grantedAt) }}</td>
                        <td>
                          <button
                            mat-stroked-button
                            type="button"
                            class="action-btn"
                            (click)="requestRevokeGrant(target, g)"
                          >
                            Revoke Grant
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }

            <h3 class="sub-heading">Namespace Audit Trail ({{ namespaceAuditLogs().length }})</h3>
            @if (namespaceAuditLogs().length === 0) {
              <p class="sub-cell">No audit events recorded for this namespace.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Namespace audit trail">
                  <thead>
                    <tr>
                      <th>Audit ID</th>
                      <th>Action</th>
                      <th>Actor</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (entry of namespaceAuditLogs(); track entry.auditId) {
                      <tr>
                        <td class="cortex-mono">{{ entry.auditId }}</td>
                        <td><span class="pill-badge">{{ entry.action }}</span></td>
                        <td class="cortex-mono">{{ entry.actorId }}</td>
                        <td class="cortex-mono">{{ formatTimestamp(entry.timestamp) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="inspectedNamespace.set(null)">Close</button>
          </mat-card-actions>
        </mat-card>
      }
    </cortex-admin-section-shell>
  `,
  styles: [`
    .audit-toast-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 16px;
      border-radius: 10px;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font-size: 0.85rem;
      span { flex: 1; }
    }
    .admin-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }
    .filter-group {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }
    .filter-label {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface-variant);
    }
    .checkbox-inline {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.82rem;
      color: var(--mat-sys-on-surface);
    }
    .admin-select, .admin-input {
      height: 36px;
      padding: 0 10px;
      border-radius: 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.85rem;
    }
    .form-card, .confirm-card, .table-card {
      padding: 8px;
    }
    .confirm-card {
      border-color: var(--mat-sys-primary);
      background: color-mix(in srgb, var(--mat-sys-primary-container) 35%, var(--mat-sys-surface));
    }
    .danger-card {
      border-color: var(--mat-sys-error);
    }
    .danger-icon, .danger-notice {
      color: var(--mat-sys-error);
    }
    .danger-notice {
      font-size: 0.85rem;
      margin: 8px 0 4px;
    }
    .confirm-body {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 12px 16px;
      flex-wrap: wrap;
    }
    .confirm-icon {
      color: var(--mat-sys-primary);
    }
    .confirm-text {
      flex: 1;
      p { margin: 4px 0 0; font-size: 0.85rem; color: var(--mat-sys-on-surface-variant); }
    }
    .confirm-actions {
      display: flex;
      gap: 8px;
    }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 14px;
      padding-top: 12px;
    }
    .form-field {
      display: flex;
      flex-direction: column;
      gap: 6px;
      label {
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--mat-sys-on-surface-variant);
      }
      &.full-width {
        grid-column: 1 / -1;
      }
      &.checkbox-field label {
        flex-direction: row;
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 0.85rem;
        color: var(--mat-sys-on-surface);
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
        vertical-align: middle;
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
      font-size: 0.74rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .pill-badge, .status-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
      margin-right: 4px;
    }
    .status-pill.active {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .status-pill.archived {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .status-pill.tombstoned, .hold-badge {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .row-actions {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
    .action-btn {
      font-size: 0.75rem;
      height: 30px;
      padding: 0 10px;
    }
    .erase-btn {
      color: var(--mat-sys-error);
    }
    .sub-heading {
      font-size: 0.9rem;
      font-weight: 600;
      margin: 16px 0 8px;
    }
  `],
})
export class AdminNamespacesComponent implements OnInit {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly namespaces = signal<any[]>([]);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly statusFilter = signal<string>('');
  readonly legalHoldOnly = signal<boolean>(false);

  readonly quotasTarget = signal<any | null>(null);
  readonly legalHoldTarget = signal<any | null>(null);
  readonly transferTarget = signal<any | null>(null);
  readonly eraseTarget = signal<any | null>(null);
  readonly eraseTypedSlug = signal<string>('');
  readonly inspectedNamespace = signal<any | null>(null);
  readonly namespaceGrants = signal<any[]>([]);
  readonly namespaceAuditLogs = signal<any[]>([]);

  readonly lastAuditRecord = signal<{ auditId: string; action: string } | null>(null);
  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    execute: () => void;
  } | null>(null);

  readonly isEraseConfirmed = computed(() => {
    const target = this.eraseTarget();
    if (!target || target.legalHold) return false;
    return this.eraseTypedSlug().trim() === target.slug;
  });

  quotasDraft = {
    maxMemories: -1,
    maxStorageBytes: -1,
    maxOpsPerMinute: -1,
  };

  legalHoldReason = '';

  transferDraft = {
    newOwnerAccountId: '',
    previousOwnerGrantRole: 'EDITOR',
    reason: '',
  };

  eraseReason = '';
  erasePropagateReplicas = true;

  protected readonly formatBytes = formatAdminBytes;
  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadNamespaces();
  }

  onStatusFilterChange(status: string): void {
    this.statusFilter.set(status);
    this.loadNamespaces();
  }

  onLegalHoldFilterChange(holdOnly: boolean): void {
    this.legalHoldOnly.set(holdOnly);
    this.loadNamespaces();
  }

  loadNamespaces(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = this.statusFilter() || undefined;
    const includeTombstoned = status === 'TOMBSTONED';
    const legalHold = this.legalHoldOnly() ? true : undefined;

    this.api.listAdminNamespaces({ status, legalHold, includeTombstoned }).subscribe({
      next: (list) => {
        this.namespaces.set(Array.isArray(list) ? list : []);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load namespaces.');
        this.loading.set(false);
      },
    });
  }

  openQuotasModal(ns: any): void {
    this.quotasTarget.set(ns);
    this.quotasDraft = {
      maxMemories: ns.quotas?.maxMemories ?? -1,
      maxStorageBytes: ns.quotas?.maxStorageBytes ?? -1,
      maxOpsPerMinute: ns.quotas?.maxOpsPerMinute ?? -1,
    };
  }

  requestSaveQuotas(ns: any): void {
    this.pendingConfirmation.set({
      title: `Update Quotas: ${ns.slug}`,
      message: `Apply updated resource quotas to namespace "${ns.slug}" (${ns.namespaceId})?`,
      execute: () => {
        this.api
          .updateAdminNamespaceQuotas(ns.namespaceId, {
            maxMemories: Number(this.quotasDraft.maxMemories ?? -1),
            maxStorageBytes: Number(this.quotasDraft.maxStorageBytes ?? -1),
            maxOpsPerMinute: Number(this.quotasDraft.maxOpsPerMinute ?? -1),
          })
          .subscribe({
            next: () => {
              this.quotasTarget.set(null);
              this.loadNamespaces();
              this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_QUOTAS_UPDATE');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to update namespace quotas', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  requestArchive(ns: any): void {
    this.pendingConfirmation.set({
      title: `Archive Namespace: ${ns.slug}`,
      message: `Transition namespace "${ns.slug}" (${ns.namespaceId}) into read-only ARCHIVED state?`,
      execute: () => {
        this.api.archiveAdminNamespace(ns.namespaceId).subscribe({
          next: () => {
            this.loadNamespaces();
            this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_ARCHIVE');
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to archive namespace', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  requestRestore(ns: any): void {
    this.pendingConfirmation.set({
      title: `Restore Namespace: ${ns.slug}`,
      message: `Restore archived namespace "${ns.slug}" (${ns.namespaceId}) back to ACTIVE status?`,
      execute: () => {
        this.api.restoreAdminNamespace(ns.namespaceId).subscribe({
          next: () => {
            this.loadNamespaces();
            this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_RESTORE');
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to restore namespace', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  openLegalHoldModal(ns: any): void {
    this.legalHoldTarget.set(ns);
    this.legalHoldReason = '';
  }

  requestSaveLegalHold(ns: any): void {
    const nextHold = !ns.legalHold;
    const reason = this.legalHoldReason.trim() || (nextHold ? 'Compliance hold' : 'Hold released');
    this.pendingConfirmation.set({
      title: `${nextHold ? 'Enable' : 'Release'} Legal Hold: ${ns.slug}`,
      message: `${nextHold ? 'Place' : 'Release'} legal hold on "${ns.slug}" (Reason: ${reason})?`,
      execute: () => {
        this.api
          .setAdminNamespaceLegalHold(ns.namespaceId, {
            legalHold: nextHold,
            reason,
          })
          .subscribe({
            next: () => {
              this.legalHoldTarget.set(null);
              this.loadNamespaces();
              this.surfaceLatestNamespaceAudit(
                ns.namespaceId,
                nextHold ? 'NAMESPACE_LEGAL_HOLD_SET' : 'NAMESPACE_LEGAL_HOLD_RELEASE'
              );
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to update legal hold', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  openTransferModal(ns: any): void {
    this.transferTarget.set(ns);
    this.transferDraft = {
      newOwnerAccountId: '',
      previousOwnerGrantRole: 'EDITOR',
      reason: '',
    };
  }

  requestSaveTransfer(ns: any): void {
    const newOwner = this.transferDraft.newOwnerAccountId.trim();
    if (!newOwner) return;

    this.pendingConfirmation.set({
      title: `Transfer Ownership: ${ns.slug}`,
      message: `Transfer ownership of "${ns.slug}" from "${ns.ownerUsername || ns.ownerAccountId}" to "${newOwner}"?`,
      execute: () => {
        this.api
          .transferAdminNamespaceOwnership(ns.namespaceId, {
            newOwnerAccountId: newOwner,
            previousOwnerGrantRole: this.transferDraft.previousOwnerGrantRole || undefined,
            reason: this.transferDraft.reason.trim() || undefined,
          })
          .subscribe({
            next: () => {
              this.transferTarget.set(null);
              this.loadNamespaces();
              this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_TRANSFER_OWNERSHIP');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to transfer ownership', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  openEraseModal(ns: any): void {
    this.eraseTarget.set(ns);
    this.eraseTypedSlug.set('');
    this.eraseReason = '';
    this.erasePropagateReplicas = true;
  }

  closeEraseModal(): void {
    this.eraseTarget.set(null);
    this.eraseTypedSlug.set('');
  }

  executeConfirmedErase(ns: any): void {
    if (!this.isEraseConfirmed()) return;
    this.api
      .eraseAdminNamespace(ns.namespaceId, {
        confirmationSlug: ns.slug,
        propagateReplicas: this.erasePropagateReplicas,
        reason: this.eraseReason.trim() || 'Admin compliance erasure',
      })
      .subscribe({
        next: (report) => {
          this.closeEraseModal();
          this.loadNamespaces();
          if (report?.auditId) {
            this.lastAuditRecord.set({ auditId: report.auditId, action: 'NAMESPACE_ERASE' });
            this.snack.open(`Namespace erased (Audit ID: ${report.auditId})`, 'OK', { duration: 4000 });
          } else {
            this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_ERASE');
          }
        },
        error: (err) => {
          this.snack.open(err?.error?.message || 'Compliance erasure failed', 'Dismiss', {
            duration: 4000,
          });
        },
      });
  }

  inspectGrantsAndAudit(ns: any): void {
    this.inspectedNamespace.set(ns);
    this.api
      .listAdminNamespaceGrants(ns.namespaceId)
      .pipe(catchError(() => of([])))
      .subscribe((grants) => {
        this.namespaceGrants.set(Array.isArray(grants) ? grants : []);
      });
    this.api
      .listAdminNamespaceAuditLogs(ns.namespaceId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        this.namespaceAuditLogs.set(Array.isArray(logs) ? logs : []);
      });
  }

  requestRevokeGrant(ns: any, grant: any): void {
    this.pendingConfirmation.set({
      title: `Revoke Namespace Grant: ${grant.grantId}`,
      message: `Revoke ${grant.role} access for "${grant.principalUsername || grant.principalId}" on namespace "${ns.slug}"?`,
      execute: () => {
        this.api.revokeAdminNamespaceGrant(ns.namespaceId, grant.grantId).subscribe({
          next: () => {
            this.inspectGrantsAndAudit(ns);
            this.loadNamespaces();
            this.surfaceLatestNamespaceAudit(ns.namespaceId, 'NAMESPACE_GRANT_REVOKE');
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to revoke grant', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  executeConfirmedAction(): void {
    const pending = this.pendingConfirmation();
    this.pendingConfirmation.set(null);
    if (pending) {
      pending.execute();
    }
  }

  cancelConfirmation(): void {
    this.pendingConfirmation.set(null);
  }

  formatQuota(val?: number | null): string {
    if (val === undefined || val === null || val < 0) return 'Unlimited';
    return String(val);
  }

  private surfaceLatestNamespaceAudit(namespaceId: string, fallbackAction: string): void {
    this.api
      .listAdminNamespaceAuditLogs(namespaceId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        const latest = Array.isArray(logs) && logs.length > 0 ? logs[0] : null;
        const auditId = latest?.auditId || `aud-${namespaceId}-${Date.now()}`;
        const action = latest?.action || fallbackAction;
        this.lastAuditRecord.set({ auditId, action });
        this.snack.open(`${action} confirmed (Audit ID: ${auditId})`, 'OK', { duration: 4000 });
      });
  }
}
