# ADR-0051: Bayesian Online Change-Point Episode Segmentation

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

In cognitive architectures and long-running autonomous agents, human-agent dialogue and interaction streams are continuous. Breaking this continuous sensory stream into discrete, cohesive episodic memories requires an objective segmentation mechanism.

### Neurocognitive Foundations: Event Segmentation Theory (EST)

Under **Event Segmentation Theory** (EST; Kurby & Zacks, 2008; Radvansky & Zacks, 2014), cognitive perception continually constructs mental event models. When the current model fails to predict ongoing sensory observations (indicated by an informational spike in predictive coding surprisal or a sudden distribution shift), the human hippocampus and prefrontal cortex:
1. Fire an **event boundary cut**.
2. Flush and consolidate the transient event buffer into an autobiographical episodic memory chunk.
3. Reset working generative predictions for the new narrative context.

---

## 2. Problem Statement

Standard approaches to conversational episode boundaries suffer from severe practical limitations:
1. **Arbitrary Fixed Chunking**: Segmenting interactions by fixed turn counts (e.g. every 10 turns) slices coherent discussions in half or merges distinct topics into incoherent single episodes.
2. **Offline Latency**: Retrospective batch clustering (e.g., agglomerative clustering across a full day's logs) cannot establish real-time episodic boundaries needed during live recall.
3. **Semantic Drift**: Unbounded episodes degrade vector embedding resolution, as averaging diverse embeddings produces diluted centroid representations.

## 3. Decision Drivers

- **Online Incremental Execution**: Detect event boundaries in real time on streaming dialogue turns without requiring backward passes over past history.
- **Cognitive Grounding**: Align with Event Segmentation Theory (Kurby & Zacks) and Bayesian Online Change-Point Detection (Adams & MacKay).
- **Dual-Gated Precision**: Combine posterior run-length probability shifts with sensory predictive surprisal to eliminate false positives.
- **Bounded Computation**: Cap active run-length hypotheses to a fixed maximum window ($N \le 50$) to ensure strictly deterministic memory and compute bounds.

## 4. Considered Options

### Option 1: Fixed-Window Chunking (e.g., Every N Dialogue Turns)
- Cut episodes strictly every $N$ turns or when idle timeouts occur.
- **Verdict**: Rejected. Slices cohesive semantic dialogues mid-thought and obscures episodic narrative structure.

### Option 2: Heavy LLM Prompt-Based Segmentation
- Prompt an external LLM after every turn to decide if an episode ended.
- **Verdict**: Rejected. Incurs prohibitive token costs, network round-trip latencies (>500ms), and unpredictable nondeterminism.

### Option 3: Bayesian Online Change-Point Detection (BOCPD) with Dual-Criteria Surprisal Gating (Selected)
- Execute a recursive Bayesian update of the run-length distribution using Gaussian hazard functions in off-heap math kernels.
- Trigger boundary consolidation when change-point probability crosses threshold or predictive surprisal spikes.
- **Verdict**: Accepted. Achieves sub-millisecond real-time boundary cuts with high semantic cohesion.

## 5. Decision Outcome

### Mathematical Formulations & Recursive Update

### 2.1 Bayesian Online Change-Point Detection (BOCPD)
Let $x_1, x_2, \dots, x_t$ be a sequence of sensory observation embeddings partitioned into contiguous segments by change points. Let $r_t \in \{0, 1, \dots, r_{\text{max}}\}$ denote the **run length** (the time elapsed since the most recent change point).

The recursive message-passing formulation (Adams & MacKay, 2007) computes the joint distribution $P(r_t, x_{1:t})$:

$$P(r_t = 0, x_{1:t}) = \sum_{r_{t-1}} P(r_{t-1}, x_{1:t-1}) \cdot \pi(x_t \mid \theta_{r_{t-1}}) \cdot H(r_{t-1})$$

$$P(r_t = r_{t-1} + 1, x_{1:t}) = P(r_{t-1}, x_{1:t-1}) \cdot \pi(x_t \mid \theta_{r_{t-1}}) \cdot (1 - H(r_{t-1}))$$

where:
- $H(r) = \frac{1}{\lambda_{\text{hazard}}}$ is the constant hazard function with expected segment length $\lambda_{\text{hazard}}$.
- $\pi(x_t \mid \theta_r)$ is the predictive probability under conjugate Gaussian hyperparameters.
- The change-point posterior probability at step $t$ is given by:

$$P(r_t = 0 \mid x_{1:t}) = \frac{P(r_t = 0, x_{1:t})}{\sum_{r_t} P(r_t, x_{1:t})}$$

---

### 2.2 Dual-Criteria Surprisal Boundary Predicate
To ensure robust segmentation under both gradual narrative shifts (tracked by BOCPD) and abrupt sensory shocks (tracked by predictive coding surprisal), the boundary decision rule is defined as:

$$\text{IsBoundary}(o_t) = \left( P(r_t = 0 \mid x_{1:t}) \ge \tau_{\text{bocpd}} \right) \lor \left( S(o_t) \ge \tau_{\text{surprisal\_cut}} \right) \lor \left( N_{\text{buffered}} \ge N_{\text{max\_frames}} \right)$$

where:
- $\tau_{\text{bocpd}} = 0.65$ (default change-point threshold)
- $\tau_{\text{surprisal\_cut}} = 1.50$ (default surprisal threshold)
- $N_{\text{max\_frames}} = 200$ (maximum frame timeout)

---

### Centroid Embedding & Episodic Segment Assembly

Upon boundary trigger, the buffered observation vectors $\{\boldsymbol{o}_1, \dots, \boldsymbol{o}_K\}$ are packaged into an immutable `EpisodicSegment`:

$$\bar{\boldsymbol{e}} = \frac{1}{K} \sum_{i=1}^K \boldsymbol{o}_i$$

The segment is decorated with start timestamp $t_1$, end timestamp $t_K$, frame count $K$, peak surprisal $\max_i S(\boldsymbol{o}_i)$, and the specific `BoundaryReason`.

## 6. Pros and Cons of the Options

### Positive
- **High Cohesion**: Automatically identifies natural topic transitions, task completions, and context shifts.
- **Sub-Millisecond Speed**: Bounded BOCPD evaluates in <50us in Java off-heap memory, enabling per-turn execution.
- **Enhanced Vector Recall**: Consolidated episodes have clean, coherent centroid embeddings that dramatically improve retrieval precision.

### Negative / Trade-offs
- **Prior Calibration**: Requires tuning prior hyperparameters for the target embedding space.
- **Truncation Pruning**: Keeping run-length tracking bounded requires pruning low-probability hypotheses at each step.

## 7. Implementation Plan

1. **Core Kernel**: Implement `BocpdKernel` in `nucleus/spector-core` supporting online recursive Gaussian updates and hazard probability calculations.
2. **Segmentation Service**: Implement `BayesianOnlineChangePointDetector` in `memory/spector-memory` managing observation buffering and boundary triggering.
3. **Integration**: Wire the detector into `RememberPathway` to trigger automatic episode consolidation when boundary predicates fire.
4. **Validation**: Author comprehensive unit and property-based test suites (`BocpdKernelTest`).

## 8. Code Reference & Verification

All mathematical kernels and segmentation controllers are verified in the repository:
- **Mathematical Kernel**:
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/cognitive/BocpdKernel.java`
  - `nucleus/spector-core/src/test/java/com/spectrayan/spector/core/similarity/BocpdKernelTest.java`
- **Memory Segmentation Engine**:
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/segmentation/BayesianOnlineChangePointDetector.java`
