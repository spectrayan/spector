# Spector TypeScript Client SDK (`@spectrayan/spector-client`)

> **Lightweight, zero-dependency universal TypeScript/JavaScript client for the Spector Cognitive Memory & Vector Search platform.**

`@spectrayan/spector-client` combines low-level OpenAPI 3.1 typed operations with an ergonomic, handwritten cognitive facade providing 1-to-1 cognitive verb parity (`remember`, `recall`, `forget`, `reinforce`, `suppress`, `resolve`, `browse`, `status`, `events`) and real-time Server-Sent Events (SSE) streaming.

## Highlights

- **Zero Runtime Dependencies**: Built entirely on standard web APIs (`fetch`, `ReadableStream`, `TextDecoder`) — works seamlessly across **Node.js 18+**, **Bun**, **Deno**, and modern browsers.
- **Universal Dual ESM & CommonJS**: Ships with complete type declarations (`index.d.ts`), source maps, and tree-shakeable dual packaging via `tsup`.
- **Cognitive Verbs Facade**: Ergonomic fluent interface modeling biologically inspired cognitive operations (Hebbian learning, Zeigarnik effect, multi-tier decay).
- **Real-Time SSE Event Streaming**: Stream cognitive consolidation pulses, recall telemetry, and mutations using native async iterables (`for await`).
- **Multi-Transport Architecture**: Ready for direct REST (default) or Model Context Protocol (MCP) JSON-RPC over HTTP/SSE.
- **Low-Level OpenAPI Escape Hatch**: Access fully typed auto-generated OpenAPI endpoints directly via `client.raw`.

---

## Installation

```bash
# Using npm
npm install @spectrayan/spector-client

# Using pnpm
pnpm add @spectrayan/spector-client

# Using yarn
yarn add @spectrayan/spector-client

# Using bun
bun add @spectrayan/spector-client
```

---

## Quick Start

```typescript
import { SpectorClient, MemoryTier } from '@spectrayan/spector-client';

// 1. Initialize client using the builder
const client = SpectorClient.builder()
  .withRest({
    baseUrl: 'http://localhost:7070', // Spector Synapse server
    apiKey: process.env.SPECTOR_API_KEY, // Optional
    bearerToken: process.env.SPECTOR_JWT, // Optional
  })
  .build();

// Or use the static shortcut:
// const client = SpectorClient.createDefault('http://localhost:7070');

async function main() {
  // 2. Remember — Store memory with cognitive tier and affective attributes
  const record = await client.memory.remember({
    text: 'User prefers concise responses with bullet points and TypeScript code snippets',
    tier: MemoryTier.SEMANTIC,
    tags: ['preferences', 'formatting'],
    interest: 0.9,
    valence: 1,
    arousal: 0.5,
  });
  console.log('Remembered:', record.id);

  // 3. Recall — Query using multi-tier associative cognitive scoring
  const memories = await client.memory.recall({
    query: 'formatting preferences',
    topK: 5,
    minSalience: 0.2,
  });

  for (const memory of memories) {
    console.log(`[${memory.id}] score=${memory.score} | ${memory.text}`);
  }

  // 4. Hebbian Reinforcement (Long-Term Potentiation)
  await client.memory.reinforce(record.id, 1);

  // 5. Zeigarnik Closure
  await client.memory.resolve(record.id);
}

main().catch(console.error);
```

---

## Real-Time SSE Event Streaming

Spector publishes real-time cognitive consolidation, mutation, and cortex events over Server-Sent Events (`/api/v1/events`).

Consume them effortlessly using async generators:

```typescript
import { SpectorClient } from '@spectrayan/spector-client';

const client = SpectorClient.createDefault('http://localhost:7070');

async function streamTelemetry() {
  console.log('📡 Listening for Spector events...');

  for await (const event of client.events.stream({ filter: ['memory', 'cortex'] })) {
    console.log(`[${event.event}] (id: ${event.id})`, event.data);
  }
}

streamTelemetry().catch(console.error);
```

---

## Direct OpenAPI Generated Client (`client.raw`)

If you need low-level access to the generated OpenAPI endpoints, schemas, and parameter models, use `client.raw`:

```typescript
// Access raw generated MemoryApi
const response = await client.raw.memoryApi.recallMemory({
  recallRequest: {
    query: 'neural plasticity',
    topK: 10,
  },
});
console.log(response.results);
```

---

## Cognitive Verbs API Reference

| Verb | Signature | Description |
|:---|:---|:---|
| **`remember()`** | `(params: RememberParams)` | Asynchronously store memory with cognitive tier and affective weights. |
| **`store()`** | `(text: string, tags?: string[])` | Lightweight memory store returning assigned ID. |
| **`recall()`** | `(query: string \| RecallParams)` | Retrieve memories via fused semantic and associative scoring. |
| **`search()`** | `(query: string, topK?: number)` | Pure dense vector semantic similarity search. |
| **`get()`** / **`find()`** | `(id: string)` | Retrieve full memory record by ID. |
| **`forget()`** | `(id: string, reason?: string)` | Soft-delete / tombstone memory. |
| **`reinforce()`** | `(id: string, valence?: number)` | Long-Term Potentiation (LTP) synaptic weight bump. |
| **`suppress()`** | `(id: string, reason?: string)` | Active inhibition / habituation. |
| **`unsuppress()`** | `(id: string)` | Remove suppression mask. |
| **`resolve()`** | `(id: string)` | Mark active goal or task as closed (Zeigarnik closure). |
| **`unresolve()`** | `(id: string)` | Reopen active task / tension. |
| **`status()`** | `()` | Memory tier counts, vector index state, and health metrics. |
| **`browse()`** | `(tags?: string[])` | Fast inverted tag index lookup. |
| **`table()`** | `(params?: TableParams)` | Paginated database records across memory tiers. |
| **`vector()`** | `(id: string)` | INT8 quantized embedding vector retrieval. |
| **`consolidate()`**| `()` | Trigger circadian sleep consolidation sweep. |
| **`vacuum()`** | `(tier?: MemoryTier)` | Trigger memory compaction and purge dead tombstones. |

---

## Development & Testing

```bash
# Install dependencies
npm install

# Build ESM, CJS, and TypeScript declaration bundles
npm run build

# Type check
npm run typecheck

# Run test suite via Node.js native test runner
npm test
```

---

## License

Apache License, Version 2.0 — see [LICENSE](../../../LICENSE) for details.
