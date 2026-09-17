# ADR-0009: AISME Phase 1 — Homeostatic Affective Core

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

**Context**: Issue #585 — Active Inference Self-Model Engine Phase 1  
**Module**: `spector-memory`, `spector-core`  

## Decision

### Package Structure

New package `com.spectrayan.spector.memory.aisme.homeostasis` within `spector-memory`:

```
memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/
├── homeostasis/
│   ├── InteroceptiveState.java       # Immutable record: VAD + K channels
│   ├── HomeostaticCore.java          # Neural ODE step integration
│   ├── EmotionalRegulator.java       # A_person matrix from Soul/Profile
│   └── AffectiveResonanceScorer.java # Mood-congruent scoring kernel
└── relay/
    └── HomeostaticBiasRelay.java     # RecallPathway relay integration
```

New SIMD kernel in `spector-core`:

```
nucleus/spector-core/src/main/java/com/spectrayan/spector/core/similarity/
└── AffectiveDistance.java            # SIMD affective resonance kernel
```

### Architectural Decisions

1. **InteroceptiveState as immutable record** — follows Spector's pattern of immutable data on API boundaries. Contains `float[] state` (VAD + channels), epoch timestamp, and version.

2. **HomeostaticCore uses explicit Euler integration** — Neural ODE with RK4 is unnecessary for the 10-dimensional affective state. Euler step: `h(t+dt) = h(t) + dt * (A·h(t) + B·u(t) + C·recall(t) + σ·w(t))`. Simpler, faster, sufficient for smooth emotional dynamics.

3. **A_person stored off-heap in InsularCortex** — the personal dynamics matrix lives alongside the self-model JSON in the Insula region. At 10×10 floats = 400 bytes, this fits easily within the existing InsularCortex allocation.

4. **HomeostaticBiasRelay position in pathway** — inserted between `QueryTransductionRelay` and `CorticalTierScanRelay`. It reads the current emotional state and injects scoring bias into the `RecallSignal` before tier scanning begins. This matches the neuroscience: emotional state biases what you look for, not just how you score results.

5. **AffectiveDistance SIMD kernel** — computes Gaussian kernel `exp(-||v_μ - v_s||² / 2σ²)` using existing `FloatVector` pattern. Same structure as `CosineSimilarity.java`. Lives in `spector-core` (Apache 2.0 licensed).

6. **Thread safety** — `HomeostaticCore` uses `ReentrantLock` for state mutation (no `synchronized`). `InteroceptiveState` is immutable and freely shareable across virtual threads.

7. **Backward compatibility** — the `HomeostaticBiasRelay` is optional. If no `HomeostaticCore` is configured, the relay is a no-op pass-through. Existing users see zero behavior change.

### Performance Budget

- Euler ODE step: <1μs (10-dim matrix-vector multiply)
- Affective resonance scoring per candidate: <0.5μs (SIMD Gaussian kernel)
- Total pipeline latency increase: <0.1ms at 10K candidates

### Risk Assessment

| Risk | Mitigation |
|---|---|
| ODE divergence under extreme inputs | Clamp state to [-1, 1] per dimension after each step |
| Performance regression in hot path | HomeostaticBiasRelay is conditional — disabled when no HomeostaticCore configured |
| InsularCortex layout breaking change | Add new region section with schema version bump, backward-compatible read |

**Approved** — implemented in PR #586.
