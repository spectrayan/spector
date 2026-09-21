---
title: "SOUL — Binary Layout Reference"
description: "Self-model soul context (UserSoul, AgentSoul, or TenantSoul)."
---
# 👻 SOUL (`IdentityRegionId.SOUL`, ID: 1)
> **Self-model soul context (UserSoul, AgentSoul, or TenantSoul).**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `identity.bundle` |
| **Memory Shape** | INSULAR |
| **Layout Class** | [`InsularLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/InsularLayout.java) |
| **Store Class** | [`InsulaMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/InsulaMemory.java) |
| **Record Stride** | Variable (Single JSON blob) |
| **Cache-Line Aligned** | Yes (Headers) |

## Wire Diagram (96 Bytes Header)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       64B RegionPreamble (SMKM, INSULAR, layout=INSL)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 64)
|         version (4B)          |       dataLength (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      updatedAt (8B)                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        checksum (4B)          |          flags (4B)           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        reserved (8B)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ (Offset 96)
|                Variable Length JSON Payload                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x40` | 4B | `version` | int | Monotonically increasing version counter |
| `0x44` | 4B | `dataLength` | int | Length of the self-model JSON payload |
| `0x48` | 8B | `updatedAt` | long | Epoch milliseconds of last update |
| `0x50` | 4B | `checksum` | int | CRC32C checksum of the JSON payload |
| `0x54` | 4B | `flags` | int | 0 = EMPTY, 1 = PRESENT |
| `0x58` | 8B | `reserved` | bytes | Reserved for future use |

## Access Patterns & Concurrency

Managed via `ReentrantLock` within `InsulaMemory`. Reads compute and verify the CRC32C checksum against the JSON payload to prevent silent corruption. 

## Cognitive Role

Stores the self-model region, acting as the central definitional reference for who or what the identity is. Maintains a persistent JSON representation of the entity (AgentSoul, UserSoul).

## Related

- [`InsularLayout` Java Source](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/InsularLayout.java)
