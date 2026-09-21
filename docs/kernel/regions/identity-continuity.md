---
title: "CONTINUITY — Binary Layout Reference"
description: "Identity continuity trajectory and narrative history."
---
# ⏳ CONTINUITY (`IdentityRegionId.CONTINUITY`, ID: 3)
> **Identity continuity trajectory and narrative history.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `identity.bundle` (also present in `runtime.bundle` as Region 25) |
| **Memory Shape** | RECORD |
| **Layout Class** | [`ContinuityLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/ContinuityLayout.java) |
| **Store Class** | [`ContinuityMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/ContinuityMemory.java) |
| **Record Stride** | 32 bytes |
| **Cache-Line Aligned** | Yes (64B Preamble) |

## Wire Diagram (32B Sub-Header + 32B Records)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       64B RegionPreamble (SMKM, RECORD, layout=CONT)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 64)
|   headIndex (4B)              |    totalSnapshots (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 lastSnapshotTimestampMs (8B)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    capacity (4B)              |        reserved (12B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 96)
```

**Record Diagram (32 Bytes):**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      timestampMs (8B)                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         phiCc (4B)            |          traceG (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       priorDrift (4B)         | val(1)| aro(1)| eng(1)| sVer(2) |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                          reserved (7B)                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Continuity Sub-Header
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x40` | 4B | `headIndex` | int | Current insertion point for circular buffer |
| `0x44` | 4B | `totalSnapshots` | int | Total number of snapshots stored |
| `0x48` | 8B | `lastSnapshotTimestampMs` | long | Timestamp of the most recent snapshot |
| `0x50` | 4B | `capacity` | int | Maximum size of circular buffer |
| `0x54` | 12B | `reserved` | bytes | Reserved padding |

### Snapshot Record (Stride: 32B)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8B | `timestampMs` | long | Epoch time of snapshot |
| `0x08` | 4B | `phiCc` | float | Integrated information cohesion score |
| `0x0C` | 4B | `traceG` | float | Riemannian manifold curvature equivalent |
| `0x10` | 4B | `priorDrift` | float | Magnitude drift from generative baseline |
| `0x14` | 1B | `valence` | byte | Emotional valence |
| `0x15` | 1B | `arousal` | byte | Arousal level |
| `0x16` | 1B | `energy` | byte | Homeostatic energy proxy |
| `0x17` | 2B | `soulVersion` | short | Associated version of the Soul payload |
| `0x19` | 7B | `reserved` | bytes | Padding |

## Access Patterns & Concurrency

Managed as a circular ring buffer avoiding garbage collection / dynamic allocations. Uses `ReentrantLock` for safety on writes (`appendSnapshot`). Trajectory snapshots are stored as fixed-stride memory slabs and read dynamically off-heap.

## Cognitive Role

Longitudinal identity cohesion tracker. Monitors identity drift, state deltas, and model consistency over continuous operation to detect excessive memory variance or divergence from the persona baseline.

## Related

- [ContinuityMemory Java Store](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/ContinuityMemory.java)
