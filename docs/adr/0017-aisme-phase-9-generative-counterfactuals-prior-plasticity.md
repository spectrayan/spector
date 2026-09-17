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

**Context**: Issue #607 — Active Inference Self-Model Engine Phase 9 (Generative Counterfactuals & Generative Prior Plasticity)  
**Module**: `spector-memory`, `spector-config`  

## Decision

### 1. Constructive Episodic Simulation & Counterfactual Recombination
- Enhanced `ConstructiveSimulationRelay` to detect complementary, high-salience memories aligned with the persona's autobiographical narrative prior.
- Recombines complementary episodes into synthesized counterfactual scenario representations tagged `[simulated, counterfactual, constructive]` (`MemoryType.EPISODIC`, `MemorySource.REFLECTED`) (Schacter & Addis 2007).

### 2. Generative Prior Mean Plasticity During Sleep Consolidation
- Added `withAdaptedPriorMean` in `GenerativeSelfModel` and thread-safe `adaptPriorMean` in `MentalStateTracker`.
- During REM sleep reflection (`SoulDriftRefusionRelay` / `ReflectPathway`), computes the moving centroid of autobiographical memories \(\mathbf{c}_{\text{autobio}}\) and adapts the generative prior mean:
  $$\boldsymbol{\mu}_0 \leftarrow (1 - \eta)\boldsymbol{\mu}_0 + \eta \mathbf{c}_{\text{autobio}}, \quad \eta = 0.005$$

**Approved** — implemented in PR #608.
