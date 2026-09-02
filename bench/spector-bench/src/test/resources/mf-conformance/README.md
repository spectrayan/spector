# MF-001 Conformance Fixtures (draft)

Three engine tests from [memory-fundamentals MF-001 §10](https://github.com/spectrayan/memory-fundamentals/blob/main/spec/MF-001-Memory-Model.md).

These measure the **recall algebra**, not a downstream LLM judge. A reader passing LongMemEval does not pass these tests.

| ID | §10 test | Claim |
|---|---|---|
| **MF-T01** | Truncation trap | An old, high-\(I\), low-cosine constraint remains a candidate and outranks a recent lexical joke. |
| **MF-T03** | Valence window | Traces outside the cue valence window are hard-gated, not merely down-weighted. |
| **MF-T10** | Isolation | Rememberer A cannot retrieve B by cue collision; two stores are not one population. |

Soul is not a recall operator here. It is the function that **writes \(I\) and the valence prior** at `remember`. Each fixture therefore ships a `persona.json` (mmap-soul stand-in) and per-trace headers that that soul would have produced.

## Shared record schema

`corpus.jsonl` lines match Spector ingest plus MF-001 headers:

```json
{
  "id": "t-constraint-redeye",
  "text": "...",
  "memoryType": "EPISODIC",
  "source": "experienced",
  "timestampMs": 1690000000000,
  "sessionId": "s-2023-07",
  "importance": 9.4,
  "valence": -82,
  "arousal": 96,
  "interest": 0.9,
  "challenge": 0.2,
  "urgency": 0.95,
  "novelty": 0.4,
  "resolved": true,
  "synapticTags": ["travel", "constraint", "flights"],
  "rememberer": "rho-a"
}
```

`queries.jsonl` is a cue, not a QA item:

```json
{
  "id": "q-book-london",
  "text": "book my flight to London",
  "cognitiveProfile": "BALANCED",
  "valenceWindow": null,
  "timeWindow": null,
  "minImportance": null,
  "topK": 10,
  "allowSimulated": false
}
```

`expected.json` is the pass/fail contract. `qrels.tsv` is graded relevance for optional nDCG, not the primary verdict.

## How an engine is scored

For each query:

1. Load only the rememberer partition named in the fixture (T10 loads two partitions separately).
2. `remember` every corpus row with the given header (do not flatten \(I\)/valence to defaults).
3. Advance logical clock to `evalAsOfMs` so decay of \(D\) is defined.
4. `recall(cue)`.
5. Compare `expected.assertions`.

**Primary metric is assertion pass/fail**, not J-score. An LLM reader is out of scope.

Recommended two-column report:

| Condition | T01 | T03 | T10 |
|---|---|---|---|
| Cosine top-k then re-rank | expect FAIL T01 | | |
| Hybrid lexical+dense, flat \(I=1\) | expect FAIL T01 | | |
| Fused \(I \times D \times\) valence + isolation | expect PASS | expect PASS | expect PASS |

## What these fixtures are not

- Not LongMemEval category scores.
- Not a test of AISME, dream, or Langevin. Those belong under NF7 (`simulated` must not appear unless `allowSimulated`).
- Not a claim that biological terms are required. Headers are \(I\), valence, arousal, \(D\), \(S\).

## Suggested home in the spec repo

```
memory-fundamentals/tests/fixtures/MF-T01-truncation-trap/
memory-fundamentals/tests/fixtures/MF-T03-valence-window/
memory-fundamentals/tests/fixtures/MF-T10-isolation/
```
