/*
 * Copyright 2026 Spectrayan — Apache 2.0
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { SpectorClient, MemoryTier, RestTransport } from '../dist/index.js';

describe('SpectorClient Builder', () => {
  it('should initialize default client', () => {
    const client = SpectorClient.createDefault('http://localhost:7070');
    assert.ok(client.memory);
    assert.ok(client.events);
    assert.ok(client.raw);
    assert.ok(client.transport instanceof RestTransport);
  });

  it('should configure custom authentication and headers', () => {
    const client = SpectorClient.builder()
      .withRest({
        baseUrl: 'https://spector.example.com',
        apiKey: 'secret-api-key',
        bearerToken: 'jwt-token',
        userId: 'agent-1',
        agentId: 'spector-core',
        namespace: 'workspace-a',
      })
      .build();

    assert.ok(client);
    assert.equal(MemoryTier.SEMANTIC, 'SEMANTIC');
  });
});
