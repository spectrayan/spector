import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpClient } from '@angular/common/http';
import { interval, Subscription, startWith, switchMap, catchError, of } from 'rxjs';
import { environment } from '@env/environment';

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
  templateUrl: './maintenance-banner.component.html',
  styleUrl: './maintenance-banner.component.scss',
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
