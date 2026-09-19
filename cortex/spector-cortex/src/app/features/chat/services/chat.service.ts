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

import { Injectable, Optional, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ChatStreamClient, StreamOptions } from './chat-stream.client';
import {
  AgentChatRequest,
  ChatConfig,
  ChatSessionRecord,
  ChatStreamEvent,
  ModelsResponse,
  RenameSessionRequest,
  SessionHistoryResponse,
  SessionSummary,
  SessionsResponse,
  ToolsResponse,
} from '../models/chat-turn.model';

/**
 * Primary Angular service coordinating Agent Chat operations in Cortex:
 * - Operational execution plane session lifecycle (CRUD endpoints from Milestone 1)
 * - Real-time SSE streaming fetch execution via ChatStreamClient (Milestone 2)
 * - LLM configuration and tool inspection
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http: HttpClient;
  private readonly streamClient: ChatStreamClient;
  private readonly baseUrl = `${environment.apiUrl}/chat`;

  constructor(
    @Optional() http?: HttpClient,
    @Optional() streamClient?: ChatStreamClient,
  ) {
    let h = http;
    let s = streamClient;
    if (!h) {
      try {
        h = inject(HttpClient);
      } catch {
        // Mock injection for standalone unit tests
      }
    }
    if (!s) {
      try {
        s = inject(ChatStreamClient);
      } catch {
        // Mock injection for standalone unit tests
      }
    }
    this.http = h!;
    this.streamClient = s!;
  }

  // ═════════════════════════════════════════════════════════════════════
  // 1. Operational Session Management (Milestone 1 — REST Endpoints)
  // ═════════════════════════════════════════════════════════════════════

  /**
   * Retrieves operational chat sessions with previews.
   * Maps to GET /api/v1/chat/sessions?limit={limit}
   *
   * @param limit maximum number of sessions to return (1-100, default 20)
   */
  getSessions(limit = 20): Observable<SessionsResponse> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<SessionsResponse>(`${this.baseUrl}/sessions`, { params });
  }

  /**
   * Explicitly creates a new operational chat session.
   * Maps to POST /api/v1/chat/sessions
   *
   * @param initialTitle optional initial title (defaults to 'New Chat')
   */
  createSession(initialTitle = 'New Chat'): Observable<ChatSessionRecord> {
    return this.http.post<ChatSessionRecord>(`${this.baseUrl}/sessions`, { title: initialTitle });
  }

  /**
   * Renames an operational chat session title.
   * Maps to PATCH /api/v1/chat/sessions/{id}
   *
   * @param sessionId the target session ID
   * @param newTitle the updated title string (max 255 characters)
   */
  updateSessionTitle(sessionId: string, newTitle: string): Observable<SessionSummary> {
    const body: RenameSessionRequest = { title: newTitle.trim() };
    return this.http.patch<SessionSummary>(
      `${this.baseUrl}/sessions/${encodeURIComponent(sessionId)}`,
      body,
    );
  }

  /**
   * Deletes an operational chat session, its turns, events, and checkpoints.
   * Invariant: Deletes relational records only; cognitive engrams in Spector Memory are preserved.
   * Maps to DELETE /api/v1/chat/sessions/{id}
   *
   * @param sessionId the target session ID
   */
  deleteSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/sessions/${encodeURIComponent(sessionId)}`);
  }

  /**
   * Loads structured turn history for historical session replay.
   * Maps to GET /api/v1/chat/sessions/{id}/messages
   * Returns rich turns[] containing past user prompts, thinking traces, and tool execution cards.
   *
   * @param sessionId the target session ID
   */
  getSessionMessages(sessionId: string): Observable<SessionHistoryResponse> {
    return this.http.get<SessionHistoryResponse>(
      `${this.baseUrl}/sessions/${encodeURIComponent(sessionId)}/messages`,
    );
  }

  // ═════════════════════════════════════════════════════════════════════
  // 2. Real-Time Streaming Execution (Milestone 2 & 3)
  // ═════════════════════════════════════════════════════════════════════

  /**
   * Sends a chat prompt and streams typed event envelopes via POST /api/v1/chat/stream.
   *
   * @param request the chat turn parameters
   * @param options optional headers and heartbeat callback
   */
  streamMessage(request: AgentChatRequest, options?: StreamOptions): Observable<ChatStreamEvent> {
    const url = `${this.baseUrl}/stream`;
    return this.streamClient.stream(url, request, options);
  }

  /**
   * Aborts the actively running chat stream, sending client-side cancellation.
   */
  abortStream(reason = 'User stopped generation'): void {
    this.streamClient.abort(reason);
  }

  /** Returns whether a stream is currently active. */
  isStreaming(): boolean {
    return this.streamClient.isActive();
  }

  // ═════════════════════════════════════════════════════════════════════
  // 3. Configuration & Metadata
  // ═════════════════════════════════════════════════════════════════════

  /** Retrieves chat configuration parameters (GET /api/v1/chat/config). */
  getConfig(): Observable<ChatConfig> {
    return this.http.get<ChatConfig>(`${this.baseUrl}/config`);
  }

  /** Lists available LLM models (GET /api/v1/chat/models). */
  getModels(): Observable<ModelsResponse> {
    return this.http.get<ModelsResponse>(`${this.baseUrl}/models`);
  }

  /** Lists registered agent tools (GET /api/v1/chat/tools). */
  getTools(): Observable<ToolsResponse> {
    return this.http.get<ToolsResponse>(`${this.baseUrl}/tools`);
  }
}
