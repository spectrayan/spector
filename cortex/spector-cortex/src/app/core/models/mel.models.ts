/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * TypeScript models and contracts for MEL (Memory Engine Language) Studio workbench.
 * Matches com.spectrayan.spector.synapse.web.MelController and spector-mel engine DTOs.
 */

export interface MelCandidateItem {
  id: string;
  rank: number;
  fusedScore: number;
  tier: string;
  content: string;
  vectorSimilarity: number;
  bm25Score: number;
  valenceFactor: number;
  decayFactor: number;
  cognitiveMass: number;
  storageStrength: number;
  hebbianBoost: number;
  synapticTags: string[];
}

export interface MelExplainRow {
  rank: number;
  id: string;
  tier: string;
  fusedScore: number;
  vectorSimilarity: number;
  bm25Score: number;
  decayFactor: number;
  cognitiveMass: number;
  storageStrength: number;
  valenceFactor: number;
  hebbianBoost: number;
  freeEnergy: number;
}

export interface MelFunnelPhase {
  phaseIndex: number;
  name: string;
  survivors: number;
  pruned: number;
  total: number;
  survivalPct: number;
  description: string;
}

export interface FunnelTelemetry {
  totalScanned: number;
  phase1Alive: number;
  phase2TagSurvivors: number;
  phase3ValenceSurvivors: number;
  phase4MassSurvivors: number;
  phase5SimdEvaluated: number;
  phase6FinalReturned: number;
}

export interface MelDiagnostic {
  line: number;
  column: number;
  message: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
}

export interface MelStatementExecutionResult {
  statement_type: string;
  status: string;
  output: string;
  elapsed_ms: number;
  duration_micros: number;
  affected_ids: string[];
  candidates?: MelCandidateItem[];
  explain_rows?: MelExplainRow[];
  funnel?: FunnelTelemetry;
  funnel_phases?: MelFunnelPhase[];
  diagnostics?: MelDiagnostic[];
}

export interface MelExecuteRequest {
  script: string;
  namespace?: string;
}

export interface MelExecuteResponse {
  status: string;
  output?: string;
  combined_output: string;
  elapsed_ms: number;
  total_elapsed_ms: number;
  duration_micros: number;
  namespace: string;
  statement_type: string;
  affected_ids: string[];
  candidates: MelCandidateItem[];
  explain_rows: MelExplainRow[];
  funnel: FunnelTelemetry;
  funnel_phases: MelFunnelPhase[];
  diagnostics: MelDiagnostic[];
  statements: MelStatementExecutionResult[];
}

export interface MelValidateRequest {
  script: string;
}

export interface MelStatementSummary {
  type: string;
  summary: string;
  line: number;
  column: number;
}

export interface MelValidateResponse {
  valid: boolean;
  statement_count: number;
  statements: MelStatementSummary[];
  diagnostics: MelDiagnostic[];
}

export interface MelClauseTemplate {
  keyword: string;
  syntax: string;
  description: string;
  example: string;
}

export interface MelSnippetTemplate {
  id: string;
  label: string;
  verb: string;
  category: 'Retrieval' | 'Ingestion & Plasticity' | 'Consolidation & Lifecycle';
  description: string;
  code: string;
}

export interface MelSchemaResponse {
  keywords: string[];
  tiers: string[];
  sources: string[];
  clauseTemplates: MelClauseTemplate[];
  snippets: MelSnippetTemplate[];
}

export interface MelExecutionHistoryItem {
  id: string;
  script: string;
  verb: string;
  status: string;
  elapsedMs: number;
  timestamp: string;
  candidateCount?: number;
}
