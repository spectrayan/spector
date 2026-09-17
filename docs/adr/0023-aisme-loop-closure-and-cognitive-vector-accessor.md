# ADR-0023: AISME Complete Loop Closure & CognitiveVectorAccessor

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-23 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## Status
Accepted

## Date
2026-08-23

## Context

Grok's verification audit of the Active Inference Self-Model Engine (AISME) surfaced three technical areas requiring clean architectural refinement:
1. **Separation of Concerns in Vector Lookup**: `SpectorMemoryFactory` should not contain off-heap byte offset calculations or scalar quantization decoding logic.
2. **Durable Constructive Persistence Wiring**: `ConstructiveMemoryPersistenceRelay` was receiving a null ingestion target in `AismeBuilder`, preventing high-alignment counterfactual simulations from persisting into long-term autobiographical memory.
3. **Simulation Tagging Standard**: Ad-hoc string prefix matching (`"sim-"`) in memory IDs violated Spector's uniform ID standards (`IdStrategy` / `TsidGenerator`) and bypassed the native binary header flag (`SynapticHeaderConstants.FLAG_SIMULATED = 0x20` at offset 34).

## Decision

### D1: Dedicated `CognitiveVectorAccessor` Component
Create `com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor` implementing `Function<String, float[]>`:
- Encapsulates point vector retrieval and scalar dequantization from partitioned off-heap memory (`MemoryIndex` $\rightarrow$ `PartitionRegistry` $\rightarrow$ `CognitiveRecordLayout` $\rightarrow$ `ScalarQuantizer`).
- Removes all byte decoding and offset arithmetic from factory classes.
- Provides zero-allocation, reusable vector retrieval across recall rerankers, epistemic learning, and constructive simulation.

### D2: Elimination of String Prefixing in Favor of `FLAG_SIMULATED`
- Eliminate `"sim-"` string prefix conventions for memory identification.
- In `ConstructiveSimulationRelay`, mark synthesized `CognitiveResult` instances using `SynapticHeaderConstants.FLAG_SIMULATED` in `consolidationFlags`.
- In `ConstructiveMemoryPersistenceRelay`, gate persistence using `SynapticHeaderConstants.isSimulated(result.consolidationFlags())`.
- Assign standard Crockford Base32 `TsidGenerator` IDs to all persisted memories while writing `FLAG_SIMULATED` to the off-heap cognitive header.

### D3: Full Production Wiring of `CognitiveIngestionTarget` in `AismeBuilder`
- Update `AismeBuilder.build(...)` to accept `CognitiveIngestionTarget`.
- Pass `cognitiveTarget` from `SpectorMemoryFactory`, completing the durable counterfactual self-model loop.

### D4: Expressive Somatic Feedback Loop
- Provide an optional somatic feedback mechanism from `ExpressPathway` into `MentalStateTracker`, simulating the biological facial feedback hypothesis and autonomic catharsis.

## Consequences

### Positive
- Closes the active-inference loop for durable constructive memory persistence.
- Eliminates code duplication and factory pollution for vector lookups.
- Standardizes all simulation metadata on off-heap bitmask flags rather than fragile string prefixes.
- Maintains 100% backward compatibility.

### Negative / Trade-offs
- Adds one new class in cortex (`CognitiveVectorAccessor`).
