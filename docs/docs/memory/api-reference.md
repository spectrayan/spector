---
title: API Reference
description: "Complete API reference for SpectorMemory, RecallOptions, CognitiveResult, and related types."
---

# 📖 API Reference

---

## SpectorMemory

The main façade for all cognitive memory operations.

### Builder

```java
SpectorMemory memory = SpectorMemoryBuilder.createEmpty()
    .dimensions(int)                        // Vector dimensionality (required)
    .embeddingProvider(EmbeddingProvider)    // Embedding provider (required)
    .workingCapacity(int)                   // Working memory slots (default: 100)
    .episodicPartitionCapacity(int)         // Records per episodic partition (default: 10,000)
    .semanticCapacity(int)                  // Semantic capacity (default: 5,000)
    .proceduralCapacity(int)                // Procedural memory slots (default: 500)
    .quantizer(ScalarQuantizer)             // Custom quantizer
    .persistenceDir(Path)                   // Episodic mmap directory
    .build();
```

### Core Methods

| Method | Return Type | Description |
|---|---|---|
| `remember(id, text, type, source, tags...)` | `CompletableFuture<Void>` | Async Remember Pathway — embeds, quantizes, stores, indexes |
| `remember(text, type, source, tags...)` | `CompletableFuture<String>` | Auto-ID Remember Pathway — generates TSID, returns the ID |
| `recall(queryText, options)` | `List<CognitiveResult>` | Recall Pathway — parallel SIMD-accelerated recall with cognitive scoring |
| `inspect(id)` | `Optional<CognitiveRecord>` | Full cognitive X-ray: text ↔ header ↔ vector |
| `browse(tags...)` | `List<CognitiveRecord>` | Tag-based browsing with AND semantics |
| `exportJson()` | `String` | Bulk JSON export of all live memories |
| `forget(id)` | `void` | Tombstones a memory (permanent, excluded from all scans) |
| `suppress(id, reason)` | `void` | Suppresses from recall results (reversible) |
| `unsuppress(id)` | `void` | Removes suppression |
| `whyNot(memoryId, query, options)` | `WhyNotExplanation` | Explains why a memory was not recalled |
| `reflect()` | `ReflectReport` | Triggers sleep consolidation cycle |
| `introspect(topic)` | `MemoryInsight` | Metamemory self-analysis on a topic |
| `totalMemories()` | `int` | Total record count across all tiers |
| `close()` | `void` | Releases all off-heap memory and file handles |

---

## RecallOptions

Builder for recall query configuration.

```java
RecallOptions options = RecallOptions.builder()
    .topK(int)                              // Max results (default: 10)
    .synapticFilter(String... tags)         // Bloom filter pre-screen
    .minImportance(float)                   // Minimum importance [0.0-1.0] (default: 0.0)
    .memoryTypes(MemoryType... types)       // Tier filter (default: all)
    .minValence(byte)                       // Min emotional valence (default: -128)
    .maxValence(byte)                       // Max emotional valence (default: +127)
    .alpha(float)                           // Similarity weight (default: 0.6)
    .beta(float)                            // Importance × decay weight (default: 0.4)
    .build();
```

### Default Options

```java
RecallOptions.DEFAULT  // topK=10, no filters, alpha=0.6, beta=0.4
```

### Scoring Formula

$$\text{FinalScore} = \alpha \cdot \text{Similarity} + \beta \cdot \text{Importance} \cdot \text{Decay}$$

Where:

- **Similarity** = `1 / (1 + L2_distance)` — semantic relevance
- **Importance** = `[0.0 - 1.0]` — computed by SurpriseDetector during the Remember Pathway
- **Decay** = precomputed bucket lookup based on memory age

---

## CognitiveResult

Immutable record returned by `recall()`:

```java
public record CognitiveResult(
    String id,                // Unique memory identifier
    String text,              // Raw text content
    float score,              // Final cognitive score (after habituation)
    float importance,         // Original importance at formation
    float ageDays,            // Age in fractional days
    short recallCount,        // Times previously recalled
    byte valence,             // Emotional coloring [-128 to +127]
    MemoryType memoryType,    // Cognitive tier (WORKING/EPISODIC/SEMANTIC/PROCEDURAL)
    MemorySource source,      // Provenance (USER_STATED/OBSERVED/PROCEDURAL/...)
    String[] synapticTags,    // Decoded tag labels
    float decayFactor,        // Current temporal decay multiplier
    float ltpAdjustedDecay    // Decay after reconsolidation adjustment
) {}
```

---

## MemoryType

Enum representing the four cognitive tiers:

```java
public enum MemoryType {
    WORKING,      // Prefrontal Cortex — volatile circular buffer
    EPISODIC,     // Hippocampus — time-partitioned mmap
    SEMANTIC,     // Neocortex — permanent knowledge
    PROCEDURAL    // Basal Ganglia — learned procedures
}
```

---

## MemorySource

Provenance tracking for memory origin:

```java
public enum MemorySource {
    USER_STATED,   // Explicit user input
    OBSERVED,      // System observation (logs, events)
    INFERRED,      // AI inference
    PROCEDURAL,    // Rule or procedure
    CONSOLIDATED   // Created by sleep consolidation (ReflectDaemon)
}
```

---

## SynapticTagEncoder

128-bit inline Bloom filter encoder (`synaptic_tags_lo` and `synaptic_tags_hi`):

```java
// Encode tags into a 128-bit Bloom filter mask
long[] mask = SynapticTagEncoder.encode128("java", "debugging", "performance");

// Check if a candidate record matches (bitwise AND containment check)
boolean matches = SynapticTagEncoder.matches(recordTagsLo, recordTagsHi, mask);

// Test individual tag membership
boolean hasJava = SynapticTagEncoder.contains(recordTagsLo, recordTagsHi, "java");
```

---

## EncodingHeader

Immutable 64-byte pure encoding record header:

```java
public record EncodingHeader(
    byte version,           // Header format version (2)
    byte flags,             // Flags: tombstone, pinned, resolved, consolidated
    byte valence,           // Emotional valence (-128 to +127)
    short arousal,          // Emotional arousal (0 to 255)
    float baseImportance,   // Intrinsic importance at formation
    long timestampMs,       // Unix epoch milliseconds
    float exactNorm,        // L2 norm of original unquantized vector
    short centroidId,       // IVF partition routing centroid
    long synapticTagsLo,    // Low 64 bits of 128-bit Bloom filter
    long synapticTagsHi,    // High 64 bits of 128-bit Bloom filter
    byte consolidationFlags,// Provenance and crystallization bits
    byte encodingProfile,   // Cognitive profile active during formation
    short soulVersion,      // Persona configuration generation
    float encodingSurprise  // Bayesian surprise z-score
) {}
```

---

## ReflectReport

Summary of a sleep consolidation cycle:

```java
public record ReflectReport(
    int partitionsProcessed,
    int memoriesConsolidated,
    int semanticMemoriesCreated,
    long durationMs
) {}
```

---

## EpisodicPartition

A single time-partitioned episodic memory file:

```java
// Access partition data
int count = partition.count();
int tombstoneCount = partition.tombstoneCount();
float tombstoneRatio = partition.tombstoneRatio();
PartitionState state = partition.state();
MemorySegment segment = partition.segment();
EngramLayout layout = partition.layout();

// Lifecycle operations
partition.seal();                          // Prevent further writes
partition.setState(PartitionState.REFLECTABLE);
partition.force();                          // Flush to disk
partition.close();                          // Release resources
```

### PartitionState

```java
public enum PartitionState {
    ACTIVE,       // Accepting writes
    SEALED,       // Read-only, awaiting consolidation
    REFLECTABLE,  // Consolidation complete, eligible for pruning
    TOMBSTONED,   // High tombstone ratio, queued for compaction
    COMPACTED     // Rebuilt as dense partition
}
```

---

## CognitiveRecord

Full cognitive snapshot returned by `inspect()` and `browse()`:

```java
public record CognitiveRecord(
    String id,
    String text,
    MemoryType memoryType,
    MemorySource source,
    String[] tags,
    Instant createdAt,
    float importance,
    byte valence,
    byte arousal,
    short agentRecallCount,
    short spectorRecallCount,
    float storageStrength,
    boolean tombstoned,
    boolean consolidated,
    boolean pinned,
    boolean resolved,
    float exactNorm,
    byte[] quantizedVector
) {}
```

---

## IdStrategy

Pluggable auto-ID generation for memories:

```java
public enum IdStrategy {
    TSID,       // 13-char Crockford Base32 (default — time-sorted, distributed-safe)
    UUID,       // Standard UUID v4 (36 chars)
    SEQUENCE    // Monotonic counter (fastest, single-node only)
}
```

Configure via Builder:

```java
SpectorMemory memory = SpectorMemory.builder()
    .idStrategy(IdStrategy.TSID)    // use built-in strategy
    .idGenerator(myCustomGen)       // or provide custom MemoryIdGenerator
    .build();

// Auto-ID Remember Pathway — returns the generated ID
String id = memory.remember("User prefers dark mode",
    MemoryType.SEMANTIC, MemorySource.USER_STATED, "preferences").join();
// id = "0HJGQK4N00000"
```

---

## Next Steps

- :material-rocket: [**Getting Started**](getting-started.md) — set up in 5 minutes
- :material-brain: [**Architecture**](architecture.md) — how it all fits together
- :material-speedometer: [**Performance**](performance.md) — benchmark results
- :material-language-python: [**Python SDK**](../sdk-usage/python-sdk.md) — Python client
