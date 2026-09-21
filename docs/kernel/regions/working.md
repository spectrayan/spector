---
title: "WORKING — Binary Layout Reference"
description: "Dedicated record layout for the Working memory tier (ADR-0030)."
---
🧠 WORKING (`RegionId.WORKING`, ID: 10)
> **Dedicated record layout for the Working memory tier (ADR-0030).**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RecordMemory |
| **Layout Class** | [`WorkingLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/WorkingLayout.java) |
| **Store Class** | [`DefaultEngramMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/DefaultEngramMemory.java) |
| **Record Stride** | 64 bytes (header) + quantized vector bytes |
| **Cache-Line Aligned** | Yes (64B Header) |

## Wire Diagram (64 Bytes Header)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|version| flags |valence|arousal|        importance (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       timestamp_ms (8B)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        exact_norm (4B)        | centroid (2B) |   _pad0 (2B)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     synaptic_tags_lo (8B)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     synaptic_tags_hi (8B)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|c_flags|e_prof |e_alpha|e_beta | soul_ver (2B) | source| _pad  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     encoding_surprise (4B)    |        _reserved (12B)        |
|                               |                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 1 | `header_version` | uint8 | Format version (always 2) |
| `0x01` | 1 | `flags` | uint8 | Tombstone, memory type, consolidated, pinned, resolved, modality |
| `0x02` | 1 | `valence` | int8 | Initial signed emotional valence (-128 to +127) |
| `0x03` | 1 | `arousal` | uint8 | Initial unsigned emotional arousal (0-255) |
| `0x04` | 4 | `importance` | float32 | Initial base importance score |
| `0x08` | 8 | `timestamp_ms` | int64 | Unix epoch ms when memory was formed |
| `0x10` | 4 | `exact_norm` | float32 | L2 norm of unquantized vector |
| `0x14` | 2 | `centroid_id` | int16 | IVF partition routing cluster ID |
| `0x16` | 2 | `_pad0` | bytes | Alignment padding |
| `0x18` | 8 | `synaptic_tags_lo` | uint64 | 128-bit Bloom filter low 64 bits |
| `0x20` | 8 | `synaptic_tags_hi` | uint64 | 128-bit Bloom filter high 64 bits |
| `0x28` | 1 | `consolidation_flags` | uint8 | Provenance flags |
| `0x29` | 1 | `encoding_profile` | uint8 | Cognitive state at ingestion (bit 7=soul-derived) |
| `0x2A` | 1 | `encoding_alpha` | uint8 | Quantized alpha weight at ingestion (0-255) |
| `0x2B` | 1 | `encoding_beta` | uint8 | Quantized beta weight at ingestion (0-255) |
| `0x2C` | 2 | `soul_version` | uint16 | Monotonic soul configuration generation counter |
| `0x2E` | 1 | `source` | uint8 | Trace source: experienced(0), distilled(1), simulated(2), rehearsed(3) |
| `0x2F` | 1 | `_pad_source` | bytes | Alignment padding |
| `0x30` | 4 | `encoding_surprise` | float32 | Bayesian surprise z-score at ingestion |
| `0x34` | 12 | `_reserved` | bytes | Zero-padded reserved block for neural tensor invariants |

## Access Patterns & Concurrency

Managed as a standard EngramMemory ring-buffer or layout block. Read-mostly after formation, with flags atomically updatable.

## Cognitive Role

Working memory tier representation, serving as the fast-access buffer for recent encoding before consolidation.

## Related

- [`WorkingHeaderLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/engram/WorkingHeaderLayout.java)
- [`EncodingHeaderFields`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/engram/field/EncodingHeaderFields.java)
