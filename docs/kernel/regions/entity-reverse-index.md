---
title: "ENTITY_REVERSE_INDEX — Binary Layout Reference"
description: "Reserved region for future entity-to-memory reverse projections"
---

# 🔄 ENTITY_REVERSE_INDEX (`RegionId.ENTITY_REVERSE_INDEX`, ID: 28)
> **Reserved region for future entity-to-memory reverse projections**

---

## Overview

| Property | Value |
|:---|:---|
| **Bundle** | `runtime.bundle` |
| **Memory Shape** | Reserved (Not implemented) |
| **Layout Class** | N/A |
| **Store Class** | N/A |
| **Record Stride** | N/A |
| **Cache-Line Aligned** | N/A |

## Wire Diagram

*Region is reserved and currently not implemented.*

## Field Specifications

*Reserved.*

## Access Patterns & Concurrency

*Reserved.*

## Cognitive Role

Planned support for fast entity-to-memory reverse projections, mapping explicit entity symbols back to all episodic and semantic memory fragments that reference them.

## Related

- [RegionPreamble](https://github.com/spectrayan/spector/blob/main/memory/spector-kernel/src/main/java/com/spectrayan/spector/kernel/region/RegionPreamble.java)
