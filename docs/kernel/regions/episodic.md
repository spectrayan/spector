---
title: "EPISODIC — Binary Layout Reference"
description: "Log-structured episodic conversation memory store."
---
# 📜 EPISODIC (`RegionId.EPISODIC`, ID: 1)
> **Log-structured episodic conversation memory store (ADR-0010 / ADR-0030, D2 Option B).**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `partition.bundle` |
| **Memory Shape** | AppendMemory |
| **Layout Class** | [`EpisodicLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/EpisodicLayout.java) |
| **Store Class** | [`EpisodicMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/EpisodicMemory.java) |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | No (Variable log-structured framing) |

## Wire Diagram (Variable Bytes)

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       payloadBytes (4B)                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       sequence_id (4B)                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        checksum (4B)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                          magic (4B)                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   EncodingHeader (64B) ...                    |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|              payload (conversation metadata + CBOR body) ...  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 4 | `payloadBytes` | int32 | Number of bytes in the payload |
| `0x04` | 4 | `sequence_id` | int32 | Incremental sequence ID |
| `0x08` | 4 | `checksum` | int32 | CRC32C over sequenceId, 64B header, and payload |
| `0x0C` | 4 | `magic` | int32 | Magic number `0x45504953` ('EPIS') |
| `0x10` | 64 | `EncodingHeader` | bytes | 64-byte EpisodicHeaderLayout (val, arousal, flags, timestamp, etc) |
| `0x50` | N | `payload` | bytes | Variable-length CBOR payload + conversation metadata |

## Access Patterns & Concurrency

Log-structured append-only operations. Writes are locked via `ReentrantLock`. Reads (scans) can occur concurrently while validating checksums and parsing framing headers.

## Cognitive Role

Acts as the episodic log of conversation turns. Every interaction is recorded sequentially. These experiences are later processed during offline consolidation and promoted into semantic and procedural stores.

## Related
- [`EpisodicMemory.java`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/EpisodicMemory.java)
- [`EpisodicLayout.java`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/EpisodicLayout.java)
