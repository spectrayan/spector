# 🔬 Labs: Project Valhalla Value Classes & JDK 27 Modernization

> **Branch:** `epic/802-jdk27-upgrade` (Mainline bridge) & `labs/valhalla` (Experimental preview fork)  
> **Target JDK:** OpenJDK 27 GA (September 2026) / [Project Valhalla Early-Access Build](https://jdk.java.net/valhalla/)  
> **Status:** Mainline Certified Ready via `@ValueCandidate` & `ValueClassValidator`

---

## Executive Summary

Spector is architected for extreme high performance in cognitive memory, vector search, and off-heap engram persistence. Memory bandwidth, cache locality, and zero-GC overhead are paramount.

This document details:
1. **The Difference Between Mainline OpenJDK 27 and Project Valhalla EA**
2. **The 22 Hot-Path Value Class Candidates Identified Across Spector**
3. **The `@ValueCandidate` Annotation & Automated JEP 390 / Valhalla Compliance Framework**
4. **Free JDK 27 Optimizations (Compact Object Headers, G1 Defaults, Lazy Constants)**
5. **How to Build & Benchmark on `labs/valhalla` with `VALHALLA_HOME`**

---

## 1. Project Valhalla (JEP 401) vs. Mainline OpenJDK 27

A common point of confusion is whether the `value` keyword (e.g., `public value record ScoredResult(...)`) is available in standard OpenJDK 27:

- **Mainline OpenJDK 27 GA / EA (`27-ea+22-2010`)**: Does **not** include JEP 401 (Value Classes and Objects). Standard `javac 27` will reject the `value` keyword with `error: ';' expected`. Mainline JDK 27 focuses on JEP 516 (AOT Caching), JEP 517 (HTTP/3), JEP 519/534 (Compact Object Headers enabled by default), JEP 523 (G1 Default GC), JEP 531 (Lazy Constants preview), JEP 532 (Primitive Pattern Matching), and JEP 537 (Vector API 12th Incubator).
- **Project Valhalla Early-Access Fork (`openjdk-27-jep401ea3`)**: An experimental JDK build specifically implementing JEP 401. It parses `value record` and `value class`, generates `ACC_VALUE` class file flags, and flattens arrays in the HotSpot C2 compiler.
- **The `labs/valhalla` Branch**: Uses Maven compiler forking (`<executable>${env.VALHALLA_HOME}/bin/javac</executable>`) to build with the experimental Valhalla compiler.
- **The Mainline Bridge (`epic/802-jdk27-upgrade`)**: Annotates all hot-path records with `@ValueCandidate` and runs unit tests using `ValueClassValidator` to enforce value-class invariants (JEP 390 Value-Based Class semantics). When JEP 401 reaches preview in a mainline GA JDK, converting them to `value record` will be a zero-refactoring mechanical keyword addition.

---

## 2. Inventory of Candidate Value Classes (22 Types Across 7 Modules)

We audited all Spector modules and identified **22 high-performance candidates** for value class conversion, categorized by hot-path execution frequency:

### 🔴 CRITICAL Frequency (Inner Loops & Vector Traversal)

| Type | Module | Fields | Flattened Size | Hot-Path Rationale |
|:---|:---|:---|:---|:---|
| `ScoredResult` | `spector-index` | `(String, int, float)` | 16 B + ref | Millions allocated during HNSW beam search (`efSearch`). Eliminates 8-16B header per candidate. |
| `BatchSearchResult` | `spector-gpu` | `(long id, float score)` | 12 B | 100% primitive! Bulk similarity scores returned from native GPU kernels. Zero pointer chasing in flat arrays. |
| `TurboCode` | `spector-core` | `(byte[], float, float, int)` | 16 B + ref | Created per-vector in product/scalar quantization encoding. |
| `Hypervector` | `spector-hdc` | `(long[], int)` | 12 B + ref | 10,000-bit binary hypervectors in Hyperdimensional Computing pipelines. |
| `HebbianEdge` | `spector-kernel` | `(int src, int dst, float w)` | 12 B | 100% primitive! Traversed millions of times during spreading activation in cognitive graphs. Flat array = L1 cache line holds 5 full edges! |
| `TemporalFact` | `spector-kernel` | `(MemoryId, long, float, Segment)` | 36 B | Queried off-heap during temporal fact retrieval. |

### 🟠 HIGH Frequency (Search, Tokenization, Routing & Cognitive Memory)

| Type | Module | Fields | Hot-Path Rationale |
|:---|:---|:---|:---|
| `CognitiveResult` | `spector-memory` | `(engramId, content, activation, salience, timestamp)` | Per-recall result scoring in cognitive episodic/semantic memory retrieval. |
| `WelfordAccumulator` | `spector-core` | `(long count, double mean, double m2)` | 100% primitive! 24 bytes. Streamed across every vector feature during online normalization. |
| `EmaTracker` | `spector-core` | `(double alpha, double value, boolean init)` | 100% primitive! 17 bytes. Exponential moving average computed per dimension in dynamic indexing. |
| `WordTokenizer.Token` | `spector-commons` | `(String text, int startOffset, int endOffset)` | Hundreds of thousands generated per document indexing cycle. |
| `TextChunk` | `spector-commons` | `(String text, int index, int startOffset, int endOffset)` | Core chunking unit flowing through embedding generation pipelines. |
| `Chunk` | `spector-commons` | `(String id, String text, int seq, Map metadata)` | Ingestion pipeline chunk boundary representation. |
| `ContinuityRecord` | `spector-kernel` | `(seqNo, wallClockTime, eventType, MemoryId)` | Continuity timeline playback and replay in cognitive kernel. |
| `MemoryId` | `spector-kernel` | `(long high, long low)` | 100% primitive! 16-byte UUID replacement. Eliminates `java.util.UUID` header overhead. |
| `TurnHeaderSnapshot` | `spector-kernel` | `(turnNumber, timestamp, speakerId, payloadLen)` | 100% primitive! 20 bytes. Episodic conversation turn headers. |
| `FactLogEntry` | `spector-kernel` | `(long timestamp, int factIndex, float weight)` | 100% primitive! 16 bytes. Temporal fact mutation log entries. |
| `RoutingKey` | `spector-cluster` | `(String namespace, String partitionKey, int hash)` | Partition routing lookup key evaluated on every distributed vector query. |
| `FenceToken` | `spector-cluster` | `(long epoch, String leaderNodeId, long issueTime)` | Consensus fencing token verified on distributed raft/shard writes. |
| `ResolvedRoute` | `spector-cluster` | `(RoutingKey, targetNodeId, partitionId, isReplica)` | Returned by partition router on every inbound cluster request. |
| `RouteBinding` | `spector-cluster` | `(routePattern, targetService, priority, active)` | Routing table entry evaluated on cluster dispatch. |

### 🟡 MEDIUM Frequency (Resource Metrics & Region Geometry)

| Type | Module | Fields | Hot-Path Rationale |
|:---|:---|:---|:---|
| `AllocationMetrics` | `spector-gpu` | `(allocatedBytes, poolBytes, peakBytes, activeCount)` | 100% primitive! 28 bytes. Tracked per GPU kernel execution. |
| `RegionSizeSpec` | `spector-kernel` | `(long baseOffset, long maxBytes, int alignment)` | 100% primitive! 20 bytes. Memory segment allocation boundary specification. |

---

## 3. The Mainline Valhalla Verification Framework

In `nucleus/spector-commons`, we introduced:

1. **`@ValueCandidate`**:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValueCandidate {
    String reason();
    Frequency hotPathFrequency() default Frequency.HIGH;

    enum Frequency {
        CRITICAL, // millions of allocations/sec in inner loops
        HIGH,     // thousands of allocations/sec in search/indexing
        MEDIUM,   // hundreds of allocations/sec
        LOW       // architectural cleanup
    }
}
```

2. **`ValueClassValidator`**:
An automated reflective assertion tool used in unit tests across all 7 modules:
```java
// Asserts JEP 390 / JEP 401 compliance:
ValueClassValidator.assertValueClassCompliant(ScoredResult.class);
```
**Invariants verified:**
- Must be a `record` or `final class`
- Must not be `abstract`
- All declared instance fields must be `final`
- No `synchronized` methods
- Overrides `equals`, `hashCode`, and `toString` (or uses record default)
- Must be annotated with `@ValueCandidate`

---

## 4. Free Improvements in JDK 27 (Zero Code Changes)

Even before JEP 401 lands in mainline Java, OpenJDK 27 provides massive performance and footprint gains:

### Compact Object Headers (JEP 534) — Default in JDK 27
- Shrinks standard object headers from **12 bytes (or 16 bytes) down to 8 bytes** (saving 4-8 bytes per object instance).
- For Spector's in-memory HNSW graphs and candidate lists:
  - **4 MB saved per million nodes** in memory.
  - Significantly improved L2/L3 cache line utilization (more objects fit into a 64-byte cache line).
  - Active by default in JDK 27 GA without needing JVM flags.

### G1 Default GC (JEP 523)
- G1 is standardized as the default garbage collector across all container topologies (even single-CPU configurations), removing the need for manual `-XX:+UseG1GC` deployment flags.

---

## 5. Building the Experimental Valhalla Branch

To build the `labs/valhalla` branch with real `value record` syntax:

```bash
# 1. Download Valhalla Early-Access JDK 27 from https://jdk.java.net/valhalla/
mkdir -p ~/jdks
tar -xzf openjdk-27-jep401ea*.tar.gz -C ~/jdks

# 2. Checkout the experimental branch
git checkout labs/valhalla

# 3. Build using VALHALLA_HOME fork
VALHALLA_HOME=~/jdks/jdk-27 mvn clean compile -DskipTests

# 4. Run tests
VALHALLA_HOME=~/jdks/jdk-27 mvn test
```

---

## 6. Merging Criteria for Mainline

The `value` keyword will be directly introduced into mainline Spector when:
1. JEP 401 enters official **Preview** in an OpenJDK GA release.
2. The standard `javac --enable-preview --release 27` accepts the `value` keyword.
3. Because all 22 candidate classes already pass `ValueClassValidator`, converting them will simply require adding the `value` modifier to the record declaration.
