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

import { Injectable, PLATFORM_ID, Optional, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, Subscriber } from 'rxjs';
import { ChatStreamEvent, AgentChatRequest } from '../models/chat-turn.model';

export interface StreamOptions {
  headers?: Record<string, string>;
  onKeepalive?: () => void;
}

export class ChatStreamHttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly statusText: string,
    public readonly errorBody?: unknown,
  ) {
    super(`Chat stream HTTP error: ${status} ${statusText}`);
    this.name = 'ChatStreamHttpError';
  }
}

export class ChatStreamNetworkError extends Error {
  override readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'ChatStreamNetworkError';
    this.cause = cause;
  }
}

/**
 * High-performance Server-Sent Events (SSE) streaming client using native fetch,
 * ReadableStream, and AbortController.
 *
 * Handles:
 * - Line buffering and W3C SSE event framing (`event: `, `id: `, `data: `, `:keepalive`)
 * - Multi-byte UTF-8 character reassembly via persistent TextDecoder({ stream: true })
 * - Single leading space stripping to preserve markdown whitespace
 * - Emitting typed ChatStreamEvent envelopes via RxJS Observable
 * - Client-side abort handling and cleanup
 */
@Injectable({ providedIn: 'root' })
export class ChatStreamClient {
  private readonly platformId: Object;
  private activeAbortController: AbortController | null = null;
  private activeReader: ReadableStreamDefaultReader<Uint8Array> | null = null;
  private activeObserver: Subscriber<ChatStreamEvent> | null = null;
  private isStreamActive = false;

  constructor(@Optional() platformId?: Object) {
    let pid = platformId;
    if (!pid) {
      try {
        pid = inject(PLATFORM_ID);
      } catch {
        pid = 'browser';
      }
    }
    this.platformId = pid ?? 'browser';
  }

  /** Checks if a stream is currently executing. */
  isActive(): boolean {
    return this.isStreamActive;
  }

  /**
   * Explicitly aborts the currently active stream.
   * Sends cancellation through AbortController to cleanly abort the backend virtual thread.
   */
  abort(reason = 'Client aborted'): void {
    if (this.activeAbortController) {
      this.activeAbortController.abort(reason);
      this.activeAbortController = null;
    }
    if (this.activeReader) {
      this.activeReader.cancel(reason).catch(() => {});
      this.activeReader = null;
    }
    if (this.activeObserver && !this.activeObserver.closed) {
      this.activeObserver.complete();
      this.activeObserver = null;
    }
    this.isStreamActive = false;
  }

  /**
   * Streams an agent chat turn via POST /api/v1/chat/stream returning an RxJS Observable.
   * Automatically aborts any active prior stream and wires Observable unsubscription to abort.
   */
  stream(
    url: string,
    request: AgentChatRequest,
    options: StreamOptions = {},
  ): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>((observer: Subscriber<ChatStreamEvent>) => {
      if (!isPlatformBrowser(this.platformId)) {
        observer.error(new Error('Chat streaming is only supported in browser platforms'));
        return;
      }

      // 1. Cancel previous stream if one is active on this client instance
      this.abort('New stream initiated');

      const abortController = new AbortController();
      this.activeAbortController = abortController;
      this.activeObserver = observer;
      this.isStreamActive = true;
      let streamCompleted = false;

      // 2. Resolve default authorization / API-Key headers
      let apiKey = 'spector-dev-key';
      let token: string | null = null;
      try {
        apiKey = localStorage.getItem('spector_api_key') || 'spector-dev-key';
        token = localStorage.getItem('spector_auth_token');
      } catch {
        // localStorage may be inaccessible
      }

      const defaultHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'X-API-Key': apiKey,
      };

      if (token) {
        defaultHeaders['Authorization'] = `Bearer ${token}`;
      }

      const mergedHeaders = { ...defaultHeaders, ...(options.headers ?? {}) };

      let reader: ReadableStreamDefaultReader<Uint8Array> | null = null;

      // 3. Initiate native POST fetch
      fetch(url, {
        method: 'POST',
        headers: mergedHeaders,
        body: JSON.stringify(request),
        signal: abortController.signal,
      })
        .then(async (response: Response) => {
          if (!response.ok) {
            let errorBody: unknown = null;
            try {
              errorBody = await response.json();
            } catch {
              try {
                errorBody = await response.text();
              } catch {
                // Ignore failure reading error body
              }
            }
            throw new ChatStreamHttpError(response.status, response.statusText, errorBody);
          }

          if (!response.body) {
            throw new ChatStreamNetworkError('Response body is null (streaming not supported)');
          }

          reader = response.body.getReader();
          if (abortController.signal.aborted || this.activeAbortController !== abortController) {
            reader.cancel('Stream already aborted').catch(() => {});
            return;
          }
          this.activeReader = reader;

          await this.processStream(reader, observer, abortController.signal, options.onKeepalive);
          streamCompleted = true;
          if (!abortController.signal.aborted && !observer.closed) {
            observer.complete();
          }
        })
        .catch((error: unknown) => {
          streamCompleted = true;
          // Check for intentional abort
          if (
            (error instanceof DOMException && error.name === 'AbortError') ||
            (error instanceof Error && error.name === 'AbortError')
          ) {
            // Deliberate cancellation — complete cleanly without throwing
            if (!observer.closed) {
              observer.complete();
            }
            return;
          }

          if (!observer.closed) {
            if (error instanceof ChatStreamHttpError) {
              observer.error(error);
            } else {
              observer.error(new ChatStreamNetworkError('Stream connection failure', error));
            }
          }
        })
        .finally(() => {
          if (this.activeAbortController === abortController) {
            this.activeAbortController = null;
            this.activeReader = null;
            this.activeObserver = null;
            this.isStreamActive = false;
          }
        });

      // 4. Teardown logic: when subscriber unsubscribes, abort the underlying stream
      return () => {
        if (!streamCompleted && this.activeAbortController === abortController) {
          abortController.abort('Subscription unsubscribed');
          if (reader) {
            reader.cancel('Subscription unsubscribed').catch(() => {});
          }
          if (this.activeReader === reader) {
            this.activeReader = null;
          }
          this.activeAbortController = null;
          this.activeObserver = null;
          this.isStreamActive = false;
        }
      };
    });
  }

  /**
   * Internal stream processor executing chunk reader loop, UTF-8 streaming decoding,
   * line buffering, and SSE protocol dispatching.
   */
  private async processStream(
    reader: ReadableStreamDefaultReader<Uint8Array>,
    observer: Subscriber<ChatStreamEvent>,
    signal: AbortSignal,
    onKeepalive?: () => void,
  ): Promise<void> {
    // Persistent TextDecoder maintaining multi-byte state across chunks
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    // SSE Event accumulation state
    let currentEventType = '';
    let currentId = '';
    let dataLines: string[] = [];

    const dispatchCurrentEvent = (): void => {
      if (dataLines.length === 0) {
        // Empty event boundary without data (e.g. heartbeat or ping)
        currentEventType = '';
        currentId = '';
        return;
      }

      const rawData = dataLines.join('\n');
      const rawType = currentEventType || 'token';

      try {
        const parsed = JSON.parse(rawData);

        // Normalize backward-compatibility alias (#108: 'content' -> 'token')
        const normalizedEventType =
          rawType === 'content'
            ? 'token'
            : currentEventType || parsed.type || parsed.eventType || 'token';

        const eventPayload = {
          ...parsed,
          type: normalizedEventType,
          eventType: normalizedEventType,
        } as ChatStreamEvent;

        if (!observer.closed) {
          observer.next(eventPayload);
        }
      } catch (err) {
        console.warn(
          `[ChatStreamClient] Failed to parse SSE JSON payload for event '${rawType}':`,
          rawData,
          err,
        );
      }

      // Reset state for next event
      currentEventType = '';
      currentId = '';
      dataLines = [];
    };

    while (true) {
      if (signal.aborted || observer.closed) {
        break;
      }

      const { done, value } = await reader.read();

      if (signal.aborted || observer.closed) {
        break;
      }

      if (done) {
        // Flush remaining decoder state
        buffer += decoder.decode();
        break;
      }

      if (value) {
        // stream: true ensures incomplete multi-byte UTF-8 sequences are preserved in decoder
        buffer += decoder.decode(value, { stream: true });

        // Normalize carriage returns: replace \r\n and \r with \n
        buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

        let newlineIndex: number;
        while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, newlineIndex);
          buffer = buffer.slice(newlineIndex + 1);

          // 1. Comment line (starts with ':')
          if (line.startsWith(':')) {
            if (line.includes('keepalive') && onKeepalive) {
              onKeepalive();
            }
            continue;
          }

          // 2. Empty line: event boundary
          if (line.length === 0) {
            dispatchCurrentEvent();
            continue;
          }

          // 3. Field line
          const colonIndex = line.indexOf(':');
          if (colonIndex === -1) {
            // Field with no value per SSE spec
            continue;
          }

          const field = line.slice(0, colonIndex);
          let val = line.slice(colonIndex + 1);

          // Strip at most one leading space per SSE specification
          if (val.charCodeAt(0) === 32 /* ' ' */) {
            val = val.slice(1);
          }

          switch (field) {
            case 'event':
              currentEventType = val;
              break;
            case 'id':
              currentId = val;
              break;
            case 'data':
              dataLines.push(val);
              break;
            case 'retry':
              // Optional SSE reconnection timer (ignored in request-scoped chat)
              break;
            default:
              // Unknown field per spec is ignored
              break;
          }
        }
      }
    }

    // Process any trailing line in buffer
    if (!signal.aborted && !observer.closed) {
      if (buffer.length > 0) {
        if (buffer.startsWith('data:')) {
          let val = buffer.slice(5);
          if (val.charCodeAt(0) === 32) val = val.slice(1);
          dataLines.push(val);
        }
      }
      if (dataLines.length > 0) {
        dispatchCurrentEvent();
      }
    }

  }

  /**
   * AsyncGenerator variant for async/await for-await-of consumption patterns.
   */
  async *streamAsync(
    url: string,
    request: AgentChatRequest,
    options: StreamOptions = {},
  ): AsyncIterableIterator<ChatStreamEvent> {
    const queue: (ChatStreamEvent | Error | 'DONE')[] = [];
    let resolver: (() => void) | null = null;

    const sub = this.stream(url, request, options).subscribe({
      next: (event) => {
        queue.push(event);
        if (resolver) {
          resolver();
          resolver = null;
        }
      },
      error: (err) => {
        queue.push(err);
        if (resolver) {
          resolver();
          resolver = null;
        }
      },
      complete: () => {
        queue.push('DONE');
        if (resolver) {
          resolver();
          resolver = null;
        }
      },
    });

    try {
      while (true) {
        if (queue.length === 0) {
          await new Promise<void>((resolve) => {
            resolver = resolve;
          });
        }

        const item = queue.shift();
        if (item === 'DONE') {
          break;
        }
        if (item instanceof Error) {
          throw item;
        }
        if (item) {
          yield item;
        }
      }
    } finally {
      sub.unsubscribe();
    }
  }
}
