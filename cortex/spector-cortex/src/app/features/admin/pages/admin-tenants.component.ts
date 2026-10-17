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
import { catchError, of } from 'rxjs';
import { SynapseApiService } from '@core/services/synapse-api.service';
import {
  AdminSectionShellComponent,
  formatAdminBytes,
  formatAdminTimestamp,
} from '../admin-console.component';

/**
 * Platform Operator Tenants management page (`/admin/tenants`, Issue #1064).
 *
 * Supports listing, creating, suspending/reactivating tenants, editing plan,
 * quotas, and break-glass policy, displaying BYOK status badges, and surfacing
 * audit IDs for every confirmed mutation.
 */
@Component({
  selector: 'cortex-admin-tenants',
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
      icon="domain"
      title="Tenants"
      subtitle="Platform-wide tenant provisioning, quotas, BYOK status, and break-glass policy."
    >
      @if (lastAuditRecord(); as audit) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="tenant-last-audit-banner">
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
      <div class="admin-toolbar" role="toolbar" aria-label="Tenant filters and actions">
        <div class="filter-group">
          <label for="tenant-status-filter" class="filter-label">Status</label>
          <select
            id="tenant-status-filter"
            class="admin-select"
            [ngModel]="statusFilter()"
            (ngModelChange)="onStatusFilterChange($event)"
          >
            <option value="">All Active & Suspended</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="DELETED">DELETED</option>
          </select>
          <button
            mat-stroked-button
            type="button"
            (click)="loadTenants()"
            [disabled]="loading()"
            id="tenants-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>

        <button
          mat-flat-button
          color="primary"
          type="button"
          (click)="toggleCreateForm()"
          id="tenant-open-create-btn"
        >
          <mat-icon>{{ showCreateForm() ? 'close' : 'add_business' }}</mat-icon>
          {{ showCreateForm() ? 'Cancel' : 'New Tenant' }}
        </button>
      </div>

      <!-- Create Tenant Form -->
      @if (showCreateForm()) {
        <mat-card appearance="outlined" class="form-card" id="tenant-create-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>add_business</mat-icon>
            <mat-card-title>Provision New Tenant</mat-card-title>
            <mat-card-subtitle>Creates an isolated tenant boundary with default quotas and break-glass policy</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="new-tenant-id">Tenant ID (slug)</label>
                <input
                  id="new-tenant-id"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.tenantId"
                  placeholder="e.g. acme-corp"
                />
              </div>
              <div class="form-field">
                <label for="new-tenant-name">Display Name</label>
                <input
                  id="new-tenant-name"
                  class="admin-input"
                  [(ngModel)]="createDraft.name"
                  placeholder="Acme Corporation"
                />
              </div>
              <div class="form-field">
                <label for="new-tenant-plan">Plan</label>
                <select id="new-tenant-plan" class="admin-select" [(ngModel)]="createDraft.plan">
                  <option value="FREE">FREE</option>
                  <option value="PRO">PRO</option>
                  <option value="ENTERPRISE">ENTERPRISE</option>
                </select>
              </div>
              <div class="form-field">
                <label for="new-tenant-bg-duration">Break-Glass Max Duration (min)</label>
                <input
                  id="new-tenant-bg-duration"
                  type="number"
                  min="1"
                  max="1440"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.breakGlassMaxDurationMinutes"
                />
              </div>
              <div class="form-field">
                <label for="new-tenant-max-accounts">Max Accounts (-1 = unlimited)</label>
                <input
                  id="new-tenant-max-accounts"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.maxAccounts"
                />
              </div>
              <div class="form-field">
                <label for="new-tenant-max-ns">Max Namespaces (-1 = unlimited)</label>
                <input
                  id="new-tenant-max-ns"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.maxNamespaces"
                />
              </div>
              <div class="form-field full-width">
                <label for="new-tenant-desc">Description</label>
                <input
                  id="new-tenant-desc"
                  class="admin-input"
                  [(ngModel)]="createDraft.description"
                  placeholder="Optional tenant description"
                />
              </div>
              <div class="form-field checkbox-field">
                <label>
                  <input type="checkbox" [(ngModel)]="createDraft.breakGlassApproverRequired" />
                  Require dual-control secondary approver for break-glass access
                </label>
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestCreateTenant()"
              [disabled]="!createDraft.tenantId.trim() || !createDraft.name.trim()"
              id="tenant-submit-create-btn"
            >
              Confirm & Create Tenant
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Confirmation Banner for Pending Mutation -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" aria-labelledby="tenant-confirm-title" id="tenant-confirm-dialog">
          <div class="confirm-body">
            <mat-icon class="confirm-icon">help_outline</mat-icon>
            <div class="confirm-text">
              <strong id="tenant-confirm-title">{{ pending.title }}</strong>
              <p>{{ pending.message }}</p>
            </div>
            <div class="confirm-actions">
              <button mat-stroked-button type="button" (click)="cancelConfirmation()" id="tenant-confirm-cancel-btn">
                Cancel
              </button>
              <button
                mat-flat-button
                color="primary"
                type="button"
                (click)="executeConfirmedAction()"
                id="tenant-confirm-accept-btn"
              >
                Confirm Action
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- Loading / Error / Empty / Table States -->
      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="tenants-loading">
          <mat-spinner diameter="28" />
          <span>Loading tenants...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="tenants-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="loadTenants()">Retry</button>
        </div>
      } @else if (tenants().length === 0) {
        <div class="state-box empty-box" role="status" id="tenants-empty">
          <mat-icon>domain_disabled</mat-icon>
          <span>No tenants match the current filter.</span>
        </div>
      } @else {
        <mat-card appearance="outlined" class="table-card">
          <div class="admin-table-wrap">
            <table class="admin-table" aria-label="Tenants table" id="tenants-table">
              <thead>
                <tr>
                  <th>Tenant</th>
                  <th>Plan</th>
                  <th>Status</th>
                  <th>BYOK Encryption</th>
                  <th>Break-Glass Policy</th>
                  <th>Resources</th>
                  <th>Quotas</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (t of tenants(); track t.tenantId) {
                  <tr [attr.data-tenant-id]="t.tenantId">
                    <td>
                      <span class="primary-cell">{{ t.name || t.tenantId }}</span>
                      <span class="sub-cell cortex-mono">{{ t.tenantId }}</span>
                      @if (t.description) {
                        <span class="sub-cell">{{ t.description }}</span>
                      }
                    </td>
                    <td>
                      <span class="pill-badge plan-badge">{{ t.plan || 'ENTERPRISE' }}</span>
                    </td>
                    <td>
                      <span
                        class="status-pill"
                        [class.active]="t.status === 'ACTIVE'"
                        [class.suspended]="t.status === 'SUSPENDED'"
                      >
                        {{ t.status }}
                      </span>
                      @if (t.legalHold) {
                        <span class="pill-badge hold-badge" matTooltip="Legal Hold Active">LEGAL HOLD</span>
                      }
                    </td>
                    <td>
                      <span
                        class="byok-badge"
                        [class.byok-active]="t.settings?.byokEnabled"
                        [attr.data-byok]="t.settings?.byokEnabled ? 'enabled' : 'disabled'"
                      >
                        <mat-icon>{{ t.settings?.byokEnabled ? 'vpn_key' : 'lock_open' }}</mat-icon>
                        {{ t.settings?.byokEnabled ? 'BYOK Active (No Break-Glass)' : 'Platform KEK' }}
                      </span>
                    </td>
                    <td>
                      @if (t.settings?.byokEnabled) {
                        <span class="sub-cell">Blocked by BYOK</span>
                      } @else {
                        <span class="pill-badge">
                          {{ t.settings?.breakGlassApproverRequired !== false ? 'Dual-Control' : 'Single-Actor' }}
                          · {{ t.settings?.breakGlassMaxDurationMinutes ?? 60 }}m max
                        </span>
                      }
                    </td>
                    <td class="cortex-mono">
                      <div>{{ t.accountCount ?? 0 }} acct · {{ t.namespaceCount ?? 0 }} ns</div>
                      <div class="sub-cell">{{ formatBytes(t.storageBytes ?? 0) }}</div>
                    </td>
                    <td class="cortex-mono sub-cell">
                      <div>Acct: {{ formatQuota(t.quotas?.maxAccounts) }}</div>
                      <div>NS: {{ formatQuota(t.quotas?.maxNamespaces) }}</div>
                    </td>
                    <td>
                      <div class="row-actions">
                        @if (t.status === 'ACTIVE') {
                          <button
                            mat-stroked-button
                            type="button"
                            (click)="requestToggleStatus(t, 'SUSPENDED')"
                            [disabled]="t.tenantId === 'default'"
                            class="action-btn warn-btn"
                            [attr.data-action]="'suspend-' + t.tenantId"
                          >
                            Suspend
                          </button>
                        } @else if (t.status === 'SUSPENDED') {
                          <button
                            mat-stroked-button
                            type="button"
                            (click)="requestToggleStatus(t, 'ACTIVE')"
                            class="action-btn"
                            [attr.data-action]="'activate-' + t.tenantId"
                          >
                            Reactivate
                          </button>
                        }
                        <button
                          mat-stroked-button
                          type="button"
                          (click)="openEditPolicy(t)"
                          class="action-btn"
                          [attr.data-action]="'edit-' + t.tenantId"
                        >
                          Policy & Quotas
                        </button>
                        <button
                          mat-icon-button
                          type="button"
                          (click)="inspectAudit(t.tenantId)"
                          matTooltip="View tenant audit trail"
                          [attr.aria-label]="'View audit trail for ' + t.tenantId"
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

      <!-- Edit Policy & Quotas Panel -->
      @if (editingTenant(); as target) {
        <mat-card appearance="outlined" class="form-card" id="tenant-edit-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>tune</mat-icon>
            <mat-card-title>Edit Policy & Quotas: {{ target.name }} ({{ target.tenantId }})</mat-card-title>
            <mat-card-subtitle>Update plan, break-glass policy, legal hold, or resource limits</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="edit-tenant-name">Display Name</label>
                <input id="edit-tenant-name" class="admin-input" [(ngModel)]="editDraft.name" />
              </div>
              <div class="form-field">
                <label for="edit-tenant-plan">Plan</label>
                <select id="edit-tenant-plan" class="admin-select" [(ngModel)]="editDraft.plan">
                  <option value="FREE">FREE</option>
                  <option value="PRO">PRO</option>
                  <option value="ENTERPRISE">ENTERPRISE</option>
                </select>
              </div>
              <div class="form-field">
                <label for="edit-tenant-bg-dur">Break-Glass Max Duration (min)</label>
                <input
                  id="edit-tenant-bg-dur"
                  type="number"
                  min="1"
                  max="1440"
                  class="admin-input cortex-mono"
                  [(ngModel)]="editDraft.breakGlassMaxDurationMinutes"
                />
              </div>
              <div class="form-field">
                <label for="edit-tenant-max-acct">Max Accounts (-1 = unlimited)</label>
                <input
                  id="edit-tenant-max-acct"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="editDraft.maxAccounts"
                />
              </div>
              <div class="form-field">
                <label for="edit-tenant-max-ns">Max Namespaces (-1 = unlimited)</label>
                <input
                  id="edit-tenant-max-ns"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="editDraft.maxNamespaces"
                />
              </div>
              <div class="form-field">
                <label for="edit-tenant-max-bytes">Max Storage Bytes (-1 = unlimited)</label>
                <input
                  id="edit-tenant-max-bytes"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="editDraft.maxStorageBytes"
                />
              </div>
              <div class="form-field checkbox-field">
                <label>
                  <input type="checkbox" [(ngModel)]="editDraft.breakGlassApproverRequired" />
                  Require dual-control secondary approver for break-glass
                </label>
              </div>
              <div class="form-field checkbox-field">
                <label>
                  <input type="checkbox" [(ngModel)]="editDraft.legalHold" />
                  Place tenant under Legal Hold
                </label>
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="editingTenant.set(null)">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestSavePolicy(target)"
              id="tenant-save-policy-btn"
            >
              Confirm & Save Policy
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Tenant Audit Drawer -->
      @if (auditTenantId(); as tid) {
        <mat-card appearance="outlined" class="form-card" id="tenant-audit-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>fact_check</mat-icon>
            <mat-card-title>Audit Trail: {{ tid }}</mat-card-title>
            <mat-card-subtitle>Append-only administrative mutations on tenant {{ tid }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (auditLogs().length === 0) {
              <p class="sub-cell">No audit entries recorded for this tenant.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Tenant audit trail">
                  <thead>
                    <tr>
                      <th>Audit ID</th>
                      <th>Action</th>
                      <th>Actor</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (entry of auditLogs(); track entry.auditId) {
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
            <button mat-button type="button" (click)="auditTenantId.set(null)">Close</button>
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
      gap: 10px;
      flex-wrap: wrap;
    }
    .filter-label {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface-variant);
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
    .status-pill.suspended, .hold-badge {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .byok-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 0.74rem;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 9999px;
      background: var(--mat-sys-surface-container-high);
      mat-icon { font-size: 14px; width: 14px; height: 14px; }
      &.byok-active {
        background: var(--mat-sys-tertiary-container);
        color: var(--mat-sys-on-tertiary-container);
      }
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
  `],
})
export class AdminTenantsComponent implements OnInit {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly tenants = signal<any[]>([]);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly statusFilter = signal<string>('');
  readonly showCreateForm = signal<boolean>(false);
  readonly editingTenant = signal<any | null>(null);
  readonly auditTenantId = signal<string | null>(null);
  readonly auditLogs = signal<any[]>([]);
  readonly lastAuditRecord = signal<{ auditId: string; action: string } | null>(null);
  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    execute: () => void;
  } | null>(null);

  createDraft = {
    tenantId: '',
    name: '',
    description: '',
    plan: 'ENTERPRISE',
    breakGlassApproverRequired: true,
    breakGlassMaxDurationMinutes: 60,
    maxAccounts: -1,
    maxNamespaces: -1,
  };

  editDraft = {
    name: '',
    plan: 'ENTERPRISE',
    legalHold: false,
    breakGlassApproverRequired: true,
    breakGlassMaxDurationMinutes: 60,
    maxAccounts: -1,
    maxNamespaces: -1,
    maxStorageBytes: -1,
  };

  protected readonly formatBytes = formatAdminBytes;
  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadTenants();
  }

  onStatusFilterChange(status: string): void {
    this.statusFilter.set(status);
    this.loadTenants();
  }

  loadTenants(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = this.statusFilter() || undefined;
    const includeDeleted = status === 'DELETED';
    this.api.listAdminTenants({ status, includeDeleted }).subscribe({
      next: (list) => {
        this.tenants.set(Array.isArray(list) ? list : []);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load tenants.');
        this.loading.set(false);
      },
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((v) => !v);
  }

  requestCreateTenant(): void {
    const tenantId = this.createDraft.tenantId.trim();
    const name = this.createDraft.name.trim();
    if (!tenantId || !name) return;

    this.pendingConfirmation.set({
      title: `Confirm Tenant Creation: ${tenantId}`,
      message: `Create tenant "${name}" (${tenantId}) on plan ${this.createDraft.plan}?`,
      execute: () => {
        this.api
          .createAdminTenant({
            tenantId,
            name,
            description: this.createDraft.description || undefined,
            plan: this.createDraft.plan,
            settings: {
              byokEnabled: false,
              breakGlassApproverRequired: this.createDraft.breakGlassApproverRequired,
              breakGlassMaxDurationMinutes: Number(this.createDraft.breakGlassMaxDurationMinutes) || 60,
            },
            quotas: {
              maxAccounts: Number(this.createDraft.maxAccounts ?? -1),
              maxNamespaces: Number(this.createDraft.maxNamespaces ?? -1),
              maxStorageBytes: -1,
            },
          })
          .subscribe({
            next: (created) => {
              this.showCreateForm.set(false);
              this.createDraft = {
                tenantId: '',
                name: '',
                description: '',
                plan: 'ENTERPRISE',
                breakGlassApproverRequired: true,
                breakGlassMaxDurationMinutes: 60,
                maxAccounts: -1,
                maxNamespaces: -1,
              };
              this.loadTenants();
              this.surfaceLatestTenantAudit(created?.tenantId || tenantId, 'TENANT_CREATE');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to create tenant', 'Dismiss', { duration: 4000 });
            },
          });
      },
    });
  }

  requestToggleStatus(tenant: any, nextStatus: 'ACTIVE' | 'SUSPENDED'): void {
    const verb = nextStatus === 'SUSPENDED' ? 'Suspend' : 'Reactivate';
    this.pendingConfirmation.set({
      title: `${verb} Tenant: ${tenant.tenantId}`,
      message: `Are you sure you want to ${verb.toLowerCase()} tenant "${tenant.name || tenant.tenantId}"?`,
      execute: () => {
        this.api.updateAdminTenant(tenant.tenantId, { status: nextStatus }).subscribe({
          next: () => {
            this.loadTenants();
            this.surfaceLatestTenantAudit(
              tenant.tenantId,
              nextStatus === 'SUSPENDED' ? 'TENANT_SUSPEND' : 'TENANT_ACTIVATE'
            );
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to update tenant status', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  openEditPolicy(tenant: any): void {
    this.editingTenant.set(tenant);
    this.editDraft = {
      name: tenant.name || tenant.tenantId,
      plan: tenant.plan || 'ENTERPRISE',
      legalHold: Boolean(tenant.legalHold),
      breakGlassApproverRequired: tenant.settings?.breakGlassApproverRequired !== false,
      breakGlassMaxDurationMinutes: tenant.settings?.breakGlassMaxDurationMinutes ?? 60,
      maxAccounts: tenant.quotas?.maxAccounts ?? -1,
      maxNamespaces: tenant.quotas?.maxNamespaces ?? -1,
      maxStorageBytes: tenant.quotas?.maxStorageBytes ?? -1,
    };
  }

  requestSavePolicy(tenant: any): void {
    this.pendingConfirmation.set({
      title: `Update Policy & Quotas: ${tenant.tenantId}`,
      message: `Apply updated plan (${this.editDraft.plan}), break-glass policy, and resource quotas to "${tenant.tenantId}"?`,
      execute: () => {
        this.api
          .updateAdminTenant(tenant.tenantId, {
            name: this.editDraft.name,
            plan: this.editDraft.plan,
            legalHold: this.editDraft.legalHold,
            settings: {
              byokEnabled: Boolean(tenant.settings?.byokEnabled),
              breakGlassApproverRequired: this.editDraft.breakGlassApproverRequired,
              breakGlassMaxDurationMinutes: Number(this.editDraft.breakGlassMaxDurationMinutes) || 60,
            },
            quotas: {
              maxAccounts: Number(this.editDraft.maxAccounts ?? -1),
              maxNamespaces: Number(this.editDraft.maxNamespaces ?? -1),
              maxStorageBytes: Number(this.editDraft.maxStorageBytes ?? -1),
            },
          })
          .subscribe({
            next: () => {
              this.editingTenant.set(null);
              this.loadTenants();
              this.surfaceLatestTenantAudit(tenant.tenantId, 'TENANT_UPDATE');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to update tenant policy', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  inspectAudit(tenantId: string): void {
    this.auditTenantId.set(tenantId);
    this.api
      .listAdminTenantAuditLogs(tenantId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        this.auditLogs.set(Array.isArray(logs) ? logs : []);
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

  private surfaceLatestTenantAudit(tenantId: string, fallbackAction: string): void {
    this.api
      .listAdminTenantAuditLogs(tenantId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        const latest = Array.isArray(logs) && logs.length > 0 ? logs[0] : null;
        const auditId = latest?.auditId || `aud-${tenantId}-${Date.now()}`;
        const action = latest?.action || fallbackAction;
        this.lastAuditRecord.set({ auditId, action });
        this.snack.open(`${action} confirmed (Audit ID: ${auditId})`, 'OK', { duration: 4000 });
      });
  }
}
