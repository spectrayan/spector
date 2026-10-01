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
  OnDestroy,
  OnChanges,
  AfterViewInit,
  ElementRef,
  ViewChild,
  input,
  signal,
  PLATFORM_ID,
  inject,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatSliderModule } from '@angular/material/slider';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Chart, Plugin, registerables } from 'chart.js';
import { MemoryStats } from '../../../../core/services/memory-table.service';

Chart.register(...registerables);

/**
 * Base stability constants per memory tier (in days).
 * These model how quickly memories decay without reinforcement,
 * inspired by Ebbinghaus forgetting curve: R(t) = e^(-t / S).
 */
const TIER_STABILITY: Record<string, number> = {
  WORKING: 3,
  EPISODIC: 14,
  SEMANTIC: 90,
  PROCEDURAL: 365,
};

/** Tier labels and their display colors. */
const TIER_CONFIG = [
  { key: 'WORKING', label: 'Working', color: '#c084fc', fillColor: 'rgba(192, 132, 252, 0.4)' },
  { key: 'EPISODIC', label: 'Episodic', color: '#2dd4bf', fillColor: 'rgba(45, 212, 191, 0.4)' },
  { key: 'SEMANTIC', label: 'Semantic', color: '#818cf8', fillColor: 'rgba(129, 140, 248, 0.4)' },
  { key: 'PROCEDURAL', label: 'Procedural', color: '#f87171', fillColor: 'rgba(248, 113, 113, 0.4)' },
];

/** Time horizons for the forecast projection. */
const TIME_HORIZONS = [
  { label: 'Now', days: 0 },
  { label: '+7d', days: 7 },
  { label: '+14d', days: 14 },
  { label: '+30d', days: 30 },
  { label: '+90d', days: 90 },
];

/**
 * Inline Chart.js plugin that draws a horizontal dashed "danger zone" line.
 * Avoids requiring chartjs-plugin-annotation as an npm dependency.
 */
function createDangerLinePlugin(getThreshold: () => number): Plugin {
  return {
    id: 'dangerLine',
    afterDraw(chart) {
      const threshold = getThreshold();
      const yScale = chart.scales['y'];
      if (!yScale) return;

      const yPixel = yScale.getPixelForValue(threshold);
      if (yPixel < yScale.top || yPixel > yScale.bottom) return;

      const ctx = chart.ctx;
      ctx.save();

      // Dashed line
      ctx.strokeStyle = 'rgba(239, 68, 68, 0.6)';
      ctx.lineWidth = 2;
      ctx.setLineDash([6, 4]);
      ctx.beginPath();
      ctx.moveTo(chart.chartArea.left, yPixel);
      ctx.lineTo(chart.chartArea.right, yPixel);
      ctx.stroke();

      // Label
      ctx.fillStyle = 'rgba(239, 68, 68, 0.8)';
      ctx.font = 'bold 10px Inter, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText('Danger Zone', chart.chartArea.right - 4, yPixel - 6);

      ctx.restore();
    },
  };
}

@Component({
  selector: 'cortex-decay-forecast',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatSliderModule,
    MatSlideToggleModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './decay-forecast.component.html',
  styleUrl: './decay-forecast.component.scss',
})
export class DecayForecastComponent implements OnChanges, OnDestroy, AfterViewInit {
  @ViewChild('forecastCanvas') private forecastCanvas!: ElementRef<HTMLCanvasElement>;

  /** Current memory stats from the health dashboard poll. */
  readonly stats = input<MemoryStats | null>(null);

  /** Decay rate multiplier (0.5 = slow decay, 2.0 = fast decay). */
  readonly decayRateMultiplier = signal(1.0);

  /** Whether the what-if scenario (daily new memories) is enabled. */
  readonly whatIfEnabled = signal(false);

  /** Number of new memories added per day in what-if scenario. */
  readonly whatIfDailyCount = signal(5);

  /** Danger zone threshold — warn when total projected memories drop below. */
  readonly dangerThreshold = signal(50);

  /** Projected total at the 90-day horizon (for display in template). */
  readonly projectedTotal90d = signal(0);

  /** Whether the projection is below the danger threshold at 90d. */
  readonly inDangerZone = signal(false);

  private readonly platformId = inject(PLATFORM_ID);
  private chart?: Chart;

  /** Display formatter for the decay rate slider thumb. */
  formatDecayRate(value: number): string {
    return `${value.toFixed(1)}×`;
  }

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.initChart();
    this.recompute();
  }

  ngOnChanges(): void {
    this.recompute();
  }

  ngOnDestroy(): void {
    if (this.chart) {
      this.chart.destroy();
    }
  }

  /** Called when the decay rate slider changes. */
  onDecayRateChange(value: number): void {
    this.decayRateMultiplier.set(value);
    this.recompute();
  }

  /** Called when the what-if toggle changes. */
  onWhatIfToggle(enabled: boolean): void {
    this.whatIfEnabled.set(enabled);
    this.recompute();
  }

  /** Called when the what-if daily count changes. */
  onWhatIfCountChange(value: number): void {
    this.whatIfDailyCount.set(value);
    this.recompute();
  }

  /** Called when the danger threshold slider changes. */
  onDangerThresholdChange(value: number): void {
    this.dangerThreshold.set(value);
    this.recompute();
  }

  /**
   * Compute the Ebbinghaus decay projection for each tier across time horizons.
   *
   * Formula per tier: projected(t) = current × e^(-t / (S × multiplier))
   * When what-if is enabled, new memories are added daily and also decay.
   */
  private recompute(): void {
    const data = this.stats();
    if (!data || !this.chart) return;

    const multiplier = this.decayRateMultiplier();
    const whatIf = this.whatIfEnabled();
    const dailyNew = this.whatIfDailyCount();

    // Compute projections per tier per time horizon
    const tierProjections: number[][] = TIER_CONFIG.map((tier) => {
      const currentCount = data.tierDistribution[tier.key] || 0;
      const stability = TIER_STABILITY[tier.key] * multiplier;

      return TIME_HORIZONS.map((horizon) => {
        // Base decay of existing memories
        let projected = currentCount * Math.exp(-horizon.days / stability);

        // What-if: add daily new memories (all enter as WORKING initially,
        // but for simplicity we model them decaying within their current tier)
        if (whatIf && tier.key === 'WORKING') {
          for (let day = 1; day <= horizon.days; day++) {
            const age = horizon.days - day;
            projected += dailyNew * Math.exp(-age / stability);
          }
        }

        return Math.round(projected);
      });
    });

    // Update chart datasets
    TIER_CONFIG.forEach((_, i) => {
      if (this.chart?.data.datasets[i]) {
        this.chart.data.datasets[i].data = tierProjections[i];
      }
    });

    // Compute totals at 90d for status display
    const totals90d = tierProjections.reduce((sum, tier) => sum + tier[tier.length - 1], 0);
    this.projectedTotal90d.set(totals90d);
    this.inDangerZone.set(totals90d < this.dangerThreshold());

    this.chart.update();
  }

  private initChart(): void {
    const isDark = true;
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.06)';
    const textColor = isDark ? '#b0b3b8' : '#4a4a4a';

    const datasets = TIER_CONFIG.map((tier) => ({
      label: tier.label,
      data: [0, 0, 0, 0, 0],
      borderColor: tier.color,
      backgroundColor: tier.fillColor,
      fill: true,
      tension: 0.3,
      pointRadius: 4,
      pointHoverRadius: 6,
    }));

    this.chart = new Chart(this.forecastCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: TIME_HORIZONS.map((h) => h.label),
        datasets,
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false,
        },
        scales: {
          x: {
            grid: { color: gridColor },
            ticks: { color: textColor, font: { size: 11 } },
          },
          y: {
            stacked: true,
            grid: { color: gridColor },
            ticks: { color: textColor, font: { size: 11 } },
            beginAtZero: true,
          },
        },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: textColor, boxWidth: 12, padding: 16 },
          },
          tooltip: {
            mode: 'index',
            intersect: false,
            callbacks: {
              footer: (items) => {
                const total = items.reduce((sum, item) => sum + (item.parsed.y || 0), 0);
                return `Total: ${total} memories`;
              },
            },
          },
        },
      },
      plugins: [createDangerLinePlugin(() => this.dangerThreshold())],
    });
  }
}
