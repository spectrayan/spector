# Scoring Regimes & Fusion Modes

Spector Memory evaluates candidate memories through two primary scoring regimes: the **Generic Scoring Regime** and the **Soul-Conditioned Scoring Regime (FERS)**.

---

## 1. Generic Scoring Regime

The Generic regime operates on standard vector similarity and metadata features when no `SoulContext` is provided or when AISME is disabled.

### 1.1 Score Fusion Modes

Spector supports two fusion modes configured via `RecallOptions` (`ScoreFusionMode`) or `spector.memory.recall.score-fusion-mode`:

#### `MULTIPLICATIVE` (Default)
Preserves backwards-compatible composite scoring where semantic tag overlap acts as a multiplicative boost:

$$\text{FinalScore} = \text{BaseScore} \cdot (1.0 + \text{tagOverlap} \cdot \text{tagRelevanceBoost})$$

Where $\text{BaseScore} = \text{Similarity} \cdot \text{ImpDecayFactor} \cdot \text{ValenceMultiplier}$.

#### `ADDITIVE`
Applies linear convex combination governed by the live parameter $\alpha \in [0.0, 1.0]$:

$$\text{BaseSimilarity} = \alpha \cdot \text{Similarity} + (1.0 - \alpha) \cdot \text{TagOverlap}$$

$$\text{FinalScore} = \text{BaseSimilarity} \cdot \text{ImpDecayFactor} \cdot \text{ValenceMultiplier}$$

> [!NOTE]
> In `MULTIPLICATIVE` mode, $\alpha$ is inert. In `ADDITIVE` mode, $\alpha$ actively controls the balance between vector semantic similarity and symbolic Bloom filter tag overlap.

### 1.2 Early Graph Associative Prior (Phase 6 Fusion)

When enabled (`enableAssociativePrior = true`), an $O(1)$ Hebbian and co-activation associative prior $A_g \in [0.0, 1.0]$ modulates candidates in Phase 6 fusion:

- **Multiplicative:** $\text{Score} = \text{Score}_{\text{base}} \cdot (1.0 + \delta \cdot A_g)$
- **Additive:** $\text{Score} = \text{Score}_{\text{base}} + \delta \cdot A_g$

> [!IMPORTANT]
> Graph associative priors are applied exclusively in **Phase 6 fusion** and never in Phases 1–4 gating. Novel memories with zero prior co-activation history are **never gated out** or truncated.

---

## 2. Soul-Conditioned Regime (FERS)

When a `SoulContext` or AISME bundle is present on the `RecallSignal`, Spector activates the **Free-Energy Resonance Scorer (FERS)**.

### 2.1 Formula

$$\text{Score} = \alpha \cdot \text{Sim} + \beta \cdot \sigma(\Delta F) + \gamma \cdot \text{Resonance}$$

Where:
- $\text{Sim}$ is the INT8 / SVASQ vector similarity.
- $\sigma(\Delta F) = \frac{1}{1 + e^{-\Delta F}}$ is the logistic sigmoid squashing variational free energy delta.
- $\text{Resonance}$ is affective and goal resonance with the persona's active mental state.

### 2.2 Dynamic Weight Modulation & Hysteresis Damping

$\alpha, \beta, \gamma$ are dynamically computed per query by `SoulConditionedWeightProvider` from:
1. **Goal Relevance** — active task alignment.
2. **Value / Purpose Alignment** — core persona drives.
3. **Cognitive Profile** — modulates precision (e.g. `HYPERFOCUS`, `DIVERGENT`).

To prevent perceptual oscillation between consecutive turns, weights undergo **Exponential Moving Average (EMA)** hysteresis damping with rate clamping:

$$\mathbf{w}_t = (1 - \lambda) \mathbf{w}_{t-1} + \lambda \mathbf{w}_{\text{target}}$$

Where $\lambda = 0.15$ (decay factor), with deadband $\epsilon = 0.01$ and max slew rate per cycle.

---

## 3. Lateral Inhibition & Interference Resolution

In Phase 8 of recall, top candidates are evaluated for mutual interference by `LateralInhibitionRelay`:

1. **Clustering:** Single-linkage clustering over cosine similarity $\theta \ge 0.88$.
2. **Soft Redundancy Attenuation:** Non-top candidates in redundant clusters receive a graded penalty $\text{penalty}_i = 1.0 - \kappa_{\text{soft}} \cdot (1.0 - 1.0 / \text{rank}_i)$ without being dropped.
3. **Hard Contradiction Arbitration:** Candidates flagged with `FLAG_CONTRADICTED` are scored against confidence $C_i = 0.4 \cdot \text{recency} + 0.3 \cdot \text{corroboration} + 0.3 \cdot \text{storageStrength}$. Losers receive $\kappa_{\text{hard}} = 0.30$.

---

## 4. Telemetry & Explainability

Every recall returns detailed scoring provenance in `ScoreBreakdown`:

| Field | Description |
|---|---|
| `effectiveAlpha`, `effectiveBeta`, `effectiveGamma` | Dynamic weights applied in Phase 6 / FERS |
| `freeEnergyDelta` | Variational free energy reduction $\Delta F$ |
| `soulAlignmentScore` | Persona autobiographical resonance score |
| `scoringRegime` | `GENERIC` or `SOUL_CONDITIONED` |
| `weightVersion` | Active soul configuration version |
| `inhibitionPenalty` | Attenuation penalty factor applied by lateral inhibition |
| `competitorIds` | IDs of competing memories in the same cluster |
