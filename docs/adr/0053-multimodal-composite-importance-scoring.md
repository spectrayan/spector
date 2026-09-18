# ADR-0053: Multimodal Composite Importance Scoring

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-23 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---


## 1. Context

In cognitive architectures and autobiographical memory systems, not all experiences warrant equal long-term storage investment. Biological brains rely on neuromodulatory systems (dopamine, norepinephrine, acetylcholine) to prioritize emotionally intense, surprising, or goal-critical events for rapid consolidation.

### Executive Summary & Biological Analog

Biological episodic memory consolidation is not a photographic recording of all sensory events; rather, it is a ruthlessly selective filtering process modulated by neuromodulators (dopamine, norepinephrine, acetylcholine) and limbic-prefrontal circuits.

In the human brain:

1. **Amygdala-Basolateral Circuitry** computes emotional arousal \(a(o_t)\) and valence \(v(o_t)\), triggering synaptic tagging and capture (STC) for emotionally charged events (Cahill & McGaugh, 1998; McGaugh, 2004).
2. **Hippocampal Dentate Gyrus & CA1** compute prediction error surprisal \(S(o_t) = -\ln P(o_t \mid \mu_t)\) and representational novelty against established cognitive maps (Kumaran & Maguire, 2006).
3. **Dorsolateral & Ventromedial PFC** continuously evaluate prospective goal relevance \(\text{GoalRel}(o_t)\) and intentional congruence (Miller & Cohen, 2001).
4. **Anterior Cingulate Cortex (ACC) & Insula** evaluate social context, interpersonal hierarchy, and agentic commitment significance (Frith & Frith, 2006).

This paper formalizes the unified computational architecture for **Composite Importance Scoring \(I(o_t)\)** in Spector's Active Inference Self-Model Engine (AISME).

---

## 2. Problem Statement

Standard conversational memory frameworks assign flat or simplistic heuristic importance scores (e.g. word counts, entity counts, or naive turn order):

1. **Monolithic Scoring Bias**: Relying on a single heuristic fails to capture complex interactions between emotional valence, surprise, goal relevance, and social cues.
2. **Flashbulb Memory Omission**: Highly consequential events (e.g. safety emergencies, user corrections, commitments) must be permanently retained with near-zero decay, whereas routine small talk should fade rapidly.
3. **Computational Bottlenecks**: Computing five multi-modal cognitive metrics sequentially on the JVM heap degrades real-time conversational latency.

## 3. Decision Drivers

- **Multimodal Signal Fusion**: Integrate 5 distinct cognitive signals: epistemic surprise, affective resonance, goal relevance, social context significance, and epistemic novelty.
- **Dynamic Profile Adaptability**: Support persona- and soul-conditioned weighting vectors so analytical personas prioritize epistemic surprise while empathetic personas prioritize affective resonance.
- **Hardware Acceleration**: Implement vectorized SIMD dot products using Java Panama Vector API to evaluate importance fusion in $<50\mu s$.
- **Nonlinear Flashbulb Gating**: Implement a biological flashbulb gating function ensuring events exceeding critical salience thresholds bypass normal forgetting curves.

## 4. Considered Options

### Option 1: Single Scalar Heuristic (e.g., TF-IDF / Length Weighting)
- Score memory importance based on lexical rarity or sentence length.
- **Verdict**: Rejected. Incapable of distinguishing critical emotional commitments from verbose filler text.

### Option 2: Synchronous LLM Importance Evaluator
- Call an external LLM on every turn to output an integer score (1-10).
- **Verdict**: Rejected. Introduces 300–800ms latency, high cost, and severe variance.

### Option 3: Vectorized Multimodal Composite Importance Fusion (Selected)
- Synthesize 5 orthogonal normalized signal components into a composite score using vectorized dot-product weighting and nonlinear flashbulb gating.
- Implement in `nucleus/spector-core` and bridge to `spector-memory` via `CompositeImportanceScorer`.
- **Verdict**: Accepted. Combines cognitive fidelity with sub-millisecond execution.

## 5. Decision Outcome

### Mathematical Formulation & Five-Signal Fusion

### 2.1 Component Signal Vector \(\boldsymbol{s}(o_t)\)

For every incoming sensory observation frame \(o_t\), we construct a 5-dimensional normalized signal vector:

\[
\boldsymbol{s}(o_t) = \begin{bmatrix}
s_1(o_t) \\
s_2(o_t) \\
s_3(o_t) \\
s_4(o_t) \\
s_5(o_t)
\end{bmatrix} = \begin{bmatrix}
\text{Surprise}(o_t) \\
\text{Affect}(o_t) \\
\text{GoalRelevance}(o_t) \\
\text{SocialContext}(o_t) \\
\text{Novelty}(o_t)
\end{bmatrix} \in [0.0, 1.0]^5
\]

#### 1. Epistemic Surprise \(s_1(o_t)\)
Derived from free energy predictive coding:
\[
s_1(o_t) = \min\left(1.0, \frac{\|\boldsymbol{o}_t - \boldsymbol{\mu}_t\|_2^2}{2 \sigma_{\text{surprisal}}^2}\right)
\]

#### 2. Affective Resonance \(s_2(o_t)\)
Derived from homeostatic state deviation:
\[
s_2(o_t) = |v(o_t)| \cdot a(o_t)
\]
where \(v(o_t) \in [-1.0, 1.0]\) is the valence deviation from baseline, and \(a(o_t) \in [0.0, 1.0]\) is physiological/cognitive arousal.

#### 3. Prospective Goal Relevance \(s_3(o_t)\)
Derived from semantic alignment with active intentions \(\mathcal{G} = \{\boldsymbol{g}_1, \dots, \boldsymbol{g}_m\}\):
\[
s_3(o_t) = \max_{j} \max\left(0.0, \frac{\boldsymbol{v}(o_t) \cdot \boldsymbol{g}_j}{\|\boldsymbol{v}(o_t)\|_2 \|\boldsymbol{g}_j\|_2}\right)
\]

#### 4. Social Context Significance \(s_4(o_t)\)
Derived from interlocutor identity, direct mentions, emotional disclosure, and conversational commitment tags:
\[
s_4(o_t) = \min\left(1.0, w_{\text{speaker}} + w_{\text{mention}} + w_{\text{commitment}}\right)
\]

#### 5. Epistemic Novelty \(s_5(o_t)\)
Derived from distance to nearest established semantic cluster centroid:
\[
s_5(o_t) = \text{clamp}\left(1.0 - \max_k \text{cosine}(\boldsymbol{v}(o_t), \boldsymbol{c}_k), 0.0, 1.0\right)
\]

---

### 2.2 Composite Importance Fusion & Profile Weighting

The composite score \(I(o_t)\) is computed via linear inner product with profile weight vector \(\boldsymbol{w}\):

\[
I(o_t) = \sum_{i=1}^{5} w_i \cdot s_i(o_t), \quad \text{subject to } \sum_{i=1}^5 w_i = 1.0, \ w_i \ge 0
\]

```
                  [ Surprise s1 ] --------* w1 ---\
                  [ Affect   s2 ] --------* w2 ----\
 Observation ---> [ GoalRel  s3 ] --------* w3 -----> (+) ---> I(o_t) in [0, 1]
                  [ Social   s4 ] --------* w4 ----/
                  [ Novelty  s5 ] --------* w5 ---/
```

### 2.3 Flashbulb Memory Gating
If \(I(o_t) \ge \theta_{\text{flashbulb}}\) (default \(0.85\)):
- Signal is tagged with `flashbulb = true`.
- Synaptic consolidation bypasses standard decay queues and writes immediately to permanent episodic tiers.

---

### SIMD Acceleration & Memory Architecture

Evaluating \(I(o_t)\) on the sensory ingestion hot path demands strictly sub-microsecond latency. Using Java 21 Vector API:
```java
public static float computeImportance(float[] signals, float[] weights) {
    var species = FloatVector.SPECIES_PREFERRED;
    var vSig = FloatVector.fromArray(species, signals, 0);
    var vWeight = FloatVector.fromArray(species, weights, 0);
    float dot = vSig.mul(vWeight).reduceLanes(VectorOperators.ADD);
    return Math.clamp(dot, 0.0f, 1.0f);
}
```

---

## 6. Pros and Cons of the Options

### Positive
- **High Cognitive Precision**: Multi-axis evaluation ensures critical interactions are accurately identified and preserved.
- **Sub-50us Evaluation**: Panama Vector API implementation provides near-instantaneous dot-product calculations off-heap.
- **Soul Customization**: Adapts smoothly to different agent personalities and domain requirements.

### Negative / Trade-offs
- **Signal Coordination**: Upstream pathways must supply calibrated input features (affective VAD scores, surprise values, goal matches).
- **SIMD Architecture Dependency**: Requires fallback paths when running on hardware architectures without vector acceleration.

## 7. Implementation Plan

### Verification Protocol & Test Suites

The implementation must pass:

1. **Analytical Kernel Tests**: Exact mathematical verification for orthogonal and blended vectors.
2. **Profile Adaptation Tests**: Proper dynamic weight reassignment across all 5 cognitive profiles.
3. **Multi-Scenario Ingestion Simulation**: 1,000-signal benchmark simulating high-affect emotional disclosures, technical goal executions, and routine background chatter, validating distinct separation in $I(o_t)$ distribution.

## 8. Code Reference & Verification

All importance kernels, signal models, and relays are verified in the codebase:
- **Core Math Kernel**:
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/cognitive/CompositeImportanceKernel.java`
  - `nucleus/spector-core/src/test/java/com/spectrayan/spector/core/similarity/CompositeImportanceKernelTest.java`
- **Memory Importance Models & Scorer**:
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/importance/CompositeImportanceSignals.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/importance/CompositeImportanceScorer.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/relay/CompositeImportanceRelay.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/model/ImportanceEstimate.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/model/ImportanceBreakdown.java`
