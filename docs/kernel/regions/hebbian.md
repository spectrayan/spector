---
title: "HEBBIAN — Binary Layout Reference"
description: "Memory layout for edges in the Hebbian Graph CSR."
---
🕸️ HEBBIAN (`RegionId.HEBBIAN`, ID: 14)
> **Memory layout for edges in the Hebbian Graph CSR.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | GraphMemory |
| **Layout Class** | [`HebbianLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/HebbianLayout.java) |
| **Store Class** | [`HebbianGraphMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/HebbianGraphMemory.java) |
| **Record Stride** | 12 bytes |
| **Cache-Line Aligned** | No |

## Wire Diagram (12 Bytes Edge)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          neighbor (4B)        |           weight (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       lastCycle (2B)  | bridge| flags |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Sub-header (16 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `edgeCapacity` | int | Edge-slab capacity |
| `0x04` | 4 | `currentCycle` | int | Current reflection cycle |

### Edge Slot (12 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `neighbor` | int | Target vertex index |
| `0x04` | 4 | `weight` | float | Association weight |
| `0x08` | 2 | `lastCycle` | uint16 | Last reflection cycle the edge was touched |
| `0x0A` | 1 | `bridgeScore`| uint8 | Bridge score for eviction protection |
| `0x0B` | 1 | `edgeFlags` | byte | Edge flags |

## Access Patterns & Concurrency

Managed as a Compressed Sparse Row (CSR) structure for dense graph operations. The offset slab manages adjacency lists, while the edge slab contains these 12-byte edge records.

## Cognitive Role

Forms the associative sub-symbolic network between discrete engrams, strengthening links that fire together to support spreading activation.

## Related

- [Issue #435 (SMKM Container Sub-header Framing)]
