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
  formatAdminTimestamp,
} from '../admin-console.component';

export interface TenantKeyStatusView {
  tenantId: string;
  byokEnabled: boolean;
  provider: string;
  kekKeyId: string | null;
  credentialName: string | null;
  region: string | null;
  endpoint: string | null;
  keyVersion: string | null;
  status: string;
  healthStatus: string;
  activeNamespaceDekCount: number;
  lastVerifiedAt: string | null;
  lastError: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  supportedProviders: string[];
}

export interface DekRotationItemView {
  namespaceId: string;
  previousDekVersion: number;
  newDekVersion: number;
  partitionBundlesEncrypted: number;
  recordsReKeyed: number;
  walEventsEncrypted: number;
  runtimeBundleEncrypted: boolean;
}

/**
 * Masks a KMS KEK reference (ARN, GCP resource path, Azure Key Vault URI, or Vault key name)
 * so only the provider prefix and trailing identifier suffix are visible by default.
 */
export function maskKekReference(kekKeyId?: string | null): string {
  if (!kekKeyId || !kekKeyId.trim()) {
    return 'Platform Default (HKDF-SHA256)';
  }
  const trimmed = kekKeyId.trim();
  if (trimmed.length <= 12) {
    return `${trimmed.slice(0, 3)}••••${trimmed.slice(-3)}`;
  }
  return `${trimmed.slice(0, 8)}••••••••${trimmed.slice(-6)}`;
}

/**
 * Admin Keys & Encryption page (`/admin/keys`, Issue #1073, ADR-0088).
 *
 * Strictly content-free administrative surface for Customer-Managed Key (BYOK)
 * envelope encryption:
 * - Displays per-tenant BYOK status, KMS provider, masked KEK reference, last rotation,
 *   health status, active DEK count, and re-encryption progress.
 * - Supports KEK registration/update, live KMS wrap/unwrap validation, KEK rotation
 *   (envelope re-wrap without rewriting segment files), namespace DEK rotation
 *   (on-disk bundle/WAL/synaptic-tag re-encryption), emergency crypto-shredding,
 *   and key lifecycle audit event inspection.
 */
@Component({
  selector: 'cortex-admin-keys',
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
      icon="vpn_key"
      title="Keys & Encryption"
      subtitle="Customer-Managed Key (BYOK) KMS configuration, envelope KEK/DEK status, rotation, and key audit trail."
    >
      @if (lastActionNotice(); as notice) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="keys-action-banner">
          <mat-icon>verified</mat-icon>
          <span>{{ notice }}</span>
          <button mat-icon-button type="button" (click)="lastActionNotice.set(null)" aria-label="Dismiss notice">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      }

      <!-- Toolbar: Tenant Selector (super-admin) & Refresh -->
      <div class="admin-toolbar" role="toolbar" aria-label="Tenant selection and key refresh">
        <div class="filter-group">
          @if (auth.isSuperAdmin() && tenants().length > 0) {
            <label for="keys-tenant-select" class="filter-label">Tenant</label>
            <select
              id="keys-tenant-select"
              class="admin-select cortex-mono"
              [ngModel]="selectedTenantId()"
              (ngModelChange)="onTenantChange($event)"
            >
              @for (t of tenants(); track t.tenantId) {
                <option [value]="t.tenantId">{{ t.tenantId }} ({{ t.name || t.plan || 'TENANT' }})</option>
              }
            </select>
          } @else {
            <span class="tenant-pill cortex-mono" id="keys-active-tenant-pill">
              Tenant: {{ selectedTenantId() }}
            </span>
          }

          <button
            mat-stroked-button
            type="button"
            (click)="reloadAll()"
            [disabled]="loading()"
            id="keys-refresh-btn"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>

        <button
          mat-stroked-button
          type="button"
          (click)="runValidateKey()"
          [disabled]="loading() || !hasConfiguredKek()"
          id="keys-validate-btn"
        >
          <mat-icon>health_and_safety</mat-icon>
          Validate KMS Health
        </button>
      </div>

      <!-- Break-Glass Policy Unavailable Notice when BYOK is enabled -->
      @if (keyStatus()?.byokEnabled) {
        <div class="byok-break-glass-notice" role="status" id="keys-break-glass-unavailable-banner">
          <mat-icon>vpn_key_off</mat-icon>
          <div>
            <strong>Break-Glass Access: Unavailable (Tenant uses BYOK — SPE-820-004)</strong>
            <p>
              While Customer-Managed Key (BYOK) encryption is active for tenant
              <code class="cortex-mono">{{ selectedTenantId() }}</code>, emergency break-glass read-only
              access is cryptographically and policy-disabled.
            </p>
          </div>
        </div>
      }

      <!-- Confirmation Modal Card -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" id="keys-confirm-dialog">
          <div class="confirm-body">
            <mat-icon class="confirm-icon">gpp_maybe</mat-icon>
            <div class="confirm-text">
              <strong>{{ pending.title }}</strong>
              <p>{{ pending.message }}</p>
            </div>
            <div class="confirm-actions">
              <button mat-stroked-button type="button" (click)="cancelConfirmation()">Cancel</button>
              <button
                mat-flat-button
                color="warn"
                type="button"
                (click)="executeConfirmedAction()"
                id="keys-confirm-accept-btn"
              >
                {{ pending.confirmLabel }}
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- 1. Per-Tenant BYOK & Envelope Encryption Status Summary -->
      <mat-card appearance="outlined" id="keys-status-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>enhanced_encryption</mat-icon>
          <mat-card-title>Envelope Encryption & KEK Status</mat-card-title>
          <mat-card-subtitle>
            AES-256-GCM 64 KiB page-level authenticated encryption with HKDF-SHA256 subkey derivation (metadata only — zero key material exposed)
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="kpi-grid" id="keys-kpi-grid">
            <div class="kpi-tile">
              <span class="kpi-label">Encryption Mode</span>
              <span
                class="status-pill"
                [class.pill-byok]="keyStatus()?.byokEnabled"
                id="keys-byok-enabled-badge"
              >
                {{ keyStatus()?.byokEnabled ? 'BYOK Customer-Managed KEK' : 'Platform-Managed Envelope' }}
              </span>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">KMS Provider</span>
              <span class="kpi-value cortex-mono" id="keys-kms-provider">
                {{ keyStatus()?.provider || 'platform-hkdf' }}
              </span>
            </div>

            <div class="kpi-tile kpi-wide">
              <span class="kpi-label">KEK Reference (Masked)</span>
              <div class="kek-ref-row">
                <code class="cortex-mono" id="keys-masked-kek-ref">
                  {{ showUnmaskedKekRef() ? (keyStatus()?.kekKeyId || 'Platform Default (HKDF-SHA256)') : maskedKekId() }}
                </code>
                @if (keyStatus()?.kekKeyId) {
                  <button
                    mat-stroked-button
                    type="button"
                    class="tiny-btn"
                    (click)="toggleRevealKekRef()"
                    id="keys-toggle-kek-ref-btn"
                  >
                    {{ showUnmaskedKekRef() ? 'Mask' : 'Reveal ID' }}
                  </button>
                }
              </div>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">KEK Version</span>
              <span class="kpi-value cortex-mono" id="keys-kek-version">
                {{ keyStatus()?.keyVersion || 'v1' }}
              </span>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">Key Health & Status</span>
              <span
                class="status-pill cortex-mono"
                [class.pill-healthy]="keyStatus()?.healthStatus === 'HEALTHY'"
                [class.pill-revoked]="keyStatus()?.healthStatus === 'REVOKED' || keyStatus()?.status === 'KEY_REVOKED'"
                [class.pill-warn]="keyStatus()?.healthStatus === 'UNREACHABLE' || keyStatus()?.healthStatus === 'DISABLED'"
                id="keys-health-badge"
              >
                {{ keyStatus()?.healthStatus || 'HEALTHY' }} ({{ keyStatus()?.status || 'UNCONFIGURED' }})
              </span>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">Active Namespace DEKs</span>
              <span class="kpi-value cortex-mono" id="keys-active-dek-count">
                {{ keyStatus()?.activeNamespaceDekCount ?? 0 }}
              </span>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">Last Rotation / Updated</span>
              <span class="kpi-value cortex-mono" id="keys-last-rotation">
                {{ formatTimestamp(keyStatus()?.updatedAt) }}
              </span>
            </div>

            <div class="kpi-tile">
              <span class="kpi-label">Last KMS Verification</span>
              <span class="kpi-value cortex-mono" id="keys-last-verified">
                {{ formatTimestamp(keyStatus()?.lastVerifiedAt) }}
              </span>
            </div>
          </div>

          @if (keyStatus()?.lastError) {
            <div class="error-callout" role="alert" id="keys-last-error-alert">
              <mat-icon>error_outline</mat-icon>
              <span>{{ keyStatus()?.lastError }}</span>
            </div>
          }

          @if (lastValidationResult(); as valRes) {
            <div
              class="validation-callout"
              [class.validation-failed]="!valRes.valid"
              id="keys-validation-result-box"
            >
              <mat-icon>{{ valRes.valid ? 'check_circle' : 'report_problem' }}</mat-icon>
              <div>
                <strong>
                  KMS Live Self-Test: {{ valRes.valid ? 'PASSED' : 'FAILED' }} ({{ valRes.healthStatus }})
                </strong>
                <p>
                  {{ valRes.message }}
                  @if (valRes.errorCode) {
                    · Error Code: <code class="cortex-mono">{{ valRes.errorCode }}</code>
                  }
                  @if (valRes.evictedCachedDeks > 0) {
                    · Evicted Cached DEKs: <code class="cortex-mono">{{ valRes.evictedCachedDeks }}</code>
                  }
                </p>
              </div>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <!-- 2. Register or Update Customer-Managed KEK -->
      <mat-card appearance="outlined" id="keys-configure-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>settings_Suggest</mat-icon>
          <mat-card-title>Register / Update Customer-Managed KEK</mat-card-title>
          <mat-card-subtitle>
            Performs a live KMS wrapDek + unwrapDek round-trip verification with an ephemeral 32-byte test key before activating
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="form-grid" id="keys-configure-form">
            <div class="field-group">
              <label for="kek-provider-select">KMS Provider *</label>
              <select
                id="kek-provider-select"
                class="admin-select cortex-mono"
                [(ngModel)]="configureDraft.provider"
              >
                @for (p of availableProviders(); track p) {
                  <option [value]="p">{{ p }}</option>
                }
              </select>
            </div>

            <div class="field-group field-wide">
              <label for="kek-key-id-input">Customer KEK Identifier (ARN / Resource Path / Vault Transit Key) *</label>
              <input
                id="kek-key-id-input"
                type="text"
                class="admin-input cortex-mono"
                [(ngModel)]="configureDraft.kekKeyId"
                placeholder="e.g. arn:aws:kms:us-east-1:123456789012:key/mrk-acme-kek"
              />
            </div>

            <div class="field-group">
              <label for="kek-credential-input">Credential Vault Name (Optional)</label>
              <input
                id="kek-credential-input"
                type="text"
                class="admin-input cortex-mono"
                [(ngModel)]="configureDraft.credentialName"
                placeholder="e.g. aws-kms-prod-cred"
              />
            </div>

            <div class="field-group">
              <label for="kek-region-input">KMS Region (Optional)</label>
              <input
                id="kek-region-input"
                type="text"
                class="admin-input cortex-mono"
                [(ngModel)]="configureDraft.region"
                placeholder="e.g. us-east-1"
              />
            </div>

            <div class="field-group">
              <label for="kek-endpoint-input">Custom KMS Endpoint (Optional)</label>
              <input
                id="kek-endpoint-input"
                type="text"
                class="admin-input cortex-mono"
                [(ngModel)]="configureDraft.endpoint"
                placeholder="https://kms.us-east-1.amazonaws.com"
              />
            </div>
          </div>
        </mat-card-content>
        <mat-card-actions align="end">
          <button
            mat-flat-button
            color="primary"
            type="button"
            (click)="submitConfigureKek()"
            [disabled]="loading() || !configureDraft.provider.trim() || !configureDraft.kekKeyId.trim()"
            id="keys-configure-submit-btn"
          >
            <mat-icon>verified_user</mat-icon>
            Verify & Save KEK Configuration
          </button>
        </mat-card-actions>
      </mat-card>

      <!-- 3. Key Rotation & Re-Encryption Progress -->
      <div class="rotation-grid">
        <!-- 3a. KEK Rotation (Envelope Re-Wrap Only) -->
        <mat-card appearance="outlined" id="keys-rotate-kek-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>sync_lock</mat-icon>
            <mat-card-title>Rotate Tenant KEK (Envelope Re-Wrap)</mat-card-title>
            <mat-card-subtitle>
              Re-wraps all ACTIVE namespace DEKs under the new KEK version without rewriting on-disk segment or WAL files
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="field-group">
                <label for="rotate-kek-id-input">New KEK Key ID (Optional — defaults to current)</label>
                <input
                  id="rotate-kek-id-input"
                  type="text"
                  class="admin-input cortex-mono"
                  [(ngModel)]="rotateKekDraft.newKekKeyId"
                  placeholder="Leave blank to rotate version in-place"
                />
              </div>
              <div class="field-group">
                <label for="rotate-kek-version-input">Target KEK Version Label (Optional)</label>
                <input
                  id="rotate-kek-version-input"
                  type="text"
                  class="admin-input cortex-mono"
                  [(ngModel)]="rotateKekDraft.newKekVersion"
                  placeholder="e.g. v2"
                />
              </div>
            </div>

            @if (lastKekRotationResult(); as kekRot) {
              <div class="rotation-summary-box" id="keys-kek-rotation-summary">
                <strong>KEK Rotated:</strong>
                <code class="cortex-mono">{{ kekRot.previousKekVersion }}</code> →
                <code class="cortex-mono">{{ kekRot.newKekVersion }}</code> ·
                Re-wrapped DEKs: <code class="cortex-mono">{{ kekRot.rewrappedDekCount }}</code> ·
                On-disk files rewritten: <code class="cortex-mono">{{ kekRot.dataFilesRewritten ? 'Yes' : 'No (0 bytes)' }}</code>
              </div>
            }
          </mat-card-content>
          <mat-card-actions align="end">
            <button
              mat-stroked-button
              color="primary"
              type="button"
              (click)="requestRotateKek()"
              [disabled]="loading()"
              id="keys-rotate-kek-btn"
            >
              <mat-icon>autorenew</mat-icon>
              Rotate KEK
            </button>
          </mat-card-actions>
        </mat-card>

        <!-- 3b. Namespace DEK Rotation (On-Disk Re-Encryption) -->
        <mat-card appearance="outlined" id="keys-rotate-dek-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>published_with_changes</mat-icon>
            <mat-card-title>Rotate Namespace DEKs & Re-Encrypt</mat-card-title>
            <mat-card-subtitle>
              Provisions dek_version N+1 and re-encrypts runtime.bundle, partition.bundle blobs, WAL chunks, and 128-bit synaptic tags
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="form-grid">
              <div class="field-group field-wide">
                <label for="rotate-dek-namespace-input">Target Namespace ID (Leave blank to rotate all active tenant namespaces)</label>
                <input
                  id="rotate-dek-namespace-input"
                  type="text"
                  class="admin-input cortex-mono"
                  [(ngModel)]="rotateDekDraft.namespaceId"
                  placeholder="All active namespaces in tenant"
                />
              </div>
            </div>

            @if (dekRotationItems().length > 0) {
              <div class="reencryption-progress-section" id="keys-dek-reencryption-progress">
                <div class="progress-header">
                  <span>Re-Encryption Progress: {{ dekRotationItems().length }} / {{ dekRotationItems().length }} namespaces completed (100%)</span>
                </div>
                <div class="admin-table-wrap">
                  <table class="admin-table" aria-label="Namespace DEK re-encryption progress">
                    <thead>
                      <tr>
                        <th>Namespace</th>
                        <th>DEK Version</th>
                        <th>Partitions</th>
                        <th>Records Re-Keyed</th>
                        <th>WAL Events</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (item of dekRotationItems(); track item.namespaceId) {
                        <tr>
                          <td class="cortex-mono">{{ item.namespaceId }}</td>
                          <td class="cortex-mono">v{{ item.previousDekVersion }} → v{{ item.newDekVersion }}</td>
                          <td class="cortex-mono">{{ item.partitionBundlesEncrypted }}</td>
                          <td class="cortex-mono">{{ item.recordsReKeyed }}</td>
                          <td class="cortex-mono">{{ item.walEventsEncrypted }}</td>
                          <td><span class="status-pill pill-healthy">COMPLETED</span></td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            }
          </mat-card-content>
          <mat-card-actions align="end">
            <button
              mat-stroked-button
              color="primary"
              type="button"
              (click)="requestRotateDek()"
              [disabled]="loading()"
              id="keys-rotate-dek-btn"
            >
              <mat-icon>enhanced_encryption</mat-icon>
              Rotate DEK & Re-Encrypt
            </button>
          </mat-card-actions>
        </mat-card>
      </div>

      <!-- 4. KMS Key Revocation & Crypto-Shredding Consequences -->
      <mat-card appearance="outlined" class="revocation-card" id="keys-revocation-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>gpp_bad</mat-icon>
          <mat-card-title>Customer KMS Revocation & Emergency Crypto-Shredding</mat-card-title>
          <mat-card-subtitle>
            How external KMS key revocation and administrative crypto-shredding enforce fail-closed data destruction (SPE-820-010)
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="revocation-guidance" id="keys-revocation-consequences">
            <p>
              <strong>Customer KMS Revocation:</strong> Revoking, disabling, or scheduling deletion of your KEK directly inside
              your cloud KMS (<code class="cortex-mono">aws-kms</code>, <code class="cortex-mono">gcp-kms</code>,
              <code class="cortex-mono">azure-key-vault</code>, or <code class="cortex-mono">hashicorp-vault</code>) is
              automatically detected within the bounded <strong>5-minute DEK cache TTL</strong> (or immediately when clicking
              <em>Validate KMS Health</em>).
            </p>
            <ul>
              <li>
                <strong>Immediate Key Zeroization:</strong> All unwrapped DEKs in <code class="cortex-mono">DekCache</code> and
                active <code class="cortex-mono">EnvelopeDataEncryptor</code> subkeys are overwritten with zeros in memory.
              </li>
              <li>
                <strong>Off-Heap Segment Scrubbing:</strong> Open <code class="cortex-mono">SpectorMemory</code> instances for the
                tenant are evicted and closed, zeroizing off-heap <code class="cortex-mono">RuntimeBundle</code> and
                <code class="cortex-mono">PartitionBundle</code> memory segments.
              </li>
              <li>
                <strong>Fail-Closed Enforcement (<code class="cortex-mono">SPE-820-010</code>):</strong> Every subsequent
                <code class="cortex-mono">remember</code>, <code class="cortex-mono">recall</code>, or background consolidation
                operation fails closed with <code class="cortex-mono">BYOK_KEY_REVOKED</code>.
              </li>
            </ul>
          </div>
        </mat-card-content>
        <mat-card-actions align="end">
          <button
            mat-flat-button
            color="warn"
            type="button"
            (click)="requestEmergencyCryptoShred()"
            [disabled]="loading()"
            id="keys-emergency-revoke-btn"
          >
            <mat-icon>delete_forever</mat-icon>
            Emergency Crypto-Shred Tenant Keys
          </button>
        </mat-card-actions>
      </mat-card>

      <!-- 5. Key Lifecycle Audit Events -->
      <mat-card appearance="outlined" id="keys-audit-events-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>history_edu</mat-icon>
          <mat-card-title>Key Lifecycle Audit Events ({{ keyAuditEvents().length }})</mat-card-title>
          <mat-card-subtitle>
            Immutable audit log of KEK configuration, KMS validation, KEK/DEK rotations, and crypto-shredding events
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (keyAuditEvents().length === 0) {
            <p class="muted-note">No key lifecycle audit events recorded for this tenant.</p>
          } @else {
            <div class="admin-table-wrap">
              <table class="admin-table" aria-label="Key lifecycle audit events" id="keys-audit-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Target</th>
                    <th>Metadata Summary</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ev of keyAuditEvents(); track ev.id || $index) {
                    <tr>
                      <td class="cortex-mono">{{ formatTimestamp(ev.createdAt || ev.timestamp) }}</td>
                      <td><span class="status-pill cortex-mono">{{ ev.action }}</span></td>
                      <td class="cortex-mono">{{ ev.actorId || 'system' }}</td>
                      <td class="cortex-mono">{{ ev.targetId || ev.tenantId || selectedTenantId() }}</td>
                      <td class="cortex-mono">{{ formatAuditDetails(ev.details) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>
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
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .tenant-pill {
      padding: 6px 12px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
      font-size: 0.82rem;
      font-weight: 600;
    }
    .byok-break-glass-notice {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 10px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font-size: 0.82rem;
      p { margin: 4px 0 0; }
    }
    .confirm-card {
      border-color: var(--mat-sys-error);
      background: var(--mat-sys-surface-container-high);
    }
    .confirm-body {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 16px;
      flex-wrap: wrap;
    }
    .confirm-text {
      flex: 1;
      p { margin: 4px 0 0; font-size: 0.82rem; color: var(--mat-sys-on-surface-variant); }
    }
    .confirm-actions {
      display: flex;
      gap: 8px;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 12px;
      margin-top: 8px;
    }
    .kpi-wide {
      grid-column: span 2;
    }
    .kpi-tile {
      padding: 12px 14px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .kpi-label {
      font-size: 0.72rem;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .kpi-value {
      font-size: 0.95rem;
      font-weight: 600;
    }
    .kek-ref-row {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .tiny-btn {
      height: 26px;
      padding: 0 8px;
      font-size: 0.72rem;
    }
    .status-pill {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 9999px;
      font-size: 0.74rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
      width: fit-content;
    }
    .pill-byok, .pill-healthy {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .pill-revoked {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .pill-warn {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .error-callout, .validation-callout {
      margin-top: 12px;
      padding: 10px 14px;
      border-radius: 8px;
      display: flex;
      align-items: flex-start;
      gap: 10px;
      font-size: 0.82rem;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      p { margin: 2px 0 0; }
    }
    .error-callout, .validation-failed {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .rotation-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
      gap: 16px;
    }
    .rotation-summary-box {
      margin-top: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container);
      font-size: 0.8rem;
    }
    .reencryption-progress-section {
      margin-top: 14px;
    }
    .progress-header {
      font-size: 0.78rem;
      font-weight: 600;
      margin-bottom: 6px;
      color: var(--mat-sys-primary);
    }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 12px;
      margin-top: 8px;
    }
    .field-wide {
      grid-column: 1 / -1;
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
      height: 36px;
      padding: 0 10px;
      border-radius: 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.84rem;
    }
    .revocation-card {
      border-color: var(--mat-sys-error);
    }
    .revocation-guidance {
      font-size: 0.83rem;
      line-height: 1.5;
      p { margin: 0 0 8px; }
      ul { margin: 0; padding-left: 20px; }
      li { margin-bottom: 4px; }
    }
    .admin-table-wrap {
      overflow-x: auto;
    }
    .admin-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.8rem;
      th, td {
        text-align: left;
        padding: 8px 10px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }
      th {
        font-size: 0.7rem;
        text-transform: uppercase;
        color: var(--mat-sys-on-surface-variant);
      }
    }
    .muted-note {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.82rem;
      margin: 8px 0;
    }
  `],
})
export class AdminKeysComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly tenants = signal<any[]>([]);
  readonly selectedTenantId = signal<string>('default');
  readonly keyStatus = signal<TenantKeyStatusView | null>(null);
  readonly showUnmaskedKekRef = signal(false);
  readonly lastValidationResult = signal<any | null>(null);
  readonly lastKekRotationResult = signal<any | null>(null);
  readonly dekRotationItems = signal<DekRotationItemView[]>([]);
  readonly keyAuditEvents = signal<any[]>([]);
  readonly lastActionNotice = signal<string | null>(null);

  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    confirmLabel: string;
    execute: () => void;
  } | null>(null);

  readonly availableProviders = computed(() => {
    const supported = this.keyStatus()?.supportedProviders;
    if (Array.isArray(supported) && supported.length > 0) {
      return supported;
    }
    return [
      'aws-kms',
      'gcp-kms',
      'azure-key-vault',
      'hashicorp-vault',
      'local-file',
      'platform-hkdf',
    ];
  });

  readonly maskedKekId = computed(() =>
    maskKekReference(this.keyStatus()?.kekKeyId)
  );

  readonly hasConfiguredKek = computed(() => {
    const st = this.keyStatus();
    return Boolean(st && st.status && st.status !== 'UNCONFIGURED');
  });

  configureDraft = {
    provider: 'aws-kms',
    kekKeyId: '',
    credentialName: '',
    region: '',
    endpoint: '',
  };

  rotateKekDraft = {
    newKekKeyId: '',
    newKekVersion: '',
  };

  rotateDekDraft = {
    namespaceId: '',
  };

  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    const callerTenant = this.auth.currentUser()?.tenantId || 'default';
    this.selectedTenantId.set(callerTenant);

    if (this.auth.isSuperAdmin()) {
      this.api
        .listAdminTenants()
        .pipe(catchError(() => of([])))
        .subscribe((list) => {
          const items = Array.isArray(list) ? list : [];
          this.tenants.set(items);
          if (
            items.length > 0 &&
            !items.some((t) => t.tenantId === this.selectedTenantId())
          ) {
            this.selectedTenantId.set(items[0].tenantId);
          }
          this.reloadAll();
        });
    } else {
      this.reloadAll();
    }
  }

  onTenantChange(tenantId: string): void {
    if (!tenantId) return;
    this.selectedTenantId.set(tenantId);
    this.showUnmaskedKekRef.set(false);
    this.lastValidationResult.set(null);
    this.lastKekRotationResult.set(null);
    this.dekRotationItems.set([]);
    this.reloadAll();
  }

  reloadAll(): void {
    this.loadKeyStatus();
    this.loadKeyAuditEvents();
  }

  loadKeyStatus(): void {
    const tid = this.selectedTenantId();
    this.loading.set(true);
    this.api
      .getTenantKeyStatus(tid)
      .pipe(catchError(() => of(null)))
      .subscribe((status) => {
        this.loading.set(false);
        if (status) {
          this.keyStatus.set(status);
          if (status.provider && status.provider !== 'platform-hkdf') {
            this.configureDraft.provider = status.provider;
          }
          if (status.kekKeyId && !this.configureDraft.kekKeyId) {
            this.configureDraft.kekKeyId = status.kekKeyId;
          }
          if (status.credentialName) {
            this.configureDraft.credentialName = status.credentialName;
          }
          if (status.region) {
            this.configureDraft.region = status.region;
          }
          if (status.endpoint) {
            this.configureDraft.endpoint = status.endpoint;
          }
        }
      });
  }

  loadKeyAuditEvents(): void {
    const tid = this.selectedTenantId();
    this.api
      .queryAdminAuditEvents({
        tenantId: tid,
        targetType: 'TENANT_KEY',
        limit: 25,
      })
      .pipe(catchError(() => of([])))
      .subscribe((res: any) => {
        const list = Array.isArray(res) ? res : res?.items ?? res?.events ?? [];
        this.keyAuditEvents.set(list);
      });
  }

  toggleRevealKekRef(): void {
    this.showUnmaskedKekRef.update((v) => !v);
  }

  submitConfigureKek(): void {
    const tid = this.selectedTenantId();
    const provider = this.configureDraft.provider.trim();
    const kekKeyId = this.configureDraft.kekKeyId.trim();
    if (!provider || !kekKeyId) return;

    this.loading.set(true);
    this.api
      .configureTenantKey(tid, {
        provider,
        kekKeyId,
        credentialName: this.configureDraft.credentialName.trim() || null,
        region: this.configureDraft.region.trim() || null,
        endpoint: this.configureDraft.endpoint.trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.loading.set(false);
          this.keyStatus.set(updated);
          this.lastActionNotice.set(
            `Configured BYOK KEK (${provider}) for tenant "${tid}" after live KMS self-test.`
          );
          this.loadKeyAuditEvents();
        },
        error: (err) => {
          this.loading.set(false);
          this.snack.open(
            err?.error?.message || 'KMS KEK verification failed',
            'Dismiss',
            { duration: 4000 }
          );
          this.loadKeyAuditEvents();
        },
      });
  }

  runValidateKey(): void {
    const tid = this.selectedTenantId();
    this.loading.set(true);
    this.api.validateTenantKey(tid).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.lastValidationResult.set(result);
        this.lastActionNotice.set(
          `KMS validation completed for "${tid}": ${result?.healthStatus || 'HEALTHY'}`
        );
        this.reloadAll();
      },
      error: (err) => {
        this.loading.set(false);
        this.snack.open(
          err?.error?.message || 'KMS health validation failed',
          'Dismiss',
          { duration: 4000 }
        );
      },
    });
  }

  requestRotateKek(): void {
    const tid = this.selectedTenantId();
    const newKekKeyId = this.rotateKekDraft.newKekKeyId.trim() || null;
    const newKekVersion = this.rotateKekDraft.newKekVersion.trim() || null;

    this.pendingConfirmation.set({
      title: `Rotate Tenant KEK: ${tid}`,
      message: `Re-wrap all ACTIVE namespace DEKs for tenant "${tid}" under ${newKekKeyId || 'current KEK'} (${newKekVersion || 'next version'}) without rewriting on-disk segment files?`,
      confirmLabel: 'Confirm KEK Rotation',
      execute: () => {
        this.loading.set(true);
        this.api
          .rotateTenantKek(tid, {
            newKekKeyId,
            newKekVersion,
          })
          .subscribe({
            next: (res) => {
              this.loading.set(false);
              this.lastKekRotationResult.set(res);
              this.lastActionNotice.set(
                `Rotated KEK for "${tid}" to ${res?.newKekVersion || 'new version'} (rewrapped ${res?.rewrappedDekCount ?? 0} DEKs, dataFilesRewritten=false).`
              );
              this.reloadAll();
            },
            error: (err) => {
              this.loading.set(false);
              this.snack.open(
                err?.error?.message || 'KEK rotation failed',
                'Dismiss',
                { duration: 4000 }
              );
            },
          });
      },
    });
  }

  requestRotateDek(): void {
    const tid = this.selectedTenantId();
    const ns = this.rotateDekDraft.namespaceId.trim() || null;

    this.pendingConfirmation.set({
      title: `Rotate Namespace DEKs: ${ns || 'All Active Namespaces'}`,
      message: `Provision dek_version N+1 and re-encrypt on-disk bundles, WAL chunks, and 128-bit synaptic tags for ${ns ? `namespace "${ns}"` : `all active namespaces in tenant "${tid}"`}?`,
      confirmLabel: 'Confirm DEK Rotation',
      execute: () => {
        this.loading.set(true);
        this.api.rotateNamespaceDek(tid, { namespaceId: ns }).subscribe({
          next: (res) => {
            this.loading.set(false);
            const items = Array.isArray(res?.namespaces) ? res.namespaces : [];
            this.dekRotationItems.set(items);
            this.lastActionNotice.set(
              `Rotated DEKs and re-encrypted ${res?.rotatedNamespaceCount ?? items.length} namespace(s) for tenant "${tid}".`
            );
            this.reloadAll();
          },
          error: (err) => {
            this.loading.set(false);
            this.snack.open(
              err?.error?.message || 'Namespace DEK rotation failed',
              'Dismiss',
              { duration: 4000 }
            );
          },
        });
      },
    });
  }

  requestEmergencyCryptoShred(): void {
    const tid = this.selectedTenantId();
    this.pendingConfirmation.set({
      title: `Emergency Crypto-Shred Tenant Keys: ${tid}`,
      message: `Immediately evict and zeroize all cached DEKs, close open memory instances, shred wrapped DEK ciphertexts, and transition tenant "${tid}" to KEY_REVOKED (SPE-820-010)?`,
      confirmLabel: 'Crypto-Shred Keys Now',
      execute: () => {
        this.loading.set(true);
        this.api
          .revokeTenantKey(tid, {
            reason: 'Emergency administrative crypto-shred via Cortex Admin Console',
          })
          .subscribe({
            next: (res) => {
              this.loading.set(false);
              this.lastActionNotice.set(
                `Crypto-shredded tenant "${tid}" (${res?.revokedNamespaceDekCount ?? 0} DEKs revoked, ${res?.evictedCachedDeks ?? 0} cached DEKs zeroized).`
              );
              this.reloadAll();
            },
            error: (err) => {
              this.loading.set(false);
              this.snack.open(
                err?.error?.message || 'Emergency crypto-shred failed',
                'Dismiss',
                { duration: 4000 }
              );
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

  formatAuditDetails(details: any): string {
    if (!details) return '—';
    if (typeof details === 'string') return details;
    try {
      return Object.entries(details)
        .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : v}`)
        .join(' · ');
    } catch {
      return '—';
    }
  }
}
