---
title: "Experimental Pathways"
description: "Pathways that are implemented but not part of the default agent contract. Off by default or behind feature flags."
---

# Experimental Pathways

These pathways are implemented and documented so contributors can find them. They are **not** part of the default agent contract. They are off by default or behind feature flags. Do not call them from production MCP prompts unless you have enabled the flag and accept provenance rules — dreamed and thought-experiment traces must not be treated as user-stated facts.

**Supported production verbs: Remember, Recall, Reflect, Forget.**

| Pathway | Flag | Trigger | Provenance tag | Risk |
|:---|:---|:---|:---|:---|
| **AISME** | `enableAisme` | Automatic if enabled | Standard | Persona/affect layer changes recall ranking |
| **Dream** | `enableDreaming` | `DreamDaemon` schedule or `/memory/dream` | `DREAMED` | Stochastic recombination; traces are synthetic |
| **Wander** | `enableDreaming` | Idle timer / `DmnSpontaneousDaemon` | `DREAMED` | Idle attractor; low-T association |
| **Decide** | `enableDreaming` | `/memory/decide` or thought-experiment fork | `THOUGHT_EXPERIMENT` | Counterfactual; not user-stated |
| **Express** | Always available | `memory_express` MCP tool | N/A (generation, not storage) | Synthesized text, not a stored fact |
| **Constructive simulation** | `enableAisme` | Recall with provenance tracing | `CONSTRUCTED` | Provenance-sensitive; must not mix with user-stated facts |
