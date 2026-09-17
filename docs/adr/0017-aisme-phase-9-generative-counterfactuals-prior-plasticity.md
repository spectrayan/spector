# ADR-0017: AISME Phase 9 — Generative Counterfactuals & Prior Plasticity

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

Issue #607 (Active Inference Self-Model Engine Phase 9) focuses on generative counterfactual simulation and prior plasticity within `spector-memory` and `spector-config`. Prior to Phase 9, episodic recall strictly retrieved historical traces as originally recorded. However, contemporary cognitive neuroscience (e.g. Schacter & Addis 2007) demonstrates that biological episodic memory is fundamentally constructive, recombining elements of past experiences to envision possible futures, evaluate alternative choices, and adapt the generative self-model.

## 2. Problem Statement

Treating episodic memory solely as a passive recording prevents the agent from synthesizing hypothetical alternatives or simulating prospective outcomes. Without constructive simulation, the agent cannot anticipate unexpected events or generate creative solutions. Furthermore, keeping the agent's generative prior mean $\boldsymbol{\mu}_0$ statically fixed over time causes progressive divergence between the agent's identity anchor and its evolving autobiographical experience.

## 3. Decision Drivers

- **Constructive Episodic Simulation**: The memory system must recombine high-salience complementary episodes into novel counterfactual scenario representations.
- **Cognitive Truth Preservation**: Simulated episodes must be explicitly demarcated from factual history (`MemorySource.REFLECTED`, `[simulated, counterfactual]`) to prevent cognitive confabulation.
- **Autobiographical Prior Plasticity**: Generative self-model prior mean $\boldsymbol{\mu}_0$ must adapt during sleep consolidation based on the centroid of autobiographical experiences.
- **Thread-Safe Plasticity**: Updating generative priors in `MentalStateTracker` must be thread-safe without interrupting concurrent read requests.

## 4. Considered Options

### Option 1: Static Generative Priors with Ephemeral LLM Prompts
- **Description**: Leave internal priors unchanged; instruct downstream LLMs via prompts to invent alternative scenarios.
- **Advantages**: No changes to mathematical kernels or storage models.
- **Disadvantages**: Fails to provide architectural grounding; simulations cannot be recalled or evaluated against active-inference objectives.

### Option 2: Full Online Real-Time Prior Plasticity
- **Description**: Mutate $\boldsymbol{\mu}_0$ on every conversation turn using gradient steps.
- **Advantages**: Rapid adaptation to immediate dialogue.
- **Disadvantages**: Highly unstable; vulnerable to adversarial manipulation and rapid catastrophic forgetting of core identity.

### Option 3: Sleep-Consolidated Prior Plasticity & Constructive Simulation Relay (Selected)
- **Description**: Integrate constructive simulation during recall and perform slow, bounded prior adaptation during sleep reflection.
- **Advantages**: Stable identity maintenance; bio-mimetic consolidation; safe provenance tracking.
- **Disadvantages**: Requires coordination between recall simulation and reflection pathways.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Sleep-Consolidated Prior Plasticity & Constructive Simulation Relay).

### Key Architectural Mechanisms:
1. **Constructive Episodic Simulation & Counterfactual Recombination**:
   - Enhanced `ConstructiveSimulationRelay` to detect complementary, high-salience memories aligned with the persona's autobiographical narrative prior.
   - Recombines complementary episodes into synthesized counterfactual scenario representations tagged `[simulated, counterfactual, constructive]` (`MemoryType.EPISODIC`, `MemorySource.REFLECTED`).
2. **Generative Prior Mean Plasticity During Sleep Consolidation**:
   - Added `withAdaptedPriorMean` in `GenerativeSelfModel` and thread-safe `adaptPriorMean` in `MentalStateTracker`.
   - During REM sleep reflection (`SoulDriftRefusionRelay` / `ReflectPathway`), computes the moving centroid of autobiographical memories $\mathbf{c}_{\text{autobio}}$ and adapts the generative prior mean:
     $$\boldsymbol{\mu}_0 \leftarrow (1 - \eta)\boldsymbol{\mu}_0 + \eta \mathbf{c}_{\text{autobio}}, \quad \eta = 0.005$$

### Positive Consequences
- Enables counterfactual reasoning and prospective memory simulation grounded in actual past experience.
- Generative identity drifts gracefully with life experience without abrupt personality jumps.
- Strict provenance tags prevent confusion between factual and imagined memories.

### Negative Consequences & Trade-offs
- Synthesized episodes consume storage and vector index space if persisted.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Prompt-Only** | Simple implementation | Ephemeral, not grounded in memory kernels |
| **Option 2: Online Gradient** | Instant adaptation | Destabilizes identity, prone to prompt poisoning |
| **Option 3: Sleep Plasticity** | Bounded stability, biologically faithful | Requires background reflection pass |

## 7. Implementation Plan

1. **Phase 1**: Implement `ConstructiveSimulationRelay` in `memory/spector-memory/aisme/relay`.
2. **Phase 2**: Add `withAdaptedPriorMean` in `GenerativeSelfModel` and thread-safe update methods in `MentalStateTracker`.
3. **Phase 3**: Wire centroid computation and adaptation step into `SoulDriftRefusionRelay`.
4. **Phase 4**: Verify stability under simulated multi-epoch sleep consolidation.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `nucleus/spector-config`
- **Key Packages**: `com.spectrayan.spector.memory.aisme.relay`, `com.spectrayan.spector.memory.aisme.fegr`, `com.spectrayan.spector.memory.pathway.reflect.relay`
- **Classes**: `ConstructiveSimulationRelay.java`, `GenerativeSelfModel.java`, `MentalStateTracker.java`, `SoulDriftRefusionRelay.java`, `ReflectPathway.java`
- **Verification Tests**: `ConstructiveSimulationRelayTest.java`, `GenerativeSelfModelTest.java`
