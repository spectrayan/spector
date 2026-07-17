// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Neural Confirm Dialog
// ═══════════════════════════════════════════════════════════════════════
// Custom themed confirmation dialog matching Spector's neural aesthetic.
// Replaces window.confirm() and provides a consistent UX for dangerous
// or important actions.
//
// Usage:
//   CortexConfirmDialog.open(this.dialog, {
//     title: 'Forget Memory',
//     message: 'This will tombstone the memory.',
//     type: 'danger',
//   }).subscribe(confirmed => { if (confirmed) { ... } });

import { Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface CortexConfirmConfig {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  type?: 'danger' | 'warning' | 'info';
  icon?: string;
}

const TYPE_DEFAULTS: Record<string, { icon: string; confirmLabel: string }> = {
  danger:  { icon: 'warning', confirmLabel: 'Delete' },
  warning: { icon: 'warning_amber', confirmLabel: 'Continue' },
  info:    { icon: 'help_outline', confirmLabel: 'Confirm' },
};

@Component({
  selector: 'app-cortex-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="confirm-dialog" [ngClass]="'confirm-' + type">
      <div class="confirm-header">
        <div class="confirm-icon-wrapper">
          <mat-icon class="confirm-icon">{{ icon }}</mat-icon>
        </div>
        <h3 class="confirm-title">{{ config.title }}</h3>
      </div>
      <p class="confirm-message">{{ config.message }}</p>
      <div class="confirm-actions">
        <button mat-stroked-button class="cancel-btn" (click)="onCancel()">
          {{ config.cancelLabel || 'Cancel' }}
        </button>
        <button mat-flat-button class="confirm-btn" [ngClass]="'btn-' + type" (click)="onConfirm()">
          <mat-icon>{{ icon }}</mat-icon>
          {{ confirmLabel }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .confirm-dialog {
      padding: 24px;
      min-width: 360px;
      max-width: 440px;
    }

    .confirm-header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 16px;
    }

    .confirm-icon-wrapper {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .confirm-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .confirm-title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: var(--mat-sys-on-surface);
      line-height: 1.3;
    }

    .confirm-message {
      margin: 0 0 24px;
      font-size: 14px;
      line-height: 1.6;
      color: var(--mat-sys-on-surface-variant);
    }

    .confirm-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }

    .cancel-btn {
      border-radius: 10px;
      font-weight: 500;
    }

    .confirm-btn {
      border-radius: 10px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 4px;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }

    /* ── Type Variants ── */
    .confirm-danger {
      .confirm-icon-wrapper {
        background: rgba(255, 82, 82, 0.12);
      }
      .confirm-icon { color: #ff5252; }
      .btn-danger {
        background: #d32f2f !important;
        color: white !important;
        &:hover { background: #b71c1c !important; }
      }
    }

    .confirm-warning {
      .confirm-icon-wrapper {
        background: rgba(255, 193, 7, 0.12);
      }
      .confirm-icon { color: #ffc107; }
      .btn-warning {
        background: #f57f17 !important;
        color: white !important;
        &:hover { background: #e65100 !important; }
      }
    }

    .confirm-info {
      .confirm-icon-wrapper {
        background: rgba(0, 229, 255, 0.12);
      }
      .confirm-icon { color: #00e5ff; }
    }
  `],
})
export class CortexConfirmDialogComponent {
  readonly type: string;
  readonly icon: string;
  readonly confirmLabel: string;

  constructor(
    private dialogRef: MatDialogRef<CortexConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public config: CortexConfirmConfig,
  ) {
    this.type = config.type ?? 'info';
    const defaults = TYPE_DEFAULTS[this.type] ?? TYPE_DEFAULTS['info'];
    this.icon = config.icon ?? defaults.icon;
    this.confirmLabel = config.confirmLabel ?? defaults.confirmLabel;
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onConfirm(): void {
    this.dialogRef.close(true);
  }

  /**
   * Static helper for one-liner usage.
   *
   * @example
   * CortexConfirmDialog.open(this.dialog, {
   *   title: 'Forget Memory',
   *   message: 'This will tombstone the memory.',
   *   type: 'danger',
   *   confirmLabel: 'Forget',
   * }).subscribe(confirmed => { if (confirmed) doSomething(); });
   */
  static open(dialog: MatDialog, config: CortexConfirmConfig): Observable<boolean> {
    return dialog.open(CortexConfirmDialogComponent, {
      data: config,
      panelClass: 'cortex-confirm-panel',
      backdropClass: 'cortex-confirm-backdrop',
      autoFocus: false,
      restoreFocus: false,
    }).afterClosed().pipe(
      map(result => result === true),
    );
  }
}
