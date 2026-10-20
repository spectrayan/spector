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
import { GpuKernelEvent } from '@core/models/cortex-events';

const KERNEL_COLORS: Record<string, string> = {
  cosine_similarity: '#0a85ff',
  dot_product: '#22d3ee',
  euclidean_dist: '#38bdf8',
  hnsw_neighbor_select: '#f59e0b',
  memcpy_h2d: '#10b981',
  memcpy_d2h: '#60a5fa',
};

@Component({
  selector: 'cortex-gpu-timeline',
  templateUrl: './gpu-timeline.component.html',
  styleUrl: './gpu-timeline.component.scss',
})
export class GpuTimelineComponent implements AfterViewInit, OnDestroy {
  @ViewChild('timelineCanvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly state = inject(CortexStateService);
  private readonly themeService = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);

  private ctx!: CanvasRenderingContext2D;
  private animationId = 0;
  private resizeObserver?: ResizeObserver;

  constructor() {
    effect(() => {
      this.state.gpuKernels();
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
    const w = Math.max(0, parent.clientWidth);
    const h = Math.max(0, Math.min(parent.clientHeight, 240));
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

    const kernels = this.state.gpuKernels();
    const streamCount = this.state.gpuStreamCount();

    if (kernels.length === 0) {
      this.drawEmptyState(ctx, w, h);
      return;
    }

    const onSurface = this.themeService.getCanvasColor('--mat-sys-on-surface-variant', '#aaa');
    const outline = this.themeService.getCanvasColor('--mat-sys-outline-variant', '#555');

    const padding = { top: 28, right: 12, bottom: 30, left: 70 };
    const chartW = w - padding.left - padding.right;
    const laneHeight = Math.min(30, (h - padding.top - padding.bottom) / streamCount - 4);
    const laneGap = 4;

    // Time window: last 3 seconds
    const now = Date.now();
    const timeWindowMs = 3000;
    const timeStart = now - timeWindowMs;

    // Stream labels
    for (let s = 0; s < streamCount; s++) {
      const y = padding.top + s * (laneHeight + laneGap);
      ctx.fillStyle = onSurface;
      ctx.font = '10px "JetBrains Mono", monospace';
      ctx.textAlign = 'right';
      ctx.fillText(`Stream ${s}`, padding.left - 8, y + laneHeight / 2 + 4);

      // Lane background
      ctx.fillStyle = outline;
      ctx.globalAlpha = 0.15;
      ctx.fillRect(padding.left, y, chartW, laneHeight);
      ctx.globalAlpha = 1;
    }

    // Draw kernel bars
    const visibleKernels = kernels.filter((k) => k.timestamp >= timeStart);
    if (visibleKernels.length > 0) {
      this.animationId = requestAnimationFrame(() => this.draw());
    }

    for (const kernel of visibleKernels) {
      const x = padding.left + ((kernel.timestamp - timeStart) / timeWindowMs) * chartW;
      const barWidth = Math.max(2, (kernel.durationMicros / 1000 / timeWindowMs) * chartW);
      const y = padding.top + kernel.streamIndex * (laneHeight + laneGap);

      const color = KERNEL_COLORS[kernel.kernelName] || '#888';
      ctx.fillStyle = color;
      ctx.globalAlpha = 0.85;

      // Rounded bar
      const r = Math.min(3, barWidth / 2);
      ctx.beginPath();
      ctx.moveTo(x + r, y + 2);
      ctx.lineTo(x + barWidth - r, y + 2);
      ctx.quadraticCurveTo(x + barWidth, y + 2, x + barWidth, y + 2 + r);
      ctx.lineTo(x + barWidth, y + laneHeight - 2 - r);
      ctx.quadraticCurveTo(x + barWidth, y + laneHeight - 2, x + barWidth - r, y + laneHeight - 2);
      ctx.lineTo(x + r, y + laneHeight - 2);
      ctx.quadraticCurveTo(x, y + laneHeight - 2, x, y + laneHeight - 2 - r);
      ctx.lineTo(x, y + 2 + r);
      ctx.quadraticCurveTo(x, y + 2, x + r, y + 2);
      ctx.closePath();
      ctx.fill();
      ctx.globalAlpha = 1;

      // Kernel label (if bar is wide enough)
      if (barWidth > 40) {
        ctx.fillStyle = '#000';
        ctx.font = '8px "JetBrains Mono", monospace';
        ctx.textAlign = 'left';
        ctx.globalAlpha = 0.9;
        const shortName = kernel.kernelName.replace(/_/g, ' ');
        ctx.fillText(shortName, x + 3, y + laneHeight / 2 + 3);
        ctx.globalAlpha = 1;
      }
    }

    // Time axis
    ctx.strokeStyle = outline;
    ctx.lineWidth = 0.5;
    const axisY = padding.top + streamCount * (laneHeight + laneGap) + 8;
    ctx.beginPath();
    ctx.moveTo(padding.left, axisY);
    ctx.lineTo(w - padding.right, axisY);
    ctx.stroke();

    // Time ticks
    ctx.fillStyle = onSurface;
    ctx.font = '8px "JetBrains Mono", monospace';
    ctx.textAlign = 'center';
    for (let t = 0; t <= 6; t++) {
      const x = padding.left + (t / 6) * chartW;
      const timeLabel = `-${((((6 - t) / 6) * timeWindowMs) / 1000).toFixed(1)}s`;
      ctx.fillText(timeLabel, x, axisY + 12);
    }

    // Legend
    const legendY = h - 6;
    let legendX = padding.left;
    ctx.font = '8px Inter, sans-serif';
    const legendKernels = Object.entries(KERNEL_COLORS);
    for (const [name, color] of legendKernels) {
      if (legendX > w - 60) break;
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.arc(legendX + 4, legendY - 2, 3, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = onSurface;
      ctx.textAlign = 'left';
      const shortName = name.replace(/_/g, ' ');
      ctx.fillText(shortName, legendX + 10, legendY);
      legendX += ctx.measureText(shortName).width + 20;
    }
  }

  private drawEmptyState(ctx: CanvasRenderingContext2D, w: number, h: number): void {
    const onSurface = this.themeService.getCanvasColor('--mat-sys-on-surface-variant', '#aaa');
    ctx.fillStyle = onSurface;
    ctx.font = '12px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.globalAlpha = 0.5;
    ctx.fillText('Waiting for GPU kernel events...', w / 2, h / 2);
    ctx.globalAlpha = 1;
  }
}
