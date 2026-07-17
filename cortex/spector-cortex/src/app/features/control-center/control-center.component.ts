import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SynapseApiService } from '../../core/services/synapse-api.service';

@Component({
  selector: 'cortex-control-center',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatChipsModule,
    MatListModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './control-center.component.html',
  styleUrl: './control-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlCenterComponent {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  // ── Agents ──
  readonly agents = signal<any[]>([]);
  readonly agentsLoading = signal(true);
  readonly agentName = signal('');
  readonly agentScopes = signal('*');
  readonly agentDescription = signal('');
  readonly registering = signal(false);

  // ── Workspaces ──
  readonly workspaces = signal<any[]>([]);
  readonly workspacesLoading = signal(true);
  readonly wsName = signal('');
  readonly wsDescription = signal('');
  readonly creatingWs = signal(false);

  // ── Benchmarks ──
  readonly suites = signal<any[]>([]);
  readonly suitesLoading = signal(true);
  readonly benchmarkRunning = signal<string | null>(null);
  readonly benchmarkResult = signal<any>(null);

  constructor() {
    this.loadAgents();
    this.loadWorkspaces();
    this.loadSuites();
  }

  // ── Agent Methods ──

  loadAgents(): void {
    this.agentsLoading.set(true);
    this.api.listAgents().subscribe({
      next: (data) => {
        this.agents.set(data.agents ?? []);
        this.agentsLoading.set(false);
      },
      error: () => this.agentsLoading.set(false),
    });
  }

  registerAgent(): void {
    const name = this.agentName().trim();
    if (!name) return;

    this.registering.set(true);
    const scopes = this.agentScopes().split(',').map(s => s.trim()).filter(Boolean);

    this.api.registerAgent(name, this.agentDescription(), scopes).subscribe({
      next: () => {
        this.registering.set(false);
        this.agentName.set('');
        this.agentScopes.set('*');
        this.agentDescription.set('');
        this.loadAgents();
        this.snack.open(`Agent "${name}" registered`, 'OK', { duration: 3000 });
      },
      error: () => {
        this.registering.set(false);
        this.snack.open('Registration failed', 'Dismiss', { duration: 5000 });
      },
    });
  }

  // ── Workspace Methods ──

  loadWorkspaces(): void {
    this.workspacesLoading.set(true);
    this.api.listWorkspaces().subscribe({
      next: (data) => {
        this.workspaces.set(data.workspaces ?? []);
        this.workspacesLoading.set(false);
      },
      error: () => this.workspacesLoading.set(false),
    });
  }

  createWorkspace(): void {
    const name = this.wsName().trim();
    if (!name) return;

    this.creatingWs.set(true);
    this.api.createWorkspace(name, this.wsDescription()).subscribe({
      next: () => {
        this.creatingWs.set(false);
        this.wsName.set('');
        this.wsDescription.set('');
        this.loadWorkspaces();
        this.snack.open(`Workspace "${name}" created`, 'OK', { duration: 3000 });
      },
      error: () => {
        this.creatingWs.set(false);
        this.snack.open('Creation failed', 'Dismiss', { duration: 5000 });
      },
    });
  }

  // ── Benchmark Methods ──

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
        this.snack.open(`Benchmark "${suiteId}" completed in ${data.durationMs}ms`, 'OK', { duration: 3000 });
      },
      error: () => {
        this.benchmarkRunning.set(null);
        this.snack.open('Benchmark failed', 'Dismiss', { duration: 5000 });
      },
    });
  }

  formatTimestamp(ts: string): string {
    return new Date(ts).toLocaleString();
  }

  getSuiteIcon(id: string): string {
    switch (id) {
      case 'recall_accuracy': return 'target';
      case 'cognitive_overhead': return 'speed';
      case 'latency_percentiles': return 'timer';
      case 'memory_stats': return 'analytics';
      default: return 'science';
    }
  }
}
