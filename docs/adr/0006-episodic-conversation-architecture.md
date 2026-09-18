# ADR-0006: Episodic Conversation Architecture

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-06 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

AI agents engaged in continuous interaction require conversational memory that captures multi-turn dialogues, user intents, temporal sequences, and contextual nuance. Flat vector databases store disjoint text fragments, losing the chronological sequence and multi-speaker turn structure inherent to human conversation. Spector's episodic memory layer must model conversation as a first-class cognitive entity.

## 2. Problem Statement

Traditional naive RAG approaches suffer from three severe conversational memory failures:

1. **Loss of temporal continuity**: Retrieving individual dialogue turns via cosine similarity loses conversational context and turn order.
2. **Speaker attribution collapse**: Inability to differentiate between user inputs, agent responses, tool execution results, and environmental observations.
3. **Context window saturation**: Storing raw verbatim transcripts rapidly exceeds LLM context windows, requiring intelligent summarization and episodic clustering.

## 3. Decision Drivers

- **Turn-Preserving Temporal Chains**: Episodic records must maintain causal and chronological links to preceding and succeeding turns.
- **Multi-Role Attribution**: Clear off-heap structural representation of speaker identities (User, Agent, Tool, System).
- **Episodic Boundary Detection**: Automatic segmentation of continuous conversational streams into coherent episodic sessions.
- **Hybrid Retrieval Integration**: Fused recall combining chronological recency, emotional valence, and semantic similarity.

## 4. Considered Options

### Option 1: Document-Per-Conversation Chunking
- **Description**: Group conversations into fixed-token documents (e.g. 500 tokens) and embed each chunk.
- **Advantages**: Simple integration with conventional vector stores.
- **Disadvantages**: Splits conversational turns arbitrarily; loses fine-grained speaker attribution and temporal trajectory.

### Option 2: External Knowledge Graph Storage (Neo4j)
- **Description**: Store conversations as nodes and edges in an external graph database.
- **Advantages**: Expressive graph query capabilities (Cypher).
- **Disadvantages**: Heavy external infrastructure dependency; introduces network query hops incompatible with sub-millisecond SLAs.

### Option 3: Off-Heap Episodic Threading & Temporal Hyperedges (Selected)
- **Description**: Represent each conversational turn as an off-heap episodic engram with explicit speaker metadata, timestamp, and forward/backward pointers (`prevEpisodeId`, `nextEpisodeId`). Group conversational sessions via temporal hyperedges in `HyperEntityGraphMemory`, enabling multi-turn context expansion during recall.
- **Advantages**: Zero-GC off-heap layout, sub-millisecond sequential traversal, exact speaker attribution, and seamless integration with the cognitive scoring pipeline.
- **Disadvantages**: Requires custom off-heap indexing for session-based conversational lookups.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Off-Heap Episodic Threading & Temporal Hyperedges).

### Positive Consequences
- Retains complete conversational trajectories across hundreds of turns.
- Traversal APIs allow the LLM to reconstruct the surrounding context window of any retrieved memory turn.
- Integrates directly with `ReflectDaemon` to consolidate episodic conversation threads into semantic facts during idle sleep cycles.

### Negative Consequences & Trade-offs
- Additional metadata overhead per conversational turn (pointers, role ordinals, timestamps).
- Session boundaries must be detected using time-gap heuristics or explicit conversation delimiters.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Fixed Chunking** | Trivial implementation | Arbitrary turn splits, no speaker attribution |
| **Option 2: External Graph** | Flexible graph queries | Network latency, operational complexity |
| **Option 3: Off-Heap Threading** | Sub-ms traversal, zero GC, exact turn order | Custom session boundary detection logic |

## 7. Implementation Plan

1. **Phase 1**: Define `EpisodicTurnRecord` layout in `spector-kernel` with role flags and pointer fields.
2. **Phase 2**: Implement conversational ingestion pipelines in `spector-ingestion` that link sequential turns.
3. **Phase 3**: Add temporal context expansion to `RecallPipeline` (retrieving $K$ turns before and after high-scoring matches).
4. **Phase 4**: Implement automated conversation session boundary detection based on inactivity thresholds.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-core`
- **Key Packages**: `com.spectrayan.spector.memory.episodic`, `com.spectrayan.spector.memory.pipeline`
- **Classes**: `EpisodicMemoryStore.java`, `ConversationSessionManager.java`, `TemporalSequenceRelay.java`
- **Verification Tests**: `EpisodicConversationIntegrationTest.java`, `TemporalContextExpansionTest.java`
