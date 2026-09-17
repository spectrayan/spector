# ADR-0007: ReflectPathway — Biological Sleep Consolidation

| Field | Value |
|:---|:---|
| **Status** | Superseded by ADR-0074 |
| **Date** | 2026-08-07 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | ADR-0074 |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In biological cognitive systems, memory consolidation occurs during rest and sleep states. Fresh, high-detail episodic experiences stored in the hippocampus are systematically replayed, abstracted, and consolidated into the neocortex as generalized semantic concepts. Spector implements this biological principle via the asynchronous `ReflectPathway` background daemon.

## 2. Problem Statement

Without a background consolidation mechanism, cognitive memory systems encounter fundamental trade-offs:
1. **Episodic saturation**: Raw episodic logs grow indefinitely, increasing search space and memory consumption.
2. **Lack of abstraction**: The system remembers exact words spoken, but fails to extract generalized semantic knowledge, recurring patterns, and user preferences.
3. **Query latency degradation**: Performing semantic extraction and graph clustering synchronously on write paths introduces unacceptable ingestion latencies.

## 3. Decision Drivers

- **Asynchronous Decoupling**: Memory abstraction and consolidation must execute out-of-band without blocking active ingestion or recall.
- **Biological Fidelity**: Implement two-stage memory consolidation (hippocampal episodic replay -> neocortical semantic integration).
- **Graceful Resource Throttling**: Consolidation must self-throttle during periods of high query or ingestion load.
- **Reconsolidation & Synaptic Pruning**: Weaken stale or unreinforced episodic traces (forgetting) while strengthening stable semantic abstractions.

## 4. Considered Options

### Option 1: Synchronous Ingestion-Time Abstraction
- **Description**: Trigger LLM summarization and entity extraction immediately upon memory ingestion.
- **Advantages**: Abstractions are immediately available in the semantic store.
- **Disadvantages**: Drastically slows down ingestion throughput (adding 500–2,000ms LLM latency per write); fails to observe cross-episode patterns over time.

### Option 2: Periodic Cron-Based Batch Jobs
- **Description**: Run external batch scripts once daily to process raw episodic memories.
- **Advantages**: Simple scheduled execution.
- **Disadvantages**: Rigid scheduling; fails to adapt to agent idle cycles; requires external job orchestration.

### Option 3: Event-Driven Cognitive Reflection Daemon (Selected)
- **Description**: An internal asynchronous daemon (`ReflectDaemon`) that monitors cognitive load, queue depth, and idle intervals. During low-activity windows, it triggers the `ReflectPathway`, which selects salience-weighted episodic memories, runs counterfactual replay, updates Hebbian synaptic weights, extracts generalized semantic records, and applies power-law decay to episodic stores.
- **Advantages**: Adapts dynamically to system load, executes biological sleep replay, extracts deep semantic associations, and maintains bounded episodic footprint.
- **Disadvantages**: Requires state machine coordination to avoid lock contention with concurrent active writes.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Event-Driven Cognitive Reflection Daemon).

### Positive Consequences
- Zero ingestion latency overhead for complex memory abstraction.
- Automatic extraction of long-term semantic knowledge from raw conversation streams.
- Continuous pruning of low-importance memories ensures stable long-term storage requirements.

### Negative Consequences & Trade-offs
- Background LLM API calls incur token and computational costs during reflection phases.
- Requires optimistic read-concurrency (`StampedLock`) to safely read memories undergoing background consolidation.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Synchronous** | Immediate abstractions | Severe write latency tax, narrow pattern visibility |
| **Option 2: Periodic Cron** | Simple execution model | Rigid, ignores agent activity states, external deps |
| **Option 3: Reflect Daemon** | Autonomous, biologically accurate, zero write tax | Background token cost, concurrency coordination |

## 7. Implementation Plan

1. **Phase 1**: Implement `ReflectDaemon` background thread manager with load-sensing idle triggers.
2. **Phase 2**: Build `ReflectPathway` orchestrator coordinating episodic candidate selection, semantic abstraction, and graph edge strengthening.
3. **Phase 3**: Implement synaptic decay pass applying power-law forgetting curves to unreinforced memories.
4. **Phase 4**: Add safety circuit breakers to halt reflection immediately if user-facing query traffic spikes.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.reflect`, `com.spectrayan.spector.memory.consolidation`
- **Classes**: `ReflectPathway.java`, `ReflectDaemon.java`, `MemoryConsolidationEngine.java`, `SynapticDecayManager.java`
- **Verification Tests**: `ReflectPathwayIntegrationTest.java`, `SleepConsolidationTest.java`
