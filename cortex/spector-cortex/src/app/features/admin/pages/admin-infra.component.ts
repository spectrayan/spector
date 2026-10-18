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
  ADMIN_INFRA_CONFIG_CATEGORY_KEYS,
  CATEGORY_METADATA,
} from '../../settings/models/settings.models';
import {
  AdminSectionShellComponent,
  formatAdminTimestamp,
} from '../admin-console.component';

export type InfraTab =
  | 'providers'
  | 'config'
  | 'cluster'
  | 'tasks'
  | 'dlq-cache'
  | 'connectors-benchmarks';

/**
 * Admin Infrastructure & Benchmarks page (`/admin/infra`, Issue #1066, supersedes #239).
 *
 * <p>Exposes role-scoped infrastructure administration (`super-admin` vs `admin`):
 * LLM/embedding providers, tenant/global-scope configuration (`llm_provider`,
 * `embedding_provider`, `hnsw`, `concurrency`, `telemetry`, `ingestion`, `multimodal`),
 * system/cluster/SIMD/GPU status (`super-admin`), scheduler tasks & task audit,
 * DLQ redrive, Caffeine cache management, connector status, and cognitive benchmarks.</p>
 */
@Component({
  selector: 'cortex-admin-infra',
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
      icon="dns"
      title="Infrastructure, Config & Benchmarks"
      [subtitle]="
        auth.isSuperAdmin()
          ? 'Platform-wide & tenant providers, global/tenant config, cluster/SIMD/GPU, tasks, DLQ, cache, connectors, and benchmarks.'
          : 'Tenant-scoped providers, tenant config overrides, scheduler tasks, DLQ redrive, cache, and connector status.'
      "
    >
      @if (lastActionNotice(); as notice) {
        <div class="audit-toast-banner" role="status" aria-live="polite" id="infra-last-action-banner">
          <mat-icon>verified</mat-icon>
          <span>{{ notice }}</span>
          <button mat-icon-button type="button" (click)="lastActionNotice.set(null)" aria-label="Dismiss notice">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      }

      <!-- Confirmation Dialog Banner -->
      @if (pendingConfirmation(); as pending) {
        <mat-card appearance="outlined" class="confirm-card" role="alertdialog" id="infra-confirm-dialog">
          <div class="confirm-body">
            <mat-icon class="confirm-icon">help_outline</mat-icon>
            <div class="confirm-text">
              <strong>{{ pending.title }}</strong>
              <p>{{ pending.message }}</p>
            </div>
            <div class="confirm-actions">
              <button mat-stroked-button type="button" (click)="cancelConfirmation()">Cancel</button>
              <button
                mat-flat-button
                color="primary"
                type="button"
                (click)="executeConfirmedAction()"
                id="infra-confirm-accept-btn"
              >
                Confirm Action
              </button>
            </div>
          </div>
        </mat-card>
      }

      <!-- Sub-Navigation Tabs (role-filtered: cluster/SIMD/GPU tab is super-admin only) -->
      <div class="infra-tabs" role="tablist" aria-label="Infrastructure sections">
        @for (tab of visibleTabs(); track tab.id) {
          <button
            type="button"
            role="tab"
            class="tab-pill"
            [class.active]="activeTab() === tab.id"
            [attr.aria-selected]="activeTab() === tab.id"
            (click)="selectTab(tab.id)"
            [id]="'infra-tab-' + tab.id"
          >
            <mat-icon>{{ tab.icon }}</mat-icon>
            <span>{{ tab.label }}</span>
          </button>
        }
      </div>

      <!-- TAB 1: LLM & Embedding Providers -->
      @if (activeTab() === 'providers') {
        <section class="tab-panel" id="panel-providers">
          <div class="panel-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>psychology</mat-icon>
                <mat-card-title>Generation (LLM) Providers</mat-card-title>
                <mat-card-subtitle>Active text generation model and registered factories</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @for (p of generationProviders(); track p.name) {
                  <div class="provider-row">
                    <div>
                      <span class="primary-cell">{{ p.displayName || p.name }}</span>
                      <span class="sub-cell cortex-mono">{{ p.name }} · {{ p.model || 'default' }}</span>
                    </div>
                    <div class="row-actions">
                      @if (p.active) {
                        <span class="status-pill active">ACTIVE</span>
                      } @else {
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn"
                          (click)="requestActivateProvider('generation', p.name)"
                        >
                          Activate
                        </button>
                      }
                    </div>
                  </div>
                } @empty {
                  <p class="muted-note">No generation providers reported.</p>
                }
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>hub</mat-icon>
                <mat-card-title>Embedding Providers</mat-card-title>
                <mat-card-subtitle>Vector embedding model and health status</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @for (p of embeddingProviders(); track p.name) {
                  <div class="provider-row">
                    <div>
                      <span class="primary-cell">{{ p.displayName || p.name }}</span>
                      <span class="sub-cell cortex-mono">{{ p.name }} · {{ p.dimensions || 768 }}d</span>
                    </div>
                    <div class="row-actions">
                      @if (p.active) {
                        <span class="status-pill active">ACTIVE</span>
                      } @else {
                        <button
                          mat-stroked-button
                          type="button"
                          class="action-btn"
                          (click)="requestActivateProvider('embedding', p.name)"
                        >
                          Activate
                        </button>
                      }
                    </div>
                  </div>
                } @empty {
                  <p class="muted-note">No embedding providers reported.</p>
                }
              </mat-card-content>
            </mat-card>
          </div>
        </section>
      }

      <!-- TAB 2: Tenant & Global Infrastructure Config -->
      @if (activeTab() === 'config') {
        <section class="tab-panel" id="panel-config">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-icon mat-card-avatar>tune</mat-icon>
              <mat-card-title>Infrastructure Configuration Overrides</mat-card-title>
              <mat-card-subtitle>
                Manage llm_provider, embedding_provider, hnsw, concurrency, telemetry, ingestion, and multimodal at
                {{ configScope() === 'system' ? 'Global (System)' : 'Tenant' }} scope
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="config-controls">
                <div class="field-group">
                  <label for="infra-config-category">Category</label>
                  <select
                    id="infra-config-category"
                    class="admin-select"
                    [ngModel]="activeConfigCategory()"
                    (ngModelChange)="selectConfigCategory($event)"
                  >
                    @for (cat of infraConfigCategories; track cat.key) {
                      <option [value]="cat.key">{{ cat.label }} ({{ cat.key }})</option>
                    }
                  </select>
                </div>

                <div class="field-group">
                  <label for="infra-config-scope">Target Scope</label>
                  <select
                    id="infra-config-scope"
                    class="admin-select"
                    [ngModel]="configScope()"
                    (ngModelChange)="configScope.set($event)"
                    [disabled]="!auth.isSuperAdmin()"
                  >
                    <option value="tenant">Tenant Scope (tenant)</option>
                    @if (auth.isSuperAdmin()) {
                      <option value="system">Global Platform Scope (system)</option>
                    }
                  </select>
                </div>

                <div class="row-actions">
                  <button
                    mat-flat-button
                    color="primary"
                    type="button"
                    (click)="requestSaveConfigCategory()"
                    id="infra-save-config-btn"
                  >
                    Save {{ configScope() | uppercase }} Override
                  </button>
                  <button
                    mat-stroked-button
                    color="warn"
                    type="button"
                    (click)="requestResetConfigCategory()"
                    id="infra-reset-config-btn"
                  >
                    Reset Override
                  </button>
                </div>
              </div>

              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Infrastructure configuration fields">
                  <thead>
                    <tr>
                      <th>Parameter Key</th>
                      <th>Effective Value</th>
                      <th>Source</th>
                      <th>Apply Mode</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (field of configFields(); track field.key) {
                      <tr>
                        <td>
                          <span class="primary-cell cortex-mono">{{ field.key }}</span>
                          <span class="sub-cell">{{ field.description }}</span>
                        </td>
                        <td>
                          <input
                            type="text"
                            class="admin-input"
                            [(ngModel)]="field.editValue"
                            [attr.aria-label]="field.key"
                          />
                        </td>
                        <td><span class="pill-badge">{{ field.source }}</span></td>
                        <td><span class="pill-badge">{{ field.applyMode || 'LIVE' }}</span></td>
                      </tr>
                    } @empty {
                      <tr>
                        <td colspan="4" class="muted-note">No schema fields loaded for {{ activeConfigCategory() }}.</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </mat-card-content>
          </mat-card>
        </section>
      }

      <!-- TAB 3: Cluster, SIMD & GPU Hardware (Super-Admin Only) -->
      @if (activeTab() === 'cluster' && auth.isSuperAdmin()) {
        <section class="tab-panel" id="panel-cluster">
          <div class="panel-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>memory</mat-icon>
                <mat-card-title>Runtime, SIMD & GPU Accelerators</mat-card-title>
                <mat-card-subtitle>Platform operator hardware and kernel status</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="kv-list">
                  <div class="kv-row">
                    <span>Engine Status</span>
                    <strong class="cortex-mono">{{ systemStatus()?.status || 'UP' }}</strong>
                  </div>
                  <div class="kv-row">
                    <span>Version</span>
                    <strong class="cortex-mono">{{ systemStatus()?.version || '0.1.0-alpha.2' }}</strong>
                  </div>
                  <div class="kv-row">
                    <span>Uptime</span>
                    <strong class="cortex-mono">{{ systemStatus()?.uptime || '0h 0m 0s' }}</strong>
                  </div>
                  <div class="kv-row">
                    <span>Vector Accelerator</span>
                    <strong class="cortex-mono">{{ systemStatus()?.simdCapability || 'Java 25 Vector API (SIMD) + Panama FFM' }}</strong>
                  </div>
                  <div class="kv-row">
                    <span>Data Directory</span>
                    <strong class="cortex-mono">{{ systemConfig()?.dataDir || '/var/lib/spector' }}</strong>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </section>
      }

      <!-- TAB 4: Scheduler Tasks & Task Audit -->
      @if (activeTab() === 'tasks') {
        <section class="tab-panel" id="panel-tasks">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-icon mat-card-avatar>schedule</mat-icon>
              <mat-card-title>Background Cognitive & Maintenance Tasks ({{ tasks().length }})</mat-card-title>
              <mat-card-subtitle>Virtual-thread scheduled jobs and execution audit history</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="admin-table-wrap">
                <table class="admin-table" aria-label="Scheduled background tasks">
                  <thead>
                    <tr>
                      <th>Task ID</th>
                      <th>Schedule</th>
                      <th>State</th>
                      <th>Last Run</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (task of tasks(); track task.id) {
                      <tr>
                        <td>
                          <span class="primary-cell cortex-mono">{{ task.id }}</span>
                          <span class="sub-cell">{{ task.description || 'Scheduled background task' }}</span>
                        </td>
                        <td class="cortex-mono">{{ task.schedule || task.cronExpression || (task.intervalSeconds + 's') }}</td>
                        <td>
                          <span class="status-pill" [class.active]="!task.paused">
                            {{ task.paused ? 'PAUSED' : (task.state || 'ACTIVE') }}
                          </span>
                        </td>
                        <td class="cortex-mono">{{ formatTimestamp(task.lastRunAt) }}</td>
                        <td>
                          <div class="row-actions">
                            <button
                              mat-stroked-button
                              type="button"
                              class="action-btn"
                              (click)="requestTaskAction(task.id, 'trigger')"
                            >
                              Trigger Now
                            </button>
                            @if (task.paused) {
                              <button
                                mat-stroked-button
                                type="button"
                                class="action-btn"
                                (click)="requestTaskAction(task.id, 'resume')"
                              >
                                Resume
                              </button>
                            } @else {
                              <button
                                mat-stroked-button
                                type="button"
                                class="action-btn"
                                (click)="requestTaskAction(task.id, 'pause')"
                              >
                                Pause
                              </button>
                            }
                          </div>
                        </td>
                      </tr>
                    } @empty {
                      <tr>
                        <td colspan="5" class="muted-note">No scheduled tasks registered.</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>

              @if (taskAuditLogs().length > 0) {
                <h4 class="sub-heading">Recent Task Execution Audit ({{ taskAuditLogs().length }})</h4>
                <div class="admin-table-wrap">
                  <table class="admin-table" aria-label="Task execution audit log">
                    <thead>
                      <tr>
                        <th>Task ID</th>
                        <th>Outcome</th>
                        <th>Duration</th>
                        <th>Timestamp</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (run of taskAuditLogs(); track $index) {
                        <tr>
                          <td class="cortex-mono">{{ run.taskId || run.id }}</td>
                          <td><span class="pill-badge">{{ run.status || run.outcome || 'SUCCESS' }}</span></td>
                          <td class="cortex-mono">{{ run.durationMs ?? 0 }} ms</td>
                          <td class="cortex-mono">{{ formatTimestamp(run.startedAt || run.timestamp) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </mat-card-content>
          </mat-card>
        </section>
      }

      <!-- TAB 5: DLQ Redrive & Cache Administration -->
      @if (activeTab() === 'dlq-cache') {
        <section class="tab-panel" id="panel-dlq-cache">
          <div class="panel-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>cached</mat-icon>
                <mat-card-title>Caffeine Caches ({{ caches().length }})</mat-card-title>
                <mat-card-subtitle>In-memory cache hit rates, evictions, and invalidation</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="row-actions" style="margin-bottom: 10px;">
                  <button
                    mat-stroked-button
                    color="warn"
                    type="button"
                    (click)="requestClearAllCaches()"
                    id="clear-all-caches-btn"
                  >
                    Clear All Caches
                  </button>
                </div>
                <div class="admin-table-wrap">
                  <table class="admin-table" aria-label="Caffeine caches">
                    <thead>
                      <tr>
                        <th>Cache</th>
                        <th>Size</th>
                        <th>Hit Rate</th>
                        <th>Evictions</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (c of caches(); track c.name) {
                        <tr>
                          <td class="cortex-mono">{{ c.name }}</td>
                          <td class="cortex-mono">{{ c.estimatedSize }}</td>
                          <td class="cortex-mono">{{ ((c.hitRate ?? 0) * 100) | number:'1.1-1' }}%</td>
                          <td class="cortex-mono">{{ c.evictionCount }}</td>
                          <td>
                            <button
                              mat-stroked-button
                              type="button"
                              class="action-btn"
                              (click)="requestClearCache(c.name)"
                            >
                              Clear
                            </button>
                          </td>
                        </tr>
                      } @empty {
                        <tr>
                          <td colspan="5" class="muted-note">No caches reported.</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>error_outline</mat-icon>
                <mat-card-title>Dead-Letter Queue ({{ dlqItems().length }})</mat-card-title>
                <mat-card-subtitle>Failed ingestion envelopes eligible for redrive</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="admin-table-wrap">
                  <table class="admin-table" aria-label="Dead letter queue items">
                    <thead>
                      <tr>
                        <th>File / Envelope</th>
                        <th>Reason</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (item of dlqItems(); track item.fileName || item.id) {
                        <tr>
                          <td class="cortex-mono">{{ item.fileName || item.id }}</td>
                          <td>{{ item.error || item.reason || 'Ingestion failure' }}</td>
                          <td>
                            <div class="row-actions">
                              <button
                                mat-stroked-button
                                type="button"
                                class="action-btn"
                                (click)="requestRedriveDlq(item.fileName || item.id)"
                              >
                                Redrive
                              </button>
                              <button
                                mat-stroked-button
                                color="warn"
                                type="button"
                                class="action-btn"
                                (click)="requestDeleteDlq(item.fileName || item.id)"
                              >
                                Delete
                              </button>
                            </div>
                          </td>
                        </tr>
                      } @empty {
                        <tr>
                          <td colspan="3" class="muted-note">Dead-letter queue is empty.</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </section>
      }

      <!-- TAB 6: Connectors (Supersedes #239) & Benchmarks -->
      @if (activeTab() === 'connectors-benchmarks') {
        <section class="tab-panel" id="panel-connectors-benchmarks">
          <div class="panel-grid">
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>cable</mat-icon>
                <mat-card-title>Connector Status ({{ connectors().length }})</mat-card-title>
                <mat-card-subtitle>Active ingestion routes and connector lifecycle</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @for (conn of connectors(); track conn.id) {
                  <div class="provider-row">
                    <div>
                      <span class="primary-cell">{{ conn.name || conn.id }}</span>
                      <span class="sub-cell cortex-mono">{{ conn.type || 'connector' }} · {{ conn.status || 'RUNNING' }}</span>
                    </div>
                    <div class="row-actions">
                      <button
                        mat-stroked-button
                        type="button"
                        class="action-btn"
                        (click)="requestConnectorAction(conn.id, 'reload')"
                      >
                        Reload
                      </button>
                    </div>
                  </div>
                } @empty {
                  <p class="muted-note">No external connectors configured.</p>
                }
              </mat-card-content>
            </mat-card>

            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-icon mat-card-avatar>speed</mat-icon>
                <mat-card-title>Cognitive Benchmark Suites ({{ suites().length }})</mat-card-title>
                <mat-card-subtitle>Synthetic latency, recall accuracy, and overhead suites</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @for (suite of suites(); track suite.id) {
                  <div class="provider-row">
                    <div>
                      <span class="primary-cell">{{ suite.name }}</span>
                      <span class="sub-cell">{{ suite.description }} (~{{ suite.estimatedDurationSec }}s)</span>
                    </div>
                    <button
                      mat-flat-button
                      color="primary"
                      type="button"
                      class="action-btn"
                      (click)="runBenchmark(suite.id)"
                      [disabled]="benchmarkRunning() !== null"
                    >
                      {{ benchmarkRunning() === suite.id ? 'Running...' : 'Run' }}
                    </button>
                  </div>
                } @empty {
                  <p class="muted-note">No benchmark suites registered.</p>
                }

                @if (benchmarkResult(); as res) {
                  <div class="bench-result-box cortex-mono">
                    <strong>{{ res.suiteId }}</strong> completed in {{ res.durationMs }}ms
                  </div>
                }
              </mat-card-content>
            </mat-card>
          </div>
        </section>
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
    .confirm-card {
      border-color: var(--mat-sys-primary);
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
    .infra-tabs {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .tab-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 14px;
      border-radius: 9999px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &.active {
        background: var(--mat-sys-primary);
        color: var(--mat-sys-on-primary);
        border-color: var(--mat-sys-primary);
      }
    }
    .panel-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
      gap: 16px;
    }
    .provider-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .config-controls {
      display: flex;
      align-items: flex-end;
      gap: 14px;
      flex-wrap: wrap;
      margin-bottom: 16px;
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
    .admin-select, .admin-input {
      height: 34px;
      padding: 0 10px;
      border-radius: 8px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface);
      font-size: 0.82rem;
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
    .kv-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-top: 8px;
    }
    .kv-row {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      font-size: 0.84rem;
    }
    .sub-heading {
      margin: 16px 0 8px;
      font-size: 0.9rem;
      font-weight: 600;
    }
    .muted-note {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.82rem;
      margin: 8px 0;
    }
    .bench-result-box {
      margin-top: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
      font-size: 0.8rem;
    }
  `],
})
export class AdminInfraComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly activeTab = signal<InfraTab>('providers');
  readonly generationProviders = signal<any[]>([]);
  readonly embeddingProviders = signal<any[]>([]);
  readonly activeConfigCategory = signal<string>('llm_provider');
  readonly configScope = signal<'tenant' | 'system'>('tenant');
  readonly configFields = signal<any[]>([]);
  readonly systemStatus = signal<any | null>(null);
  readonly systemConfig = signal<any | null>(null);
  readonly tasks = signal<any[]>([]);
  readonly taskAuditLogs = signal<any[]>([]);
  readonly caches = signal<any[]>([]);
  readonly dlqItems = signal<any[]>([]);
  readonly connectors = signal<any[]>([]);
  readonly suites = signal<any[]>([]);
  readonly benchmarkRunning = signal<string | null>(null);
  readonly benchmarkResult = signal<any | null>(null);
  readonly lastActionNotice = signal<string | null>(null);

  readonly pendingConfirmation = signal<{
    title: string;
    message: string;
    execute: () => void;
  } | null>(null);

  readonly infraConfigCategories = Array.from(ADMIN_INFRA_CONFIG_CATEGORY_KEYS).map((key) => ({
    key,
    label: CATEGORY_METADATA[key]?.label || key,
  }));

  readonly visibleTabs = computed(() => {
    const base: { id: InfraTab; label: string; icon: string }[] = [
      { id: 'providers', label: 'Providers', icon: 'psychology' },
      { id: 'config', label: 'Tenant / Global Config', icon: 'tune' },
    ];
    if (this.auth.isSuperAdmin()) {
      base.push({ id: 'cluster', label: 'Cluster, SIMD & GPU', icon: 'memory' });
    }
    base.push(
      { id: 'tasks', label: 'Scheduler Tasks', icon: 'schedule' },
      { id: 'dlq-cache', label: 'DLQ & Caches', icon: 'cached' },
      { id: 'connectors-benchmarks', label: 'Connectors & Benchmarks', icon: 'speed' },
    );
    return base;
  });

  protected readonly formatTimestamp = formatAdminTimestamp;

  ngOnInit(): void {
    this.loadProviders();
    this.loadConfigCategory(this.activeConfigCategory());
    if (this.auth.isSuperAdmin()) {
      this.loadClusterAndSystem();
    }
    this.loadTasks();
    this.loadCachesAndDlq();
    this.loadConnectorsAndBenchmarks();
  }

  selectTab(tab: InfraTab): void {
    this.activeTab.set(tab);
  }

  loadProviders(): void {
    this.api
      .listGenerationProviders()
      .pipe(catchError(() => of([])))
      .subscribe((res) => {
        const list = Array.isArray(res) ? res : res?.providers ?? [];
        this.generationProviders.set(list);
      });
    this.api
      .listEmbeddingProviders()
      .pipe(catchError(() => of([])))
      .subscribe((res) => {
        const list = Array.isArray(res) ? res : res?.providers ?? [];
        this.embeddingProviders.set(list);
      });
  }

  requestActivateProvider(kind: 'generation' | 'embedding', name: string): void {
    this.pendingConfirmation.set({
      title: `Activate ${kind === 'generation' ? 'Generation' : 'Embedding'} Provider: ${name}`,
      message: `Switch active ${kind} provider to "${name}"?`,
      execute: () => {
        const call$ =
          kind === 'generation'
            ? this.api.activateGenerationProvider(name)
            : this.api.activateEmbeddingProvider(name);
        call$.subscribe({
          next: () => {
            this.loadProviders();
            this.lastActionNotice.set(`Activated ${kind} provider "${name}"`);
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to activate provider', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  selectConfigCategory(category: string): void {
    this.activeConfigCategory.set(category);
    this.loadConfigCategory(category);
  }

  loadConfigCategory(category: string): void {
    this.api
      .getConfigSchema(category)
      .pipe(catchError(() => of({ fields: [] })))
      .subscribe((schemaRes) => {
        this.api
          .getAnnotatedConfig(category)
          .pipe(catchError(() => of({})))
          .subscribe((annotatedRes) => {
            const fields = (schemaRes?.fields || []).map((f: any) => {
              const entry = annotatedRes ? annotatedRes[f.key] : null;
              return {
                key: f.key,
                description: f.description || '',
                defaultValue: f.defaultValue,
                editValue: entry?.value ?? f.defaultValue ?? '',
                source: entry?.source ?? 'system',
                applyMode: f.applyMode || 'LIVE',
              };
            });
            this.configFields.set(fields);
          });
      });
  }

  requestSaveConfigCategory(): void {
    const category = this.activeConfigCategory();
    const scope = this.auth.isSuperAdmin() ? this.configScope() : 'tenant';
    const values: Record<string, any> = {};
    for (const f of this.configFields()) {
      values[f.key] = f.editValue;
    }

    this.pendingConfirmation.set({
      title: `Save ${scope.toUpperCase()} Config Override: ${category}`,
      message: `Persist ${Object.keys(values).length} parameter(s) for "${category}" at ${scope} scope?`,
      execute: () => {
        this.api.saveConfig(category, scope, values).subscribe({
          next: (res) => {
            this.loadConfigCategory(category);
            this.lastActionNotice.set(
              `Saved ${category} config at ${scope} scope (status: ${res?.status || 'applied'})`,
            );
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to save configuration', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  requestResetConfigCategory(): void {
    const category = this.activeConfigCategory();
    const scope = this.auth.isSuperAdmin() ? this.configScope() : 'tenant';
    this.pendingConfirmation.set({
      title: `Reset ${scope.toUpperCase()} Config Override: ${category}`,
      message: `Remove ${scope}-level overrides for "${category}" and revert to inherited defaults?`,
      execute: () => {
        this.api.deleteConfig(category, scope).subscribe({
          next: () => {
            this.loadConfigCategory(category);
            this.lastActionNotice.set(`Reset ${category} override at ${scope} scope`);
          },
          error: (err) => {
            this.snack.open(err?.error?.message || 'Failed to reset configuration', 'Dismiss', {
              duration: 4000,
            });
          },
        });
      },
    });
  }

  loadClusterAndSystem(): void {
    this.api
      .getSystemStatus()
      .pipe(catchError(() => of(null)))
      .subscribe((st) => this.systemStatus.set(st));
    this.api
      .getSystemConfig()
      .pipe(catchError(() => of(null)))
      .subscribe((cfg) => this.systemConfig.set(cfg));
  }

  loadTasks(): void {
    this.api
      .listTasks()
      .pipe(catchError(() => of([])))
      .subscribe((list) => this.tasks.set(Array.isArray(list) ? list : []));
    this.api
      .getRecentTaskAuditHistory(25)
      .pipe(catchError(() => of([])))
      .subscribe((logs) => this.taskAuditLogs.set(Array.isArray(logs) ? logs : []));
  }

  requestTaskAction(taskId: string, action: 'trigger' | 'pause' | 'resume'): void {
    this.pendingConfirmation.set({
      title: `${action.toUpperCase()} Task: ${taskId}`,
      message: `Confirm ${action} action on scheduled task "${taskId}"?`,
      execute: () => {
        const req$ =
          action === 'trigger'
            ? this.api.triggerTask(taskId)
            : action === 'pause'
              ? this.api.pauseTask(taskId)
              : this.api.resumeTask(taskId);
        req$.subscribe({
          next: () => {
            this.loadTasks();
            this.lastActionNotice.set(`Task "${taskId}" ${action}ed`);
          },
        });
      },
    });
  }

  loadCachesAndDlq(): void {
    this.api
      .listAdminCaches()
      .pipe(catchError(() => of([])))
      .subscribe((list) => this.caches.set(Array.isArray(list) ? list : []));

    const tenantId = this.auth.currentUser()?.tenantId || 'default';
    this.api
      .listDlq(tenantId)
      .pipe(catchError(() => of([])))
      .subscribe((items) => this.dlqItems.set(Array.isArray(items) ? items : []));
  }

  requestClearCache(cacheName: string): void {
    this.pendingConfirmation.set({
      title: `Clear Cache: ${cacheName}`,
      message: `Evict all entries from cache "${cacheName}"?`,
      execute: () => {
        this.api.clearAdminCache(cacheName).subscribe({
          next: () => {
            this.loadCachesAndDlq();
            this.lastActionNotice.set(`Cleared cache "${cacheName}"`);
          },
        });
      },
    });
  }

  requestClearAllCaches(): void {
    this.pendingConfirmation.set({
      title: 'Clear All Caches',
      message: 'Evict all entries across all registered Caffeine caches?',
      execute: () => {
        this.api.clearAllAdminCaches().subscribe({
          next: () => {
            this.loadCachesAndDlq();
            this.lastActionNotice.set('Cleared all Caffeine caches');
          },
        });
      },
    });
  }

  requestRedriveDlq(fileName: string): void {
    const tenantId = this.auth.currentUser()?.tenantId || 'default';
    this.pendingConfirmation.set({
      title: `Redrive DLQ Item: ${fileName}`,
      message: `Re-submit dead-letter queue item "${fileName}" for ingestion?`,
      execute: () => {
        this.api.redriveDlq(tenantId, fileName).subscribe({
          next: () => {
            this.loadCachesAndDlq();
            this.lastActionNotice.set(`Redrove DLQ envelope "${fileName}"`);
          },
        });
      },
    });
  }

  requestDeleteDlq(fileName: string): void {
    const tenantId = this.auth.currentUser()?.tenantId || 'default';
    this.pendingConfirmation.set({
      title: `Delete DLQ Item: ${fileName}`,
      message: `Permanently delete dead-letter queue item "${fileName}"?`,
      execute: () => {
        this.api.deleteDlq(tenantId, fileName).subscribe({
          next: () => {
            this.loadCachesAndDlq();
            this.lastActionNotice.set(`Deleted DLQ envelope "${fileName}"`);
          },
        });
      },
    });
  }

  loadConnectorsAndBenchmarks(): void {
    this.api
      .listConnectors()
      .pipe(catchError(() => of([])))
      .subscribe((list) => this.connectors.set(Array.isArray(list) ? list : []));
    this.api
      .listBenchmarkSuites()
      .pipe(catchError(() => of({ suites: [] })))
      .subscribe((data) => this.suites.set(data?.suites ?? []));
  }

  requestConnectorAction(id: string, action: 'reload' | 'start' | 'stop'): void {
    this.pendingConfirmation.set({
      title: `${action.toUpperCase()} Connector: ${id}`,
      message: `Confirm ${action} on connector "${id}"?`,
      execute: () => {
        const req$ =
          action === 'reload'
            ? this.api.reloadConnector(id)
            : action === 'start'
              ? this.api.startConnector(id)
              : this.api.stopConnector(id);
        req$.subscribe({
          next: () => {
            this.loadConnectorsAndBenchmarks();
            this.lastActionNotice.set(`Connector "${id}" ${action}ed`);
          },
        });
      },
    });
  }

  runBenchmark(suiteId: string): void {
    this.benchmarkRunning.set(suiteId);
    this.benchmarkResult.set(null);
    this.api.runBenchmark(suiteId).subscribe({
      next: (data) => {
        this.benchmarkResult.set(data);
        this.benchmarkRunning.set(null);
        this.lastActionNotice.set(`Benchmark "${suiteId}" completed in ${data?.durationMs ?? 0}ms`);
      },
      error: () => {
        this.benchmarkRunning.set(null);
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
}
