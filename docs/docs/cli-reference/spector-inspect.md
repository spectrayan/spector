---
title: "spector-inspect — Diagnostics CLI"
description: "Diagnostics and inspection CLI utility for reading and analyzing off-heap Spector Memory Kernel (SMK) binary files and headers."
---

# 🔍 spector-inspect — Diagnostics CLI

`spector-inspect` is a command-line utility designed for offline diagnostics, introspection, and binary header validation of Spector Memory Kernel (SMK) files. It maps and inspects off-heap segments directly without booting the full JVM memory engine.

---

## Installation & Running

The utility is packaged as part of the `spector-inspect` module:

```bash
java -jar target/spector-inspect-0.1.0-alpha.jar header <file-path>
```

---

## Commands

### `header`

Parses the versioned 64-byte binary header of a memory shape file (e.g., `.mem`, `.dat`) and prints human-readable diagnostics.

#### Usage
```bash
spector-inspect header <file-path>
```

#### Example Output
```text
==================================================
Spector Memory Kernel Header: episodic-20260527.mem
==================================================
Magic:            0x534D4B4D (SMKM)
Schema Version:   1
Memory Shape:     PARTITIONED_RECORD
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
*   **Memory Shape:** The SMK structural shape of this file (e.g., `RECORD`, `PARTITIONED_RECORD`, `APPEND`, `REGISTRY`, `GRAPH`, `CHAIN`).
*   **Flags:** Shape-specific bitwise flag markers.
*   **Capacity:** Bounded limit of records/slots allocated for this segment.
*   **Count:** Number of active, non-tombstoned entries currently materialized.
*   **Record Stride:** Byte stride of each record in the segment (header + payload size).
*   **Layout ID:** The 4-character ASCII layout signature code (e.g., `"COGN"` for cognitive records, `"ENTT"` for entities, `"HEBB"` for Hebbian weights).
*   **Created At:** Time the memory region segment was initialized.
*   **Last Flush:** Monotonic timestamp of the last durable checkpoint flush.
