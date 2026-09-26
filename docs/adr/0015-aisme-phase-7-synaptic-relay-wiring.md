# ADR-0015: AISME Phase 7 — Synaptic Relay Wiring & Pathway Configuration

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-28 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Phases 1 through 6 of AISME developed specialized cognitive subsystems: homeostatic regulation, free-energy scoring, Hopfield associative memory, neural manifold distance, predictive self-modeling, and continuity evaluation. Phase 7 integrates these decoupled components into Spector's core `RecallPathway` and `RememberPathway` execution pipelines via composable synaptic relays.

## 2. Problem Statement

Individual cognitive kernels remained isolated without an end-to-end wiring harness. Hardcoding relay execution order would violate Spector's modularity principles, while uncoordinated relay chaining risks compounding query latencies. We need a declarative, zero-overhead pipeline wiring architecture.

## 3. Decision Drivers

- **Declarative Pipeline Sequencing**: Order of synaptic relay execution must be configurable per tenant namespace.
- **Zero Allocation on Hot Paths**: Relay execution must pass mutable `RecallSignal` and `IngestSignal` handles without creating intermediate wrapper objects.
- **Fail-Safe Bypass**: If any experimental cognitive relay fails or exceeds its latency SLA, the pipeline must fail-open to standard vector retrieval.
- **Strict Latency Budget**: Cumulative relay pipeline overhead must remain < 0.25ms at 10K candidates.

## 4. Considered Options

### Option 1: Java Reflection & Dynamic Invocation

- **Description**: Configure pipeline stages via reflection using class names specified in properties.
- **Advantages**: Fully decoupled dynamic loading.
- **Disadvantages**: High invocation overhead, lack of compiler type-safety, and issues with native image compilation.

### Option 2: Pre-Compiled Direct Relay Chain (Selected)

- **Description**: Implement a statically typed, array-indexed pipeline runner executing pre-compiled relay chains:
  $$\text{Input} \xrightarrow{\text{Transduce}} \text{HomeostaticBias} \xrightarrow{\text{Scan}} \text{ManifoldDistance} \xrightarrow{\text{HopfieldAssociator}} \text{FreeEnergyRerank} \rightarrow \text{Output}$$
  Configured declaratively in `spector-config` using immutable builder records.

- **Advantages**: Zero reflection overhead; branch-predicted sequential execution; JIT inlining; sub-microsecond step transitions.
- **Disadvantages**: Adding new relays requires registering them in the canonical relay catalog.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Pre-Compiled Direct Relay Chain).

### Positive Consequences

- End-to-end cognitive memory execution in < 0.5ms.
- Clean separation of concerns between individual relay logic and pipeline orchestration.
- Declarative configuration via `spector-config` property trees.

### Negative Consequences & Trade-offs

- Modifying relay sequence requires updating the pipeline configuration builder.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Reflection** | Dynamic class loading | High invocation overhead, no JIT inlining |
| **Option 2: Direct Chain** | Zero GC, JIT inlined, deterministic ordering | Static relay catalog registration |

## 7. Implementation Plan

1. **Phase 1**: Define `SynapticRelay` interface with `process(Signal)` contract in `memory/spector-memory/pathway/relay`.
2. **Phase 2**: Implement canonical relay pipeline sequence in `RecallPathway`.
3. **Phase 3**: Add declarative configuration properties in `spector-config` under `spector.memory.pathway.relays`.
4. **Phase 4**: Validate end-to-end integration with JMH micro-benchmarks.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-config`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.relay`, `com.spectrayan.spector.config.pathway`
- **Classes**: `SynapticRelay.java`, `RecallPathway.java`, `PathwayPipelineBuilder.java`, `PathwayConfig.java`
- **Verification Tests**: `SynapticRelayWiringTest.java`, `EndToEndPathwayIntegrationTest.java`
