# ADR-0019: AISME Phase 11 — Expected Free Energy Policy Engine

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

Phases 1–10 of the Active Inference Self-Model Engine (AISME) established the mathematical, biological, and off-heap kernel substrate for perceptual inference, homeostatic interoception, generative counterfactual simulation, DMN mind-wandering, and longitudinal continuity persistence. However, active inference is fundamentally a dual-process framework: perceptual inference without policy inference is passive perception. To attain proactive cognitive agency, Spector must evaluate prospective actions $\pi \in \Pi$ and select policies that minimize Expected Free Energy ($G$) over future time horizons.

## 2. Problem Statement

Prior to Phase 11, Spector's execution was entirely reactive: queries prompted memory retrieval and affective updates, but the system had no principled mechanism to autonomously balance epistemic exploration (gathering information, clarifying ambiguities) against pragmatic exploitation (executing goals, retrieving factual answers). Hardcoded heuristics or standard reinforcement learning lacked the biological grounding, epistemic drive, and sub-millisecond mathematical tractability required for sovereign AI agents.

## 3. Decision Drivers

- **Formal Dual-Value Decomposition**: Expected Free Energy ($G$) must rigorously balance pragmatic value (goal achievement) and epistemic value (information gain).
- **Sub-Millisecond Vectorized Evaluation**: Evaluating candidate policies against high-dimensional beliefs must leverage SIMD parallelism without on-heap GC allocation.
- **Dynamic Affective Precision Modulation**: Policy selection confidence ($\gamma$) must dynamically reflect interoceptive arousal and dominance states.
- **Composable Cognitive Action Taxonomy**: Provide structured, extensible policy categories covering retrieval, clarification, execution, reflection, and crystallization.

## 4. Considered Options

### Option 1: Heuristic Hardcoded Rule Engine

- **Description**: Use conditional thresholding over confidence scores to select exploratory vs exploitative behaviors.
- **Advantages**: Simple to understand and quick to implement.
- **Disadvantages**: Highly brittle; fails in edge cases; lacks unified probabilistic decision foundations.

### Option 2: Model-Free Reinforcement Learning (Q-Learning / PPO)

- **Description**: Train policy value networks via external rewards.
- **Advantages**: Standard machine learning formulation.
- **Disadvantages**: Requires massive offline training data; lacks intrinsic epistemic motivation; introduces unpredictable neural black-box decision points.

### Option 3: Variational Active Inference with SIMD Expected Free Energy (Selected)

- **Description**: Vectorized evaluation of Expected Free Energy ($G$) decomposing into KL divergence (pragmatic risk) and conditional entropy (epistemic uncertainty), combined with Boltzmann distribution policy selection.
- **Advantages**: Mathematically unified; zero training required; intrinsic motivation to resolve ambiguity; AVX-512 SIMD accelerated.
- **Disadvantages**: Requires careful calibration of prior preference distributions.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Variational Active Inference with SIMD Expected Free Energy).

### Mathematical Architecture:

#### 1. Expected Free Energy ($G$) Formulation
For a discrete candidate policy $\pi$ projecting into future time horizon $\tau$, the Expected Free Energy $G(\pi)$ is defined as:
\[
G(\pi) = \sum_{\tau > t} G(\pi, \tau)
\]
Where each prospective horizon $G(\pi, \tau)$ decomposes into:
\[
G(\pi, \tau) = \underbrace{D_{\text{KL}}[q(o_\tau \mid \pi) \parallel p(o_\tau)]}_{\text{Pragmatic Value (Goal Risk)}} + \underbrace{\mathbb{E}_{q(s_\tau \mid \pi)}[\mathcal{H}(q(o_\tau \mid s_\tau, \pi))]}_{\text{Epistemic Value (Ambiguity / Uncertainty)}}
\]

Equivalently formulated as:
\[
G(\pi, \tau) = \underbrace{-\mathbb{E}_{q(o_\tau \mid \pi)}[\ln p(o_\tau)]}_{\text{Instrumental Loss (Preference Deviation)}} - \underbrace{\mathbb{E}_{q(o_\tau \mid \pi)}[D_{\text{KL}}[q(s_\tau \mid o_\tau, \pi) \parallel q(s_\tau \mid \pi)]]}_{\text{Epistemic Information Gain (Salience)}}
\]

- **Pragmatic Value**: Measures the degree to which predicted future observations $q(o_\tau \mid \pi)$ diverge from prior preferences $p(o_\tau)$, derived from `AgentSoul` core values, ethical guardrails, and homeostatic setpoints.
- **Epistemic Value**: Measures expected uncertainty reduction regarding hidden environmental states, driving exploratory interrogation or memory retrieval when ambiguity is elevated.

#### 2. Canonical Policy Taxonomy
We establish 6 canonical cognitive policy categories in `PolicyType`:

1. `EPISTEMIC_EXPLORATION`: Deep multi-partition memory retrieval and associative search across sparse or novel knowledge clusters.
2. `PRAGMATIC_EXPLOITATION`: Direct factual synthesis and goal-directed task execution when observation ambiguity is low.
3. `CLARIFYING_INTERACTION`: Active interrogation and dialogue disambiguation when query entropy exceeds confidence thresholds.
4. `PROCEDURAL_CRYSTALLIZATION`: Encoding and crystallizing reusable cognitive strategies and execution plans into procedural memory.
5. `HOMEOSTATIC_REST`: Dispatching sleep consolidation (`ReflectPathway`) or DMN wandering (`WanderPathway`) when allostatic load/fatigue is elevated.
6. `NARRATIVE_REFRAMING`: Aligning current conversational stance with autobiographical identity and long-term narrative themes.

#### 3. Policy Selection & Precision Modulation
The probability of selecting policy $\pi$ is computed via a Boltzmann distribution over negative Expected Free Energy:
\[
P(\pi) = \sigma(-\gamma \cdot G(\pi)) = \frac{\exp(-\gamma \cdot G(\pi))}{\sum_{\pi'} \exp(-\gamma \cdot G(\pi'))}
\]
Where $\gamma \ge 0$ is the policy precision (action readiness), dynamically modulated by:
\[
\gamma = \gamma_0 \cdot \left(1.0 + \alpha_{\text{arousal}} \cdot \text{arousal} + \alpha_{\text{dominance}} \cdot \text{dominance}\right)
\]
sourced from `HomeostaticCore` / `InteroceptiveState`.

### Positive Consequences

- Transforms Spector agents from passive memory stores into autonomous active-inference agents.
- Intrinsic drive to clarify ambiguities before taking high-risk actions.
- AVX-512 SIMD acceleration guarantees sub-millisecond policy inference latency.

### Negative Consequences & Trade-offs

- Setting accurate prior preferences requires well-defined `AgentSoul` configuration profiles.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Rule Engine** | Simple logic | Brittle, lacks mathematical basis, fails on edge cases |
| **Option 2: Model-Free RL** | Standard ML framework | Training data dependency, black-box decisions, no intrinsic curiosity |
| **Option 3: Active Inference** | Intrinsic epistemic motivation, zero-training, SIMD-accelerated | Requires calibrated prior preference vectors |

## 7. Implementation Plan

1. **Phase 1**: Implement `ExpectedFreeEnergyKernel` in `nucleus/spector-core` using Panama Vector API.
2. **Phase 2**: Implement `CognitivePolicy`, `PolicyType`, and `ExpectedFreeEnergyCalculator` in `memory/spector-memory/aisme/policy`.
3. **Phase 3**: Implement `PolicyInferenceEngine` and `PolicyInferenceRelay`.
4. **Phase 4**: Connect policy decisions to `spector-synapse` orchestration graphs (`AgenticChatGraph`).

## 8. Code Reference & Verification

- **Primary Module(s)**: `nucleus/spector-core`, `memory/spector-memory`, `nucleus/spector-config`, `synapse/spector-synapse`
- **Key Packages**: `com.spectrayan.spector.core.cognitive`, `com.spectrayan.spector.memory.aisme.policy`, `com.spectrayan.spector.memory.aisme.relay`
- **Classes**: `ExpectedFreeEnergyKernel.java`, `CognitivePolicy.java`, `PolicyType.java`, `ExpectedFreeEnergyCalculator.java`, `PolicyInferenceEngine.java`, `PolicyInferenceRelay.java`
- **Verification Tests**: `ExpectedFreeEnergyKernelTest.java`, `PolicyInferenceEngineTest.java`
