# ADR-0008: Cognitive Substrate Evolution (TANGLE, GPM, MSCE)

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-08 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

As Spector evolved from a vector memory store into a comprehensive cognitive architecture, agent applications required richer cognitive substrates: temporal sequence tracking, procedural habit execution, homeostatic balance, and dynamic prompt fusion. To support autonomous persona replication and digital continuity, Spector established three specialized memory components: TANGLE (Temporal Associative Network Graph), GPM (Goal & Procedural Memory), and MSCE (Multi-Stage Context Extraction).

## 2. Problem Statement

Previous cognitive retrieval presented critical structural gaps:
1. **Static prompt formatting**: Client applications had to manually fetch and stitch working memory, semantic facts, and episodic context, resulting in fragmented, redundant LLM prompts.
2. **Procedural amnesia**: The system had no representation for behavioral heuristics, interaction cadences, or tool invocation policies.
3. **Temporal blind spots**: Pure semantic search retrieved disjoint facts without awareness of chronological order or causal transitions.

## 3. Decision Drivers

- **Unified Context Synthesis**: Provide a single atomic operation to retrieve fully formatted, multi-tier context packs directly consumable by LLMs.
- **Procedural Habit Representation**: Model procedural memory (rules, heuristics, cadences) as active cognitive policies.
- **Temporal Causal Graphing**: Maintain chronological episodic sequences and causal transition links.
- **Sub-100ms Latency Budget**: End-to-end multi-tier context assembly must complete within strict real-time conversational budgets.

## 4. Considered Options

### Option 1: Client-Side Multi-Query Fusion
- **Description**: Clients make separate calls to episodic, semantic, and graph endpoints and merge prompts in client SDKs.
- **Advantages**: Keeps server-side APIs minimal.
- **Disadvantages**: Incurs multiple network roundtrips (150–400ms); client-side prompt formatting varies across languages and lacks unified scoring.

### Option 2: Standard Graph-RAG Pipeline
- **Description**: Use off-the-shelf Graph-RAG frameworks (NetworkX, external vector DBs).
- **Advantages**: Utilizes existing open-source libraries.
- **Disadvantages**: High GC pressure, high query latency (50–500ms), and inability to enforce biological cognitive constraints.

### Option 3: Unified Cognitive Substrate Architecture (TANGLE, GPM, MSCE) (Selected)
- **Description**: Implement an off-heap cognitive substrate natively inside `spector-memory` and `spector-synapse`:
  - **TANGLE**: Off-heap temporal associative network tracking chronological chains and entity co-activations.
  - **GPM**: Procedural heuristics engine representing interaction cadence and behavioral rules.
  - **MSCE**: Multi-Stage Context Extraction engine fusing working scratchpads, procedural habits, semantic axioms, and episodic anecdotes into a single coherent prompt payload.
- **Advantages**: Delivers end-to-end prompt synthesis in < 15ms; guarantees consistent cognitive formatting across all agent runtimes.
- **Disadvantages**: Requires deep integration across all memory tiers and synapse gateway layers.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Unified Cognitive Substrate Architecture).

### Positive Consequences
- Single atomic API call generates complete, deduplicated cognitive prompt contexts for LLM agents.
- Procedural memory enables agents to preserve unique behavioral reflexes and interaction cadences over time.
- TANGLE graph ensures temporal and causal continuity across multi-session interactions.

### Negative Consequences & Trade-offs
- Increased complexity in synapse gateway routing to coordinate multi-tier context extraction.
- Context pack generation requires strict token budgeting to prevent prompt overflow.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Client Fusion** | Simple server APIs | High network latency, fragmented prompt structure |
| **Option 2: External Graph-RAG** | Off-the-shelf code | Heavy GC pauses, high query latency, no procedural tier |
| **Option 3: Unified Substrate** | < 15ms latency, atomic context packs, procedural memory | Deeper internal integration complexity |

## 7. Implementation Plan

1. **Phase 1**: Implement `TangleGraphEngine` for temporal episodic chaining in `memory/spector-memory`.
2. **Phase 2**: Construct `ProceduralMemoryStore` for behavioral heuristics and interaction policies.
3. **Phase 3**: Build `MultiStageContextExtractor` in `synapse/spector-synapse` to generate unified context packs.
4. **Phase 4**: Wire context packs into MCP server tools and client SDK interfaces.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `synapse/spector-synapse`
- **Key Packages**: `com.spectrayan.spector.memory.tangle`, `com.spectrayan.spector.synapse.context`
- **Classes**: `TangleGraphEngine.java`, `ProceduralMemoryStore.java`, `MultiStageContextExtractor.java`, `ContextPack.java`
- **Verification Tests**: `TangleGraphIntegrationTest.java`, `ContextExtractionPipelineTest.java`
