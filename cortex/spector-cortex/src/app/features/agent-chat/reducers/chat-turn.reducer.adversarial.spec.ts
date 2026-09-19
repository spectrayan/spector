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
  createEmptyTurn,
  reduceChatEvents,
  reduceTurnEventStream,
  toggleThinkingCollapse,
  hydrateTurnFromHistory,
} from './chat-turn.reducer';
import {
  ChatStreamEvent,
  ChatTurnView,
  SessionStreamEvent,
  ThinkingStreamEvent,
  TokenStreamEvent,
  ToolCallStreamEvent,
  ToolResultStreamEvent,
  DoneStreamEvent,
  ErrorStreamEvent,
} from '../models/chat-turn.model';

/**
 * Deep freezes an object recursively to guarantee strict immutability.
 */
function deepFreeze<T>(obj: T): T {
  if (obj === null || obj === undefined || typeof obj !== 'object') {
    return obj;
  }
  Object.freeze(obj);
  for (const key of Object.keys(obj as any)) {
    const prop = (obj as any)[key];
    if (prop !== null && typeof prop === 'object' && !Object.isFrozen(prop)) {
      deepFreeze(prop);
    }
  }
  return obj;
}

describe('ChatTurnReducer Adversarial Empirical Stress Suite', () => {
  const SESSION_ID = '01J8Y000000000000000000000';
  const TURN_ID = '01J8Y000000000000000000001';

  // =========================================================================
  // FOCUS 1: Live stream reduction vs historical turn replay parity
  // =========================================================================
  describe('Focus 1: Live Stream Reduction vs Historical Replay Parity', () => {
    it('achieves complete value equality across all keys between live SSE stream and historical hydration', () => {
      const liveEvents: ChatStreamEvent[] = [
        {
          type: 'session',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 1,
          tsEpochMs: 1758265000000,
          isNew: true,
          model: 'qwen2.5:7b',
        },
        {
          type: 'thinking',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 2,
          tsEpochMs: 1758265000150,
          text: 'Synthesizing contextual priors from off-heap storage...',
          elapsedMs: 150,
        },
        {
          type: 'thinking',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 3,
          tsEpochMs: 1758265000380,
          text: ' Evaluating hippocampal engram activations.',
          elapsedMs: 380,
        },
        {
          type: 'tool_call',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 4,
          tsEpochMs: 1758265000450,
          callId: 'call_mem_1',
          name: 'memory_recall',
          arguments: { query: 'hippocampal replay', limit: 5 },
        },
        {
          type: 'tool_result',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 5,
          tsEpochMs: 1758265000570,
          callId: 'call_mem_1',
          name: 'memory_recall',
          status: 'success',
          preview: '{"engrams": ["eng_001", "eng_002"]}',
          truncated: false,
          elapsedMs: 120,
        },
        {
          type: 'tool_call',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 6,
          tsEpochMs: 1758265000600,
          callId: 'call_calc_2',
          name: 'calculator',
          arguments: { expr: '1024 * 64' },
        },
        {
          type: 'tool_result',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 7,
          tsEpochMs: 1758265000645,
          callId: 'call_calc_2',
          name: 'calculator',
          status: 'failure',
          preview: 'Syntax error in expression at offset 4',
          truncated: false,
          elapsedMs: 45,
        },
        {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 8,
          tsEpochMs: 1758265000750,
          text: 'Spector leverages ',
        },
        {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 9,
          tsEpochMs: 1758265000850,
          text: 'fused 6-phase scoring ',
        },
        {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 10,
          tsEpochMs: 1758265000950,
          text: 'for cognitive memory retrieval.',
        },
        {
          type: 'done',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 11,
          tsEpochMs: 1758265001150,
          summary: 'Cognitive retrieval query resolved',
          latencyMs: 1150,
          primedMemories: 4,
          usage: {
            inputTokens: 256,
            outputTokens: 64,
            totalTokens: 320,
          },
        },
      ];

      const initial = createEmptyTurn(TURN_ID, SESSION_ID, 'Explain Spector cognitive scoring');
      const liveState = reduceTurnEventStream(initial, liveEvents);

      const historicalTurn: Partial<ChatTurnView> = {
        turnId: TURN_ID,
        sessionId: SESSION_ID,
        seq: 11,
        status: 'DONE',
        user: { text: 'Explain Spector cognitive scoring' },
        thinking: {
          text: 'Synthesizing contextual priors from off-heap storage... Evaluating hippocampal engram activations.',
          elapsedMs: 380,
          isCollapsed: true,
          userToggled: false,
        },
        tools: [
          {
            callId: 'call_mem_1',
            name: 'memory_recall',
            arguments: { query: 'hippocampal replay', limit: 5 },
            status: 'success',
            preview: '{"engrams": ["eng_001", "eng_002"]}',
            truncated: false,
            elapsedMs: 120,
          },
          {
            callId: 'call_calc_2',
            name: 'calculator',
            arguments: { expr: '1024 * 64' },
            status: 'failure',
            preview: 'Syntax error in expression at offset 4',
            truncated: false,
            elapsedMs: 45,
          },
        ],
        assistant: {
          text: 'Spector leverages fused 6-phase scoring for cognitive memory retrieval.',
        },
        usage: {
          inputTokens: 256,
          outputTokens: 64,
          totalTokens: 320,
        },
        primedMemories: 4,
        error: null,
        hasError: false,
        errorMessage: '',
      };

      const hydratedState = hydrateTurnFromHistory(historicalTurn, SESSION_ID);

      // Deep value equality check across all fields
      expect(liveState).toEqual(hydratedState);

      // Explicit key-by-key verification
      expect(liveState.turnId).toBe(hydratedState.turnId);
      expect(liveState.sessionId).toBe(hydratedState.sessionId);
      expect(liveState.seq).toBe(hydratedState.seq);
      expect(liveState.status).toBe(hydratedState.status);
      expect(liveState.isStreaming).toBe(hydratedState.isStreaming);
      expect(liveState.user).toEqual(hydratedState.user);
      expect(liveState.thinking).toEqual(hydratedState.thinking);
      expect(liveState.tools).toEqual(hydratedState.tools);
      expect(liveState.assistant).toEqual(hydratedState.assistant);
      expect(liveState.usage).toEqual(hydratedState.usage);
      expect(liveState.primedMemories).toBe(hydratedState.primedMemories);
      expect(liveState.error).toEqual(hydratedState.error);
      expect(liveState.hasError).toBe(hydratedState.hasError);
      expect(liveState.errorMessage).toBe(hydratedState.errorMessage);
    });
  });

  // =========================================================================
  // FOCUS 2: Monotonic sequence enforcement: out-of-order seq arrival
  // =========================================================================
  describe('Focus 2: Monotonic Sequence Enforcement', () => {
    it('does not regress sequence number when out-of-order seq arrives', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, {
        type: 'session',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 10,
        tsEpochMs: 1000,
      });
      expect(turn.seq).toBe(10);

      // Stale event arrives with seq: 4
      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1100,
        text: 'late thinking',
      });
      expect(turn.seq).toBe(10);
    });

    it('prevents state regression when late session event arrives after turn is DONE', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 10,
        tsEpochMs: 2000,
        usage: { inputTokens: 10, outputTokens: 10, totalTokens: 20 },
      });
      expect(turn.status).toBe('DONE');
      expect(turn.isStreaming).toBe(false);

      // Late out-of-order session event with seq: 1
      turn = reduceChatEvents(turn, {
        type: 'session',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 1,
        tsEpochMs: 1000,
      });

      // Terminal status must not regress from DONE to RUNNING
      expect(turn.status).toBe('DONE');
      expect(turn.isStreaming).toBe(false);
    });

    it('prevents tool state regression when late duplicate tool_call arrives after tool_result', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      // 1. Tool call registered
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_100',
        name: 'database_query',
        arguments: { table: 'engrams' },
      });
      expect(turn.tools[0].status).toBe('running');

      // 2. Tool result completed
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        callId: 'call_100',
        name: 'database_query',
        status: 'success',
        preview: '10 records found',
        elapsedMs: 200,
      });
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('10 records found');
      expect(turn.tools[0].elapsedMs).toBe(200);

      // 3. Out-of-order duplicate tool_call arrives with seq: 3
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        callId: 'call_100',
        name: 'database_query',
        arguments: { table: 'engrams' },
      });

      // MUST NOT regress tool status back to 'running' or wipe preview!
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('10 records found');
      expect(turn.tools[0].elapsedMs).toBe(200);
    });

    it('handles inverted arrival where tool_result arrives before tool_call', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      // Out-of-order: tool_result arrives first
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        callId: 'call_inverted',
        name: 'search_index',
        status: 'success',
        preview: 'result data',
        elapsedMs: 150,
      });

      expect(turn.tools.length).toBe(1);
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('result data');

      // Out-of-order: tool_call arrives second
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_inverted',
        name: 'search_index',
        arguments: { query: 'test' },
      });

      // Status must remain 'success' with preview intact, while updating arguments
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('result data');
      expect(turn.tools[0].arguments).toEqual({ query: 'test' });
    });

    it('prevents late token from flipping isStreaming back to true after turn is DONE', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 10,
        tsEpochMs: 2000,
      });
      expect(turn.status).toBe('DONE');
      expect(turn.isStreaming).toBe(false);

      // Late token arrives
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 8,
        tsEpochMs: 1800,
        text: 'stale token',
      });

      // isStreaming must NOT flip back to true on a completed turn
      expect(turn.status).toBe('DONE');
      expect(turn.isStreaming).toBe(false);
    });

    it('prevents out-of-order thinking event from re-opening auto-collapsed thinking after token arrival', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Thinking phase',
        elapsedMs: 100,
      });

      // Token arrives, auto-collapsing thinking
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        text: 'Visible response',
      });
      expect(turn.thinking.isCollapsed).toBe(true);

      // Out-of-order thinking event arrives with seq: 3
      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: 'late thinking delta',
        elapsedMs: 150,
      });

      // Thinking must NOT re-open after tokens have started streaming
      expect(turn.thinking.isCollapsed).toBe(true);
    });
  });

  // =========================================================================
  // FOCUS 3: Deep immutability: wrap intermediate states in Object.freeze()
  // =========================================================================
  describe('Focus 3: Deep Immutability Under Continuous Object.freeze()', () => {
    it('survives aggressive recursive deepFreeze on every transition without in-place mutations', () => {
      let turn = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Immutable stress test'));

      const events: ChatStreamEvent[] = [
        {
          type: 'session',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 1,
          tsEpochMs: 1000,
        },
        {
          type: 'thinking',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 2,
          tsEpochMs: 1100,
          text: 'Thinking step 1...',
          elapsedMs: 100,
        },
        {
          type: 'tool_call',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 3,
          tsEpochMs: 1200,
          callId: 'call_freeze_1',
          name: 'tool_alpha',
          arguments: { x: 42, nested: { y: [1, 2, 3] } },
        },
        {
          type: 'tool_call',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 4,
          tsEpochMs: 1300,
          callId: 'call_freeze_2',
          name: 'tool_beta',
          arguments: { target: 'nucleus' },
        },
        {
          type: 'tool_result',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 5,
          tsEpochMs: 1400,
          callId: 'call_freeze_1',
          name: 'tool_alpha',
          status: 'success',
          preview: 'alpha result',
          elapsedMs: 200,
        },
        {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 6,
          tsEpochMs: 1500,
          text: 'First token emitted.',
        },
        {
          type: 'tool_result',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 7,
          tsEpochMs: 1600,
          callId: 'call_freeze_2',
          name: 'tool_beta',
          status: 'failure',
          preview: 'beta failed',
          elapsedMs: 300,
        },
        {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 8,
          tsEpochMs: 1700,
          text: ' Second token.',
        },
        {
          type: 'done',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 9,
          tsEpochMs: 1800,
          usage: { inputTokens: 50, outputTokens: 20, totalTokens: 70 },
          primedMemories: 2,
        },
      ];

      const intermediateSnapshots: ChatTurnView[] = [];

      for (const ev of events) {
        // Must never throw TypeError: Cannot assign to read only property
        expect(() => {
          turn = reduceChatEvents(turn, ev);
        }).not.toThrow();

        // Recursively freeze state immediately
        turn = deepFreeze(turn);
        intermediateSnapshots.push(turn);
      }

      // Verify that all intermediate states remained unchanged
      expect(intermediateSnapshots[0].status).toBe('RUNNING');
      expect(intermediateSnapshots[0].tools.length).toBe(0);

      expect(intermediateSnapshots[2].tools.length).toBe(1);
      expect(intermediateSnapshots[2].tools[0].status).toBe('running');

      expect(intermediateSnapshots[4].tools[0].status).toBe('success');
      expect(intermediateSnapshots[4].tools[1].status).toBe('running');

      expect(intermediateSnapshots[8].status).toBe('DONE');
      expect(intermediateSnapshots[8].isStreaming).toBe(false);
      expect(intermediateSnapshots[8].tools[1].status).toBe('failure');
      expect(intermediateSnapshots[8].assistant.text).toBe('First token emitted. Second token.');
    });
  });

  // =========================================================================
  // FOCUS 4: Rapid interleaved tool execution
  // =========================================================================
  describe('Focus 4: Rapid Interleaved Tool Execution', () => {
    it('handles concurrent tool execution with out-of-order completions (Call A, Call B -> Result B, Result A)', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Interleaved tools');

      // 1. tool_call A
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_A',
        name: 'geo_lookup',
        arguments: { city: 'Austin' },
      });

      // 2. tool_call B
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1050,
        callId: 'call_B',
        name: 'weather_fetch',
        arguments: { lat: 30.2672, lon: -97.7431 },
      });

      // Both running
      expect(turn.tools.length).toBe(2);
      expect(turn.tools[0].callId).toBe('call_A');
      expect(turn.tools[0].status).toBe('running');
      expect(turn.tools[1].callId).toBe('call_B');
      expect(turn.tools[1].status).toBe('running');

      // 3. tool_result B completes FIRST
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1150,
        callId: 'call_B',
        name: 'weather_fetch',
        status: 'success',
        preview: 'Sunny, 78F',
        elapsedMs: 100,
      });

      // Tool A remains running; Tool B is success
      expect(turn.tools[0].callId).toBe('call_A');
      expect(turn.tools[0].status).toBe('running');
      expect(turn.tools[0].preview).toBe('');
      expect(turn.tools[1].callId).toBe('call_B');
      expect(turn.tools[1].status).toBe('success');
      expect(turn.tools[1].preview).toBe('Sunny, 78F');
      expect(turn.tools[1].elapsedMs).toBe(100);

      // 4. tool_result A completes SECOND
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1300,
        callId: 'call_A',
        name: 'geo_lookup',
        status: 'success',
        preview: 'Texas, USA',
        elapsedMs: 300,
      });

      // Both are success, preserving original array index positions
      expect(turn.tools[0].callId).toBe('call_A');
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('Texas, USA');
      expect(turn.tools[0].elapsedMs).toBe(300);

      expect(turn.tools[1].callId).toBe('call_B');
      expect(turn.tools[1].status).toBe('success');
      expect(turn.tools[1].preview).toBe('Sunny, 78F');
    });

    it('handles high concurrency with 6 interleaved tools completing in reverse order', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      const toolCount = 6;

      // Register tools 0 through 5
      for (let i = 0; i < toolCount; i++) {
        turn = reduceChatEvents(turn, {
          type: 'tool_call',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: i + 2,
          tsEpochMs: 1000 + i * 50,
          callId: `call_${i}`,
          name: `tool_${i}`,
          arguments: { index: i },
        });
      }
      expect(turn.tools.length).toBe(toolCount);

      // Complete tools in reverse order (5 down to 0)
      for (let i = toolCount - 1; i >= 0; i--) {
        turn = reduceChatEvents(turn, {
          type: 'tool_result',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 10 + (toolCount - i),
          tsEpochMs: 2000 + (toolCount - i) * 50,
          callId: `call_${i}`,
          name: `tool_${i}`,
          status: i % 2 === 0 ? 'success' : 'failure',
          preview: `result_${i}`,
          elapsedMs: 100 * (i + 1),
        });
      }

      // Check all 6 tools preserve index ordering and have expected values
      for (let i = 0; i < toolCount; i++) {
        expect(turn.tools[i].callId).toBe(`call_${i}`);
        expect(turn.tools[i].status).toBe(i % 2 === 0 ? 'success' : 'failure');
        expect(turn.tools[i].preview).toBe(`result_${i}`);
        expect(turn.tools[i].elapsedMs).toBe(100 * (i + 1));
      }
    });
  });

  // =========================================================================
  // FOCUS 5: Extreme payload handling (64 KiB tool results & truncation)
  // =========================================================================
  describe('Focus 5: Extreme Payload Handling & Truncation Invariants', () => {
    it('handles 64 KiB tool results and automatically flags truncated = true when >= 2048 chars', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Payload stress test');

      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_huge',
        name: 'large_data_dump',
        arguments: {},
      });

      // 64 KiB payload (65,536 characters)
      const payload64KiB = 'A'.repeat(65536);

      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1500,
        callId: 'call_huge',
        name: 'large_data_dump',
        status: 'success',
        preview: payload64KiB,
        // truncated flag omitted -> should auto-evaluate to true
        elapsedMs: 500,
      });

      expect(turn.tools[0].preview.length).toBe(65536);
      expect(turn.tools[0].truncated).toBe(true);
    });

    it('verifies strict boundary conditions around 2048 characters for auto-truncation', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Boundary test');

      // Test 2047 chars -> truncated: false
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_2047',
        name: 'tool_test',
        arguments: {},
      });
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        callId: 'call_2047',
        name: 'tool_test',
        status: 'success',
        preview: 'x'.repeat(2047),
      });
      expect(turn.tools[0].truncated).toBe(false);

      // Test 2048 chars -> truncated: true
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        callId: 'call_2048',
        name: 'tool_test',
        arguments: {},
      });
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1300,
        callId: 'call_2048',
        name: 'tool_test',
        status: 'success',
        preview: 'x'.repeat(2048),
      });
      expect(turn.tools[1].truncated).toBe(true);

      // Test explicit truncated: false override on large payload
      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 6,
        tsEpochMs: 1400,
        callId: 'call_override',
        name: 'tool_test',
        arguments: {},
      });
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 7,
        tsEpochMs: 1500,
        callId: 'call_override',
        name: 'tool_test',
        status: 'success',
        preview: 'x'.repeat(5000),
        truncated: false, // explicit false override
      });
      expect(turn.tools[2].truncated).toBe(false);
    });
  });

  // =========================================================================
  // FOCUS 6: Thinking auto-collapse and manual userToggled preservation
  // =========================================================================
  describe('Focus 6: Thinking Auto-Collapse & userToggled Preservation', () => {
    it('auto-collapses thinking on first token when user has not toggled', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Analyzing memory tiers...',
        elapsedMs: 200,
      });
      expect(turn.thinking.isCollapsed).toBe(false);
      expect(turn.thinking.userToggled).toBe(false);

      // First token arrives
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        text: 'Answer text',
      });
      expect(turn.thinking.isCollapsed).toBe(true);
      expect(turn.thinking.userToggled).toBe(false);

      // Subsequent token keeps it collapsed
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1300,
        text: ' continues',
      });
      expect(turn.thinking.isCollapsed).toBe(true);

      // Done keeps it collapsed
      turn = reduceChatEvents(turn, {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1400,
      });
      expect(turn.thinking.isCollapsed).toBe(true);
    });

    it('preserves user expansion (userToggled = true) across incoming streaming tokens and done', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Deep thinking trace',
        elapsedMs: 300,
      });

      // User manually pins or expands thinking
      turn = toggleThinkingCollapse(turn, false);
      expect(turn.thinking.isCollapsed).toBe(false);
      expect(turn.thinking.userToggled).toBe(true);

      // 5 streaming tokens arrive
      for (let i = 0; i < 5; i++) {
        turn = reduceChatEvents(turn, {
          type: 'token',
          sessionId: SESSION_ID,
          turnId: TURN_ID,
          seq: 3 + i,
          tsEpochMs: 1100 + i * 50,
          text: ` token_${i}`,
        });
        // Thinking MUST remain open
        expect(turn.thinking.isCollapsed).toBe(false);
        expect(turn.thinking.userToggled).toBe(true);
      }

      // Done arrives
      turn = reduceChatEvents(turn, {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 10,
        tsEpochMs: 1500,
      });
      expect(turn.thinking.isCollapsed).toBe(false);
    });

    it('preserves user manual re-expansion after auto-collapse on subsequent tokens', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Initial thinking',
      });

      // Token 1 arrives -> auto-collapse
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: 'Token 1',
      });
      expect(turn.thinking.isCollapsed).toBe(true);
      expect(turn.thinking.userToggled).toBe(false);

      // User clicks to re-expand thinking while tokens are still streaming
      turn = toggleThinkingCollapse(turn);
      expect(turn.thinking.isCollapsed).toBe(false);
      expect(turn.thinking.userToggled).toBe(true);

      // Token 2 arrives
      turn = reduceChatEvents(turn, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        text: 'Token 2',
      });
      // MUST stay expanded because user explicitly re-opened it!
      expect(turn.thinking.isCollapsed).toBe(false);
    });

    it('auto-collapses thinking on done event even if no tokens were emitted (tool-only turn)', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

      turn = reduceChatEvents(turn, {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Evaluating tool execution...',
      });
      expect(turn.thinking.isCollapsed).toBe(false);

      turn = reduceChatEvents(turn, {
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        callId: 'call_x',
        name: 'save_state',
        arguments: {},
      });
      turn = reduceChatEvents(turn, {
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        callId: 'call_x',
        name: 'save_state',
        status: 'success',
      });

      // Still no tokens emitted, thinking was open
      expect(turn.thinking.isCollapsed).toBe(false);

      // Done arrives
      turn = reduceChatEvents(turn, {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1300,
      });

      // Must auto-collapse on done
      expect(turn.thinking.isCollapsed).toBe(true);
    });
  });
});
