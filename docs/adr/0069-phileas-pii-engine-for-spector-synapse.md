# ADR-0069: Phileas PII Redaction Engine for Spector Synapse

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-31 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Spector Synapse must redact personally identifiable information before text leaves the trust boundary toward LLM providers, then rehydrate stable tokens in responses so conversation continuity is preserved.

A handmade regex + CapWord name heuristic (as first drafted on PR #914) is easy to ship and hard to trust: identifier coverage is thin, person/address/PHI detection is weak, and every new entity type becomes bespoke code. Spector already established a classpath-cached policy pattern for prompt-injection signals (`security/*.yml`); PII should follow the same operational model.

## 2. Problem Statement

Cognitive agents continuously ingest raw conversational text and interaction histories. Inadvertently persisting personally identifiable information (PII—e.g. social security numbers, credit card tokens, medical identifiers, contact details) into persistent memory-mapped engrams poses severe compliance and privacy risks (GDPR, HIPAA, SOC 2).

## 3. Decision Drivers

- **Zero Cloud Exfiltration**: PII detection and redaction must execute completely in-process without transmitting user text to third-party cloud DLP APIs.
- **Low-Latency Streaming**: Redaction must process text in <5ms during ingestion.
- **Configurable Anonymization Policies**: Support tokenization, hashing, synthetic replacement, or deletion per entity type.
- **Transparent Gateway Integration**: Integrate directly into Spector Synapse's inbound API controllers.

## 4. Considered Options

### Alternatives Evaluated

| Option | Why not |
|--------|---------|
| Keep homemade regex/NER | Low trust bar; endless maintenance; names/PHI remain stubs |
| Remote Philter service | Extra network hop, ops surface, and data egress for a core trust control |
| Enable Ph-Eye HTTP NER by default | Violates no-egress posture for outbound LLM shielding |

## 5. Decision Outcome

### Architectural Decisions & Non-Negotiable Constraints

Adopt **Phileas** (`ai.philterd:phileas`, Apache License 2.0, in-process) as the detection/redaction **engine** for Spector Synapse, behind a Spector-owned facade.

### Non-negotiable constraints

1. **Facade isolation** — Expose Spector types only (`PiiEngine` / existing `PiiDetector`-style API). Do **not** leak Phileas types into public Synapse APIs or OpenAPI/SDK surfaces.
2. **Keep Spector LLM boundary** — Retain `PiiInterceptor`, `PiiRedactionSession`, and Spector token rehydration. Use Phileas **spans** to build Spector tokens under a per-request context id. Do not treat opaque Phileas replacement strings as the rehydration contract.
3. **Classpath policies** — Author RELAXED / MODERATE / STRICT as three PhiSQL or JSON policies under `security/`, loaded once and cached (same pattern as injection classifier signals). Fail closed if a policy will not load.
4. **No remote Ph-Eye by default** — Remote NER would egress plaintext. Ship built-in identifier filters only. Optional local ONNX/Ph-Eye later only behind explicit config; never silent HTTP.
5. **Dependency hygiene** — Pin Phileas **4.x** from Maven Central (implementation target: `4.4.0`), scoped to `spector-synapse`.

## 6. Pros and Cons of the Options

### Consequences & Trade-offs

**Positive**
- Stronger, maintained identifier coverage with policy-driven strategies
- Clear module boundary: engine swappable without rewriting interceptor/session
- Aligns with Spector security config-as-resource pattern

**Negative / risks**
- New third-party dependency and version pin discipline
- Facade must be enforced in review so Phileas does not leak into public contracts
- Name/NER quality deferred until an approved local path exists (acceptable for v1 identifier shield)

## 7. Implementation Plan

1. **Dependency Integration**: Embed Phileas / Philter Java redaction libraries into `synapse/spector-synapse`.
2. **Gateway Filter**: Implement an inbound servlet / reactive filter redacting memory payloads prior to storage.
3. **Policy Configuration**: Provide YAML-configurable redaction profiles per tenant.
4. **Test Suite**: Author comprehensive unit tests verifying that sensitive patterns are scrubbed.

## 8. Code Reference & Verification

All PII redaction components and configuration models are verified in the repository:
- **Synapse Ingestion Controllers**: `synapse/spector-synapse/src/main/java/com/spectrayan/spector/synapse/`
- **Differential Privacy & Anonymization Relays**: `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/privacy/`
