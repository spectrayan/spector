---
title: "HEADER — Binary Layout Reference"
description: "Magic, schema version, and 16-entry region directory for Identity Bundles."
---
# 🔖 HEADER (`IdentityRegionId.HEADER`, ID: 0)
> **Magic, schema version, and 16-entry region directory for Identity Bundles.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `identity.bundle` |
| **Memory Shape** | BUNDLE |
| **Layout Class** | [`IdentityBundleHeader`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundleHeader.java), [`IdentityRegionEntry`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityRegionEntry.java) |
| **Store Class** | [`IdentityBundle`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java) |
| **Record Stride** | 64 bytes (Per Region Entry) |
| **Cache-Line Aligned** | Yes (64B) |

## Wire Diagram (1152 Bytes Initialized)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       64B RegionPreamble (SMKM, BUNDLE, layout=IDNT)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 64)
|        Sub-Magic (SIDB)       |         Version (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      Max Regions (16) (4B)    |       Data Start (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Total Size (8B)                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Reserved (40B)                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 128)
| 16 x 64B IdentityRegionEntry directory (Starts here)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 1152)
```

**Individual `IdentityRegionEntry` (64 Bytes per entry):**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  regionCode(2)|    flags(2)   |          reserved(4)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     offset (8B)                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  allocatedSize (8B)                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     usedSize (8B)                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         version (4B)          |         checksum (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    updatedAt (8B)                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    reserved (16B)                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

### Identity Sub-Header
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x40` | 4B | `subMagic` | int | 'SIDB' (0x53494442) |
| `0x44` | 4B | `version` | int | Schema version (1) |
| `0x48` | 4B | `maxRegions` | int | Maximum number of regions (16) |
| `0x4C` | 4B | `dataStartOffset` | int | Offset where payloads begin (4096) |
| `0x50` | 8B | `totalSize` | long | Total initial size of bundle (1,052,672) |

### Identity Region Entry
| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 2B | `regionId` | short | Numeric region ID (0..15) |
| `0x02` | 2B | `flags` | short | 0x00=EMPTY, 0x01=PRESENT, 0x02=DIRTY |
| `0x04` | 4B | `reserved` | int | Reserved |
| `0x08` | 8B | `offset` | long | Byte offset of payload in bundle file |
| `0x10` | 8B | `allocatedSize` | long | Bytes allocated for this region (Default 64KB) |
| `0x18` | 8B | `usedSize` | long | Current bytes used by payload |
| `0x20` | 4B | `version` | int | Incrementing mutation version |
| `0x24` | 4B | `checksum` | int | CRC32C checksum of the payload bytes |
| `0x28` | 8B | `updatedAt` | long | Epoch ms timestamp of last update |
| `0x30` | 16B | `reserved` | bytes | Reserved for future use |

## Access Patterns & Concurrency

Managed under `ReentrantLock` in `IdentityBundle`. Single-writer thread-safe when altering the payload of individual regions. Changes increment the entry's `version` counter and update `updatedAt` and `checksum`.

## Cognitive Role

The `IdentityBundleHeader` coordinates the `identity.bundle`, allocating an independent mmap structure that holds crucial policy, identity, and parameter definitions separate from the operational runtime or partition graphs.

## Related

- [ADR-0029 Episodic-Semantic Lineage & Provenance Region](../../adr/0029-episodic-semantic-lineage-provenance-region.md)
