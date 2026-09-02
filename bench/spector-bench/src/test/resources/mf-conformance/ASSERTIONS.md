# Assertion language

`expected.json` uses a small closed set of predicates. A harness should implement these and nothing else for v0.1.

| `require` | Fields | Pass when |
|---|---|---|
| `retrieved` | `ids`, optional `atMostRank` | Every id appears in the result list; if `atMostRank` is set, each is at rank ≤ that value (1-based) |
| `outranks` | `higher`, `lower` | Both retrieved and rank(higher) < rank(lower) |
| `absent` | `ids` | None of the ids appear at any rank |
| `hard-gate-excludes` | `ids` | Same as `absent`, but a fail here is a valence/time/\(I_{\min}\) gate bug, not a ranking bug |
| `absent-from-top` | `ids`, `k`, optional `soft` | None of the ids appear in ranks `1..k`. If `soft`, warn only |
| `engine-property` | `property` | Harness-level check, not per-hit |

Ranks are 1-based after the engine's own dedup. Tombstoned traces do not count as retrieved.

## Required harness report shape

```json
{
  "testId": "MF-T01",
  "engine": "spector",
  "condition": "fused",
  "passed": ["T01-A1", "T01-A2"],
  "failed": [
    {
      "id": "T01-A2",
      "got": {"higherRank": 7, "lowerRank": 1}
    }
  ]
}
```

Run at least two conditions on T01: `cosine-topk-then-rerank` and `fused`. The fixture is doing its job if the first fails A1/A2 and the second passes them.
