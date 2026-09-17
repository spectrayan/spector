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

## 1. Context

Following the implementation of AISME Phases 1–12, an architectural verification audit identified three technical areas in the self-model loop requiring structural refinement:
1. Low-level off-heap byte offset arithmetic and scalar dequantization logic had leaked into `SpectorMemoryFactory`.
2. High-alignment constructive simulations generated in `ConstructiveSimulationRelay` failed to persist into long-term autobiographical storage because `ConstructiveMemoryPersistenceRelay` received a null ingestion target in `AismeBuilder`.
3. Ad-hoc string prefix matching (`"sim-"`) was being used for memory identification, violating uniform Crockford Base32 ID generation standards (`IdStrategy` / `TsidGenerator`) and bypassing native off-heap binary header flags (`SynapticHeaderConstants.FLAG_SIMULATED = 0x20`).

## 2. Problem Statement

Leaking low-level layout details into factory classes violates the Single Responsibility Principle and creates tight coupling between cortex memory models and physical storage slabs. Furthermore, leaving the constructive persistence loop unwired causes imagined scenarios to be lost, preventing cumulative narrative learning. Finally, using string prefixes to distinguish memory types is fragile, slow, and unaligned with the 64-byte `SynapticHeader` specification.

## 3. Decision Drivers

- **Clean Decoupling**: Isolate vector lookup and dequantization from high-level factories and relay coordinators.
- **Loop Closure**: Ensure high-alignment constructive simulations reliably persist into autobiographical memory.
- **Binary Metadata Standards**: Use native bitmask flags (`FLAG_SIMULATED`) in the off-heap cognitive header rather than fragile string prefixes.
- **Backward Compatibility**: Preserve existing factory and builder contracts while wiring missing components.

## 4. Considered Options

### Option 1: Inline Lookups in Relays
- **Description**: Let each relay perform its own raw segment slicing and dequantization.
- **Advantages**: No new classes required.
- **Disadvantages**: Massive code duplication across `ConstructiveSimulationRelay`, `PolicyInferenceRelay`, and `EpistemicLearningRelay`.

### Option 2: Full Entity Object Hydration
- **Description**: Hydrate full Java domain objects for every vector lookup.
- **Advantages**: Standard object-oriented access.
- **Disadvantages**: Heavy heap allocation and GC churn on hot cognitive traversal paths.

### Option 3: Dedicated `CognitiveVectorAccessor` + Binary Header Gating (Selected)
- **Description**: Introduce a functional `CognitiveVectorAccessor` component encapsulated in cortex, wire `CognitiveIngestionTarget` into `AismeBuilder`, and standardize on `FLAG_SIMULATED`.
- **Advantages**: Zero-allocation point lookup; clean separation of concerns; completes the active-inference loop.
- **Disadvantages**: Adds a new cortex component to maintain.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Dedicated `CognitiveVectorAccessor` & Binary Header Gating).

### Architectural Decisions:

#### D1: Dedicated `CognitiveVectorAccessor` Component
Create `com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor` implementing `Function<String, float[]>`:
- Encapsulates point vector retrieval and scalar dequantization from partitioned off-heap memory (`MemoryIndex` $\rightarrow$ `PartitionRegistry` $\rightarrow$ `CognitiveRecordLayout` $\rightarrow$ `ScalarQuantizer`).
- Removes all byte decoding and offset arithmetic from factory classes.
- Provides zero-allocation, reusable vector retrieval across recall rerankers, epistemic learning, and constructive simulation.

#### D2: Elimination of String Prefixing in Favor of `FLAG_SIMULATED`
- Eliminate `"sim-"` string prefix conventions for memory identification.
- In `ConstructiveSimulationRelay`, mark synthesized `CognitiveResult` instances using `SynapticHeaderConstants.FLAG_SIMULATED` in `consolidationFlags`.
- In `ConstructiveMemoryPersistenceRelay`, gate persistence using `SynapticHeaderConstants.isSimulated(result.consolidationFlags())`.
- Assign standard Crockford Base32 `TsidGenerator` IDs to all persisted memories while writing `FLAG_SIMULATED` to the off-heap cognitive header.

#### D3: Full Production Wiring of `CognitiveIngestionTarget` in `AismeBuilder`
- Update `AismeBuilder.build(...)` to accept `CognitiveIngestionTarget`.
- Pass `cognitiveTarget` from `SpectorMemoryFactory`, completing the durable counterfactual self-model loop.

#### D4: Expressive Somatic Feedback Loop
- Provide an optional somatic feedback mechanism from `ExpressPathway` into `MentalStateTracker`, simulating the biological facial feedback hypothesis and autonomic catharsis.

### Positive Consequences
- Closes the active-inference loop for durable constructive memory persistence.
- Eliminates code duplication and factory pollution for vector lookups.
- Standardizes all simulation metadata on off-heap bitmask flags rather than fragile string prefixes.
- Maintains 100% backward compatibility.

### Negative Consequences & Trade-offs
- Adds one new class in cortex (`CognitiveVectorAccessor`).

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Inline Lookups** | No new classes | Code duplication, breaks encapsulation |
| **Option 2: Object Hydration** | Conventional Java OOP | Severe GC pressure on hot paths |
| **Option 3: Vector Accessor** | Zero-allocation, clean encapsulation, standard bitmask | Minor added class surface in cortex |

## 7. Implementation Plan

1. **Phase 1**: Author `CognitiveVectorAccessor` in `memory/spector-memory/cortex`.
2. **Phase 2**: Refactor `ConstructiveSimulationRelay` and `ConstructiveMemoryPersistenceRelay` to use `FLAG_SIMULATED`.
3. **Phase 3**: Wire `CognitiveIngestionTarget` through `AismeBuilder` and `SpectorMemoryFactory`.
4. **Phase 4**: Add somatic feedback hook in `ExpressPathway`.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-kernel`
- **Key Packages**: `com.spectrayan.spector.memory.cortex`, `com.spectrayan.spector.memory.aisme.relay`, `com.spectrayan.spector.kernel.engram`
- **Classes**: `CognitiveVectorAccessor.java`, `ConstructiveMemoryPersistenceRelay.java`, `ConstructiveSimulationRelay.java`, `AismeBuilder.java`
- **Verification Tests**: `CognitiveVectorAccessorTest.java`, `ConstructivePersistenceIntegrationTest.java`
