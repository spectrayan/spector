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
  input,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';

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

