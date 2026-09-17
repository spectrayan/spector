# ADR-0012-RND: Bayesian Online Change-Point Episode Segmentation

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

**Target Repo**: `spectrayan/spector`  
**Issues**: spectrayan/spector#631  

---

## 1. Neurocognitive Foundations: Event Segmentation Theory (EST)

Under **Event Segmentation Theory** (EST; Kurby & Zacks, 2008; Radvansky & Zacks, 2014), cognitive perception continually constructs mental event models. When the current model fails to predict ongoing sensory observations (indicated by an informational spike in predictive coding surprisal or a sudden distribution shift), the human hippocampus and prefrontal cortex:
1. Fire an **event boundary cut**.
2. Flush and consolidate the transient event buffer into an autobiographical episodic memory chunk.
3. Reset working generative predictions for the new narrative context.

---

## 2. Mathematical Formulations

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

## 3. Centroid Embedding & Episodic Segment Assembly
Upon boundary trigger, the buffered observation vectors $\{\boldsymbol{o}_1, \dots, \boldsymbol{o}_K\}$ are packaged into an immutable `EpisodicSegment`:

$$\bar{\boldsymbol{e}} = \frac{1}{K} \sum_{i=1}^K \boldsymbol{o}_i$$

The segment is decorated with start timestamp $t_1$, end timestamp $t_K$, frame count $K$, peak surprisal $\max_i S(\boldsymbol{o}_i)$, and the specific `BoundaryReason`.
