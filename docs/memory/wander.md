---
title: "Wander — Idle Association (Experimental)"
description: "The Wander pathway runs idle association scans when the agent is not actively processing queries."
---

!!! warning "Experimental"
    This pathway is not part of the default memory contract.
    Enable only with the documented flag. Traces it writes use a
    distinct provenance and must not be mixed with user-stated facts
    at recall time unless you opt in.

# Wander — Idle Association

The Wander pathway (`DmnSpontaneousDaemon`) runs idle association scans when the agent has no active queries. It walks the co-activation graph at low temperature, surfaces latent connections, and optionally writes `DREAMED`-provenance traces.

## Configuration

| Parameter | Default | Description |
|:---|:---|:---|
| `enableDreaming` | `false` | Master switch for Dream and Wander |
| `dmn.idleTimeoutMs` | `30000` | Idle time before Wander activates |
| `dmn.temperature` | `0.3` | Association temperature (lower = more conservative) |

## How to disable

Set `enableDreaming: false` in `spector.yml` or pass `--spector.memory.dreaming.enabled=false`.

→ See also: [Dream](dreaming.md) · [Decide](decide.md) · [Experimental overview](experimental.md)
