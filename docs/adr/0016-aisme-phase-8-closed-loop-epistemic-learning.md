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

**Context**: Issue #605 — Active Inference Self-Model Engine Phase 8 (Closing the Active Perception Loop)  
**Module**: `spector-config`, `spector-memory`, `spector-spring`, `spector-synapse`  

## Decision

### 1. Epistemic Learning Relay (`EpistemicLearningRelay`)
Integrated as Stage 16 in `RecallPathway`:
- Extracts the fused observation from retrieved candidate memories and query vectors.
- Updates the live variational belief state \(q(s)\) via `MentalStateTracker.updateWithObservation(observation, timestamp)`.
- Applies exponential temporal belief decay \(\boldsymbol{\mu}_t \to \boldsymbol{\mu}_0\) when idle time exceeds threshold.
- Numerically advances `HomeostaticCore.step(stimulus, reward, dt)` to couple sensory stimuli and memory retrieval with affective state.

### 2. Hebbian Co-Activation Supplier in Sleep Reflection
- Wired a dynamic supplier in `ReflectPathway.Builder` providing top co-activated Hebbian memory edge vector differences:
  `() -> hebbianGraph.findTopCoActivatedPairs(50, 0.4f).stream().map(edge -> vectorDifference(edge)).toList()`
- Enables `ManifoldConsolidationRelay` to adapt the Riemannian metric tensor \(G(s)\) on each circadian sleep consolidation cycle.

### 3. System-Wide AismeProperties Integration
- Added first-class `AismeProperties` to `spector-config`, `spector-spring`, and `spector-synapse` for system-level YAML and environment variable configuration.

**Approved** — implemented in PR #606.
