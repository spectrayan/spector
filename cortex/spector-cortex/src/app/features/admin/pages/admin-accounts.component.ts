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
 * Admin Accounts & Agent Principals page (`/admin/accounts`, Issue #1064).
 *
 * Displays human accounts, autonomous agent principals, profiles, roles,
 * per-account resource aggregates, suspend/reactivate, credential resets,
 * and metadata-only API-key oversight without ever exposing secrets or memory content.
 */
@Component({
  selector: 'cortex-admin-accounts',
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
      icon="group"
      title="Accounts & Agent Principals"
      subtitle="User accounts, autonomous agent principals, per-account aggregates, and metadata-only credential oversight."
    >
      @if (lastAuditRecord(); as audit) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="account-last-audit-banner">
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
      <div class="admin-toolbar" role="toolbar" aria-label="Account filters and actions">
        <div class="filter-group">
          <label for="account-kind-filter" class="filter-label">Principal Kind</label>
          <select
            id="account-kind-filter"
            class="admin-select"
            [ngModel]="kindFilter()"
            (ngModelChange)="onFilterChange($event, activeFilter())"
          >
            <option value="">All Principals</option>
            <option value="HUMAN">HUMAN</option>
            <option value="AGENT">AGENT</option>
            <option value="SERVICE">SERVICE</option>
          </select>

          <label for="account-active-filter" class="filter-label">Status</label>
          <select
            id="account-active-filter"
            class="admin-select"
            [ngModel]="activeFilter()"
            (ngModelChange)="onFilterChange(kindFilter(), $event)"
          >
            <option value="">All Statuses</option>
            <option value="true">Active Only</option>
            <option value="false">Suspended Only</option>
          </select>

          <button
            mat-stroked-button
            type="button"
            (click)="loadAccounts()"
            [disabled]="loading()"
            id="accounts-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>

        <button
          mat-flat-button
          color="primary"
          type="button"
          (click)="showCreateForm.set(!showCreateForm())"
          id="account-open-create-btn"
        >
          <mat-icon>{{ showCreateForm() ? 'close' : 'person_add' }}</mat-icon>
          {{ showCreateForm() ? 'Cancel' : 'Provision Principal' }}
        </button>
      </div>

      <!-- Create Account / Agent Form -->
      @if (showCreateForm()) {
        <mat-card appearance="outlined" class="form-card" id="account-create-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>person_add</mat-icon>
            <mat-card-title>Provision Human Account or Agent Principal</mat-card-title>
            <mat-card-subtitle>Creates a user or autonomous agent principal with packaging profile and default namespace</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="new-acct-username">Username / Handle</label>
                <input
                  id="new-acct-username"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.username"
                  placeholder="e.g. research-agent-01"
                />
              </div>
              <div class="form-field">
                <label for="new-acct-display">Display Name</label>
                <input
                  id="new-acct-display"
                  class="admin-input"
                  [(ngModel)]="createDraft.displayName"
                  placeholder="Research Agent 01"
                />
              </div>
              <div class="form-field">
                <label for="new-acct-kind">Principal Kind</label>
                <select id="new-acct-kind" class="admin-select" [(ngModel)]="createDraft.kind">
                  <option value="HUMAN">HUMAN</option>
                  <option value="AGENT">AGENT</option>
                  <option value="SERVICE">SERVICE</option>
                </select>
              </div>
              <div class="form-field">
                <label for="new-acct-profile">Packaging Profile</label>
                <select id="new-acct-profile" class="admin-select" [(ngModel)]="createDraft.profile">
                  <option value="DEVELOPER">DEVELOPER</option>
                  <option value="TEAM">TEAM</option>
                  <option value="ENTERPRISE">ENTERPRISE</option>
                </select>
              </div>
              <div class="form-field">
                <label for="new-acct-role">Role</label>
                <select id="new-acct-role" class="admin-select" [(ngModel)]="createDraft.role">
                  <option value="editor">editor</option>
                  <option value="agent">agent</option>
                  <option value="viewer">viewer</option>
                  <option value="admin">admin</option>
                </select>
              </div>
              <div class="form-field">
                <label for="new-acct-owner">Owner Account ID (for AGENT)</label>
                <input
                  id="new-acct-owner"
                  class="admin-input cortex-mono"
                  [(ngModel)]="createDraft.ownerAccountId"
                  placeholder="Optional owner accountId"
                />
              </div>
              <div class="form-field">
                <label for="new-acct-password">Initial Password (for HUMAN)</label>
                <input
                  id="new-acct-password"
                  type="password"
                  class="admin-input"
                  [(ngModel)]="createDraft.password"
                  placeholder="Optional initial password"
                />
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestCreateAccount()"
              [disabled]="!createDraft.username.trim()"
              id="account-submit-create-btn"
            >
              Confirm & Provision
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- Confirmation Dialog Banner -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" aria-labelledby="account-confirm-title" id="account-confirm-dialog">
          <div class="confirm-body">
            <mat-icon class="confirm-icon">help_outline</mat-icon>
            <div class="confirm-text">
              <strong id="account-confirm-title">{{ pending.title }}</strong>
              <p>{{ pending.message }}</p>
            </div>
            <div class="confirm-actions">
              <button mat-stroked-button type="button" (click)="cancelConfirmation()" id="account-confirm-cancel-btn">
                Cancel
              </button>
              <button
                mat-flat-button
                color="primary"
                type="button"
                (click)="executeConfirmedAction()"
                id="account-confirm-accept-btn"
              >
                Confirm Action
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- Loading / Error / Empty / Table States -->
      @if (loading()) {
        <div class="state-box" role="status" aria-live="polite" id="accounts-loading">
          <mat-spinner diameter="28" />
          <span>Loading accounts and agent principals...</span>
        </div>
      } @else if (error(); as errMsg) {
        <div class="state-box error-box" role="alert" id="accounts-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errMsg }}</span>
          <button mat-stroked-button type="button" (click)="loadAccounts()">Retry</button>
        </div>
      } @else if (accounts().length === 0) {
        <div class="state-box empty-box" role="status" id="accounts-empty">
          <mat-icon>person_off</mat-icon>
          <span>No accounts or agent principals match the current filter.</span>
        </div>
      } @else {
        <mat-card appearance="outlined" class="table-card">
          <div class="admin-table-wrap">
            <table class="admin-table" aria-label="Accounts and agent principals table" id="accounts-table">
              <thead>
                <tr>
                  <th>Principal</th>
                  <th>Kind & Profile</th>
                  <th>Roles</th>
                  <th>Status</th>
                  <th>Aggregates</th>
                  <th>Owned Agents</th>
                  <th>Last Activity</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (acct of accounts(); track acct.accountId) {
                  <tr [attr.data-account-id]="acct.accountId">
                    <td>
                      <span class="primary-cell">{{ acct.displayName || acct.username }}</span>
                      <span class="sub-cell cortex-mono">&#64;{{ acct.username }} · {{ acct.accountId }}</span>
                      @if (acct.ownerUsername || acct.ownerAccountId) {
                        <span class="sub-cell">Owner: {{ acct.ownerUsername || acct.ownerAccountId }}</span>
                      }
                    </td>
                    <td>
                      <span class="pill-badge" [class.agent-badge]="acct.kind === 'AGENT'">
                        {{ acct.kind || 'HUMAN' }}
                      </span>
                      <span class="pill-badge">{{ acct.profile || 'DEVELOPER' }}</span>
                    </td>
                    <td>
                      @for (role of acct.roles || []; track role) {
                        <span class="pill-badge cortex-mono">{{ role }}</span>
                      }
                    </td>
                    <td>
                      <span
                        class="status-pill"
                        [class.active]="acct.active !== false"
                        [class.suspended]="acct.active === false"
                      >
                        {{ acct.active !== false ? 'ACTIVE' : 'SUSPENDED' }}
                      </span>
                      @if (acct.legalHold) {
                        <span class="pill-badge hold-badge">LEGAL HOLD</span>
                      }
                    </td>
                    <td class="cortex-mono">
                      <div>{{ acct.namespaceCount ?? 0 }} ns · {{ acct.apiKeyCount ?? 0 }} keys</div>
                      <div class="sub-cell">{{ formatBytes(acct.storageBytes ?? 0) }}</div>
                    </td>
                    <td class="cortex-mono">
                      {{ acct.ownedAgents?.length ?? 0 }}
                    </td>
                    <td class="cortex-mono sub-cell">
                      {{ formatTimestamp(acct.lastActivityAt || acct.createdAt) }}
                    </td>
                    <td>
                      <div class="row-actions">
                        <button
                          mat-stroked-button
                          type="button"
                          (click)="requestToggleActive(acct)"
                          class="action-btn"
                          [attr.data-action]="(acct.active !== false ? 'suspend-' : 'activate-') + acct.accountId"
                        >
                          {{ acct.active !== false ? 'Suspend' : 'Activate' }}
                        </button>
                        <button
                          mat-stroked-button
                          type="button"
                          (click)="openProfileEditor(acct)"
                          class="action-btn"
                          [attr.data-action]="'edit-' + acct.accountId"
                        >
                          Profile
                        </button>
                        <button
                          mat-stroked-button
                          type="button"
                          (click)="inspectAccountOversight(acct)"
                          class="action-btn"
                          [attr.data-action]="'oversight-' + acct.accountId"
                        >
                          Keys & Agents ({{ acct.apiKeyCount ?? 0 }})
                        </button>
                        <button
                          mat-stroked-button
                          type="button"
                          (click)="requestResetCredentials(acct)"
                          class="action-btn"
                          [attr.data-action]="'reset-' + acct.accountId"
                        >
                          Reset Auth
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

      <!-- Edit Account Profile & Quotas Panel -->
      @if (editingAccount(); as target) {
        <mat-card appearance="outlined" class="form-card" id="account-edit-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>manage_accounts</mat-icon>
            <mat-card-title>Edit Principal: {{ target.username }} ({{ target.accountId }})</mat-card-title>
            <mat-card-subtitle>Update packaging profile, display name, legal hold, or namespace quotas</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="form-field">
                <label for="edit-acct-display">Display Name</label>
                <input id="edit-acct-display" class="admin-input" [(ngModel)]="editDraft.displayName" />
              </div>
              <div class="form-field">
                <label for="edit-acct-profile">Packaging Profile</label>
                <select id="edit-acct-profile" class="admin-select" [(ngModel)]="editDraft.profile">
                  <option value="DEVELOPER">DEVELOPER</option>
                  <option value="TEAM">TEAM</option>
                  <option value="ENTERPRISE">ENTERPRISE</option>
                </select>
              </div>
              <div class="form-field">
                <label for="edit-acct-max-ns">Max Namespaces</label>
                <input
                  id="edit-acct-max-ns"
                  type="number"
                  class="admin-input cortex-mono"
                  [(ngModel)]="editDraft.maxNamespaces"
                />
              </div>
              <div class="form-field checkbox-field">
                <label>
                  <input type="checkbox" [(ngModel)]="editDraft.legalHold" />
                  Legal Hold
                </label>
              </div>
            </div>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-button type="button" (click)="editingAccount.set(null)">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="requestSaveAccountProfile(target)"
              id="account-save-profile-btn"
            >
              Confirm & Save Profile
            </button>
          </mat-card-actions>
        </mat-card>
      }

      <!-- API-Key Oversight, Owned Agents & Audit Drawer -->
      @if (oversightAccount(); as target) {
        <mat-card appearance="outlined" class="form-card" id="account-oversight-panel">
          <mat-card-header>
            <mat-icon mat-card-avatar>vpn_key</mat-icon>
            <mat-card-title>Credential & Agent Oversight: {{ target.username }}</mat-card-title>
            <mat-card-subtitle>
              Metadata-only API key inventory (secrets and hashes are never exposed), owned agents, and audit trail
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <h3 class="sub-heading">API Keys & Credentials ({{ accountCredentials().length }})</h3>
            @if (accountCredentials().length === 0) {
              <p class="sub-cell">No credentials registered for this principal.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Account credentials metadata table" id="account-credentials-table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Category</th>
                      <th>Masked Preview</th>
                      <th>Created</th>
                      <th>Last Used</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (cred of accountCredentials(); track cred.name) {
                      <tr>
                        <td class="cortex-mono">{{ cred.name }}</td>
                        <td><span class="pill-badge">{{ cred.category || 'AUTH' }}</span></td>
                        <td class="cortex-mono">{{ cred.maskedPreview || '••••••••' }}</td>
                        <td class="cortex-mono sub-cell">{{ formatTimestamp(cred.createdAt) }}</td>
                        <td class="cortex-mono sub-cell">{{ formatTimestamp(cred.lastUsedAt) }}</td>
                        <td>
                          <button
                            mat-stroked-button
                            type="button"
                            class="action-btn"
                            (click)="requestRevokeCredential(target, cred)"
                            [attr.data-action]="'revoke-cred-' + cred.name"
                          >
                            Revoke
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }

            <h3 class="sub-heading">Owned Agent Principals ({{ target.ownedAgents?.length ?? 0 }})</h3>
            @if ((target.ownedAgents?.length ?? 0) === 0) {
              <p class="sub-cell">No autonomous agent principals owned by this account.</p>
            } @else {
              <div class="agent-chips">
                @for (ag of target.ownedAgents; track ag.accountId) {
                  <span class="pill-badge agent-badge cortex-mono">
                    {{ ag.username }} ({{ ag.kind }} · {{ ag.active ? 'ACTIVE' : 'SUSPENDED' }})
                  </span>
                }
              </div>
            }

            <h3 class="sub-heading">Recent Account Audit Trail ({{ accountAuditLogs().length }})</h3>
            @if (accountAuditLogs().length === 0) {
              <p class="sub-cell">No audit events recorded for this account.</p>
            } @else {
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Account audit trail">
                  <thead>
                    <tr>
                      <th>Audit ID</th>
                      <th>Action</th>
                      <th>Actor</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (entry of accountAuditLogs(); track entry.auditId) {
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
            <button mat-button type="button" (click)="oversightAccount.set(null)">Close</button>
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
    .agent-badge {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .status-pill.active {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .status-pill.suspended, .hold-badge {
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
    .sub-heading {
      font-size: 0.9rem;
      font-weight: 600;
      margin: 16px 0 8px;
    }
    .agent-chips {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  `],
})
export class AdminAccountsComponent implements OnInit {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly accounts = signal<any[]>([]);
  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly kindFilter = signal<string>('');
  readonly activeFilter = signal<string>('');
  readonly showCreateForm = signal<boolean>(false);
  readonly editingAccount = signal<any | null>(null);
  readonly oversightAccount = signal<any | null>(null);
  readonly accountCredentials = signal<any[]>([]);
  readonly accountAuditLogs = signal<any[]>([]);
  readonly lastAuditRecord = signal<{ auditId: string; action: string } | null>(null);
  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    execute: () => void;
  } | null>(null);

  createDraft = {
    username: '',
    displayName: '',
    kind: 'HUMAN',
    profile: 'DEVELOPER',
    role: 'editor',
    ownerAccountId: '',
    password: '',
  };

  editDraft = {
    displayName: '',
    profile: 'DEVELOPER',
    legalHold: false,
    maxNamespaces: 5,
  };

  protected readonly formatBytes = formatAdminBytes;
  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadAccounts();
  }

  onFilterChange(kind: string, active: string): void {
    this.kindFilter.set(kind);
    this.activeFilter.set(active);
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: { kind?: string; active?: boolean } = {};
    if (this.kindFilter()) filters.kind = this.kindFilter();
    if (this.activeFilter() === 'true') filters.active = true;
    if (this.activeFilter() === 'false') filters.active = false;

    this.api.listAdminAccounts(filters).subscribe({
      next: (list) => {
        this.accounts.set(Array.isArray(list) ? list : []);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load accounts.');
        this.loading.set(false);
      },
    });
  }

  requestCreateAccount(): void {
    const username = this.createDraft.username.trim();
    if (!username) return;

    this.pendingConfirmation.set({
      title: `Confirm Principal Provisioning: ${username}`,
      message: `Create ${this.createDraft.kind} principal "${username}" with profile ${this.createDraft.profile}?`,
      execute: () => {
        this.api
          .createAdminAccount({
            username,
            displayName: this.createDraft.displayName.trim() || username,
            kind: this.createDraft.kind,
            profile: this.createDraft.profile,
            roles: [this.createDraft.role],
            ownerAccountId: this.createDraft.ownerAccountId.trim() || undefined,
            password: this.createDraft.password || undefined,
          })
          .subscribe({
            next: (created) => {
              this.showCreateForm.set(false);
              this.createDraft = {
                username: '',
                displayName: '',
                kind: 'HUMAN',
                profile: 'DEVELOPER',
                role: 'editor',
                ownerAccountId: '',
                password: '',
              };
              this.loadAccounts();
              this.surfaceLatestAccountAudit(created?.accountId || username, 'ACCOUNT_CREATE');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to provision principal', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  requestToggleActive(acct: any): void {
    const nextActive = acct.active === false;
    const verb = nextActive ? 'Activate' : 'Suspend';
    this.pendingConfirmation.set({
      title: `${verb} Principal: ${acct.username}`,
      message: `Are you sure you want to ${verb.toLowerCase()} account "${acct.username}" (${acct.accountId})?`,
      execute: () => {
        this.api.updateAdminAccount(acct.accountId, { active: nextActive }).subscribe({
          next: () => {
            this.loadAccounts();
            this.surfaceLatestAccountAudit(
              acct.accountId,
              nextActive ? 'ACCOUNT_ACTIVATE' : 'ACCOUNT_SUSPEND'
            );
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to update account status', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  openProfileEditor(acct: any): void {
    this.editingAccount.set(acct);
    this.editDraft = {
      displayName: acct.displayName || acct.username,
      profile: acct.profile || 'DEVELOPER',
      legalHold: Boolean(acct.legalHold),
      maxNamespaces: acct.quotas?.maxNamespaces ?? 5,
    };
  }

  requestSaveAccountProfile(acct: any): void {
    this.pendingConfirmation.set({
      title: `Update Account Profile: ${acct.username}`,
      message: `Save profile (${this.editDraft.profile}) and quota changes for "${acct.username}"?`,
      execute: () => {
        this.api
          .updateAdminAccount(acct.accountId, {
            displayName: this.editDraft.displayName,
            profile: this.editDraft.profile,
            legalHold: this.editDraft.legalHold,
            maxNamespaces: Number(this.editDraft.maxNamespaces),
          })
          .subscribe({
            next: () => {
              this.editingAccount.set(null);
              this.loadAccounts();
              this.surfaceLatestAccountAudit(acct.accountId, 'ACCOUNT_UPDATE');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to update account profile', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  requestResetCredentials(acct: any): void {
    this.pendingConfirmation.set({
      title: `Reset Credentials & Revoke Sessions: ${acct.username}`,
      message: `Revoke active refresh tokens and API keys for "${acct.username}" and require password rotation?`,
      execute: () => {
        this.api
          .resetAccountCredentials(acct.accountId, {
            mustChangePassword: true,
            revokeRefreshTokens: true,
            revokeApiKeys: true,
          })
          .subscribe({
            next: () => {
              this.loadAccounts();
              this.surfaceLatestAccountAudit(acct.accountId, 'ACCOUNT_RESET_CREDENTIALS');
            },
            error: (err) => {
              this.snack.open(err?.error?.message || 'Failed to reset credentials', 'Dismiss', {
                duration: 4000,
              });
            },
          });
      },
    });
  }

  inspectAccountOversight(acct: any): void {
    this.oversightAccount.set(acct);
    this.api
      .listAdminCredentials({ userId: acct.username, tenantId: acct.tenantId })
      .pipe(catchError(() => of([])))
      .subscribe((creds) => {
        this.accountCredentials.set(Array.isArray(creds) ? creds : []);
      });

    this.api
      .listAccountAuditLogs(acct.accountId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        this.accountAuditLogs.set(Array.isArray(logs) ? logs : []);
      });
  }

  requestRevokeCredential(acct: any, cred: any): void {
    this.pendingConfirmation.set({
      title: `Revoke Credential: ${cred.name}`,
      message: `Permanently revoke credential "${cred.name}" owned by "${acct.username}"?`,
      execute: () => {
        this.api.deleteAdminCredential(cred.name, acct.tenantId).subscribe({
          next: () => {
            this.inspectAccountOversight(acct);
            this.loadAccounts();
            this.surfaceLatestAccountAudit(acct.accountId, 'REVOKE_CREDENTIAL');
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to revoke credential', 'Dismiss', {
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

  private surfaceLatestAccountAudit(accountId: string, fallbackAction: string): void {
    this.api
      .listAccountAuditLogs(accountId)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => {
        const latest = Array.isArray(logs) && logs.length > 0 ? logs[0] : null;
        const auditId = latest?.auditId || `aud-${accountId}-${Date.now()}`;
        const action = latest?.action || fallbackAction;
        this.lastAuditRecord.set({ auditId, action });
        this.snack.open(`${action} confirmed (Audit ID: ${auditId})`, 'OK', { duration: 4000 });
      });
  }
}
