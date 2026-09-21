---
title: "ORG_DIR — Binary Layout Reference"
description: "Tenant organizational unit directory and soul slabs."
---
# 🏢 ORG_DIR (`IdentityRegionId.ORG_DIR`, ID: 5)
> **Tenant organizational unit directory and soul slabs.**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `identity.bundle` |
| **Memory Shape** | Raw Payload (Managed by Bundle) |
| **Layout Class** | N/A (Standard `IdentityBundle` region payload) |
| **Store Class** | [`IdentityBundle`](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java) |
| **Record Stride** | Variable |
| **Cache-Line Aligned** | Payload page-aligned initially |

## Wire Diagram (Variable Length Payload)

*Note: ORG_DIR utilizes the raw region payload storage of the `IdentityBundle`. Its entry layout is dictated by `IdentityRegionEntry` in the bundle directory.*

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

Uses generic un-structured or domain-level managed blobs. Kernel metadata tracking offset, allocated/used size, versioning, and CRC resides in the 64-byte `IdentityRegionEntry` within the Header directory block.

## Access Patterns & Concurrency

Managed under `ReentrantLock` at the bundle level via the `IdentityBundle.readRaw` and `IdentityBundle.writeRaw` methods. 

## Cognitive Role

Enables grouping of sub-agents or sub-entities underneath a parent Tenant identity. Holds structural relationships for organizational hierarchy that govern broader namespace authorization inside the cognitive memory scope.

## Related

- [IdentityBundle (Store)](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java)
