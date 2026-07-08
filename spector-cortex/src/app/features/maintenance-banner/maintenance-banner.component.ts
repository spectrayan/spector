import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpClient } from '@angular/common/http';
import { interval, Subscription, startWith, switchMap, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * System status for maintenance mode, migration, upgrades.
 */
interface SystemStatus {
  mode: 'NORMAL' | 'MIGRATION' | 'MAINTENANCE' | 'UPGRADE';
  active: boolean;
  message: string | null;
  progress: number | null;
  estimatedRemainingMs: number | null;
  startedAt: string | null;
  details: string | null;
}

/**
 * Persistent top banner displayed during maintenance/migration.
 *
 * - Polls GET /api/v1/system/status every 5 seconds
 * - Shows progress bar with percentage
 * - Auto-hides when mode returns to NORMAL
 * - Shows success toast briefly after completion
 */
@Component({
  selector: 'cortex-maintenance-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressBarModule],
  template: `
    @if (isActive()) {
      <div class="maintenance-banner" [class]="'maintenance-banner--' + mode()">
        <div class="maintenance-banner__content">
          <mat-icon class="maintenance-banner__icon">{{ icon() }}</mat-icon>
          <div class="maintenance-banner__text">
            <span class="maintenance-banner__title">{{ title() }}</span>
            <span class="maintenance-banner__message">{{ message() }}</span>
          </div>
          <div class="maintenance-banner__progress-info">
            @if (progressPercent() !== null) {
              <span class="maintenance-banner__percent">{{ progressPercent() }}%</span>
            }
            @if (eta()) {
              <span class="maintenance-banner__eta">ETA: {{ eta() }}</span>
            }
          </div>
        </div>
        @if (progressPercent() !== null) {
          <mat-progress-bar
            mode="determinate"
            [value]="progressPercent()"
            class="maintenance-banner__bar">
          </mat-progress-bar>
        } @else {
          <mat-progress-bar
            mode="indeterminate"
            class="maintenance-banner__bar">
          </mat-progress-bar>
        }
        <div class="maintenance-banner__subtitle">
          Read-only mode: Browse and recall are available
        </div>
      </div>
    }

    @if (showCompletionToast()) {
      <div class="maintenance-toast maintenance-toast--success">
        <mat-icon>check_circle</mat-icon>
        <span>{{ completionMessage() }}</span>
      </div>
    }
  `,
  styles: [`
    .maintenance-banner {
      position: sticky;
      top: 0;
      z-index: 1100;
      padding: 12px 24px 4px;
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 100%);
      color: #fff;
      font-family: 'Inter', sans-serif;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .maintenance-banner--MIGRATION {
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 100%);
    }
    .maintenance-banner--MAINTENANCE {
      background: linear-gradient(135deg, #e65100 0%, #ff6d00 100%);
    }
    .maintenance-banner--UPGRADE {
      background: linear-gradient(135deg, #1b5e20 0%, #2e7d32 100%);
    }

    .maintenance-banner__content {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 8px;
    }

    .maintenance-banner__icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      opacity: 0.9;
      animation: pulse-icon 2s ease-in-out infinite;
    }

    @keyframes pulse-icon {
      0%, 100% { opacity: 0.7; }
      50% { opacity: 1; }
    }

    .maintenance-banner__text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .maintenance-banner__title {
      font-weight: 600;
      font-size: 14px;
      letter-spacing: 0.5px;
    }

    .maintenance-banner__message {
      font-size: 12px;
      opacity: 0.85;
    }

    .maintenance-banner__progress-info {
      display: flex;
      align-items: center;
      gap: 12px;
      font-variant-numeric: tabular-nums;
    }

    .maintenance-banner__percent {
      font-size: 20px;
      font-weight: 700;
      min-width: 48px;
      text-align: right;
    }

    .maintenance-banner__eta {
      font-size: 11px;
      opacity: 0.7;
      white-space: nowrap;
    }

    .maintenance-banner__bar {
      margin: 0 -24px;
      --mdc-linear-progress-active-indicator-color: rgba(255, 255, 255, 0.9);
      --mdc-linear-progress-track-color: rgba(255, 255, 255, 0.15);
    }

    .maintenance-banner__subtitle {
      font-size: 11px;
      opacity: 0.6;
      padding: 4px 0;
      text-align: center;
    }

    /* Success toast */
    .maintenance-toast {
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 1200;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px;
      border-radius: 8px;
      font-family: 'Inter', sans-serif;
      font-size: 14px;
      font-weight: 500;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
      animation: toast-in 0.3s ease-out;
    }

    .maintenance-toast--success {
      background: linear-gradient(135deg, #1b5e20, #2e7d32);
      color: #fff;
    }

    @keyframes toast-in {
      from { opacity: 0; transform: translateY(-20px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class MaintenanceBannerComponent implements OnInit, OnDestroy {

  private pollSub?: Subscription;
  private toastTimeout?: ReturnType<typeof setTimeout>;

  /** Current system status */
  readonly status = signal<SystemStatus | null>(null);
  readonly showCompletionToast = signal(false);
  readonly completionMessage = signal('');

  /** Whether the banner should be visible */
  readonly isActive = computed(() => {
    const s = this.status();
    return s !== null && s.active && s.mode !== 'NORMAL';
  });

  readonly mode = computed(() => this.status()?.mode ?? 'NORMAL');

  readonly icon = computed(() => {
    switch (this.status()?.mode) {
      case 'MIGRATION': return 'psychology';
      case 'MAINTENANCE': return 'build';
      case 'UPGRADE': return 'system_update';
      default: return 'info';
    }
  });

  readonly title = computed(() => {
    switch (this.status()?.mode) {
      case 'MIGRATION': return '🧠 Neural Upgrade in Progress';
      case 'MAINTENANCE': return '🔧 System Maintenance';
      case 'UPGRADE': return '⬆️ System Upgrade';
      default: return 'System Status';
    }
  });

  readonly message = computed(() => this.status()?.message ?? '');

  readonly progressPercent = computed(() => {
    const p = this.status()?.progress;
    return p !== null && p !== undefined ? Math.round(p * 100) : null;
  });

  readonly eta = computed(() => {
    const ms = this.status()?.estimatedRemainingMs;
    if (!ms || ms <= 0) return null;
    if (ms < 60_000) return `~${Math.ceil(ms / 1000)}s`;
    return `~${Math.ceil(ms / 60_000)} min`;
  });

  private wasActive = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    // TEMP: Status polling disabled during Spring migration testing
    // TODO: Re-enable once /api/v1/system/status endpoint is verified stable
    return;
    // Poll every 5 seconds
    this.pollSub = interval(5000).pipe(
      startWith(0),
      switchMap(() =>
        this.http.get<SystemStatus>(`${environment.apiUrl}/system/status`).pipe(
          catchError(() => of(null))
        )
      )
    ).subscribe(status => {
      if (status) {
        this.status.set(status);

        // Detect transition from active → normal (completion)
        const nowActive = status.active && status.mode !== 'NORMAL';
        if (this.wasActive && !nowActive) {
          this.showSuccessToast();
        }
        this.wasActive = nowActive;
      }
    });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
  }

  private showSuccessToast(): void {
    this.completionMessage.set('✅ Operation completed successfully');
    this.showCompletionToast.set(true);
    this.toastTimeout = setTimeout(() => {
      this.showCompletionToast.set(false);
    }, 5000);
  }
}
