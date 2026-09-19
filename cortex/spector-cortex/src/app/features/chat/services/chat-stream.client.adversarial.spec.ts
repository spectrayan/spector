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
import { ChatStreamClient, ChatStreamHttpError, ChatStreamNetworkError } from './chat-stream.client';
import { ChatStreamEvent, ErrorStreamEvent, TokenStreamEvent } from '../models/chat-turn.model';
import { reduceChatEvents, createInitialTurn } from '../reducers/chat-turn.reducer';

describe('ChatStreamClient — Adversarial Empirical Stress Suite', () => {
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

  // ──────────────────────────────────────────────────────────────────────────
  // Adversarial Stream Generators & Test Harnesses
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Partitions the entire string into 1-byte Uint8Array chunks.
   * Every single UTF-8 byte is delivered in an individual read() iteration.
   */
  function createByteByByteStream(text: string): ReadableStream<Uint8Array> {
    const fullBytes = new TextEncoder().encode(text);
    return new ReadableStream<Uint8Array>({
      start(controller) {
        for (let i = 0; i < fullBytes.length; i++) {
          controller.enqueue(new Uint8Array([fullBytes[i]]));
        }
        controller.close();
      },
    });
  }

  /**
   * Partitions bytes into arbitrary variable-sized slices (e.g. 1 to 4 bytes).
   */
  function createSlicedStream(bytes: Uint8Array, sliceSizes: number[]): ReadableStream<Uint8Array> {
    return new ReadableStream<Uint8Array>({
      start(controller) {
        let offset = 0;
        let sizeIndex = 0;
        while (offset < bytes.length) {
          const size = sliceSizes[sizeIndex % sliceSizes.length];
          const end = Math.min(offset + size, bytes.length);
          controller.enqueue(bytes.slice(offset, end));
          offset = end;
          sizeIndex++;
        }
        controller.close();
      },
    });
  }

  /**
   * Creates an active stream that enqueues chunks with controllable delays
   * to allow testing race conditions and mid-stream client abort.
   */
  function createDelayedStream(
    chunks: Uint8Array[],
    intervalMs: number,
  ): {
    stream: ReadableStream<Uint8Array>;
    cancelSpy: ReturnType<typeof vi.fn>;
  } {
    const cancelSpy = vi.fn();
    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        for (const chunk of chunks) {
          await new Promise((r) => setTimeout(r, intervalMs));
          try {
            controller.enqueue(chunk);
          } catch {
            // Stream might be closed/cancelled
            break;
          }
        }
        try {
          controller.close();
        } catch {
          // Ignore
        }
      },
      cancel(reason) {
        cancelSpy(reason);
      },
    });
    return { stream, cancelSpy };
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 1. Pathological Chunk Boundaries & Multi-Byte UTF-8 Splitting
  // ──────────────────────────────────────────────────────────────────────────

  describe('Pathological Chunk Boundaries', () => {
    it('handles 1-byte chunk stream splitting 4-byte and 3-byte UTF-8 emojis (🧠, 🚀, ⚡)', async () => {
      // 🧠 = F0 9F A7 A0 (4 bytes), 🚀 = F0 9F 9A 80 (4 bytes), ⚡ = E2 9A A1 (3 bytes)
      const payload =
        'event: session\nid: turn-1:0\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000,"isNew":true}\n\n' +
        'event: thinking\nid: turn-1:1\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1010,"text":"Analyzing brain 🧠 and rocket 🚀 dynamics with high energy ⚡...","elapsedMs":50}\n\n' +
        'event: token\nid: turn-1:2\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1020,"text":"Synthesized: 🧠⚡🚀 deep thoughts"}\n\n' +
        'event: done\nid: turn-1:3\ndata: {"sessionId":"s1","turnId":"t1","seq":3,"tsEpochMs":1030,"latencyMs":30,"primedMemories":1}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(payload),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'emoji byte-by-byte' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(4);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('thinking');
      expect((events[1] as any).text).toBe(
        'Analyzing brain 🧠 and rocket 🚀 dynamics with high energy ⚡...',
      );
      expect(events[2].type).toBe('token');
      expect((events[2] as any).text).toBe('Synthesized: 🧠⚡🚀 deep thoughts');
      expect(events[3].type).toBe('done');

      // Verify no unicode replacement characters (\uFFFD) leaked
      const fullText = JSON.stringify(events);
      expect(fullText).not.toContain('\uFFFD');
    });

    it('reassembles emojis split across adversarial 1-2-1 byte cut points', async () => {
      // Specifically split 🧠 (F0 9F A7 A0) as [F0], [9F, A7], [A0]
      const prefix = new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"');
      const b1 = new Uint8Array([0xf0]);
      const b2 = new Uint8Array([0x9f, 0xa7]);
      const b3 = new Uint8Array([0xa0]);
      const suffix = new TextEncoder().encode('"}\n\n');

      const customStream = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(prefix);
          controller.enqueue(b1);
          controller.enqueue(b2);
          controller.enqueue(b3);
          controller.enqueue(suffix);
          controller.close();
        },
      });

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: customStream,
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'split-emoji' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(1);
      expect(events[0].type).toBe('token');
      expect((events[0] as any).text).toBe('🧠');
    });

    it('parses correctly when event: and data: headers are sliced mid-word', async () => {
      // Chunks split through 'event:', 'token', 'data:', and JSON body
      const chunks = [
        new TextEncoder().encode('eve'),
        new TextEncoder().encode('nt: to'),
        new TextEncoder().encode('ken\n'),
        new TextEncoder().encode('da'),
        new TextEncoder().encode('ta: {"sessionId":"s1","turnId":"t1"'),
        new TextEncoder().encode(',"seq":1,"tsEpochMs":1000,"text":"Header split test"}\n\n'),
      ];

      const splitStream = new ReadableStream<Uint8Array>({
        start(controller) {
          for (const c of chunks) controller.enqueue(c);
          controller.close();
        },
      });

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: splitStream,
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'split-header' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(1);
      expect(events[0].type).toBe('token');
      expect((events[0] as any).text).toBe('Header split test');
    });

    it('handles split \\n\\n event boundaries where first \\n is in chunk N and second \\n is in chunk N+1', async () => {
      const chunk1 = new TextEncoder().encode(
        'event: session\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000}\n',
      );
      // chunk 2 only provides the second \n to complete the event boundary
      const chunk2 = new TextEncoder().encode(
        '\nevent: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1010,"text":"Second"}\n\n',
      );

      const customStream = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(chunk1);
          controller.enqueue(chunk2);
          controller.close();
        },
      });

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: customStream,
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'boundary-split' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(2);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('token');
      expect((events[1] as any).text).toBe('Second');
    });

    it('processes trailing event buffer when stream terminates without trailing \\n\\n', async () => {
      // Notice: ends with no trailing newlines after data: {...}
      const raw =
        'event: session\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000}\n\n' +
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1010,"text":"EOF without newline"}';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(raw),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'trailing-eof' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(2);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('token');
      expect((events[1] as any).text).toBe('EOF without newline');
    });

    it('processes trailing event when stream terminates with a single trailing \\n (EOF after line)', async () => {
      const raw =
        'event: session\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000}\n\n' +
        'event: error\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1010,"code":"SPE-700-001","message":"fatal"}\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(raw),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'single-newline-eof' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(2);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('error');
      expect((events[1] as any).message).toBe('fatal');
    });

    it('normalizes Windows CRLF (\\r\\n) split across chunks without emitting spurious events', async () => {
      const part1 = new TextEncoder().encode(
        'event: token\r\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"Line 1"}\r',
      );
      // \n arrives in next chunk, completing the \r\n
      const part2 = new TextEncoder().encode(
        '\n\r\nevent: token\r\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1010,"text":"Line 2"}\r\n\r\n',
      );

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
        client.stream('/api/v1/chat/stream', { message: 'crlf' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(2);
      expect((events[0] as any).text).toBe('Line 1');
      expect((events[1] as any).text).toBe('Line 2');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 2. Leading Whitespace Preservation in Tokens
  // ──────────────────────────────────────────────────────────────────────────

  describe('Leading Whitespace Preservation', () => {
    it('preserves code indentation and distinguishes "data:   indent" from "data: normal"', async () => {
      // In W3C SSE, "data:   val" strips exactly ONE leading space, leaving "  val"
      // In JSON, leading spaces before { are valid whitespace and preserved by JSON.parse
      // And "text" with leading indentation spaces must remain strictly intact
      const ssePayload = [
        'event: token\n',
        // 3 spaces after colon: 1 stripped by SSE framing, 2 retained in JSON
        'data:   {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"  def indented():"}\n\n',
        'event: token\n',
        // 1 space after colon: 1 stripped by SSE framing
        'data: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1010,"text":"normal"}\n\n',
        'event: token\n',
        // 4 spaces indentation in token text
        'data: {"sessionId":"s1","turnId":"t1","seq":3,"tsEpochMs":1020,"text":"    return 42"}\n\n',
      ].join('');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(ssePayload),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'whitespace' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(3);
      expect((events[0] as any).text).toBe('  def indented():');
      expect((events[1] as any).text).toBe('normal');
      expect((events[2] as any).text).toBe('    return 42');
    });

    it('correctly handles "data:" with zero space after colon per W3C SSE spec', async () => {
      // SSE spec: "If value starts with a U+0020 SPACE character, remove the first U+0020 SPACE character"
      // When no space exists after colon, no character is removed
      const ssePayload =
        'event: token\n' +
        'data:{"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"compact no space"}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(ssePayload),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'no-space' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(1);
      expect((events[0] as any).text).toBe('compact no space');
    });

    it('preserves multi-line data: lines with nested code block indentation', async () => {
      // In W3C SSE, multi-line data: entries are joined with \n
      const multilineSse =
        'event: token\n' +
        'data: {\n' +
        'data:   "sessionId": "s1",\n' +
        'data:   "turnId": "t1",\n' +
        'data:   "seq": 1,\n' +
        'data:   "tsEpochMs": 1000,\n' +
        'data:   "text": "line1\\n    line2_indented"\n' +
        'data: }\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(multilineSse),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'multiline-data' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(1);
      expect((events[0] as any).text).toBe('line1\n    line2_indented');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 3. Comment Line Filtering (:keepalive)
  // ──────────────────────────────────────────────────────────────────────────

  describe('Comment Line Filtering & Heartbeats', () => {
    it('filters out :keepalive\\n\\n interspersed every 15s emitting zero client events', async () => {
      const sseWithHeartbeats =
        ':keepalive\n\n' +
        ':keepalive\n\n' +
        'event: session\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000}\n\n' +
        ':keepalive\n\n' +
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1015,"text":"After 15s heartbeat"}\n\n' +
        ':keepalive\n\n' +
        ':keepalive\n\n' +
        'event: done\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1030}\n\n' +
        ':keepalive\n\n';

      const onKeepalive = vi.fn();
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(sseWithHeartbeats),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'heartbeats' }, { onKeepalive }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      // Exactly 3 real events emitted
      expect(events.length).toBe(3);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('token');
      expect(events[2].type).toBe('done');

      // 6 keepalive comments triggered onKeepalive callback
      expect(onKeepalive).toHaveBeenCalledTimes(6);
    });

    it('ignores arbitrary server comments (: ping, : comment with text) without emitting events', async () => {
      const sseWithArbitraryComments =
        ':\n\n' +
        ': ping\n\n' +
        ': this is an arbitrary server diagnostic comment\n\n' +
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"clean"}\n\n' +
        ': trailing comment without event\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(sseWithArbitraryComments),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'comments' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(1);
      expect((events[0] as any).text).toBe('clean');
    });

    it('does not corrupt in-flight event state when comment line is embedded between headers', async () => {
      const embeddedComment =
        'event: token\n' +
        ':keepalive\n' +
        'id: turn-1:1\n' +
        ':another comment\n' +
        'data: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"embedded keepalive survived"}\n\n';

      const onKeepalive = vi.fn();
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(embeddedComment),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'embedded' }, { onKeepalive }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(onKeepalive).toHaveBeenCalledTimes(1);
      expect(events.length).toBe(1);
      expect(events[0].type).toBe('token');
      expect((events[0] as any).text).toBe('embedded keepalive survived');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 4. Client Abort Cancellation
  // ──────────────────────────────────────────────────────────────────────────

  describe('Client Abort Cancellation', () => {
    it('calling abort() immediately halts stream and stops further event emission', async () => {
      let abortSignalCaptured: AbortSignal | undefined;
      const chunks = [
        new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"first"}\n\n'),
        new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1010,"text":"second"}\n\n'),
        new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":3,"tsEpochMs":1020,"text":"third"}\n\n'),
      ];

      const { stream, cancelSpy } = createDelayedStream(chunks, 20);

      globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
        abortSignalCaptured = init?.signal;
        return Promise.resolve({
          ok: true,
          body: stream,
        } as unknown as Response);
      });

      const events: ChatStreamEvent[] = [];
      let completed = false;
      let caughtError: unknown = null;

      client.stream('/api/v1/chat/stream', { message: 'abort-test' }).subscribe({
        next: (ev) => {
          events.push(ev);
          if (events.length === 1) {
            // Abort immediately after receiving first event
            client.abort('User stopped generation');
          }
        },
        error: (err) => {
          caughtError = err;
        },
        complete: () => {
          completed = true;
        },
      });

      // Wait for stream delays to finish
      await new Promise((r) => setTimeout(r, 100));

      expect(events.length).toBe(1);
      expect((events[0] as any).text).toBe('first');
      expect(client.isActive()).toBe(false);
      expect(abortSignalCaptured?.aborted).toBe(true);
      expect(caughtError).toBeNull();
      expect(completed).toBe(true);
    });

    it('unsubscribing an active RxJS subscription cleanly cancels the underlying reader', async () => {
      let abortSignalCaptured: AbortSignal | undefined;
      const chunks = [
        new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"tok1"}\n\n'),
        new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1010,"text":"tok2"}\n\n'),
      ];

      const { stream, cancelSpy } = createDelayedStream(chunks, 30);

      globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
        abortSignalCaptured = init?.signal;
        return Promise.resolve({
          ok: true,
          body: stream,
        } as unknown as Response);
      });

      const events: ChatStreamEvent[] = [];
      const sub = client.stream('/api/v1/chat/stream', { message: 'unsub' }).subscribe({
        next: (ev) => {
          events.push(ev);
          sub.unsubscribe();
        },
      });

      await new Promise((r) => setTimeout(r, 80));

      expect(events.length).toBe(1);
      expect(client.isActive()).toBe(false);
      expect(abortSignalCaptured?.aborted).toBe(true);
    });

    it('starting a new stream automatically aborts any active prior stream', async () => {
      let signal1: AbortSignal | undefined;
      let signal2: AbortSignal | undefined;

      const stream1 = createDelayedStream(
        [new TextEncoder().encode('event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"s1"}\n\n')],
        50,
      ).stream;

      const stream2 = createByteByByteStream(
        'event: token\ndata: {"sessionId":"s2","turnId":"t2","seq":1,"tsEpochMs":2000,"text":"s2"}\n\n',
      );

      globalThis.fetch = vi.fn()
        .mockImplementationOnce((_url, init) => {
          signal1 = init?.signal;
          return Promise.resolve({ ok: true, body: stream1 } as unknown as Response);
        })
        .mockImplementationOnce((_url, init) => {
          signal2 = init?.signal;
          return Promise.resolve({ ok: true, body: stream2 } as unknown as Response);
        });

      const events1: ChatStreamEvent[] = [];
      const events2: ChatStreamEvent[] = [];

      // Start stream 1
      client.stream('/api/v1/chat/stream', { message: 'stream 1' }).subscribe({
        next: (ev) => events1.push(ev),
      });

      expect(client.isActive()).toBe(true);

      // Start stream 2 immediately — should abort stream 1
      await new Promise<void>((resolve) => {
        client.stream('/api/v1/chat/stream', { message: 'stream 2' }).subscribe({
          next: (ev) => events2.push(ev),
          complete: resolve,
        });
      });

      expect(signal1?.aborted).toBe(true);
      expect(signal2?.aborted).toBe(false);
      expect(events2.length).toBe(1);
      expect((events2[0] as any).text).toBe('s2');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 5. HTTP Non-2xx Errors & Mid-Stream SSE Error Envelopes
  // ──────────────────────────────────────────────────────────────────────────

  describe('HTTP Errors and Mid-Stream Error Envelopes', () => {
    it('handles HTTP 400 Bad Request with JSON error envelope', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        json: vi.fn().mockResolvedValue({
          error: 'Validation failed',
          code: 'SPE-100-001',
          details: ['message must not be blank'],
        }),
      } as unknown as Response);

      let error: any = null;
      await new Promise<void>((resolve) => {
        client.stream('/api/v1/chat/stream', { message: '' }).subscribe({
          error: (err) => {
            error = err;
            resolve();
          },
        });
      });

      expect(error).toBeInstanceOf(ChatStreamHttpError);
      expect(error.status).toBe(400);
      expect(error.errorBody).toEqual({
        error: 'Validation failed',
        code: 'SPE-100-001',
        details: ['message must not be blank'],
      });
    });

    it('handles HTTP 401 Unauthorized with plain text body', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: vi.fn().mockRejectedValue(new Error('Not JSON')),
        text: vi.fn().mockResolvedValue('API key is invalid or revoked'),
      } as unknown as Response);

      let error: any = null;
      await new Promise<void>((resolve) => {
        client.stream('/api/v1/chat/stream', { message: 'hi' }).subscribe({
          error: (err) => {
            error = err;
            resolve();
          },
        });
      });

      expect(error).toBeInstanceOf(ChatStreamHttpError);
      expect(error.status).toBe(401);
      expect(error.errorBody).toBe('API key is invalid or revoked');
    });

    it('handles HTTP 500 Internal Server Error with HTML error page', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: vi.fn().mockRejectedValue(new Error('Not JSON')),
        text: vi.fn().mockResolvedValue('<html><body>500 Internal Server Error</body></html>'),
      } as unknown as Response);

      let error: any = null;
      await new Promise<void>((resolve) => {
        client.stream('/api/v1/chat/stream', { message: 'hi' }).subscribe({
          error: (err) => {
            error = err;
            resolve();
          },
        });
      });

      expect(error).toBeInstanceOf(ChatStreamHttpError);
      expect(error.status).toBe(500);
      expect(error.errorBody).toContain('500 Internal Server Error');
    });

    it('handles HTTP 503 Service Unavailable / 502 Bad Gateway', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
        json: vi.fn().mockResolvedValue({ message: 'LangGraph4j engine restarting' }),
      } as unknown as Response);

      let error: any = null;
      await new Promise<void>((resolve) => {
        client.stream('/api/v1/chat/stream', { message: 'hi' }).subscribe({
          error: (err) => {
            error = err;
            resolve();
          },
        });
      });

      expect(error).toBeInstanceOf(ChatStreamHttpError);
      expect(error.status).toBe(503);
    });

    it('processes mid-stream SSE "event: error" envelopes and verifies integration with state reducer', async () => {
      // Stream starts normally (session -> thinking) and then encounters a mid-stream failure
      const sseWithErrorEnvelope =
        'event: session\nid: turn-1:0\ndata: {"sessionId":"s-100","turnId":"t-200","seq":0,"tsEpochMs":1000,"isNew":false}\n\n' +
        'event: thinking\nid: turn-1:1\ndata: {"sessionId":"s-100","turnId":"t-200","seq":1,"tsEpochMs":1010,"text":"Evaluating graph paths...","elapsedMs":10}\n\n' +
        'event: error\nid: turn-1:2\ndata: {"sessionId":"s-100","turnId":"t-200","seq":2,"tsEpochMs":1020,"code":"SPE-700-001","message":"Upstream LLM connection terminated unexpectedly during reasoning pass","retryable":true}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(sseWithErrorEnvelope),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'error-stream' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      expect(events.length).toBe(3);
      expect(events[0].type).toBe('session');
      expect(events[1].type).toBe('thinking');

      const errorEvent = events[2] as ErrorStreamEvent;
      expect(errorEvent.type).toBe('error');
      expect(errorEvent.code).toBe('SPE-700-001');
      expect(errorEvent.message).toBe(
        'Upstream LLM connection terminated unexpectedly during reasoning pass',
      );
      expect(errorEvent.retryable).toBe(true);

      // Verify the emitted events cleanly feed into the pure state reducer
      let turnState = createInitialTurn('s-100', 'Explain cognitive graph');
      for (const ev of events) {
        turnState = reduceChatEvents(turnState, ev);
      }

      expect(turnState.status).toBe('ERROR');
      expect(turnState.hasError).toBe(true);
      expect(turnState.errorMessage).toBe(
        'Upstream LLM connection terminated unexpectedly during reasoning pass',
      );
      expect(turnState.error).toEqual({
        code: 'SPE-700-001',
        message: 'Upstream LLM connection terminated unexpectedly during reasoning pass',
        retryable: true,
      });
      expect(turnState.isStreaming).toBe(false);
    });

    it('gracefully skips corrupted/malformed JSON in data lines without crashing the stream', async () => {
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

      const streamWithCorruption =
        'event: token\nid: turn-1:1\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"valid 1"}\n\n' +
        'event: token\nid: turn-1:2\ndata: NOT_VALID_JSON_CORRUPTED_CHUNK\n\n' +
        'event: token\nid: turn-1:3\ndata: {"sessionId":"s1","turnId":"t1","seq":3,"tsEpochMs":1020,"text":"valid 2"}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(streamWithCorruption),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      await new Promise<void>((resolve, reject) => {
        client.stream('/api/v1/chat/stream', { message: 'malformed' }).subscribe({
          next: (ev) => events.push(ev),
          error: reject,
          complete: resolve,
        });
      });

      // Valid events 1 and 2 received, corrupted event skipped
      expect(events.length).toBe(2);
      expect((events[0] as any).text).toBe('valid 1');
      expect((events[1] as any).text).toBe('valid 2');
      expect(warnSpy).toHaveBeenCalled();
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 6. AsyncGenerator streamAsync Method Verification
  // ──────────────────────────────────────────────────────────────────────────

  describe('streamAsync Generator', () => {
    it('yields events sequentially via for-await-of', async () => {
      const sse =
        'event: session\ndata: {"sessionId":"s1","turnId":"t1","seq":0,"tsEpochMs":1000}\n\n' +
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1010,"text":"async hello"}\n\n' +
        'event: done\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1020}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(sse),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      for await (const ev of client.streamAsync('/api/v1/chat/stream', { message: 'async test' })) {
        events.push(ev);
      }

      expect(events.length).toBe(3);
      expect(events[0].type).toBe('session');
      expect((events[1] as any).text).toBe('async hello');
      expect(events[2].type).toBe('done');
    });

    it('throws HTTP errors as catchable exceptions in streamAsync', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        json: vi.fn().mockResolvedValue({ error: 'Permission denied' }),
      } as unknown as Response);

      let caught: any = null;
      try {
        for await (const _ev of client.streamAsync('/api/v1/chat/stream', { message: 'forbidden' })) {
          // should not reach
        }
      } catch (err) {
        caught = err;
      }

      expect(caught).toBeInstanceOf(ChatStreamHttpError);
      expect(caught.status).toBe(403);
    });

    it('terminates cleanly when loop breaks early', async () => {
      const sse =
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":1,"tsEpochMs":1000,"text":"part1"}\n\n' +
        'event: token\ndata: {"sessionId":"s1","turnId":"t1","seq":2,"tsEpochMs":1010,"text":"part2"}\n\n';

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        body: createByteByByteStream(sse),
      } as unknown as Response);

      const events: ChatStreamEvent[] = [];
      for await (const ev of client.streamAsync('/api/v1/chat/stream', { message: 'early-break' })) {
        events.push(ev);
        break; // break early after 1st event
      }

      expect(events.length).toBe(1);
      expect(client.isActive()).toBe(false);
    });
  });
});
