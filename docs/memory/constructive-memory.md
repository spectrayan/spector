# Constructive Simulation & Provenance

Spector implements the **Constructive Episodic Simulation Hypothesis** (Schacter & Addis, 2007), allowing cognitive agents to recombine elements of past experiences into counterfactual scenarios while maintaining rigorous cryptographic and binary provenance.

---

## 1. Flexible Fragment Recombination

Unlike static centroid averaging, constructive simulation samples diverse candidate pairs across the top retrieval pool:

1. **Diversity Sampling:** Deterministic pseudo-random pairing seeded by query hash ensures reproducible benchmark evaluations.
2. **Fragment Decomposition:** Extracts entity links, temporal position, affective valence/arousal, and semantic vector embeddings.
3. **Alignment-Weighted Synthesis:** Vector embeddings are blended proportionally to their narrative alignment with the agent's prior:

$$\mathbf{v}_{\text{sim}} = w_1 \mathbf{v}_1 + w_2 \mathbf{v}_2, \quad w_1 = \frac{\text{align}_1}{\text{align}_1 + \text{align}_2}$$

4. **Predictive Error Gate:** Evaluated against `PredictiveCodingNetwork` hierarchical error to discard implausible counterfactual fantasies.

---

## 2. Cryptographic & Binary Provenance (MR-01)

Synthetic memories can lead to confabulation if unlabelled. Spector enforces provenance at the binary kernel layer:

```
Byte 34 (Consolidation Flags):
┌───┬───┬───┬───┬───┬───┬───┬───┐
│ 7 │ 6 │ 5 │ 4 │ 3 │ 2 │ 1 │ 0 │
└───┴───┴───┴───┴───┴───┴───┴───┘
          │
          └── Bit 5: FLAG_SIMULATED (0x20)
```

- **In-Memory:** Candidates carry `EncodingHeaderFields.FLAG_SIMULATED` in `consolidationFlags()`.
- **Durable Storage:** Stamped into consolidation flags (byte 40 in `EncodingHeaderLayout`). Any downstream scan instantly identifies simulated records via `EncodingHeaderFields.isSimulated(flags)`.
- **Soul Version Tracking:** Persisted with `soulVersion` (bytes 46–47) indicating the exact agent configuration under which the simulation was generated.

---

## 3. High-Alignment Persistence Gate

Synthetic memories are not persisted indiscriminately. `ConstructiveMemoryPersistenceRelay` enforces that memories must meet an explicit narrative alignment threshold:

$$\text{alignSim} \ge \text{persistenceThreshold} \quad (\text{default: } 0.70)$$

Where `alignSim` is explicitly preserved in candidate metadata and passed directly to durable ingestion.

---

## 4. Benchmark Transparency

Spector tracks and publishes false-memory rates and gap-filling utility through the Cognitive Fidelity Benchmark Suite (`ConstructiveRecallFidelityBenchmark`), ensuring counterfactual synthesis enhances reasoning without polluting long-term factual recall.
