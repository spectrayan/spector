---
title: "TEMPORAL_FACTS — Binary Layout Reference"
description: "Memory layout for 64-byte temporal fact records."
---
📅 TEMPORAL_FACTS (`RegionId.TEMPORAL_FACTS`, ID: 16)
> **Memory layout for 64-byte temporal fact records.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | AppendMemory |
| **Layout Class** | [`TemporalFactLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/TemporalFactLayout.java) |
| **Store Class** | [`TemporalFactsMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/TemporalFactsMemory.java) |
| **Record Stride** | 64 bytes |
| **Cache-Line Aligned** | Yes (64B) |

## Wire Diagram (64 Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      objectTextOffset (8B)                    |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         validFrom (8B)                        |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                          validTo (8B)                         |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           txTime (8B)                         |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           factId (4B)         |      subjectEntityId (4B)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        predicateId (4B)       |       objectEntityId (4B)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         confidence (4B)       |      retractsFactId (4B)      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           crc32c (4B)         | objTxtLen(2B) | flags |  resv |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 8 | `objectTextOffset` | long | Offset to object textual representation |
| `0x08` | 8 | `validFrom` | long | Valid from timestamp |
| `0x10` | 8 | `validTo` | long | Valid to timestamp |
| `0x18` | 8 | `txTime` | long | Transaction time |
| `0x20` | 4 | `factId` | int | Fact ID |
| `0x24` | 4 | `subjectEntityId` | int | Subject Entity ID |
| `0x28` | 4 | `predicateId` | int | Predicate ID |
| `0x2C` | 4 | `objectEntityId` | int | Object Entity ID |
| `0x30` | 4 | `confidence` | float | Fact confidence score |
| `0x34` | 4 | `retractsFactId` | int | Retracts Fact ID |
| `0x38` | 4 | `crc32c` | int | CRC32C checksum |
| `0x3C` | 2 | `objectTextLength` | short | Length of object text |
| `0x3E` | 1 | `flags` | byte | Bit flags (inferred=0x01, resolved=0x02) |
| `0x3F` | 1 | `reserved` | byte | Reserved for alignment |

## Access Patterns & Concurrency

Append-only ledger of discrete propositional facts over time. The full cache-line size helps rapid analytical scans.

## Cognitive Role

Grounds episodic facts and symbolic relationships, enabling temporal logic queries (what was true at time T).
