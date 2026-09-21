---
title: "CONTINUITY — Binary Layout Reference"
description: "Cross-turn session continuity checkpoints"
---

# ⏳ CONTINUITY (`RegionId.CONTINUITY`, ID: 25)
> **Cross-turn session continuity checkpoints**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RecordMemory |
| **Layout Class** | [`ContinuityLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/ContinuityLayout.java) |
| **Store Class** | [`ContinuityMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/ContinuityMemory.java) |
| **Record Stride** | 32 bytes |
| **Cache-Line Aligned** | No (32B stride) |

## Wire Diagram (32 Bytes Record)

The continuity sub-header occupies 32 bytes immediately after the standard 64-byte `RegionPreamble`, followed by a fixed-stride array of 32-byte immutable snapshot records at offset 96.

### Continuity Sub-Header Layout (32 bytes at offset 64)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   headIndex (4B)              | totalSnapshots (4B)           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   lastSnapshotTimestampMs (8B)                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   capacity (4B)               | reserved (4B)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        reserved (8B)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Snapshot Record Layout (32 bytes per record at offset 96 + i*32)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      timestampMs (8B)                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      phiCc (4B)               |     traceG (4B)               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    priorDrift(4B)             | valence(1B) | arousal(1B)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| energy(1B)| soulVersion(2B)   | reserved (5B)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         reserved (2B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
*Note: Layout visualization adjusts exact 1-byte splits for readability.*

## Field Specifications

### Continuity Sub-Header (Offset 64)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4B | `headIndex` | int32 | Index of the most recent snapshot |
| `0x04` | 4B | `totalSnapshots` | int32 | Total snapshots recorded |
| `0x08` | 8B | `lastSnapshotTimestampMs` | int64 | Epoch ms of the last snapshot |
| `0x10` | 4B | `capacity` | int32 | Max number of snapshot records |
| `0x14` | 12B| `reserved` | bytes | Future expansion |

### Snapshot Record
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8B | `timestampMs` | int64 | Snapshot timestamp |
| `0x08` | 4B | `phiCc` | float32 | Continuity scalar |
| `0x0C` | 4B | `traceG` | float32 | Trace gradient |
| `0x10` | 4B | `priorDrift` | float32 | Prior drift scalar |
| `0x14` | 1B | `valence` | int8 | Core affect valence |
| `0x15` | 1B | `arousal` | int8 | Core affect arousal |
| `0x16` | 1B | `energy` | int8 | Cognitive energy level |
| `0x17` | 2B | `soulVersion` | int16 | Kernel identity schema version |
| `0x19` | 7B | `reserved` | bytes | Future expansion |

## Access Patterns & Concurrency

Managed as an append-only ring buffer via `ContinuityMemory`. Immutable snapshots are written serially.

## Cognitive Role

Preserves longitudinal consciousness continuity and identity trajectory, tracking affective and cognitive drifts across conversation turns.

## Related

- [RegionPreamble](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
