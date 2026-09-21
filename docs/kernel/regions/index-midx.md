---
title: "INDEX_MIDX — Binary Layout Reference"
description: "Memory layout for the index entry slot table (48 bytes fixed size, v6)."
---
🗂️ INDEX_MIDX (`RegionId.INDEX_MIDX`, ID: 12)
> **Memory layout for the index entry slot table (48 bytes fixed size, v6).**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RecordMemory |
| **Layout Class** | [`IndexEntryLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/IndexEntryLayout.java) |
| **Store Class** | [`IndexEntryMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/IndexEntryMemory.java) |
| **Record Stride** | 48 bytes |
| **Cache-Line Aligned** | No (48B, 8B aligned) |

## Wire Diagram (48 Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       idPoolOffset (8B)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       idPoolLength (4B)       |       typeOrdinal (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                          offset (8B)                          |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        graphSlot (4B)         |        textOffset (8B) ...    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  ... textOffset (cont'd)      |       textLength (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    colocatedPartition (4B)    |         reserved (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8 | `idPoolOffset` | long | Offset of the ID string in IDPL |
| `0x08` | 4 | `idPoolLength` | int | Length of the ID string |
| `0x0C` | 4 | `typeOrdinal` | int | Memory type ordinal |
| `0x10` | 8 | `offset` | long | Offset in the record region |
| `0x18` | 4 | `graphSlot` | int | Semantic-HNSW / Hebbian node slot |
| `0x1C` | 8 | `textOffset` | long | Offset of the textual representation |
| `0x24` | 4 | `textLength` | int | Length of the textual representation |
| `0x28` | 4 | `colocatedPartition`| int | DISK partition the record lives in (v6) |
| `0x2C` | 4 | `reserved` | int | Must be 0 (for 8-byte alignment) |

## Access Patterns & Concurrency

Fixed-size contiguous records. Used in conjunction with IDPL to provide an index mapping memory UUIDs to internal structures. 

## Cognitive Role

Acts as the master directory of all memory records, allowing fast lookup by ID and providing structural offsets for text and graph representations.

## Related

- [Issue #443 Phase 2 (v6 changes)]
