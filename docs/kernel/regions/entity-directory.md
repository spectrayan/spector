---
title: "ENTITY_DIRECTORY — Binary Layout Reference"
description: "Memory layout for the kernel-substrate companion that owns entity identity."
---
📇 ENTITY_DIRECTORY (`RegionId.ENTITY_DIRECTORY`, ID: 17)
> **Memory layout for the kernel-substrate companion that owns entity identity.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RecordMemory (composite) |
| **Layout Class** | [`EntityDirectoryLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/EntityDirectoryLayout.java) |
| **Store Class** | [`EntityDirectoryMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/EntityDirectoryMemory.java) |
| **Record Stride** | 64 bytes (node) |
| **Cache-Line Aligned** | Yes (64B) |

## Wire Diagram (64 Bytes Node Slot)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            type (4B)          |             pad (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        nameHash (8B)                          |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         adjOffset (4B)        |         adjCount (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        adjCapacity (4B)       |        mergedInto (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                            pad (32B)                          |
|                 (unused binary-edge region)                   |
|                                                               |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Wire Diagram (8 Bytes Adjacency Entry)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           memIdx (4B)         |          weight (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Sub-header (16 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `adjCapacity` | int | Adjacency-segment capacity in entries |
| `0x04` | 4 | `adjHwm` | int | Adjacency-segment high-water mark in entries |

### Entity Node (64 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `type` | int | Entity type id |
| `0x04` | 4 | `pad` | bytes | Alignment padding |
| `0x08` | 8 | `nameHash` | long | Normalized-name hash |
| `0x10` | 4 | `adjOffset` | int | Index of this entity's block in the adjacency segment |
| `0x14` | 4 | `adjCount` | int | Number of adjacency entries in use |
| `0x18` | 4 | `adjCapacity` | int | Allocated adjacency slots for this entity |
| `0x1C` | 4 | `mergedInto` | int | If merged, the id of the canonical entity, else -1 |

### Adjacency Entry (8 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `memIdx` | int | Memory slot index |
| `0x04` | 4 | `weight` | float | Link weight |

## Access Patterns & Concurrency

Follows an SMKM composite structure where nodes point to blocks of adjacency entries in an adjacent slab. 

## Cognitive Role

Owns entity identity, type mapping, and the authoritative entity-to-memory adjacency mappings. Divorces identity from pure hyperedge storage allowing single-entity memories to be tracked without requiring hyperedge allocation.

## Related

- [ADR-0003, #455 Hypergraph Graduation]
