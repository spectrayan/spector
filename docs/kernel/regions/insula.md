---
title: "INSULA — Binary Layout Reference"
description: "Dynamic self-model container for agent state, confidence, and affective markers"
---

# 🌡️ Insula (`RegionId.INSULA`, ID: 24)
> **Dynamic self-model container for agent state, confidence, and affective markers**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | Insular |
| **Layout Class** | [`InsularLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/InsularLayout.java) |
| **Store Class** | [`InsulaMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/InsulaMemory.java) |
| **Record Stride** | 0 bytes (variable-length single JSON blob) |
| **Cache-Line Aligned** | No (Sub-header is 32B, sits at offset 64) |

## Wire Diagram (32 Bytes Header)

The insular header occupies 32 bytes immediately after the standard 64-byte `RegionPreamble`, giving an overall 96-byte overhead before the self-model JSON payload begins.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    version (4B)               |  dataLength (4B)              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       updatedAt (8B)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    checksum (4B)              |  flags (4B)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    reserved (8B)                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4B | `version` | int32 | Monotonically increasing version counter |
| `0x04` | 4B | `dataLength` | int32 | Length of the self-model JSON payload in bytes |
| `0x08` | 8B | `updatedAt` | int64 | Epoch milliseconds of last update |
| `0x10` | 4B | `checksum` | int32 | CRC32C checksum of the JSON payload |
| `0x14` | 4B | `flags` | int32 | 0 = EMPTY, 1 = PRESENT |
| `0x18` | 8B | `reserved` | bytes | Reserved for future use |

## Access Patterns & Concurrency

Managed as a single variable-length blob with CRC validation. Typically updated atomically via a lock or single-writer paradigm on state transitions.

## Cognitive Role

The dynamic self-model container. It holds the agent's internal self-state, confidence baseline, and affective context.

## Related

- [RegionPreamble](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
