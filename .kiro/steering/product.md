# Product Overview

Spector is a zero-overhead, agent-ready AI cognitive memory backbone — a SIMD-accelerated semantic search engine with biologically-inspired cognitive memory built on Java 25, Panama FFM, and the Vector API.

## What It Does

- Provides AI agents with persistent cognitive memory (working → episodic → semantic → procedural tiers)
- Delivers hybrid retrieval: dense HNSW + BM25 + SPLADE + ColBERT with RRF fusion
- Achieves 0.13ms p50 recall at 1M memories with zero GC pressure (100% off-heap Panama storage)
- Exposes 16 MCP tools over stdio and Streamable HTTP for direct agent integration

## Key Design Principles

- **Biologically-inspired**: Mimics cortex tiers, synaptic gating, emotional valence, sleep consolidation
- **Zero-overhead**: No allocations in hot paths, off-heap Panama MemorySegment storage, SIMD-saturated scoring
- **MCP-native**: The MCP server runs in-process with the memory system (zero network hops, zero serialization)
- **Modular**: 22-module Maven reactor with strict layered architecture boundaries

## Licensing

- Apache 2.0 for all modules except `spector-memory`, `spector-synapse`, and `spector-cortex`
- Business Source License 1.1 for those three modules (transitions to Apache 2.0 on Change Date)
