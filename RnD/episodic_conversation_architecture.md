# ADR-0006: Episodic Conversation Architecture

| Field | Value |
|---|---|
| **Status** | Approved |
| **Authors** | @jarvis (CTO), @titan (Architect), Bharat (CEO) |
| **Date** | August 13, 2026 |
| **GitHub Issue** | [#518](https://github.com/spectrayan/spector/issues/518) |

---

## Context & Problem Statement

Chat turns in `SpectorChatMemoryRepository` are currently stored through the full `CognitiveIngestionTarget` pipeline into the **SEMANTIC** tier. Every user message and assistant response undergoes embedding generation, quantization, HNSW insertion, BM25 tokenization, SPLADE sparse vector generation, Hebbian weight adjustment, and entity extraction.

This architecture produces four critical problems:

1. **Embedding latency**: 30–80ms per turn for embedding generation and quantization — unacceptable for interactive chat where append should be sub-millisecond.

2. **HNSW pollution**: Chat turn embeddings inflate the proximity graph with low-value nodes (e.g., "thanks, that works!"), degrading traversal time and recall quality for all nearest-neighbor queries.

3. **BM25/SPLADE noise**: Every chat turn is tokenized into `text.dat` and indexed in BM25 posting lists and SPLADE sparse vectors. Conversational filler competes with genuine knowledge during sparse retrieval.

4. **SpectorChatMemoryRepository recall bug**: The current implementation embeds the literal session ID string (e.g., `"sess_a1b2c3d4"`) and performs vector similarity search. This produces semantically meaningless results — recall "works" only by accident when the graph is small.

---

## Decision Drivers

- **Sub-millisecond write latency**: Chat append must complete in < 1ms to maintain interactive responsiveness.
- **No external database dependency**: Adding H2, SQLite, or any JDBC-based store defeats Spector's single-binary, zero-dependency deployment model.
- **Neuroscience alignment**: The memory type taxonomy should reflect cognitive science — episodic memory stores personal experiences in temporal sequence.
- **Pagination & replay**: Must support session-based pagination, tail-N retrieval for LLM context assembly, and full session replay.
- **Clean retrieval indexes**: Chat turns must not pollute BM25, SPLADE, or HNSW indexes used for knowledge retrieval.

---

## Considered Options

### Option A: EPISODIC Tier + Lightweight Ingestion ✅ CHOSEN

Repurpose `MemoryType.EPISODIC` as a log-structured conversation store. The existing 64B cache-line-aligned header is preserved with fields reinterpreted for episodic use. Variable-length CBOR message bodies are stored inline in the EPISODIC region immediately following each header. An `EpisodicSessionIndex` backed by `ConcurrentHashMap<Long, List<Long>>` provides O(1) session lookup and pagination.

**Pros**:
- Zero structural changes to `MemoryType` enum or its 2-bit encoding
- Reuses existing mmap infrastructure (region allocation, WAL, fsync)
- Backward compatible — SEMANTIC and PROCEDURAL tiers are untouched
- Neuroscience-correct: episodic memory IS the system for personal experiences
- No BM25/SPLADE pollution by design (no text.dat usage)
- Sub-millisecond writes (mmap append, no embedding pipeline)

**Cons**:
- Reinterprets header fields, requiring documentation discipline
- Session index must be rebuilt on startup (sub-second for 100K records)

### Option B: New CONVERSATIONAL MemoryType ❌ Rejected

Add a fifth `MemoryType.CONVERSATIONAL` value with dedicated storage region and ingestion path.

**Rejection rationale**:
- **Encoding cascade**: `MemoryType` is encoded as 2 bits in the flags byte of every 64B header. All four values are used (`00`=SEMANTIC, `01`=EPISODIC, `10`=PROCEDURAL, `11`=WORKING). Adding a fifth type requires expanding to 3 bits, cascading changes through `MemoryHeader`, `BundleSubHeader`, `PartitionBundle`, and every `MemoryType` switch statement.
- **Semantic vacancy**: If CONVERSATIONAL stores conversations, EPISODIC loses its purpose. In cognitive neuroscience, episodic memory is defined as memory for personal experiences — conversations are the quintessential episodic memory.

### Option C: In-Process JSON Document Store ❌ Rejected

Implement a lightweight JSON document store within the Spector process for chat persistence.

**Rejection rationale**:
- Reinvents the mmap-based storage infrastructure that already exists
- Doubles the maintenance surface area (two storage engines)
- JSON is verbose for binary-compatible message content (tool calls, attachments)
- No integration with existing WAL, fsync, or region management

### Option D: External Database (H2/SQLite) ❌ Rejected

Add an embedded relational database for structured chat storage.

**Rejection rationale**:
- Breaks the single-binary deployment model (introduces JDBC driver dependency)
- Adds connection pool management inside a memory-mapped engine
- Creates a second durability model (DB transactions) alongside mmap+WAL
- Defeats Spector's core value proposition of zero-dependency memory infrastructure

---

## Decision

**Repurpose `MemoryType.EPISODIC` as a log-structured conversation store.**

Episodic records use the same 64B header structure as all memory types, with fields reinterpreted for conversation semantics. Variable-length CBOR bodies are stored inline in the EPISODIC region. A new lightweight ingestion path (`rememberEpisodic()`) bypasses the full cognitive pipeline entirely. Knowledge extraction from conversations is handled asynchronously by the circadian reflection subsystem.

---

## Architecture Details

### 64B Header Field Map (Episodic Reinterpretation)

The full 64B header layout with both cognitive (SEMANTIC/PROCEDURAL) and episodic interpretations:

| Offset | Size | Cognitive Field | Episodic Field | Notes |
|---|---|---|---|---|
| 0 | 2B | `flags` | `flags` | Shared — includes MemoryType bits (01=EPISODIC), consolidated bit 3 |
| 2 | 2B | `valence` | `role` | USER=0, ASSISTANT=1, SYSTEM=2, TOOL_CALL=3, TOOL_RESULT=4, THOUGHT=5 |
| 4 | 4B | `importance` | `sequence_id` | int32, monotonic counter per session |
| 8 | 8B | `timestamp` | `timestamp` | Shared — epoch millis |
| 16 | 8B | `record_id` | `record_id` | Shared — unique record identifier |
| 24 | 8B | `synaptic_tags` | `session_id` | 8B TSID hash (NOT Bloom filter) |
| 32 | 8B | `decay_rate` | `reserved` | Unused in episodic context |
| 40 | 8B | `access_count` | `reserved` | Unused in episodic context |
| 48 | 4B | `source_modality` | `source_modality` | Shared — bits 6-7 encode content type |
| 52 | 4B | `embedding_dim` | `reserved` | Unused (no vector payload) |
| 56 | 4B | `encoding_surprise` | `body_length` | int32, CBOR payload size in bytes |
| 60 | 4B | `checksum` | `checksum` | Shared — CRC32 of header + body |

#### Key Reinterpretations

- **`valence` → `role`** (offset 2, 2B): The emotional valence of a semantic memory becomes the conversational role. Six roles cover all current and anticipated turn types: `USER`(0), `ASSISTANT`(1), `SYSTEM`(2), `TOOL_CALL`(3), `TOOL_RESULT`(4), `THOUGHT`(5).

- **`importance` → `sequence_id`** (offset 4, 4B): The cognitive importance score becomes a monotonically increasing sequence counter per session. Enables ordering guarantees and gap detection.

- **`synaptic_tags` → `session_id`** (offset 24, 8B): In SEMANTIC records, this field holds a Bloom filter of tag hashes. In EPISODIC records, the same 8 bytes store a TSID-derived session identifier. The field width is identical; only the interpretation changes.

- **`encoding_surprise` → `body_length`** (offset 56, 4B): The information-theoretic surprise score becomes the byte length of the CBOR payload following the header. This enables variable-stride traversal of the EPISODIC region.

- **`source_modality` bits 6–7**: Already defined for content type discrimination:

  | Bits 6–7 | Content Type |
  |---|---|
  | `00` | TEXT |
  | `01` | IMAGE |
  | `10` | AUDIO |
  | `11` | VIDEO |

- **`consolidated` flag (bit 3 of flags)**: Marks episodic turns that have been reflected into the SEMANTIC tier by the circadian consolidation process.

### Storage Format

Episodic records use a **log-structured variable-length** format within the EPISODIC region:

```
┌──────────────────────┬──────────────────────────────────┐
│   64B Header         │   Variable-length CBOR Body      │
│                      │   (body_length bytes)            │
├──────────────────────┼──────────────────────────────────┤
│   64B Header         │   Variable-length CBOR Body      │
│                      │   (body_length bytes)            │
├──────────────────────┼──────────────────────────────────┤
│   ...                │   ...                            │
└──────────────────────┴──────────────────────────────────┘

stride = 64 + body_length (read from header field at offset 56)
```

**Key properties**:
- Each record is `[64B Header][Variable CBOR Body]` — contiguous in the EPISODIC region
- Stride is variable: `64 + body_length` (body_length read from offset 56 of the header)
- **No vector payload** — episodic records carry no embedding vectors
- **No text.dat usage** — message content is in the CBOR body, not the shared text region
- CBOR body carries message content plus optional structured fields: `tool_calls`, `thinking`, `attachments`
- CBOR provides compact binary encoding, schema flexibility, and zero-copy-friendly layout

### Session Index

```java
/**
 * In-memory index mapping session IDs to ordered byte offsets in the EPISODIC region.
 * Rebuilt on startup via sequential mmap scan.
 */
public class EpisodicSessionIndex {
    // session_id (8B TSID hash) → ordered list of byte offsets
    private final ConcurrentHashMap<Long, List<Long>> sessionIndex;
}
```

**Operations**:
- **Full session replay**: Iterate the offset list for a given session_id, read each header+body
- **Tail-N for LLM context**: Take the last N entries from the offset list
- **Cursor-based pagination**: Use offset position as cursor, slice the list
- **Session enumeration**: `keySet()` returns all known session IDs

**Startup rebuild**:
- Single sequential scan of the memory-mapped EPISODIC region
- For each record: read 64B header, extract `session_id` (offset 24) and record byte offset
- Advance position by `64 + body_length` (offset 56) to find next record
- Populate `ConcurrentHashMap` entries
- Sub-second completion for 100K records (sequential I/O on mmap'd memory)

### Ingestion Path

A new `rememberEpisodic()` method provides a lightweight ingestion path that **bypasses `CognitiveIngestionTarget` entirely**:

**Skipped stages** (compared to SEMANTIC ingestion):
- ❌ Embedding generation
- ❌ PQ/SQ quantization
- ❌ Encoding surprise calculation
- ❌ Importance scoring
- ❌ HNSW graph insertion
- ❌ BM25 tokenization
- ❌ SPLADE sparse vector generation
- ❌ Hebbian weight adjustment
- ❌ Entity extraction

**Retained stages**:
- ✅ WAL append (crash recovery)
- ✅ `MemoryIndex` registration (global record tracking)
- ✅ Session index update (`EpisodicSessionIndex.add()`)
- ✅ Circadian trigger check (reflection threshold)

### Consolidation Flow

The circadian reflection subsystem handles knowledge extraction from conversations:

```
EPISODIC (chat turns)
    │
    │  circadian trigger (after N episodic writes)
    ▼
ConversationReflector
    │
    │  reads episodic turns
    │  extracts knowledge / insights
    │  generates embeddings
    ▼
SEMANTIC (distilled knowledge)
    │
    │  full vector embedding
    │  BM25/SPLADE indexing
    │  HNSW insertion
    ▼
Available for retrieval
```

This mirrors the neuroscience model of **hippocampal consolidation**: episodic experiences (hippocampus) are replayed during rest periods and consolidated into semantic knowledge (neocortex). The circadian cycle is already implemented — the `ConversationReflector` reads episodic turns, extracts durable knowledge, and stores the results into the SEMANTIC tier with full vector embedding.

The `consolidated` flag (bit 3 of the header flags byte) marks episodic turns that have been processed by the reflector, preventing redundant consolidation.

### Bundle Layout

Updated partition bundle layout showing the EPISODIC region change:

```
┌─────────────────────────────────────┐  offset 0
│ 64B MemoryHeader (SMKM)            │
├─────────────────────────────────────┤  offset 64
│ 64B BundleSubHeader (SPTB)         │
├─────────────────────────────────────┤  offset 128
│ RegionEntry[0..3] (64B × 4)        │
├─────────────────────────────────────┤  page-aligned
│ Region 0: SEMANTIC (fixed-stride)  │  64B header + quantized vector
│ Region 1: EPISODIC (log-structured)│  64B header + variable CBOR body  ← CHANGED
│ Region 2: PROCEDURAL (fixed-stride)│  64B header + quantized vector
│ Region 3: TEXT (append-only)       │  raw UTF-8 for SEMANTIC/PROCEDURAL
└─────────────────────────────────────┘
```

**Region 1 change**: Previously fixed-stride (64B header + quantized vector, identical to SEMANTIC). Now log-structured with variable-length records (64B header + variable CBOR body). The `TEXT` region (Region 3) serves only SEMANTIC and PROCEDURAL records — EPISODIC records are self-contained.

---

## Consequences

### Positive

- **`EpisodicRecordMemory` deprecated**: The class that treated episodic records as mini-semantic memories with embeddings is removed.
- **Chat turns no longer appear in SEMANTIC recall**: Eliminates false positives from conversational noise in knowledge retrieval.
- **BM25/SPLADE indexes stay clean**: No episodic content enters `text.dat` or sparse retrieval indexes.
- **Sub-millisecond append latency**: Target < 1ms per turn (mmap write + WAL append) vs. current 30–80ms (full cognitive pipeline).
- **O(1) pagination via session index**: `EpisodicSessionIndex` provides constant-time session lookup and efficient tail-N retrieval.
- **Session index rebuild is fast**: Sub-second for 100K records via sequential mmap scan on startup.
- **Neuroscience alignment**: EPISODIC tier now correctly represents personal experiences, matching the cognitive science model.

### Negative

- **Header field overloading**: The same 64B structure carries different semantics depending on `MemoryType`. Requires documentation discipline and clear code-level abstraction.
- **Variable-stride region**: The EPISODIC region no longer supports O(1) random access by record index (must use session index offsets or sequential scan). This is acceptable because episodic access patterns are session-oriented, not index-oriented.
- **Startup cost**: Session index rebuild adds a sequential scan to startup. At ~640ns per record (64B header read), 100K records rebuild in ~64ms — negligible.

### Neutral

- **No schema migration**: Existing EPISODIC records (if any from `EpisodicRecordMemory`) are not migrated. The region is effectively re-initialized for the new format.
- **CBOR dependency**: CBOR serialization is added as a dependency. The library is small (~50KB) and has no transitive dependencies.

---

## Related

- **GitHub Issue**: [#518](https://github.com/spectrayan/spector/issues/518) — Episodic Conversation Architecture
- **Implementation Plan**: See issue description for phased implementation tasks
- **Supersedes**: None (first formalization of chat storage architecture; `EpisodicRecordMemory` was an ad-hoc implementation without an ADR)
