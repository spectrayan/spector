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
  inject,
  signal,
  input,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SynapseApiService } from '@core/services/synapse-api.service';

/**
 * Formats a byte count into a human-readable unit string (`B`, `KB`, `MB`, `GB`, `TB`).
 */
export function formatAdminBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes) || bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex++;
  }
  return `${unitIndex === 0 ? Math.round(value) : value.toFixed(1)} ${units[unitIndex]}`;
}

/**
 * Formats an ISO-8601 timestamp string into a concise UTC/local operational label.
 */
export function formatAdminTimestamp(iso?: string | null): string {
  if (!iso) {
    return 'Never';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
}

/**
 * Shared content-free Admin Console page header and card shell.
 */
@Component({
  selector: 'cortex-admin-section-shell',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="admin-page">
      <header class="admin-page-header">
        <div class="admin-title-group">
          <mat-icon class="admin-title-icon">{{ icon() }}</mat-icon>
          <div>
            <h1 class="admin-page-title">{{ title() }}</h1>
            <p class="admin-page-subtitle">{{ subtitle() }}</p>
          </div>
        </div>
        <span class="content-free-badge" title="Admin APIs never expose raw memory content">
          <mat-icon>verified_user</mat-icon>
          Content-Free Admin Surface
        </span>
      </header>
      <ng-content />
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
    .admin-page {
      padding: 28px 32px;
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .admin-page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }
    .admin-title-group {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .admin-title-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      color: var(--mat-sys-primary);
    }
    .admin-page-title {
      margin: 0;
      font-size: 1.375rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface);
    }
    .admin-page-subtitle {
      margin: 4px 0 0;
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .content-free-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 4px 12px;
      border-radius: 9999px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
    }
  `],
})
export class AdminSectionShellComponent {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly subtitle = input.required<string>();
}

@Component({
  selector: 'cortex-admin-analytics',
  standalone: true,
  imports: [MatCardModule, AdminSectionShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="insights"
      title="Analytics"
      subtitle="Content-free fleet, tenant, account, and namespace metrics."
    />
  `,
})
export class AdminAnalyticsComponent {}

@Component({
  selector: 'cortex-admin-keys',
  standalone: true,
  imports: [MatCardModule, AdminSectionShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="vpn_key"
      title="Keys & Encryption"
      subtitle="BYOK KMS configuration, envelope KEK/DEK status, and key rotation."
    />
  `,
})
export class AdminKeysComponent {}

@Component({
  selector: 'cortex-admin-audit',
  standalone: true,
  imports: [MatCardModule, AdminSectionShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="fact_check"
      title="Audit Log"
      subtitle="Tamper-evident SHA-256 hash-chained administrative audit log."
    />
  `,
})
export class AdminAuditComponent {}

@Component({
  selector: 'cortex-admin-break-glass',
  standalone: true,
  imports: [MatCardModule, AdminSectionShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="emergency"
      title="Break-Glass Access"
      subtitle="Time-boxed, dual-control emergency read-only access for non-BYOK tenants."
    />
  `,
})
export class AdminBreakGlassComponent {}

/**
 * Admin Infrastructure & Benchmarks page (`/admin/infra`).
 * Houses the benchmark suite runner migrated from the retired ControlCenterComponent.
 */
@Component({
  selector: 'cortex-admin-infra',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    AdminSectionShellComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cortex-admin-section-shell
      icon="dns"
      title="Infrastructure & Benchmarks"
      subtitle="Providers, system configuration, scheduler tasks, cache, and cognitive benchmarks."
    >
      <section class="infra-benchmarks">
        <h3 class="section-heading">
          <mat-icon>speed</mat-icon>
          <span>Benchmark Suites ({{ suites().length }})</span>
          <button mat-icon-button (click)="loadSuites()" matTooltip="Refresh suites">
            <mat-icon>refresh</mat-icon>
          </button>
        </h3>

        @if (suitesLoading()) {
          <div class="loading-row">
            <mat-spinner diameter="24" />
            <span>Loading benchmark suites...</span>
          </div>
        } @else {
          <div class="benchmark-grid">
            @for (suite of suites(); track suite.id) {
              <mat-card appearance="outlined" class="benchmark-card">
                <mat-card-content>
                  <div class="bench-header">
                    <mat-icon class="bench-icon">{{ getSuiteIcon(suite.id) }}</mat-icon>
                    <div>
                      <span class="bench-name">{{ suite.name }}</span>
                      <span class="bench-desc">{{ suite.description }}</span>
                    </div>
                  </div>
                  <span class="bench-est">~{{ suite.estimatedDurationSec }}s</span>
                </mat-card-content>
                <mat-card-actions>
                  <button
                    mat-flat-button
                    color="primary"
                    (click)="runBenchmark(suite.id)"
                    [disabled]="benchmarkRunning() !== null"
                  >
                    @if (benchmarkRunning() === suite.id) {
                      <mat-spinner diameter="20" />
                      Running...
                    } @else {
                      <mat-icon>play_arrow</mat-icon>
                      Run
                    }
                  </button>
                </mat-card-actions>
              </mat-card>
            }
          </div>
        }

        @if (benchmarkResult(); as result) {
          <mat-card appearance="outlined" class="result-card">
            <mat-card-header>
              <mat-icon mat-card-avatar>assessment</mat-icon>
              <mat-card-title>Results: {{ result.suiteId }}</mat-card-title>
              <mat-card-subtitle>Completed in {{ result.durationMs }}ms</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="results-grid">
                @for (entry of result.results | keyvalue; track entry.key) {
                  <div class="result-item">
                    <span class="result-key">{{ entry.key }}</span>
                    <span class="result-value">{{ entry.value ?? '—' }}</span>
                  </div>
                }
              </div>
            </mat-card-content>
          </mat-card>
        }
      </section>
    </cortex-admin-section-shell>
  `,
  styles: [`
    .section-heading {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 1rem;
      font-weight: 600;
      color: var(--mat-sys-on-surface);
      margin: 0 0 16px;
    }
    .loading-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .benchmark-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
    }
    .bench-header {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 8px;
    }
    .bench-icon {
      color: var(--mat-sys-primary);
    }
    .bench-name {
      display: block;
      font-weight: 600;
    }
    .bench-desc {
      display: block;
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
      margin-top: 2px;
    }
    .bench-est {
      font-size: 0.75rem;
      color: var(--mat-sys-outline);
      font-family: monospace;
    }
    .result-card {
      margin-top: 20px;
    }
    .results-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px;
      margin-top: 12px;
    }
    .result-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 12px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container);
      border: 1px solid var(--mat-sys-outline-variant);
    }
    .result-key {
      font-size: 0.7rem;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .result-value {
      font-size: 1.15rem;
      font-weight: 700;
      font-family: monospace;
    }
  `],
})
export class AdminInfraComponent {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  readonly suites = signal<any[]>([]);
  readonly suitesLoading = signal(true);
  readonly benchmarkRunning = signal<string | null>(null);
  readonly benchmarkResult = signal<any>(null);

  constructor() {
    this.loadSuites();
  }

  loadSuites(): void {
    this.suitesLoading.set(true);
    this.api.listBenchmarkSuites().subscribe({
      next: (data) => {
        this.suites.set(data.suites ?? []);
        this.suitesLoading.set(false);
      },
      error: () => this.suitesLoading.set(false),
    });
  }

  runBenchmark(suiteId: string): void {
    this.benchmarkRunning.set(suiteId);
    this.benchmarkResult.set(null);

    this.api.runBenchmark(suiteId).subscribe({
      next: (data) => {
        this.benchmarkResult.set(data);
        this.benchmarkRunning.set(null);
        this.snack.open(`Benchmark "${suiteId}" completed in ${data.durationMs}ms`, 'OK', {
          duration: 3000,
        });
      },
      error: () => {
        this.benchmarkRunning.set(null);
        this.snack.open('Benchmark failed', 'Dismiss', { duration: 5000 });
      },
    });
  }

  getSuiteIcon(id: string): string {
    switch (id) {
      case 'recall_accuracy':
        return 'target';
      case 'cognitive_overhead':
        return 'speed';
      case 'latency_percentiles':
        return 'timer';
      case 'memory_stats':
        return 'analytics';
      default:
        return 'science';
    }
  }
}
