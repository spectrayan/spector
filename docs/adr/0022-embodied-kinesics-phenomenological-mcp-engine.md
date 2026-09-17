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

## 1. Context

Spector's digital persona architecture aims to support digital twin continuity (Homo Digitalis) by modeling not only memory and facts, but also expressive behavior, affective tone, and embodied presence. Previous work established `ExpressPathway` (the 6th canonical cognitive pathway in `spector-memory`), `VocalProsodyKernel` (AVX-512 SIMD in `spector-core`), and idiolect/prosody modeling.

## 2. Problem Statement

Prior to this decision, persona expression was limited to text and SSML acoustic modulation. The system lacked:
1. **Embodied Kinesics & Avatar Dynamics**: Real-time computation of standard facial blendshapes, gaze aversion vectors, and head pose deltas driven by AISME interoceptive state $(V, A, D)$ and cognitive recall load.
2. **Phenomenological Stream & MCP Exporters**: A unified multi-modal payload compiling pre-verbal introspective monologues, prompt directives, prosody parameters, and facial blendshapes exposed to external runtimes via standard Model Context Protocol (MCP) tools in `synapse/spector-mcp`.

## 3. Decision Drivers

- **Industry Compatibility**: Blendshape standard must integrate cleanly with Unreal Engine MetaHuman, Unity, MediaPipe, Live2D, Ready Player Me, and WebGL rendering engines.
- **Biophysiological Coupling**: Facial kinesics and gaze aversion must couple directly to cognitive load and affective $(V, A, D)$ states.
- **Zero-Allocation Vectorization**: Computing 52 blendshape coefficients from interoceptive states must use SIMD vector math without heap allocations.
- **Declarative MCP Tool Integration**: Introspective states and expressive parameters must be callable by standard LLM agent clients via MCP.

## 4. Considered Options

### Option 1: Client-Side Avatar Heuristics
- **Description**: Expose only emotional valence/arousal numbers and let external client applications interpret avatar animations.
- **Advantages**: Minimal server-side compute.
- **Disadvantages**: Inconsistent character behavior across different client frontends; disconnects micro-expressions from memory recall cognitive load.

### Option 2: Server-Side Proprietary 3D Mesh Deformation
- **Description**: Generate full 3D vertex meshes in Spector.
- **Advantages**: Extreme control over 3D geometry.
- **Disadvantages**: Massive payload sizes; heavy server GPU load; incompatible with client-side game engines.

### Option 3: 52-Blendshape ARKit/FACS Standard + `ExpressPathway` Relays + MCP Exporters (Selected)
- **Description**: Standardize on the 52 Apple ARKit / FACS Action Unit blendshape specification calculated via `KinesicBlendshapeKernel` in `spector-core`, orchestrated through `ExpressPathway` and exported via `spector-mcp`.
- **Advantages**: Lightweight payload (52 floats); universal avatar engine support; coupled to internal cognitive state.
- **Disadvantages**: Clients must support standard blendshape targeting.

## 5. Decision Outcome

**Chosen Option**: Option 3 (52 ARKit/FACS Standard + `ExpressPathway` Relays + MCP Exporters).

### Architectural Decisions:

#### D1: 52 ARKit / FACS Blendshape Standard
Standardize kinesic output on the 52 Apple ARKit / FACS Action Unit blendshape specification:
- Universal compatibility with Unreal Engine MetaHuman, Unity, MediaPipe, Live2D, Ready Player Me, and WebGL avatar pipelines.
- Vectorized computation in `KinesicBlendshapeKernel` (`spector-core`) utilizing Java 25 Panama Vector API (`FloatVector`).

#### D2: Complete 4-Relay Execution Pipeline in `ExpressPathway`
Structure `ExpressPathway` with 4 sequential gated relays:
1. `IdiolectStylometryRelay`: Injects signature idioms, catchphrases, and prompt rules.
2. `VocalProsodyRelay`: Computes affective acoustic modulations and SSML tags via `VocalProsodyKernel`.
3. `EmbodiedKinesicsRelay`: Computes 52 ARKit blendshapes and gaze coordinates via `KinesicBlendshapeKernel`.
4. `PhenomenologicalStreamRelay`: Synthesizes pre-verbal introspective monologue and compiles `PhenomenologicalContextPack`.

#### D3: MCP Tool Endpoints in `synapse/spector-mcp`
Introduce two dedicated MCP tools:
- `memory_express`: Single-call multi-modal expression endpoint returning prompts, prosody parameters, blendshapes, and introspective monologues.
- `memory_persona_context`: Exposes the stored `PersonaContext` (`IdiolectProfile`, `VocalProsodyDNA`, `EmbodiedKinesicsDNA`) for inspection and tooling.

### Positive Consequences
- Completes the 4-pillar Persona & Expression Architecture.
- Enables rich 3D avatar animation and affective TTS streaming directly from Spector's cognitive state.
- Exposes clean, structured MCP tools for modern LLM clients and frontend renderers.

### Negative Consequences & Trade-offs
- Adds new classes to `spector-core`, `spector-memory`, and `spector-mcp`.
- Minor heap overhead during multi-modal context pack assembly.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Client Heuristics** | Zero server overhead | Inconsistent avatar behavior, lacks recall-state coupling |
| **Option 2: 3D Mesh Output** | Full rendering control | Massive network payloads, server GPU saturation |
| **Option 3: 52 ARKit Standard** | Universal engine compatibility, lightweight (52 floats), SIMD | Client must have ARKit/FACS rig |

## 7. Implementation Plan

1. **Phase 1**: Implement `KinesicBlendshapeKernel` in `nucleus/spector-core/expression`.
2. **Phase 2**: Add `EmbodiedKinesicsRelay` and `PhenomenologicalStreamRelay` to `memory/spector-memory/pathway/express/relay`.
3. **Phase 3**: Implement `memory_express` and `memory_persona_context` MCP tools in `synapse/spector-mcp`.
4. **Phase 4**: Validate blendshape generation under varied cognitive load profiles.

## 8. Code Reference & Verification

- **Primary Module(s)**: `nucleus/spector-core`, `memory/spector-memory`, `synapse/spector-mcp`
- **Key Packages**: `com.spectrayan.spector.core.expression`, `com.spectrayan.spector.memory.pathway.express`, `com.spectrayan.spector.mcp.tools`
- **Classes**: `KinesicBlendshapeKernel.java`, `VocalProsodyKernel.java`, `ExpressPathway.java`, `EmbodiedKinesicsRelay.java`, `PhenomenologicalStreamRelay.java`, `MemoryExpressTool.java`
- **Verification Tests**: `KinesicBlendshapeKernelTest.java`, `ExpressPathwayTest.java`
