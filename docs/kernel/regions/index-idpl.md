---
title: "INDEX_IDPL — Binary Layout Reference"
description: "Memory layout for the variable-length ID/metadata payload pool."
---
🗃️ INDEX_IDPL (`RegionId.INDEX_IDPL`, ID: 13)
> **Memory layout for the variable-length ID/metadata payload pool.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | AppendMemory |
| **Layout Class** | [`IdBlobLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/IdBlobLayout.java) |
| **Store Class** | [`IndexEntryMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/store/IndexEntryMemory.java) |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | No |

## Wire Diagram (Variable Bytes)

```
Variable length blob region containing concatenated UTF-8 ID strings.
Addressed dynamically via offset/length pairs in MIDX.
```

## Field Specifications

This region contains variable-length metadata and ID strings appended sequentially. It has no fixed fields; structures are resolved via offsets stored in the `INDEX_MIDX` layout.

## Access Patterns & Concurrency

Append-only log style allocation. Usually queried by resolving an `offset` and `length` from `INDEX_MIDX`.

## Cognitive Role

Stores the actual string identifiers (UUIDs, URIs) and arbitrary metadata for memories mapped by the main index, keeping the main index slots fixed-size.

## Related

- `IndexEntryLayout`
