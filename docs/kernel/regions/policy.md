---
title: "POLICY — Binary Layout Reference"
description: "Tenant compliance policies, governance floors, and domain constraints."
---
# 📜 POLICY (`IdentityRegionId.POLICY`, ID: 4)
> **Tenant compliance policies, governance floors, and domain constraints.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `identity.bundle` |
| **Memory Shape** | Raw Payload (Managed by Bundle) |
| **Layout Class** | N/A (Standard `IdentityBundle` region payload) |
| **Store Class** | [`IdentityBundle`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java) |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | Payload page-aligned initially |

## Wire Diagram (Variable Length Payload)

*Note: POLICY utilizes the raw region payload storage of the `IdentityBundle`. Its entry layout is dictated by `IdentityRegionEntry` in the bundle directory.*

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|        Raw Payload Array (Size stored in RegionEntry)         |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Field Specifications

No structured memory kernel layout exists for this region natively. Payload structural mapping occurs dynamically at domain-level layers, typically resolving to strict JSON or Protobuf rule engine directives.

## Access Patterns & Concurrency

Managed under `ReentrantLock` at the bundle level. Read operations (`IdentityBundle.readRaw`) fetch the payload via mmap segment copies and validate the stored CRC32C. Writes replace the block and increment the metadata version.

## Cognitive Role

Acts as the governance boundary for an identity context, strictly imposing floors for behavioral bounds, data compliance rules, and external action throttling that are evaluated globally across the agent memory operations.

## Related

- [IdentityBundle (Store)](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java)
