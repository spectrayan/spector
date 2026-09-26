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

## 1. Context

`DreamPathway` delivers the 12-stage synaptic generative dreaming pipeline, Panama FFM off-heap dream journal, and `FLAG_DREAMED` source monitoring. In biological brains, sleep-dependent memory consolidation is inherently personalized: an individual's personal history, emotional baselines, cognitive boundaries, core values, and active concerns dictate what is replayed, how fluidly concepts recombine, and which insights survive prefrontal reality testing.

## 2. Problem Statement

Prior to this decision, `DreamPathway` operated on a global, homogeneous configuration. Exploration temperature, seed selection, Langevin diffusion, and Expected Free Energy (EFE) triage were identical for every agent and tenant. While Spector already possessed rich multi-soul and selective attention constructs (`SoulContext`, `AgentSoul`, `UserSoul`, `OrgUnitSoul`, `TenantSoul`, `SalienceProfile`), these were disconnected from the dreaming engine, resulting in generic, persona-blind dream consolidation.

## 3. Decision Drivers

- **Personalized Memory Consolidation**: Dream replay must resonate with the specific persona's core values, ethical guardrails, and emotional baseline.
- **Hardware-Accelerated Seed Selection**: Computing composite salience across large memory stores must utilize SIMD/GPU vectorized kernels.
- **Biophysiological Faithfulness**: Implement Hartmann boundary personality modulation and soul-guided Langevin stochastic differential equations (SDE).
- **Hierarchical Governance**: Enforce strict 4-tier governance ($\text{TenantSoul} \succ \text{OrgUnitSoul} \succ \text{AgentSoul} \succ \text{UserSoul}$) during dream triage.

## 4. Considered Options

### Option 1: Global Dream Configuration with Prompt Injection

- **Description**: Keep dreaming pipeline parameters identical across all agents; prepend persona instructions during final LLM dream text generation.
- **Advantages**: No changes to mathematical diffusion or seed scoring kernels.
- **Disadvantages**: Fails to personalize seed selection, associative tunneling, or EFE triage; wastes compute dreaming irrelevant scenarios.

### Option 2: Isolated Per-Persona Dream Engines

- **Description**: Instantiate separate dream pipeline runtimes and daemons for every registered soul.
- **Advantages**: Complete isolation between personas.
- **Disadvantages**: Excessive thread and memory resource footprint; unmanageable daemon proliferation under multi-tenant deployments.

### Option 3: Context-Propagated Soul Conditioning in Unified `DreamPathway` (Selected)

- **Description**: Propagate `SoulContext` and `SalienceProfile` dynamically via `DreamSignal` through the existing 12-stage pipeline, modulating seed salience, boundary thickness, Langevin drift, and EFE triage per cycle.
- **Advantages**: High computational efficiency; zero daemon explosion; deep mathematical personalization.
- **Disadvantages**: Requires propagating soul context into all 12 dream relay stages.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Context-Propagated Soul Conditioning in Unified `DreamPathway`).

### Architectural Decisions:

#### D1: Multi-Soul and Salience Context Propagation

- Enhance `DreamSignal` to carry `SoulContext primarySoul`, `List<SoulContext> soulContexts`, and `SalienceProfile salienceProfile`.
- Thread active souls and salience profiles from `SpectorMemoryBuilder`, `DefaultSpectorMemory`, and `SpectorMemoryFactory` into `DreamPathway.Builder`.
- Preserve 100% backward compatibility when `soul` or `salienceProfile` is null.

#### D2: Soul-Conditioned & Hardware-Accelerated Seed Salience (`SalientSeedRelay`)

- Replace the static seed formula with a composite score evaluating recency, novelty, primary soul cosine alignment, and user salience profile semantic matching:
  $$S(m) = w_r R + w_n N + w_{\text{soul}} \cos(\mathbf{e}_m, \mathbf{e}_{\text{soul}}) + w_{\text{sal}} \max_{k} \left[ \text{mult}_k \cos(\mathbf{e}_m, \mathbf{e}_{\text{interest}_k}) \right]$$

- Strictly execute all vector similarity calculations via SIMD/GPU batch `AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(...)`.
- All weights and thresholds defined in `SpectorPropertyConstants` (zero hardcoded magic numbers).

#### D3: Hartmann Boundary Personality Modulation (`RemReplayRelay` / `SceneConstructRelay`)

- Derive boundary thickness factor $\kappa_{\text{boundary}} \in [0.75, 1.35]$ from `AgentSoul.emotionalBaseline()` and personality traits.
- Scale effective REM temperature $\mathcal{T}_{\text{eff}} = \mathcal{T}_{\text{base}} \cdot \kappa_{\text{boundary}}$ and Hoel regularizing noise $\sigma_{\text{eff}} = \sigma_{\text{base}} \cdot \kappa_{\text{boundary}}$.

#### D4: Soul-Guided Langevin Diffusion SDE (`LangevinDiscoveryRelay`)

- Augment the continuous stochastic differential equation over the holographic memory tensor with a soul attractor potential $V_{\text{soul}}(\mathbf{v}) = \frac{1}{2}\lambda_{\text{soul}} \|\mathbf{v} - \mathbf{e}_{\text{soul}}\|^2$:
  $$\mathbf{v}_{t+1} = \mathbf{v}_t - \eta \left( \nabla E(\mathbf{v}_t; \mathbf{T}) + \lambda_{\text{soul}}(\mathbf{v}_t - \mathbf{e}_{\text{soul}}) \right) + \sqrt{2\eta \mathcal{T}_{\text{eff}}} \boldsymbol{\epsilon}_t$$

- Bias interstitial discovery towards the agent's core purpose while thermal noise enables tunneling over high-energy barriers.

#### D5: Hierarchical Multi-Soul EFE Triage (`EfeTriageRelay`)

- Enforce strict 4-tier governance: $\text{TenantSoul} \succ \text{OrgUnitSoul} \succ \text{AgentSoul} \succ \text{UserSoul}$.
- Ethical guardrail violations trigger immediate `NOISE` triage and Hebbian synaptic inhibition ($\Delta w = -0.05$).
- True `IDENTITY` triage outcome awarded when scene embedding resonates with `primarySoul.identityEmbedding()` ($\cos \ge \tau_{\text{identity}}$).

#### D6: Namespace & Soul-Aware Daemon (`DreamDaemon`)

- `DreamDaemon` extracts the active soul context and salience profile from `DefaultSpectorMemory` during periodic sleep consolidation cycles.

### Positive Consequences

- Transforms dreaming from a generic global thermostat into an individualized, biologically faithful consolidation engine.
- Distinct agents and users with different souls produce distinct seed selections, exploration temperatures, and distilled insights from the exact same memory store.
- Zero hardcoded magic numbers—all weights and thresholds are externally configurable via `SpectorPropertyConstants`.
- Preserves full backward compatibility and strict `FLAG_DREAMED` source monitoring isolation.

### Negative Consequences & Trade-offs

- Slight increase in seed scanning compute cost (mitigated by batch hardware SIMD/GPU cosine kernels).

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Global Config** | Zero kernel changes | Homogeneous dreams, lacks deep cognitive grounding |
| **Option 2: Per-Persona Daemons** | Isolated instances | Massive thread and memory resource proliferation |
| **Option 3: Signal Conditioning** | High resource efficiency, deep mathematical personalization | Requires threading context through all 12 dream relays |

## 7. Implementation Plan

1. **Phase 1**: Enhance `DreamSignal` with `SoulContext` and `SalienceProfile` fields.
2. **Phase 2**: Implement SIMD-vectorized seed scoring in `SalientSeedRelay`.
3. **Phase 3**: Add Hartmann boundary scaling to REM replay and soul attractor term to Langevin diffusion.
4. **Phase 4**: Implement hierarchical 4-tier EFE triage in `EfeTriageRelay`.
5. **Phase 5**: Validate with end-to-end multi-persona dream consolidation test suites.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-kernel`, `nucleus/spector-config`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.dream`, `com.spectrayan.spector.memory.pathway.dream.relay`, `com.spectrayan.spector.kernel.store`
- **Classes**: `DreamPathway.java`, `DreamSignal.java`, `DreamDaemon.java`, `DreamJournalMemory.java`, `RemDreamJob.java`, `DreamProperties.java`
- **Verification Tests**: `DreamPathwayTest.java`, `DreamGateAbortTest.java`, `DreamJournalMemoryTest.java`
