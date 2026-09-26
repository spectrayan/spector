---
title: "STRENGTH — Binary Layout Reference"
description: "Off-heap Recall Audit Region storing mutable recall telemetry, LTP counters, and storage strength."
---
# 🏋️ STRENGTH (`RegionId.STRENGTH`, ID: 4)
> **Memory layout descriptor for the off-heap Recall Audit Region.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `partition.bundle` |
| **Memory Shape** | RecordMemory |
| **Layout Class** | [`StrengthLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/StrengthLayout.java) |
| **Store Class** | [`StrengthMemory`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/StrengthMemory.java) |
| **Record Stride** | 96 bytes |
| **Cache-Line Aligned** | No (32B Aligned) |

## Wire Diagram (96 Bytes)

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|aud_flg|lst_pro|lst_val| pad0  |    agent_recall_count (4B)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    spector_recall_cnt (4B)    |  effective_importance (4B)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     storage_strength (4B)     |      last_agent_hash (4B)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        last_auto_ltp (8B)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       last_recall_ts (8B)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                 act_r_ring_buffer (int32[8])                  |
|                           (32B)                               |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    reconsolidation_delta (8B)                 |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                        _reserved (16B)                        |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 1 | `audit_flags` | uint8 | Bits 0-1: memory_type ordinal |
| `0x01` | 1 | `last_recall_profile` | uint8 | Profile ordinal of last recall |
| `0x02` | 1 | `last_recall_valence` | int8 | Signed emotional valence feedback |
| `0x03` | 1 | `_pad0` | bytes | Alignment padding |
| `0x04` | 4 | `agent_recall_count` | int32 | Explicit agent reinforcement counter |
| `0x08` | 4 | `spector_recall_cnt` | int32 | Passive auto-LTP retrieval counter |
| `0x0C` | 4 | `effective_importance` | float32 | Mutable importance (ICNU re-fusions) |
| `0x10` | 4 | `storage_strength` | float32 | Two-Factor Bjork S(t) in [1.0, 5.0] |
| `0x14` | 4 | `last_agent_hash` | uint32 | MurmurHash3 of caller agent ID |
| `0x18` | 8 | `last_auto_ltp` | int64 | Epoch ms of last auto-LTP cooldown |
| `0x20` | 8 | `last_recall_ts` | int64 | Epoch ms of most recent recall |
| `0x28` | 32 | `act_r_ring_buffer` | int32[8] | 8 relative-second recall timestamps |
| `0x48` | 8 | `reconsolidation_delta` | int64 | Micro-plasticity weight shift history |
| `0x50` | 16 | `_reserved` | bytes | Zero-padded reserved block |

## Access Patterns & Concurrency

Highly concurrent. Telemetry fields use `VarHandle CAS` and `VarHandle Add` for lock-free updates to avoid blocking reads on the hot path. 

## Cognitive Role

Separates mutable recall telemetry, Long-Term Potentiation (LTP) counters, Two-Factor storage strength, and ACT-R recall timestamp ring buffers from the read-mostly 64-byte encoding headers in other tiers. This prevents false sharing and CPU cache invalidation during sequential SIMD scoring, mapping all tiers (Semantic, Episodic, Procedural) via deterministic deterministic offsets.

## Related

- [`StrengthLayout.java`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/StrengthLayout.java)
- [`StrengthMemory.java`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/StrengthMemory.java)
