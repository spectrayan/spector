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
  template: `
    <div class="cortex-snackbar" [ngClass]="typeClass">
      <div class="snack-glow"></div>
      <div class="snack-content">
        <mat-icon class="snack-icon">{{ icon }}</mat-icon>
        <span class="snack-message">{{ data.message }}</span>
        <button class="snack-close" (click)="dismiss()">
          <mat-icon>close</mat-icon>
        </button>
      </div>
      <div class="snack-progress">
        <div class="snack-progress-bar"
             [style.animation-duration.ms]="data.duration"></div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .cortex-snackbar {
      position: relative;
      overflow: hidden;
      border-radius: 12px;
      backdrop-filter: blur(20px) saturate(1.8);
      background: rgba(18, 18, 24, 0.92);
      border: 1px solid rgba(255, 255, 255, 0.08);
      box-shadow:
        0 8px 32px rgba(0, 0, 0, 0.4),
        inset 0 1px 0 rgba(255, 255, 255, 0.05);
      animation: snack-slide-up 0.35s cubic-bezier(0.2, 0.8, 0.2, 1);
    }

    .snack-glow {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 1px;
      opacity: 0.6;
    }

    .snack-content {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 14px;
    }

    .snack-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    .snack-message {
      flex: 1;
      font-size: 13px;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.92);
      line-height: 1.4;
      letter-spacing: 0.01em;
    }

    .snack-close {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s ease;
      flex-shrink: 0;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: rgba(255, 255, 255, 0.5);
      }
    }

    .snack-progress {
      height: 2px;
      background: rgba(255, 255, 255, 0.05);
      overflow: hidden;
    }

    .snack-progress-bar {
      height: 100%;
      width: 100%;
      transform-origin: left;
      animation: snack-progress-shrink linear forwards;
    }

    /* ── Type Variants ── */
    .cortex-snack-success {
      .snack-glow { background: linear-gradient(90deg, transparent, #00e676, transparent); }
      .snack-icon { color: #00e676; }
      .snack-progress-bar { background: #00e676; }
      border-color: rgba(0, 230, 118, 0.15);
    }

    .cortex-snack-error {
      .snack-glow { background: linear-gradient(90deg, transparent, #ff5252, transparent); }
      .snack-icon { color: #ff5252; }
      .snack-progress-bar { background: #ff5252; }
      border-color: rgba(255, 82, 82, 0.15);
    }

    .cortex-snack-info {
      .snack-glow { background: linear-gradient(90deg, transparent, #00e5ff, transparent); }
      .snack-icon { color: #00e5ff; }
      .snack-progress-bar { background: #00e5ff; }
      border-color: rgba(0, 229, 255, 0.15);
    }

    .cortex-snack-warning {
      .snack-glow { background: linear-gradient(90deg, transparent, #ffc107, transparent); }
      .snack-icon { color: #ffc107; }
      .snack-progress-bar { background: #ffc107; }
      border-color: rgba(255, 193, 7, 0.15);
    }

    /* ── Animations ── */
    @keyframes snack-slide-up {
      from {
        opacity: 0;
        transform: translateY(16px) scale(0.96);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    @keyframes snack-progress-shrink {
      from { transform: scaleX(1); }
      to { transform: scaleX(0); }
    }
  `],
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
