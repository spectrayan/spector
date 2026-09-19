/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

export interface InterestEntry {
  topic: string;
  level: string;
}

export type ConfigApplyMode = 'LIVE' | 'POLICY' | 'REBUILD' | 'BOOT';

export interface AiConfigField {
  key: string;
  defaultValue: any;
  type: string;
  description: string;
  editValue: any;
  source: 'system' | 'user' | 'tenant';
  applyMode?: ConfigApplyMode;
  options?: string[];
  min?: number;
  max?: number;
  step?: number;
  secret?: boolean;
}

export interface ConfigCategoryMeta {
  key: string;
  label: string;
  icon: string;
  description: string;
}

export const CATEGORY_METADATA: Record<string, { label: string; icon: string; description: string }> = {
  llm_provider: { label: 'LLM Provider', icon: 'psychology', description: 'Text generation model, credentials, and parameters' },
  embedding_provider: { label: 'Embedding Provider', icon: 'hub', description: 'Vector embedding model, dimensions, and batching' },
  memory: { label: 'Memory Core', icon: 'memory', description: 'Working memory capacity, decay rates, and Hebbian degree' },
  recall: { label: 'Recall & Search', icon: 'search', description: 'Cognitive retrieval, MMR diversity, lateral search, and BM25' },
  hnsw: { label: 'HNSW Vector Index', icon: 'scatter_plot', description: 'Hierarchical Navigable Small World graph parameters' },
  spectrum: { label: 'Spectrum Scoring', icon: 'speed', description: 'Salience calibration, score bounds, and scaling' },
  ingestion: { label: 'Ingestion & Chunking', icon: 'upload_file', description: 'Document chunking, overlap, and parent-child linking' },
  multimodal: { label: 'Multimodal', icon: 'perm_media', description: 'Vision and audio perceptual processing limits' },
  concurrency: { label: 'Concurrency', icon: 'alt_route', description: 'Virtual threads, executor pools, and task queue limits' },
  telemetry: { label: 'Telemetry & Tracing', icon: 'insights', description: 'OpenTelemetry sampling, export rates, and metrics' },
  salience: { label: 'Salience & Emotion', icon: 'auto_awesome', description: 'Emotional valence, novelty bonus, and circadian rhythm' },
  soul: { label: 'Soul & Identity', icon: 'face', description: 'Agent persona baselines, temperament, and empathy' },
};

export interface StressResponseOption {
  value: string;
  label: string;
}

export interface CommunicationStyleOption {
  value: string;
  label: string;
}

export interface InterestLevel {
  value: string;
  label: string;
  color: string;
}

export const STRESS_RESPONSE_OPTIONS: StressResponseOption[] = [
  { value: 'FIGHT', label: 'Fight — confrontational, aggressive' },
  { value: 'FLIGHT', label: 'Flight — avoidant, anxious' },
  { value: 'FREEZE', label: 'Freeze — paralysis, dissociation' },
  { value: 'FAWN', label: 'Fawn — people-pleasing, conflict-avoidant' },
  { value: 'ADAPTIVE', label: 'Adaptive — flexible, context-dependent' },
];

export const COMMUNICATION_STYLE_OPTIONS: CommunicationStyleOption[] = [
  { value: '', label: 'Not specified' },
  { value: 'ANALYTICAL', label: 'Analytical — data-driven, precise' },
  { value: 'INTUITIVE', label: 'Intuitive — big-picture, conceptual' },
  { value: 'FUNCTIONAL', label: 'Functional — process-oriented, structured' },
  { value: 'PERSONAL', label: 'Personal — emotionally aware, empathetic' },
  { value: 'DIRECT', label: 'Direct — concise, action-oriented' },
  { value: 'COLLABORATIVE', label: 'Collaborative — consensus-seeking' },
];

export const INTEREST_LEVELS: InterestLevel[] = [
  { value: 'CRITICAL', label: 'Critical (2.0×)', color: '#e74c3c' },
  { value: 'HIGH', label: 'High (1.5×)', color: '#f39c12' },
  { value: 'NORMAL', label: 'Normal (1.0×)', color: '#3498db' },
  { value: 'LOW', label: 'Low (0.5×)', color: '#95a5a6' },
  { value: 'IGNORE', label: 'Ignore (0.1×)', color: '#7f8c8d' },
];

export const API_KEY_EXPIRY_OPTIONS = [
  { value: 7, label: '7 days' },
  { value: 30, label: '30 days' },
  { value: 60, label: '60 days' },
  { value: 90, label: '90 days' },
  { value: 365, label: '1 year' },
  { value: null, label: 'Never expires' },
];

export const API_KEY_SCOPE_OPTIONS = [
  { value: 'memory:read', label: 'Read memories', icon: 'visibility' },
  { value: 'memory:write', label: 'Write memories', icon: 'edit' },
  { value: 'memory:forget', label: 'Forget memories', icon: 'delete' },
];
