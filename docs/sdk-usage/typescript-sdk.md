---
title: TypeScript SDK
description: "Install and use the Spector universal TypeScript/JavaScript SDK across Node.js, Bun, Deno, and modern browser runtimes."
---

# 🔷 TypeScript SDK (`@spectrayan/spector-client`)

> **Zero-dependency, universal TypeScript/JavaScript client for the Spector Cognitive Memory & Vector Search platform.**

`@spectrayan/spector-client` bridges low-level OpenAPI 3.1 typed schemas with an ergonomic, handwritten cognitive facade. It provides 1-to-1 parity with formal memory recall algebra verbs (`remember`, `recall`, `forget`, `reinforce`, `suppress`, `resolve`, `browse`, `status`) and native Server-Sent Events (SSE) streaming.

---

## Highlights

- **Zero External Runtime Dependencies**: Built entirely with standard web APIs (`fetch`, `ReadableStream`, `TextDecoder`) — runs out-of-the-box on **Node.js 18+**, **Bun**, **Deno**, and modern browsers.
- **Strict Typing**: 100% type-safe with exported TypeScript declarations (`index.d.ts`), source maps, and tree-shakeable dual ESM/CJS packaging.
- **Authentic Cognitive Verbs**: Fluent interface implementing closed memory algebra dynamics (Hebbian association, Zeigarnik effect, multi-tier decay).
- **Real-Time Streaming**: Stream live cognitive consolidation pulses and recall telemetry using async iterables (`for await`).
- **OpenAPI Escape Hatch**: Direct access to low-level typed API endpoints via `client.raw`.

---

## Installation

=== "npm"
    ```bash title="Terminal"
    npm install @spectrayan/spector-client
    ```

=== "pnpm"
    ```bash title="Terminal"
    pnpm add @spectrayan/spector-client
    ```

=== "yarn"
    ```bash title="Terminal"
    yarn add @spectrayan/spector-client
    ```

=== "bun"
    ```bash title="Terminal"
    bun add @spectrayan/spector-client
    ```

---

## Quick Start

```typescript title="spector_agent.ts" hl_lines="16-24 28-31"
import { SpectorClient, MemoryTier } from '@spectrayan/spector-client';

// 1. Initialize client using the fluent builder
const client = SpectorClient.builder()
  .withRest({
    baseUrl: 'http://localhost:7070', // Spector Synapse daemon endpoint
    apiKey: process.env.SPECTOR_API_KEY, // Optional authentication
  })
  .build();

// Or use the static shortcut:
// const client = SpectorClient.createDefault('http://localhost:7070');

async function main() {
  // 2. Remember — Store a memory with cognitive tier and emotional attributes
  const record = await client.memory.remember({
    text: 'User prefers concise responses with bullet points and TypeScript code snippets',
    tier: MemoryTier.SEMANTIC,
    tags: ['preferences', 'formatting'],
    interest: 0.9,
    valence: 1,
    arousal: 50,
  });
  console.log('Ingestion accepted:', record.status ?? 'ACCEPTED');

  // 3. Recall — Query using multi-tier associative cognitive scoring
  const memories = await client.memory.recall('formatting preferences', {
    topK: 5,
    minSalience: 0.2,
  });

  for (const memory of memories) {
    console.log(`[${memory.id}] score=${memory.score.toFixed(4)} | ${memory.text}`);
  }

  // 4. Store synchronously when immediate ID is required
  const engram = await client.memory.store(
    'User prefers dark mode and high-contrast theme',
    ['preferences', 'ui']
  );
  console.log('Stored engram ID:', engram.id);

  // 5. Hebbian Reinforcement (Long-Term Potentiation)
  await client.memory.reinforce(engram.id, 1);

  // 6. Zeigarnik Loop Closure
  await client.memory.resolve(engram.id);
}

main().catch(console.error);
```

---

## Cognitive Memory Operations

The `client.memory` facade exposes the authentic cognitive vocabulary:

### `remember(params)`
Store a memory into one of the 4 cognitive tiers (`WORKING`, `EPISODIC`, `SEMANTIC`, `PROCEDURAL`):

```typescript
const record = await client.memory.remember({
  text: 'The production database runs PostgreSQL 17 on AWS Aurora',
  tier: MemoryTier.SEMANTIC,
  tags: ['database', 'infrastructure', 'aws'],
  interest: 0.8,     // ICNU interest weighting (0.0 to 1.0)
  challenge: 0.4,    // ICNU challenge factor
  urgency: 0.2,      // ICNU urgency factor
  valence: 0,        // Emotional valence (-128 to 127)
  arousal: 64,       // Emotional intensity (0 to 255)
});
```

### `recall(query, options)`
Retrieve memories using fused cognitive scoring (similarity × importance × decay × valence):

```typescript
const memories = await client.memory.recall('database engine version', {
  topK: 10,
  tags: ['database'],
  minSalience: 0.3,
  profile: 'BALANCED', // Or 'HYPERFOCUS', 'THE_EXECUTOR', 'DIVERGENT', 'DEBUGGING'
});
```

### `reinforce(id, valence)`
Apply Long-Term Potentiation (LTP) to strengthen synaptic associations and counteract forgetting decay:

```typescript
await client.memory.reinforce(engram.id, 1);
```

### `suppress(id, reason)`
Actively inhibit recall of an obsolete or superseded memory without permanently deleting it:

```typescript
await client.memory.suppress(engram.id, 'Superseded by Postgres 17 migration');
```

### `resolve(id)`
Close open task-oriented loops (Zeigarnik effect resolution):

```typescript
await client.memory.resolve(engram.id);
```

### `forget(id)`
Permanently remove a memory and prune connected associative graph edges:

```typescript
await client.memory.forget(engram.id);
```

### `browse(tags)`
Instant tag index filtering using inverted index lookups:

```typescript
const tagged = await client.memory.browse(['database', 'infrastructure']);
```

### `status()`
Fetch node cognitive statistics, memory tier distribution, and active cache counts:

```typescript
const stats = await client.memory.status();
console.log(`Total memories: ${stats.totalMemories}, Semantic: ${stats.semanticCount}, Working: ${stats.workingCount}`);
```

---

## Real-Time SSE Event Streaming

Spector broadcasts live memory lifecycle, Hebbian graph updates, and consolidation pulses over Server-Sent Events (`/api/v1/events`).

Consume them natively with `for await`:

```typescript
import { SpectorClient } from '@spectrayan/spector-client';

const client = SpectorClient.createDefault('http://localhost:7070');

async function watchEvents() {
  console.log('📡 Subscribing to Spector memory stream...');
  const eventStream = client.events.stream({
    filter: ['memory', 'consolidation', 'graph'],
  });

  for await (const event of eventStream) {
    console.log(`[Event ${event.event}]`, event.data);
  }
}

watchEvents().catch(console.error);
```

---

## Error Handling

All SDK exceptions derive from `SpectorClientError`:

```typescript
import {
  SpectorClientError,
  TransportError,
  MemoryNotFoundError,
  SpectorAuthError,
  SpectorServerError,
} from '@spectrayan/spector-client';

try {
  await client.memory.recall('test');
} catch (err) {
  if (err instanceof TransportError) {
    console.error('Cannot reach Spector Synapse daemon:', err.message);
  } else if (err instanceof MemoryNotFoundError) {
    console.error(`Memory ${err.memoryId} not found`);
  } else if (err instanceof SpectorClientError) {
    console.error(`Client Error [${err.statusCode}]:`, err.message);
  } else {
    console.error('Unexpected error:', err);
  }
}
```

---

## Universal Runtime Compatibility

`@spectrayan/spector-client` uses only web standard primitives:

| Runtime | Supported Version | Notes |
|:---|:---:|:---|
| **Node.js** | ≥ 18.0.0 | Full ESM and CommonJS support |
| **Bun** | ≥ 1.0.0 | Native fetch and SSE support |
| **Deno** | ≥ 1.30.0 | Supported via npm specifier |
| **Browser / Edge** | Modern Chrome, Firefox, Safari, Edge | Full fetch support; CORS must be configured on Synapse |
| **Cloudflare Workers** | Supported | Native fetch and streaming response compatible |
