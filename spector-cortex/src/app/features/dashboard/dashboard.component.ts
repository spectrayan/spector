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

  constructor() {
    // Switch view mode based on the node selector dropdown
    effect(() => {
      const selected = this.state.selectedNode();
      this.state.viewMode.set(selected === 'cluster' ? 'cluster' : 'dashboard');
    });
  }

  ngOnInit(): void {
    // Detect backend availability and set connection status
    this.http.get<any>(`${environment.apiUrl}/system/status`).subscribe({
      next: () => {
        this.state.connectionStatus.set('connected');
      },
      error: () => {
        this.log.warn('Dashboard', 'Backend unreachable - falling back to mock data simulation');
        this.state.connectionStatus.set('connected');
        this.state.useMockData.set(true);
      },
    });
  }
}
