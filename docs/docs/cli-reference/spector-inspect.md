---
title: "spector-inspect — Diagnostics CLI"
description: "Diagnostics and inspection CLI utility for reading and analyzing off-heap Spector Memory Kernel (SMK) binary files and V4 growable bundles."
---

# 🔍 `spector-inspect` — Diagnostics CLI

`spector-inspect` is a command-line utility designed for offline diagnostics, low-level introspection, and binary header validation of Spector Memory Kernel (SMK) files and V4 bundles (`partition.bundle`, `runtime.bundle`, `identity.bundle`). It maps and inspects off-heap segments directly without booting the full JVM memory engine.

---

## 📦 Installation & Running

The utility is packaged as part of the `spector-inspect` module:

```bash
java -jar target/spector-inspect-0.1.0-beta.jar <subcommand> <file-path>
```

---

## 📋 Subcommands

### 1. `bundle` — Inspect V4 Single-VMA Bundles

Parses the bundle directory table, sub-header, and internal region entries (Working, Episodic, Semantic, Procedural, Strength, Graph, WAL) for any V4 bundle container.

#### Usage
```bash
spector-inspect bundle <bundle-path>
```

#### Example Output
```text
==================================================================================
Spector Memory Bundle Diagnostics: partition.bundle
==================================================================================
Magic:              0x534D4B4D (SMKM)
Layout ID:          0x424E444C ("BNDL")
Bundle Type:        0x50415254 ("PART")
Bundle Version:     4
File Size (Header): 20971520 bytes
File Size (Actual): 20971520 bytes
Directory Checksum: 0x8F34A12B00C49102
Data Start Offset:  4096 bytes (page-aligned)
Created At:         Wed May 27 10:00:00 UTC 2026 (1777284000000)
Last Modified:      Wed May 27 10:30:00 UTC 2026 (1777285800000)
Regions Capacity:   16 max, 5 live
================================================----------------------------------
#  Region ID        Status Offset       Allocated    Used         Capacity Stride   Layout ID  Version
----------------------------------------------------------------------------------
0  EPISODIC         LIVE   4096         8320000      4193280      10000    832      COGN       2      
1  STRENGTH         LIVE   8324096      960000       483840       10000    96       STRG       1      
2  TEXT_BLOB        LIVE   9284096      4194304      1048576      10000    varies   BLOB       1      
3  ASSOCIATION      LIVE   13478400     2097152      524288       50000    40       HEBB       1      
4  WAL              LIVE   15575552     1048576      131072       10000    64       WALL       1      
----------------------------------------------------------------------------------
Allocated: 16.62 MB | Live Used: 6.38 MB | Utilization: 38.38%
==================================================================================
```

---

### 2. `header` — Inspect Standalone Region Segments

Parses the 64-byte `RegionPreamble` of an individual unbundled memory shape file.

#### Usage
```bash
spector-inspect header <file-path>
```

#### Example Output
```text
==================================================
Spector Memory Kernel Header: episodic-sample.mem
==================================================
Magic:            0x534D4B4D (SMKM)
Schema Version:   2
Memory Shape:     RECORD
Flags:            0x00000000
Capacity:         10000
Count:            5042
Record Stride:    832 bytes
Layout ID:        0x434F474E ("COGN")
Created At:       Wed May 27 10:00:00 UTC 2026 (1777284000000)
Last Flush:       Wed May 27 10:30:00 UTC 2026 (1777285800000)
==================================================
```

#### Field Details

*   **Magic:** The SMK file identifier signature (`0x534D4B4D` which translates to ASCII string `"SMKM"`).
*   **Schema Version:** Stamped layout version used for compatibility checks.
*   **Memory Shape:** The structural shape of this file (e.g., `RECORD`, `APPEND`, `REGISTRY`, `GRAPH`, `CHAIN`, `HASH_TABLE`, `INSULAR`, `BUNDLE`).
*   **Flags:** Shape-specific bitwise flag markers.
*   **Capacity:** Bounded limit of records/slots allocated for this segment.
*   **Count:** Number of active, non-tombstoned entries currently materialized.
*   **Record Stride:** Byte stride of each record in the segment (header + payload size).
*   **Layout ID:** The 4-character ASCII layout signature code (e.g., `"COGN"` for cognitive records, `"STRG"` for strength records, `"ENTT"` for entities, `"HEBB"` for Hebbian weights).
*   **Created At:** Time the memory region segment was initialized.
*   **Last Flush:** Monotonic timestamp of the last durable checkpoint flush.
