# ADR-0034-PII: Phileas PII Engine for Spector Synapse

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-09-13 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Related**: Spector [#203](https://github.com/spectrayan/spector/issues/203), PR [#914](https://github.com/spectrayan/spector/pull/914)

## Context

Spector Synapse must redact personally identifiable information before text leaves the trust boundary toward LLM providers, then rehydrate stable tokens in responses so conversation continuity is preserved.

A handmade regex + CapWord name heuristic (as first drafted on PR #914) is easy to ship and hard to trust: identifier coverage is thin, person/address/PHI detection is weak, and every new entity type becomes bespoke code. Spector already established a classpath-cached policy pattern for prompt-injection signals (`security/*.yml`); PII should follow the same operational model.

## Decision

Adopt **Phileas** (`ai.philterd:phileas`, Apache License 2.0, in-process) as the detection/redaction **engine** for Spector Synapse, behind a Spector-owned facade.

### Non-negotiable constraints

1. **Facade isolation** — Expose Spector types only (`PiiEngine` / existing `PiiDetector`-style API). Do **not** leak Phileas types into public Synapse APIs or OpenAPI/SDK surfaces.
2. **Keep Spector LLM boundary** — Retain `PiiInterceptor`, `PiiRedactionSession`, and Spector token rehydration. Use Phileas **spans** to build Spector tokens under a per-request context id. Do not treat opaque Phileas replacement strings as the rehydration contract.
3. **Classpath policies** — Author RELAXED / MODERATE / STRICT as three PhiSQL or JSON policies under `security/`, loaded once and cached (same pattern as injection classifier signals). Fail closed if a policy will not load.
4. **No remote Ph-Eye by default** — Remote NER would egress plaintext. Ship built-in identifier filters only. Optional local ONNX/Ph-Eye later only behind explicit config; never silent HTTP.
5. **Dependency hygiene** — Pin Phileas **4.x** from Maven Central (implementation target: `4.4.0`), scoped to `spector-synapse`.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Keep homemade regex/NER | Low trust bar; endless maintenance; names/PHI remain stubs |
| Remote Philter service | Extra network hop, ops surface, and data egress for a core trust control |
| Enable Ph-Eye HTTP NER by default | Violates no-egress posture for outbound LLM shielding |

## Consequences

**Positive**
- Stronger, maintained identifier coverage with policy-driven strategies
- Clear module boundary: engine swappable without rewriting interceptor/session
- Aligns with Spector security config-as-resource pattern

**Negative / risks**
- New third-party dependency and version pin discipline
- Facade must be enforced in review so Phileas does not leak into public contracts
- Name/NER quality deferred until an approved local path exists (acceptable for v1 identifier shield)

## Implementation note

Implementation proceeds on Spector PR #914 (`feat/203-pii-redaction`). This ADR records the architecture/dependency bet only; it does not change runtime code in the `spectrayan` repo.
