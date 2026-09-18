# ADR-0016: AISME Phase 8 — Closed-Loop Epistemic Learning

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

Issue #605 (Active Inference Self-Model Engine Phase 8) addresses closing the active perception loop across `spector-config`, `spector-memory`, `spector-spring`, and `spector-synapse`. Prior to Phase 8, perceptual inference evaluated variational free energy $\mathcal{F}(s, o)$ for memory ranking, but did not feed observed query tokens or retrieved memories back into the variational posterior belief state $q(s)$. Consequently, cognitive beliefs remained static across dialogue turns unless manually reset.

## 2. Problem Statement

Operating active inference in an open loop prevents dynamic learning. When incoming observations disagree with the current belief state, prediction errors must drive variational updates to internal states and modulate homeostatic affect. Furthermore, biological sleep consolidation in `ReflectPathway` lacked dynamic empirical co-activation input to adapt the Riemannian manifold metric tensor $G(s)$, leaving the underlying manifold geometry static.

## 3. Decision Drivers

- **Real-Time Variational Updating**: Incoming observations and retrieved memory engrams must iteratively update the internal belief state $q(s)$.
- **Temporal Belief Decay**: Internal beliefs must gracefully decay toward the baseline prior $\boldsymbol{\mu}_0$ when the agent is idle.
- **Biologically Grounded Manifold Plasticity**: Synaptic co-activation patterns must inform Riemannian metric tensor $G(s)$ updates during circadian sleep reflection.
- **Unified Configuration**: System-wide properties must govern learning rates, decay constants, and coupling coefficients through `AismeProperties`.

## 4. Considered Options

### Option 1: Asynchronous Post-Processing Worker
- **Description**: Queue observations for background belief updates outside the recall request path.
- **Advantages**: Completely isolates query latency from belief updating.
- **Disadvantages**: Belief updates lag behind subsequent conversation turns, causing incoherence during rapid turn-taking; introduces cross-thread synchronization overhead.

### Option 2: Synchronous Epistemic Learning Relay in `RecallPathway` (Selected)
- **Description**: Integrate an explicit `EpistemicLearningRelay` as Stage 16 in `RecallPathway`, executing synchronous sub-microsecond belief updates and coupling with `HomeostaticCore`.
- **Advantages**: Immediate belief consistency across sequential turns; zero-allocation in-memory arithmetic; deterministic execution order.
- **Disadvantages**: Adds ~0.02ms to the tail of `RecallPathway`.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Synchronous Epistemic Learning Relay).

### Architectural Additions:

1. **Epistemic Learning Relay (`EpistemicLearningRelay`)**:
   - Integrated as Stage 16 in `RecallPathway`.
   - Extracts the fused observation vector from retrieved candidate memories and query vectors.
   - Updates the live variational belief state $q(s)$ via `MentalStateTracker.updateWithObservation(observation, timestamp)`.
   - Applies exponential temporal belief decay $\boldsymbol{\mu}_t \to \boldsymbol{\mu}_0$ when idle time exceeds threshold.
   - Numerically advances `HomeostaticCore.step(stimulus, reward, dt)` to couple sensory stimuli and memory retrieval with affective state.

2. **Hebbian Co-Activation Supplier in Sleep Reflection**:
   - Wired a dynamic supplier in `ReflectPathway.Builder` providing top co-activated Hebbian memory edge vector differences:
     `() -> hebbianGraph.findTopCoActivatedPairs(50, 0.4f).stream().map(edge -> vectorDifference(edge)).toList()`
   - Enables `ManifoldConsolidationRelay` to adapt the Riemannian metric tensor $G(s)$ on each circadian sleep consolidation cycle.

3. **System-Wide `AismeProperties` Integration**:
   - Added first-class `AismeProperties` to `spector-config`, `spector-spring`, and `spector-synapse` for system-level YAML and environment variable configuration.

### Positive Consequences
- True closed-loop active inference: perceptions continuously adapt internal beliefs and affective states.
- Long-term cognitive manifold geometry dynamically reflects learned associations through sleep consolidation.
- Fully declarative configuration via Spring Boot and standalone configuration profiles.

### Negative Consequences & Trade-offs
- Slight latency addition (~20 microseconds) at the conclusion of recall pipeline execution.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Async Worker** | Decoupled latency | Stale beliefs across rapid turns, complex thread synchronization |
| **Option 2: Inline Relay** | Strict turn-to-turn consistency, zero-allocation math, deterministic | Minor (~20µs) tail latency addition |

## 7. Implementation Plan

1. **Phase 1**: Implement `EpistemicLearningRelay` in `memory/spector-memory/aisme/relay`.
2. **Phase 2**: Wire Hebbian co-activation pair extraction into `ReflectPathway.Builder`.
3. **Phase 3**: Register `AismeProperties` in `nucleus/spector-config` and `synapse/spector-synapse`.
4. **Phase 4**: Verify convergence and belief stability under multi-turn dialogue simulation.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-config`, `synapse/spector-synapse`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.relay`, `com.spectrayan.spector.memory.aisme.fegr`, `com.spectrayan.spector.memory.aisme.homeostasis`, `com.spectrayan.spector.memory.pathway.reflect`
- **Classes**: `EpistemicLearningRelay.java`, `MentalStateTracker.java`, `HomeostaticCore.java`, `ReflectPathway.java`, `HebbianGraphMemory.java`
- **Verification Tests**: `EpistemicLearningRelayTest.java`, `AismeIntegrationTest.java`
