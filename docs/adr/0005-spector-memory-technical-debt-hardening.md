# ADR-0005: spector-memory Technical Debt Hardening

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-05 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

As Spector's cognitive memory engine evolved, rapid feature delivery across episodic, semantic, working, and procedural stores introduced architectural friction in `spector-memory`. A comprehensive technical debt audit identified redundant locking primitives, ambiguous exception boundaries, unclosed off-heap resources, and inconsistent naming conventions across store lifecycle managers.

## 2. Problem Statement

Key technical debt issues compromised system stability and maintainability:

1. **Locking inconsistency**: Mixed usage of `synchronized` blocks and `ReentrantLock`, risking virtual thread pinning under Project Loom.
2. **Exception opacity**: Catch-and-swallow patterns and generic runtime exceptions masking off-heap memory corruption or I/O failures.
3. **Lifecycle ambiguity**: Undefined cleanup order for memory-mapped buffers during abnormal namespace termination.
4. **Naming drift**: Inconsistent naming across partition and store facades (`StoreManager` vs `PartitionManager`).

## 3. Decision Drivers

- **Virtual Thread Friendliness**: Avoid monitor locks (`synchronized`) on I/O or blocking operations to prevent virtual thread carrier pinning.
- **Explicit Failure Semantics**: All subsystem failures must propagate typed `SpectorException` hierarchy instances with actionable error codes.
- **Deterministic Resource Release**: All off-heap memory-mapped regions must be governed by scoped Panama `Arena` lifecycles.
- **Architectural Uniformity**: Consistent naming and structural patterns across all memory stores.

## 4. Considered Options

### Option 1: Incremental Opportunistic Cleanup
- **Description**: Fix issues opportunistically as new features touch existing classes.
- **Advantages**: Minimal immediate sprint disruption.
- **Disadvantages**: High risk of leaving subtle concurrency bugs and resource leaks in untouched legacy paths.

### Option 2: Full Rewrite of spector-memory
- **Description**: Redesign the entire cognitive memory module from scratch.
- **Advantages**: Total clean slate.
- **Disadvantages**: Extremely high risk of introducing behavioral regressions into production memory pipelines.

### Option 3: Dedicated Hardening & Stabilization Sprint (Selected)
- **Description**: Execute a focused hardening milestone targeting locking migration (`synchronized` -> `ReentrantLock`), typed exception refactoring, `Arena` lifecycle unification, and naming standardization.
- **Advantages**: Eliminates systemic technical debt, preserves tested algorithmic logic, and establishes clear quality baselines.
- **Disadvantages**: Requires dedicated QA validation and regression test coverage across all cognitive stores.

## 5. Decision Outcome

**Chosen Option**: Option 3 (Dedicated Hardening & Stabilization Sprint).

### Positive Consequences
- Virtual thread pinning eliminated across all memory stores.
- Consistent error handling via `SpectorException` and standardized error registries.
- Deterministic off-heap resource release prevents memory leaks across partition rolls.

### Negative Consequences & Trade-offs
- Refactoring locking primitives required comprehensive concurrency re-benchmarking under heavy contention.

## 6. Pros and Cons of the Options

| Option | Pros | Cons |
|:---|:---|:---|
| **Option 1: Opportunistic** | Low upfront effort | Persistent debt, unaddressed edge-case leaks |
| **Option 2: Full Rewrite** | Clean design | High regression risk, wasted engineering velocity |
| **Option 3: Hardening Sprint** | Systemic reliability, retains proven logic | Requires extensive regression testing |

## 7. Implementation Plan

1. **Phase 1**: Replace all `synchronized` methods and blocks with `ReentrantLock` or `StampedLock`.
2. **Phase 2**: Refactor error propagation to use domain-specific `SpectorException` types with defined `ErrorCode` mappings.
3. **Phase 3**: Standardize naming across store facades (`PartitionManager`, `StoreRegistry`).
4. **Phase 4**: Verify zero virtual thread carrier pinning using JVM flight recorder (JFR) profiling.

## 8. Code Reference & Verification

- **Primary Module(s)**: `memory/spector-memory`
- **Key Packages**: `com.spectrayan.spector.memory.store`, `com.spectrayan.spector.memory.exception`
- **Classes**: `PartitionManager.java`, `SpectorException.java`, `EpisodicMemoryStore.java`
- **Verification Tests**: `PartitionConcurrencyTest.java`, `VirtualThreadPinningTest.java`
