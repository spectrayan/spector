---
title: "PROVENANCE — Binary Layout Reference"
description: "Episodic→Semantic lineage tracking and verification"
---

# 📜 PROVENANCE (`RegionId.PROVENANCE`, ID: 26)
> **Episodic→Semantic lineage tracking and verification**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RecordMemory |
| **Layout Class** | [`ProvenanceLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/ProvenanceLayout.java) |
| **Store Class** | [`ProvenanceMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/ProvenanceMemory.java) |
| **Record Stride** | 72 bytes |
| **Cache-Line Aligned** | No |

## Wire Diagram (72 Bytes)

Each 72-byte record captures the lineage between a batch of episodic conversation turns and a single consolidated semantic (or procedural) memory.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|flg|src|tgt|pfx| pass_number   |   turn_count  | session_id... |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      ...session_id                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       target_tsid                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       target_tsid...                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    consolidated_at_ms                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 consolidated_at_ms...                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      partition_seq            |      first_seq                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         last_seq              |      first_offset_hint        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      last_offset_hint         |fidx|bfct| content_hash_hi   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       reserved (8B)                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       reserved (cont.)        |     reserved_2 (4B)           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          crc32c (4B)          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | 1B | `flags` | uint8 | 0=LIVE, 1=TOMBSTONE, 2=PARTIAL_RUN |
| `0x01` | 1B | `source_kind` | uint8 | EPISODIC_LOG = 1 |
| `0x02` | 1B | `target_kind` | uint8 | SEMANTIC = 2, PROCEDURAL = 3 |
| `0x03` | 1B | `prefix_kind` | uint8 | Target ID prefix registry ordinal |
| `0x04` | 2B | `pass_number` | uint16 | Monotonic consolidation pass counter |
| `0x06` | 2B | `turn_count` | uint16 | Turns covered by this row |
| `0x08` | 8B | `session_id` | int64 | Matches episodic header session_id |
| `0x10` | 8B | `target_tsid` | int64 | Raw 64-bit TSID of consolidated fact |
| `0x18` | 8B | `consolidated_at_ms` | int64 | Epoch ms of the consolidation pass |
| `0x20` | 4B | `partition_seq` | int32 | Partition holding source turns |
| `0x24` | 4B | `first_seq` | int32 | First episodic sequence_id in the run |
| `0x28` | 4B | `last_seq` | int32 | Last episodic sequence_id in the run |
| `0x2C` | 4B | `first_offset_hint` | uint32 | Region-relative byte offset hint |
| `0x30` | 4B | `last_offset_hint` | uint32 | Region-relative byte offset hint |
| `0x34` | 1B | `fact_index` | uint8 | Index of this fact within its batch |
| `0x35` | 1B | `batch_fact_count` | uint8 | Total facts in this batch |
| `0x36` | 2B | `content_hash_hi` | uint16 | Upper 16 bits of fact text CRC32C |
| `0x38` | 8B | `reserved` | bytes | Zero-filled |
| `0x40` | 4B | `reserved_2` | bytes | Zero-filled |
| `0x44` | 4B | `crc32c` | int32 | Record-level CRC32C checksum |

## Access Patterns & Concurrency

Appended continuously during memory consolidation passes. Reads occur linearly or via binary search over `target_tsid` during provenance tracing.

## Cognitive Role

Acts as the episodic→semantic audit log. It maps extracted knowledge back to the raw conversation turns that produced it, enabling memory traceability, truth verification, and recursive consolidation.

## Related

- [RegionPreamble](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
