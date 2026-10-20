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
  selector: 'cortex-decay-curve',
  templateUrl: './decay-curve.component.html',
  styleUrl: './decay-curve.component.scss',
})
export class DecayCurveComponent implements AfterViewInit, OnDestroy {
  @ViewChild('decayCanvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly state = inject(CortexStateService);
  private readonly themeService = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);

  private ctx!: CanvasRenderingContext2D;
  private animationId = 0;
  private resizeObserver?: ResizeObserver;

  constructor() {
    effect(() => {
      this.state.decayCurve();
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

    const curve = this.state.decayCurve();
    if (curve.length < 2) return;

    const error = this.themeService.getCanvasColor('--mat-sys-error', '#ef4444');
    const primary = this.themeService.getCanvasColor('--primary', '#0a85ff');
    const outline = this.themeService.getCanvasColor('--border-subtle', '#1d283a');
    const onSurface = this.themeService.getCanvasColor('--foreground-muted', '#7f8ea3');

    const pad = { top: 10, right: 10, bottom: 24, left: 35 };
    const cw = w - pad.left - pad.right;
    const ch = h - pad.top - pad.bottom;

    // Grid
    ctx.strokeStyle = outline;
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 4; i++) {
      const y = pad.top + (ch / 4) * i;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(w - pad.right, y);
      ctx.stroke();
      ctx.fillStyle = onSurface;
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.textAlign = 'right';
      ctx.fillText(((100 * (4 - i)) / 4).toFixed(0) + '%', pad.left - 4, y + 3);
    }

    // X-axis labels
    ctx.fillStyle = onSurface;
    ctx.font = '9px Inter, sans-serif';
    ctx.textAlign = 'center';
    for (let d = 0; d <= 30; d += 5) {
      const x = pad.left + (d / 30) * cw;
      ctx.fillText(d + 'd', x, h - 4);
    }

    // Raw Ebbinghaus curve (red, dashed)
    ctx.beginPath();
    ctx.setLineDash([4, 3]);
    ctx.strokeStyle = error;
    ctx.lineWidth = 1.5;
    ctx.globalAlpha = 0.6;
    for (let i = 0; i < curve.length; i++) {
      const x = pad.left + (curve[i].ageDays / 30) * cw;
      const y = pad.top + ch - curve[i].rawDecay * ch;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;

    // LTP reconsolidation curve (primary, solid)
    ctx.beginPath();
    ctx.strokeStyle = primary;
    ctx.lineWidth = 2;
    for (let i = 0; i < curve.length; i++) {
      const x = pad.left + (curve[i].ageDays / 30) * cw;
      const y = pad.top + ch - curve[i].ltpDecay * ch;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Fill under LTP curve
    ctx.lineTo(pad.left + cw, pad.top + ch);
    ctx.lineTo(pad.left, pad.top + ch);
    ctx.closePath();
    ctx.fillStyle = primary;
    ctx.globalAlpha = 0.08;
    ctx.fill();
    ctx.globalAlpha = 1;

    // Legend
    ctx.setLineDash([4, 3]);
    ctx.strokeStyle = error;
    ctx.lineWidth = 1.5;
    ctx.globalAlpha = 0.6;
    ctx.beginPath();
    ctx.moveTo(pad.left, h - 8);
    ctx.lineTo(pad.left + 15, h - 8);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
    ctx.fillStyle = onSurface;
    ctx.font = '9px Inter, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('Ebbinghaus', pad.left + 18, h - 5);

    ctx.strokeStyle = primary;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(pad.left + 80, h - 8);
    ctx.lineTo(pad.left + 95, h - 8);
    ctx.stroke();
    ctx.fillText('+ LTP Reconsolidation', pad.left + 98, h - 5);
  }
}
