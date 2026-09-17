# ADR-0032: Persona Enactment — Soul as Policy over Memory

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-09-06 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

- **Status:** Proposed (Revised with AISME Engine Integration)
- **Date:** 2026-09-06
- **Deciders:** Spectrayan / Spector (Jarvis, Titan, Neuron, Bharat)
- **Target repo:** `spectrayan/spector`
- **Layer:** Synapse (enactment loop & bounded deliberation) + Memory (AISME cognitive physics, self-recall, Hopfield energy landscapes, EFE policy selection) + Cortex (trace & deliberation visibility)
- **Supersedes:** none
- **Extends:** 
  - ADR-0009 through ADR-0020 (AISME Phases 1–12: Homeostatic Core, FEGR, Continuous Hopfield, Manifold, Predictive Coding, EFE Policy Engine)
  - ADR-0024 (Polymorphic Soul Context: Tenant, OrgUnit, Agent, User)
  - ADR-0027 (Soul-Conditioned Personalized Dreaming)
  - ADR-0028 (Encoding identity / recall audit)
  - ADR-0029 (Identity plane, soul stack, PEP, federated recall budgets)
  - ADR-0030 (Tier layouts, spacetime search, synaptic relays)
  - ADR-0031 (Wander / Dream / Express simulation + epistemic tense)

---

## 0. One-line decision

Synapse will enact the bound persona by composing Memory's **AISME cognitive engine** (`GenerativeSelfModel`, `InteroceptiveState` VAD appraisal, `ContinuousHopfieldNetwork` attractors, and `PolicyInferenceEngine` Expected Free Energy selection) with **System 2 Bounded Deliberation** and **Epistemic Tense (`FACT`/`SIM`/`REPLAY`)**: given a problem, the agent appraises it through the persona's affective state and core dogmas, selects a cognitive policy minimizing Expected Free Energy, deliberates within that policy's bounds, enforces fail-closed tool gates, expresses under explicit tense, and consolidates lived case law back into memory.

Memory remains the cognitive physics. Synapse becomes the living nervous system that *thinks, appraises, and acts as* the person those physics describe.

---

## 1. Context

### 1.1 What already exists

Spector possesses an industry-leading cognitive memory and self-model engine in `spector-memory`:

| Piece | Module / Subsystem | What it provides |
|---|---|---|
| `SoulContext` sealed hierarchy (`TenantSoul`, `OrgUnitSoul`, `AgentSoul`, `UserSoul`) | `memory.model` / ADR-0024 | Immutable identity document, values, purpose, baseline embeddings |
| `IdentityPlane` + identity bundle | Synapse / ADR-0029 §23–§24 | Multi-soul stack assembly, PEP governance, Region 24 migration |
| `CognitiveSoulService` + INSULA mirror | Synapse | Load/save agent and user souls with homeostatic caching |
| `InteroceptiveState` & `HomeostaticCore` | `memory.aisme.homeostasis` / ADR-0009 | 3D VAD (Valence, Arousal, Dominance) SDE affective tracking |
| `EmotionalRegulator` | `memory.aisme.homeostasis` | Personality-tuned affective decay matrices based on `CognitiveProfile` |
| `ContinuousHopfieldNetwork` & `AttractorState` | `memory.aisme.hopfield` / ADR-0011 | Content-addressable energy basins holding dogmas and core heuristics |
| `GenerativeSelfModel` & `MentalStateTracker` | `memory.aisme.fegr` / ADR-0010 | Top-down generative priors $p(s\|m)$ and continuous posterior belief $q(s_t)$ |
| `PolicyInferenceEngine` & `ExpectedFreeEnergyCalculator` | `memory.aisme.policy` / ADR-0019 | Active inference action selection minimizing Expected Free Energy $G(\pi)$ |
| `GlobalWorkspace` & `AttentionSchema` | `memory.aisme.workspace` | Conscious access bottleneck (~7 items) and meta-attentional focus |
| `NarrativeSelfEngine` | `memory.aisme.narrative` / ADR-0013 | Autobiographical identity prior and narrative alignment scoring |
| 4-tier cortex + 64-byte synaptic header | Memory / ADR-0030 | Working, Episodic, Semantic, Procedural layouts with SIMD fusion |
| Wander / Dream / Express + `ExpressTense` | Memory / ADR-0031 | Simulation, counterfactual dreaming, and verbalization under strict epistemic tense |
| Namespace isolation & PEP | ADR-0029 | Physical isolation, zero cross-persona bleed, federated recall budgets |

### 1.2 The gap: Why previous persona approaches failed

Despite these state-of-the-art memory physics, previous persona enactment suffered from two opposite errors:

1. **The Cosmetic Prompt Fallacy:** Injecting adjectives into an LLM system prompt ("You are a grumpy kernel engineer"). The LLM produces a caricature: it changes its vocabulary, but its underlying decision-making remains the generic, agreeable base model prior.
2. **The Skinnerian Behaviorist Fallacy (The initial ADR-0032 draft flaw):** Reducing human decision-making to an 8-item closed enum (`situation.kind`) and majority-voting an 8-item `first_move` (`ASK | FIX | CONTAIN...`). Real humans do not operate as discrete state machines. If two personas both choose `FIX`, their actions are fundamentally different: one patches dirty code in prod because their mental model prioritizes *immediate uptime*; the other halts deployment and rolls back because their mental model prioritizes *structural integrity*.

### 1.3 The Motivating Questions

1. **How does this persona actually appraise a crisis?** Not what words they use, but how their internal affective state (Valence, Arousal, Dominance/Coping Potential) shifts when confronted with threat, ambiguity, or failure.
2. **What mental models and dogmas govern their reasoning?** What trade-offs will they accept (speed vs. correctness, autonomy vs. consensus), and what past "scar tissue" do they refuse to repeat?
3. **How does the persona deliberate and decide?** How does System 1 associative recall combine with System 2 deliberative reasoning without either hallucinating an out-of-character move or freezing into a rigid lookup table?
4. **How do they learn from lived outcomes?** How do new episodes strengthen procedural habits and adjust generative priors without causing single-turn identity drift?

---

## 2. Decision drivers

1. **Dual-Process Cognitive Fidelity (System 1 Physics + System 2 Bounded Deliberation).** Fast sub-millisecond memory physics (Hopfield attractors, VAD affective SDE, EFE policy evaluation) provide the non-negotiable boundaries, mental models, and emotional state. The LLM then performs bounded deliberation within those constraints. The LLM is the reasoning voice; AISME is the cognitive soul.
2. **Cognitive Appraisal over Mechanical Arithmetic.** Situations are not flat text labels. They are evaluated along appraisal dimensions (Goal Congruence $\rightarrow$ Valence, Stakes/Urgency $\rightarrow$ Arousal, Coping Potential $\rightarrow$ Dominance, Agency Attribution $\rightarrow$ Precision Weighting).
3. **Attractor Basins as Dogmas & Heuristics.** A persona's core philosophies and cognitive biases are stable energy minima in a `ContinuousHopfieldNetwork`. Problems relax into the nearest attractor basin, pulling the agent toward that persona's world view.
4. **Active Policy Inference via Expected Free Energy ($G$).** Candidate actions are evaluated by balancing pragmatic value (satisfying the soul's prior preferences) against epistemic value (reducing uncertainty and exploring ambiguity).
5. **Case Law over Costume.** Enacted decisions must cite lived episodic traces and procedural playbooks. When written bio and lived case law conflict, repeated lived case law outranks written adjectives (except for hard guardrails).
6. **Epistemic Tense Discipline (ADR-0031).** Dreamed, wandered, and simulated traces are marked `SIM` or `REPLAY`. Only waking, verified episodes can be verbalized or cited as `FACT`.
7. **Fail-Closed Governance.** Tool execution is gated by the winning cognitive policy and ancestral ethical guardrails. The LLM never sees or executes tools outside the stance allow-list.
8. **Slow Consolidation ($N \ge 3$).** A single turn cannot mutate `AgentSoul` or increment `soulVersion`. Procedural playbooks and generative priors crystallize only during sleep consolidation after repeated waking confirmations.
9. **Observable in Cortex.** Cortex must display the full cognitive anatomy: VAD appraisal, active Hopfield attractor, EFE policy distribution, internal deliberation monologue, and cited memory IDs.

---

## 3. Decision

Introduce **AISME-Conditioned Persona Enactment** in Synapse, bridging Memory's cognitive physics (`spector-memory/aisme`) into an embodied, deliberative execution loop.

### 3.1 Primitive

```
Enact(problem, namespace, acting_soul, mode, context) → Enactment
```

`Enactment` is an immutable, rich cognitive object:

| Field | Type | Description |
|---|---|---|
| `situation` | `SituationFrame` | Problem description, entity context, and temporal stakes |
| `appraisal` | `CognitiveAppraisal` | Evaluated Valence, Arousal, Dominance (Coping Potential), Agency Attribution |
| `active_attractor` | `AttractorState` | Dominant mental model / dogma converged from `ContinuousHopfieldNetwork` |
| `self_context` | `SelfContext` | 4-tier memories selected via `GlobalWorkspace` conscious access |
| `policy_report` | `PolicyDecisionReport` | EFE scores ($G(\pi)$), pragmatic risk, epistemic gain, and Boltzmann selection |
| `deliberation` | `PersonaDeliberation` | Bounded System 2 internal monologue, trade-off rationale, and recognized blind spots |
| `intended_acts` | `Set<String>` | Tool classes allowed by the selected policy and stance |
| `utterance` | `String` | Embodied response verbalized via `Express` under the assigned tense |
| `confidence` | `ConfidenceLevel` | `EVIDENCED` \| `MIXED` \| `INFERRED` |
| `tense` | `ExpressTense` | `FACT` \| `SIM` \| `REPLAY` |
| `citations` | `List<EngramCitation>` | Memory IDs, tiers, and tags justifying the stance |
| `vetoes` | `List<VetoRecord>` | Guardrails, tenant floors, or PEP blocks that fired |

### 3.2 Cognitive Enactment Loop (Synapse Graph)

`DynamicGraphBuilder` incorporates the enactment cognitive pipeline into `NodeType.ENACT`:

```
                 ┌────────────────────────────────────────────────────────┐
                 │                   STIMULUS / PROBLEM                   │
                 └──────────────────────────┬─────────────────────────────┘
                                            │
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ SYNAPSE COGNITIVE LOOP                                                                  │
│                                                                                         │
│  1. PERCEIVE & PRE-APPRAISE (Amended D3)                                                │
│     AttentionSchema + GlobalWorkspace (~7 conscious items)                             │
│     ↳ System 1 Fast Intuitive Pre-Appraisal: Evaluates baseline VAD and stakes          │
│                                            │                                            │
│                                            ▼                                            │
│  2. PERSONA-CONDITIONED, INTENSITY-GATED SELF-RECALL                                    │
│     PersonaRecall (4-Tier)                                                              │
│     ↳ Low urgency (A < 0.15) gates retrieval to lightweight top-K queries               │
│     ↳ Recalls autobiographical scars, procedural habits, dogmas, and constitution       │
│                                            │                                            │
│                                            ▼                                            │
│  3. REFINED COGNITIVE APPRAISAL & ATTRACTOR CONVERGENCE                                 │
│     ContinuousHopfieldNetwork + Refined Appraisal Engine                                │
│     ↳ Recalled scars/playbooks serve as PRIMARY signal (keywords faint fallback)        │
│     ↳ Relaxes sensory state into nearest Hopfield AttractorState (Causal Dogma)         │
│     ↳ Pure functional evaluation: HomeostaticCore SDE is stepped post-turn, not in-turn │
│     ↳ Computes dynamic policy precision: γ = γ₀(1 + 0.5·A + 0.3·D)                      │
│                                            │                                            │
│                                            ▼                                            │
│  4. ACTIVE POLICY INFERENCE (EFE)                                                       │
│     PolicyInferenceEngine + ExpectedFreeEnergyCalculator                                │
│     ↳ Evaluates candidate PolicyTypes against composite soul prior p(o)                 │
│     ↳ Boltzmann softmax selects winning CognitivePolicy minimizing G(π)                 │
│                                            │                                            │
│                                            ▼                                            │
│  5. SYSTEM 2: BOUNDED PERSONA DELIBERATION                                              │
│     Structured Deliberation Node (LLM constrained by winning policy & attractor)        │
│     ↳ Generates internal monologue: trade-off matrix, blind spot check, first move      │
│                                            │                                            │
│                                            ▼                                            │
│  6. FAIL-CLOSED TOOL GATE                                                               │
│     Policy Action Filter ∩ Soul Tool Allow-List \ Refused Tools                         │
│     ↳ Strips unauthorized tools; enforces ancestor guardrails and PEP                   │
│                                            │                                            │
│                                            ▼                                            │
│  7. EXECUTION & EMBODIMENT                                                              │
│     Act (Agent + Tools, decide mode) → Embody (Express under FACT | SIM | REPLAY)       │
│                                            │                                            │
│                                            ▼                                            │
│  8. EPISODIC LEARNING & SLEEP CONSOLIDATION                                             │
│     EnactmentLearner writes lived trace; Sleep consolidation at N ≥ 3 waking trials     │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Normative Decisions

### D1. Soul is Policy over Memory, Powered by AISME
A soul document is not a prompt string. It is a generative specification comprising:
- Top-down prior preferences $p(o)$ in embedding space (`GenerativeSelfModel`).
- Inherent emotional baseline and SDE regulation rates (`HomeostaticCore`, `EmotionalRegulator`).
- Content-addressable attractor basins representing core dogma (`ContinuousHopfieldNetwork`).
- Lived episodic case law and procedural playbooks in the 4-tier cortical memory.

Every decision must pass through the AISME appraisal and policy inference pipeline before language generation or tool invocation.

### D2. Four-Cue Self-Recall via Global Workspace Conscious Access
`PersonaRecall` executes a unified, multi-tier self-retrieval against the bound namespace:

| Cue Role | Memory Tier | Synaptic Tags | Function |
|---|---|---|---|
| **Constitution** | Semantic + INSULA | `soul`, `value`, `belief`, `constraint`, `dogma` | Invariants, ethical floors, identity prior |
| **Autobiography / Scars** | Episodic | `lived`, `reaction`, `decision`, `scar` | Lived analogues, past failures, emotional precedents |
| **Skills / Habits** | Procedural | `habit`, `playbook`, `when_*` | Crystallized execution routines |
| **Now / State** | Working | `affect`, `stance`, `open_loop`, `active_task` | Working affect and unfinished cognitive loops |

The `GlobalWorkspace` enforces the conscious access bottleneck, admitting the top-ranking items ($\le 7$) based on salience, narrative alignment (`NarrativeSelfEngine`), and affective resonance (`AffectiveResonanceScorer`).

### D3. Cognitive Appraisal & Hopfield Attractors Replace Closed Enums

#### A. Cognitive Appraisal Vector
Situations are evaluated along continuous appraisal dimensions mapped to AISME's `InteroceptiveState`:
```java
public record CognitiveAppraisal(
    float goalCongruence,        // [-1.0, 1.0] -> Valence: does this advance or threaten core purpose?
    float urgencyAndStakes,      // [-1.0, 1.0] -> Arousal: activation level and time pressure
    float copingPotential,       // [-1.0, 1.0] -> Dominance: internal competence vs external helplessness
    AgencyAttribution agency,    // SELF | OTHER_ADVERSARY | OTHER_BENIGN | CIRCUMSTANTIAL
    boolean normativeViolation,  // Does this violate professional or personal ethics?
    String primaryConcern        // Core tension identified by the persona
) {}
```
Cognitive appraisal is a **pure, thread-safe functional evaluation** of `(situation, soul, recallOutput)`. It does **NOT** mutate the shared `HomeostaticCore` SDE in place during the turn; the homeostatic SDE is stepped post-turn during Step 8 (Episodic Learning). The appraisal output computes dynamic policy precision:
$$\gamma = \gamma_0 \cdot \left(1.0 + 0.5 \cdot \text{arousal} + 0.3 \cdot \text{dominance}\right)$$
- High Urgency + High Dominance $\rightarrow$ decisive, proactive stance (high $\gamma$, focused policy distribution).
- High Urgency + Low Dominance $\rightarrow$ defensive, anxious stance (lower $\gamma$, heightened sensitivity to risk, shifting toward `NARRATIVE_REFRAMING` or `HOMEOSTATIC_REST`).
- Low Urgency ($A < 0.15$) $\rightarrow$ low-intensity fast path: gates retrieval to lightweight top-K queries and skips System 2 deliberation overhead.

#### B. Continuous Hopfield Attractor Convergence
The incoming problem is fed into the persona's `ContinuousHopfieldNetwork`. The network relaxes through the energy landscape into the nearest `AttractorState`:
- An attractor represents a **Causal Mental Model / Dogma** (e.g., *"Data structures over algorithms"*, *"Contain the blast radius before fixing root cause"*, *"Never deploy without full test coverage"*).
- The converged attractor injects non-negotiable trade-off weights into the deliberation step.

#### C. Active Policy Inference (Expected Free Energy)
`PolicyInferenceEngine` evaluates candidate policies from `PolicyType`:
1. `PRAGMATIC_EXPLOITATION`: Direct problem-solving and immediate task execution.
2. `EPISTEMIC_EXPLORATION`: Deep investigation and associative search across sparse memory clusters.
3. `CLARIFYING_INTERACTION`: Active dialogue to reduce ambiguity when query entropy is high.
4. `PROCEDURAL_CRYSTALLIZATION`: Formalizing newly discovered solutions into reusable playbooks.
5. `NARRATIVE_REFRAMING`: Psychological defense/reappraisal when facing insolvable constraints.
6. `HOMEOSTATIC_REST`: Withdrawing/pausing to prevent error compounding under high allostatic load.

Each policy $\pi$ is scored on Expected Free Energy:
$$G(\pi) = \text{Pragmatic Risk (Goal Divergence)} + \text{Epistemic Ambiguity (Uncertainty)}$$
The winning policy determines the operational strategy.

#### D. System 2 Bounded Persona Deliberation
Once System 1 (AISME) has established the appraisal, the active Hopfield attractor, and the winning policy, the LLM is invoked in a bounded deliberation node:
```java
public record PersonaDeliberation(
    String internalMonologue,     // First-person private reasoning trace
    String activeDogma,           // The Hopfield mental model framing the response
    TradeOffSelection tradeOffs,  // Explicitly chosen trade-offs (e.g. speed vs quality)
    List<String> blindSpots,      // Self-acknowledged biases or risks
    String tacticalFirstMove      // Concrete, context-specific first action
) {}
```
This deliberation:
- Must adhere strictly to the active Hopfield dogma and winning `PolicyType`.
- Is stored in `enactment.deliberation` and made fully inspectable in Cortex.
- Bridges unconscious cognitive physics into rich, context-aware human reasoning.

### D4. Conflict Resolution Order (Normative Precedence)

When internal signals disagree, resolution proceeds in strict order:
1. **PEP / Legal Hold / Tenant Floor** (ADR-0029) — Hard non-negotiable security boundary.
2. **Hard Guardrails** (`AgentSoul.ethicalGuardrails` and ancestor soul constraints) — Absolute vetoes.
3. **Hopfield Attractor Basins & High-Confidence Procedural Playbooks ($N \ge 3$)** — Deeply ingrained mental models and habits.
4. **PolicyInferenceEngine EFE Boltzmann Selection** — Mathematical minimization of risk and ambiguity.
5. **Episodic Precedent Majority** — Analogous waking lived experiences above importance floor.
6. **Written Soul Constitution** (`coreValues`, `personality`, `purpose`).
7. **Base Model Inferred Prior** — Allowed only as `INFERRED` confidence with mandatory epistemic hedging; never labeled as the persona.

### D5. Fail-Closed Action Gating
Before any tool is executed in `decide` mode:
- The tool must belong to `intended_acts` authorized by the winning `CognitivePolicy`.
- The tool must belong to `AgentSoul.tools`.
- The tool must NOT match any condition in `stance.refuse[]` or `ethicalGuardrails`.
- Read-only tools are permitted in `react` mode only if required by an `EPISTEMIC_EXPLORATION` or `CLARIFYING_INTERACTION` policy.
- `simulate` and `replay` modes cannot reach side-effecting tools under any circumstances.
- Existing Synapse `approval` framework is the sole legal mechanism to override a gate refusal.

### D6. Embodiment through Express under Epistemic Tense

Verbalization is handled by the `Express` subsystem (ADR-0031 Part E):

| Mode | `ExpressTense` | Memory Filter | Verbalization Rule |
|---|---|---|---|
| `react`, `decide` | `FACT` | Waking episodic, semantic, procedural; suppress synthetic/dreamed/future | Direct first-person autobiography; no hedges unless confidence is `INFERRED`. |
| `simulate` | `SIM` | May include dreamed/wandered seeds | Explicit hypothetical framing ("If I were facing this, my instinct would be..."). |
| `replay` | `REPLAY` | Spacetime search restricted to state as of $\tau$ | Historical framing ("As of version X, my stance was..."). |

Tone is modulated by `AgentSoul.communicationStyle` and the active VAD `InteroceptiveState`:
- High arousal / low dominance $\rightarrow$ concise, guarded syntax.
- High dominance / positive valence $\rightarrow$ expansive, authoritative syntax.
Tone modulation affects style only; it cannot alter facts or bypass stance invariants.

### D7. Episodic Learning & Sleep Consolidation

#### Turn-by-Turn Lived Writes (Hot Path)
After a waking `react` or `decide` turn that executed:
1. `EnactmentLearner` writes an Episodic trace tagged `lived`, `reaction`, `enactment`:
   $$\text{Trace} = \{\text{problem}, \text{appraisal}, \text{attractor}, \text{deliberation}, \text{act}, \text{outcome}\}$$
2. `HomeostaticCore` steps its SDE, recording residual interoceptive state into Working memory.
3. `MentalStateTracker` updates its continuous posterior $q(s_t)$.

#### Slow Consolidation (Offline / Sleep Pathway)
- **Procedural Crystallization:** When an action pattern is observed $\ge N$ times ($N=3$ default) with positive outcome valence, it is compiled into a Procedural playbook tagged `habit`, `playbook`.
- **Attractor Deepening:** Repeated successful lived traces adjust the energy basin depths of the `ContinuousHopfieldNetwork`.
- **Soul Document Versioning:** `AgentSoul` and `UserSoul` documents mutate **only** during consolidation. Single-turn updates cannot alter `soulVersion`.
- **Strict Prohibition:** Dreamed (`SIM`) or counterfactual traces are NEVER tagged `lived` and cannot crystallize into `FACT` procedures without subsequent waking confirmation.

---

## 5. Architecture

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                           CORTEX                            │
                    │   VAD gauge · Hopfield Attractor · EFE Policy Distribution  │
                    │   Internal Monologue · Citations · Epistemic Tense Badge    │
                    └─────────────────────────────▲───────────────────────────────┘
                                                  │ enactment telemetry & audit
┌──────────── Synapse ────────────────────────────┼───────────────────────────────┐
│  MCP persona_enact / Companion Chat / FlowSpec  │                               │
│                                                                                 │
│   perceive → self_recall → appraise → infer_policy → deliberate → gate → act    │
│                 │              │            │             │        │     │      │
│                 │              │            │      Bounded LLM     │  Tools     │
│                 │              │            │      Deliberation    │     │      │
│                 │              │            ▼                      ▼     │      │
│                 │              │     PolicyInferenceEngine     Approval  │      │
│                 │              │     (EFE G(π) Boltzmann)      Override  │      │
│                 │              │                                         │      │
│                 │              ▼                                         ▼      │
│                 │       HomeostaticCore                              Express    │
│                 │       & EmotionalRegulator                         (ADR-0031) │
│                 │       (VAD SDE Dynamics)                               │      │
│                 │                                                        ▼      │
│                 │                                                  learn (D7)   │
└─────────────────┼────────────────────────────────────────────────────────┼──────┘
                  │                                                        │
                  ▼                                                        ▼
┌──────────── Memory (AISME) ─────────────────────┐          IdentityPlane (ADR-0029)
│ SpectorMemory (Bound Namespace)                 │          Soul Stack + PEP
│  • 4-tier PersonaRecall profile                 │
│  • ContinuousHopfieldNetwork (Dogma Attractors) │
│  • GenerativeSelfModel & MentalStateTracker     │
│  • GlobalWorkspace (Conscious Access Bottleneck)│
│  • NarrativeSelfEngine (Autobiographical Prior) │
│  • Off-heap AVX-512 EFE Kernel (spector-core)   │
└─────────────────────────────────────────────────┘
```

---

## 6. Testable Invariants

A pull request or implementation that violates any invariant is rejected:

- **I1. Strict Namespace Isolation.** Enactment in namespace $A$ must never cite or be influenced by memory IDs from namespace $B$.
- **I2. Epistemic Tense Integrity.** FACT mode stances cannot cite records flagged `synthetic`, `dreamed`, or `simulated`.
- **I3. Cognitive Fidelity (Moves over Adjectives).** Given identical problems and the same base LLM stub, two personas with differing Hopfield attractors and appraisal profiles must produce distinct `CognitivePolicy` selections and distinct tactical first moves. Tone-only differences fail the fixture.
- **I4. Fail-Closed Tool Gate.** No tool outside `intended_acts` or matching `refuse[]` can execute without an explicit, cryptographically verifiable `ApprovalRecord`.
- **I5. Thin Soul Honesty.** An uninitialized or sparse namespace must evaluate to `confidence = INFERRED`, disable side-effecting tools by default, and mandate epistemic hedging in verbalization.
- **I6. Anti-Impersonation Floor.** `DEFAULT_FALLBACK_SOUL` cannot be emitted under a named person's identity.
- **I7. Slow Soul Drift.** A single conversational turn must never increment `soulVersion` or directly mutate soul core values.
- **I8. Auditability.** Every `persona_enact` invocation writes an ADR-0028 recall audit record containing `acting_soul_id`, `mode`, `tense`, `appraisal`, `active_attractor`, `selected_policy`, and cited memory IDs.
- **I9. Layering Purity.** `spector-memory` must never depend on Synapse graph classes. AISME types in `memory.aisme` remain pure records and mathematical engines; Synapse orchestrates enactment.
- **I10. Sub-Millisecond System 1 Envelope.** Associative recall, Hopfield relaxation, VAD appraisal update, and EFE policy scoring must execute within the off-heap SIMD hybrid recall budget ($< 15$ ms p95).

---

## 7. Implementation Plan

### Phase 0 — Spec & Domain Contracts Lock
- Finalize `CognitiveAppraisal`, `PersonaDeliberation`, and `Enactment` records.
- Standardize reserved Bloom tags (`dogma`, `scar`, `playbook`, `habit`).
- Build unit test fixtures for Invariants I1–I6 using a stubbed LLM.

### Phase 1 — Memory & AISME Integration (System 1)
- Wire `PersonaRecall` profile into `SpectorMemory` with `GlobalWorkspace` conscious bottleneck.
- Connect `ContinuousHopfieldNetwork` to retrieve dominant `AttractorState` for a query.
- Wire `HomeostaticCore` to compute `CognitiveAppraisal` and update dynamic policy precision $\gamma$.
- Wire `PolicyInferenceEngine` to rank candidate `PolicyType`s using Expected Free Energy $G(\pi)$.
- Expose `persona_enact` MCP tool in `simulate` and `react` modes.

### Phase 2 — System 2 Bounded Deliberation & Gating
- Implement Synapse `DeliberationNode` generating structured `PersonaDeliberation`.
- Implement fail-closed `ToolGate` intersecting winning policy actions with `AgentSoul.tools`.
- Integrate Synapse `approval` package for gate overrides.
- Enable `decide` mode.

### Phase 3 — Lived Learning & Sleep Consolidation
- Implement `EnactmentLearner` writing episodic `lived` traces.
- Connect sleep reflection daemon (`ReflectPathway`) to consolidate playbooks at $N \ge 3$ and deepen Hopfield attractors.
- Ensure `ConversationReflector` (facts about user) and `EnactmentLearner` (facts about persona behavior) operate without cross-talk.

### Phase 4 — Express & Cortex Observability
- Route embodied utterances through `Express` with strict `ExpressTense` enforcement.
- Build Cortex Enactment Dashboard: VAD emotional gauge, active Hopfield dogma card, EFE policy distribution chart, internal monologue drawer, and memory citations.
- Implement `replay` mode via spacetime search (`as_of`).

### Phase 5 — Empirical Benchmark & Evaluation
- Benchmark cognitive fidelity against **TwinVoice** and **PersonaGym** protocols.
- Run LongMemEval with persona isolation enabled to confirm zero regression on recall accuracy.
- Verify p95 latency against baseline chat.

---

## 8. Summary of Normative Choices

| ID | Choice | Rationale |
|---|---|---|
| **D1** | Soul is policy over memory, powered by AISME | Moves beyond prompt adjectives into grounded computational identity. |
| **D2** | 4-cue self-recall via Global Workspace | Biologically grounded conscious access bottleneck preventing context pollution. |
| **D3** | Cognitive appraisal + Hopfield attractors + EFE selection | Replaces brittle 8-item enums with continuous affective dynamics, causal mental models, and active inference. |
| **D4** | Strict conflict precedence hierarchy | PEP and guardrails always outrank lived case law; lived case law outranks written text. |
| **D5** | Fail-closed tool gate before LLM execution | Guarantees safety and prevents rogue tool execution. |
| **D6** | Verbalization via Express with epistemic tense | Enforces truth in advertising: waking `FACT` vs hypothetical `SIM` vs historical `REPLAY`. |
| **D7** | Lived episodic writes with slow sleep consolidation | Prevents single-turn personality drift while allowing cumulative behavioral growth. |
| **D8** | Explicit CognitiveState channels | Eliminates hidden prompt soup; guarantees full auditability in Cortex. |
| **D9** | Synapse graph node + MCP tool + Cortex panel | Native integration across all Spector interaction layers without module sprawl. |
| **D10** | Compose AISME organs rather than duplicating them | Leverages existing, tested off-heap SIMD kernels in `spector-core` and `spector-memory`. |

---

*Authored by:* **Technical Lead** (CTO) & **@titan** (Solutions Architect)  
*Cognitive Architecture Review by:* **@neuron** (Chief Cognitive Scientist)  
*Approved by:* **Bharat** (CEO)
