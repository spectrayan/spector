---
title: "RELATION_TYPES — Binary Layout Reference"
description: "Interned relation type symbols (causes, depends_on, works_at, etc.)"
---

# 🔗 RELATION_TYPES (`RegionId.RELATION_TYPES`, ID: 21)
> **Interned relation type symbols (causes, depends_on, works_at, etc.)**

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

`RegistryLayout` defines the standard `LAYOUT_ID = 0x52454700` (`'REG\0'`) and a stride of 0 (variable-length). Requires standard `RegionPreamble`.

## Access Patterns & Concurrency

Managed via standard `RegistryMemory` patterns, avoiding locks on hot path lookups.

## Cognitive Role

Interns relation type symbols (e.g., causes, depends_on, works_at) to integer ordinals, preventing string duplication and speeding up graph traversal.

## Related

- [RegionPreamble](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
