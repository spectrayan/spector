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

export type ChatTurnStatus = 'RUNNING' | 'DONE' | 'ERROR' | 'INTERRUPTED' | 'FAILED';
export type ToolStatus = 'running' | 'success' | 'failure';

export interface UserMessageView {
  readonly text: string;
  readonly timestamp?: Date | number;
}

export interface ThinkingView {
  readonly text: string;
  readonly elapsedMs: number;
  readonly isCollapsed: boolean;
  readonly userToggled: boolean;
}

export interface ToolExecutionView {
  readonly callId: string;
  readonly name: string;
  readonly arguments: Record<string, unknown>;
  readonly status: ToolStatus;
  readonly preview: string;
  readonly truncated: boolean;
  readonly elapsedMs: number;
}

export type ToolCardView = ToolExecutionView;

export interface AssistantMessageView {
  readonly text: string;
}

export interface UsageView {
  readonly inputTokens: number;
  readonly outputTokens: number;
  readonly totalTokens: number;
}

export type TokenUsageDto = UsageView;

export interface ErrorView {
  readonly code?: string;
  readonly message: string;
  readonly retryable?: boolean;
}

export interface ChatTurnView {
  readonly turnId: string;
  readonly sessionId: string;
  readonly seq: number;
  readonly status: ChatTurnStatus;
  readonly isStreaming: boolean;
  readonly user: UserMessageView;
  readonly thinking: ThinkingView;
  readonly tools: readonly ToolExecutionView[];
  readonly assistant: AssistantMessageView;
  readonly usage: UsageView | null;
  readonly primedMemories: number;
  readonly error: ErrorView | null;
  readonly hasError?: boolean;
  readonly errorMessage?: string;
}

export type ChatEventType =
  | 'session'
  | 'thinking'
  | 'token'
  | 'content'
  | 'tool_call'
  | 'tool_result'
  | 'done'
  | 'error';

export interface BaseStreamEvent {
  readonly sessionId: string;
  readonly turnId: string;
  readonly seq: number;
  readonly tsEpochMs: number;
  readonly type?: string;
  readonly eventType?: string;
}

export interface SessionStreamEvent extends BaseStreamEvent {
  readonly type: 'session';
  readonly eventType?: 'session';
  readonly isNew?: boolean;
  readonly model?: string;
}

export interface ThinkingStreamEvent extends BaseStreamEvent {
  readonly type: 'thinking';
  readonly eventType?: 'thinking';
  readonly text: string;
  readonly elapsedMs?: number;
}

export interface TokenStreamEvent extends BaseStreamEvent {
  readonly type: 'token' | 'content';
  readonly eventType?: 'token' | 'content';
  readonly text: string;
}

export interface ToolCallStreamEvent extends BaseStreamEvent {
  readonly type: 'tool_call';
  readonly eventType?: 'tool_call';
  readonly callId: string;
  readonly name: string;
  readonly arguments: Record<string, unknown>;
}

export interface ToolResultStreamEvent extends BaseStreamEvent {
  readonly type: 'tool_result';
  readonly eventType?: 'tool_result';
  readonly callId: string;
  readonly name: string;
  readonly status: 'success' | 'failure' | string;
  readonly preview?: string;
  readonly truncated?: boolean;
  readonly elapsedMs?: number;
}

export interface DoneStreamEvent extends BaseStreamEvent {
  readonly type: 'done';
  readonly eventType?: 'done';
  readonly summary?: string;
  readonly latencyMs?: number;
  readonly primedMemories?: number;
  readonly usage?: UsageView;
}

export interface ErrorStreamEvent extends BaseStreamEvent {
  readonly type: 'error';
  readonly eventType?: 'error';
  readonly code?: string;
  readonly message: string;
  readonly retryable?: boolean;
}

export type ChatStreamEvent =
  | SessionStreamEvent
  | ThinkingStreamEvent
  | TokenStreamEvent
  | ToolCallStreamEvent
  | ToolResultStreamEvent
  | DoneStreamEvent
  | ErrorStreamEvent;

// ── Operational Plane Session & History Models ──

export interface SessionSummary {
  readonly sessionId: string;
  readonly preview: string;
  readonly lastActivity: string;
  readonly messageCount: number;
  readonly title?: string;
  readonly updatedAt?: string;
}

export interface SessionsResponse {
  readonly sessions: SessionSummary[];
  readonly hasMore: boolean;
}

export interface ChatSessionRecord {
  readonly id: string;
  readonly title: string;
  readonly status: string;
  readonly archived?: boolean;
  readonly createdAt?: string;
  readonly updatedAt?: string;
}

export interface RenameSessionRequest {
  readonly title: string;
}

export interface SessionHistoryResponse {
  readonly sessionId: string;
  readonly title?: string;
  readonly turns: readonly ChatTurnView[];
  readonly messages?: readonly { role: string; content: string }[];
}

export interface AgentChatRequest {
  readonly message: string;
  readonly sessionId?: string;
  readonly conversationId?: string;
  readonly model?: string;
  readonly contextDepth?: number;
  readonly enableGraph?: boolean;
  readonly enableTextSearch?: boolean;
  readonly enableTrace?: boolean;
  readonly messages?: readonly { role: string; content: string }[];
}

export interface ChatConfig {
  readonly defaultModel: string;
  readonly maxContextDepth: number;
  readonly defaultContextDepth: number;
  readonly agentMode: boolean;
  readonly version: string;
}

export interface OllamaModel {
  readonly id: string;
  readonly name: string;
  readonly size: number;
  readonly modified_at: string;
  readonly active: boolean;
}

export interface ModelsResponse {
  readonly models: OllamaModel[];
}

export interface ToolsResponse {
  readonly tools: Array<{ name: string; description?: string; parameters?: any }>;
}
