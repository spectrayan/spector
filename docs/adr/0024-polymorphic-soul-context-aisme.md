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

## 1. Context

Spector's domain model defines a rich, polymorphic sealed soul hierarchy in `com.spectrayan.spector.memory.model`:
- **`UserSoul`**: Human digital twin and persona continuity (`PersonaContext`, `IdiolectProfile`, `VocalProsodyDNA`, `EmbodiedKinesicsDNA`, `identityEmbedding`).
- **`AgentSoul`**: Autonomous AI assistant persona, tools, and system prompt.
- **`TenantSoul`**: Enterprise compliance and domain focus policies.
- **`OrgUnitSoul`**: Team/department expertise and guardrails.

However, the initial AISME integration (`GenerativeSelfModel`, `AismeBundle`, `SpectorMemoryBuilder`) was constrained exclusively to `AgentSoul`.

## 2. Problem Statement

Restricting AISME to `AgentSoul` prevented `UserSoul` (digital twin and ancestral continuity) from directly seeding the top-down generative prior $\boldsymbol{\mu}_0$ of the active inference self-model. Furthermore, enterprise multi-tenant soul stacks (`TenantSoul`, `OrgUnitSoul`) could not be exposed in `AismeBundle`, preventing departmental and tenant-level policies from influencing generative inference and policy selection.

## 3. Decision Drivers

- **Polymorphic Generative Priors**: All types in the `SoulContext` hierarchy must be capable of seeding or influencing generative self-model priors.
- **Hierarchical Prior Blending**: Multi-tier soul configurations (Tenant + Org + Agent + User) must support composite prior synthesis.
- **Backward Compatibility**: Existing clients and tests depending on `AgentSoul` accessors in `AismeBundle` must continue functioning without modification.
- **Builder Unification**: `SpectorMemoryBuilder` and `AismeBuilder` must expose uniform polymorphic methods for soul configuration.

## 4. Considered Options

### Option 1: Parallel Model Implementations
- **Description**: Create separate `UserGenerativeSelfModel`, `AgentGenerativeSelfModel`, and `TenantGenerativeSelfModel` classes.
- **Advantages**: Avoids modifying existing classes.
- **Disadvantages**: Massive code duplication; fails to support blended multi-soul hierarchies.

### Option 2: Polymorphic `SoulContext` Hierarchy Integration (Selected)
- **Description**: Upgrade `GenerativeSelfModel` and `AismeBundle` to operate on the sealed `SoulContext` interface, supporting polymorphic prior computation and multi-soul blending while providing backward-compatible adaptors.
- **Advantages**: Universal support for all soul types; supports multi-tier composite blending; 100% backward compatible.
- **Disadvantages**: Requires updating constructor signatures in internal builders.

## 5. Decision Outcome

**Chosen Option**: Option 2 (Polymorphic `SoulContext` Hierarchy Integration).

### Architectural Decisions:

#### D1: Polymorphic `SoulContext` in `GenerativeSelfModel`
- Replace `AgentSoul soul` with `SoulContext soul` in `GenerativeSelfModel`.
- Support polymorphic prior mean initialization via `SoulContext.identityEmbedding()`.
- Add `fromSoulsAndProfile(List<SoulContext> soulContexts, CognitiveProfile profile, int dimensions)` to compute a composite, blended generative prior $\boldsymbol{\mu}_0$.

#### D2: Polymorphic `AismeBundle`
- Update `AismeBundle` to hold `SoulContext primarySoul` and `List<SoulContext> soulContexts`.
- Provide backward-compatible accessor `public AgentSoul agentSoul()` which returns `(AgentSoul) primarySoul` if applicable, else `null`.

#### D3: Builder Alignment (`SpectorMemoryBuilder` & `AismeBuilder`)
- Expose `soul(SoulContext soul)` and `soulContexts(List<SoulContext> contexts)` in `SpectorMemoryBuilder`.
- Pass polymorphic `soul` and `soulContexts` to `AismeBuilder.build(...)`.

### Positive Consequences
- Closes the architectural gap for human digital twin persona storage (Homo Digitalis) by allowing `UserSoul` to directly drive active-inference self-models.
- Supports multi-tenant enterprise and departmental policy integration.
- 100% backward compatible with existing code expecting `AgentSoul`.

### Negative Consequences & Trade-offs
- Internal bundle structures hold a collection of souls, requiring null-safe checks when casting to specific concrete types.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Parallel Models** | Preserves existing classes | Extreme code duplication, cannot blend multi-tier souls |
| **Option 2: Polymorphic SoulContext** | Clean inheritance, composite prior blending, backward compatible | Requires builder signature updates |

## 7. Implementation Plan

1. **Phase 1**: Update `GenerativeSelfModel` to accept `SoulContext` and implement prior blending.
2. **Phase 2**: Refactor `AismeBundle` with `primarySoul` and `soulContexts` while retaining `agentSoul()`.
3. **Phase 3**: Update `AismeBuilder` and `SpectorMemoryBuilder` fluent APIs.
4. **Phase 4**: Add test coverage for `UserSoul` active-inference initialization.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.model`, `com.spectrayan.spector.memory.aisme.fegr`, `com.spectrayan.spector.memory.builder`
- **Classes**: `SoulContext.java`, `UserSoul.java`, `AgentSoul.java`, `GenerativeSelfModel.java`, `AismeBundle.java`, `AismeBuilder.java`
- **Verification Tests**: `GenerativeSelfModelTest.java`, `PolymorphicSoulAismeTest.java`
