/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

import { Page, Route } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const FIXTURES_DIR = path.resolve(__dirname, '../fixtures');

/**
 * Reads a fixture file as string.
 */
export function loadFixture(filename: string): string {
  const filePath = path.join(FIXTURES_DIR, filename);
  return fs.readFileSync(filePath, 'utf-8');
}

/**
 * Reads a JSON fixture file and parses it.
 */
export function loadJsonFixture<T = any>(filename: string): T {
  return JSON.parse(loadFixture(filename));
}

export interface RouteMockOptions {
  chatEnabled?: boolean;
  sessions?: any[];
  streamFixture?: string;
  streamDelayMs?: number;
  historyResponse?: any;
  models?: any[];
  config?: any;
}

/**
 * Standard route interception helper ensuring all backend API calls
 * are intercepted and deterministically answered with golden fixtures.
 */
export async function setupChatMocks(page: Page, options: RouteMockOptions = {}): Promise<void> {
  const {
    chatEnabled = true,
    sessions = [],
    streamFixture = 'thinking-then-tokens.sse',
    historyResponse = loadJsonFixture('history-replay.json'),
    models = [
      {
        id: 'qwen2.5:7b',
        name: 'Qwen 2.5 7B',
        size: 4500000000,
        modified_at: '2026-09-18T12:00:00Z',
        active: true,
      },
    ],
    config = {
      defaultModel: 'qwen2.5:7b',
      maxContextDepth: 5,
      defaultContextDepth: 3,
      agentMode: true,
      version: '0.1.0-alpha.2',
    },
  } = options;

  // 1. CRITICAL: Intercept feature flags to enable chat route
  await page.route('**/api/v1/features', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        chatEnabled,
        agentChatEnabled: true,
        agentWorkspacesEnabled: false,
      }),
    });
  });

  // 2. Intercept models and config
  await page.route('**/api/v1/chat/models', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ models }),
    });
  });

  await page.route('**/api/v1/chat/config', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(config),
    });
  });

  // 3. Intercept session listings
  await page.route('**/api/v1/chat/sessions?*', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        sessions,
        hasMore: false,
      }),
    });
  });

  await page.route('**/api/v1/chat/sessions', async (route: Route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sessions,
          hasMore: false,
        }),
      });
    } else if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '01J8Y000000000000000000000',
          title: 'New Chat',
          status: 'ACTIVE',
        }),
      });
    } else {
      await route.continue();
    }
  });

  // 4. Intercept individual session messages (historical replay)
  await page.route('**/api/v1/chat/sessions/*/messages', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(historyResponse),
    });
  });

  // 5. Intercept SSE Streaming endpoint
  await page.route('**/api/v1/chat/stream', async (route: Route) => {
    const sseBody = loadFixture(streamFixture);
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      headers: {
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      },
      body: sseBody,
    });
  });

  // 6. Intercept legacy agentChat POST endpoint for backward compatibility
  await page.route('**/api/v1/chat/agent', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        response:
          'Spector is an off-heap cognitive memory engine designed for autonomous agent architectures.',
        sessionId: '01J8Y000000000000000000000',
        isNewSession: false,
        model: 'qwen2.5:7b',
        status: 'DONE',
        latency: 420,
        durationMs: 1250,
        primedMemories: 3,
        trace: [
          {
            type: 'thinking',
            data: 'Accessing cognitive memory graph and evaluating memory salience...',
          },
        ],
        sources: ['memory://austin-move'],
      }),
    });
  });

  // 7. Intercept tools & soul queries
  await page.route('**/api/v1/agent/soul', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'spector-default',
        name: 'Spector',
        communicationStyle: 'Concise & Analytical',
        expertise: ['Cognitive Memory', 'Panama FFM', 'Vector Search'],
        emotionalBaseline: 'NEUTRAL',
        values: ['Truthfulness', 'Precision'],
        tools: ['memory_recall', 'memory_remember'],
      }),
    });
  });

  await page.route('**/api/v1/agent/tools', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        tools: [
          { name: 'memory_recall', description: 'Recall from cognitive memory' },
          { name: 'memory_remember', description: 'Store engram in cognitive memory' },
        ],
      }),
    });
  });

  await page.route('**/api/v1/chat/tools', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        tools: [
          { name: 'memory_recall', description: 'Recall from cognitive memory' },
          { name: 'memory_remember', description: 'Store engram in cognitive memory' },
        ],
      }),
    });
  });
}
