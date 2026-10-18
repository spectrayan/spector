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

/**
 * Admin Break-Glass Access page (`/admin/break-glass`, Issue #1066, ADR-0087 §5.4).
 *
 * <p>Implements time-boxed, dual-admin approved, emergency read-only namespace access:
 * <ul>
 *   <li>Hard-disabled with explicit explanation when the tenant has BYOK enabled (`SPE-820-004`).</li>
 *   <li>Request form with mandatory justification, ticket reference, and bounded duration.</li>
 *   <li>Dual-control approvals queue (`approver != requester`).</li>
 *   <li>Active sessions with live countdown and immediate revocation.</li>
 *   <li>Read-only content viewer visibly watermarked `"BREAK-GLASS SESSION"`.</li>
 * </ul>
 * </p>
 */
@Component({
  selector: 'cortex-admin-break-glass',
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
      icon="emergency"
      title="Emergency Break-Glass Access"
      subtitle="Time-boxed, dual-admin approved, emergency read-only namespace access for non-BYOK tenants."
    >
      @if (lastAuditNotice(); as notice) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="break-glass-audit-banner">
          <mat-icon>verified</mat-icon>
          <span>{{ notice }}</span>
          <button mat-icon-button type="button" (click)="lastAuditNotice.set(null)" aria-label="Dismiss notice">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      }

      <!-- BYOK Tenant Hard-Deny Banner -->
      @if (isByokTenant()) {
        <div class="byok-prohibited-banner" role="alert" id="break-glass-byok-prohibited-banner">
          <mat-icon>vpn_key_off</mat-icon>
          <div class="byok-text">
            <strong>Break-Glass Access Disabled — Customer-Managed BYOK Encryption Active (SPE-820-004)</strong>
            <p>
              Tenant <code class="cortex-mono">{{ currentTenant()?.tenantId || 'current' }}</code> has Bring-Your-Own-Key
              (BYOK) envelope encryption enabled. Emergency break-glass access is cryptographically and policy-prohibited
              on BYOK tenants.
            </p>
          </div>
        </div>
      }

      <!-- Confirmation Dialog Banner -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" id="break-glass-confirm-dialog">
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
                id="break-glass-confirm-accept-btn"
              >
                Confirm Emergency Action
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- Request Emergency Break-Glass Session Form (Disabled when BYOK is enabled) -->
      <mat-card
        appearance="outlined"
        class="request-card"
        [class.card-disabled]="isByokTenant()"
        id="break-glass-request-card"
      >
        <mat-card-header>
          <mat-icon mat-card-avatar>emergency_recording</mat-icon>
          <mat-card-title>Request Emergency Read-Only Access</mat-card-title>
          <mat-card-subtitle>
            Requires dual-admin approval (a second distinct administrator must approve before access activates).
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="form-grid" id="break-glass-request-form">
            <div class="field-group">
              <label for="bg-namespace-input">Target Namespace ID / Slug *</label>
              <input
                id="bg-namespace-input"
                type="text"
                class="admin-input"
                [(ngModel)]="requestDraft.namespaceId"
                [disabled]="isByokTenant()"
                placeholder="e.g. prod-agent-memory"
              />
            </div>

            <div class="field-group">
              <label for="bg-ticket-input">Incident / Ticket Reference</label>
              <input
                id="bg-ticket-input"
                type="text"
                class="admin-input"
                [(ngModel)]="requestDraft.ticketRef"
                [disabled]="isByokTenant()"
                placeholder="e.g. INC-4821"
              />
            </div>

            <div class="field-group">
              <label for="bg-duration-select">Duration (Minutes)</label>
              <select
                id="bg-duration-select"
                class="admin-select"
                [(ngModel)]="requestDraft.durationMinutes"
                [disabled]="isByokTenant()"
              >
                <option [ngValue]="15">15 minutes</option>
                <option [ngValue]="30">30 minutes</option>
                <option [ngValue]="60">60 minutes (default)</option>
                <option [ngValue]="120">120 minutes</option>
              </select>
            </div>

            <div class="field-group field-wide">
              <label for="bg-justification-input">Mandatory Incident Justification *</label>
              <input
                id="bg-justification-input"
                type="text"
                class="admin-input"
                [(ngModel)]="requestDraft.justification"
                [disabled]="isByokTenant()"
                placeholder="Detailed operational reason for emergency read-only inspection"
              />
            </div>
          </div>
        </mat-card-content>
        <mat-card-actions align="end">
          <button
            mat-flat-button
            color="warn"
            type="button"
            (click)="requestCreateBreakGlass()"
            [disabled]="isByokTenant() || !requestDraft.namespaceId.trim() || !requestDraft.justification.trim()"
            id="bg-submit-request-btn"
          >
            <mat-icon>emergency</mat-icon>
            Submit Break-Glass Request
          </button>
        </mat-card-actions>
      </mat-card>

      <!-- Approvals Queue (PENDING_APPROVAL) -->
      <mat-card appearance="outlined" id="break-glass-pending-queue">
        <mat-card-header>
          <mat-icon mat-card-avatar>How_To_Reg</mat-icon>
          <mat-card-title>Pending Dual-Control Approvals ({{ pendingRequests().length }})</mat-card-title>
          <mat-card-subtitle>Second administrator approval required to issue time-boxed read-only grant</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (pendingRequests().length === 0) {
            <p class="muted-note">No break-glass requests awaiting approval.</p>
          } @else {
            <div class="admin-table-wrap">
              <table class="admin-table" aria-label="Pending break-glass approvals">
                <thead>
                  <tr>
                    <th>Session ID</th>
                    <th>Namespace</th>
                    <th>Requester</th>
                    <th>Justification & Ticket</th>
                    <th>Duration</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (req of pendingRequests(); track req.id) {
                    <tr>
                      <td class="cortex-mono">{{ req.id }}</td>
                      <td class="cortex-mono">{{ req.namespaceId }}</td>
                      <td class="cortex-mono">{{ req.requesterAccountId }}</td>
                      <td>
                        <span class="primary-cell">{{ req.justification }}</span>
                        @if (req.ticketRef) {
                          <span class="sub-cell cortex-mono">Ticket: {{ req.ticketRef }}</span>
                        }
                      </td>
                      <td class="cortex-mono">{{ req.durationMinutes }}m</td>
                      <td>
                        <div class="row-actions">
                          @if (isSelfRequested(req)) {
                            <span class="pill-badge" title="Dual-control requires a different administrator to approve">
                              Awaiting 2nd Admin
                            </span>
                          } @else {
                            <button
                              mat-flat-button
                              color="primary"
                              type="button"
                              class="action-btn"
                              (click)="requestApprove(req)"
                            >
                              Approve
                            </button>
                          }
                          <button
                            mat-stroked-button
                            color="warn"
                            type="button"
                            class="action-btn"
                            (click)="requestReject(req)"
                          >
                            Reject
                          </button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <!-- Active Sessions (ACTIVE) with Countdown & Revoke -->
      <mat-card appearance="outlined" id="break-glass-active-sessions">
        <mat-card-header>
          <mat-icon mat-card-avatar>timer</mat-icon>
          <mat-card-title>Active Emergency Sessions ({{ activeSessions().length }})</mat-card-title>
          <mat-card-subtitle>Time-boxed READ_ONLY sessions (exportPermitted=false, mcpPermitted=false)</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (activeSessions().length === 0) {
            <p class="muted-note">No active break-glass sessions.</p>
          } @else {
            <div class="admin-table-wrap">
              <table class="admin-table" aria-label="Active break-glass sessions">
                <thead>
                  <tr>
                    <th>Session ID</th>
                    <th>Namespace</th>
                    <th>Requester / Approver</th>
                    <th>Countdown / Expires</th>
                    <th>Scope</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (sess of activeSessions(); track sess.id) {
                    <tr>
                      <td class="cortex-mono">{{ sess.id }}</td>
                      <td class="cortex-mono">{{ sess.namespaceId }}</td>
                      <td>
                        <span class="primary-cell">Req: {{ sess.requesterAccountId }}</span>
                        <span class="sub-cell cortex-mono">App: {{ sess.approverAccountId || 'policy' }}</span>
                      </td>
                      <td>
                        <span class="countdown-badge cortex-mono">{{ formatCountdown(sess.expiresAt) }}</span>
                        <span class="sub-cell cortex-mono">{{ formatTimestamp(sess.expiresAt) }}</span>
                      </td>
                      <td><span class="pill-badge cortex-mono">{{ sess.accessScope || 'READ_ONLY' }}</span></td>
                      <td>
                        <div class="row-actions">
                          <button
                            mat-stroked-button
                            type="button"
                            class="action-btn"
                            (click)="openWatermarkedViewer(sess)"
                            id="open-break-glass-viewer-btn"
                          >
                            Inspect Read-Only
                          </button>
                          <button
                            mat-flat-button
                            color="warn"
                            type="button"
                            class="action-btn"
                            (click)="requestRevoke(sess)"
                          >
                            Revoke Now
                          </button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <!-- Watermarked Read-Only Break-Glass Content Viewer -->
      @if (inspectedSession(); as activeSess) {
        <mat-card appearance="outlined" class="watermarked-viewer-card" id="break-glass-viewer">
          <div class="watermark-stamp" aria-hidden="true">
            BREAK-GLASS SESSION — READ-ONLY EMERGENCY ACCESS
          </div>
          <div class="viewer-banner" role="alert">
            <mat-icon>warning_amber</mat-icon>
            <div>
              <strong>BREAK-GLASS SESSION ({{ activeSess.id }}) — READ-ONLY</strong>
              <span>
                Namespace: <code class="cortex-mono">{{ activeSess.namespaceId }}</code> ·
                Approver: <code class="cortex-mono">{{ activeSess.approverAccountId || 'dual-admin' }}</code> ·
                Expires: <code class="cortex-mono">{{ formatCountdown(activeSess.expiresAt) }}</code> ·
                Export & MCP strictly disabled
              </span>
            </div>
            <button mat-stroked-button type="button" (click)="closeWatermarkedViewer()">Close Viewer</button>
          </div>

          <mat-card-content>
            <div class="admin-table-wrap">
              <table class="admin-table" aria-label="Break-glass read-only memory inspection">
                <thead>
                  <tr>
                    <th>Engram ID</th>
                    <th>Tier</th>
                    <th>Content (Read-Only Emergency Inspection)</th>
                    <th>Salience</th>
                  </tr>
                </thead>
                <tbody>
                  @for (mem of inspectedMemories(); track mem.id || $index) {
                    <tr>
                      <td class="cortex-mono">{{ mem.id }}</td>
                      <td><span class="pill-badge">{{ mem.tier || mem.memoryType || 'EPISODIC' }}</span></td>
                      <td>{{ mem.content || mem.text }}</td>
                      <td class="cortex-mono">{{ (mem.salience ?? mem.importance ?? 0) | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="4" class="muted-note">No memory records returned for this break-glass session.</td>
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
    .byok-prohibited-banner {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 14px 18px;
      border-radius: 12px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      border: 1px solid var(--mat-sys-error);
      .byok-text {
        p { margin: 4px 0 0; font-size: 0.82rem; }
      }
    }
    .card-disabled {
      opacity: 0.65;
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
    .pill-badge, .countdown-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 0.72rem;
      font-weight: 600;
      background: var(--mat-sys-surface-container-high);
    }
    .countdown-badge {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .row-actions {
      display: flex;
      gap: 6px;
      align-items: center;
      flex-wrap: wrap;
    }
    .action-btn {
      font-size: 0.75rem;
      height: 30px;
      padding: 0 10px;
    }
    .muted-note {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.82rem;
      margin: 8px 0;
    }
    .watermarked-viewer-card {
      position: relative;
      overflow: hidden;
      border: 2px solid var(--mat-sys-error);
    }
    .watermark-stamp {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      font-weight: 800;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: rgba(220, 38, 38, 0.11);
      transform: rotate(-12deg);
      pointer-events: none;
      user-select: none;
      z-index: 1;
    }
    .viewer-banner {
      position: relative;
      z-index: 2;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      div {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 2px;
        font-size: 0.8rem;
      }
    }
  `],
})
export class AdminBreakGlassComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly requests = signal<any[]>([]);
  readonly currentTenant = signal<any | null>(null);
  readonly inspectedSession = signal<any | null>(null);
  readonly inspectedMemories = signal<any[]>([]);
  readonly lastAuditNotice = signal<string | null>(null);

  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    execute: () => void;
  } | null>(null);

  readonly isByokTenant = computed(() => {
    return Boolean(this.currentTenant()?.settings?.byokEnabled);
  });

  readonly pendingRequests = computed(() =>
    this.requests().filter((r) => r.status === 'PENDING_APPROVAL'),
  );

  readonly activeSessions = computed(() =>
    this.requests().filter((r) => r.status === 'ACTIVE'),
  );

  requestDraft = {
    namespaceId: '',
    justification: '',
    ticketRef: '',
    durationMinutes: 60,
  };

  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadTenantByokStatus();
    this.loadBreakGlassRequests();
  }

  loadTenantByokStatus(): void {
    const tid = this.auth.currentUser()?.tenantId || 'default';
    this.api
      .getAdminTenant(tid)
      .pipe(catchError(() => of(null)))
      .subscribe((tenant) => {
        if (tenant) {
          this.currentTenant.set(tenant);
        }
      });
  }

  loadBreakGlassRequests(): void {
    this.api
      .listBreakGlassRequests()
      .pipe(catchError(() => of([])))
      .subscribe((list) => {
        this.requests.set(Array.isArray(list) ? list : []);
      });
  }

  isSelfRequested(req: any): boolean {
    const user = this.auth.currentUser();
    if (!user) return false;
    return (
      req.requesterAccountId === user.userId ||
      req.requesterAccountId === user.username
    );
  }

  requestCreateBreakGlass(): void {
    if (this.isByokTenant()) return;
    const ns = this.requestDraft.namespaceId.trim();
    const justification = this.requestDraft.justification.trim();
    if (!ns || !justification) return;

    this.pendingConfirmation.set({
      title: `Submit Break-Glass Request: ${ns}`,
      message: `Request ${this.requestDraft.durationMinutes}m emergency read-only access to "${ns}" (requires 2nd admin approval)?`,
      execute: () => {
        this.api
          .requestBreakGlass({
            namespaceId: ns,
            justification,
            ticketRef: this.requestDraft.ticketRef.trim() || undefined,
            durationMinutes: Number(this.requestDraft.durationMinutes) || 60,
          })
          .subscribe({
            next: (created) => {
              this.requestDraft = {
                namespaceId: '',
                justification: '',
                ticketRef: '',
                durationMinutes: 60,
              };
              this.loadBreakGlassRequests();
              this.lastAuditNotice.set(
                `Break-glass request submitted (Session ID: ${created?.id || 'pending'})`,
              );
            },
            error: (err) => {
              this.snack.open(
                err?.error?.message || 'Break-glass request rejected',
                'Dismiss',
                { duration: 4000 },
              );
            },
          });
      },
    });
  }

  requestApprove(req: any): void {
    this.pendingConfirmation.set({
      title: `Approve Break-Glass Request: ${req.id}`,
      message: `Grant ${req.durationMinutes}m emergency read-only access to namespace "${req.namespaceId}" for "${req.requesterAccountId}"?`,
      execute: () => {
        this.api.approveBreakGlass(req.id, 'Approved via dual-control console').subscribe({
          next: (approved) => {
            this.loadBreakGlassRequests();
            this.lastAuditNotice.set(
              `Approved break-glass session ${approved?.id || req.id} (Grant: ${approved?.grantId || 'READ_ONLY'})`,
            );
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Approval failed', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  requestReject(req: any): void {
    this.pendingConfirmation.set({
      title: `Reject Break-Glass Request: ${req.id}`,
      message: `Reject pending break-glass request "${req.id}" for namespace "${req.namespaceId}"?`,
      execute: () => {
        this.api.rejectBreakGlass(req.id, 'Rejected by administrator').subscribe({
          next: () => {
            this.loadBreakGlassRequests();
            this.lastAuditNotice.set(`Rejected break-glass request ${req.id}`);
          },
        });
      },
    });
  }

  requestRevoke(sess: any): void {
    this.pendingConfirmation.set({
      title: `Revoke Active Break-Glass Session: ${sess.id}`,
      message: `Immediately terminate emergency access to "${sess.namespaceId}" (Session ${sess.id})?`,
      execute: () => {
        this.api.revokeBreakGlass(sess.id, 'Manually revoked via Admin Console').subscribe({
          next: () => {
            if (this.inspectedSession()?.id === sess.id) {
              this.closeWatermarkedViewer();
            }
            this.loadBreakGlassRequests();
            this.lastAuditNotice.set(`Revoked break-glass session ${sess.id}`);
          },
        });
      },
    });
  }

  openWatermarkedViewer(sess: any): void {
    this.inspectedSession.set(sess);
    this.api
      .inspectBreakGlassNamespaceMemories(sess.namespaceId, 25)
      .pipe(catchError(() => of([])))
      .subscribe((res) => {
        const rows = Array.isArray(res) ? res : res?.memories ?? res?.items ?? [];
        this.inspectedMemories.set(rows);
      });
  }

  closeWatermarkedViewer(): void {
    this.inspectedSession.set(null);
    this.inspectedMemories.set([]);
  }

  formatCountdown(expiresAt?: string | null): string {
    if (!expiresAt) return 'Active';
    const diffMs = new Date(expiresAt).getTime() - Date.now();
    if (Number.isNaN(diffMs) || diffMs <= 0) return 'Expired';
    const mins = Math.floor(diffMs / 60000);
    const secs = Math.floor((diffMs % 60000) / 1000);
    return `${mins}m ${secs}s remaining`;
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
}
