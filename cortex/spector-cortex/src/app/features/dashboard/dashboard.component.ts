import {
  Component,
  inject,
  OnInit,
  effect,
  ChangeDetectionStrategy,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { NeuralGraphComponent } from '../neural-graph/neural-graph.component';
import { VectorSpaceComponent } from '../vector-space/vector-space.component';
import { PipelineFunnelComponent } from '../pipeline-funnel/pipeline-funnel.component';
import { SimdPanelComponent } from '../simd-panel/simd-panel.component';
import { MemoryHeatmapComponent } from '../memory-heatmap/memory-heatmap.component';
import { ProfileRadarComponent } from '../profile-radar/profile-radar.component';
import { QueryInputComponent } from '../query-input/query-input.component';
import { QueryHistoryComponent } from '../query-history/query-history.component';
import { MetricsChartComponent } from '../metrics-chart/metrics-chart.component';
import { DecayCurveComponent } from '../decay-curve/decay-curve.component';
import { ZeigarnikTrackerComponent } from '../zeigarnik-tracker/zeigarnik-tracker.component';
import { HabituationMeterComponent } from '../habituation-meter/habituation-meter.component';
import { MemoryDiffComponent } from '../memory-diff/memory-diff.component';
import { GpuTimelineComponent } from '../gpu-timeline/gpu-timeline.component';
import { ClusterViewComponent } from '../cluster-view/cluster-view.component';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { LoggerService } from '../../core/services/logger.service';
import { MemoryTableService } from '../../core/services/memory-table.service';

@Component({
  selector: 'cortex-dashboard',
  imports: [
    MatCardModule,
    MatIconModule,
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

  constructor() {
    // Switch view mode based on the node selector dropdown
    effect(() => {
      const selected = this.state.selectedNode();
      this.state.viewMode.set(selected === 'cluster' ? 'cluster' : 'dashboard');
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

  private bootstrapTelemetry(): void {
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
