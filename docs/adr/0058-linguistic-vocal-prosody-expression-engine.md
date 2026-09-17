# ADR-0058: Linguistic & Vocal Prosody Expression Engine

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-24 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Conversational autonomous agents communicating across multimodal voice channels require expressive vocal prosody (pitch modulation, speech rate, volume contouring, emphasis) that dynamically reflects their underlying internal cognitive and affective state.

Following the completion of AISME Phases 1–12, Spector provides an active cognitive substrate (generative priors, continuous homeostasis, counterfactual simulation, EFE policy selection, spontaneous wander, and identity continuity). However, to enable multi-generational digital persona continuity, the system must bridge this internal subjective mind to human sensory perception.

Two foundational layers of this bridge are:
1. **Idiolect & Linguistic DNA**: The structural and idiosyncratic patterns of how the individual expresses thought in language (sentence length distributions, vocabulary diversity, signature idioms, and rhetorical habits).
2. **Vocal Prosody DNA & Parameter Vectors**: The acoustic signature of the voice and the real-time mathematical transfer functions that modulate speech parameters (fundamental frequency $F_0$, tempo, pitch variance, breathiness, assertiveness) based on the agent's internal AISME `InteroceptiveState` $(V, A, D)$.

## 2. Problem Statement

Standard approaches to conversational voice synthesis suffer from a fundamental architectural tension:
1. **Heavy Model Bloat**: Embedding complete neural audio generation models (e.g. multi-gigabyte diffusion or vocoder models) inside Spector blows up container sizes and requires massive GPU allocations.
2. **Flat Robotic Monotones**: Decoupled TTS engines lacking affective signals produce flat, robotic speech that contradicts the agent's internal emotional state.
3. **Latency Bottlenecks**: Speech parameter modulation must be emitted in <10ms during stream chunking.

## 3. Decision Drivers

- **Zero Heavy Model Bloat**: Emit lightweight parameter vectors and SSML markup rather than raw synthesized audio waveforms.
- **Mathematical Grounding**: Formulate affective transfer functions mapping internal continuous emotion vectors (Valence-Arousal-Dominance) directly to acoustic parameters (Pitch, Rate, Energy).
- **Persona Context Integration**: Modulate acoustic baselines according to active persona identities and soul archetypes.
- **Sub-Millisecond Overhead**: Compute prosody vectors in <1ms during `ExpressPathway` execution.

## 4. Considered Options

### Option 1: In-Process Neural Vocoder
- Bundle an end-to-end neural TTS engine inside Spector.
- **Verdict**: Rejected. Infeasible operational footprint (multi-gigabyte models, dedicated GPU dependencies).

### Option 2: Static Heuristic Rules
- Use simple lookup tables mapping discrete emotions (happy, sad, angry) to fixed pitch adjustments.
- **Verdict**: Rejected. Unnatural transitions and failure to handle blended emotional states.

### Option 3: Continuous Affective Transfer Function & Parameter Vector Emission (Selected)
- Implement continuous transfer functions mapping internal VAD vectors to acoustic parameters (pitch $\Delta F_0$, rate $\Delta R$, volume $\Delta V$, breathiness $\beta$).
- Emit standardized SSML prosody tags or metadata JSON vectors alongside text chunks.
- **Verdict**: Accepted. Delivers rich expressive prosody with zero heavy model dependencies.

## 5. Decision Outcome

### Architectural Decisions

### D1: Co-location within `spector-memory`
Place the persona models (`IdiolectProfile`, `VocalProsodyDNA`, `StylometricAnalyzer`, `VocalProsodyTransferEngine`) directly within `com.spectrayan.spector.memory.model.persona` in the `spector-memory` module rather than creating a separate top-level Maven artifact.

**Rationale**:
- `PersonaContext` and `UserSoul` already reside in `spector-memory` and are persisted zero-copy in the `INSULA` mmap partition.
- Co-locating maintains high coherence and avoids redundant cross-module serialization overhead.

### D2: Parameter Vector Emission Model (Zero Heavy Model Bloat)
Spector produces **standardized parameter vectors** (`ProsodyParameterVector`, SSML attribute deltas) and **stylometric prompt directives** rather than hosting heavy neural TTS or voice cloning models directly in the Java kernel.

**Rationale**:
- Keeps Spector lean, high-throughput, and sub-millisecond in latency.
- External rendering engines (ElevenLabs, XTTS, FishSpeech, StyleTTS2, WebRTC audio streamers) consume these vectors natively.

### D3: Mathematical Transfer Function for Affective Prosody
Define deterministic transfer functions mapping AISME `InteroceptiveState` $(V, A, D) \rightarrow \text{ProsodyModulation}$:
- **Pitch Shift ($\Delta F_0$)**: $\Delta F_0 = \alpha \cdot A + \beta \cdot V$
- **Tempo Multiplier**: $\text{Tempo} = \text{Tempo}_0 \cdot (1 + \gamma \cdot A)$
- **Pitch Variance**: $\sigma_{\text{pitch}} = \sigma_0 \cdot (1 + \delta \cdot A)$
- **Intensity & Breathiness**: Modulated inversely with Dominance and Arousal.

### D4: Seamless `PersonaContext` Integration
Enrich `PersonaContext` with optional `IdiolectProfile` and `VocalProsodyDNA` fields with null-safe builders and neutral defaults (`IdiolectProfile.NEUTRAL`, `VocalProsodyDNA.NEUTRAL`), ensuring 100% backward compatibility with existing tests and storage records.

## 6. Pros and Cons of the Options

### Consequences & Trade-offs

### Positive
- Captures idiosyncratic language patterns and acoustic profiles for sovereign digital relics.
- Connects internal emotional states dynamically to vocal delivery.
- Zero-copy persistence within existing mmap `INSULA` region.
- Completely decoupled from external TTS vendors.

### Negative / Trade-offs
- Adds new classes to the `spector-memory` model package.
- Minor heap overhead when compiling rich stylometric profiles.

## 7. Implementation Plan

1. **Model Specification**: Define `VocalProsodyVector` and acoustic transfer functions in `spector-memory`.
2. **Pathway Integration**: Integrate prosody calculation into `ExpressPathway`.
3. **SSML Codec**: Provide utilities to format prosody parameters into standardized SSML tags.
4. **Validation Suite**: Author unit tests verifying VAD-to-prosody transfer curves.

## 8. Code Reference & Verification

All prosody formulations and pathway integrations are verified in the repository:
- **Express Pathway**: `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/cortex/pathway/ExpressPathway.java`
- **Affective & Soul Context**: `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/model/SoulContext.java`
