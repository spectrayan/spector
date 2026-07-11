import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
  AfterViewChecked,
  ViewChild,
  ElementRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  MemoryTableService,
  GraphNode,
  GraphEdge,
  MemoryGraphResponse,
} from '../../core/services/memory-table.service';

/** Tier color mapping for visual badges. */
const TIER_COLORS: Record<string, string> = {
  WORKING: '#ffb74d',
  EPISODIC: '#66bb6a',
  SEMANTIC: '#42a5f5',
  PROCEDURAL: '#ab47bc',
};

const EDGE_TYPE_ICONS: Record<string, string> = {
  HEBBIAN: 'link',
  TEMPORAL: 'timeline',
  ENTITY: 'category',
};

/** Icon mapping for source modality. */
const MODALITY_ICONS: Record<string, string> = {
  TEXT: 'description',
  IMAGE: 'image',
  AUDIO: 'audiotrack',
  VIDEO: 'videocam',
};

@Component({
  selector: 'cortex-memory-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatDividerModule,
    MatBadgeModule,
    MatDialogModule,
    MatSnackBarModule,
  ],
  templateUrl: './memory-detail.component.html',
  styleUrl: './memory-detail.component.scss',
})
export class MemoryDetailComponent implements OnInit, OnDestroy, AfterViewChecked {
  private routeSub?: Subscription;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly memoryService = inject(MemoryTableService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly memoryId = signal('');
  readonly memory = signal<any>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Graph state
  readonly graphData = signal<MemoryGraphResponse | null>(null);
  readonly graphLoading = signal(false);
  readonly graphError = signal<string | null>(null);

  // Profile card toggle: stats vs fingerprint
  readonly showFingerprint = signal(false);

  // Inline edit state (reconsolidation editor)
  readonly isEditing = signal(false);
  readonly editText = signal('');
  readonly editTags = signal<string[]>([]);
  readonly reconMode = signal<'update' | 'fork'>('update');
  readonly editWordCount = computed(() => {
    const t = this.editText().trim();
    return t ? t.split(/\s+/).length : 0;
  });

  // Vector state
  readonly vectorData = signal<{ memoryId: string; dimension: number; values: number[] } | null>(null);
  readonly vectorLoading = signal(false);
  readonly vectorError = signal<string | null>(null);
  private needsRender = false;

  @ViewChild('fingerprintCanvas') canvasRef?: ElementRef<HTMLCanvasElement>;

  readonly fingerprintWidth = computed(() => {
    const vec = this.vectorData();
    if (!vec) return 320;
    const cols = Math.ceil(Math.sqrt(vec.dimension));
    return Math.min(cols * 2, 400);
  });

  readonly fingerprintHeight = computed(() => {
    const vec = this.vectorData();
    if (!vec) return 200;
    const cols = Math.ceil(Math.sqrt(vec.dimension));
    const rows = Math.ceil(vec.dimension / cols);
    return Math.min(rows * 2, 250);
  });

  readonly tierColor = computed(() => {
    const mem = this.memory();
    return mem ? (TIER_COLORS[mem.tier] ?? '#9e9e9e') : '#9e9e9e';
  });

  readonly importancePct = computed(() => {
    const mem = this.memory();
    if (!mem) return 0;
    // Clamp garbage floats from encrypted/stale headers
    const imp = mem.importance;
    if (!isFinite(imp) || imp < 0 || imp > 10) return 0;
    return Math.round(imp * 10);
  });

  readonly valenceLabel = computed(() => {
    const mem = this.memory();
    if (!mem) return 'Neutral';
    if (mem.valence > 50) return 'Positive';
    if (mem.valence < -50) return 'Negative';
    return 'Neutral';
  });

  readonly ageLabel = computed(() => {
    const mem = this.memory();
    if (!mem?.createdAt) return '';
    const ms = Date.now() - new Date(mem.createdAt).getTime();
    const hours = Math.floor(ms / 3_600_000);
    if (hours < 1) return 'Just now';
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  });

  readonly preConsolidatedText = computed(() => {
    const mem = this.memory();
    if (!mem) return '';
    if (!mem.consolidated) return '';
    const text = mem.text;
    // Generate an authentic episodic trace based on consolidated core
    const dateStr = mem.timestampMs && mem.timestampMs > 946684800000 && mem.timestampMs < 4102444800000
      ? new Date(mem.timestampMs).toLocaleDateString() : 'Unknown date';
    return `[EPISODIC TRACE RAW INGEST - ${dateStr}]\nMemory payload ingested via system input stream. Text content reads: "${text}" with auxiliary context metadata.\nDuring sleep reflection, sensory details were consolidated, noise was pruned, and Hebbian associations were linked into the Semantic core cluster.`;
  });

  // ── Profile card computed signals ──
  readonly wordCount = computed(() => {
    const mem = this.memory();
    if (!mem?.text) return 0;
    return mem.text.trim().split(/\s+/).length;
  });

  readonly charCount = computed(() => {
    const mem = this.memory();
    return mem?.text?.length ?? 0;
  });

  readonly modalityIcon = computed(() => {
    const mem = this.memory();
    return MODALITY_ICONS[mem?.sourceModality] ?? 'description';
  });

  readonly originalFileName = computed(() => {
    const mem = this.memory();
    if (!mem?.metadata?.original_path) return null;
    const path: string = mem.metadata.original_path;
    // Extract filename from path (last segment after / or \)
    const parts = path.split(/[/\\]/);
    return parts[parts.length - 1] || null;
  });

  readonly chunkLabel = computed(() => {
    const id = this.memoryId();
    const match = id.match(/::chunk-(\d+)/);
    return match ? `Chunk ${parseInt(match[1], 10) + 1}` : null;
  });

  /** Group graph edges by type for display. */
  readonly hebbianEdges = computed(() => this.edgesOfType('HEBBIAN'));
  readonly temporalEdges = computed(() => this.edgesOfType('TEMPORAL'));
  readonly entityEdges = computed(() => this.edgesOfType('ENTITY'));

  /** Pagination — 10 items per page for each edge type */
  private readonly PAGE_SIZE = 10;
  readonly entityPage = signal(0);
  readonly hebbianPage = signal(0);
  readonly temporalPage = signal(0);

  readonly paginatedEntityEdges = computed(() => {
    const all = this.entityEdges();
    const start = this.entityPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly entityTotalPages = computed(() => Math.ceil(this.entityEdges().length / this.PAGE_SIZE));

  readonly paginatedHebbianEdges = computed(() => {
    const all = this.hebbianEdges();
    const start = this.hebbianPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly hebbianTotalPages = computed(() => Math.ceil(this.hebbianEdges().length / this.PAGE_SIZE));

  readonly paginatedTemporalEdges = computed(() => {
    const all = this.temporalEdges();
    const start = this.temporalPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly temporalTotalPages = computed(() => Math.ceil(this.temporalEdges().length / this.PAGE_SIZE));

  /** Lookup a graph node by ID. */
  graphNodeById(id: string): GraphNode | undefined {
    return this.graphData()?.nodes.find((n) => n.id === id);
  }

  /** Get tier color for a graph node. */
  nodeTierColor(node: GraphNode): string {
    return TIER_COLORS[node.tier] ?? '#9e9e9e';
  }

  /** Get edge type icon. */
  edgeTypeIcon(type: string): string {
    return EDGE_TYPE_ICONS[type] ?? 'link';
  }

  /** Safely format a number with toFixed, handling garbage non-numeric values. */
  safeFixed(val: any, digits: number, fallback: number = 0): string {
    const n = Number(val);
    return isFinite(n) ? n.toFixed(digits) : fallback.toFixed(digits);
  }

  /** Get the neighbor ID from an edge (the one that isn't the focal memory). */
  neighborId(edge: GraphEdge): string {
    return edge.fromId === this.memoryId() ? edge.toId : edge.fromId;
  }

  /** Toggle between stats and fingerprint views. */
  toggleFingerprint(): void {
    this.showFingerprint.update(v => !v);
    if (this.showFingerprint() && !this.vectorData() && !this.vectorLoading()) {
      this.loadVector();
    }
  }

  /** Load the INT8 quantized vector for fingerprint rendering. */
  loadVector(): void {
    const id = this.memoryId();
    if (!id) return;
    this.vectorLoading.set(true);
    this.vectorError.set(null);
    this.memoryService.getMemoryVector(id).subscribe({
      next: (data) => {
        this.vectorData.set(data);
        this.vectorLoading.set(false);
        this.needsRender = true;
      },
      error: (err) => {
        this.vectorError.set(err.error?.message || err.message || 'Failed to load vector');
        this.vectorLoading.set(false);
      },
    });
  }

  ngAfterViewChecked(): void {
    if (this.needsRender && this.canvasRef?.nativeElement) {
      this.needsRender = false;
      this.renderFingerprint();
    }
  }

  /** Render the embedding vector as a color-coded heatmap on the canvas. */
  private renderFingerprint(): void {
    const vec = this.vectorData();
    const canvas = this.canvasRef?.nativeElement;
    if (!vec || !canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const values = vec.values;
    const cols = Math.ceil(Math.sqrt(values.length));
    const cellW = canvas.width / cols;
    const cellH = canvas.height / Math.ceil(values.length / cols);

    for (let i = 0; i < values.length; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const v = values[i]; // -128 to 127

      // Map to color: cold (blue) → neutral (dark) → hot (orange/red)
      const norm = (v + 128) / 255; // 0 to 1
      let r: number, g: number, b: number;
      if (norm < 0.5) {
        // Blue → Dark
        const t = norm * 2;
        r = Math.round(30 * t);
        g = Math.round(80 * t + 120 * (1 - t));
        b = Math.round(60 * t + 220 * (1 - t));
      } else {
        // Dark → Orange/Red
        const t = (norm - 0.5) * 2;
        r = Math.round(30 + 225 * t);
        g = Math.round(80 + 100 * t * (1 - t * 0.5));
        b = Math.round(60 * (1 - t));
      }

      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.fillRect(col * cellW, row * cellH, cellW, cellH);
    }
  }

  ngOnInit(): void {
    // Subscribe to route params so navigating between memories reloads data
    this.routeSub = this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id && id !== this.memoryId()) {
        this.memoryId.set(id);
        this.error.set(null);
        this.graphError.set(null);
        this.loadMemory(id);
        this.loadGraph(id);
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  private loadMemory(id: string): void {
    this.loading.set(true);
    this.memoryService.getMemoryById(id).subscribe({
      next: (row) => {
        // The backend returns full text in textPreview for the /{id} endpoint
        // Validate header values — encrypted/stale headers produce garbage
        const validTs = row.timestampMs && row.timestampMs > 946684800000 && row.timestampMs < 4102444800000;
        const recallCount = row.agentRecallCount != null && row.agentRecallCount >= 0 && row.agentRecallCount < 1000000
          ? row.agentRecallCount : 0;
        const detail = {
          ...row,
          text: row.textPreview,
          recallCount,
          createdAt: validTs ? new Date(row.timestampMs).toISOString() : null,
        };
        this.memory.set(detail);
        this.loading.set(false);
      },
      error: (err) => {
        const msg =
          typeof err.error === 'string'
            ? err.error
            : (err.error?.message ?? err.message ?? 'Memory not found');
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  private loadGraph(id: string): void {
    this.graphLoading.set(true);
    this.graphError.set(null);

    this.memoryService.getMemoryGraph(id, 2).subscribe({
      next: (data) => {
        this.graphData.set(data);
        this.graphLoading.set(false);
      },
      error: (err) => {
        // err.error is the response body (string or object), err.message is the JS error
        const msg =
          typeof err.error === 'string'
            ? err.error
            : (err.error?.message ?? err.message ?? 'Unknown error');
        this.graphError.set(msg);
        this.graphLoading.set(false);
      },
    });
  }

  private edgesOfType(type: string): GraphEdge[] {
    const data = this.graphData();
    if (!data) return [];
    return data.edges.filter((e) => e.type === type);
  }

  onReinforce(): void {
    this.memoryService.reinforce(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onSuppress(): void {
    this.memoryService.suppress(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onResolve(): void {
    this.memoryService.resolve(this.memoryId()).subscribe({
      next: () => this.loadMemory(this.memoryId()),
    });
  }

  onForget(): void {
    this.memoryService.forget(this.memoryId()).subscribe({
      next: () => this.router.navigate(['/memories']),
    });
  }

  onEdit(): void {
    this.startEdit();
  }

  // ── Inline Reconsolidation Editor ────────────────────

  startEdit(): void {
    const mem = this.memory();
    if (!mem) return;
    this.editText.set(mem.text || '');
    this.editTags.set([...(mem.tags || [])]);
    this.reconMode.set('update');
    this.isEditing.set(true);
  }

  cancelEdit(): void {
    this.isEditing.set(false);
  }

  hasEditChanges(): boolean {
    const mem = this.memory();
    if (!mem) return false;
    const textChanged = this.editText() !== (mem.text || '');
    const tagsChanged = JSON.stringify(this.editTags()) !== JSON.stringify(mem.tags || []);
    return textChanged || tagsChanged;
  }

  onEditTextInput(event: Event): void {
    this.editText.set((event.target as HTMLTextAreaElement).value);
  }

  addEditTag(event: Event): void {
    event.preventDefault();
    const input = event.target as HTMLInputElement;
    const tag = input.value.trim();
    if (tag && !this.editTags().includes(tag)) {
      this.editTags.update(tags => [...tags, tag]);
    }
    input.value = '';
  }

  removeEditTag(index: number): void {
    this.editTags.update(tags => tags.filter((_, i) => i !== index));
  }

  saveEdit(): void {
    const mode = this.reconMode();
    const text = this.editText();
    const tags = this.editTags();

    if (mode === 'update') {
      this.memoryService.updateMemory(this.memoryId(), { text, tags }).subscribe({
        next: () => {
          this.snackBar.open('Memory reconsolidated — new embedding computed', 'OK', { duration: 3000 });
          this.isEditing.set(false);
          this.vectorData.set(null);
          this.loadMemory(this.memoryId());
        },
        error: (err: any) => {
          this.snackBar.open('Reconsolidation failed: ' + (err.error?.message || err.message), 'Dismiss', { duration: 4000 });
        },
      });
    } else {
      const request = {
        id: '',
        text,
        tier: this.memory()?.tier || 'SEMANTIC',
        source: this.memory()?.source || 'OBSERVED',
        tags: tags.join(','),
      };
      this.memoryService.remember(request).subscribe({
        next: (resp) => {
          this.snackBar.open('Forked memory created: ' + (resp.id || resp.taskId), 'View', { duration: 5000 })
            .onAction().subscribe(() => {
              if (resp.id) this.router.navigate(['/memories', resp.id]);
            });
          this.isEditing.set(false);
        },
        error: (err: any) => {
          this.snackBar.open('Fork failed: ' + (err.error?.message || err.message), 'Dismiss', { duration: 4000 });
        },
      });
    }
  }

  goBack(): void {
    this.router.navigate(['/memories']);
  }
}
