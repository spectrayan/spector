# ADR-0027: Soul-Conditioned & Salience-Modulated Personalized Dreaming

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-27 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## Status
Accepted

## Date
2026-08-27

## Context

`DreamPathway` (#679 / #680) successfully delivered the 12-stage synaptic generative dreaming pipeline, Panama FFM off-heap dream journal, and `FLAG_DREAMED` source monitoring. However, its dream policy operates as a process-global configuration where temperature, seed selection, Langevin diffusion, and Expected Free Energy (EFE) triage are identical for every agent and user.

In computational neuroscience, sleep-dependent memory consolidation is inherently idiosyncratic: an individual's personal history, emotional baselines, cognitive boundaries, core values, and active concerns dictate **what** is replayed, **how** fluidly concepts recombine, and **which** insights survive prefrontal reality testing.

Spector already possesses rich multi-soul and selective attention constructs (`SoulContext`, `AgentSoul`, `UserSoul`, `OrgUnitSoul`, `TenantSoul`, and `SalienceProfile`), but these are not wired into the `DreamPathway`.

## Decision

### D1: Multi-Soul and Salience Context Propagation
- Enhance `DreamSignal` to carry `SoulContext primarySoul`, `List<SoulContext> soulContexts`, and `SalienceProfile salienceProfile`.
- Thread active souls and salience profiles from `SpectorMemoryBuilder`, `DefaultSpectorMemory`, and `SpectorMemoryFactory` into `DreamPathway.Builder`.
- Preserve 100% backward compatibility when `soul` or `salienceProfile` is null.

### D2: Soul-Conditioned & Hardware-Accelerated Seed Salience (`SalientSeedRelay`)
- Replace the static seed formula with a composite score evaluating recency, novelty, primary soul cosine alignment, and user salience profile semantic matching:
  $$S(m) = w_r R + w_n N + w_{\text{soul}} \cos(\mathbf{e}_m, \mathbf{e}_{\text{soul}}) + w_{\text{sal}} \max_{k} \left[ \text{mult}_k \cos(\mathbf{e}_m, \mathbf{e}_{\text{interest}_k}) \right]$$
- Strictly execute all vector similarity calculations via SIMD/GPU batch `AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(...)`.
- All weights and thresholds defined in `SpectorPropertyConstants` (zero hardcoded magic numbers).

### D3: Hartmann Boundary Personality Modulation (`RemReplayRelay` / `SceneConstructRelay`)
- Derive boundary thickness factor $\kappa_{\text{boundary}} \in [0.75, 1.35]$ from `AgentSoul.emotionalBaseline()` and `personality` traits.
- Scale effective REM temperature $\mathcal{T}_{\text{eff}} = \mathcal{T}_{\text{base}} \cdot \kappa_{\text{boundary}}$ and Hoel regularizing noise $\sigma_{\text{eff}} = \sigma_{\text{base}} \cdot \kappa_{\text{boundary}}$.

### D4: Soul-Guided Langevin Diffusion SDE (`LangevinDiscoveryRelay`)
- Augment the continuous stochastic differential equation over the holographic memory tensor with a soul attractor potential $V_{\text{soul}}(\mathbf{v}) = \frac{1}{2}\lambda_{\text{soul}} \|\mathbf{v} - \mathbf{e}_{\text{soul}}\|^2$:
  $$\mathbf{v}_{t+1} = \mathbf{v}_t - \eta \left( \nabla E(\mathbf{v}_t; \mathbf{T}) + \lambda_{\text{soul}}(\mathbf{v}_t - \mathbf{e}_{\text{soul}}) \right) + \sqrt{2\eta \mathcal{T}_{\text{eff}}} \boldsymbol{\epsilon}_t$$
- Bias interstitial discovery towards the agent's core purpose while thermal noise enables tunneling over high-energy barriers.

### D5: Hierarchical Multi-Soul EFE Triage (`EfeTriageRelay`)
- Enforce strict 4-tier governance: $\text{TenantSoul} \succ \text{OrgUnitSoul} \succ \text{AgentSoul} \succ \text{UserSoul}$.
- Ethical guardrail violations trigger immediate `NOISE` triage and Hebbian synaptic inhibition ($\Delta w = -0.05$).
- True `IDENTITY` triage outcome awarded when scene embedding resonates with `primarySoul.identityEmbedding()` ($\cos \ge \tau_{\text{identity}}$).

### D6: Namespace & Soul-Aware Daemon (`DreamDaemon`)
- `DreamDaemon` extracts the active soul context and salience profile from `DefaultSpectorMemory` during periodic sleep consolidation cycles.

## Consequences

### Positive
- Transforms dreaming from a generic global thermostat into an individualized, biologically faithful consolidation engine.
- Distinct agents/users with different souls produce distinct seed selections, exploration temperatures, and distilled insights from the exact same memory store.
- Zero hardcoded magic numbers—all weights and thresholds are externally configurable via `SpectorPropertyConstants`.
- Preserves full backward compatibility and strict `FLAG_DREAMED` source monitoring isolation.

### Negative / Trade-offs
- Slight increase in seed scanning compute cost (mitigated by batch hardware SIMD/GPU cosine kernels).
