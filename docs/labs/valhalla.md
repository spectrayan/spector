---
title: "Project Valhalla & Value Classes"
description: "Spector's architecture and roadmap for Project Valhalla (JEP 401) value classes, memory flattening, and JDK 27 optimizations."
---

# ⚡ Project Valhalla & Value Classes Roadmap

Spector is engineered for extreme data locality and low-latency cognitive processing. As part of our JDK 27 modernization (Issue #808), Spector establishes complete Valhalla readiness across all vector search, memory engram, GPU compute, and cluster dispatch paths.

---

## 1. Project Valhalla vs. Mainline OpenJDK 27

A key architectural distinction exists between **Mainline OpenJDK 27** and **Project Valhalla**:

| Aspect | Mainline OpenJDK 27 (GA Sept 2026) | Project Valhalla (JEP 401 EA) |
|:---|:---|:---|
| **Value Keyword** (`value record`) | Not recognized (`javac` error) | Fully supported |
| **Object Header Size** | **8 bytes** (JEP 534 Compact Headers default) | 0 bytes for flat values, 8B fallback |
| **Array Storage** | Object references (pointers) | Contiguous inlined values (flattened) |
| **Stack Allocation** | Escape analysis scalarization | Guaranteed scalarization in registers |
| **Adoption Strategy in Spector** | `@ValueCandidate` + `ValueClassValidator` | `labs/valhalla` branch with `VALHALLA_HOME` |

In mainline JDK 27, **Compact Object Headers (JEP 534)** are enabled by default, shrinking object headers from 12/16 bytes to 8 bytes across all objects without code modifications. This yields 10–20% heap savings and ~4 MB saved per million HNSW graph nodes.

When JEP 401 merges into mainline Java preview, all `@ValueCandidate` types will mechanically transition from `record` to `value record`.

---

## 2. Certified Value Class Inventory (22 Hot-Path Types)

Spector classifies value class candidates by execution frequency and memory density:

### 🔴 Critical Hot Paths (Millions of allocations/sec)

- **`ScoredResult` (`spector-index`)**: `(String id, int index, float score)` — Allocated per neighbor candidate during HNSW beam traversal (`efSearch`). Flattening saves 8–16 bytes per candidate and eliminates pointer dereferences.
- **`BatchSearchResult` (`spector-gpu`)**: `(long id, float score)` — 100% primitive (12 bytes). Flattened arrays returned directly from GPU native kernels into off-heap and heap structures.
- **`TurboCode` (`spector-core`)**: `(byte[] packedBytes, float min, float step, int originalDim)` — Generated per vector during product and scalar quantization.
- **`Hypervector` (`spector-hdc`)**: `(long[] bits, int dimension)` — Binary hyperdimensional computing representations manipulated by SIMD Vector API instructions.
- **`HebbianEdge` (`spector-kernel`)**: `(int sourceIndex, int targetIndex, float weight)` — 100% primitive (12 bytes). Spreading activation in cognitive associative memory traverses millions of these edges. Flat arrays allow 5 edges to fit in a single 64-byte L1 cache line.
- **`TemporalFact` (`spector-kernel`)**: `(MemoryId id, long timestamp, float confidence, MemorySegment payload)` — Point-in-time fact entries in the temporal cognitive memory tier.

### 🟠 High Frequency (Search, Sharding & Cognitive Memory)

- **`CognitiveResult` (`spector-memory`)**: `(String engramId, String content, float activation, float salience, long timestamp)` — Scored recall hit emitted during hybrid memory recall.
- **`WelfordAccumulator` (`spector-core`)**: `(long count, double mean, double m2)` — 100% primitive (24 bytes). Numerically stable one-pass variance tracker for vector normalization.
- **`EmaTracker` (`spector-core`)**: `(double alpha, double value, boolean initialized)` — 100% primitive (17 bytes). Exponential moving average across dimensions.
- **`WordTokenizer.Token` (`spector-commons`)**: `(String text, int startOffset, int endOffset)` — Document lexical token streams.
- **`TextChunk` (`spector-commons`)**: `(String text, int index, int startOffset, int endOffset)` — Chunk stream representation.
- **`Chunk` (`spector-commons`)**: `(String id, String text, int sequence, Map metadata)` — Boundary model for ingested documents.
- **`ContinuityRecord` (`spector-kernel`)**: `(long sequenceNumber, long wallClockTime, int eventType, MemoryId entityId)` — Timeline sequencing in the kernel.
- **`MemoryId` (`spector-kernel`)**: `(long high, long low)` — 100% primitive (16 bytes). 128-bit identity eliminating `UUID` object headers.
- **`EpisodicMemory.TurnHeaderSnapshot` (`spector-kernel`)**: `(int turnNumber, long timestamp, int speakerId, int payloadLength)` — 100% primitive (20 bytes). Conversation turn layout.
- **`TemporalFactsMemory.FactLogEntry` (`spector-kernel`)**: `(long timestamp, int factIndex, float weight)` — 100% primitive (16 bytes). Append-only temporal fact log.
- **`RoutingKey` (`spector-cluster`)**: `(String namespace, String partitionKey, int hash)` — Sharding key evaluated on every distributed query.
- **`FenceToken` (`spector-cluster`)**: `(long epoch, String leaderNodeId, long issueTimestamp)` — Distributed consensus fencing token.
- **`ResolvedRoute` (`spector-cluster`)**: `(RoutingKey key, String targetNodeId, int partitionId, boolean isReplica)` — Request routing determination.
- **`RouteBinding` (`spector-cluster`)**: `(String routePattern, String targetService, int priority, boolean active)` — Route table entry.

### 🟡 Medium Frequency (Metrics & Allocations)

- **`AllocationMetrics` (`spector-gpu`)**: `(long allocatedBytes, long poolBytes, long peakBytes, int activeCount)` — 100% primitive (28 bytes). GPU memory pool telemetry.
- **`RegionSizeSpec` (`spector-kernel`)**: `(long baseOffset, long maxBytes, int alignment)` — 100% primitive (20 bytes). Off-heap segment boundary spec.

---

## 3. Automated JEP 390 / Valhalla Validation

To guarantee seamless future migration, Spector provides `ValueClassValidator` in `spector-commons`:

```java
@Test
void testValueClassCompliance() {
    ValueClassValidator.assertValueClassCompliant(ScoredResult.class);
    ValueClassValidator.assertValueClassCompliant(BatchSearchResult.class);
}
```

Every candidate class is continuously asserted against:
- Strict finality (class and all instance fields)
- Zero synchronization / monitor locks
- Independent value-based `equals` and `hashCode`
- `@ValueCandidate` annotation presence

---

## 4. Building with Valhalla Early-Access

To experiment with true value-class bytecode generation and flattening:

```bash
# 1. Download OpenJDK Valhalla Early-Access
mkdir -p ~/jdks && tar -xzf openjdk-27-jep401ea*.tar.gz -C ~/jdks

# 2. Compile via Maven fork
git checkout labs/valhalla
VALHALLA_HOME=~/jdks/jdk-27 mvn clean compile -DskipTests
```
