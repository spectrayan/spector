// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Neural Snackbar Service
// ═══════════════════════════════════════════════════════════════════════
// Typed wrapper around MatSnackBar that opens the custom CortexSnackbar
// component with proper configuration.
//
// Usage:
//   private readonly toast = inject(CortexSnackbarService);
//   this.toast.success('Profile saved');
//   this.toast.error('Connection failed');

import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CortexSnackbarComponent, CortexSnackbarData } from '../components/cortex-snackbar/cortex-snackbar.component';

@Injectable({ providedIn: 'root' })
export class CortexSnackbarService {
  private readonly snackBar = inject(MatSnackBar);

  /** Show a success toast (green glow, check icon). */
  success(message: string, duration = 3000): void {
    this.show({ message, type: 'success', duration });
  }

  /** Show an error toast (red glow, error icon). */
  error(message: string, duration = 5000): void {
    this.show({ message, type: 'error', duration });
  }

  /** Show an info toast (cyan glow, info icon). */
  info(message: string, duration = 4000): void {
    this.show({ message, type: 'info', duration });
  }

  /** Show a warning toast (amber glow, warning icon). */
  warn(message: string, duration = 4000): void {
    this.show({ message, type: 'warning', duration });
  }

  private show(data: CortexSnackbarData): void {
    this.snackBar.openFromComponent(CortexSnackbarComponent, {
      data,
      duration: data.duration,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: ['cortex-snackbar-panel'],
    });
  }
}
