---
title: "COACTIVATION — Binary Layout Reference"
description: "Memory layout descriptor for the co-activation tracker's compound hash tables."
---
🔗 COACTIVATION (`RegionId.COACTIVATION`, ID: 11)
> **Memory layout descriptor for the co-activation tracker's compound hash tables.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | HashTableMemory |
| **Layout Class** | [`CoActivationLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/CoActivationLayout.java) |
| **Store Class** | [`CoActivationMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/CoActivationMemory.java) |
| **Record Stride** | N/A (1 for compatibility) |
| **Cache-Line Aligned** | No |

## Wire Diagram (32 Bytes Pair Slot)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         hashA (8B)                            |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         hashB (8B)                            |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           count (4B)          |           flags (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                            pad (8B)                           |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Wire Diagram (40 Bytes Edge Slot)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        srcHash (8B)                           |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        tgtHash (8B)                           |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          weight (4B)          |             pad (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    lastActivatedMs (8B)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     activationCount (4B)      |           flags (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Sub-header (8 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `pairCapacity` | int | Capacity of pair table |
| `0x04` | 4 | `edgeCapacity` | int | Capacity of edge table |

### Pair Slot (32 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8 | `hashA` | long | First tag hash |
| `0x08` | 8 | `hashB` | long | Second tag hash |
| `0x10` | 4 | `count` | int | Co-activation count |
| `0x14` | 4 | `flags` | int | Flags bitfield |

### Edge Slot (40 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8 | `srcHash` | long | Source tag hash |
| `0x08` | 8 | `tgtHash` | long | Target tag hash |
| `0x10` | 4 | `weight` | float | STDP weight |
| `0x14` | 4 | `pad` | bytes | Alignment padding |
| `0x18` | 8 | `lastActivatedMs` | long | Epoch millis of last activation |
| `0x20` | 4 | `activationCount` | int | Total activation count |
| `0x24` | 4 | `flags` | int | Flags bitfield |

## Access Patterns & Concurrency

Managed as an open-addressing hash table in off-heap memory. Provides lock-free or synchronized primitive accesses to track tag and entity co-occurrence during cognition.

## Cognitive Role

Supports associative learning by tracking undirected co-occurrences (Pair Table) and directed sequences (STDP Edge Table) between concepts in active memory.

## Related

- [ADR-0009: Co-activation Hash Tables]
