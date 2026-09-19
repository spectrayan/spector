// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Neural Snackbar Component
// ═══════════════════════════════════════════════════════════════════════
// Custom themed snackbar with glassmorphic design, auto-dismiss progress
// bar, and neural/cortex iconography per message type.

import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MAT_SNACK_BAR_DATA, MatSnackBarRef } from '@angular/material/snack-bar';

export interface CortexSnackbarData {
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
  duration: number;
  icon?: string;
}

const TYPE_CONFIG: Record<string, { icon: string; class: string }> = {
  success: { icon: 'check_circle', class: 'cortex-snack-success' },
  error:   { icon: 'error_outline', class: 'cortex-snack-error' },
  info:    { icon: 'info', class: 'cortex-snack-info' },
  warning: { icon: 'warning_amber', class: 'cortex-snack-warning' },
};

@Component({
  selector: 'app-cortex-snackbar',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './cortex-snackbar.component.html',
  styleUrl: './cortex-snackbar.component.scss',
})
export class CortexSnackbarComponent {
  readonly icon: string;
  readonly typeClass: string;

  constructor(
    @Inject(MAT_SNACK_BAR_DATA) public data: CortexSnackbarData,
    private snackBarRef: MatSnackBarRef<CortexSnackbarComponent>,
  ) {
    const config = TYPE_CONFIG[data.type] ?? TYPE_CONFIG['info'];
    this.icon = data.icon ?? config.icon;
    this.typeClass = config.class;
  }

  dismiss(): void {
    this.snackBarRef.dismiss();
  }
}
