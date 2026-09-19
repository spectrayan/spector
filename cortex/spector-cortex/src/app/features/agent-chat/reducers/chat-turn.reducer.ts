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
  ToolExecutionView,
} from '../models/chat-turn.model';

export function createEmptyTurn(turnId = '', sessionId = '', userText = ''): ChatTurnView {
  return {
    turnId,
    sessionId,
    seq: 0,
    status: 'RUNNING',
    isStreaming: true,
    user: { text: userText },
    thinking: {
      text: '',
      elapsedMs: 0,
      isCollapsed: false,
      userToggled: false,
    },
    tools: [],
    assistant: { text: '' },
    usage: null,
    primedMemories: 0,
    error: null,
    hasError: false,
    errorMessage: '',
  };
}

export function createInitialTurn(
  turnId = '',
  sessionId = '',
  seq = 1,
  userText = '',
): ChatTurnView {
  return {
    ...createEmptyTurn(turnId, sessionId, userText),
    seq,
  };
}

export function reduceChatEvents(prev: ChatTurnView, event: ChatStreamEvent): ChatTurnView {
  const eventType = (event as any).type || (event as any).eventType;
  switch (eventType) {
    case 'session':
      return handleSessionEvent(prev, event as SessionStreamEvent);
    case 'thinking':
      return handleThinkingEvent(prev, event as ThinkingStreamEvent);
    case 'token':
    case 'content':
      return handleTokenEvent(prev, event as TokenStreamEvent);
    case 'tool_call':
      return handleToolCallEvent(prev, event as ToolCallStreamEvent);
    case 'tool_result':
      return handleToolResultEvent(prev, event as ToolResultStreamEvent);
    case 'done':
      return handleDoneEvent(prev, event as DoneStreamEvent);
    case 'error':
      return handleErrorEvent(prev, event as ErrorStreamEvent);
    default:
      return prev;
  }
}

function handleSessionEvent(prev: ChatTurnView, event: SessionStreamEvent): ChatTurnView {
  return {
    ...prev,
    turnId: event.turnId || prev.turnId,
    sessionId: event.sessionId || prev.sessionId,
    seq: Math.max(prev.seq, event.seq ?? 0),
    status: 'RUNNING',
    isStreaming: true,
  };
}

function handleThinkingEvent(prev: ChatTurnView, event: ThinkingStreamEvent): ChatTurnView {
  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    thinking: {
      ...prev.thinking,
      text: prev.thinking.text + (event.text ?? ''),
      elapsedMs: event.elapsedMs ?? prev.thinking.elapsedMs,
      isCollapsed: prev.thinking.userToggled ? prev.thinking.isCollapsed : false,
    },
  };
}

function handleTokenEvent(prev: ChatTurnView, event: TokenStreamEvent): ChatTurnView {
  const shouldAutoCollapse = !prev.thinking.userToggled;
  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    isStreaming: true,
    assistant: {
      ...prev.assistant,
      text: prev.assistant.text + (event.text ?? ''),
    },
    thinking: {
      ...prev.thinking,
      isCollapsed: shouldAutoCollapse ? true : prev.thinking.isCollapsed,
    },
  };
}

function handleToolCallEvent(prev: ChatTurnView, event: ToolCallStreamEvent): ChatTurnView {
  const existingIndex = prev.tools.findIndex((t) => t.callId === event.callId);
  const newTool: ToolExecutionView = {
    callId: event.callId,
    name: event.name,
    arguments: event.arguments ?? {},
    status: 'running',
    preview: '',
    truncated: false,
    elapsedMs: 0,
  };

  let nextTools: readonly ToolExecutionView[];
  if (existingIndex >= 0) {
    nextTools = prev.tools.map((t, idx) => (idx === existingIndex ? { ...t, ...newTool } : t));
  } else {
    nextTools = [...prev.tools, newTool];
  }

  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    tools: nextTools,
  };
}

function handleToolResultEvent(prev: ChatTurnView, event: ToolResultStreamEvent): ChatTurnView {
  const isFailure = event.status === 'error' || event.status === 'failure';
  const preview = event.preview ?? '';
  const truncated = event.truncated ?? preview.length >= 2048;
  const elapsedMs = event.elapsedMs ?? 0;

  const existingIndex = prev.tools.findIndex((t) => t.callId === event.callId);

  let nextTools: readonly ToolExecutionView[];
  if (existingIndex >= 0) {
    nextTools = prev.tools.map((tool, idx) => {
      if (idx === existingIndex) {
        return {
          ...tool,
          status: (isFailure ? 'failure' : 'success') as ToolExecutionView['status'],
          preview,
          truncated,
          elapsedMs: elapsedMs > 0 ? elapsedMs : tool.elapsedMs,
        };
      }
      return tool;
    });
  } else {
    const synthesizedTool: ToolExecutionView = {
      callId: event.callId,
      name: event.name,
      arguments: {},
      status: isFailure ? 'failure' : 'success',
      preview,
      truncated,
      elapsedMs,
    };
    nextTools = [...prev.tools, synthesizedTool];
  }

  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    tools: nextTools,
  };
}

function handleDoneEvent(prev: ChatTurnView, event: DoneStreamEvent): ChatTurnView {
  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    status: 'DONE',
    isStreaming: false,
    primedMemories: event.primedMemories ?? prev.primedMemories,
    usage: event.usage ?? prev.usage,
    thinking: {
      ...prev.thinking,
      isCollapsed: prev.thinking.userToggled ? prev.thinking.isCollapsed : true,
    },
  };
}

function handleErrorEvent(prev: ChatTurnView, event: ErrorStreamEvent): ChatTurnView {
  return {
    ...prev,
    seq: Math.max(prev.seq, event.seq ?? 0),
    status: 'ERROR',
    isStreaming: false,
    hasError: true,
    errorMessage: event.message,
    error: {
      code: event.code,
      message: event.message,
      retryable: event.retryable ?? false,
    },
  };
}

export function reduceTurnEventStream(
  initial: ChatTurnView,
  events: readonly ChatStreamEvent[],
): ChatTurnView {
  return events.reduce(reduceChatEvents, initial);
}

export function toggleThinkingCollapse(turn: ChatTurnView, forceCollapsed?: boolean): ChatTurnView {
  const isCollapsed = forceCollapsed !== undefined ? forceCollapsed : !turn.thinking.isCollapsed;
  return {
    ...turn,
    thinking: {
      ...turn.thinking,
      isCollapsed,
      userToggled: true,
    },
  };
}

export function hydrateTurnFromHistory(
  rawTurn: Partial<ChatTurnView>,
  sessionId = '',
): ChatTurnView {
  return {
    turnId: rawTurn.turnId ?? '',
    sessionId: rawTurn.sessionId || sessionId,
    seq: rawTurn.seq ?? 0,
    status: rawTurn.status ?? 'DONE',
    isStreaming: false,
    user: {
      text: rawTurn.user?.text ?? '',
    },
    thinking: {
      text: rawTurn.thinking?.text ?? '',
      elapsedMs: rawTurn.thinking?.elapsedMs ?? 0,
      isCollapsed: true,
      userToggled: false,
    },
    tools: (rawTurn.tools ?? []).map((t) => ({
      callId: t.callId,
      name: t.name,
      arguments: t.arguments ?? {},
      status: t.status,
      preview: t.preview ?? '',
      truncated: t.truncated ?? false,
      elapsedMs: t.elapsedMs ?? 0,
    })),
    assistant: {
      text: rawTurn.assistant?.text ?? '',
    },
    usage: rawTurn.usage ?? null,
    primedMemories: rawTurn.primedMemories ?? 0,
    error: rawTurn.error ?? null,
    hasError: rawTurn.hasError ?? false,
    errorMessage: rawTurn.errorMessage ?? '',
  };
}
