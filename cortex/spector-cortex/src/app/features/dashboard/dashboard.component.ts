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
  inject,
  OnInit,
  effect,
  ChangeDetectionStrategy,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { KpiRowComponent } from './components/kpi-row/kpi-row.component';
import { EventTickerComponent } from './components/event-ticker/event-ticker.component';
import { NeuralGraphComponent } from './components/neural-graph/neural-graph.component';
import { VectorSpaceComponent } from './components/vector-space/vector-space.component';
import { PipelineFunnelComponent } from '@shared/components/cognitive/pipeline-funnel/pipeline-funnel.component';
import { SimdPanelComponent } from './components/simd-panel/simd-panel.component';
import { MemoryHeatmapComponent } from './components/memory-heatmap/memory-heatmap.component';
import { ProfileRadarComponent } from './components/profile-radar/profile-radar.component';
import { QueryInputComponent } from '@shared/components/cognitive/query-input/query-input.component';
import { QueryHistoryComponent } from '@shared/components/cognitive/query-history/query-history.component';
import { MetricsChartComponent } from './components/metrics-chart/metrics-chart.component';
import { DecayCurveComponent } from './components/decay-curve/decay-curve.component';
import { ZeigarnikTrackerComponent } from './components/zeigarnik-tracker/zeigarnik-tracker.component';
import { HabituationMeterComponent } from './components/habituation-meter/habituation-meter.component';
import { MemoryDiffComponent } from './components/memory-diff/memory-diff.component';
import { GpuTimelineComponent } from './components/gpu-timeline/gpu-timeline.component';
import { ClusterViewComponent } from './components/cluster-view/cluster-view.component';
import { CortexStateService } from '@core/services/cortex-state.service';
import { LoggerService } from '@core/services/logger.service';
import { MemoryTableService } from '@core/services/memory-table.service';

@Component({
  selector: 'cortex-dashboard',
  imports: [
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatSnackBarModule,
    KpiRowComponent,
    EventTickerComponent,
    NeuralGraphComponent,
    VectorSpaceComponent,
    PipelineFunnelComponent,
    SimdPanelComponent,
    MemoryHeatmapComponent,
    ProfileRadarComponent,
    QueryInputComponent,
    QueryHistoryComponent,
    MetricsChartComponent,
    DecayCurveComponent,
    ZeigarnikTrackerComponent,
    HabituationMeterComponent,
    MemoryDiffComponent,
    GpuTimelineComponent,
    ClusterViewComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly state = inject(CortexStateService);
  private readonly log = inject(LoggerService);
  private readonly memoryService = inject(MemoryTableService);
  private readonly snackBar = inject(MatSnackBar);
  private lastLoadedNamespace: string | null = null;

  constructor() {
    // Switch view mode based on the node selector dropdown
    effect(() => {
      const selected = this.state.selectedNode();
      this.state.viewMode.set(selected === 'cluster' ? 'cluster' : 'dashboard');
    });

    // Reload telemetry when active namespace changes
    effect(() => {
      const ns = this.state.currentNamespace();
      if (this.lastLoadedNamespace !== null && this.lastLoadedNamespace !== ns && !this.state.useMockData()) {
        this.bootstrapTelemetry();
      }
    });
  }

  ngOnInit(): void {
    // Detect backend availability and bootstrap telemetry
    this.http.get<any>(`${environment.apiUrl}/system/status`).subscribe({
      next: () => {
        this.state.connectionStatus.set('connected');
        this.bootstrapTelemetry();
      },
      error: () => {
        this.log.warn('Dashboard', 'Backend unreachable - falling back to mock data simulation');
        this.state.connectionStatus.set('connected');
        this.state.useMockData.set(true);
      },
    });
  }

  setViewMode(mode: 'dashboard' | 'cluster'): void {
    this.state.viewMode.set(mode);
  }

  triggerDream(): void {
    this.http.post(`${environment.apiUrl}/memory/dream`, { namespace: this.state.currentNamespace() }).subscribe({
      next: () => {
        this.snackBar.open('Cognitive dream replay cycle initiated', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Dream cycle simulated (backend offline)', 'Close', { duration: 3000 });
      },
    });
  }

  triggerReflect(): void {
    this.memoryService.reflect().subscribe({
      next: () => {
        this.snackBar.open('Consolidation reflection completed', 'Close', { duration: 3000 });
        this.bootstrapTelemetry();
      },
      error: () => {
        this.snackBar.open('Consolidation reflection simulated', 'Close', { duration: 3000 });
      },
    });
  }

  triggerWander(): void {
    this.http.post(`${environment.apiUrl}/memory/wander`, { namespace: this.state.currentNamespace() }).subscribe({
      next: () => {
        this.snackBar.open('Associative wandering spreading activation started', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Associative wandering activated (simulation)', 'Close', { duration: 3000 });
      },
    });
  }

  private bootstrapTelemetry(): void {
    this.lastLoadedNamespace = this.state.currentNamespace();
    // 1. Initial Memory Diagnostic snapshot
    this.memoryService.getMemoryDiagnostics().subscribe({
      next: (diag) => this.state.memoryDiag.set(diag),
      error: (e) => this.log.debug('Dashboard', 'Diagnostics snapshot error:', e),
    });

    // 2. Calculated Mathematical Ebbinghaus & LTP Decay Curve
    this.memoryService.getDecayCurve().subscribe({
      next: (curve) => this.state.decayCurve.set(curve),
      error: (e) => this.log.debug('Dashboard', 'Decay curve fetch error:', e),
    });

    // 3. Consolidation Snapshot Diff
    this.memoryService.getConsolidationDiff().subscribe({
      next: (diffs) => {
        if (diffs && diffs.length > 0) {
          this.state.memoryDiffs.set(diffs as any);
        }
      },
      error: (e) => this.log.debug('Dashboard', 'Consolidation diff fetch error:', e),
    });

    // 4. Live Ops/Sec Rolling Metrics History
    this.memoryService.getLiveMetrics().subscribe({
      next: (history) => {
        if (history && history.length > 0) {
          this.state.metricsHistory.set(
            history.map((m) => ({
              timestamp: m.timestamp || Date.now(),
              recallRate: m.recallRate || 0,
              rememberRate: m.rememberRate || 0,
              reinforceRate: m.reinforceRate || 0,
              forgetRate: m.forgetRate || 0,
              avgLatencyMs: 0,
            }))
          );
        }
      },
      error: (e) => this.log.debug('Dashboard', 'Live metrics history fetch error:', e),
    });

    // 5. System Status (for Zeigarnik task count)
    this.memoryService.getStatus().subscribe({
      next: (st) => {
        if (st) {
          this.state.totalTaskCount.set(st.totalMemories);
        }
      },
      error: (e) => this.log.debug('Dashboard', 'Status fetch error:', e),
    });
  }
}
