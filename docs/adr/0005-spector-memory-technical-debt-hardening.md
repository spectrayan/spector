# ADR-0005: spector-memory Technical Debt Hardening

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-08 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## Status
Accepted

## Context
A deep code review audit of the `spector-memory` module identified critical architectural and correctness debt:
1. **Virtual Thread Carrier Pinning**: The `synchronized` keyword causes underlying carrier thread pinning during I/O blocking operations (writes, WAL, segment forcing).
2. **Hardcoded String Memory IDs**: Internal databases instantiate on-the-fly `MemoryId.of(...)` using string literals, preventing strict typing and validation.
3. **Swallowed Recovery Exceptions**: Boot recovery paths bypass failures, risking database boot in an inconsistent state.
4. **Hot-Path Allocator Overhead**: Writing facts allocates new `Arena.ofAuto()` instances on the hot path, causing GC pressure.

## Decisions
1. **Virtual Thread Safety**: Migrate all `synchronized` methods/blocks on hot paths that perform file system/segment operations to fair `ReentrantLock` instances.
2. **System Memory ID Enum**: Introduce a central `SystemMemoryId` enum and mark the direct constructor `MemoryId.of(String, String)` as `@Deprecated` to prevent future string-based instantiations.
3. **Fail-Fast WAL Recovery**: Propagate exceptions in `MemoryWalRecovery` and `WalRecoveryDispatcher` and throw `SpectorWalCorruptionException` to halt boot on corrupted WAL event sequences.
4. **FFM Allocator Reuse**: Refactor `TemporalKnowledgeGraph` and `TextAppendMemory` writes to use the shared partition/layout `Arena` or SegmentAllocators, removing `Arena.ofAuto()` calls on the hot-path transaction loop.

## Alternatives
- **Keep synchronized blocks**: Rejected due to high scale carrier pinning degradation under virtual threads.
- **Bypass recovery failures**: Rejected due to critical correctness risks (e.g. database boots with a drifted index state).
