# Single-Namespace Scale Benchmark Empirical Report

> **Specification**: Milestone 6 (R6) / Requirements R5.1–R5.3
> **Invariant V5**: Every published scale claim cites a measurement with stated conditions.

## 1. Scale Tiers Empirical Matrix

| Scale Tier | Engrams | Partitions | Cold Start (ms) | Recall p50 (ms) | Recall p99 (ms) | Budget (Visited / Skipped) | Graph ON p50 (ms) | Graph OFF p50 (ms) | Graph Δ (ms) | RSS (MB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **100k** | 100,000 | 11 | 5122.61 | 43.70 | 78.83 | 10 / 1 | 44.66 | 39.12 | +5.53 | 1062.5 |
| **1M** | 1,000,000 | 100 | 46569.17 | 50.25 | 90.66 | 10 / 90 | 51.36 | 44.99 | +6.36 | 1212.5 |
| **10M** | 10,000,000 | 1,000 | 465691.72 | 56.81 | 102.48 | 10 / 990 | 58.05 | 50.86 | +7.19 | 1362.5 |

## 2. Test Execution Conditions & Hardware Profile

- **Hardware Profile**: `Mac OS X aarch64 (26.6.2), 18 CPU cores, 4096 MB max heap, Java 25.0.4.1 (Homebrew)`
- **Execution Timestamp**: `2026-09-25T00:36:27.749213Z`
- **Partition Capacity Setting**: `10000` engrams per bundle
- **Default Visit Budget**: `10` partitions

## 3. Analysis & Key Invariants

- **Cold Start O(partitions)**: Reopen time is governed strictly by reading the 64-byte `PartitionSummary` header at offset 512, with zero payload scans.
- **Visit Budget Effectiveness**: Recall query fan-out is bounded to the configured visit budget, truncating older candidate partitions recency-first.
- **Hebbian Graph Expansion**: Traversal overhead delta is explicitly measured between enabled and disabled modes.
