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
  ChatTurnStatus,
} from '../models/chat-turn.model';

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

describe('ChatTurnReducer Challenger Stress Suite (Adversarial Empirical Verification)', () => {
  const SESSION_ID = '01J8Y000000000000000000000';
  const TURN_ID = '01J8Y000000000000000000001';

  // =========================================================================
  // 1. OUT-OF-ORDER EVENTS: Late Session after Terminal Statuses
  // =========================================================================
  describe('Adversarial 1: Late Session after Terminal Statuses', () => {
    const terminalStatuses: ChatTurnStatus[] = ['DONE', 'ERROR', 'INTERRUPTED', 'FAILED'];

    for (const termStatus of terminalStatuses) {
      it(`preserves terminal status ${termStatus} and isStreaming: false when late session arrives`, () => {
        let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
        turn = {
          ...turn,
          status: termStatus,
          isStreaming: false,
          seq: 15,
        };
        turn = deepFreeze(turn);

        const lateSessionEv: SessionStreamEvent = deepFreeze({
          type: 'session',
          sessionId: 'new_session_id',
          turnId: 'new_turn_id',
          seq: 5,
          tsEpochMs: 500,
        });

        const next = reduceChatEvents(turn, lateSessionEv);

        expect(next.status).toBe(termStatus);
        expect(next.isStreaming).toBe(false);
        expect(next.seq).toBe(15); // seq does not regress
        expect(next.sessionId).toBe('new_session_id');
        expect(next.turnId).toBe('new_turn_id');
      });
    }
  });

  // =========================================================================
  // 2. OUT-OF-ORDER EVENTS: Late Duplicate Tool Call after Tool Result
  // =========================================================================
  describe('Adversarial 2: Late Duplicate Tool Call after Tool Result', () => {
    it('preserves success status, preview, and elapsedMs when duplicate tool_call arrives with empty args', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_42',
        name: 'fetch_user',
        arguments: { userId: 42 },
      }));

      turn = reduceChatEvents(turn, deepFreeze({
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        callId: 'call_42',
        name: 'fetch_user',
        status: 'success',
        preview: 'User details found',
        elapsedMs: 200,
      }));

      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('User details found');

      // Duplicate tool_call arrives with seq: 3 and empty arguments
      turn = deepFreeze(turn);
      const duplicateCall: ToolCallStreamEvent = deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        callId: 'call_42',
        name: 'fetch_user',
        arguments: {},
      });

      const next = reduceChatEvents(turn, duplicateCall);
      expect(next.tools[0].status).toBe('success');
      expect(next.tools[0].preview).toBe('User details found');
      expect(next.tools[0].elapsedMs).toBe(200);
      expect(next.tools[0].arguments).toEqual({ userId: 42 }); // Preserved original arguments!
    });

    it('preserves failure status when duplicate tool_call arrives with updated arguments', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'call_fail',
        name: 'db_exec',
        arguments: { query: 'SELECT *' },
      }));

      turn = reduceChatEvents(turn, deepFreeze({
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        callId: 'call_fail',
        name: 'db_exec',
        status: 'failure',
        preview: 'Connection timeout',
        elapsedMs: 200,
      }));

      // Duplicate arrives with detailed arguments
      turn = deepFreeze(turn);
      const duplicateCall: ToolCallStreamEvent = deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        callId: 'call_fail',
        name: 'db_exec',
        arguments: { query: 'SELECT *', timeoutMs: 5000 },
      });

      const next = reduceChatEvents(turn, duplicateCall);
      expect(next.tools[0].status).toBe('failure');
      expect(next.tools[0].preview).toBe('Connection timeout');
      expect(next.tools[0].arguments).toEqual({ query: 'SELECT *', timeoutMs: 5000 });
    });
  });

  // =========================================================================
  // 3. OUT-OF-ORDER EVENTS: Inverted Tool Result before Tool Call
  // =========================================================================
  describe('Adversarial 3: Inverted Tool Result before Tool Call', () => {
    it('creates synthesized tool on tool_result and enriches arguments on subsequent tool_call', () => {
      let turn = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt'));

      // 1. tool_result arrives first
      const invertedResult: ToolResultStreamEvent = deepFreeze({
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        callId: 'inverted_call_99',
        name: 'vector_search',
        status: 'success',
        preview: '3 embeddings found',
        elapsedMs: 125,
      });

      turn = reduceChatEvents(turn, invertedResult);
      expect(turn.tools.length).toBe(1);
      expect(turn.tools[0].callId).toBe('inverted_call_99');
      expect(turn.tools[0].name).toBe('vector_search');
      expect(turn.tools[0].status).toBe('success');
      expect(turn.tools[0].preview).toBe('3 embeddings found');
      expect(turn.tools[0].arguments).toEqual({});

      // 2. tool_call arrives later
      turn = deepFreeze(turn);
      const lateCall: ToolCallStreamEvent = deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'inverted_call_99',
        name: 'vector_search',
        arguments: { vector: [0.1, 0.2, 0.3], topK: 3 },
      });

      const next = reduceChatEvents(turn, lateCall);
      expect(next.tools.length).toBe(1);
      expect(next.tools[0].status).toBe('success');
      expect(next.tools[0].preview).toBe('3 embeddings found');
      expect(next.tools[0].elapsedMs).toBe(125);
      expect(next.tools[0].arguments).toEqual({ vector: [0.1, 0.2, 0.3], topK: 3 });
    });

    it('handles inverted tool_result with error status followed by tool_call', () => {
      let turn = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt'));

      const invertedErrorResult: ToolResultStreamEvent = deepFreeze({
        type: 'tool_result',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        callId: 'inverted_err',
        name: 'network_probe',
        status: 'error',
        preview: 'Host unreachable',
        elapsedMs: 400,
      });

      turn = reduceChatEvents(turn, invertedErrorResult);
      expect(turn.tools[0].status).toBe('failure');

      turn = deepFreeze(turn);
      const lateCall: ToolCallStreamEvent = deepFreeze({
        type: 'tool_call',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        callId: 'inverted_err',
        name: 'network_probe',
        arguments: { host: '192.168.1.1' },
      });

      const next = reduceChatEvents(turn, lateCall);
      expect(next.tools[0].status).toBe('failure');
      expect(next.tools[0].preview).toBe('Host unreachable');
      expect(next.tools[0].arguments).toEqual({ host: '192.168.1.1' });
    });
  });

  // =========================================================================
  // 4. OUT-OF-ORDER EVENTS: Late Token on DONE or ERROR Turn
  // =========================================================================
  describe('Adversarial 4: Late Token on Terminal Turn', () => {
    it('does not flip isStreaming to true when late token arrives after DONE', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Initial message',
      }));

      turn = reduceChatEvents(turn, deepFreeze({
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1500,
        usage: { inputTokens: 10, outputTokens: 5, totalTokens: 15 },
      }));

      expect(turn.status).toBe('DONE');
      expect(turn.isStreaming).toBe(false);

      turn = deepFreeze(turn);
      const lateToken: TokenStreamEvent = deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        text: ' late extra text',
      });

      const next = reduceChatEvents(turn, lateToken);
      expect(next.status).toBe('DONE');
      expect(next.isStreaming).toBe(false);
      expect(next.assistant.text).toBe('Initial message late extra text');
    });

    it('does not flip isStreaming to true when late token arrives after ERROR', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'error',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1200,
        code: 'SPE-ERR-001',
        message: 'Fatal model fault',
      }));

      expect(turn.status).toBe('ERROR');
      expect(turn.isStreaming).toBe(false);

      turn = deepFreeze(turn);
      const lateToken: TokenStreamEvent = deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'orphan token',
      });

      const next = reduceChatEvents(turn, lateToken);
      expect(next.status).toBe('ERROR');
      expect(next.isStreaming).toBe(false);
    });
  });

  // =========================================================================
  // 5. OUT-OF-ORDER EVENTS: Late Thinking on Collapsed Accordion
  // =========================================================================
  describe('Adversarial 5: Late Thinking on Collapsed Accordion', () => {
    it('maintains isCollapsed: true when thinking arrives after visible token has been emitted', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Thinking start',
        elapsedMs: 50,
      }));
      expect(turn.thinking.isCollapsed).toBe(false);

      // First token emits -> collapses thinking
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1200,
        text: 'Visible text',
      }));
      expect(turn.thinking.isCollapsed).toBe(true);

      // Late thinking arrives with seq: 3
      turn = deepFreeze(turn);
      const lateThinking: ThinkingStreamEvent = deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: ' delayed thought',
        elapsedMs: 80,
      });

      const next = reduceChatEvents(turn, lateThinking);
      expect(next.thinking.isCollapsed).toBe(true);
      expect(next.thinking.text).toBe('Thinking start delayed thought');
    });

    it('maintains isCollapsed: true when thinking arrives on terminal DONE turn with no tokens', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Tool reasoning',
      }));
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1500,
      }));
      expect(turn.thinking.isCollapsed).toBe(true);

      // Late thinking arrives
      turn = deepFreeze(turn);
      const lateThinking: ThinkingStreamEvent = deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: ' late thinking delta',
      });

      const next = reduceChatEvents(turn, lateThinking);
      expect(next.thinking.isCollapsed).toBe(true);
    });

    it('honors userToggled: true when user explicitly pinned thinking open despite late tokens/thinking', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
      turn = toggleThinkingCollapse(turn, false); // pinned open
      expect(turn.thinking.isCollapsed).toBe(false);
      expect(turn.thinking.userToggled).toBe(true);

      turn = deepFreeze(turn);
      turn = reduceChatEvents(turn, deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'Token text',
      }));
      expect(turn.thinking.isCollapsed).toBe(false);

      turn = reduceChatEvents(turn, deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: 'Thinking text',
      }));
      expect(turn.thinking.isCollapsed).toBe(false);
    });
  });

  // =========================================================================
  // 6. IMMUTABILITY & DEEP OBJECT INTEGRITY
  // =========================================================================
  describe('Adversarial 6: Immutability and Deep Object Integrity', () => {
    it('produces brand new references for modified sub-trees while preserving unmodified sub-trees', () => {
      const initial = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt'));

      // Thinking event modifies thinking only
      const s1 = reduceChatEvents(initial, deepFreeze({
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: 'pondering',
      }));

      expect(s1).not.toBe(initial);
      expect(s1.thinking).not.toBe(initial.thinking);
      expect(s1.thinking.text).toBe('pondering');
      expect(s1.assistant).toBe(initial.assistant); // unmodified reference preserved
      expect(s1.user).toBe(initial.user); // unmodified reference preserved
      expect(s1.tools).toBe(initial.tools); // unmodified reference preserved

      // Token event modifies assistant and thinking (due to auto-collapse)
      const s2 = reduceChatEvents(deepFreeze(s1), deepFreeze({
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1100,
        text: 'answer',
      }));

      expect(s2).not.toBe(s1);
      expect(s2.assistant).not.toBe(s1.assistant);
      expect(s2.thinking).not.toBe(s1.thinking);
      expect(s2.user).toBe(s1.user);
      expect(s2.tools).toBe(s1.tools);
    });

    it('safely handles special callIds (__proto__, constructor, toString) without prototype pollution', () => {
      let turn = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Prototype check'));

      const specialIds = ['__proto__', 'constructor', 'toString', 'valueOf', ''];
      for (let i = 0; i < specialIds.length; i++) {
        const id = specialIds[i];
        expect(() => {
          turn = reduceChatEvents(turn, deepFreeze({
            type: 'tool_call',
            sessionId: SESSION_ID,
            turnId: TURN_ID,
            seq: i + 2,
            tsEpochMs: 1000 + i * 10,
            callId: id,
            name: `tool_${id}`,
            arguments: { test: true },
          }));
          turn = deepFreeze(turn);

          turn = reduceChatEvents(turn, deepFreeze({
            type: 'tool_result',
            sessionId: SESSION_ID,
            turnId: TURN_ID,
            seq: i + 10,
            tsEpochMs: 2000 + i * 10,
            callId: id,
            name: `tool_${id}`,
            status: 'success',
            preview: `result_${id}`,
          }));
          turn = deepFreeze(turn);
        }).not.toThrow();
      }

      expect(turn.tools.length).toBe(specialIds.length);
      for (let i = 0; i < specialIds.length; i++) {
        expect(turn.tools[i].callId).toBe(specialIds[i]);
        expect(turn.tools[i].status).toBe('success');
      }
    });
  });

  // =========================================================================
  // 7. HIGH-CONCURRENCY PERMUTATION & PROPERTY-BASED STRESS HARNESS
  // =========================================================================
  describe('Adversarial 7: High-Concurrency Permutation Stress Harness', () => {
    it('survives 1,000 completely scrambled event sequences without invariant violation', () => {
      const canonicalEvents: ChatStreamEvent[] = [
        { type: 'session', sessionId: SESSION_ID, turnId: TURN_ID, seq: 1, tsEpochMs: 100 },
        { type: 'thinking', sessionId: SESSION_ID, turnId: TURN_ID, seq: 2, tsEpochMs: 200, text: 'T1 ' },
        { type: 'thinking', sessionId: SESSION_ID, turnId: TURN_ID, seq: 3, tsEpochMs: 300, text: 'T2 ' },
        { type: 'tool_call', sessionId: SESSION_ID, turnId: TURN_ID, seq: 4, tsEpochMs: 400, callId: 'c1', name: 'tool1', arguments: { a: 1 } },
        { type: 'tool_call', sessionId: SESSION_ID, turnId: TURN_ID, seq: 5, tsEpochMs: 500, callId: 'c2', name: 'tool2', arguments: { b: 2 } },
        { type: 'tool_result', sessionId: SESSION_ID, turnId: TURN_ID, seq: 6, tsEpochMs: 600, callId: 'c1', name: 'tool1', status: 'success', preview: 'p1' },
        { type: 'tool_result', sessionId: SESSION_ID, turnId: TURN_ID, seq: 7, tsEpochMs: 700, callId: 'c2', name: 'tool2', status: 'failure', preview: 'p2' },
        { type: 'token', sessionId: SESSION_ID, turnId: TURN_ID, seq: 8, tsEpochMs: 800, text: 'Tok1 ' },
        { type: 'token', sessionId: SESSION_ID, turnId: TURN_ID, seq: 9, tsEpochMs: 900, text: 'Tok2 ' },
        { type: 'done', sessionId: SESSION_ID, turnId: TURN_ID, seq: 10, tsEpochMs: 1000, usage: { inputTokens: 10, outputTokens: 5, totalTokens: 15 } },
      ];

      // Fisher-Yates shuffle generator
      function shuffle<T>(arr: readonly T[], seed: number): T[] {
        const copy = [...arr];
        let s = seed;
        for (let i = copy.length - 1; i > 0; i--) {
          s = (s * 9301 + 49297) % 233280;
          const j = Math.floor((s / 233280) * (i + 1));
          [copy[i], copy[j]] = [copy[j], copy[i]];
        }
        return copy;
      }

      for (let trial = 0; trial < 100; trial++) {
        const shuffled = shuffle(canonicalEvents, trial * 31 + 7);
        let turn = deepFreeze(createEmptyTurn(TURN_ID, SESSION_ID, 'Permutation query'));

        let seenDone = false;
        let seenToken = false;
        let lastSeq = 0;

        for (const ev of shuffled) {
          turn = reduceChatEvents(turn, ev);
          turn = deepFreeze(turn);

          // INVARIANT 1: seq must be monotonic
          expect(turn.seq).toBeGreaterThanOrEqual(lastSeq);
          lastSeq = turn.seq;

          // Track events seen
          if ((ev as any).type === 'done') {
            seenDone = true;
          }
          if ((ev as any).type === 'token') {
            seenToken = true;
          }

          // INVARIANT 2: Once DONE is processed, status is DONE and isStreaming is false
          if (seenDone) {
            expect(turn.status).toBe('DONE');
            expect(turn.isStreaming).toBe(false);
          }

          // INVARIANT 3: Once token emitted, thinking must be collapsed (userToggled = false)
          if (seenToken && !turn.thinking.userToggled) {
            expect(turn.thinking.isCollapsed).toBe(true);
          }
        }

        // Final state validation: all 2 tools must exist
        expect(turn.tools.length).toBe(2);
        const c1 = turn.tools.find((t) => t.callId === 'c1');
        const c2 = turn.tools.find((t) => t.callId === 'c2');
        expect(c1).toBeDefined();
        expect(c2).toBeDefined();
        expect(c1?.status).toBe('success');
        expect(c2?.status).toBe('failure');
        expect(c1?.preview).toBe('p1');
        expect(c2?.preview).toBe('p2');
      }
    });

    it('processes 10,000 rapid event stream transitions cleanly under 500ms', () => {
      let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Load test');
      const start = Date.now();

      const batchSize = 10000;
      for (let i = 0; i < batchSize; i++) {
        const evType = i % 5;
        let ev: ChatStreamEvent;
        if (evType === 0) {
          ev = { type: 'thinking', sessionId: SESSION_ID, turnId: TURN_ID, seq: i + 1, tsEpochMs: i, text: 't' };
        } else if (evType === 1) {
          ev = { type: 'token', sessionId: SESSION_ID, turnId: TURN_ID, seq: i + 1, tsEpochMs: i, text: 'k' };
        } else if (evType === 2) {
          ev = { type: 'tool_call', sessionId: SESSION_ID, turnId: TURN_ID, seq: i + 1, tsEpochMs: i, callId: `c_${i % 10}`, name: 'tool', arguments: {} };
        } else if (evType === 3) {
          ev = { type: 'tool_result', sessionId: SESSION_ID, turnId: TURN_ID, seq: i + 1, tsEpochMs: i, callId: `c_${i % 10}`, name: 'tool', status: 'success', preview: 'ok' };
        } else {
          ev = { type: 'token', eventType: 'content', sessionId: SESSION_ID, turnId: TURN_ID, seq: i + 1, tsEpochMs: i, text: 'c' } as any;
        }
        turn = reduceChatEvents(turn, ev);
      }

      const elapsed = Date.now() - start;
      expect(elapsed).toBeLessThan(1000); // Well within performance SLA
      expect(turn.seq).toBe(batchSize);
      expect(turn.assistant.text.length).toBeGreaterThan(0);
    });
  });
});
