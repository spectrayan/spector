---
title: TypeScript SDK
description: "Install and use the Spector universal TypeScript/JavaScript SDK across Node.js, Bun, Deno, and modern browser runtimes."
---

# 🔷 TypeScript SDK (`@spectrayan/spector-client`)

> **Zero-dependency, universal TypeScript/JavaScript client for the Spector Cognitive Memory & Vector Search platform.**

`@spectrayan/spector-client` bridges low-level OpenAPI 3.1 typed schemas with an ergonomic, handwritten cognitive facade. It provides 1-to-1 parity with biological memory verbs (`remember`, `recall`, `forget`, `reinforce`, `suppress`, `resolve`, `browse`, `status`) and native Server-Sent Events (SSE) streaming.

---

## Highlights

- **Zero External Runtime Dependencies**: Built entirely with standard web APIs (`fetch`, `ReadableStream`, `TextDecoder`) — runs out-of-the-box on **Node.js 18+**, **Bun**, **Deno**, and modern browsers.
- **Strict Typing**: 100% type-safe with exported TypeScript declarations (`index.d.ts`), source maps, and tree-shakeable dual ESM/CJS packaging.
- **Authentic Cognitive Verbs**: Fluent interface modeling biological memory dynamics (Hebbian learning, Zeigarnik effect, multi-tier decay).
- **Real-Time Streaming**: Stream live cognitive consolidation pulses and recall telemetry using async iterables (`for await`).
- **OpenAPI Escape Hatch**: Direct access to low-level typed API endpoints via `client.raw`.

---

## Installation

=== "npm"
    ```bash
    npm install @spectrayan/spector-client
    ```

=== "pnpm"
    ```bash
    pnpm add @spectrayan/spector-client
    ```

=== "yarn"
    ```bash
    yarn add @spectrayan/spector-client
    ```

=== "bun"
    ```bash
    bun add @spectrayan/spector-client
    ```

---

## Quick Start

```typescript
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
    arousal: 0.5,
  });
  console.log('Ingested memory TSID:', record.id);

  // 3. Recall — Query using multi-tier associative cognitive scoring
  const memories = await client.memory.recall({
    query: 'formatting preferences',
    topK: 5,
    minSalience: 0.2,
  });

  for (const memory of memories) {
    console.log(`[${memory.id}] score=${memory.score.toFixed(4)} | ${memory.text}`);
  }

  // 4. Hebbian Reinforcement (Long-Term Potentiation)
  await client.memory.reinforce(record.id, 1);

  // 5. Zeigarnik Loop Closure
  await client.memory.resolve(record.id);
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

### `recall(params)`
Retrieve memories using fused cognitive scoring (similarity × importance × decay × valence):

```typescript
const memories = await client.memory.recall({
  query: 'database engine version',
  topK: 10,
  tier: MemoryTier.SEMANTIC,
  tags: ['database'],
  minSalience: 0.3,
  profile: 'BALANCED', // Or 'HYPERFOCUS', 'THE_EXECUTOR', 'DIVERGENT', 'DEBUGGING'
});
```

### `reinforce(id, strength)`
Apply Long-Term Potentiation (LTP) to strengthen synaptic associations and counteract forgetting decay:

```typescript
await client.memory.reinforce(record.id, 1);
```

### `suppress(id, reason)`
Actively inhibit recall of an obsolete or superseded memory without permanently deleting it:

```typescript
await client.memory.suppress(record.id, 'Superseded by Postgres 17 migration');
```

### `resolve(id)`
Close open task-oriented loops (Zeigarnik effect resolution):

```typescript
await client.memory.resolve(record.id);
```

### `forget(id)`
Permanently remove a memory and prune connected associative graph edges:

```typescript
await client.memory.forget(record.id);
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
console.log(`Total memories: ${stats.totalMemories}, Tier breakdown:`, stats.tierCounts);
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
    topics: ['memory', 'consolidation', 'graph'],
  });

  for await (const event of eventStream) {
    console.log(`[Event ${event.event}]`, event.data);
  }
}

watchEvents().catch(console.error);
```

---

## Error Handling

All SDK exceptions derive from `SpectorError`:

```typescript
import { SpectorError, SpectorConnectionError, SpectorApiError } from '@spectrayan/spector-client';

try {
  await client.memory.recall({ query: 'test' });
} catch (err) {
  if (err instanceof SpectorConnectionError) {
    console.error('Cannot reach Spector Synapse daemon:', err.message);
  } else if (err instanceof SpectorApiError) {
    console.error(`API Error HTTP ${err.status}:`, err.data);
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
