# ADR-0024: Polymorphic SoulContext Hierarchy in AISME

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

## Status
Accepted

## Date
2026-08-23

## Context

Spector's domain model defines a rich, polymorphic sealed soul hierarchy in `com.spectrayan.spector.memory.model`:
- **`UserSoul`**: Human digital twin and persona continuity (`PersonaContext`, `IdiolectProfile`, `VocalProsodyDNA`, `EmbodiedKinesicsDNA`, `identityEmbedding`).
- **`AgentSoul`**: Autonomous AI assistant persona, tools, and system prompt.
- **`TenantSoul`**: Enterprise compliance and domain focus policies.
- **`OrgUnitSoul`**: Team/department expertise and guardrails.

However, the initial AISME integration (`GenerativeSelfModel`, `AismeBundle`, `SpectorMemoryBuilder`) was constrained exclusively to `AgentSoul`. This prevented `UserSoul` (digital twin / ancestral continuity) from directly seeding the top-down generative prior $\mu_0$ of the active inference self-model, and prevented enterprise multi-tenant soul stacks from being fully exposed in `AismeBundle`.

## Decision

### D1: Polymorphic `SoulContext` in `GenerativeSelfModel`
- Replace `AgentSoul soul` with `SoulContext soul` in `GenerativeSelfModel`.
- Support polymorphic prior mean initialization via `SoulContext.identityEmbedding()`.
- Add `fromSoulsAndProfile(List<SoulContext> soulContexts, CognitiveProfile profile, int dimensions)` to compute a composite, blended generative prior $\mu_0$.

### D2: Polymorphic `AismeBundle`
- Update `AismeBundle` to hold `SoulContext primarySoul` and `List<SoulContext> soulContexts`.
- Provide backward-compatible accessor `public AgentSoul agentSoul()` which returns `(AgentSoul) primarySoul` if applicable, else `null`.

### D3: Builder Alignment (`SpectorMemoryBuilder` & `AismeBuilder`)
- Expose `soul(SoulContext soul)` and `soulContexts(List<SoulContext> contexts)` in `SpectorMemoryBuilder`.
- Pass polymorphic `soul` and `soulContexts` to `AismeBuilder.build(...)`.

## Consequences

### Positive
- Closes the architectural gap for human digital twin persona storage (Homo Digitalis) by allowing `UserSoul` to directly drive active-inference self-models.
- Supports multi-tenant enterprise and departmental policy integration.
- 100% backward compatible with existing code expecting `AgentSoul`.

### Negative / Trade-offs
- None; expands functionality while preserving backward compatibility.
