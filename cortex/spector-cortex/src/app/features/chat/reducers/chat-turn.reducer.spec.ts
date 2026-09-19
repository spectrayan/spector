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

describe('ChatTurnReducer (reduceChatEvents)', () => {
  const SESSION_ID = '01J8Y000000000000000000000';
  const TURN_ID = '01J8Y000000000000000000001';

  it('initializes a turn with session event', () => {
    const initial = createEmptyTurn('', '', 'What is Spector?');
    const sessionEv: SessionStreamEvent = {
      type: 'session',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 1,
      tsEpochMs: 1758265000000,
      isNew: true,
      model: 'qwen2.5:7b',
    };

    const next = reduceChatEvents(initial, sessionEv);

    expect(next.turnId).toBe(TURN_ID);
    expect(next.sessionId).toBe(SESSION_ID);
    expect(next.seq).toBe(1);
    expect(next.status).toBe('RUNNING');
    expect(next.isStreaming).toBe(true);
    expect(next.user.text).toBe('What is Spector?');
  });

  it('accumulates thinking deltas and updates elapsedMs', () => {
    const turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

    const think1: ThinkingStreamEvent = {
      type: 'thinking',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1758265000200,
      text: 'Querying episodic memory',
      elapsedMs: 200,
    };

    const think2: ThinkingStreamEvent = {
      type: 'thinking',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1758265000650,
      text: ' and synthesizing results.',
      elapsedMs: 650,
    };

    const s1 = reduceChatEvents(turn, think1);
    expect(s1.thinking.text).toBe('Querying episodic memory');
    expect(s1.thinking.elapsedMs).toBe(200);
    expect(s1.thinking.isCollapsed).toBe(false);

    const s2 = reduceChatEvents(s1, think2);
    expect(s2.thinking.text).toBe('Querying episodic memory and synthesizing results.');
    expect(s2.thinking.elapsedMs).toBe(650);
    expect(s2.thinking.isCollapsed).toBe(false);
  });

  it('accumulates tokens and auto-collapses thinking on first token', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
    turn = reduceChatEvents(turn, {
      type: 'thinking',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1758265000200,
      text: 'Reasoning trace...',
      elapsedMs: 200,
    });
    expect(turn.thinking.isCollapsed).toBe(false);

    const token1: TokenStreamEvent = {
      type: 'token',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 4,
      tsEpochMs: 1758265000750,
      text: 'Hello, ',
    };

    const next = reduceChatEvents(turn, token1);
    expect(next.assistant.text).toBe('Hello, ');
    // Auto-collapsed because user has not manually toggled
    expect(next.thinking.isCollapsed).toBe(true);
    expect(next.thinking.userToggled).toBe(false);

    const token2: TokenStreamEvent = {
      type: 'token',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 5,
      tsEpochMs: 1758265000850,
      text: 'world!',
    };

    const next2 = reduceChatEvents(next, token2);
    expect(next2.assistant.text).toBe('Hello, world!');
    expect(next2.thinking.isCollapsed).toBe(true);
  });

  it('supports backward-compatibility alias "content" for token event', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
    const tokenEv: TokenStreamEvent = {
      type: 'token',
      eventType: 'content',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1758265000750,
      text: 'Aliased content text',
    };

    const next = reduceChatEvents(turn, tokenEv);
    expect(next.assistant.text).toBe('Aliased content text');
    expect(next.isStreaming).toBe(true);
  });

  it('respects user manual override and does NOT auto-collapse thinking when userToggled is true', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
    turn = reduceChatEvents(turn, {
      type: 'thinking',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1758265000200,
      text: 'Reasoning...',
      elapsedMs: 200,
    });

    // User explicitly expands or pins thinking
    turn = toggleThinkingCollapse(turn, false); // isCollapsed = false, userToggled = true
    expect(turn.thinking.isCollapsed).toBe(false);
    expect(turn.thinking.userToggled).toBe(true);

    // Token arrives
    const tokenEv: TokenStreamEvent = {
      type: 'token',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 4,
      tsEpochMs: 1758265000750,
      text: 'Spector response',
    };

    const next = reduceChatEvents(turn, tokenEv);
    expect(next.assistant.text).toBe('Spector response');
    // MUST remain open because user explicitly opened/pinned it!
    expect(next.thinking.isCollapsed).toBe(false);
  });

  it('tracks tool calls and results with arguments, status, and duration', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

    const toolCall: ToolCallStreamEvent = {
      type: 'tool_call',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1758265002300,
      callId: 'call_recall_001',
      name: 'memory_recall',
      arguments: { query: 'Austin relocation', limit: 3 },
    };

    turn = reduceChatEvents(turn, toolCall);
    expect(turn.tools.length).toBe(1);
    expect(turn.tools[0].callId).toBe('call_recall_001');
    expect(turn.tools[0].name).toBe('memory_recall');
    expect(turn.tools[0].status).toBe('running');
    expect(turn.tools[0].arguments).toEqual({ query: 'Austin relocation', limit: 3 });

    const toolResult: ToolResultStreamEvent = {
      type: 'tool_result',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 4,
      tsEpochMs: 1758265002620,
      callId: 'call_recall_001',
      name: 'memory_recall',
      status: 'success',
      preview: '{"results": ["October 1 move"]}',
      truncated: false,
      elapsedMs: 320,
    };

    turn = reduceChatEvents(turn, toolResult);
    expect(turn.tools.length).toBe(1);
    expect(turn.tools[0].status).toBe('success');
    expect(turn.tools[0].preview).toBe('{"results": ["October 1 move"]}');
    expect(turn.tools[0].elapsedMs).toBe(320);
    expect(turn.tools[0].truncated).toBe(false);
  });

  it('correctly maps tool failure status', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

    turn = reduceChatEvents(turn, {
      type: 'tool_call',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1000,
      callId: 'call_err_01',
      name: 'broken_tool',
      arguments: {},
    });

    turn = reduceChatEvents(turn, {
      type: 'tool_result',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1200,
      callId: 'call_err_01',
      name: 'broken_tool',
      status: 'error',
      preview: 'Tool execution timed out',
      elapsedMs: 200,
    });

    expect(turn.tools[0].status).toBe('failure');
  });

  it('auto-detects truncated flag when preview is >= 2048 chars', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

    const bigPreview = 'x'.repeat(2048);
    turn = reduceChatEvents(turn, {
      type: 'tool_call',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1000,
      callId: 'call_big',
      name: 'big_dump',
      arguments: {},
    });

    turn = reduceChatEvents(turn, {
      type: 'tool_result',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1200,
      callId: 'call_big',
      name: 'big_dump',
      status: 'success',
      preview: bigPreview,
      elapsedMs: 150,
    });

    expect(turn.tools[0].truncated).toBe(true);
  });

  it('tracks multiple interleaved tool calls by callId', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');

    turn = reduceChatEvents(turn, {
      type: 'tool_call',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 2,
      tsEpochMs: 1000,
      callId: 'call_1',
      name: 'tool_one',
      arguments: { step: 1 },
    });

    turn = reduceChatEvents(turn, {
      type: 'tool_call',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1100,
      callId: 'call_2',
      name: 'tool_two',
      arguments: { step: 2 },
    });

    expect(turn.tools.length).toBe(2);
    expect(turn.tools[0].status).toBe('running');
    expect(turn.tools[1].status).toBe('running');

    // Resolve call_2 first (out of order completion)
    turn = reduceChatEvents(turn, {
      type: 'tool_result',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 4,
      tsEpochMs: 1200,
      callId: 'call_2',
      name: 'tool_two',
      status: 'success',
      preview: 'result 2',
      elapsedMs: 100,
    });

    expect(turn.tools[0].status).toBe('running');
    expect(turn.tools[1].status).toBe('success');

    // Resolve call_1
    turn = reduceChatEvents(turn, {
      type: 'tool_result',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 5,
      tsEpochMs: 1300,
      callId: 'call_1',
      name: 'tool_one',
      status: 'success',
      preview: 'result 1',
      elapsedMs: 300,
    });

    expect(turn.tools[0].status).toBe('success');
    expect(turn.tools[1].status).toBe('success');
  });

  it('marks turn as DONE with usage and primedMemories on done event', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
    const doneEv: DoneStreamEvent = {
      type: 'done',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 10,
      tsEpochMs: 1758265001250,
      summary: 'Completed successfully',
      latencyMs: 1250,
      primedMemories: 3,
      usage: {
        inputTokens: 124,
        outputTokens: 48,
        totalTokens: 172,
      },
    };

    turn = reduceChatEvents(turn, doneEv);
    expect(turn.status).toBe('DONE');
    expect(turn.isStreaming).toBe(false);
    expect(turn.primedMemories).toBe(3);
    expect(turn.usage).toEqual({
      inputTokens: 124,
      outputTokens: 48,
      totalTokens: 172,
    });
  });

  it('marks turn as ERROR and records error payload on error event', () => {
    let turn = createEmptyTurn(TURN_ID, SESSION_ID, 'Prompt');
    const errorEv: ErrorStreamEvent = {
      type: 'error',
      sessionId: SESSION_ID,
      turnId: TURN_ID,
      seq: 3,
      tsEpochMs: 1758265004500,
      code: 'SPE-700-001',
      message: 'Upstream LLM connection terminated unexpectedly',
      retryable: true,
    };

    turn = reduceChatEvents(turn, errorEv);
    expect(turn.status).toBe('ERROR');
    expect(turn.isStreaming).toBe(false);
    expect(turn.hasError).toBe(true);
    expect(turn.errorMessage).toBe('Upstream LLM connection terminated unexpectedly');
    expect(turn.error).toEqual({
      code: 'SPE-700-001',
      message: 'Upstream LLM connection terminated unexpectedly',
      retryable: true,
    });
  });

  it('strictly preserves immutability on frozen input states', () => {
    const frozenInitial: ChatTurnView = Object.freeze({
      turnId: TURN_ID,
      sessionId: SESSION_ID,
      seq: 1,
      status: 'RUNNING',
      isStreaming: true,
      user: Object.freeze({ text: 'Frozen user query' }),
      thinking: Object.freeze({
        text: 'Initial thinking',
        elapsedMs: 100,
        isCollapsed: false,
        userToggled: false,
      }),
      tools: Object.freeze([
        Object.freeze({
          callId: 'call_1',
          name: 'tool_1',
          arguments: Object.freeze({ a: 1 }),
          status: 'running',
          preview: '',
          truncated: false,
          elapsedMs: 0,
        }),
      ]) as readonly any[],
      assistant: Object.freeze({ text: 'Start' }),
      usage: null,
      primedMemories: 0,
      error: null,
      hasError: false,
      errorMessage: '',
    }) as unknown as ChatTurnView;

    // Must not throw TypeError: Cannot assign to read only property
    expect(() => {
      const updated = reduceChatEvents(frozenInitial, {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1000,
        text: ' more words',
      });
      expect(updated).not.toBe(frozenInitial);
      expect(updated.assistant).not.toBe(frozenInitial.assistant);
      expect(updated.assistant.text).toBe('Start more words');
    }).not.toThrow();
  });

  it('guarantees replay parity: live SSE stream reduction equals replayed historical state', () => {
    // 1. Live stream fold from fixtures: thinking-then-tokens.sse
    const streamEvents: ChatStreamEvent[] = [
      {
        type: 'session',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 1,
        tsEpochMs: 1758265000000,
        isNew: true,
      },
      {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 2,
        tsEpochMs: 1758265000200,
        text: 'Accessing cognitive memory graph and evaluating memory salience...',
        elapsedMs: 200,
      },
      {
        type: 'thinking',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 3,
        tsEpochMs: 1758265000650,
        text: ' Synthesizing memory engrams with dual-plane operational context.',
        elapsedMs: 650,
      },
      {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 4,
        tsEpochMs: 1758265000750,
        text: 'Spector ',
      },
      {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 5,
        tsEpochMs: 1758265000850,
        text: 'is an off-heap ',
      },
      {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 6,
        tsEpochMs: 1758265000950,
        text: 'cognitive memory engine ',
      },
      {
        type: 'token',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 7,
        tsEpochMs: 1758265001050,
        text: 'designed for autonomous agent architectures.',
      },
      {
        type: 'done',
        sessionId: SESSION_ID,
        turnId: TURN_ID,
        seq: 8,
        tsEpochMs: 1758265001250,
        primedMemories: 3,
        latencyMs: 1250,
        usage: { inputTokens: 124, outputTokens: 48, totalTokens: 172 },
      },
    ];

    const initial = createEmptyTurn(TURN_ID, SESSION_ID, 'What is Spector memory architecture?');
    const finalLiveState = reduceTurnEventStream(initial, streamEvents);

    // 2. State hydrated from REST GET /messages (history-replay.json equivalent)
    const hydratedFromHistory = hydrateTurnFromHistory(
      {
        turnId: TURN_ID,
        sessionId: SESSION_ID,
        seq: 8,
        status: 'DONE',
        user: { text: 'What is Spector memory architecture?' },
        thinking: {
          text: 'Accessing cognitive memory graph and evaluating memory salience... Synthesizing memory engrams with dual-plane operational context.',
          elapsedMs: 650,
          isCollapsed: true,
          userToggled: false,
        },
        tools: [],
        assistant: {
          text: 'Spector is an off-heap cognitive memory engine designed for autonomous agent architectures.',
        },
        usage: { inputTokens: 124, outputTokens: 48, totalTokens: 172 },
        primedMemories: 3,
      },
      SESSION_ID,
    );

    // Compare value equality across all fields
    expect(finalLiveState.status).toBe(hydratedFromHistory.status);
    expect(finalLiveState.assistant.text).toBe(hydratedFromHistory.assistant.text);
    expect(finalLiveState.thinking.text).toBe(hydratedFromHistory.thinking.text);
    expect(finalLiveState.thinking.elapsedMs).toBe(hydratedFromHistory.thinking.elapsedMs);
    expect(finalLiveState.thinking.isCollapsed).toBe(hydratedFromHistory.thinking.isCollapsed);
    expect(finalLiveState.primedMemories).toBe(hydratedFromHistory.primedMemories);
    expect(finalLiveState.usage).toEqual(hydratedFromHistory.usage);
    expect(finalLiveState.tools).toEqual(hydratedFromHistory.tools);
  });
});
