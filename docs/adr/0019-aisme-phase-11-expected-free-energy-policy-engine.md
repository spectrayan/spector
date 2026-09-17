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

**Context**: Active Inference Self-Model Engine Phase 11 (Expected Free Energy Policy Selection & Action Loop)  
**Module**: `spector-core`, `spector-memory`, `spector-config`, `spector-synapse`  

---

## 1. Context & Problem Statement

Phases 1–10 of the Active Inference Self-Model Engine (AISME) established the mathematical, biological, and off-heap kernel substrate for:
1. **Perceptual Inference**: Minimizing variational free energy \(\mathcal{F}(s, o)\) over internal hidden beliefs \(q(s)\) upon receiving queries and sensory observations.
2. **Epistemic & Affective Dynamic Learning**: Updating posterior states \(q(s_t)\) via precision-weighted Bayesian fusion and stepping linear neural ODEs for homeostatic interoception.
3. **Generative Simulation & Plasticity**: Constructive narrative generation and generative prior centroid drift during sleep consolidation.
4. **Default Mode Network (DMN) Mind-Wandering & Continuity**: Background spontaneous Hopfield associative search and zero-copy mmap \(\Phi_{CC}\) trajectory snapshots.

However, Active Inference is inherently a dual-process theory: **perceptual inference without policy inference is passive perception**. To achieve full cognitive agency, the system must evaluate prospective actions \(\pi \in \Pi\) and select the policy that minimizes **Expected Free Energy (\(G\))** over future time horizons.

---

## 2. Decision & Mathematical Architecture

### 2.1 Expected Free Energy (\(G\)) Formulation
For a discrete candidate policy \(\pi\) projecting into future time horizon \(\tau\), the Expected Free Energy \(G(\pi)\) is defined as:
\[
G(\pi) = \sum_{\tau > t} G(\pi, \tau)
\]
Where each prospective horizon \(G(\pi, \tau)\) decomposes into:
\[
G(\pi, \tau) = \underbrace{D_{\text{KL}}[q(o_\tau \mid \pi) \parallel p(o_\tau)]}_{\text{Pragmatic Value (Goal Risk)}} + \underbrace{\mathbb{E}_{q(s_\tau \mid \pi)}[\mathcal{H}(q(o_\tau \mid s_\tau, \pi))]}_{\text{Epistemic Value (Ambiguity / Uncertainty)}}
\]

Equivalently formulated as:
\[
G(\pi, \tau) = \underbrace{-\mathbb{E}_{q(o_\tau \mid \pi)}[\ln p(o_\tau)]}_{\text{Instrumental Loss (Preference Deviation)}} - \underbrace{\mathbb{E}_{q(o_\tau \mid \pi)}[D_{\text{KL}}[q(s_\tau \mid o_\tau, \pi) \parallel q(s_\tau \mid \pi)]]}_{\text{Epistemic Information Gain (Salience)}}
\]

- **Pragmatic Value**: Measures the degree to which predicted future observations \(q(o_\tau \mid \pi)\) diverge from the agent's prior preferences \(p(o_\tau)\). In Spector, \(p(o)\) is derived from `AgentSoul` core values, ethical guardrails, purpose vectors, and homeostatic setpoints.
- **Epistemic Value**: Measures the expected uncertainty reduction regarding hidden environmental states. Compels the agent to explore ambiguous, high-salience contexts (e.g. asking clarifying questions, retrieving deep biographical memories) when confidence is low.

---

### 2.2 Policy Taxonomy
We establish 6 canonical cognitive policy categories in `PolicyType`:
1. `EPISTEMIC_EXPLORATION`: Deep multi-partition memory retrieval and associative search across sparse or novel knowledge clusters.
2. `PRAGMATIC_EXPLOITATION`: Direct factual synthesis and goal-directed task execution when observation ambiguity is low.
3. `CLARIFYING_INTERACTION`: Active interrogation and dialogue disambiguation when query entropy exceeds confidence thresholds.
4. `PROCEDURAL_CRYSTALLIZATION`: Encoding and crystallizing reusable cognitive strategies and execution plans into procedural memory.
5. `HOMEOSTATIC_REST`: Dispatching sleep consolidation (`ReflectPathway`) or DMN wandering (`WanderPathway`) when allostatic load/fatigue is elevated.
6. `NARRATIVE_REFRAMING`: Aligning current conversational stance with autobiographical identity and long-term narrative themes.

---

### 2.3 Policy Selection & Precision Modulation
The probability of selecting policy \(\pi\) is computed via a Boltzmann distribution over negative Expected Free Energy:
\[
P(\pi) = \sigma(-\gamma \cdot G(\pi)) = \frac{\exp(-\gamma \cdot G(\pi))}{\sum_{\pi'} \exp(-\gamma \cdot G(\pi'))}
\]
Where \(\gamma \ge 0\) is the **policy precision** (action readiness), dynamically modulated by:
\[
\gamma = \gamma_0 \cdot \left(1.0 + \alpha_{\text{arousal}} \cdot \text{arousal} + \alpha_{\text{dominance}} \cdot \text{dominance}\right)
\]
sourced from `HomeostaticCore` / `InteroceptiveState`.

---

## 3. Implementation Blueprint

1. **`spector-core`**:
   - `ExpectedFreeEnergyKernel`: AVX-512 SIMD vectorized evaluation of Gaussian KL divergence, entropy, and multi-policy parallel loss computation.
2. **`spector-memory`**:
   - `CognitivePolicy` & `PolicyType`: Domain models representing candidate actions and transition dynamics.
   - `ExpectedFreeEnergyCalculator`: High-level evaluator computing pragmatic and epistemic scores against `AgentSoul` and `MentalStateTracker`.
   - `PolicyInferenceEngine`: Softmax policy ranking and decision telemetry generation.
   - `PolicyInferenceRelay`: Synaptic relay for pathway integration.
3. **`spector-config`**:
   - `AismeProperties` & `SpectorPropertyConstants`: Master toggle and precision/weight configuration keys.
4. **`spector-synapse`**:
   - Bridge policy decisions to agent execution graphs (`AgenticChatGraph`, `CoordinatorGraph`).

---

## 4. Consequences & Verification

- **Agency & Autonomy**: Spector agents transition from reactive retrieval to proactive decision-makers balancing exploration and exploitation.
- **Zero-Allocation SIMD Performance**: Vectorized EFE evaluations maintain sub-millisecond latency.
- **Verification Gate**: Unit tests in `spector-core` and `spector-memory`, multi-policy scenario benchmarks, and license validation.
