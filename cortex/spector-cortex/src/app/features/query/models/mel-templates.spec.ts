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

import { describe, it, expect } from 'vitest';
import {
  MEL_SNIPPET_TEMPLATES,
  DEFAULT_MEL_SCRIPT,
  DEFAULT_VISUAL_ASSIST_STATE,
  parseMelToVisualState,
  buildMelFromVisualState,
} from './mel-templates';

describe('mel-templates (Milestone 4)', () => {
  it('contains at least 8 categorized snippet templates', () => {
    expect(MEL_SNIPPET_TEMPLATES.length).toBeGreaterThanOrEqual(8);
    const verbs = MEL_SNIPPET_TEMPLATES.map((t) => t.verb);
    expect(verbs).toContain('EXPLAIN RECALL');
    expect(verbs).toContain('RECALL');
    expect(verbs).toContain('REMEMBER');
    expect(verbs).toContain('CONSOLIDATE');
    expect(verbs).toContain('FORGET');
    expect(verbs).toContain('INTROSPECT');
  });

  it('parses MEL recall script to visual assist state correctly', () => {
    const script = `EXPLAIN RECALL 'cache stampede incident'\n  TIERS WORKING, EPISODIC\n  TAGS CONTAIN ['cache', 'redis']\n  IMPORTANCE >= 0.75\n  TOP 12;`;
    const state = parseMelToVisualState(script);
    expect(state.explain).toBe(true);
    expect(state.cue).toBe('cache stampede incident');
    expect(state.tiers).toEqual(['WORKING', 'EPISODIC']);
    expect(state.tags).toBe('cache, redis');
    expect(state.minImportance).toBe(0.75);
    expect(state.topK).toBe(12);
  });

  it('builds valid MEL statement from visual assist state', () => {
    const built = buildMelFromVisualState({
      cue: 'deadlock transaction',
      explain: false,
      profile: 'BALANCED',
      topK: 8,
      minImportance: 0.60,
      tiers: ['EPISODIC', 'SEMANTIC'],
      tags: 'db, sql',
    });

    expect(built).toContain("RECALL 'deadlock transaction'");
    expect(built).not.toContain('EXPLAIN RECALL');
    expect(built).toContain('TIERS EPISODIC, SEMANTIC');
    expect(built).toContain("TAGS CONTAIN ['db', 'sql']");
    expect(built).toContain('IMPORTANCE >= 0.60');
    expect(built).toContain('TOP 8;');
  });

  it('handles round-trip bidirectional synchronization', () => {
    const original = {
      cue: 'circuit breaker trip',
      explain: true,
      profile: 'BALANCED',
      topK: 6,
      minImportance: 0.50,
      tiers: ['WORKING', 'SEMANTIC'],
      tags: 'resilience',
    };
    const melScript = buildMelFromVisualState(original);
    const parsed = parseMelToVisualState(melScript, DEFAULT_VISUAL_ASSIST_STATE);

    expect(parsed.cue).toBe(original.cue);
    expect(parsed.explain).toBe(original.explain);
    expect(parsed.topK).toBe(original.topK);
    expect(parsed.minImportance).toBe(original.minImportance);
    expect(parsed.tiers).toEqual(original.tiers);
    expect(parsed.tags).toBe(original.tags);
  });
});
