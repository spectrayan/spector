/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { EventClient, RestTransport, type SseEvent } from '../dist/index.js';

describe('EventClient SSE Streaming', () => {
  it('should parse Server-Sent Events stream from response body', async () => {
    const rawLines = [
      'event: memory.mutation\n',
      'data: {"id":"mem-1","action":"stored"}\n',
      'id: evt-42\n\n',
      'event: cortex\n',
      'data: {"pulse":100}\n\n',
    ];

    const stream = new ReadableStream({
      start(controller) {
        const encoder = new TextEncoder();
        for (const line of rawLines) {
          controller.enqueue(encoder.encode(line));
        }
        controller.close();
      },
    });

    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () =>
      new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      });

    try {
      const transport = new RestTransport({ baseUrl: 'http://localhost:7070' });
      const events = new EventClient(transport);

      const received: SseEvent[] = [];
      for await (const evt of events.stream({ filter: ['memory', 'cortex'] })) {
        received.push(evt);
      }

      assert.equal(received.length, 2);
      assert.equal(received[0].event, 'memory.mutation');
      assert.deepEqual(received[0].data, { id: 'mem-1', action: 'stored' });
      assert.equal(received[0].id, 'evt-42');

      assert.equal(received[1].event, 'cortex');
      assert.deepEqual(received[1].data, { pulse: 100 });
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
