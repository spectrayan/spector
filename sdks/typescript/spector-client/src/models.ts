/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

export enum MemoryTier {
  WORKING = 'WORKING',
  EPISODIC = 'EPISODIC',
  SEMANTIC = 'SEMANTIC',
  PROCEDURAL = 'PROCEDURAL',
}

export enum MemorySource {
  USER_STATED = 'USER_STATED',
  OBSERVED = 'OBSERVED',
  INFERRED = 'INFERRED',
  PROCEDURAL = 'PROCEDURAL',
}

export interface RecallRecord {
  id: string;
  text: string;
  score: number;
  tier: MemoryTier;
  tags: string[];
  valence: number;
  arousal: number;
  importance: number;
  metadata: Record<string, unknown>;
  similarity: number;
  ageDays: number;
  decayFactor: number;
}

export interface SearchRecord {
  id: string;
  text: string;
  score: number;
  tags: string[];
  metadata: Record<string, unknown>;
}

export interface MemoryRecord {
  id: string;
  text: string;
  tier: MemoryTier;
  tags: string[];
  valence: number;
  arousal: number;
  importance: number;
  decayFactor: number;
  recallCount: number;
  resolved: boolean;
  tombstoned: boolean;
  metadata: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface MemoryStatus {
  totalMemories: number;
  workingCount: number;
  episodicCount: number;
  semanticCount: number;
  proceduralCount: number;
  tombstoneCount: number;
  dimensions: number;
  persistenceMode: string;
  extra?: Record<string, unknown>;
}

export interface SseEvent<T = unknown> {
  event: string;
  data: T;
  id?: string;
  retry?: number;
}

export interface ClientOptions {
  baseUrl?: string;
  apiKey?: string;
  bearerToken?: string;
  userId?: string;
  agentId?: string;
  namespace?: string;
  timeout?: number;
  headers?: Record<string, string>;
}
