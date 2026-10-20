import {
  Component,
  ElementRef,
  ViewChild,
  AfterViewInit,
  OnDestroy,
  inject,
  effect,
  PLATFORM_ID,
  ChangeDetectionStrategy,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CortexStateService } from '@core/services/cortex-state.service';
import { ThemeService } from '@core/services/theme.service';

@Component({
  selector: 'cortex-metrics-chart',
  templateUrl: './metrics-chart.component.html',
  styleUrl: './metrics-chart.component.scss',
})
export class MetricsChartComponent implements AfterViewInit, OnDestroy {
  @ViewChild('chartCanvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly state = inject(CortexStateService);
  private readonly themeService = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);

  private ctx!: CanvasRenderingContext2D;
  private animationId = 0;
  private resizeObserver?: ResizeObserver;

  constructor() {
    effect(() => {
      this.state.metricsHistory();
      this.themeService.isDark();
      this.scheduleDraw();
    });
  }

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.ctx = this.canvasRef.nativeElement.getContext('2d')!;
    this.resizeObserver = new ResizeObserver(() => this.resizeCanvas());
    this.resizeObserver.observe(this.canvasRef.nativeElement.parentElement!);
    this.resizeCanvas();
    this.scheduleDraw();
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationId);
    this.resizeObserver?.disconnect();
  }

  private scheduleDraw(): void {
    if (!this.ctx) return;
    cancelAnimationFrame(this.animationId);
    this.animationId = requestAnimationFrame(() => this.draw());
  }

  private resizeCanvas(): void {
    const parent = this.canvasRef.nativeElement.parentElement;
    if (!parent) return;
    const canvas = this.canvasRef.nativeElement;
    const w = Math.max(0, parent.clientWidth - 16);
    const h = Math.max(0, Math.min(parent.clientHeight - 8, 240));
    if (Math.abs(canvas.width - w) > 1 || Math.abs(canvas.height - h) > 1) {
      canvas.width = w;
      canvas.height = h;
      this.scheduleDraw();
    }
  }

  private draw(): void {
    if (!this.ctx) return;
    const canvas = this.canvasRef.nativeElement;
    const ctx = this.ctx;
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    const history = this.state.metricsHistory();
    if (history.length < 2) return;

    const primary = this.themeService.getCanvasColor('--primary', '#0a85ff');
    const tertiary = this.themeService.getCanvasColor('--accent-cyan', '#22d3ee');
    const secondary = this.themeService.getCanvasColor('--tier-procedural', '#10b981');
    const error = this.themeService.getCanvasColor('--mat-sys-error', '#ef4444');
    const outline = this.themeService.getCanvasColor('--border-subtle', '#1d283a');
    const onSurface = this.themeService.getCanvasColor('--foreground-muted', '#7f8ea3');

    const padding = { top: 10, right: 10, bottom: 20, left: 35 };
    const chartW = w - padding.left - padding.right;
    const chartH = h - padding.top - padding.bottom;

    // Find max across all series
    let maxVal = 1;
    for (const p of history) {
      maxVal = Math.max(maxVal, p.recallRate, p.rememberRate, p.reinforceRate, p.forgetRate);
    }
    maxVal *= 1.1;

    // Grid lines
    ctx.strokeStyle = outline;
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 4; i++) {
      const y = padding.top + (chartH / 4) * i;
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(w - padding.right, y);
      ctx.stroke();

      ctx.fillStyle = onSurface;
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.textAlign = 'right';
      ctx.fillText(((maxVal * (4 - i)) / 4).toFixed(1), padding.left - 4, y + 3);
    }

    // Draw series
    const series = [
      { key: 'recallRate' as const, color: primary, label: 'Recall' },
      { key: 'rememberRate' as const, color: tertiary, label: 'Remember' },
      { key: 'reinforceRate' as const, color: secondary, label: 'Reinforce' },
      { key: 'forgetRate' as const, color: error, label: 'Forget' },
    ];

    for (const s of series) {
      ctx.beginPath();
      ctx.strokeStyle = s.color;
      ctx.lineWidth = 1.5;
      ctx.globalAlpha = 0.8;

      for (let i = 0; i < history.length; i++) {
        const x = padding.left + (i / (history.length - 1)) * chartW;
        const y = padding.top + chartH - (history[i][s.key] / maxVal) * chartH;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
      ctx.globalAlpha = 1;
    }

    // Legend
    let legendX = padding.left;
    for (const s of series) {
      ctx.fillStyle = s.color;
      ctx.beginPath();
      ctx.arc(legendX + 4, h - 6, 3, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = onSurface;
      ctx.font = '9px Inter, sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(s.label, legendX + 10, h - 3);
      legendX += ctx.measureText(s.label).width + 20;
    }
  }
}
