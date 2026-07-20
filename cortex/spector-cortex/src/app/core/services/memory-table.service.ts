import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/** A single memory row from the backend. */
export interface MemoryRow {
  id: string;
  text: string;
  textPreview: string;
  tier: string;
  source: string;
  importance: number;
  valence: number;
  arousal: number;
  timestampMs: number;
  agentRecallCount: number;
  recallCount: number;
  tombstoned: boolean;
  suppressed: boolean;
  pinned: boolean;
  resolved: boolean;
  consolidated: boolean;
  tags: string[];
  synapticTags: number;
  createdAt: string;
  metadata?: Record<string, string> | null;
}

/** Paginated table response from the backend. */
export interface MemoryTableResponse {
  rows: MemoryRow[];
  totalCount: number;
  page: number;
  pageSize: number;
  tierCounts: Record<string, number>;
  tombstoneRatios: Record<string, number>;
}

/** Compaction result from vacuum endpoint. */
export interface CompactionResult {
  tier: string;
  beforeCount: number;
  afterCount: number;
  tombstonesRemoved: number;
  bytesReclaimed: number;
  durationMs: number;
}

/** Request DTO for remember. */
export interface RememberRequest {
  id: string;
  text: string;
  tier?: string;
  source?: string;
  tags?: string;
  interest?: number;
  challenge?: number;
  urgency?: number;
  valence?: number;
  arousal?: number;
}

/** Request DTO for updating an existing memory. */
export interface UpdateMemoryRequest {
  text?: string;
  tier?: string;
  source?: string;
  tags?: string[];
  importance?: number;
  valence?: number;
  arousal?: number;
}

/** Memory status from backend. */
export interface MemoryStatus {
  totalMemories: number;
  tierCounts: Record<string, number>;
  hebbianEdges: number;
  temporalLinks: number;
  entityNodes: number;
  entityEdges: number;
}

/** Score breakdown from the recall pipeline. */
export interface ScoreBreakdown {
  similarity: number;
  importanceDecay: number;
  tagBoostFactor: number;
  habituationPenalty: number;
  graphBoost: number;
  valenceAlignment: number;
  finalScore: number;
}

/** A single recall result from the backend. */
export interface RecallResult {
  id: string;
  text: string;
  score: number;
  importance: number;
  ageDays: number;
  agentRecallCount: number;
  valence: number;
  memoryType: string;
  source: string;
  synapticTags: string[];
  breakdown: ScoreBreakdown;
  lateral: boolean;
  negativeOutcome: boolean;
  hyperfocused: boolean;
  positivelyReinforced: boolean;
}

/** Full recall response from the backend. */
export interface RecallResponse {
  results: RecallResult[];
  totalMemories: number;
  queryTimeMs: number;
  profile: string;
}

/** A node in the memory graph. */
export interface GraphNode {
  id: string;
  tier: string;
  textPreview: string;
  importance: number;
  valence: number;
  timestampMs: number;
  entityNames?: string[];
}

/** An edge in the memory graph. */
export interface GraphEdge {
  fromId: string;
  toId: string;
  type: 'HEBBIAN' | 'TEMPORAL' | 'ENTITY';
  relation: string | null;
  weight: number;
  fromEntityType?: string;
  toEntityType?: string;
}

/** Graph response from the backend. */
export interface MemoryGraphResponse {
  memoryId: string | null;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** Topology stats response from the backend. */
export interface EntityTypeStats {
  type: string;
  nodes: number;
  edges: number;
  memories: number;
}

export interface RelationTypeStats {
  type: string;
  edges: number;
  nodes: number;
  memories: number;
}

export interface TopologyStatsResponse {
  entityTypes: EntityTypeStats[];
  relationTypes: RelationTypeStats[];
}

export interface IndexStats {
  totalEntries: number;
  levels: number;
  recallEstimate: number;
}

export interface ConsolidationStats {
  lastRunTimestamp: number;
  memoriesMerged: number;
  duplicatesRemoved: number;
  partitionsCompacted: number;
}

export interface MemoryStats {
  totalCount: number;
  tierDistribution: { [key: string]: number };
  storageBytes: number;
  indexStats: IndexStats;
  consolidationStats: ConsolidationStats;
  growthOverTime: { [key: string]: number };
  decayForecast: { [key: string]: number };
}

export interface ScoringStats {
  avgSimilarity: number;
  avgRecency: number;
  avgFrequency: number;
  avgImportance: number;
  avgValence: number;
}

/** Response from async remember/ingest endpoints (202 Accepted). */
export interface AcceptedResponse {
  taskId: string;
  id?: string;
  fileName?: string;
  documentId?: string;
  path?: string;
  status: 'accepted';
}

/** Response from file ingestion endpoint (legacy — kept for backward compat). */
export interface FileIngestResponse {
  status: string;
  fileName: string;
  chunksStored: number;
  durationMs: number;
}


/** Task status from GET /memory/tasks/{taskId}. */
export interface TaskStatusResponse {
  taskId: string;
  description: string;
  type: string;
  status: 'ACCEPTED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  chunksStored: number;
  totalChunks: number;
  failures: number;
  progressPercent: number;
  durationMs: number;
  startedAt: string;
}

@Injectable({ providedIn: 'root' })
export class MemoryTableService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/memory`;

  // ── State signals ──
  readonly page = signal(0);
  readonly pageSize = signal(50);
  readonly tierFilter = signal<string | null>(null);
  readonly showTombstoned = signal(false);
  readonly sortField = signal<string>('timestampMs');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // ── Data signals ──
  readonly rows = signal<MemoryRow[]>([]);
  readonly totalCount = signal(0);
  readonly tierCounts = signal<Record<string, number>>({});
  readonly tombstoneRatios = signal<Record<string, number>>({});
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Derived: total pages */
  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalCount() / this.pageSize()))
  );

  // ══════════════════════════════════════════════════════════════
  // READ
  // ══════════════════════════════════════════════════════════════

  /** Fetches a page of memory rows from the backend. */
  loadPage(): void {
    this.loading.set(true);
    this.error.set(null);

    let params = new HttpParams()
      .set('page', this.page().toString())
      .set('pageSize', this.pageSize().toString())
      .set('tombstoned', this.showTombstoned().toString());

    const tier = this.tierFilter();
    if (tier) {
      params = params.set('tier', tier);
    }

    this.http.get<MemoryTableResponse>(`${this.API}/table`, { params })
      .subscribe({
        next: (resp) => {
          const sorted = this.sortRows(resp.rows);
          this.rows.set(sorted);
          this.totalCount.set(resp.totalCount);
          this.tierCounts.set(resp.tierCounts);
          this.tombstoneRatios.set(resp.tombstoneRatios);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message || 'Failed to load memory table');
          this.loading.set(false);
        },
      });
  }

  /** Returns a table page as an Observable (for use by detail view). */
  getTable(page: number, pageSize: number, tier: string, tombstoned: boolean): Observable<MemoryTableResponse> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('pageSize', pageSize.toString())
      .set('tombstoned', tombstoned.toString());
    if (tier) params = params.set('tier', tier);
    return this.http.get<MemoryTableResponse>(`${this.API}/table`, { params });
  }

  /** Returns full detail for a single memory by ID. */
  getMemoryById(id: string): Observable<MemoryRow> {
    return this.http.get<MemoryRow>(`${this.API}/${encodeURIComponent(id)}`);
  }

  /** Returns memory system status. */
  getStatus(): Observable<MemoryStatus> {
    return this.http.get<MemoryStatus>(`${this.API}/status`);
  }

  // ══════════════════════════════════════════════════════════════
  // GRAPH API
  // ══════════════════════════════════════════════════════════════

  /** Returns the graph neighborhood for a specific memory. */
  getMemoryGraph(id: string, depth: number = 2): Observable<MemoryGraphResponse> {
    const params = new HttpParams().set('depth', depth.toString());
    return this.http.get<MemoryGraphResponse>(`${this.API}/${encodeURIComponent(id)}/graph`, { params });
  }

  /** Returns a sampled overview of the entire memory graph. */
  getGraphOverview(maxNodes: number = 100): Observable<MemoryGraphResponse> {
    const params = new HttpParams().set('maxNodes', maxNodes.toString());
    return this.http.get<MemoryGraphResponse>(`${this.API}/graph/overview`, { params });
  }

  /** Returns topology statistics for entity and relation types. */
  getTopologyStats(): Observable<TopologyStatsResponse> {
    return this.http.get<TopologyStatsResponse>(`${this.API}/topology-stats`);
  }

  /** Returns memory database statistics. */
  getMemoryStats(): Observable<MemoryStats> {
    return this.http.get<MemoryStats>(`${this.API}/stats`);
  }

  /** Returns average scoring weights. */
  getScoringStats(): Observable<ScoringStats> {
    return this.http.get<ScoringStats>(`${this.API}/stats/scoring`);
  }

  /** Triggers a sleep consolidation (reflect) cycle. */
  consolidate(): Observable<any> {
    return this.http.post<any>(`${this.API}/reflect`, {});
  }

  // ══════════════════════════════════════════════════════════════
  // WRITE — CRUD
  // ══════════════════════════════════════════════════════════════

  /** Stores a new memory (async — returns 202 Accepted with taskId). */
  remember(request: RememberRequest): Observable<AcceptedResponse> {
    return this.http.post<AcceptedResponse>(`${this.API}/remember`, request);
  }

  /**
   * Reconsolidates a memory: creates a new version with updated content,
   * then suppresses the old version. Mimics biological memory reconsolidation.
   *
   * Returns the AcceptedResponse for the NEW memory (with its new ID).
   */
  reconsolidate(oldId: string, request: UpdateMemoryRequest): Observable<AcceptedResponse> {
    const newId = `${oldId}-v-${Date.now().toString(36)}`;
    const rememberReq: RememberRequest = {
      id: newId,
      text: request.text ?? '',
      tier: request.tier,
      source: request.source,
      tags: request.tags?.join(','),
      interest: request.importance,
      valence: request.valence,
      arousal: request.arousal,
    };

    // 1. Remember the new version, then 2. suppress the old one
    return this.http.post<AcceptedResponse>(`${this.API}/remember`, rememberReq).pipe(
      switchMap((resp) =>
        this.suppress(oldId, `Superseded by ${newId}`).pipe(
          map(() => resp),
        ),
      ),
    );
  }

  /** Uploads a file for chunked ingestion (async — returns 202 Accepted). */
  ingestFile(file: File, tier: string = 'SEMANTIC', source: string = 'OBSERVED'): Observable<AcceptedResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('tier', tier);
    formData.append('source', source);
    return this.http.post<AcceptedResponse>(`${this.API}/ingest-file`, formData);
  }



  /** Returns all active/recent ingestion tasks. */
  getTasks(): Observable<TaskStatusResponse[]> {
    return this.http.get<TaskStatusResponse[]>(`${this.API}/tasks`);
  }

  /** Returns status of a single task. */
  getTaskStatus(taskId: string): Observable<TaskStatusResponse> {
    return this.http.get<TaskStatusResponse>(`${this.API}/tasks/${encodeURIComponent(taskId)}`);
  }

  /** Tombstones (soft-deletes) a memory. */
  forget(id: string): Observable<string> {
    return this.http.delete(`${this.API}/${id}`, { responseType: 'text' });
  }

  /** Reinforces a memory's recall weight. */
  reinforce(id: string, valence: number = 0): Observable<string> {
    return this.http.post(`${this.API}/${id}/reinforce`, { valence }, { responseType: 'text' });
  }

  /** Suppresses a memory from recall results. */
  suppress(id: string, reason: string = ''): Observable<string> {
    return this.http.post(`${this.API}/${id}/suppress`, { action: 'SUPPRESS', reason }, { responseType: 'text' });
  }

  /** Unsuppresses a memory. */
  unsuppress(id: string): Observable<string> {
    return this.http.post(`${this.API}/${id}/suppress`, { action: 'UNSUPPRESS' }, { responseType: 'text' });
  }

  /** Marks a memory as resolved. */
  resolve(id: string): Observable<string> {
    return this.http.post(`${this.API}/${id}/resolve`, { resolved: true }, { responseType: 'text' });
  }

  /** Marks a memory as unresolved. */
  unresolve(id: string): Observable<string> {
    return this.http.post(`${this.API}/${id}/resolve`, { resolved: false }, { responseType: 'text' });
  }

  // ══════════════════════════════════════════════════════════════
  // ADMIN
  // ══════════════════════════════════════════════════════════════

  /** Triggers vacuum compaction for a tier (returns Observable). */
  vacuum(tier: string): Observable<any> {
    return this.http.post(`${this.API}/vacuum`, { tier });
  }

  /** Triggers reflect consolidation cycle. */
  reflect(): Observable<any> {
    return this.http.post(`${this.API}/reflect`, {});
  }

  /** Updates a memory's text and/or tags. */
  updateMemory(id: string, update: { text?: string; tags?: string[] }): Observable<string> {
    return this.http.put(`${this.API}/${encodeURIComponent(id)}`, update, { responseType: 'text' });
  }

  /** Returns the INT8 quantized embedding vector for a memory. */
  getMemoryVector(id: string): Observable<{ memoryId: string; dimension: number; values: number[] }> {
    return this.http.get<{ memoryId: string; dimension: number; values: number[] }>(`${this.API}/${encodeURIComponent(id)}/vector`);
  }

  // ══════════════════════════════════════════════════════════════
  // RECALL — Cognitive Query
  // ══════════════════════════════════════════════════════════════

  /** Executes a cognitive recall query against the memory subsystem. */
  recall(query: string, topK: number = 10, profile?: string, queryValence?: number | null): Observable<RecallResponse> {
    const startTime = Date.now();
    const body: Record<string, any> = { query, topK };
    if (profile) body['profile'] = profile;
    if (queryValence !== undefined && queryValence !== null) {
      body['queryValence'] = queryValence;
    }
    return this.http.post<any[]>(`${this.API}/recall`, body).pipe(
      map(items => {
        const endTime = Date.now();
        const queryTimeMs = endTime - startTime;
        
        const results = (items || []).map(item => {
          return {
            id: item.id,
            text: item.text,
            score: item.cognitiveScore ?? 0,
            importance: item.importance ?? 1.0,
            ageDays: parseFloat(item.ageDescription) || 0,
            agentRecallCount: item.agentRecallCount ?? 0,
            valence: item.valence ?? 0,
            memoryType: item.memoryType || item.tier || 'SEMANTIC',
            source: item.source || 'OBSERVED',
            synapticTags: item.tags || [],
            breakdown: {
              similarity: item.cognitiveScore ?? 0,
              importanceDecay: 1.0,
              tagBoostFactor: 1.0,
              habituationPenalty: 1.0,
              graphBoost: 1.0,
              valenceAlignment: 1.0,
              finalScore: item.cognitiveScore ?? 0
            },
            lateral: false,
            negativeOutcome: false,
            hyperfocused: false,
            positivelyReinforced: false
          } as RecallResult;
        });

        return {
          results: results,
          totalMemories: results.length,
          queryTimeMs: queryTimeMs,
          profile: profile || 'BALANCED'
        } as RecallResponse;
      })
    );
  }

  // ══════════════════════════════════════════════════════════════
  // NAVIGATION / SORT
  // ══════════════════════════════════════════════════════════════

  /** Client-side sort of rows. */
  private sortRows(rows: MemoryRow[]): MemoryRow[] {
    const field = this.sortField();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;

    return [...rows].sort((a, b) => {
      const va = (a as any)[field];
      const vb = (b as any)[field];
      if (typeof va === 'number' && typeof vb === 'number') {
        return (va - vb) * dir;
      }
      return String(va).localeCompare(String(vb)) * dir;
    });
  }

  /** Navigate to next page. */
  nextPage(): void {
    if (this.page() < this.totalPages() - 1) {
      this.page.update(p => p + 1);
      this.loadPage();
    }
  }

  /** Navigate to previous page. */
  prevPage(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.loadPage();
    }
  }

  /** Set tier filter and reload. */
  setTierFilter(tier: string | null): void {
    this.tierFilter.set(tier);
    this.page.set(0);
    this.loadPage();
  }

  /** Toggle tombstoned visibility and reload. */
  toggleTombstoned(): void {
    this.showTombstoned.update(v => !v);
    this.page.set(0);
    this.loadPage();
  }

  /** Set sort and reload. */
  setSort(field: string): void {
    if (this.sortField() === field) {
      this.sortDirection.update(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('desc');
    }
    this.loadPage();
  }
}
