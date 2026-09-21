---
title: "ENTITY_NAMES — Binary Layout Reference"
description: "A simple RegionLayout implementation for registries."
---
📛 ENTITY_NAMES (`RegionId.ENTITY_NAMES`, ID: 18)
> **A simple RegionLayout implementation for registries.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | RegistryMemory |
| **Layout Class** | [`RegistryLayout`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/layout/RegistryLayout.java) |
| **Store Class** | `RegistryMemory` based |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | No |

## Wire Diagram (Variable Bytes)

```
Variable-length registry entries encoded in an opaque structure.
No uniform record stride.
```

## Field Specifications

Variable length string dictionary format. Resolves entity string names. Exact format handled opaque by the registry structure.

## Access Patterns & Concurrency

Optimized for fast dictionary / trie style lookups converting entity strings to internal integer IDs.

## Cognitive Role

Supplies the name-to-id bidirectional translation required to interpret internal entity matrices into human-readable symbols.
