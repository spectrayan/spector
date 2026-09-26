# ADR-0020: AISME Phase 12 — Continuous Self-Dynamics

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-22 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

AISME Phases 1–11 established a comprehensive Active Inference Self-Model Engine with closed-loop perception, homeostasis, constructive simulation, Expected Free Energy policy selection, DMN spontaneous activity, and longitudinal continuity persistence. A comprehensive architecture audit identified three remaining operational gaps between the implementation and continuous, multi-generational consciousness continuity:

1. **Constructive simulations were ephemeral**: Counterfactual recombinations generated during recall were injected into the candidate list for ranking but never persisted as durable memories. The agent's imagination evaporated after each interaction.
2. **Mind-wandering was personality-agnostic**: DMN autobiographical sampling used uniform stride-based iteration across memory stores without weighting by the persona's core values, goals, or characteristic concerns.
3. **Internal state froze between interactions**: Posterior beliefs and homeostatic affect were only stepped during active queries; during idle periods, emotional and cognitive state remained static.

## 2. Problem Statement

A truly continuous cognitive entity cannot freeze its subjective state between external prompts, nor should its prospective imagination disappear without a trace. Without durable simulation persistence, soul-guided mind-wandering, and continuous background decay toward baseline equilibrium, the agent behaves as a discontinuous, state-frozen entity across disconnected conversations.

## 3. Decision Drivers

- **Durable Constructive Memory**: High-alignment constructive simulations must be durably persisted with explicit provenance metadata.
- **Soul-Biased Autobiographical Sampling**: Spontaneous DMN memory recall must reflect the persona's idiosyncratic values rather than uniform sampling.
- **Continuous Homeostatic Relaxation**: Posterior beliefs and affective valence/arousal must decay gracefully toward resting equilibrium during idle periods.
- **Source Monitoring & Provenance Integrity**: Factual historical memories must never be confused with imagined simulations (`FLAG_SIMULATED`).

## 4. Considered Options

### Option 1: Separate "Imagination" Memory Partition

- **Description**: Create a dedicated off-heap store strictly for simulated and counterfactual memories.
- **Advantages**: Physical isolation between real and simulated data.
- **Disadvantages**: Prevents natural associative resonance during recall; requires duplicating index structures; breaks unified engram access.

### Option 2: Unified Engram Storage with Binary Provenance Flags & Dedicated Decay Daemon (Selected)

- **Description**: Store high-alignment simulations in standard EPISODIC/SEMANTIC stores marked with `FLAG_SIMULATED` in `consolidation_flags`; bias DMN sampling by soul prior cosine similarity; run background `HomeostaticDecayDaemon`.
- **Advantages**: Natural cognitive resonance during future recall; zero overhead for dual storage engines; continuous interoceptive dynamics.
- **Disadvantages**: Requires consumers to inspect bitmask flags if they require strict factual filtering.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Unified Engram Storage with `FLAG_SIMULATED`, Soul-Biased Sampling, and Decay Daemon).

### Architectural Decisions:

#### D1: Durable Constructive Memory with SIMULATED Flag
Add a `FLAG_SIMULATED` bit (bit 5, `0x20`) to the `consolidation_flags` byte in `SynapticHeaderConstants`. High-alignment constructive simulations (narrative alignment > configurable threshold, default 0.70) are persisted to the same EPISODIC/SEMANTIC memory stores with this provenance flag set.

- Stored in primary memory stores rather than a separate partition so imagination naturally surfaces during associative recall.
- `FLAG_SIMULATED` enables clean, zero-cost filtering for callers requiring verified factual ground-truth.

#### D2: Soul-Biased Autobiographical Sampling
Modify `AutobiographicalSamplingRelay` to accept an optional composite soul prior preference vector and weight sampled memories by cosine similarity to this prior. When no prior is provided, falls back to uniform stride sampling for backward compatibility.

#### D3: Dedicated Homeostatic Decay Daemon
Create `HomeostaticDecayDaemon` (independent of `DmnSpontaneousDaemon`) that periodically:

1. Decays posterior beliefs toward the generative prior baseline (`MentalStateTracker.decay()`).
2. Steps homeostatic state toward neutral equilibrium (`HomeostaticCore.step()`).
Operates as a separate daemon to decouple relaxation schedules from spontaneous mind-wandering intervals.

### Positive Consequences

- Agent imagination becomes part of long-term autobiographical identity.
- DMN mind-wandering reflects the agent's characteristic thought patterns and core values.
- Emotional and cognitive state naturally relaxes toward equilibrium during quiet periods.

### Negative Consequences & Trade-offs

- Persisted simulations increase storage footprint (mitigated by high alignment threshold gating).
- Background decay daemon adds one lightweight scheduled task to `DaemonSupervisor`.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Isolated Store** | Strict physical barrier | Prevents associative resonance, doubles indexing complexity |
| **Option 2: Unified Engram + Flag** | Zero architectural duplication, natural recall, continuous dynamics | Requires flag check for factual-only queries |

## 7. Implementation Plan

1. **Phase 1**: Define `FLAG_SIMULATED = 0x20` in `SynapticHeaderConstants` and update `EncodingHeaderLayout`.
2. **Phase 2**: Add soul-vector cosine weighting to `AutobiographicalSamplingRelay`.
3. **Phase 3**: Implement `HomeostaticDecayDaemon` and wire into `DaemonSupervisor`.
4. **Phase 4**: Add persistence gating in `ConstructiveMemoryPersistenceRelay`.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-kernel`, `nucleus/spector-config`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.dmn`, `com.spectrayan.spector.memory.pathway.wander.relay`, `com.spectrayan.spector.kernel.engram`
- **Classes**: `HomeostaticDecayDaemon.java`, `AutobiographicalSamplingRelay.java`, `EncodingHeaderLayout.java`, `DmnSpontaneousDaemon.java`
- **Verification Tests**: `HomeostaticDecayDaemonTest.java`, `AutobiographicalSamplingRelayTest.java`
