/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import {
  MemoryRecord,
  MemoryStatus,
  MemoryTier,
  RecallRecord,
  SearchRecord,
} from './models';
import { MemoryNotFoundError } from './errors';
import { Transport } from './transports';

export interface RememberParams {
  text: string;
  tier?: MemoryTier | string;
  tags?: string[];
  interest?: number;
  urgency?: number;
  challenge?: number;
  valence?: number;
  arousal?: number;
  metadata?: Record<string, unknown>;
}

export interface RecallOptions {
  topK?: number;
  profile?: string;
  minSalience?: number;
  tags?: string[];
}

export interface TableOptions {
  page?: number;
  pageSize?: number;
  tier?: string;
  tombstoned?: boolean;
}

export class MemoryClient {
  constructor(private readonly transport: Transport) {}

  /**
   * Stores a memory with cognitive tier hints.
   */
  async remember(params: RememberParams): Promise<Record<string, unknown>> {
    const tierStr = typeof params.tier === 'string' ? params.tier : (params.tier ?? MemoryTier.SEMANTIC);
    const body = {
      text: params.text,
      tier: tierStr,
      tags: params.tags?.join(','),
      interest: params.interest ?? 0.0,
      urgency: params.urgency ?? 0.0,
      challenge: params.challenge ?? 0.0,
      valence: params.valence ?? 0,
      arousal: params.arousal ?? 0,
      metadata: params.metadata ?? {},
    };
    return this.transport.request('POST', '/api/v1/memory/remember', { body });
  }

  /**
   * Synchronously stores a memory, returning the assigned memory ID.
   */
  async store(text: string, tags?: string[]): Promise<{ id: string; status?: string }> {
    const body = { text, tags: tags ?? [] };
    return this.transport.request('POST', '/api/v1/memory', { body });
  }

  /**
   * Recalls memories using fused cognitive scoring (vector similarity + Hebbian graph + temporal decay).
   */
  async recall(query: string, options: RecallOptions = {}): Promise<RecallRecord[]> {
    const body = {
      query,
      topK: options.topK ?? 5,
      profile: options.profile ?? 'BALANCED',
      minSalience: options.minSalience ?? 0.0,
      tags: options.tags ?? [],
    };
    const res = await this.transport.request<any>('POST', '/api/v1/memory/recall', { body });
    const items: any[] = Array.isArray(res)
      ? res
      : (res?.results ?? res?.memories ?? res?.data ?? []);

    return items.map((item) => ({
      id: String(item.id ?? ''),
      text: String(item.text ?? ''),
      score: Number(item.score ?? item.cognitiveScore ?? 0),
      tier: (item.tier as MemoryTier) ?? MemoryTier.SEMANTIC,
      tags: Array.isArray(item.tags) ? item.tags : [],
      valence: Number(item.valence ?? 0),
      arousal: Number(item.arousal ?? 0),
      importance: Number(item.importance ?? 0),
      metadata: item.metadata ?? {},
      similarity: Number(item.similarity ?? 0),
      ageDays: Number(item.ageDays ?? item.age_days ?? 0),
      decayFactor: Number(item.decayFactor ?? item.decay_factor ?? 1),
    }));
  }

  /**
   * Performs pure dense vector similarity search.
   */
  async search(query: string, topK: number = 5): Promise<SearchRecord[]> {
    const body = { query, topK };
    const res = await this.transport.request<any>('POST', '/api/v1/memory/search', { body });
    const items: any[] = Array.isArray(res)
      ? res
      : (res?.results ?? res?.hits ?? res?.data ?? []);

    return items.map((item) => ({
      id: String(item.id ?? ''),
      text: String(item.text ?? ''),
      score: Number(item.score ?? 0),
      tags: Array.isArray(item.tags) ? item.tags : [],
      metadata: item.metadata ?? {},
    }));
  }

  /**
   * Retrieves a memory by ID. Throws MemoryNotFoundError if not found.
   */
  async get(memoryId: string): Promise<MemoryRecord> {
    const res = await this.transport.request<any>('GET', `/api/v1/memory/${memoryId}`);
    return {
      id: String(res.id ?? memoryId),
      text: String(res.text ?? ''),
      tier: (res.tier as MemoryTier) ?? MemoryTier.SEMANTIC,
      tags: Array.isArray(res.tags) ? res.tags : [],
      valence: Number(res.valence ?? 0),
      arousal: Number(res.arousal ?? 0),
      importance: Number(res.importance ?? 0),
      decayFactor: Number(res.decayFactor ?? 1),
      recallCount: Number(res.recallCount ?? 0),
      resolved: Boolean(res.resolved ?? false),
      tombstoned: Boolean(res.tombstoned ?? false),
      metadata: res.metadata ?? {},
      createdAt: res.createdAt,
      updatedAt: res.updatedAt,
    };
  }

  /**
   * Finds a memory by ID, returning null if not found.
   */
  async find(memoryId: string): Promise<MemoryRecord | null> {
    try {
      return await this.get(memoryId);
    } catch (err) {
      if (err instanceof MemoryNotFoundError) return null;
      throw err;
    }
  }

  /**
   * Updates an existing memory's text, tags, or metadata.
   */
  async update(
    memoryId: string,
    params: { text?: string; tags?: string[]; metadata?: Record<string, unknown> }
  ): Promise<void> {
    await this.transport.request('PUT', `/api/v1/memory/${memoryId}`, { body: params });
  }

  /**
   * Tombstones a memory, removing it from active recall.
   */
  async forget(memoryId: string, reason?: string): Promise<void> {
    const body = reason ? { reason } : undefined;
    await this.transport.request('DELETE', `/api/v1/memory/${memoryId}`, { body });
  }

  /**
   * Reinforces a memory via Hebbian Long-Term Potentiation (LTP).
   */
  async reinforce(memoryId: string, valence: number = 1): Promise<void> {
    await this.transport.request('POST', `/api/v1/memory/${memoryId}/reinforce`, {
      body: { valence },
    });
  }

  /**
   * Suppresses a memory from active recall consideration.
   */
  async suppress(memoryId: string, reason?: string): Promise<void> {
    await this.transport.request('POST', `/api/v1/memory/${memoryId}/suppress`, {
      body: { action: 'suppress', reason },
    });
  }

  /**
   * Restores a previously suppressed memory to recall consideration.
   */
  async unsuppress(memoryId: string): Promise<void> {
    await this.transport.request('POST', `/api/v1/memory/${memoryId}/suppress`, {
      body: { action: 'unsuppress' },
    });
  }

  /**
   * Resolves a memory (Zeigarnik closure).
   */
  async resolve(memoryId: string): Promise<void> {
    await this.transport.request('POST', `/api/v1/memory/${memoryId}/resolve`, {
      body: { resolved: true },
    });
  }

  /**
   * Reopens active tension on a memory.
   */
  async unresolve(memoryId: string): Promise<void> {
    await this.transport.request('POST', `/api/v1/memory/${memoryId}/resolve`, {
      body: { resolved: false },
    });
  }

  /**
   * Retrieves real-time memory tier counts and system status.
   */
  async status(): Promise<MemoryStatus> {
    const res = await this.transport.request<any>('GET', '/api/v1/memory/status');
    return {
      totalMemories: Number(res.totalMemories ?? 0),
      workingCount: Number(res.workingCount ?? 0),
      episodicCount: Number(res.episodicCount ?? 0),
      semanticCount: Number(res.semanticCount ?? 0),
      proceduralCount: Number(res.proceduralCount ?? 0),
      tombstoneCount: Number(res.tombstoneCount ?? 0),
      dimensions: Number(res.dimensions ?? 384),
      persistenceMode: String(res.persistenceMode ?? 'OFF'),
      extra: res,
    };
  }

  /**
   * Retrieves memory subsystem health and performance statistics.
   */
  async stats(): Promise<Record<string, unknown>> {
    return this.transport.request('GET', '/api/v1/memory/stats');
  }

  /**
   * Browses memories using tag-based exact matching (inverted tag index).
   */
  async browse(tags?: string[]): Promise<Array<Record<string, unknown>>> {
    return this.transport.request('POST', '/api/v1/memory/browse', {
      body: { tags: tags ?? [] },
    });
  }

  /**
   * Retrieves paginated database memory records.
   */
  async table(options: TableOptions = {}): Promise<Record<string, unknown>> {
    const queryParams: Record<string, unknown> = {
      page: options.page ?? 0,
      pageSize: options.pageSize ?? 20,
      tier: options.tier,
      tombstoned: options.tombstoned ?? false,
    };
    return this.transport.request('GET', '/api/v1/memory/table', { queryParams });
  }

  /**
   * Retrieves the INT8 quantized embedding vector for a memory.
   */
  async vector(memoryId: string): Promise<number[]> {
    const res = await this.transport.request<{ vector?: number[] }>(
      'GET',
      `/api/v1/memory/${memoryId}/vector`
    );
    return res.vector ?? [];
  }

  /**
   * Manually triggers a circadian sleep consolidation sweep.
   */
  async consolidate(): Promise<void> {
    await this.transport.request('POST', '/api/v1/memory/consolidate');
  }

  /**
   * Triggers vacuum compaction for a tier or all tiers.
   */
  async vacuum(tier?: string): Promise<Record<string, unknown>> {
    const body = tier ? { tier } : {};
    return this.transport.request('POST', '/api/v1/memory/vacuum', { body });
  }
}
