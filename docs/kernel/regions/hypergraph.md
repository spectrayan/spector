---
title: "HYPERGRAPH — Binary Layout Reference"
description: "Memory layout for nodes/relations in the Hyper Entity Graph."
---
🌐 HYPERGRAPH (`RegionId.HYPERGRAPH`, ID: 19)
> **Memory layout for nodes/relations in the Hyper Entity Graph.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | GraphMemory |
| **Layout Class** | [`HyperEntityLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/HyperEntityLayout.java) |
| **Store Class** | [`HyperEntityGraphMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/HyperEntityGraphMemory.java) |
| **Record Stride** | 32 bytes |
| **Cache-Line Aligned** | No (32B) |

## Wire Diagram (32 Bytes Hyperedge)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          edgeId (4B)          |           type (4B)           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          weight (4B)          |        vertexCount (4B)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        vertexOffset (4B)      |         memoryIdx (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          timestamp (4B)       |            pad (4B)           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Wire Diagram (8 Bytes Vertex Entry)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         entityId (4B)         |          roleId (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Sub-header (16 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `entityCap` | int | Entity capacity |
| `0x04` | 4 | `nextHyperedgeId` | int | Next free hyperedge id |
| `0x08` | 4 | `nextVertexOffset`| int | Next free vertex offset |
| `0x0C` | 4 | `totalHyperedges` | int | Total (live) hyperedges |

### Hyperedge (32 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `edgeId` | int | Hyperedge ID |
| `0x04` | 4 | `type` | int | Hyperedge Type |
| `0x08` | 4 | `weight` | float | Hyperedge Weight |
| `0x0C` | 4 | `vertexCount` | int | Number of associated vertices |
| `0x10` | 4 | `vertexOffset` | int | Offset into vertex slab |
| `0x14` | 4 | `memoryIdx` | int | Memory Index |
| `0x18` | 4 | `timestamp` | int | Timestamp |

### Vertex (8 Bytes)
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `entityId` | int | Entity ID |
| `0x04` | 4 | `roleId` | int | Role ID within hyperedge |

## Access Patterns & Concurrency

Managed as a pure hyperedge store over an externally-owned dense entity-id space. Utilizes a hyperedge slab for 32-byte properties pointing to a vertex slab for associated entities.

## Cognitive Role

Forms multi-way N-ary relations between concepts and entities, supporting complex composite assertions beyond binary graphs.

## Related

- [Issue #435 (SMKM v2 Container Migration)]
