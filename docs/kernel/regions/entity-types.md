---
title: "ENTITY_TYPES — Binary Layout Reference"
description: "Interned entity type symbols (person, organization, location, etc.)"
---

# 🏷️ ENTITY_TYPES (`RegionId.ENTITY_TYPES`, ID: 20)
> **Interned entity type symbols (person, organization, location, etc.)**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RegistryMemory |
| **Layout Class** | [`RegistryLayout`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/RegistryLayout.java) |
| **Store Class** | [`RegistryMemory`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/shape/RegistryMemory.java) |
| **Record Stride** | 0 bytes (variable-length) |
| **Cache-Line Aligned** | No |

## Wire Diagram (Variable Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 Variable-length registry entries              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

`RegistryLayout` defines the standard `LAYOUT_ID = 0x52454700` (`'REG\0'`) and no fixed layout beyond the variable length blobs for registry entries. Uses standard `RegionPreamble`.

## Access Patterns & Concurrency

Managed via standard `RegistryMemory` patterns, avoiding locks on hot path lookups.

## Cognitive Role

Interns entity type symbols (e.g., person, organization, location) to integer ordinals, preventing string duplication and speeding up graph traversal.

## Related

- [RegionPreamble](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
