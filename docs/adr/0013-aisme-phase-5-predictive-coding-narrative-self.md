# ADR-0013: AISME Phase 5 — Predictive Coding Narrative Self

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

**Context**: Issue #593 — Active Inference Self-Model Engine Phase 5  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New SIMD kernel in `spector-core`:
```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── PredictiveCodingKernel.java       # SIMD precision-weighted errors, hierarchical energy & affine projection
```

New packages in `spector-memory`:
```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── pcmn/
│   ├── TierPrediction.java           # Immutable record: tier level, predicted vector, precision
│   ├── HierarchicalPredictionError.java # Immutable record: multi-tier error vectors & energy
│   └── PredictiveCodingNetwork.java  # 4-tier top-down prediction & bottom-up error propagation engine
│
├── narrative/
│   └── NarrativeSelfEngine.java      # Autobiographical self-schema & constructive simulation engine
│
├── workspace/
│   ├── AttentionSchema.java          # Meta-cognitive self-model of conscious attention focus
│   └── GlobalWorkspace.java          # Limited-capacity conscious broadcast gateway
│
└── relay/
    ├── ConstructiveSimulationRelay.java # Multi-tier predictive coding error reduction relay
    └── ConsciousAccessRelay.java        # Global workspace conscious broadcast gating relay
```

### Architectural Decisions

1. **4-Tier Cortical Predictive Coding Hierarchy**:
   - Maps directly onto Spector's 4-tier cortex architecture:
     - Tier 4: Procedural / Narrative Self / Insular Core
     - Tier 3: Semantic Knowledge Store
     - Tier 2: Episodic Memory Store
     - Tier 1: Working Context
     - Tier 0: Sensory / Query Input
   - Top-down generative models $f_\ell(\mathbf{x}_{\ell+1})$ predict expected representations at tier $\ell$.
   - Bottom-up precision-weighted prediction errors $\tilde{\boldsymbol{\epsilon}}_\ell = \boldsymbol{\pi}_\ell \odot (\mathbf{x}_\ell - \hat{\mathbf{x}}_\ell)$ quantify unexpected situational variance.

2. **SIMD-Accelerated Error Propagation**:
   - `PredictiveCodingKernel` vectorizes precision-weighted error calculation and total energy summation across all 4 cortical layers in a single pass with zero allocation.

3. **Narrative Self Engine (NSE) & Constructive Simulation**:
   - Generates autobiographical narrative priors consistent with persona identity, core values, and communication styles.
   - Evaluates candidate memories on their ability to minimize hierarchical prediction error under the narrative self-model.

4. **Global Workspace & Attention Schema**:
   - Conscious broadcast bottleneck: selects top-$k_{\text{GW}}$ candidate memories (capacity ~7) for final response generation.
   - `AttentionSchema` maintains meta-cognitive awareness of why specific memories were elevated into conscious focus.

5. **RecallPathway Relay Sequencing**:
   - `ConstructiveSimulationRelay` runs after associative Hopfield & Manifold relays, applying multi-tier predictive coding scoring.
   - `ConsciousAccessRelay` gates and annotates the candidate set into the Global Workspace before lexical fusion and output formatting.

### Performance Budget

- Full 4-tier prediction & error evaluation per query: $< 0.15\,\text{ms}$.
- Global Workspace broadcast gating: $< 0.05\,\text{ms}$.

**Approved** — implemented in PR #594.
