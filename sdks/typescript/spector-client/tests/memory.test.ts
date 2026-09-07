/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  MemoryClient,
  MemoryTier,
  type Transport,
  type RequestOptions,
} from '../dist/index.js';

class MockTransport implements Transport {
  public lastMethod?: string;
  public lastPath?: string;
  public lastOptions?: RequestOptions;
  public cannedResponse: any = {};

  async request<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
    this.lastMethod = method;
    this.lastPath = path;
    this.lastOptions = options;
    return this.cannedResponse as T;
  }

  async *streamEvents(path: string, options?: RequestOptions): AsyncIterable<SseEvent> {
    // mock stream
  }

  close(): void {}
}

describe('MemoryClient Cognitive Verbs', () => {
  it('should format remember request with cognitive parameters', async () => {
    const transport = new MockTransport();
    transport.cannedResponse = { status: 'ACCEPTED' };
    const memory = new MemoryClient(transport);

    const res = await memory.remember({
      text: 'TypeScript SDK architecture',
      tier: MemoryTier.PROCEDURAL,
      tags: ['ts', 'sdk', 'architecture'],
      interest: 0.95,
      valence: 1,
    });

    assert.equal(transport.lastMethod, 'POST');
    assert.equal(transport.lastPath, '/api/v1/memory/remember');
    assert.deepEqual(transport.lastOptions?.body, {
      text: 'TypeScript SDK architecture',
      tier: 'PROCEDURAL',
      tags: 'ts,sdk,architecture',
      interest: 0.95,
      urgency: 0.0,
      challenge: 0.0,
      valence: 1,
      arousal: 0,
      metadata: {},
    });
    assert.equal(res.status, 'ACCEPTED');
  });

  it('should format recall query and parse result records', async () => {
    const transport = new MockTransport();
    transport.cannedResponse = [
      {
        id: 'mem-101',
        text: 'TypeScript SDK architecture',
        score: 0.98,
        tier: 'PROCEDURAL',
        tags: ['ts'],
        valence: 1,
        arousal: 0,
        importance: 0.9,
        similarity: 0.95,
        ageDays: 0.1,
        decayFactor: 0.99,
      },
    ];
    const memory = new MemoryClient(transport);

    const records = await memory.recall('architecture', { topK: 3 });
    assert.equal(transport.lastMethod, 'POST');
    assert.equal(transport.lastPath, '/api/v1/memory/recall');
    assert.equal(records.length, 1);
    assert.equal(records[0].id, 'mem-101');
    assert.equal(records[0].tier, MemoryTier.PROCEDURAL);
    assert.equal(records[0].score, 0.98);
  });

  it('should execute cognitive lifecycle verbs (forget, reinforce, resolve)', async () => {
    const transport = new MockTransport();
    const memory = new MemoryClient(transport);

    await memory.forget('mem-101', 'obsolete');
    assert.equal(transport.lastMethod, 'DELETE');
    assert.equal(transport.lastPath, '/api/v1/memory/mem-101');
    assert.deepEqual(transport.lastOptions?.body, { reason: 'obsolete' });

    await memory.reinforce('mem-101', 1);
    assert.equal(transport.lastMethod, 'POST');
    assert.equal(transport.lastPath, '/api/v1/memory/mem-101/reinforce');
    assert.deepEqual(transport.lastOptions?.body, { valence: 1 });

    await memory.resolve('mem-101');
    assert.equal(transport.lastMethod, 'POST');
    assert.equal(transport.lastPath, '/api/v1/memory/mem-101/resolve');
    assert.deepEqual(transport.lastOptions?.body, { resolved: true });
  });
});
