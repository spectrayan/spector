# ADR-0021-PROSODY: Linguistic & Vocal Prosody Expression Engine

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

Following the completion of AISME Phases 1–12, Spector provides an active cognitive substrate (generative priors, continuous homeostasis, counterfactual simulation, EFE policy selection, spontaneous wander, and identity continuity). However, to enable multi-generational digital persona continuity, the system must bridge this internal subjective mind to human sensory perception.

Two foundational layers of this bridge are:
1. **Idiolect & Linguistic DNA**: The structural and idiosyncratic patterns of how the individual expresses thought in language (sentence length distributions, vocabulary diversity, signature idioms, and rhetorical habits).
2. **Vocal Prosody DNA & Parameter Vectors**: The acoustic signature of the voice and the real-time mathematical transfer functions that modulate speech parameters (fundamental frequency $F_0$, tempo, pitch variance, breathiness, assertiveness) based on the agent's internal AISME `InteroceptiveState` $(V, A, D)$.

## Decision

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

## Consequences

### Positive
- Captures idiosyncratic language patterns and acoustic profiles for sovereign digital relics.
- Connects internal emotional states dynamically to vocal delivery.
- Zero-copy persistence within existing mmap `INSULA` region.
- Completely decoupled from external TTS vendors.

### Negative / Trade-offs
- Adds new classes to the `spector-memory` model package.
- Minor heap overhead when compiling rich stylometric profiles.
