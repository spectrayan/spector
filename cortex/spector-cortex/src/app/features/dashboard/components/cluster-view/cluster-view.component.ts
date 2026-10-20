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
import { ClusterNodeInfo } from '@core/models/cortex-events';

const STATUS_COLORS: Record<string, string> = {
  active: '#4caf50',
  draining: '#ff9800',
  down: '#f44336',
};

interface NodePosition {
  x: number;
  y: number;
  radius: number;
  node: ClusterNodeInfo;
  pulsePhase: number;
}

@Component({
  selector: 'cortex-cluster-view',
  templateUrl: './cluster-view.component.html',
  styleUrl: './cluster-view.component.scss',
})
export class ClusterViewComponent implements AfterViewInit, OnDestroy {
  @ViewChild('clusterCanvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly state = inject(CortexStateService);
  private readonly themeService = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);

  private ctx!: CanvasRenderingContext2D;
  private animationId = 0;
  private resizeObserver?: ResizeObserver;
  private nodePositions: NodePosition[] = [];
  private lastTime = 0;

  constructor() {
    effect(() => {
      const nodes = this.state.clusterNodes();
      this.themeService.isDark();
      if (this.ctx) {
        this.updateNodePositions(nodes);
        this.scheduleDraw();
      }
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
    const h = Math.max(0, Math.min(parent.clientHeight, 360));
    if (Math.abs(canvas.width - w) > 1 || Math.abs(canvas.height - h) > 1) {
      canvas.width = w;
      canvas.height = h;
      const nodes = this.state.clusterNodes();
      if (nodes.length > 0) this.updateNodePositions(nodes);
      this.scheduleDraw();
    }
  }

  private updateNodePositions(nodes: ClusterNodeInfo[]): void {
    const w = this.canvasRef.nativeElement.width;
    const h = this.canvasRef.nativeElement.height;
    const centerX = w / 2;
    const centerY = h / 2;
    const orbitalRadius = Math.min(w, h) * 0.28;

    this.nodePositions = nodes.map((node, i) => {
      const angle = (i / nodes.length) * Math.PI * 2 - Math.PI / 2;
      const radius = 20 + (node.shardCount / 12) * 15;
      return {
        x: centerX + Math.cos(angle) * orbitalRadius,
        y: centerY + Math.sin(angle) * orbitalRadius,
        radius,
        node,
        pulsePhase: Math.random() * Math.PI * 2,
      };
    });
  }

  private draw(): void {
    if (!this.ctx) return;
    const now = performance.now();
    const dt = (now - this.lastTime) / 1000;
    this.lastTime = now;

    const canvas = this.canvasRef.nativeElement;
    const ctx = this.ctx;
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    if (this.nodePositions.length === 0) {
      this.drawEmptyState(ctx, w, h);
      return;
    }

    this.animationId = requestAnimationFrame(() => this.draw());

    const primary = this.themeService.getCanvasColor('--primary', '#0a85ff');
    const onSurface = this.themeService.getCanvasColor('--foreground-muted', '#7f8ea3');
    const outline = this.themeService.getCanvasColor('--border-subtle', '#1d283a');

    // Draw replication links
    const links = this.state.clusterLinks();
    for (const [fromId, toId] of links) {
      const fromPos = this.nodePositions.find((p) => p.node.nodeId === fromId);
      const toPos = this.nodePositions.find((p) => p.node.nodeId === toId);
      if (!fromPos || !toPos) continue;

      ctx.strokeStyle = outline;
      ctx.lineWidth = 1;
      ctx.globalAlpha = 0.3;
      ctx.setLineDash([4, 4]);
      ctx.beginPath();
      ctx.moveTo(fromPos.x, fromPos.y);
      ctx.lineTo(toPos.x, toPos.y);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.globalAlpha = 1;
    }

    // Draw nodes
    for (const pos of this.nodePositions) {
      pos.pulsePhase += dt * 2;
      const pulseScale = 1 + Math.sin(pos.pulsePhase) * 0.05 * (pos.node.queryRate / 5);
      const statusColor = STATUS_COLORS[pos.node.status] || '#888';
      const r = pos.radius * pulseScale;

      // Glow
      if (pos.node.status === 'active') {
        const glow = ctx.createRadialGradient(pos.x, pos.y, r * 0.5, pos.x, pos.y, r * 2.5);
        glow.addColorStop(0, statusColor + '30');
        glow.addColorStop(1, statusColor + '00');
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, r * 2.5, 0, Math.PI * 2);
        ctx.fill();
      }

      // Main circle
      const grad = ctx.createRadialGradient(pos.x - r * 0.3, pos.y - r * 0.3, 0, pos.x, pos.y, r);
      grad.addColorStop(0, statusColor + 'cc');
      grad.addColorStop(1, statusColor + '60');
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(pos.x, pos.y, r, 0, Math.PI * 2);
      ctx.fill();

      // Border
      ctx.strokeStyle = statusColor;
      ctx.lineWidth = 2;
      ctx.stroke();

      // Node ID label
      ctx.fillStyle = onSurface;
      ctx.font = 'bold 11px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(pos.node.nodeId, pos.x, pos.y - r - 8);

      // Status label
      ctx.fillStyle = statusColor;
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.fillText(pos.node.status.toUpperCase(), pos.x, pos.y - 4);

      // Shard count
      ctx.fillStyle = onSurface;
      ctx.font = '10px "JetBrains Mono", monospace';
      ctx.fillText(`${pos.node.shardCount} shards`, pos.x, pos.y + 8);

      // Memory usage
      const memMB = (pos.node.memoryUsedBytes / 1_000_000).toFixed(0);
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.globalAlpha = 0.7;
      ctx.fillText(`${memMB} MB`, pos.x, pos.y + 20);
      ctx.globalAlpha = 1;

      // Query rate
      ctx.fillStyle = primary;
      ctx.font = '9px "JetBrains Mono", monospace';
      ctx.fillText(`${pos.node.queryRate.toFixed(1)} q/s`, pos.x, pos.y + r + 14);
    }

    // Title
    ctx.fillStyle = onSurface;
    ctx.font = '10px Inter, sans-serif';
    ctx.textAlign = 'left';
    ctx.globalAlpha = 0.6;
    ctx.fillText(
      `${this.nodePositions.length} nodes · ${links.length} replication links`,
      12,
      h - 8,
    );
    ctx.globalAlpha = 1;
  }

  private drawEmptyState(ctx: CanvasRenderingContext2D, w: number, h: number): void {
    const onSurface = this.themeService.getCanvasColor('--mat-sys-on-surface-variant', '#aaa');
    ctx.fillStyle = onSurface;
    ctx.font = '12px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.globalAlpha = 0.5;
    ctx.fillText('Waiting for cluster topology...', w / 2, h / 2);
    ctx.globalAlpha = 1;
  }
}
