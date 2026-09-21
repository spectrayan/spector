---
title: "TEMPORAL_CHAIN — Binary Layout Reference"
description: "Memory layout for nodes in the temporal causal chain."
---
⏱️ TEMPORAL_CHAIN (`RegionId.TEMPORAL_CHAIN`, ID: 15)
> **Memory layout for nodes in the temporal causal chain.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | ChainMemory |
| **Layout Class** | [`TemporalLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/TemporalLayout.java) |
| **Store Class** | [`TemporalChainMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/TemporalChainMemory.java) |
| **Record Stride** | 16 bytes |
| **Cache-Line Aligned** | No |

## Wire Diagram (16 Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          prevIdx (4B)         |          nextIdx (4B)         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         sessionId (4B)        |          epochSec (4B)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `prevIdx` | int | Index of previous node in chain |
| `0x04` | 4 | `nextIdx` | int | Index of next node in chain |
| `0x08` | 4 | `sessionId` | int | Cognitive session identifier |
| `0x0C` | 4 | `epochSec` | int | Second-level precision timestamp |

## Access Patterns & Concurrency

Managed as a doubly-linked list structure mapped over dense off-heap memory arrays. Allows chronological and reverse-chronological traversal of memory formations.

## Cognitive Role

Embeds memories in continuous time, supporting timeline recall, causal chain inference, and episodic sequence reconstruction.
