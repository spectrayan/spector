# ADR-0015: AISME Phase 7 — Synaptic Relay Wiring & Pathway Configuration

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

**Context**: Issue #597 — Active Inference Self-Model Engine Phase 7 (Final System Integration)  
**Module**: `spector-config`, `spector-memory`, `spector-core`  

## Decision

### Architecture & Pipeline Sequencing

All 7 AISME synaptic relays are integrated into the `RecallPathwayFactory` in neuro-computational order with `ErrorPolicy.DEGRADE_GRACEFULLY` and gated by `Specification<RecallSignal>` in `RecallGates`:

1. `HOMEOSTATIC_BIAS` (`HomeostaticBiasRelay`): Modulates query/candidate affective resonance before deep vector search
2. `FREE_ENERGY_GUIDED` (`FreeEnergyGuidedRelay`): Variational free energy minimization and active inference scoring
3. `HOPFIELD_ASSOCIATIVE` (`HopfieldAssociativeRelay`): Attractor state energy relaxation and pattern completion
4. `MANIFOLD_RERANK` (`ManifoldRerankRelay`): Riemannian geodesic distance re-ranking on the personal cognitive manifold
5. `CONSTRUCTIVE_SIMULATION` (`ConstructiveSimulationRelay`): Top-down narrative self-schema validation and multi-tier prediction error reduction
6. `CONSCIOUSNESS_CONTINUITY` (`ConsciousnessContinuityRelay`): Gaussian IIT $\Phi_{\text{CC}}$ holistic synergy scoring against `AgentSoul`
7. `CONSCIOUS_ACCESS` (`ConsciousAccessRelay`): Global Workspace conscious broadcast bottleneck (~7 items) with Attention Schema

### Configuration Architecture

- **`SpectorPropertyConstants.java`** in `spector-config`: Central property keys (`spector.memory.aisme.*`) with `false` default for zero impact on existing deployments.
- **`AismeConfig.java`** in `spector-memory`: Immutable record holding master toggle, per-relay toggles, and hyperparameters.
- **`RecallOptions.java`**: Supports `aismeConfig` and per-query overrides.
- **`AismeBundle.java`** & **`AismeBuilder.java`**: Self-contained lifecycle factory that builds all 6 AISME engines and passes them to `RecallPathway`, `ReflectPathway`, and `RememberPathway`.
- **`ReflectPathway`**: Integrates `ManifoldConsolidationRelay` during sleep reflection cycles.

### Backward Compatibility Guarantee

When `aisme.enabled=false` (the default), all AISME gates evaluate to `false` and the pipeline executes with 0 overhead, producing results identical to the legacy pipeline.

**Approved** — implemented in PR #598.
