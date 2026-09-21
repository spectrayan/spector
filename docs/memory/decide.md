---
title: "Decide — Thought Experiments (Experimental)"
description: "The Decide pathway runs low-temperature thought experiments at decision forks."
---

!!! warning "Experimental"
    This pathway is not part of the default memory contract.
    Enable only with the documented flag. Traces it writes use a
    distinct provenance and must not be mixed with user-stated facts
    at recall time unless you opt in.

# Decide — Thought Experiments

The Decide pathway (`DecidePathway`) runs low-temperature thought experiments at decision forks. It takes a question and a set of candidate actions, simulates recall under each, and returns ranked outcomes with provenance `THOUGHT_EXPERIMENT`.

## Configuration

| Parameter | Default | Description |
|:---|:---|:---|
| `enableDreaming` | `false` | Master switch (shared with Dream/Wander) |
| `decide.temperature` | `0.5` | Sampling temperature for thought experiments |

## Provenance

All traces written by Decide carry provenance `THOUGHT_EXPERIMENT`. They are excluded from standard Recall unless `includeThoughtExperiments: true` is set in `RecallOptions`.

→ See also: [Dream](dreaming.md) · [Wander](wander.md) · [Experimental overview](experimental.md)
