import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ElementRef,
  ViewChild,
  inject,
  signal,
  PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { MemoryTableService, MemoryStats, ScoringStats } from '../../core/services/memory-table.service';

Chart.register(...registerables);

@Component({
  selector: 'cortex-health',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './health.component.html',
  styleUrl: './health.component.scss',
})
export class HealthComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('tierCanvas') private tierCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('growthCanvas') private growthCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('decayCanvas') private decayCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('scoringCanvas') private scoringCanvas!: ElementRef<HTMLCanvasElement>;

  private readonly memoryService = inject(MemoryTableService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly stats = signal<MemoryStats | null>(null);
  readonly scoring = signal<ScoringStats | null>(null);
  readonly loading = signal(true);
  readonly consolidating = signal(false);

  private pollSubscription!: Subscription;
  private tierChart?: Chart;
  private growthChart?: Chart;
  private decayChart?: Chart;
  private scoringChart?: Chart;

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    // Start 5-second polling loop
    this.pollSubscription = interval(5000)
      .pipe(
        startWith(0),
        switchMap(() => this.memoryService.getMemoryStats())
      )
      .subscribe({
        next: (data) => {
          this.stats.set(data);
          this.updateCharts();
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Failed to poll memory stats', err);
          this.loading.set(false);
        },
      });

    // Also fetch scoring stats
    this.memoryService.getScoringStats().subscribe({
      next: (data) => {
        this.scoring.set(data);
        this.updateScoringChart();
      },
      error: (err) => console.error('Failed to get scoring stats', err),
    });
  }

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.initCharts();
  }

  ngOnDestroy(): void {
    if (this.pollSubscription) {
      this.pollSubscription.unsubscribe();
    }
    this.destroyCharts();
  }

  triggerConsolidation(): void {
    this.consolidating.set(true);
    this.memoryService.consolidate().subscribe({
      next: () => {
        // Reload stats
        this.memoryService.getMemoryStats().subscribe((data) => this.stats.set(data));
        this.consolidating.set(false);
      },
      error: (err) => {
        console.error('Consolidation failed', err);
        this.consolidating.set(false);
      },
    });
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  formatLastConsolidation(timestamp: number): string {
    if (timestamp === 0) return 'Never';
    return new Date(timestamp).toLocaleTimeString();
  }

  private initCharts(): void {
    const isDark = document.body.classList.contains('dark-theme') || true;
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.06)';
    const textColor = isDark ? '#b0b3b8' : '#4a4a4a';

    // 1. Tier Distribution (Donut)
    this.tierChart = new Chart(this.tierCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: ['Working', 'Episodic', 'Semantic', 'Procedural'],
        datasets: [{
          data: [0, 0, 0, 0],
          backgroundColor: ['#c084fc', '#2dd4bf', '#818cf8', '#f87171'],
          borderWidth: 0,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { color: textColor, boxWidth: 12 } },
        },
      },
    });

    // 2. Memory Growth (Line)
    this.growthChart = new Chart(this.growthCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'Total Memories',
          data: [],
          borderColor: '#818cf8',
          backgroundColor: 'rgba(129, 140, 248, 0.1)',
          fill: true,
          tension: 0.3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: { grid: { color: gridColor }, ticks: { color: textColor } },
          y: { grid: { color: gridColor }, ticks: { color: textColor } },
        },
        plugins: {
          legend: { display: false },
        },
      },
    });

    // 3. Decay Projection (Line/Area)
    this.decayChart = new Chart(this.decayCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: ['Current', '7 Days Proj', '30 Days Proj'],
        datasets: [{
          label: 'Projected Retention',
          data: [0, 0, 0],
          borderColor: '#f87171',
          backgroundColor: 'rgba(248, 113, 113, 0.1)',
          fill: true,
          tension: 0.3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: { grid: { color: gridColor }, ticks: { color: textColor } },
          y: { grid: { color: gridColor }, ticks: { color: textColor } },
        },
        plugins: {
          legend: { display: false },
        },
      },
    });

    // 4. Scoring Radar
    this.scoringChart = new Chart(this.scoringCanvas.nativeElement, {
      type: 'radar',
      data: {
        labels: ['Similarity', 'Recency', 'Frequency', 'Importance', 'Valence'],
        datasets: [{
          label: 'Weights',
          data: [0, 0, 0, 0, 0],
          borderColor: '#2dd4bf',
          backgroundColor: 'rgba(45, 212, 191, 0.2)',
          pointBackgroundColor: '#2dd4bf',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          r: {
            angleLines: { color: gridColor },
            grid: { color: gridColor },
            pointLabels: { color: textColor, font: { size: 10 } },
            ticks: { display: false },
            suggestedMin: 0,
            suggestedMax: 1,
          },
        },
        plugins: {
          legend: { display: false },
        },
      },
    });

    this.updateCharts();
    this.updateScoringChart();
  }

  private updateCharts(): void {
    const data = this.stats();
    if (!data) return;

    // Donut Update
    if (this.tierChart) {
      const counts = [
        data.tierDistribution['WORKING'] || 0,
        data.tierDistribution['EPISODIC'] || 0,
        data.tierDistribution['SEMANTIC'] || 0,
        data.tierDistribution['PROCEDURAL'] || 0,
      ];
      this.tierChart.data.datasets[0].data = counts;
      this.tierChart.update();
    }

    // Line Update (Growth)
    if (this.growthChart && data.growthOverTime) {
      const sortedKeys = Object.keys(data.growthOverTime).sort();
      this.growthChart.data.labels = sortedKeys.map(k => k.substring(5)); // just MM-DD
      this.growthChart.data.datasets[0].data = sortedKeys.map(k => data.growthOverTime[k]);
      this.growthChart.update();
    }

    // Decay Update
    if (this.decayChart && data.decayForecast) {
      const forecast = [
        data.decayForecast['current'] || 0,
        data.decayForecast['day7'] || 0,
        data.decayForecast['day30'] || 0,
      ];
      this.decayChart.data.datasets[0].data = forecast;
      this.decayChart.update();
    }
  }

  private updateScoringChart(): void {
    const sc = this.scoring();
    if (!sc || !this.scoringChart) return;

    // Normalizations:
    // Similarity: raw (0.0 to 1.0)
    // Recency: raw (0.0 to 1.0)
    // Frequency: avg / 5.0 capped at 1.0
    // Importance: base is 0 to 10, so avg / 10.0
    // Valence: maps -128..127 to 0..1 -> (valence + 128.0)/255.0
    const values = [
      sc.avgSimilarity,
      sc.avgRecency,
      Math.min(1.0, sc.avgFrequency / 5.0),
      Math.min(1.0, sc.avgImportance / 10.0),
      (sc.avgValence + 128.0) / 255.0,
    ];

    this.scoringChart.data.datasets[0].data = values;
    this.scoringChart.update();
  }

  private destroyCharts(): void {
    if (this.tierChart) this.tierChart.destroy();
    if (this.growthChart) this.growthChart.destroy();
    if (this.decayChart) this.decayChart.destroy();
    if (this.scoringChart) this.scoringChart.destroy();
  }
}
