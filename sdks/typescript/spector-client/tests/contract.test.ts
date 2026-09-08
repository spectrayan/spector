/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import {
  SpectorClient,
  MemoryTier,
  MemoryNotFoundError,
  SpectorAuthError,
  SpectorValidationError,
  SpectorServerError,
} from '../dist/index.js';

describe('TypeScript SDK HTTP Contract & Dual Package', () => {
  it('should verify dual-package resolution (ESM import and CJS require)', async () => {
    // ESM import already validated via top-level import
    assert.ok(SpectorClient);

    // CJS require resolution
    const require = createRequire(import.meta.url);
    const cjsModule = require('../dist/index.cjs');
    assert.ok(cjsModule.SpectorClient);
    assert.ok(cjsModule.MemoryTier);
    assert.equal(cjsModule.MemoryTier.SEMANTIC, 'SEMANTIC');
  });

  it('should unpack wrapped recall payloads and map cognitiveScore to score', async () => {
    const originalFetch = globalThis.fetch;
    try {
      globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.includes('/api/v1/memory/recall')) {
          const body = JSON.parse(String(init?.body || '{}'));
          return new Response(
            JSON.stringify({
              results: [
                {
                  id: 'mem-99',
                  text: 'Wrapped payload memory',
                  cognitiveScore: 0.94,
                  tier: 'SEMANTIC',
                  tags: ['vector'],
                  ageDays: 2.1,
                  decayFactor: 0.97,
                },
              ],
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          );
        }
        return new Response('Not Found', { status: 404 });
      };

      const client = SpectorClient.createDefault('http://localhost:7070');
      const records = await client.memory.recall('test query', { topK: 5 });

      assert.equal(records.length, 1);
      assert.equal(records[0].id, 'mem-99');
      assert.equal(records[0].score, 0.94);
      assert.equal(records[0].ageDays, 2.1);
      assert.equal(records[0].decayFactor, 0.97);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('should map HTTP error status codes to domain error classes', async () => {
    const originalFetch = globalThis.fetch;
    try {
      const client = SpectorClient.builder()
        .withRest({ baseUrl: 'http://localhost:7070', apiKey: 'test' })
        .build();

      // Test 404 -> MemoryNotFoundError
      globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: 'Memory not found' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        });

      await assert.rejects(
        async () => client.memory.get('mem-missing'),
        (err: Error) => err instanceof MemoryNotFoundError
      );

      // Test 401 -> SpectorAuthError
      globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: 'Unauthorized' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        });

      await assert.rejects(
        async () => client.memory.get('mem-auth'),
        (err: Error) => err instanceof SpectorAuthError
      );

      // Test 400 -> SpectorValidationError
      globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: 'Invalid field' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        });

      await assert.rejects(
        async () => client.memory.store(''),
        (err: Error) => err instanceof SpectorValidationError
      );

      // Test 500 -> SpectorServerError
      globalThis.fetch = async () =>
        new Response(JSON.stringify({ message: 'Internal error' }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
        });

      await assert.rejects(
        async () => client.memory.status(),
        (err: Error) => err instanceof SpectorServerError
      );
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('should send required auth and metadata headers', async () => {
    const originalFetch = globalThis.fetch;
    let recordedHeaders: Headers | undefined;
    try {
      globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
        recordedHeaders = new Headers(init?.headers);
        return new Response(
          JSON.stringify({
            totalMemories: 10,
            workingCount: 2,
            episodicCount: 3,
            semanticCount: 5,
            proceduralCount: 0,
            tombstoneCount: 0,
            dimensions: 384,
            persistenceMode: 'ROCKSDB',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      };

      const client = SpectorClient.builder()
        .withRest({
          baseUrl: 'http://localhost:7070',
          apiKey: 'key-test',
          bearerToken: 'token-jwt',
          userId: 'user-007',
          agentId: 'agent-47',
          namespace: 'ns-mcp',
        })
        .build();

      await client.memory.status();

      assert.ok(recordedHeaders);
      assert.equal(recordedHeaders.get('X-API-Key'), 'key-test');
      assert.equal(recordedHeaders.get('Authorization'), 'Bearer token-jwt');
      assert.equal(recordedHeaders.get('X-User-Id'), 'user-007');
      assert.equal(recordedHeaders.get('X-Agent-Id'), 'agent-47');
      assert.equal(recordedHeaders.get('X-Namespace'), 'ns-mcp');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('should verify exact wire payload shapes for remember and store against OpenAPI specification', async () => {
    const originalFetch = globalThis.fetch;
    const recordedCalls: { url: string; body: any }[] = [];
    try {
      globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const body = init?.body ? JSON.parse(String(init.body)) : null;
        recordedCalls.push({ url, body });

        if (url.includes('/api/v1/memory/remember')) {
          return new Response(JSON.stringify({ status: 'ACCEPTED', message: 'Queued' }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.includes('/api/v1/memory')) {
          return new Response(JSON.stringify({ id: 'mem-101', status: 'STORED' }), {
            status: 201,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        return new Response('Not Found', { status: 404 });
      };

      const client = SpectorClient.createDefault('http://localhost:7070');

      // 1. remember verb: wire body tags must be string (comma-separated), matching RememberRequest in OpenAPI
      await client.memory.remember({
        text: 'Neural memory consolidation',
        tier: MemoryTier.EPISODIC,
        tags: ['synapse', 'hebbian'],
        interest: 0.8,
        urgency: 0.5,
        challenge: 0.2,
        valence: 10,
        arousal: 20,
      });

      const rememberCall = recordedCalls.find((c) => c.url.includes('/api/v1/memory/remember'));
      assert.ok(rememberCall);
      assert.equal(rememberCall.body.text, 'Neural memory consolidation');
      assert.equal(rememberCall.body.tier, 'EPISODIC');
      assert.equal(rememberCall.body.tags, 'synapse,hebbian');
      assert.equal(rememberCall.body.interest, 0.8);
      assert.equal(rememberCall.body.urgency, 0.5);
      assert.equal(rememberCall.body.challenge, 0.2);
      assert.equal(rememberCall.body.valence, 10);
      assert.equal(rememberCall.body.arousal, 20);

      // 2. store verb: wire body tags must be string array, matching StoreRequest in OpenAPI
      await client.memory.store('Fast synchronous note', ['quick', 'sync']);

      const storeCall = recordedCalls.find((c) => c.url.endsWith('/api/v1/memory'));
      assert.ok(storeCall);
      assert.equal(storeCall.body.text, 'Fast synchronous note');
      assert.deepEqual(storeCall.body.tags, ['quick', 'sync']);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

