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

import { MelSnippetTemplate } from '@core/models/mel.models';

export const DEFAULT_MEL_SCRIPT = `-- MEL-001 (Memory Engine Language) Workbench
-- Press Cmd+Enter (or Ctrl+Enter) to execute script

EXPLAIN RECALL 'authentication timeout redis'
  TIERS EPISODIC, SEMANTIC
  IMPORTANCE >= 0.40
  TOP 5;
`;

export const MEL_SNIPPET_TEMPLATES: MelSnippetTemplate[] = [
  {
    id: 'explain-recall',
    label: 'EXPLAIN RECALL (Glass-Box)',
    verb: 'EXPLAIN RECALL',
    category: 'Retrieval',
    description: '6-phase funnel & per-candidate fused score breakdown',
    code: `EXPLAIN RECALL 'authentication timeout redis'\n  TIERS EPISODIC, SEMANTIC\n  IMPORTANCE >= 0.40\n  TOP 5;`,
  },
  {
    id: 'recall-filtered',
    label: 'RECALL (Filtered Scan)',
    verb: 'RECALL',
    category: 'Retrieval',
    description: 'Multi-tier cognitive retrieval with tag & valence gates',
    code: `RECALL 'database connection pool'\n  TIERS WORKING, EPISODIC, SEMANTIC\n  TAGS CONTAIN ['infra']\n  IMPORTANCE >= 0.50\n  VALENCE BETWEEN -0.8 AND 0.9\n  TOP 10;`,
  },
  {
    id: 'remember-episodic',
    label: 'REMEMBER (Ingest Engram)',
    verb: 'REMEMBER',
    category: 'Ingestion & Plasticity',
    description: 'Store an experienced episodic trace with valence & tags',
    code: `REMEMBER TEXT 'Redis pool exhausted on primary shard during peak traffic'\n  INTO EPISODIC\n  ID 'ep-redis-pool-01'\n  TAGS ['infra', 'redis', 'incident']\n  IMPORTANCE 0.85\n  VALENCE -0.45\n  AROUSAL 0.80\n  SOURCE EXPERIENCED;`,
  },
  {
    id: 'associate-hebbian',
    label: 'ASSOCIATE (Hebbian Link)',
    verb: 'ASSOCIATE',
    category: 'Ingestion & Plasticity',
    description: 'Wire bidirectional Hebbian synapse or causal sequence',
    code: `ASSOCIATE 'ep-redis-pool-01' WITH 'ep-auth-timeout-02' WEIGHT 0.82;\nASSOCIATE 'ep-redis-pool-01' AFTER 'ep-deploy-v4';`,
  },
  {
    id: 'rehearse-reconsolidate',
    label: 'REHEARSE & RECONSOLIDATE',
    verb: 'REHEARSE',
    category: 'Ingestion & Plasticity',
    description: 'Strengthen storage S or update trace under prediction error',
    code: `REHEARSE 'ep-redis-pool-01';\nRECONSOLIDATE 'ep-redis-pool-01'\n  SET TEXT 'Redis pool exhausted; fixed by raising max_connections to 256'\n  LINEAGE 'ep-redis-pool-01';`,
  },
  {
    id: 'consolidate-semantic',
    label: 'CONSOLIDATE (Episodic -> Semantic)',
    verb: 'CONSOLIDATE',
    category: 'Consolidation & Lifecycle',
    description: 'Distill episodic traces into durable semantic knowledge',
    code: `CONSOLIDATE 'ep-redis-pool-01'\n  INTO SEMANTIC\n  AS TEXT 'Primary Redis shard requires max_connections >= 256 under load';`,
  },
  {
    id: 'forget-suppress',
    label: 'FORGET (WEAKEN / SUPPRESS / TOMBSTONE)',
    verb: 'FORGET',
    category: 'Consolidation & Lifecycle',
    description: 'Apply biological forgetting or soft tombstone',
    code: `FORGET 'ep-redis-pool-01' SUPPRESS;`,
  },
  {
    id: 'dream-introspect',
    label: 'DREAM & INTROSPECT',
    verb: 'INTROSPECT',
    category: 'Consolidation & Lifecycle',
    description: 'Run generative replay cycle and inspect tier telemetry',
    code: `DREAM TEMPERATURE 1.0 LIMIT 3;\nINTROSPECT;`,
  },
  {
    id: 'simulate-counterfactual',
    label: 'SIMULATE (Counterfactual)',
    verb: 'SIMULATE',
    category: 'Retrieval',
    description: 'Run counterfactual imagination query tagged SOURCE SIMULATED',
    code: `SIMULATE RECALL 'what if redis replica failover is delayed by 30s'\n  TIERS EPISODIC, SEMANTIC\n  TOP 5\n  AS 'sim-redis-failover';`,
  },
];

export interface VisualRecallAssistState {
  cue: string;
  explain: boolean;
  profile: string;
  topK: number;
  minImportance: number;
  tiers: string[];
  tags: string;
}

export const DEFAULT_VISUAL_ASSIST_STATE: VisualRecallAssistState = {
  cue: 'authentication timeout redis',
  explain: true,
  profile: 'BALANCED',
  topK: 5,
  minImportance: 0.40,
  tiers: ['EPISODIC', 'SEMANTIC'],
  tags: '',
};

export function parseMelToVisualState(
  script: string,
  fallback: VisualRecallAssistState = DEFAULT_VISUAL_ASSIST_STATE
): VisualRecallAssistState {
  const explain = /^\s*EXPLAIN\s+RECALL\b/im.test(script);
  const cueMatch = script.match(/\bRECALL\s+'((?:[^']|'')*)'/i);
  const cue = cueMatch ? cueMatch[1].replace(/''/g, "'") : fallback.cue;

  const topMatch = script.match(/\bTOP\s+(\d+)/i);
  const topK = topMatch ? Math.max(1, parseInt(topMatch[1], 10)) : fallback.topK;

  const impMatch = script.match(/\bIMPORTANCE\s*>=\s*([+-]?\d+(?:\.\d+)?)/i);
  const minImportance = impMatch ? parseFloat(impMatch[1]) : fallback.minImportance;

  const tiersMatch = script.match(
    /\b(?:FROM\s+)?TIERS\s+((?:(?:WORKING|EPISODIC|SEMANTIC|PROCEDURAL)\s*,?\s*)+)/i
  );
  const tiers = tiersMatch
    ? tiersMatch[1]
        .split(',')
        .map((t) => t.trim().toUpperCase())
        .filter(Boolean)
    : fallback.tiers;

  const tagsMatch = script.match(/\bTAGS\s+CONTAIN\s*[[(]([^\]\)]*)[\])]/i);
  const tags = tagsMatch
    ? tagsMatch[1]
        .split(',')
        .map((t) => t.trim().replace(/^'|'$/g, ''))
        .filter(Boolean)
        .join(', ')
    : '';

  return {
    cue,
    explain,
    profile: fallback.profile,
    topK,
    minImportance,
    tiers: tiers.length > 0 ? tiers : ['EPISODIC', 'SEMANTIC'],
    tags,
  };
}

export function buildMelFromVisualState(state: VisualRecallAssistState): string {
  const verb = state.explain ? 'EXPLAIN RECALL' : 'RECALL';
  const escapedCue = (state.cue || 'memory recall').replace(/'/g, "''");
  const lines: string[] = [`${verb} '${escapedCue}'`];

  if (state.tiers && state.tiers.length > 0) {
    lines.push(`  TIERS ${state.tiers.join(', ')}`);
  }

  const tagList = (state.tags || '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
  if (tagList.length > 0) {
    const formattedTags = tagList.map((t) => `'${t.replace(/'/g, "''")}'`).join(', ');
    lines.push(`  TAGS CONTAIN [${formattedTags}]`);
  }

  if (state.minImportance > 0) {
    lines.push(`  IMPORTANCE >= ${state.minImportance.toFixed(2)}`);
  }

  lines.push(`  TOP ${state.topK};`);
  return lines.join('\n');
}
