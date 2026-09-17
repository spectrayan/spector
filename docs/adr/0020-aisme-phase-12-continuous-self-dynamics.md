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

## Status
Accepted

## Date
2026-08-22

## Context

AISME Phases 1–11 established a complete Active Inference Self-Model Engine with closed-loop perception, homeostasis, constructive simulation, EFE policy selection, DMN spontaneous activity, and longitudinal continuity. An external audit (Grok final report) identified 3 remaining engineering gaps between the current system and the goal of multi-generational consciousness continuity:

1. **Constructive simulations are ephemeral** — counterfactual recombinations generated during recall are injected into the candidate list for ranking but never persisted as durable memories. The person's imagination evaporates after each interaction.

2. **Mind-wandering is personality-agnostic** — DMN autobiographical sampling uses uniform stride-based iteration across memory stores without weighting by the person's values, goals, or characteristic concerns.

3. **Posterior and homeostatic state freeze between interactions** — `MentalStateTracker.decay()` and `HomeostaticCore.step()` exist but are never called outside active perception cycles. Between conversations, the system's emotional state and beliefs are completely static.

## Decision

### D1: Durable Constructive Memory with SIMULATED Flag

Add a `FLAG_SIMULATED` bit (bit 5) to the `consolidation_flags` byte in `SynapticHeaderConstants`. High-alignment constructive simulations (narrative alignment > configurable threshold, default 0.70) are persisted to the same EPISODIC/SEMANTIC memory stores with this provenance flag set.

**Rationale**: Store in same stores rather than a new mmap region because:
- Constructive memories should naturally surface during recall (the person's imagination IS part of who they are)
- The SIMULATED flag enables clean filtering for callers that need to distinguish real vs imagined
- Same record structure — no need for a separate memory layout

### D2: Soul-Biased Autobiographical Sampling

Modify `AutobiographicalSamplingRelay` to accept an optional composite soul prior preference vector and weight sampled memories by cosine similarity to this prior. When no prior is provided, falls back to uniform stride sampling (backward compatible).

### D3: Dedicated Homeostatic Decay Daemon

Create a `HomeostaticDecayDaemon` (separate from `DmnSpontaneousDaemon`) that periodically:
1. Decays posterior beliefs toward the generative prior baseline (`MentalStateTracker.decay()`)
2. Steps homeostatic state toward neutral equilibrium (`HomeostaticCore.step()`)

**Rationale**: Separate daemon rather than enriching `DmnSpontaneousDaemon` because decay and wandering have different scheduling requirements and coupling concerns.

## Consequences

### Positive
- Imagination becomes part of long-term identity narrative
- DMN activity reflects the person's characteristic thought patterns
- Emotional and cognitive state naturally relaxes between interactions (like sleep)
- Closer approximation to continuous phenomenological self-dynamics

### Negative
- Persisted simulations increase storage footprint (mitigated by high alignment threshold)
- Soul-biased sampling slightly increases per-sample computation
- Background decay daemon adds one more scheduled task to DaemonSupervisor

### Risks
- Simulated memories could be confused with real memories if downstream consumers don't check the SIMULATED flag — mitigated by clear provenance tagging and metadata markers
