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

import '@angular/compiler';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { ChatStreamClient, ChatStreamHttpError } from './chat-stream.client';
import { ChatStreamEvent } from '../models/chat-turn.model';

describe('ChatStreamClient', () => {
  let client: ChatStreamClient;
  let originalFetch: typeof globalThis.fetch;

  beforeEach(() => {
    client = new ChatStreamClient('browser');
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  /** Helper to create a mock ReadableStream from string chunks */
  function createMockStream(chunks: string[]): ReadableStream<Uint8Array> {
    const encoder = new TextEncoder();
    return new ReadableStream<Uint8Array>({
      start(controller) {
        for (const chunk of chunks) {
          controller.enqueue(encoder.encode(chunk));
        }
        controller.close();
      },
    });
  }

  it('correctly parses canonical SSE sequence: session -> thinking -> token -> done', async () => {
    const ssePayload = [
      'event: session\nid: turn-1:0\ndata: {"sessionId":"s-1","turnId":"t-1","seq":0,"tsEpochMs":1000,"isNew":true}\n\n',
      'event: thinking\nid: turn-1:1\ndata: {"sessionId":"s-1","turnId":"t-1","seq":1,"tsEpochMs":1100,"text":"Evaluating query...","elapsedMs":100}\n\n',
      'event: token\nid: turn-1:2\ndata: {"sessionId":"s-1","turnId":"t-1","seq":2,"tsEpochMs":1200,"text":"Hello "}\n\n',
      'event: token\nid: turn-1:3\ndata: {"sessionId":"s-1","turnId":"t-1","seq":3,"tsEpochMs":1300,"text":"world!"}\n\n',
      'event: done\nid: turn-1:4\ndata: {"sessionId":"s-1","turnId":"t-1","seq":4,"tsEpochMs":1400,"latencyMs":400,"primedMemories":2,"usage":{"inputTokens":10,"outputTokens":5,"totalTokens":15}}\n\n',
    ];

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createMockStream(ssePayload),
    } as unknown as Response);

    const events: ChatStreamEvent[] = [];
    await new Promise<void>((resolve, reject) => {
      client.stream('/api/v1/chat/stream', { message: 'hi' }).subscribe({
        next: (ev) => events.push(ev),
        error: reject,
        complete: resolve,
      });
    });

    expect(events.length).toBe(5);
    expect(events[0].type).toBe('session');
    expect(events[1].type).toBe('thinking');
    expect(events[2].type).toBe('token');
    expect((events[2] as any).text).toBe('Hello ');
    expect(events[3].type).toBe('token');
    expect((events[3] as any).text).toBe('world!');
    expect(events[4].type).toBe('done');
  });

  it('reassembles multi-byte UTF-8 emoji split across chunk boundaries', async () => {
    // 🧠 encoded in UTF-8 is [0xF0, 0x9F, 0xA7, 0xA0] (4 bytes)
    const part1 = new Uint8Array([
      ...new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"Brain: '),
      0xf0, 0x9f, // first 2 bytes of 🧠
    ]);
    const part2 = new Uint8Array([
      0xa7, 0xa0, // remaining 2 bytes of 🧠
      ...new TextEncoder().encode('"}\n\n'),
    ]);

    const customStream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(part1);
        controller.enqueue(part2);
        controller.close();
      },
    });

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: customStream,
    } as unknown as Response);

    const events: ChatStreamEvent[] = [];
    await new Promise<void>((resolve, reject) => {
      client.stream('/api/v1/chat/stream', { message: 'emoji test' }).subscribe({
        next: (ev) => events.push(ev),
        error: reject,
        complete: resolve,
      });
    });

    expect(events.length).toBe(1);
    expect(events[0].type).toBe('token');
    expect((events[0] as any).text).toBe('Brain: 🧠');
  });

  it('filters out :keepalive comments and invokes onKeepalive callback', async () => {
    const sseWithKeepalive = [
      'event: session\ndata: {"sessionId":"s-1","turnId":"t-1","seq":0,"tsEpochMs":1000,"isNew":false}\n\n',
      ':keepalive\n\n',
      'event: token\ndata: {"sessionId":"s-1","turnId":"t-1","seq":1,"tsEpochMs":1010,"text":"Resumed"}\n\n',
    ];

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createMockStream(sseWithKeepalive),
    } as unknown as Response);

    const onKeepalive = vi.fn();
    const events: ChatStreamEvent[] = [];

    await new Promise<void>((resolve, reject) => {
      client.stream('/api/v1/chat/stream', { message: 'test' }, { onKeepalive }).subscribe({
        next: (ev) => events.push(ev),
        error: reject,
        complete: resolve,
      });
    });

    expect(onKeepalive).toHaveBeenCalledTimes(1);
    expect(events.length).toBe(2);
    expect(events[0].type).toBe('session');
    expect(events[1].type).toBe('token');
  });

  it('handles client abort cleanly without emitting error', async () => {
    let abortSignalCaptured: any = null;
    globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
      abortSignalCaptured = init?.signal;
      return new Promise((_, reject) => {
        init?.signal?.addEventListener('abort', () => {
          const err = new DOMException('The user aborted a request.', 'AbortError');
          reject(err);
        });
      });
    });

    let completed = false;
    let errorReceived: unknown = null;

    const sub = client.stream('/api/v1/chat/stream', { message: 'abort test' }).subscribe({
      next: () => {},
      error: (err) => {
        errorReceived = err;
      },
      complete: () => {
        completed = true;
      },
    });

    expect(client.isActive()).toBe(true);
    client.abort('User stopped');

    // Allow promise tick
    await new Promise((r) => setTimeout(r, 10));

    expect(abortSignalCaptured?.aborted).toBe(true);
    expect(completed).toBe(true);
    expect(errorReceived).toBeNull();
    expect(client.isActive()).toBe(false);
    sub.unsubscribe();
  });

  it('propagates HTTP errors (4xx, 5xx) with status code and body', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: vi.fn().mockResolvedValue({ error: 'Ollama offline' }),
    } as unknown as Response);

    let caughtError: any = null;
    await new Promise<void>((resolve) => {
      client.stream('/api/v1/chat/stream', { message: 'error test' }).subscribe({
        next: () => {},
        error: (err) => {
          caughtError = err;
          resolve();
        },
        complete: () => resolve(),
      });
    });

    expect(caughtError).toBeInstanceOf(ChatStreamHttpError);
    expect(caughtError.status).toBe(502);
    expect(caughtError.errorBody).toEqual({ error: 'Ollama offline' });
  });

  it('normalizes legacy #108 "content" event alias to canonical "token"', async () => {
    const sseWithLegacyContent = [
      'event: content\ndata: {"sessionId":"s-1","turnId":"t-1","seq":1,"tsEpochMs":1000,"text":"Legacy content text"}\n\n',
    ];

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: createMockStream(sseWithLegacyContent),
    } as unknown as Response);

    const events: ChatStreamEvent[] = [];
    await new Promise<void>((resolve, reject) => {
      client.stream('/api/v1/chat/stream', { message: 'legacy' }).subscribe({
        next: (ev) => events.push(ev),
        error: reject,
        complete: resolve,
      });
    });

    expect(events.length).toBe(1);
    expect(events[0].type).toBe('token');
    expect((events[0] as any).text).toBe('Legacy content text');
  });
});
