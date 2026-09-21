---
title: "SALIENCE — Binary Layout Reference"
description: "Salience profile (ICNU weights, interest topics, modulation constants)."
---
# 💡 SALIENCE (`IdentityRegionId.SALIENCE`, ID: 2)
> **Salience profile (ICNU weights, interest topics, modulation constants).**

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

*Note: SALIENCE utilizes the raw region payload storage of the `IdentityBundle`. Its entry layout is dictated by `IdentityRegionEntry` in the bundle directory, while the region itself is purely a variable-length byte array.*

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

*Payload specific parsing is delegated to higher-level domain models rather than strict layout structs in the memory kernel.*

See [`IdentityRegionEntry`](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityRegionEntry.java) for metadata (offset, size, CRC32C, version).

## Access Patterns & Concurrency

Managed under `ReentrantLock` at the bundle level. Read operations (`IdentityBundle.readRaw`) fetch the payload via mmap segment copies and validate the stored CRC32C against the payload length. Writes perform copy-in and update the directory entry.

## Cognitive Role

Holds dynamic prioritization and attention filters for an entity. Defines ICNU (Important, Critical, Novel, Urgent) base weights and tuning parameters. This acts as the salience network configuration for the cognitive pipeline.

## Related

- [IdentityBundle (Store)](file:///d:/git/spector/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/bundle/identity/IdentityBundle.java)
