---
title: "SPLADE — Binary Layout Reference"
description: "Binary snapshot of SPLADE sparse neural index for cold start"
---

# 🌌 SPLADE (`RegionId.SPLADE`, ID: 27)
> **Binary snapshot of SPLADE sparse neural index for cold start**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | AppendMemory |
| **Layout Class** | Raw append stream |
| **Store Class** | N/A |
| **Record Stride** | 0 bytes (variable-length) |
| **Cache-Line Aligned** | No |

## Wire Diagram (Variable Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Serialized SPLADE index snapshot blobs             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

This region utilizes a raw `AppendMemory` append stream format. It contains opaque serialized bytes corresponding to the sparse neural index structures mapping BERT/SPLADE dimensions to post-activation weights.

## Access Patterns & Concurrency

Appended asynchronously on checkpoint events. Scanned serially into memory at startup to prime the index.

## Cognitive Role

Provides persistent cold-start recovery for the SPLADE sparse neural index. Avoids needing to compute heavy transformer inferences against the memory store at startup.

## Related

- [RegionPreamble](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
