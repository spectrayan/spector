---
title: "TEXT — Binary Layout Reference"
description: "Variable-length text payload pool."
---
# 📝 TEXT (`RegionId.TEXT`, ID: 3)
> **Memory layout for the variable-length text payload pool.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `partition.bundle` |
| **Memory Shape** | AppendMemory |
| **Layout Class** | [`TextBlobLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/TextBlobLayout.java) |
| **Store Class** | N/A (AppendMemory) |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | No |

## Wire Diagram

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Variable Text Data ...                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

| Offset | Size | Field Name | Type | Description |
|:---:|:---:|:---|:---|:---|
| `0x00` | N | `payload` | bytes | Variable-length blob data |

## Access Patterns & Concurrency

Standard variable append log writes.

## Cognitive Role

Acts as a storage pool for text chunks, documents, and other varying length text elements linked from semantic records or indexes.

## Related
- [`TextBlobLayout.java`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/TextBlobLayout.java)
