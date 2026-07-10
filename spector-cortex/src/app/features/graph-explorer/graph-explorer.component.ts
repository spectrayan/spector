import {
  Component,
  inject,
  signal,
  computed,
  effect,
  ElementRef,
  ViewChild,
  AfterViewInit,
  OnDestroy,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { FormsModule } from '@angular/forms';
import { MemoryTableService, GraphNode, GraphEdge, EntityTypeStats, RelationTypeStats } from '../../core/services/memory-table.service';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { LoggerService } from '../../core/services/logger.service';
import { QueryInputComponent } from '../query-input/query-input.component';
import { environment } from '../../../environments/environment';
import * as THREE from 'three';

const TIER_COLORS: Record<string, number> = {
  WORKING: 0xffb74d,
  EPISODIC: 0x66bb6a,
  SEMANTIC: 0x42a5f5,
  PROCEDURAL: 0xab47bc,
};

const EDGE_TYPE_COLORS: Record<string, number> = {
  HEBBIAN: 0x00ffcc,   // Cyan-green for sci-fi feel
  TEMPORAL: 0x00bcd4,
  ENTITY: 0xffc107,
};

const STORAGE_KEY = 'spector.graph.camera';
const MAX_PARTICLES = 50;
const NODES_PER_PAGE = 50;

interface CameraState {
  theta: number;
  phi: number;
  radius: number;
}

interface ExplorerNode {
  id: string;
  tier: string;
  text: string;
  importance: number;
  valence: number;
  timestampMs: number;
  position: THREE.Vector3;
  velocity: THREE.Vector3;
  mesh: THREE.Sprite;
  glowMesh: THREE.Sprite;
  labelSprite: THREE.Sprite;
  selected: boolean;
  baseSize: number;
  visible: boolean;        // filter-driven visibility
  targetOpacity: number;   // for ghost-fade animation
}

interface ExplorerEdge {
  from: string;
  to: string;
  type: string;
  weight: number;
  relation: string | null;
  line: THREE.Line;
  labelSprite?: THREE.Sprite;
  weightSprite?: THREE.Sprite;
}

interface HoverInfo {
  x: number;
  y: number;
  id: string;
  tier: string;
  text: string;
  importance: number;
}

/** Neuron-firing particle traveling along an edge */
interface FiringParticle {
  mesh: THREE.Sprite;
  trailMesh: THREE.Sprite;
  edgeIndex: number;
  progress: number;
  speed: number;
  alive: boolean;
  color: number;
}

/** Debug console line entry */
interface DebugLine {
  id: number;
  time: string;
  tag: 'SSE' | 'QRY' | 'ERR' | 'SYS';
  message: string;
}

let debugLineId = 0;

@Component({
  selector: 'cortex-graph-explorer',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSliderModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatDatepickerModule,
    MatNativeDateModule,
    FormsModule,
    QueryInputComponent,
  ],
  templateUrl: './graph-explorer.component.html',
  styleUrl: './graph-explorer.component.scss',
})
export class GraphExplorerComponent implements AfterViewInit, OnDestroy {
  @ViewChild('graphCanvas', { static: true })
  private canvasContainer!: ElementRef<HTMLDivElement>;

  private readonly platformId = inject(PLATFORM_ID);
  private readonly memoryService = inject(MemoryTableService);
  private readonly state = inject(CortexStateService);
  private readonly log = inject(LoggerService);

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private animationId = 0;
  private nodes: ExplorerNode[] = [];
  private edges: ExplorerEdge[] = [];
  private particles: FiringParticle[] = [];
  private raycaster = new THREE.Raycaster();
  private mouse = new THREE.Vector2();
  private gridGroup!: THREE.Group;
  private dustParticles!: THREE.Points;
  private particleSpawnTimer = 0;
  private lastRealEventTime = 0; // timestamp of last real SSE event

  // Shared geometries/textures for particle pooling
  private particleTexture!: THREE.CanvasTexture;
  private trailTexture!: THREE.CanvasTexture;

  // Orbit state
  private isDragging = false;
  private orbitTheta = 0;
  private orbitPhi = Math.PI / 4;
  private orbitRadius = 100;
  private dragStartX = 0;
  private dragStartY = 0;
  private dragStartTheta = 0;
  private dragStartPhi = 0;
  private timer = new THREE.Timer();
  private saveTimer: ReturnType<typeof setTimeout> | null = null;

  // Fly-to animation state
  private lookAtTarget = new THREE.Vector3(0, 0, 0);
  private flyFromPos = new THREE.Vector3(0, 0, 0);
  private flyToPos: THREE.Vector3 | null = null;
  private flyFromRadius = 100;
  private flyToRadius = 0;
  private flyProgress = 1;

  readonly selectedNode = signal<ExplorerNode | null>(null);
  readonly selectedEdge = signal<ExplorerEdge | null>(null);
  readonly hoverInfo = signal<HoverInfo | null>(null);
  readonly searchQuery = signal('');
  readonly depth = signal(2);
  readonly showHebbian = signal(true);
  readonly showTemporal = signal(true);
  readonly showEntity = signal(true);
  readonly showLabels = signal(true);
  readonly nodeCount = signal(0);
  readonly edgeCount = signal(0);
  readonly graphLoading = signal(false);
  readonly graphError = signal<string | null>(null);
  readonly isCapturing = signal(false);
  readonly showSharePanel = signal(false);
  readonly isSharing = signal(false);
  readonly shareToast = signal<string | null>(null);
  private shareToastTimer: ReturnType<typeof setTimeout> | null = null;

  readonly showTopologyStats = signal(false);
  readonly entityTypesStats = signal<EntityTypeStats[]>([]);
  readonly relationTypesStats = signal<RelationTypeStats[]>([]);
  readonly topologyStatsLoading = signal(false);
  readonly topologyStatsError = signal<string | null>(null);

  // Filter signals — sci-fi command deck
  readonly importanceMin = signal(0);
  readonly importanceMax = signal(10);
  readonly valenceMin = signal(-128);
  readonly valenceMax = signal(127);
  readonly timestampMin = signal(0);
  readonly timestampMax = signal(Date.now());
  readonly showFilters = signal(false);

  // Pagination
  readonly totalAvailable = signal(0);
  readonly loadedCount = signal(0);
  readonly isLoadingMore = signal(false);
  private currentPage = 0;

  // Stats for the HUD
  readonly avgImportance = signal(0);
  readonly densityRatio = signal(0);
  readonly hebbianCount = signal(0);
  readonly temporalCount = signal(0);
  readonly entityCount = signal(0);
  readonly visibleNodeCount = computed(() =>
    this.nodes.filter(n => n.visible).length
  );

  // Timestamp range for slider
  readonly oldestTimestamp = signal(0);
  readonly newestTimestamp = signal(Date.now());

  // Computed date objects for date pickers
  readonly fromDate = computed(() => {
    const ts = this.timestampMin();
    return ts > 0 ? new Date(ts) : null;
  });
  readonly toDate = computed(() => {
    const ts = this.timestampMax();
    return ts > 0 ? new Date(ts) : null;
  });

  // Debug console — only enabled in non-prod debug mode
  readonly showDebugConsole = signal(
    !environment.production && environment.logging.level === 'debug'
  );
  readonly debugLines = signal<DebugLine[]>([]);
  private readonly MAX_DEBUG_LINES = 100;

  // ── Recall Mode ──────────────────────────────────────────
  readonly recallMode = signal(false);
  readonly recallMatchedIds = signal<Set<string>>(new Set());
  readonly recallResults = signal<Array<{ id: string; score: number; text: string; tier: string }>>([]); 
  private recallAmbientLight: THREE.AmbientLight | null = null;

  // ── Neighborhood Expansion ───────────────────────────────
  readonly isExpanding = signal(false);
  readonly expansionDepth = signal(1);
  private isOverviewMode = true;

  // ── Query-Driven Graph Animation ─────────────────────────
  readonly queryAnimationMode = signal(false);
  readonly queryAnimationStage = signal<string>('');
  readonly queryAnimationProgress = signal(0);
  private queryAnimationTimer: ReturnType<typeof setTimeout> | null = null;
  private lastAnimationMatchedIds: Set<string> = new Set();

  // ── Time-Travel Mode ───────────────────────────────────
  readonly timeTravelMode = signal(false);
  readonly timeTravelTimestamp = signal(0);
  readonly timeTravelPlaying = signal(false);
  readonly timeTravelSpeed = signal(1);
  private timeTravelTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    // ── Real event effects — fire particles on actual SSE events ──

    // Query trace: fire particles proportional to hebbian+temporal+entity activations
    effect(() => {
      const trace = this.state.currentQueryTrace();
      if (trace && this.edges.length > 0) {
        const count = trace.hebbianActivated + trace.temporalLinked + trace.entityDiscovered;
        this.log.info('GraphExplorer', `Query trace: ${count} activations — firing particles`);
        this.lastRealEventTime = Date.now();
        this.fireBurst(Math.min(count, 30));
        this.pushDebugLine('SSE', `cortex.query.trace — ${trace.finalTopK} results, ${count} activations`);
      }
    });

    // Graph pulse: fire particles along traversed edges
    effect(() => {
      const pulses = this.state.graphPulses();
      if (pulses.length > 0 && this.edges.length > 0) {
        const latest = pulses[0];
        this.log.debug('GraphExplorer', `Graph pulse: ${latest.edgesTraversed} edges traversed`);
        this.lastRealEventTime = Date.now();
        this.fireBurst(Math.min(latest.edgesTraversed, 20));
        this.pushDebugLine('SSE', `cortex.graph.pulse — ${latest.edgesTraversed} edges traversed`);
      }
    });

    // Reflect cycle: consolidation — fire a slower, dimmer burst
    effect(() => {
      const reflect = this.state.lastReflect();
      if (reflect && this.edges.length > 0) {
        this.log.debug('GraphExplorer', `Reflect cycle: ${reflect.hebbianEdgesRemoved} edges consolidated`);
        this.lastRealEventTime = Date.now();
        this.fireBurst(Math.min(reflect.hebbianEdgesRemoved + 3, 15));
        this.pushDebugLine('SSE', `cortex.reflect.cycle — ${reflect.hebbianEdgesRemoved} edges removed`);
      }
    });

    // Connection status debug line
    effect(() => {
      const status = this.state.connectionStatus();
      this.pushDebugLine('SYS', `Connection: ${status}`);
    });

    // ── Recall Mode: watch recallResults from CortexStateService ──
    effect(() => {
      const results = this.state.recallResults();
      if (results && results.length > 0 && this.nodes.length > 0) {
        this.enterRecallMode(results);
      }
    });
  }

  loadTopologyStats(): void {
    this.topologyStatsLoading.set(true);
    this.topologyStatsError.set(null);
    this.memoryService.getTopologyStats().subscribe({
      next: (stats) => {
        this.entityTypesStats.set(stats.entityTypes || []);
        this.relationTypesStats.set(stats.relationTypes || []);
        this.topologyStatsLoading.set(false);
      },
      error: (err) => {
        this.topologyStatsError.set('Failed to load topology statistics');
        this.topologyStatsLoading.set(false);
      }
    });
  }

  toggleTopologyStats(): void {
    const nextVal = !this.showTopologyStats();
    this.showTopologyStats.set(nextVal);
    if (nextVal) {
      this.loadTopologyStats();
      this.selectedNode.set(null);
      this.selectedEdge.set(null);
      this.clearEdgeHighlight();
    }
  }

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.restoreCameraState();
    this.initScene();
    this.createParticleTextures();
    this.loadGraphData();
    this.animate();
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.animationId);
    this.renderer?.dispose();
    if (this.saveTimer) clearTimeout(this.saveTimer);
  }

  onMouseMove(event: MouseEvent): void {
    if (this.isDragging) {
      const dx = (event.clientX - this.dragStartX) * 0.005;
      const dy = (event.clientY - this.dragStartY) * 0.005;
      this.orbitTheta = this.dragStartTheta - dx;
      this.orbitPhi = Math.max(0.1, Math.min(Math.PI - 0.1, this.dragStartPhi + dy));
      return;
    }

    const container = this.canvasContainer.nativeElement;
    const rect = container.getBoundingClientRect();
    this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    let closestNode: ExplorerNode | null = null;
    let closestDist = 0.04;
    for (const node of this.nodes) {
      if (!node.visible) continue;
      const projected = node.position.clone().project(this.camera);
      const dx = projected.x - this.mouse.x;
      const dy = projected.y - this.mouse.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < closestDist) {
        closestDist = dist;
        closestNode = node;
      }
    }

    if (closestNode) {
      this.hoverInfo.set({
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
        id: closestNode.id,
        tier: closestNode.tier,
        text: closestNode.text,
        importance: closestNode.importance,
      });
      container.style.cursor = 'pointer';
      return;
    }
    this.hoverInfo.set(null);
    container.style.cursor = this.isDragging ? 'grabbing' : 'grab';
  }

  onMouseDown(event: MouseEvent): void {
    this.isDragging = true;
    this.dragStartX = event.clientX;
    this.dragStartY = event.clientY;
    this.dragStartTheta = this.orbitTheta;
    this.dragStartPhi = this.orbitPhi;
    this.hoverInfo.set(null);
    event.preventDefault();
  }

  onMouseUp(event: MouseEvent): void {
    if (this.isDragging) {
      const dx = event.clientX - this.dragStartX;
      const dy = event.clientY - this.dragStartY;
      const moved = Math.sqrt(dx * dx + dy * dy);
      this.isDragging = false;
      this.debounceSaveCameraState();
      if (moved < 5) {
        this.handleClick(event);
      }
    }
  }

  onWheel(event: WheelEvent): void {
    this.orbitRadius = Math.max(10, Math.min(800, this.orbitRadius + event.deltaY * 0.15));
    this.debounceSaveCameraState();
    event.preventDefault();
  }

  onClick(event: MouseEvent): void {
    // Handled by onMouseUp click detection
  }

  private handleClick(event: MouseEvent): void {
    const container = this.canvasContainer.nativeElement;
    const rect = container.getBoundingClientRect();
    this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.mouse, this.camera);

    let closestNode: ExplorerNode | null = null;
    let closestNodeDist = 0.06;
    for (const node of this.nodes) {
      if (!node.visible) continue;
      const projected = node.position.clone().project(this.camera);
      const dx = projected.x - this.mouse.x;
      const dy = projected.y - this.mouse.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < closestNodeDist) {
        closestNodeDist = dist;
        closestNode = node;
      }
    }
    if (closestNode) {
      this.selectedNode.set(closestNode);
      this.selectedEdge.set(null);
      this.showTopologyStats.set(false);
      this.flyToNode(closestNode);
      return;
    }

    let closestEdge: ExplorerEdge | null = null;
    let closestDist = 3.0;
    for (const edge of this.edges) {
      if (!edge.line.visible) continue;
      const fromNode = this.nodes.find((n) => n.id === edge.from);
      const toNode = this.nodes.find((n) => n.id === edge.to);
      if (!fromNode || !toNode) continue;
      const midpoint = new THREE.Vector3()
        .addVectors(fromNode.position, toNode.position)
        .multiplyScalar(0.5);
      const projected = midpoint.clone().project(this.camera);
      const dx = projected.x - this.mouse.x;
      const dy = projected.y - this.mouse.y;
      const screenDist = Math.sqrt(dx * dx + dy * dy);
      if (screenDist < 0.08 && screenDist < closestDist) {
        closestDist = screenDist;
        closestEdge = edge;
      }
    }
    if (closestEdge) {
      this.selectedEdge.set(closestEdge);
      this.selectedNode.set(null);
      this.showTopologyStats.set(false);
      this.highlightEdge(closestEdge);
      return;
    }

    this.selectedNode.set(null);
    this.selectedEdge.set(null);
    this.clearEdgeHighlight();
  }

  /** Capture the current graph view as a branded PNG screenshot */
  async captureScreenshot(): Promise<void> {
    if (this.isCapturing() || !this.renderer) return;
    this.isCapturing.set(true);
    this.showSharePanel.set(false);

    // Force a render to ensure current frame is captured
    this.renderer.render(this.scene, this.camera);

    const blob = await this.captureBrandedBlob();
    if (!blob) {
      this.isCapturing.set(false);
      return;
    }

    // Try clipboard first
    try {
      await navigator.clipboard.write([
        new ClipboardItem({ 'image/png': blob }),
      ]);
      this.log.info('GraphExplorer', 'Screenshot copied to clipboard');
      this.pushDebugLine('SYS', 'Screenshot captured → clipboard');
      this.showShareToast('Screenshot copied to clipboard');
    } catch {
      this.log.info('GraphExplorer', 'Clipboard unavailable, downloading instead');
    }

    // Also trigger download
    this.downloadBlob(blob, `spector-graph-${Date.now()}.png`);

    // Reset after brief flash
    setTimeout(() => this.isCapturing.set(false), 600);
  }

  /** Share the current graph view using the Web Share API (or fallback to download) */
  async shareScreenshot(): Promise<void> {
    if (this.isSharing() || !this.renderer) return;
    this.isSharing.set(true);
    this.showSharePanel.set(false);

    // Force render to get current frame
    this.renderer.render(this.scene, this.camera);

    const blob = await this.captureBrandedBlob();
    if (!blob) {
      this.isSharing.set(false);
      return;
    }

    const fileName = `spector-graph-${Date.now()}.png`;
    const file = new File([blob], fileName, { type: 'image/png' });

    // Try Web Share API first (mobile + modern browsers)
    if (navigator.share && navigator.canShare?.({ files: [file] })) {
      try {
        await navigator.share({
          title: '⬡ Spector Cortex — Neural Graph',
          text: `${this.nodeCount()} nodes · ${this.edgeCount()} synapses — Cognitive memory graph`,
          files: [file],
        });
        this.showShareToast('Shared successfully');
      } catch (err: any) {
        if (err?.name !== 'AbortError') {
          // User cancelled — not an error. Otherwise fall back to download.
          this.downloadBlob(blob, fileName);
          this.showShareToast('Downloaded to disk');
        }
      }
    } else {
      // Fallback: copy to clipboard + download
      try {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob }),
        ]);
        this.showShareToast('Copied to clipboard');
      } catch {
        this.downloadBlob(blob, fileName);
        this.showShareToast('Downloaded to disk');
      }
    }

    setTimeout(() => this.isSharing.set(false), 600);
  }

  /** Copy a deep link with encoded camera state + filter params */
  copyShareLink(): void {
    this.showSharePanel.set(false);
    const params = new URLSearchParams();
    params.set('theta', this.orbitTheta.toFixed(3));
    params.set('phi', this.orbitPhi.toFixed(3));
    params.set('radius', this.orbitRadius.toFixed(1));

    // Encode active edge toggles
    if (!this.showHebbian()) params.set('heb', '0');
    if (!this.showTemporal()) params.set('tmp', '0');
    if (!this.showEntity()) params.set('ent', '0');

    const url = `${window.location.origin}/graph?${params.toString()}`;
    navigator.clipboard.writeText(url).then(
      () => this.showShareToast('Link copied to clipboard'),
      () => this.showShareToast('Failed to copy link'),
    );
  }

  /** Toggle the share panel dropdown */
  toggleSharePanel(): void {
    this.showSharePanel.update(v => !v);
  }

  private showShareToast(message: string): void {
    if (this.shareToastTimer) clearTimeout(this.shareToastTimer);
    this.shareToast.set(message);
    this.shareToastTimer = setTimeout(() => this.shareToast.set(null), 3000);
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  }

  /** Capture the current graph view as a branded PNG blob */
  private captureBrandedBlob(): Promise<Blob | null> {
    return new Promise(resolve => {
      const threeCanvas = this.renderer.domElement;
      const width = threeCanvas.width;
      const height = threeCanvas.height;

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d')!;

      ctx.drawImage(threeCanvas, 0, 0);

      // Branded watermark (bottom-right)
      const padding = 16 * (window.devicePixelRatio || 1);
      const fontSize = 12 * (window.devicePixelRatio || 1);
      ctx.font = `600 ${fontSize}px 'JetBrains Mono', 'Consolas', monospace`;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'bottom';

      const watermarkText = '⬡ SPECTOR CORTEX';
      const textMetrics = ctx.measureText(watermarkText);
      const pillW = textMetrics.width + padding * 2;
      const pillH = fontSize * 2;
      const pillX = width - padding - pillW;
      const pillY = height - padding - pillH;
      ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
      ctx.beginPath();
      ctx.roundRect(pillX, pillY, pillW, pillH, 6 * (window.devicePixelRatio || 1));
      ctx.fill();
      ctx.fillStyle = 'rgba(0, 255, 204, 0.7)';
      ctx.fillText(watermarkText, width - padding - padding / 2, height - padding - (pillH - fontSize) / 2);

      // HUD stats overlay (top-left)
      const statsText = `${this.nodeCount()} NODES · ${this.edgeCount()} SYNAPSES`;
      const statsFontSize = 10 * (window.devicePixelRatio || 1);
      ctx.font = `700 ${statsFontSize}px 'JetBrains Mono', 'Consolas', monospace`;
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';
      const statsMetrics = ctx.measureText(statsText);
      const statsPillW = statsMetrics.width + padding * 2;
      const statsPillH = statsFontSize * 2.2;
      ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
      ctx.beginPath();
      ctx.roundRect(padding, padding, statsPillW, statsPillH, 6 * (window.devicePixelRatio || 1));
      ctx.fill();
      ctx.fillStyle = 'rgba(0, 255, 204, 0.6)';
      ctx.fillText(statsText, padding * 2, padding + (statsPillH - statsFontSize) / 2);

      canvas.toBlob(blob => resolve(blob), 'image/png');
    });
  }

  // ── Recall Mode ──────────────────────────────────────────

  /** Enter recall mode — highlight matched nodes, ghost others */
  private enterRecallMode(results: any[]): void {
    const matchedIds = new Set<string>();
    const recallItems: Array<{ id: string; score: number; text: string; tier: string }> = [];

    for (const r of results) {
      const id = r.id || r.memoryId;
      if (id) {
        matchedIds.add(id);
        recallItems.push({
          id,
          score: r.score ?? r.cognitiveScore ?? 0,
          text: r.textPreview ?? r.content?.substring(0, 80) ?? id,
          tier: r.tier ?? r.memoryType ?? 'UNKNOWN',
        });
      }
    }

    this.recallMode.set(true);
    this.recallMatchedIds.set(matchedIds);
    this.recallResults.set(recallItems);

    // Set recall-specific opacity targets
    for (const node of this.nodes) {
      if (matchedIds.has(node.id)) {
        node.targetOpacity = 1.0;
        node.visible = true;
      } else {
        node.targetOpacity = 0.05;
      }
    }

    // Add blue-shift ambient light for recall atmosphere
    if (!this.recallAmbientLight && this.scene) {
      this.recallAmbientLight = new THREE.AmbientLight(0x42a5f5, 0.3);
      this.scene.add(this.recallAmbientLight);
    }

    // Fire particles along edges connecting matched nodes
    const matchedEdges = this.edges.filter(e => matchedIds.has(e.from) && matchedIds.has(e.to));
    this.fireBurst(Math.min(matchedEdges.length * 2 + 5, 30));

    this.pushDebugLine('QRY', `Recall mode: ${matchedIds.size} matched, ${results.length} results`);
    this.log.info('GraphExplorer', `Entered recall mode with ${matchedIds.size} matched nodes`);
  }

  /** Exit recall mode — restore all nodes */
  exitRecallMode(): void {
    this.recallMode.set(false);
    this.recallMatchedIds.set(new Set());
    this.recallResults.set([]);

    // Restore all node visibility
    for (const node of this.nodes) {
      node.targetOpacity = 1.0;
      node.visible = true;
    }

    // Remove blue-shift light
    if (this.recallAmbientLight && this.scene) {
      this.scene.remove(this.recallAmbientLight);
      this.recallAmbientLight = null;
    }

    this.pushDebugLine('SYS', 'Recall mode exited');
  }

  /** Fly to a recall result node */
  flyToRecallResult(id: string): void {
    const node = this.nodes.find(n => n.id === id);
    if (node) {
      this.selectedNode.set(node);
      this.flyToNode(node);
    }
  }

  // ── Neighborhood Expansion ───────────────────────────────

  /** Expand the neighborhood of a node — load its graph connections */
  expandNodeNeighbors(nodeId: string): void {
    if (this.isExpanding()) return;
    this.isExpanding.set(true);
    this.isOverviewMode = false;
    const depth = this.expansionDepth();

    this.pushDebugLine('QRY', `Expanding neighborhood: ${nodeId} (depth=${depth})`);

    this.memoryService.getMemoryGraph(nodeId, depth).subscribe({
      next: (response) => {
        const existingIds = new Set(this.nodes.map(n => n.id));
        const newNodes = response.nodes.filter(n => !existingIds.has(n.id));
        const newEdges = response.edges.filter(e =>
          !this.edges.some(ex => ex.from === e.fromId && ex.to === e.toId && ex.type === e.type)
        );

        if (newNodes.length > 0) {
          // Position new nodes in a radial burst around the parent
          const parentNode = this.nodes.find(n => n.id === nodeId);
          if (parentNode) {
            this.addNodesToSceneAroundParent(newNodes, parentNode);
          } else {
            this.addNodesToScene(newNodes, true);
          }
          this.addEdgesToScene(newEdges);
          this.nodeCount.set(this.nodes.length);
          this.edgeCount.set(this.edges.length);
          this.loadedCount.set(this.nodes.length);
          this.computeGraphStats();
          this.updateTimestampRange();

          // Fire a burst to celebrate
          this.fireBurst(Math.min(newNodes.length * 2, 20));
          this.pushDebugLine('SYS', `Expanded: +${newNodes.length} nodes, +${newEdges.length} edges`);
        } else {
          this.pushDebugLine('SYS', 'No new nodes to expand');
        }
        this.isExpanding.set(false);
      },
      error: (err) => {
        this.pushDebugLine('ERR', `Expansion failed: ${err.message || 'unknown error'}`);
        this.isExpanding.set(false);
      },
    });
  }

  /** Position new nodes in a radial burst around a parent node */
  private addNodesToSceneAroundParent(apiNodes: GraphNode[], parent: ExplorerNode): void {
    const nodeIdMap = new Map<string, ExplorerNode>();
    for (const n of this.nodes) nodeIdMap.set(n.id, n);

    const golden = (1 + Math.sqrt(5)) / 2;

    for (let i = 0; i < apiNodes.length; i++) {
      const n = apiNodes[i];
      if (nodeIdMap.has(n.id)) continue;

      const color = TIER_COLORS[n.tier] ?? 0x888888;
      const importance = Math.max(0.1, Math.min(1.0, n.importance / 10));

      // Radial burst around parent: golden spiral offset
      const theta = (2 * Math.PI * i) / golden;
      const phi = Math.acos(1 - (2 * (i + 0.5)) / Math.max(1, apiNodes.length));
      const burstRadius = 8 + importance * 15;

      const pos = new THREE.Vector3(
        parent.position.x + burstRadius * Math.sin(phi) * Math.cos(theta),
        parent.position.y + burstRadius * Math.sin(phi) * Math.sin(theta),
        parent.position.z + burstRadius * Math.cos(phi),
      );

      const size = 0.3 + importance * 0.6;

      // Star core sprite
      const starTex = this.createStarTexture(color, 1.0);
      const starMat = new THREE.SpriteMaterial({
        map: starTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const mesh = new THREE.Sprite(starMat);
      mesh.scale.set(0, 0, 1); // warp-in from zero
      mesh.position.copy(pos);
      this.scene.add(mesh);

      // Outer glow halo
      const glowTex = this.createStarTexture(color, 0.3);
      const glowMat = new THREE.SpriteMaterial({
        map: glowTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const glowMesh = new THREE.Sprite(glowMat);
      glowMesh.scale.set(0, 0, 1);
      glowMesh.position.copy(pos);
      this.scene.add(glowMesh);

      // Node label
      const labelSprite = this.createNodeLabel(n.id, n.tier, importance, color);
      labelSprite.position.copy(pos);
      labelSprite.position.y += size * 3 + 0.8;
      this.scene.add(labelSprite);

      const explorerNode: ExplorerNode = {
        id: n.id,
        tier: n.tier,
        text: n.textPreview,
        importance: n.importance,
        valence: n.valence ?? 0,
        timestampMs: n.timestampMs ?? Date.now(),
        position: pos,
        velocity: new THREE.Vector3(
          (Math.random() - 0.5) * 0.003,
          (Math.random() - 0.5) * 0.003,
          (Math.random() - 0.5) * 0.003,
        ),
        mesh,
        glowMesh,
        labelSprite,
        selected: false,
        baseSize: size,
        visible: true,
        targetOpacity: 1.0,
      };
      this.nodes.push(explorerNode);
      nodeIdMap.set(n.id, explorerNode);
    }
  }

  /** Collapse back to the overview graph — clear expansion nodes */
  collapseToOverview(): void {
    this.isOverviewMode = true;
    this.clearScene();
    this.lookAtTarget.set(0, 0, 0);
    this.flyToPos = null;
    this.flyProgress = 1;
    this.currentPage = 0;
    this.loadGraphData();
    this.pushDebugLine('SYS', 'Collapsed to overview');
  }

  // ── Query-Driven Graph Animation ─────────────────────────

  /**
   * Animate the query traversal through the graph in 5 stages:
   * 1. Ghost all nodes (dim to 0.05)
   * 2. Bloom-gate flash (green pulse on candidate nodes)
   * 3. Decay gate glow (amber wave on surviving nodes)
   * 4. Top-K highlight (full brightness + sonar ripple)
   * 5. Edge cascade (rapid particles between top-K)
   */
  animateQueryTraversal(): void {
    const results = this.state.recallResults();
    if (!results || results.length === 0 || this.nodes.length === 0) return;

    // Collect matched node IDs
    const matchedIds = new Set<string>();
    for (const r of results) {
      const id = r.id || r.memoryId;
      if (id) matchedIds.add(id);
    }
    this.lastAnimationMatchedIds = matchedIds;

    if (matchedIds.size === 0) return;

    this.queryAnimationMode.set(true);
    this.queryAnimationProgress.set(0);

    // Cancel any existing animation
    if (this.queryAnimationTimer) clearTimeout(this.queryAnimationTimer);

    // Stage 1: Ghost all nodes
    this.queryAnimationStage.set('DIMMING');
    for (const node of this.nodes) {
      node.targetOpacity = 0.05;
    }
    this.queryAnimationProgress.set(0.1);

    // Stage 2: Bloom gate flash (500ms)
    this.queryAnimationTimer = setTimeout(() => {
      this.queryAnimationStage.set('BLOOM GATE');
      this.queryAnimationProgress.set(0.3);
      // Flash all nodes briefly as bloom candidates
      for (const node of this.nodes) {
        node.targetOpacity = 0.15;
      }
      this.pushDebugLine('QRY', 'Animation: Bloom gate scan');

      // Stage 3: Decay gate (1000ms)
      this.queryAnimationTimer = setTimeout(() => {
        this.queryAnimationStage.set('DECAY GATE');
        this.queryAnimationProgress.set(0.5);
        // Fade out non-matched, brighten potential matches
        for (const node of this.nodes) {
          node.targetOpacity = matchedIds.has(node.id) ? 0.5 : 0.03;
        }
        this.pushDebugLine('QRY', `Animation: Decay gate — ${matchedIds.size} survivors`);

        // Stage 4: Top-K reveal (1000ms)
        this.queryAnimationTimer = setTimeout(() => {
          this.queryAnimationStage.set('TOP-K REVEAL');
          this.queryAnimationProgress.set(0.75);
          for (const node of this.nodes) {
            if (matchedIds.has(node.id)) {
              node.targetOpacity = 1.0;
              node.visible = true;
            } else {
              node.targetOpacity = 0.03;
            }
          }
          this.pushDebugLine('QRY', `Animation: Top-K revealed — ${matchedIds.size} results`);

          // Stage 5: Edge cascade (1000ms)
          this.queryAnimationTimer = setTimeout(() => {
            this.queryAnimationStage.set('SYNAPSE CASCADE');
            this.queryAnimationProgress.set(0.95);
            // Fire particles along edges connecting matched nodes
            const matchedEdges = this.edges.filter(e => matchedIds.has(e.from) && matchedIds.has(e.to));
            this.fireBurst(Math.min(matchedEdges.length * 3 + 5, 30));
            this.pushDebugLine('QRY', `Animation: Synapse cascade — ${matchedEdges.length} edges activated`);

            // Complete (1500ms)
            this.queryAnimationTimer = setTimeout(() => {
              this.queryAnimationStage.set('');
              this.queryAnimationProgress.set(1.0);
              this.queryAnimationMode.set(false);
              // Leave nodes in recall-highlighted state
              this.enterRecallMode(results);
            }, 1500);
          }, 1000);
        }, 1000);
      }, 1000);
    }, 500);
  }

  /** Replay the last query animation */
  replayQueryAnimation(): void {
    if (this.lastAnimationMatchedIds.size === 0) return;
    // Exit recall mode first
    this.exitRecallMode();
    // Re-trigger the animation
    this.animateQueryTraversal();
  }

  // ── Time-Travel Mode ───────────────────────────────────

  /** Enter time-travel mode — scrub through graph history */
  enterTimeTravel(): void {
    if (this.nodes.length === 0) return;
    this.timeTravelMode.set(true);
    this.timeTravelTimestamp.set(this.oldestTimestamp());
    this.applyTimeTravelFilter();
    this.pushDebugLine('SYS', 'Time-travel mode activated');
  }

  /** Exit time-travel mode — restore all nodes */
  exitTimeTravel(): void {
    this.timeTravelMode.set(false);
    this.timeTravelPlaying.set(false);
    if (this.timeTravelTimer) {
      clearInterval(this.timeTravelTimer);
      this.timeTravelTimer = null;
    }
    // Restore all nodes
    for (const node of this.nodes) {
      node.targetOpacity = 1.0;
      node.visible = true;
    }
    this.pushDebugLine('SYS', 'Time-travel mode exited');
  }

  /** Play/pause time-travel animation */
  toggleTimeTravelPlay(): void {
    if (this.timeTravelPlaying()) {
      this.timeTravelPlaying.set(false);
      if (this.timeTravelTimer) {
        clearInterval(this.timeTravelTimer);
        this.timeTravelTimer = null;
      }
    } else {
      this.timeTravelPlaying.set(true);
      const range = this.newestTimestamp() - this.oldestTimestamp();
      const step = range / 200; // ~200 frames
      this.timeTravelTimer = setInterval(() => {
        const current = this.timeTravelTimestamp();
        const next = current + step * this.timeTravelSpeed();
        if (next >= this.newestTimestamp()) {
          this.timeTravelTimestamp.set(this.newestTimestamp());
          this.timeTravelPlaying.set(false);
          if (this.timeTravelTimer) {
            clearInterval(this.timeTravelTimer);
            this.timeTravelTimer = null;
          }
        } else {
          this.timeTravelTimestamp.set(next);
        }
        this.applyTimeTravelFilter();
      }, 50);
    }
  }

  /** Update time-travel timestamp from slider */
  onTimeTravelScrub(value: number): void {
    this.timeTravelTimestamp.set(value);
    this.applyTimeTravelFilter();
  }

  /** Apply time-travel filter: show nodes that existed at the current timestamp */
  private applyTimeTravelFilter(): void {
    const cutoff = this.timeTravelTimestamp();
    for (const node of this.nodes) {
      const born = node.timestampMs <= cutoff;
      node.visible = born;
      node.targetOpacity = born ? 1.0 : 0.0;
    }
  }

  refreshGraph(): void {
    this.clearScene();
    this.lookAtTarget.set(0, 0, 0);
    this.flyToPos = null;
    this.flyProgress = 1;
    this.currentPage = 0;
    this.isOverviewMode = true;
    this.loadGraphData();
    if (this.showTopologyStats()) {
      this.loadTopologyStats();
    }
  }

  /** Load more nodes (pagination) */
  expandScanRange(): void {
    if (this.isLoadingMore()) return;
    this.currentPage++;
    this.isLoadingMore.set(true);

    this.memoryService.getGraphOverview(NODES_PER_PAGE * (this.currentPage + 1)).subscribe({
      next: (response) => {
        // Filter to only new nodes not already loaded
        const existingIds = new Set(this.nodes.map(n => n.id));
        const newNodes = response.nodes.filter(n => !existingIds.has(n.id));
        const newEdges = response.edges.filter(e =>
          !this.edges.some(ex => ex.from === e.fromId && ex.to === e.toId && ex.type === e.type)
        );

        if (newNodes.length > 0) {
          this.addNodesToScene(newNodes, true); // warp-in animation
          this.addEdgesToScene(newEdges);
          this.nodeCount.set(this.nodes.length);
          this.edgeCount.set(this.edges.length);
          this.loadedCount.set(this.nodes.length);
          this.computeGraphStats();
          this.updateTimestampRange();
          if (this.showTopologyStats()) {
            this.loadTopologyStats();
          }
        }
        this.isLoadingMore.set(false);
      },
      error: () => {
        this.isLoadingMore.set(false);
      },
    });
  }

  /** Reset all filters to default */
  resetFilters(): void {
    this.importanceMin.set(0);
    this.importanceMax.set(10);
    this.valenceMin.set(-128);
    this.valenceMax.set(127);
    this.timestampMin.set(this.oldestTimestamp());
    this.timestampMax.set(this.newestTimestamp());
  }

  /** Handle from-date picker change */
  onFromDateChange(date: Date | null): void {
    if (date) {
      this.timestampMin.set(date.getTime());
    }
  }

  /** Handle to-date picker change */
  onToDateChange(date: Date | null): void {
    if (date) {
      // Set to end of day
      const endOfDay = new Date(date);
      endOfDay.setHours(23, 59, 59, 999);
      this.timestampMax.set(endOfDay.getTime());
    }
  }

  /** Set temporal filter to a quick preset */
  setTemporalPreset(preset: string): void {
    const now = Date.now();
    switch (preset) {
      case '1h':  this.timestampMin.set(now - 3_600_000); break;
      case '24h': this.timestampMin.set(now - 86_400_000); break;
      case '7d':  this.timestampMin.set(now - 7 * 86_400_000); break;
      case '30d': this.timestampMin.set(now - 30 * 86_400_000); break;
      case 'all': this.timestampMin.set(this.oldestTimestamp()); break;
    }
    this.timestampMax.set(now);
  }

  /** Format a timestamp for display */
  formatTimestamp(ms: number): string {
    if (ms <= 0) return '—';
    const d = new Date(ms);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  private flyToNode(node: ExplorerNode): void {
    this.flyFromPos.copy(this.lookAtTarget);
    this.flyFromRadius = this.orbitRadius;
    this.flyToPos = node.position.clone();
    this.flyToRadius = Math.max(20, node.baseSize * 15);
    this.flyProgress = 0;
  }

  onDoubleClick(): void {
    // If a node is selected, expand its neighborhood instead of resetting
    const selected = this.selectedNode();
    if (selected) {
      this.expandNodeNeighbors(selected.id);
      return;
    }

    // No node selected — reset to overview
    this.flyFromPos.copy(this.lookAtTarget);
    this.flyFromRadius = this.orbitRadius;
    this.flyToPos = new THREE.Vector3(0, 0, 0);
    this.flyToRadius = 120;
    this.flyProgress = 0;
    this.selectedNode.set(null);
    this.selectedEdge.set(null);
    this.clearEdgeHighlight();

    // Exit recall mode if active
    if (this.recallMode()) {
      this.exitRecallMode();
    }
  }

  private easeInOutCubic(t: number): number {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  edgeLabel(edge: ExplorerEdge): string {
    switch (edge.type) {
      case 'HEBBIAN':
        return `weight: ${edge.weight.toFixed(1)}`;
      case 'TEMPORAL':
        return '→ next';
      case 'ENTITY':
        return edge.relation ?? 'RELATED';
      default:
        return edge.type;
    }
  }

  edgeIcon(type: string): string {
    switch (type) {
      case 'HEBBIAN':
        return 'link';
      case 'TEMPORAL':
        return 'timeline';
      case 'ENTITY':
        return 'category';
      default:
        return 'device_hub';
    }
  }


  // ── Camera persistence ──────────────────────────────────

  private saveCameraState(): void {
    const state: CameraState = {
      theta: this.orbitTheta,
      phi: this.orbitPhi,
      radius: this.orbitRadius,
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* storage unavailable */
    }
  }

  private restoreCameraState(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const state: CameraState = JSON.parse(raw);
        this.orbitTheta = state.theta ?? 0;
        this.orbitPhi = state.phi ?? Math.PI / 4;
        this.orbitRadius = state.radius ?? 100;
      }
    } catch {
      /* ignore */
    }
  }

  private debounceSaveCameraState(): void {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.saveCameraState(), 300);
  }

  // ── Highlighting ────────────────────────────────────────

  private highlightEdge(edge: ExplorerEdge): void {
    for (const e of this.edges) {
      const mat = e.line.material as THREE.LineBasicMaterial;
      mat.opacity = e === edge ? 0.8 : 0.1;
      mat.linewidth = e === edge ? 3 : 1;
    }
  }

  private clearEdgeHighlight(): void {
    for (const e of this.edges) {
      const mat = e.line.material as THREE.LineBasicMaterial;
      mat.opacity = e.type === 'TEMPORAL' ? 0.35 : 0.3;
      mat.linewidth = 1;
    }
  }

  // ── Scene setup ─────────────────────────────────────────

  private initScene(): void {
    const container = this.canvasContainer.nativeElement;
    const width = container.clientWidth;
    const height = container.clientHeight;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(60, width / height, 0.1, 2000);
    this.camera.position.z = this.orbitRadius;

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;
    container.appendChild(this.renderer.domElement);

    this.scene.add(new THREE.AmbientLight(0xffffff, 0.4));
    const pointLight = new THREE.PointLight(0xffffff, 0.8, 200);
    pointLight.position.copy(this.camera.position);
    this.scene.add(pointLight);

    // Hexagonal wireframe grid
    this.gridGroup = new THREE.Group();
    this.createHexGrid();
    this.scene.add(this.gridGroup);

    // Cosmic dust particles (nebula)
    this.createDustField();

    const observer = new ResizeObserver(() => {
      const w = container.clientWidth;
      const h = container.clientHeight;
      this.camera.aspect = w / h;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(w, h);
    });
    observer.observe(container);
  }

  /** Hexagonal wireframe grid — sci-fi command center feel */
  private createHexGrid(): void {
    const gridMat = new THREE.LineBasicMaterial({
      color: 0x00ffcc,
      transparent: true,
      opacity: 0.03,
    });

    // Equatorial and meridian circles with hex feel
    for (let i = 0; i < 3; i++) {
      const curve = new THREE.EllipseCurve(0, 0, 45, 45, 0, Math.PI * 2, false, 0);
      const pts = curve.getPoints(6); // hexagonal shape
      pts.push(pts[0].clone()); // close the hexagon
      const geo = new THREE.BufferGeometry().setFromPoints(
        pts.map((p) => {
          if (i === 0) return new THREE.Vector3(p.x, 0, p.y);
          if (i === 1) return new THREE.Vector3(p.x, p.y, 0);
          return new THREE.Vector3(0, p.x, p.y);
        }),
      );
      this.gridGroup.add(new THREE.Line(geo, gridMat));
    }

    // Concentric hex rings
    for (const r of [15, 30, 60]) {
      const curve = new THREE.EllipseCurve(0, 0, r, r, 0, Math.PI * 2, false, 0);
      const pts = curve.getPoints(6);
      pts.push(pts[0].clone());
      const geo = new THREE.BufferGeometry().setFromPoints(
        pts.map((p) => new THREE.Vector3(p.x, 0, p.y)),
      );
      this.gridGroup.add(
        new THREE.Line(
          geo,
          new THREE.LineBasicMaterial({ color: 0x00ffcc, transparent: true, opacity: 0.02 }),
        ),
      );
    }
  }

  /** Background dust — dense nebula with slow color cycling */
  private createDustField(): void {
    const count = 800;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const sizes = new Float32Array(count);

    for (let i = 0; i < count; i++) {
      const r = 50 + Math.random() * 120;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(2 * Math.random() - 1);
      positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      positions[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
      positions[i * 3 + 2] = r * Math.cos(phi);

      // Cyan-teal palette
      const brightness = 0.2 + Math.random() * 0.4;
      colors[i * 3] = brightness * 0.3;
      colors[i * 3 + 1] = brightness * 0.8;
      colors[i * 3 + 2] = brightness * 1.0;

      sizes[i] = 0.06 + Math.random() * 0.12;
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    geo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    const mat = new THREE.PointsMaterial({
      size: 0.12,
      vertexColors: true,
      transparent: true,
      opacity: 0.5,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });

    this.dustParticles = new THREE.Points(geo, mat);
    this.scene.add(this.dustParticles);
  }

  /** Create shared particle textures (pooled, not per-particle) */
  private createParticleTextures(): void {
    // Core particle texture — bright cyan dot
    const size = 64;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;
    const cx = size / 2;
    const grad = ctx.createRadialGradient(cx, cx, 0, cx, cx, size / 2);
    grad.addColorStop(0.0, 'rgba(255, 255, 255, 1.0)');
    grad.addColorStop(0.15, 'rgba(0, 255, 204, 0.9)');
    grad.addColorStop(0.5, 'rgba(0, 255, 204, 0.3)');
    grad.addColorStop(1.0, 'rgba(0, 255, 204, 0)');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, size, size);
    this.particleTexture = new THREE.CanvasTexture(canvas);
    this.particleTexture.minFilter = THREE.LinearFilter;

    // Trail texture — dimmer, wider
    const trailCanvas = document.createElement('canvas');
    trailCanvas.width = size;
    trailCanvas.height = size;
    const tCtx = trailCanvas.getContext('2d')!;
    const tGrad = tCtx.createRadialGradient(cx, cx, 0, cx, cx, size / 2);
    tGrad.addColorStop(0.0, 'rgba(0, 255, 204, 0.4)');
    tGrad.addColorStop(0.5, 'rgba(0, 255, 204, 0.1)');
    tGrad.addColorStop(1.0, 'rgba(0, 255, 204, 0)');
    tCtx.fillStyle = tGrad;
    tCtx.fillRect(0, 0, size, size);
    this.trailTexture = new THREE.CanvasTexture(trailCanvas);
    this.trailTexture.minFilter = THREE.LinearFilter;
  }

  // ── Graph data loading ──────────────────────────────────

  private loadGraphData(): void {
    this.graphLoading.set(true);
    this.graphError.set(null);

    this.memoryService.getGraphOverview(NODES_PER_PAGE).subscribe({
      next: (response) => {
        this.buildGraphFromResponse(response.nodes, response.edges);
        this.graphLoading.set(false);
        this.log.info('GraphExplorer', `Graph loaded: ${response.nodes.length} nodes, ${response.edges.length} edges, ${this.edges.length} visual edges`);
      },
      error: (err) => {
        this.log.warn('GraphExplorer', 'Graph API unavailable:', err);
        this.graphError.set('Backend graph API unavailable — no memories to display');
        this.graphLoading.set(false);
      },
    });
  }

  private buildGraphFromResponse(apiNodes: GraphNode[], apiEdges: GraphEdge[]): void {
    if (apiNodes.length === 0) {
      this.graphError.set('No memories stored yet — add memories to see the graph');
      return;
    }

    this.addNodesToScene(apiNodes, false);
    this.addEdgesToScene(apiEdges);

    this.nodeCount.set(this.nodes.length);
    this.edgeCount.set(this.edges.length);
    this.loadedCount.set(this.nodes.length);
    this.computeGraphStats();
    this.updateTimestampRange();
  }

  /** Add nodes to the THREE.js scene — with optional warp-in animation */
  private addNodesToScene(apiNodes: GraphNode[], warpIn: boolean): void {
    const nodeIdMap = new Map<string, ExplorerNode>();
    for (const n of this.nodes) nodeIdMap.set(n.id, n);

    for (let i = 0; i < apiNodes.length; i++) {
      const n = apiNodes[i];
      if (nodeIdMap.has(n.id)) continue; // Skip duplicates

      const color = TIER_COLORS[n.tier] ?? 0x888888;
      const importance = Math.max(0.1, Math.min(1.0, n.importance / 10));
      const offset = this.nodes.length + i;

      // Volumetric golden spiral
      const golden = (1 + Math.sqrt(5)) / 2;
      const theta = (2 * Math.PI * offset) / golden;
      const phi = Math.acos(1 - (2 * (offset + 0.5)) / Math.max(1, apiNodes.length + this.nodes.length));
      const baseRadius = 15 + importance * 50;
      const jitter = 1.0 + Math.sin(offset * 7.31) * 0.4;
      const radius = baseRadius * jitter;

      const pos = new THREE.Vector3(
        radius * Math.sin(phi) * Math.cos(theta),
        radius * Math.sin(phi) * Math.sin(theta),
        radius * Math.cos(phi),
      );

      const size = 0.3 + importance * 0.6;

      // Star core sprite
      const starTex = this.createStarTexture(color, 1.0);
      const starMat = new THREE.SpriteMaterial({
        map: starTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const mesh = new THREE.Sprite(starMat);
      mesh.scale.set(warpIn ? 0 : size * 3, warpIn ? 0 : size * 3, 1);
      mesh.position.copy(pos);
      this.scene.add(mesh);

      // Outer glow halo sprite
      const glowTex = this.createStarTexture(color, 0.3);
      const glowMat = new THREE.SpriteMaterial({
        map: glowTex,
        transparent: true,
        depthTest: false,
        blending: THREE.AdditiveBlending,
      });
      const glowMesh = new THREE.Sprite(glowMat);
      glowMesh.scale.set(warpIn ? 0 : size * 8, warpIn ? 0 : size * 8, 1);
      glowMesh.position.copy(pos);
      this.scene.add(glowMesh);

      // Node label sprite
      const labelSprite = this.createNodeLabel(n.id, n.tier, importance, color);
      labelSprite.position.copy(pos);
      labelSprite.position.y += size * 3 + 0.8;
      this.scene.add(labelSprite);

      const explorerNode: ExplorerNode = {
        id: n.id,
        tier: n.tier,
        text: n.textPreview,
        importance: n.importance,
        valence: n.valence ?? 0,
        timestampMs: n.timestampMs ?? Date.now(),
        position: pos,
        velocity: new THREE.Vector3(
          (Math.random() - 0.5) * 0.005,
          (Math.random() - 0.5) * 0.005,
          (Math.random() - 0.5) * 0.005,
        ),
        mesh,
        glowMesh,
        labelSprite,
        selected: false,
        baseSize: size,
        visible: true,
        targetOpacity: 1.0,
      };
      this.nodes.push(explorerNode);
      nodeIdMap.set(n.id, explorerNode);
    }
  }

  /** Add edges to the scene */
  private addEdgesToScene(apiEdges: GraphEdge[]): void {
    let hCount = this.hebbianCount(), tCount = this.temporalCount(), eCount = this.entityCount();
    const nodeMap = new Map(this.nodes.map(n => [n.id, n]));

    for (const e of apiEdges) {
      const fromNode = nodeMap.get(e.fromId);
      const toNode = nodeMap.get(e.toId);
      if (!fromNode || !toNode) continue;

      // Skip if already exists
      if (this.edges.some(ex => ex.from === e.fromId && ex.to === e.toId && ex.type === e.type)) continue;

      if (e.type === 'HEBBIAN') hCount++;
      else if (e.type === 'TEMPORAL') tCount++;
      else if (e.type === 'ENTITY') eCount++;

      const color = EDGE_TYPE_COLORS[e.type] ?? 0x888888;
      const material =
        e.type === 'TEMPORAL'
          ? new THREE.LineDashedMaterial({
              color,
              transparent: true,
              opacity: 0.35,
              dashSize: 1,
              gapSize: 0.5,
            })
          : new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.3 });

      const geo = new THREE.BufferGeometry().setFromPoints([fromNode.position, toNode.position]);
      const line = new THREE.Line(geo, material);
      if (e.type === 'TEMPORAL') line.computeLineDistances();
      this.scene.add(line);

      let labelSprite: THREE.Sprite | undefined;
      if (e.type === 'ENTITY' && e.relation) {
        labelSprite = this.createEdgeLabel(e.relation, fromNode.position, toNode.position, color);
      }

      let weightSprite: THREE.Sprite | undefined;
      if (e.type === 'HEBBIAN' && e.weight > 0.05) {
        weightSprite = this.createWeightBadge(e.weight, fromNode.position, toNode.position);
      }

      this.edges.push({
        from: e.fromId,
        to: e.toId,
        type: e.type,
        weight: e.weight,
        relation: e.relation,
        line,
        labelSprite,
        weightSprite,
      });
    }

    this.hebbianCount.set(hCount);
    this.temporalCount.set(tCount);
    this.entityCount.set(eCount);
  }

  private computeGraphStats(): void {
    if (this.nodes.length === 0) return;
    const avgImp = this.nodes.reduce((s, n) => s + n.importance, 0) / this.nodes.length;
    this.avgImportance.set(avgImp);
    const maxPossibleEdges = (this.nodes.length * (this.nodes.length - 1)) / 2;
    this.densityRatio.set(maxPossibleEdges > 0 ? this.edges.length / maxPossibleEdges : 0);
  }

  private updateTimestampRange(): void {
    const timestamps = this.nodes.map(n => n.timestampMs).filter(t => t > 0);
    if (timestamps.length > 0) {
      this.oldestTimestamp.set(Math.min(...timestamps));
      this.newestTimestamp.set(Math.max(...timestamps));
      this.timestampMin.set(Math.min(...timestamps));
      this.timestampMax.set(Math.max(...timestamps));
    }
  }

  // ── Filter logic ────────────────────────────────────────

  /** Apply filters: visible nodes get targetOpacity=1, filtered-out get 0.04 (ghost) */
  private applyFilters(): void {
    const impMin = this.importanceMin();
    const impMax = this.importanceMax();
    const valMin = this.valenceMin();
    const valMax = this.valenceMax();
    const tsMin = this.timestampMin();
    const tsMax = this.timestampMax();

    for (const node of this.nodes) {
      const passImp = node.importance >= impMin && node.importance <= impMax;
      const passVal = node.valence >= valMin && node.valence <= valMax;
      const passTs = node.timestampMs >= tsMin && node.timestampMs <= tsMax;
      node.visible = passImp && passVal && passTs;
      node.targetOpacity = node.visible ? 1.0 : 0.04;
    }
  }

  // ── Neuron firing particle system ───────────────────────

  private spawnFiringParticle(): void {
    if (this.edges.length === 0 || this.particles.length >= MAX_PARTICLES) return;

    // Pick a random visible edge
    const visibleEdges = this.edges.filter(e => e.line.visible);
    if (visibleEdges.length === 0) return;
    const edge = visibleEdges[Math.floor(Math.random() * visibleEdges.length)];
    const edgeIndex = this.edges.indexOf(edge);
    const color = EDGE_TYPE_COLORS[edge.type] ?? 0x00ffcc;

    // Create colored particle texture
    const particleMat = new THREE.SpriteMaterial({
      map: this.particleTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthTest: false,
      color: new THREE.Color(color),
    });
    const mesh = new THREE.Sprite(particleMat);
    mesh.scale.set(1.5, 1.5, 1);
    this.scene.add(mesh);

    // Trail
    const trailMat = new THREE.SpriteMaterial({
      map: this.trailTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthTest: false,
      color: new THREE.Color(color),
    });
    const trailMesh = new THREE.Sprite(trailMat);
    trailMesh.scale.set(3, 3, 1);
    this.scene.add(trailMesh);

    this.particles.push({
      mesh,
      trailMesh,
      edgeIndex,
      progress: 0,
      speed: 0.015 + Math.random() * 0.025,
      alive: true,
      color,
    });
  }

  /** Fire a burst of particles along edges — simulates neurons firing */
  private fireBurst(count: number): void {
    const budget = Math.min(count, MAX_PARTICLES - this.particles.length);
    for (let i = 0; i < budget; i++) {
      this.spawnFiringParticle();
    }
  }

  /** Push a line to the debug console overlay */
  private pushDebugLine(tag: DebugLine['tag'], message: string): void {
    if (!this.showDebugConsole()) return;
    const now = new Date();
    const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
    const line: DebugLine = { id: ++debugLineId, time, tag, message };
    this.debugLines.update(lines => {
      const updated = [...lines, line];
      return updated.length > this.MAX_DEBUG_LINES
        ? updated.slice(updated.length - this.MAX_DEBUG_LINES)
        : updated;
    });
  }

  // ── Scientific label sprites ────────────────────────────

  private createNodeLabel(
    id: string,
    tier: string,
    importance: number,
    color: number,
  ): THREE.Sprite {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 512;
    canvas.height = 160;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const tierCode = tier.substring(0, 3).toUpperCase();
    const impPct = Math.round(importance * 100);
    const shortId = id.replace('mem-', '#');
    const displayText = `${tierCode} ${shortId}`;
    const impText = `⬤ ${impPct}%`;

    const hexColor = '#' + color.toString(16).padStart(6, '0');

    const textMetrics = (() => {
      ctx.font = 'bold 26px Consolas, "Courier New", monospace';
      return ctx.measureText(displayText);
    })();
    const pillW = Math.max(textMetrics.width + 32, 140);
    const pillH = 42;
    const pillX = (canvas.width - pillW) / 2;
    const pillY = 24;
    ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
    ctx.beginPath();
    ctx.roundRect(pillX, pillY, pillW, pillH, 10);
    ctx.fill();

    ctx.globalAlpha = 0.9;
    ctx.font = 'bold 26px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = hexColor;
    ctx.fillText(displayText, canvas.width / 2, pillY + pillH / 2);

    ctx.globalAlpha = 0.5;
    const barW = 80;
    const barH = 4;
    const barX = (canvas.width - barW) / 2;
    const barY = pillY + pillH + 10;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.12)';
    ctx.beginPath();
    ctx.roundRect(barX, barY, barW, barH, 2);
    ctx.fill();
    ctx.fillStyle = hexColor;
    ctx.beginPath();
    ctx.roundRect(barX, barY, barW * Math.min(1, importance), barH, 2);
    ctx.fill();

    ctx.globalAlpha = 0.7;
    ctx.font = '600 18px Consolas, "Courier New", monospace';
    ctx.fillStyle = '#bbc4dd';
    ctx.fillText(impText, canvas.width / 2, barY + barH + 16);

    ctx.globalAlpha = 1.0;

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(8, 2.5, 1);
    return sprite;
  }

  private createWeightBadge(weight: number, from: THREE.Vector3, to: THREE.Vector3): THREE.Sprite {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 192;
    canvas.height = 80;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.45)';
    ctx.beginPath();
    ctx.roundRect(40, 16, 112, 48, 10);
    ctx.fill();

    ctx.font = 'bold 28px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
    ctx.fillText(weight.toFixed(2), canvas.width / 2, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(4, 1.6, 1);

    const mid = new THREE.Vector3().addVectors(from, to).multiplyScalar(0.5);
    mid.y -= 1.0;
    sprite.position.copy(mid);
    this.scene.add(sprite);
    return sprite;
  }

  private createStarTexture(color: number, intensity: number): THREE.CanvasTexture {
    const size = 128;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    const r = (color >> 16) & 0xff;
    const g = (color >> 8) & 0xff;
    const b = color & 0xff;

    const cx = size / 2;
    const cy = size / 2;
    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2);

    if (intensity > 0.6) {
      grad.addColorStop(0.0, `rgba(255, 255, 255, ${intensity})`);
      grad.addColorStop(0.15, `rgba(${r}, ${g}, ${b}, ${intensity * 0.9})`);
      grad.addColorStop(0.4, `rgba(${r}, ${g}, ${b}, ${intensity * 0.4})`);
      grad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
    } else {
      grad.addColorStop(0.0, `rgba(${r}, ${g}, ${b}, ${intensity})`);
      grad.addColorStop(0.3, `rgba(${r}, ${g}, ${b}, ${intensity * 0.5})`);
      grad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
    }

    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, size, size);

    if (intensity > 0.6) {
      ctx.globalCompositeOperation = 'lighter';
      const spikeGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2);
      spikeGrad.addColorStop(0.0, `rgba(255, 255, 255, 0.4)`);
      spikeGrad.addColorStop(0.5, `rgba(${r}, ${g}, ${b}, 0.05)`);
      spikeGrad.addColorStop(1.0, `rgba(${r}, ${g}, ${b}, 0)`);
      ctx.fillStyle = spikeGrad;
      ctx.fillRect(0, cy - 1, size, 2);
      ctx.fillRect(cx - 1, 0, 2, size);
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    return texture;
  }

  private createEdgeLabel(
    text: string,
    from: THREE.Vector3,
    to: THREE.Vector3,
    color: number,
  ): THREE.Sprite {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 256;
    canvas.height = 40;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const hexColor = '#' + color.toString(16).padStart(6, '0');
    ctx.font = '600 16px Consolas, "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.globalAlpha = 0.85;
    ctx.fillStyle = hexColor;
    ctx.fillText(text, canvas.width / 2, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.minFilter = THREE.LinearFilter;
    const material = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(4, 0.7, 1);

    const midpoint = new THREE.Vector3().addVectors(from, to).multiplyScalar(0.5);
    midpoint.y += 0.4;
    sprite.position.copy(midpoint);

    this.scene.add(sprite);
    return sprite;
  }

  // ── Scene cleanup ───────────────────────────────────────

  private clearScene(): void {
    // Dispose nodes
    for (const node of this.nodes) {
      this.scene.remove(node.mesh);
      this.scene.remove(node.glowMesh);
      this.scene.remove(node.labelSprite);
      (node.mesh.material as THREE.SpriteMaterial).map?.dispose();
      node.mesh.material.dispose();
      (node.glowMesh.material as THREE.SpriteMaterial).map?.dispose();
      node.glowMesh.material.dispose();
      (node.labelSprite.material as THREE.SpriteMaterial).map?.dispose();
      node.labelSprite.material.dispose();
    }
    // Dispose edges
    for (const edge of this.edges) {
      this.scene.remove(edge.line);
      edge.line.geometry.dispose();
      if (edge.labelSprite) {
        this.scene.remove(edge.labelSprite);
        (edge.labelSprite.material as THREE.SpriteMaterial).map?.dispose();
        edge.labelSprite.material.dispose();
      }
      if (edge.weightSprite) {
        this.scene.remove(edge.weightSprite);
        (edge.weightSprite.material as THREE.SpriteMaterial).map?.dispose();
        edge.weightSprite.material.dispose();
      }
    }
    // Dispose particles
    for (const p of this.particles) {
      this.scene.remove(p.mesh);
      p.mesh.material.dispose();
      this.scene.remove(p.trailMesh);
      p.trailMesh.material.dispose();
    }
    this.nodes = [];
    this.edges = [];
    this.particles = [];
    this.nodeCount.set(0);
    this.edgeCount.set(0);
    this.selectedNode.set(null);
    this.selectedEdge.set(null);
  }

  // ── Animation loop ──────────────────────────────────────

  private animate(): void {
    this.animationId = requestAnimationFrame(() => this.animate());
    this.timer.update(performance.now());
    const delta = this.timer.getDelta();

    // Auto orbit when not dragging
    if (!this.isDragging) {
      this.orbitTheta += 0.02 * delta;
    }

    // Fly-to animation
    if (this.flyToPos && this.flyProgress < 1) {
      this.flyProgress = Math.min(1, this.flyProgress + delta * 2.0);
      const t = this.easeInOutCubic(this.flyProgress);
      this.lookAtTarget.lerpVectors(this.flyFromPos, this.flyToPos, t);
      this.orbitRadius = this.flyFromRadius + (this.flyToRadius - this.flyFromRadius) * t;
      if (this.flyProgress >= 1) {
        this.lookAtTarget.copy(this.flyToPos);
        this.orbitRadius = this.flyToRadius;
        this.flyToPos = null;
      }
    }

    this.camera.position.x =
      this.lookAtTarget.x + this.orbitRadius * Math.sin(this.orbitPhi) * Math.cos(this.orbitTheta);
    this.camera.position.y = this.lookAtTarget.y + this.orbitRadius * Math.cos(this.orbitPhi);
    this.camera.position.z =
      this.lookAtTarget.z + this.orbitRadius * Math.sin(this.orbitPhi) * Math.sin(this.orbitTheta);
    this.camera.lookAt(this.lookAtTarget);

    // Animate dust rotation
    if (this.dustParticles) {
      this.dustParticles.rotation.y += 0.001 * delta;
    }

    // Apply filters
    this.applyFilters();

    const showNodeLabels = this.showLabels();
    const camPos = this.camera.position;
    const time = Date.now() * 0.001;

    // ── Animate nodes ──
    for (let i = 0; i < this.nodes.length; i++) {
      const node = this.nodes[i];
      node.position.add(node.velocity);
      node.mesh.position.copy(node.position);
      node.glowMesh.position.copy(node.position);
      if (node.position.length() > 120) node.velocity.multiplyScalar(-1);

      // Ghost-fade animation: smoothly lerp opacity
      const coreMat = node.mesh.material as THREE.SpriteMaterial;
      const glowMat = node.glowMesh.material as THREE.SpriteMaterial;
      const labelMat = node.labelSprite.material as THREE.SpriteMaterial;

      const currentOpacity = coreMat.opacity;
      const targetOpacity = node.targetOpacity;
      const newOpacity = currentOpacity + (targetOpacity - currentOpacity) * Math.min(1, delta * 4);
      coreMat.opacity = newOpacity;
      glowMat.opacity = newOpacity * 0.5;
      labelMat.opacity = node.visible ? 1.0 : 0.0;

      // Star pulsing — recall mode matched nodes get sonar pulse
      const isRecallMatched = this.recallMode() && this.recallMatchedIds().has(node.id);
      const pulseAmp = isRecallMatched ? 0.5 : 0.15;
      const pulseSpeed = isRecallMatched ? 3.0 : 1.5;
      const pulse = 1.0 + pulseAmp * Math.sin(time * pulseSpeed + i * 0.7);
      const coreScale = node.baseSize * 3 * pulse * (node.visible ? 1.0 : 0.5);
      node.mesh.scale.set(coreScale, coreScale, 1);
      const glowMultiplier = isRecallMatched ? 12 : 8;
      const glowScale = node.baseSize * glowMultiplier * pulse * (node.visible ? 1.0 : 0.3);
      node.glowMesh.scale.set(glowScale, glowScale, 1);

      // Warp-in: scale from 0 to target
      if (coreScale > 0 && node.mesh.scale.x < node.baseSize * 2) {
        const warpScale = Math.min(node.baseSize * 3, node.mesh.scale.x + delta * node.baseSize * 10);
        node.mesh.scale.set(warpScale, warpScale, 1);
        node.glowMesh.scale.set(warpScale * 2.5, warpScale * 2.5, 1);
      }

      // Label position
      node.labelSprite.position.copy(node.position);
      node.labelSprite.position.y += node.baseSize * 4 + 2.0;
      node.labelSprite.visible = showNodeLabels && node.visible;

      if (showNodeLabels && node.visible) {
        const dist = camPos.distanceTo(node.labelSprite.position);
        const s = dist * 0.06;
        node.labelSprite.scale.set(s * 3.2, s, 1);
      }
    }

    // ── Animate edges ──
    for (const edge of this.edges) {
      const fromNode = this.nodes.find((n) => n.id === edge.from);
      const toNode = this.nodes.find((n) => n.id === edge.to);
      if (fromNode && toNode) {
        const positions = edge.line.geometry.attributes['position'] as THREE.BufferAttribute;
        positions.setXYZ(0, fromNode.position.x, fromNode.position.y, fromNode.position.z);
        positions.setXYZ(1, toNode.position.x, toNode.position.y, toNode.position.z);
        positions.needsUpdate = true;

        if (edge.labelSprite) {
          const mid = new THREE.Vector3()
            .addVectors(fromNode.position, toNode.position)
            .multiplyScalar(0.5);
          mid.y += 0.4;
          edge.labelSprite.position.copy(mid);
          const dist = camPos.distanceTo(mid);
          const s = dist * 0.035;
          edge.labelSprite.scale.set(s * 2.5, s * 0.5, 1);
        }
        if (edge.weightSprite) {
          const mid = new THREE.Vector3()
            .addVectors(fromNode.position, toNode.position)
            .multiplyScalar(0.5);
          mid.y -= 1.2;
          edge.weightSprite.position.copy(mid);
          const dist = camPos.distanceTo(mid);
          const s = dist * 0.04;
          edge.weightSprite.scale.set(s * 2.5, s, 1);
        }
      }

      // Layer visibility + filter: both endpoints must be visible
      const bothVisible = fromNode?.visible && toNode?.visible;
      const layerVisible =
        (edge.type === 'HEBBIAN' && this.showHebbian()) ||
        (edge.type === 'TEMPORAL' && this.showTemporal()) ||
        (edge.type === 'ENTITY' && this.showEntity());
      edge.line.visible = layerVisible && !!bothVisible;
      if (edge.labelSprite) {
        edge.labelSprite.visible = edge.line.visible && this.orbitRadius < 150;
      }
      if (edge.weightSprite) {
        edge.weightSprite.visible = edge.line.visible && showNodeLabels;
      }
    }

    // ── Neuron firing particles ──
    // Real SSE events / REST callbacks drive bursts via effects.
    // Ambient disabled for testing.

    for (const particle of this.particles) {
      if (!particle.alive) continue;

      particle.progress += particle.speed;
      if (particle.progress >= 1) {
        particle.alive = false;
        this.scene.remove(particle.mesh);
        particle.mesh.material.dispose();
        this.scene.remove(particle.trailMesh);
        particle.trailMesh.material.dispose();
        continue;
      }

      const edge = this.edges[particle.edgeIndex];
      if (!edge || !edge.line.visible) {
        particle.alive = false;
        this.scene.remove(particle.mesh);
        particle.mesh.material.dispose();
        this.scene.remove(particle.trailMesh);
        particle.trailMesh.material.dispose();
        continue;
      }

      const fromNode = this.nodes.find(n => n.id === edge.from);
      const toNode = this.nodes.find(n => n.id === edge.to);
      if (!fromNode || !toNode) continue;

      const pos = new THREE.Vector3();
      pos.lerpVectors(fromNode.position, toNode.position, particle.progress);
      particle.mesh.position.copy(pos);

      // Trail follows behind
      const trailProgress = Math.max(0, particle.progress - 0.1);
      particle.trailMesh.position.lerpVectors(fromNode.position, toNode.position, trailProgress);

      // Fade in/out along path
      const alpha = Math.sin(particle.progress * Math.PI);
      (particle.mesh.material as THREE.SpriteMaterial).opacity = alpha * 0.95;
      (particle.trailMesh.material as THREE.SpriteMaterial).opacity = alpha * 0.3;
      particle.mesh.scale.setScalar(1.0 + alpha * 0.8);

      // Pulse the source/destination nodes when particle arrives
      if (particle.progress > 0.85) {
        const glowMat = toNode.glowMesh.material as THREE.SpriteMaterial;
        glowMat.opacity = Math.min(1, glowMat.opacity + delta * 2);
        toNode.glowMesh.scale.setScalar(toNode.baseSize * 10);
      }
      if (particle.progress < 0.15) {
        const glowMat = fromNode.glowMesh.material as THREE.SpriteMaterial;
        glowMat.opacity = Math.min(1, glowMat.opacity + delta * 2);
        fromNode.glowMesh.scale.setScalar(fromNode.baseSize * 10);
      }
    }

    // Clean up dead particles
    this.particles = this.particles.filter(p => p.alive);

    this.renderer.render(this.scene, this.camera);
  }
}
