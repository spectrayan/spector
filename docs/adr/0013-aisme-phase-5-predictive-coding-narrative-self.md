# ADR-0013: AISME Phase 5 — Predictive Coding Narrative Self

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-26 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

In cognitive psychology, the "narrative self" represents an agent's continuous, autobiographical identity over time. Under the predictive coding paradigm, this narrative identity acts as a top-down generative model that constantly predicts upcoming observations, actions, and user interactions. When incoming events deviate from predictions, precision-weighted prediction errors update the narrative self model.

## 2. Problem Statement

Autonomous agents lack a cohesive self-model that persists and adapts across interaction sessions. Without predictive self-modeling, agents exhibit behavioral incoherence, forget their own established perspectives, and fail to track shifts in their relationship with users. Spector needs a continuous narrative self engine that updates through hierarchical predictive coding.

## 3. Decision Drivers

- **Autobiographical Coherence**: Maintain an evolving identity state vector and narrative summary across multi-turn sessions.
- **Predictive Coding Architecture**: Implement hierarchical top-down prediction and bottom-up error propagation.
- **Plasticity vs. Stability Dilemma**: Prevent catastrophic identity drift while allowing authentic character growth.
- **Off-Heap Identity Persistence**: Store self-model state vectors in dedicated off-heap memory segments.

## 4. Considered Options

### Option 1: Static Prompt Injection

- **Description**: Hardcode agent identity in system prompts and inject fixed persona text.
- **Advantages**: Simple configuration.
- **Disadvantages**: Static and brittle; cannot learn from interactions or adapt to evolving user relationships.

### Option 2: Hierarchical Predictive Coding Self-Engine (Selected)

- **Description**: Maintain an off-heap `NarrativeSelfState` vector representing core beliefs, personality traits, and autobiographical milestones. At each turn, generate top-down predictions of user responses. Compute prediction error $\epsilon = y - g(\theta)$; when precision-weighted error exceeds an epistemic threshold, update narrative traits via Kalman-filtered Bayesian updates.
- **Advantages**: Produces organic, authentic behavioral evolution; mathematically grounded in predictive coding; highly compact off-heap footprint (2 KB).
- **Disadvantages**: Requires calibrating prediction error learning rates to avoid identity oscillations.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Hierarchical Predictive Coding Self-Engine).

### Positive Consequences

- Consistent autobiographical memory and character voice across long-horizon interactions.
- Quantitative measurement of conversational surprise via prediction error tracking.
- Self-model updates occur out-of-band without degrading dialogue response latency.

### Negative Consequences & Trade-offs

- Extreme conversational shocks require dampening to prevent personality instability.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Static Persona** | Zero compute overhead | Rigid, unadaptive, breaks illusion of continuity |
| **Option 2: Predictive Coding** | Evolving narrative, quantified surprise, biological fidelity | Requires learning rate tuning and error dampening |

## 7. Implementation Plan

1. **Phase 1**: Define `NarrativeSelfState` record and off-heap layout in `spector-kernel`.
2. **Phase 2**: Implement `PredictiveSelfEngine` and error computation in `memory/spector-memory/aisme/self`.
3. **Phase 3**: Add `NarrativeSelfBiasRelay` to modulate recall scoring using autobiographical relevance.
4. **Phase 4**: Implement periodic identity checkpointing in `ReflectDaemon`.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `synapse/spector-synapse`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.self`, `com.spectrayan.spector.synapse.persona`
- **Classes**: `PredictiveSelfEngine.java`, `NarrativeSelfState.java`, `IdentityUpdatePolicy.java`
- **Verification Tests**: `NarrativeSelfContinuityTest.java`, `PredictiveErrorUpdateTest.java`
