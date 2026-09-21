---
title: "CHECKPOINT — Binary Layout Reference"
description: "Namespace recovery checkpoint metadata"
---

# 💾 CHECKPOINT (`RegionId.CHECKPOINT`, ID: 23)
> **Namespace recovery checkpoint metadata**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RegionPreamble Only |
| **Layout Class** | [`RegionPreamble`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java) |
| **Store Class** | N/A |
| **Record Stride** | N/A |
| **Cache-Line Aligned** | Yes (64B) |

## Wire Diagram (64 Bytes)

The CHECKPOINT region relies primarily on the `RegionPreamble` to store kernel-wide metadata (such as the high-water mark for the WAL and co-activation metadata serialization parameters).

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      magic (0x534D4B4D)                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        schemaVersion          |             shape             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            flags              |                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               +
|                           capacity                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                            count                              |
+                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                               |         recordStride          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           layoutId            |                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               +
|                       createdAtEpochMs                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       lastFlushEpochMs                        |
+                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                               |         headerCrc32c          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           reserved            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4B | `magic` | int32 | `0x534D4B4D` ('SMKM') |
| `0x04` | 4B | `schemaVersion` | int32 | Region schema version |
| `0x08` | 4B | `shape` | int32 | MemoryShape ordinal |
| `0x0C` | 4B | `flags` | int32 | bit0=persistent, bit1=encrypted, bit2=dirty |
| `0x10` | 8B | `capacity` | int64 | Metadata capacity |
| `0x18` | 8B | `count` | int64 | Used for WAL HWM or element count |
| `0x20` | 4B | `recordStride` | int32 | Stride in bytes (often 0 here) |
| `0x24` | 4B | `layoutId` | int32 | Target Layout ID |
| `0x28` | 8B | `createdAtEpochMs` | int64 | Region creation timestamp |
| `0x30` | 8B | `lastFlushEpochMs` | int64 | Last checkpoint sync timestamp |
| `0x38` | 4B | `headerCrc32c` | int32 | CRC32C over bytes 0-55 |
| `0x3C` | 4B | `reserved` | bytes | Must be 0 |

## Access Patterns & Concurrency

Written exclusively by the checkpointing thread during a sync cycle. Read during startup namespace recovery.

## Cognitive Role

Acts as the namespace recovery anchor, holding high-water marks and diagnostic metadata to resume cleanly from the last synchronized snapshot.

## Related

- [RegionPreamble](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
