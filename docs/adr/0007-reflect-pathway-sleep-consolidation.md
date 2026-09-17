# ADR-0007: ReflectPathway — Biological Sleep Consolidation

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-19 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Approver**: Bharat (CEO), Technical Lead  
**Date**: 2026-08-19  
**Target Repository**: `spectrayan/spector` (Module: `spector-memory`, `spector-synapse`)  
**Related Issues**: [#503](https://github.com/spectrayan/spector/issues/503), [#561](https://github.com/spectrayan/spector/issues/561), [#446](https://github.com/spectrayan/spector/issues/446)  

---

## 1. Context & Problem Statement

In Spector, memory reflection represents the biological two-phase sleep consolidation cycle (NREM Deep Sleep synaptic downscaling + REM Sleep memory replay, schema integration, and gist extraction). 

Prior to this ADR, sleep consolidation was implemented across two tightly-coupled legacy classes:
1. `ReflectionOrchestrator.java` (575 LOC) — handled Hebbian synaptic homeostasis, temporal link pruning, STC cross-layer promotion, and entity maintenance.
2. `ReflectDaemon.java` (890 LOC) — handled partition scanning, tombstoning, compaction, legacy fixed-stride clustering, log turn extraction, proactive interference, and hardcoded LLM prompt formatting.

This architecture suffered from several critical liabilities:
- **Violation of Single Responsibility Principle (SRP)**: A single background daemon mixed low-level Foreign Function & Memory (FFM) memory-segment writes with high-level LLM prompt generation and cross-layer graph maintenance.
- **Divergent Ingestion Pathways**: While standard ingestion migrated to `RememberPathway` (ADR-0002 / #561), reflection still used procedural `CognitiveIngestionTarget.ingestCognitiveWithHeader(...)` and legacy fixed-stride `EpisodicRecordMemory`.
- **Identity Staleness (Soul-Drift)**: When an agent's `AgentSoul` or `UserSoul` evolved (updating ICNU weights, expertise domains, or personality traits), memories encoded under previous soul versions remained scored with obsolete importance metrics.
- **Missing Automated Scheduling**: `CircadianPolicy` defined `timeTrigger = 1h`, but no background scheduler actually executed periodic time-based sleep cycles.

---

## 2. Decision

We replace the procedural sleep consolidation logic with **`ReflectPathway`** (`CognitivePathway<ReflectSignal>`), standardizing reflection on the same composable relay framework powering `RecallPathway` and `RememberPathway`.

### 2.1 The 9-Stage Relay Sequence

```
ReflectPathway (CognitivePathway<ReflectSignal>)
  ├── 1. SynapticPruningRelay          [NREM: Prune decayed partition records & compact] (FAIL_FAST)
  ├── 2. EpisodicLogConsolidationRelay [REM: Multi-topic gist extraction via Handlebars] (DEGRADE_GRACEFULLY)
  ├── 3. SoulDriftRefusionRelay        [REM: #503 Detect stale soul_version & re-fuse]    (DEGRADE_GRACEFULLY)
  ├── 4. ProactiveInterferenceRelay    [REM: Zero-allocation near-duplicate decay]        (DEGRADE_GRACEFULLY)
  ├── 5. HebbianHomeostasisRelay       [Homeostasis: Arousal-modulated Hebbian decay]     (DEGRADE_GRACEFULLY)
  ├── 6. TemporalPruningRelay          [Homeostasis: Prune old weak causal links]          (DEGRADE_GRACEFULLY)
  ├── 7. CrossLayerPromotionRelay      [Schema: Hebbian -> Entity STC promotion]          (DEGRADE_GRACEFULLY)
  ├── 8. EntityMaintenanceRelay        [Schema: Entity decay, LTD adjacency, HyperGraph]  (DEGRADE_GRACEFULLY)
  └── 9. WalJournalRelay               [Persistence: WAL REFLECT event logging]          (FAIL_FAST)
```

### 2.2 Standardizing on `EpisodicLogMemory` & `RememberPathway`
- Reflection operates directly on log-structured conversation turns in `EpisodicLogMemory` (ADR-0006).
- Extracted semantic facts are ingested through `RememberPathway` with `MemorySource.REFLECTED` and rich `IngestionContext`.
- Legacy `EpisodicRecordMemory` methods in `ReflectDaemon` and `ReflectionOrchestrator` are marked `@Deprecated(since = "1.3.0", forRemoval = true)`.

### 2.3 Handlebars Prompt Externalization & Multi-Topic Extraction
- Prompts are externalized to `templates/prompts/reflection-synthesis.hbs` and rendered via `TemplateEngine.getDefault()`.
- To avoid lossy over-compression, the prompt instructs the LLM to extract distinct semantic facts if multiple topics or critical personal facts are discussed in the session.

### 2.4 Soul-Drift Re-Fusion Subsystem (#503)
- Scans memory headers across active/frozen partitions for `soul_version < currentSoulVersion`.
- Prioritizes drifted records using a bounded max-heap keyed on `encoding_surprise` (z-score).
- Recalculates importance using `ImportanceProvider` with active `SalienceProfile` / `IcnuWeights`.
- Updates importance and `soul_version` in-place on the memory segment.

### 2.5 Configurable Circadian Ticker
- Standalone `DefaultSpectorMemory` runs a background virtual-thread timer scheduling `reflect()` every `circadianPolicy.timeTrigger()`.
- `spector-synapse` `ConsolidationScheduler` runs periodic reflection across all cached tenant/user memory instances.

---

## 3. Consequences

### Positive
- **Architectural Cohesion**: All 3 memory operations (`recall`, `remember`, `reflect`) now use `CognitivePathway`.
- **Full Observability**: Every reflection stage is individually timed, metered, and logged via `ObservableRelay`.
- **Living Memory Alignment**: Solves #503 — agent memories stay permanently synchronized with personality/soul evolution.
- **Zero-Allocation Inner Loops**: Proactive interference eliminates heap allocations during distance comparisons.

### Negative / Trade-offs
- Refactoring requires deprecating legacy entry points, requiring tests to be updated to target `ReflectPathway`.
