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
  templateUrl: './cortex-confirm-dialog.component.html',
  styleUrl: './cortex-confirm-dialog.component.scss',
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
