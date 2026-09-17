# ADR-0022: Embodied Kinesics & Phenomenological MCP Engine

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

## Status
Accepted

## Date
2026-08-22

## Context

Phases 1 & 2 established `ExpressPathway` (the 6th Canonical Cognitive Pathway in `spector-memory`), `VocalProsodyKernel` (AVX-512 SIMD in `spector-core`), and idiolect / prosody modeling. To complete the digital persona expression architecture for sovereign relics in Homo Digitalis, the system requires:
1. **Embodied Kinesics & Avatar Dynamics**: Real-time synthesis of 52 standard Apple ARKit / FACS facial blendshape weights, gaze aversion coordinates, and head pose deltas driven by AISME `InteroceptiveState` $(V, A, D)$ and memory recall cognitive load.
2. **Phenomenological Stream & MCP Exporters**: A unified `PhenomenologicalContextPack` compiling pre-verbal introspective monologues, prompt directives, prosody parameters, and blendshapes, exposed to external runtimes via standard Model Context Protocol (MCP) tools in `synapse/spector-mcp`.

## Decision

### D1: 52 ARKit / FACS Blendshape Standard
Standardize kinesic output on the 52 Apple ARKit / FACS Action Unit blendshape specification:
- Universal compatibility with Unreal Engine MetaHuman, Unity, MediaPipe, Live2D, Ready Player Me, and WebGL avatar pipelines.
- Vectorized computation in `KinesicBlendshapeKernel` (`spector-core`) utilizing Java 25 Panama Vector API (`FloatVector`).

### D2: Complete 4-Relay Execution Pipeline in `ExpressPathway`
Structure `ExpressPathway` with 4 sequential gated relays:
1. `IdiolectStylometryRelay`: Injects signature idioms, catchphrases, and prompt rules.
2. `VocalProsodyRelay`: Computes affective acoustic modulations and SSML tags via `VocalProsodyKernel`.
3. `EmbodiedKinesicsRelay`: Computes 52 ARKit blendshapes and gaze coordinates via `KinesicBlendshapeKernel`.
4. `PhenomenologicalStreamRelay`: Synthesizes pre-verbal introspective monologue and compiles `PhenomenologicalContextPack`.

### D3: MCP Tool Endpoints in `synapse/spector-mcp`
Introduce two dedicated MCP tools:
- `memory_express`: Single-call multi-modal expression endpoint returning prompts, prosody parameters, blendshapes, and introspective monologues.
- `memory_persona_context`: Exposes the stored `PersonaContext` (`IdiolectProfile`, `VocalProsodyDNA`, `EmbodiedKinesicsDNA`) for inspection and tooling.

## Consequences

### Positive
- Completes the 4-pillar Persona & Expression Architecture.
- Enables rich 3D avatar animation and affective TTS streaming directly from Spector's cognitive state.
- Exposes clean, structured MCP tools for modern LLM clients and frontend renderers.

### Negative / Trade-offs
- Adds new classes to `spector-core`, `spector-memory`, and `spector-mcp`.
- Minor heap overhead during multi-modal context pack assembly.
