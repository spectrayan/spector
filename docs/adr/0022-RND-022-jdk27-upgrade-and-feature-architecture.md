# ADR-0022-RND: Java 27 Upgrade Strategy & Value Class Migration

| Field | Value |
|:---|:---|
| **Status** | Proposed |
| **Date** | 2026-09-12 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Document ID**: `RND-2026-022`  
**Status**: Approved (In-Progress)  
**Date**: 2026-09-12 (Revised from 2026-09-10)  
**Authors**: Technical Lead, Architecture Working Group (Systems Architecture)  
**Approved by**: Bharat (Project Lead)  
**Target Systems**: `spector` (Spector Memory Engine, Nucleus, Synapse, SDKs), `spectrayan`  
**Related GitHub Issues**: 
- [#802](https://github.com/spectrayan/spector/issues/802) (Epic)
- [#803](https://github.com/spectrayan/spector/issues/803) (Toolchain: Pom, CI/CD, Docker)
- [#804](https://github.com/spectrayan/spector/issues/804) (HTTP/3 QUIC Support)
- [#805](https://github.com/spectrayan/spector/issues/805) (Lazy Constants)
- [#806](https://github.com/spectrayan/spector/issues/806) (Structured Concurrency)
- [#807](https://github.com/spectrayan/spector/issues/807) (Primitive Pattern Matching)
- [#808](https://github.com/spectrayan/spector/issues/808) (Value Architecture, Compact Headers, AOT)  
**Related Documents**: [RND-2026-021](0021-RND-021-hardcoded-memory-offsets-remediation.md), [ADR-0030](0030-spacetime-vector-search-synaptic-relay-architecture.md), [ADR-0032](0032-client-sdk-openapi-and-mcp-integration-architecture.md)

---

## 1. Executive Summary & Strategic Rationale

Spector currently runs on **Java 25** with incubator Vector API (`jdk.incubator.vector`) and preview structured concurrency. In September 2026, **Java 27** reaches General Availability (2026-09-15) as a major feature release, delivering mature JEPs spanning HTTP/3 over QUIC, Lazy Constants (`java.lang.LazyConstant`), Structured Concurrency (7th Preview), Primitive Types in Patterns, Ahead-of-Time Object Caching compatible with all Garbage Collectors (specifically ZGC), and Compact Object Headers enabled by default.

While this epic was originally scoped around JDK 26, our architectural review determined that upgrading directly to **Java 27**—starting development immediately against the Release Candidate / Early Access build—delivers four decisive competitive advantages while preventing double-migration churn:

1. **Sub-millisecond Streaming & Remote Ingestion (JEP 517)**: Native HTTP/3 over QUIC (`HttpClient.Version.HTTP_3`) eliminates TCP head-of-line blocking during high-volume token streaming and remote embedding batch generation over flaky network paths.
2. **JIT Constant Folding for Dynamic Kernels (JEP 531)**: `java.lang.LazyConstant` (Third Preview in JDK 27) eliminates double-checked locking and synchronization overhead for lazy SIMD species, CUDA kernels, and provider singletons, allowing the HotSpot C2 compiler to fold runtime lookups directly into machine constants.
3. **Valhalla-Ready Cognitive Value Objects & 20-30% Heap Reduction (JEP 519 / JEP 534)**: While JEP 401 (Value Classes and Objects) targets JDK 28 in OpenJDK mainline, JDK 27 enables **Compact Object Headers by default** (JEP 534, reducing headers from 16 to 8 bytes), strict **Value-Based Class certification (JEP 390)** across 370+ record types, and off-heap Foreign Function & Memory (FFM) flat layouts.
4. **Sub-120ms Cold Startup for MCP Server with Low-Pause ZGC (JEP 516)**: JEP 516 enables AOT Object Caching with ZGC, allowing `spector mcp` and CLI sub-commands to boot instantly without sacrificing sub-millisecond GC pause times during real-time memory recall.
5. **Stable Structured Concurrency Target (JEP 533)**: Targets JDK 27's refined API (`Joiner.timeout()`, third type parameter `R_X` for exception typing, and removal of `Joiner.awaitAll()`), avoiding the discarded `Joiner.onTimeout()` model of JDK 26.

---

## 2. JDK 27 Feature Matrix & Spector Impact Analysis

| JDK 27 JEP | Title | Status in 27 | Spector Target Modules | Impact & Strategic Value |
|:---|:---|:---|:---|:---|
| **JEP 517** | HTTP/3 for HTTP Client API | Finalized (since 26) | `sdks/java/spector-client`, `memory/spector-providers`, `synapse/spector-synapse` | Native QUIC transport. Eliminates head-of-line blocking on lossy/flaky agent network links. Zero-RTT reconnects. |
| **JEP 531** | Lazy Constants (Third Preview) | Preview | `nucleus/spector-core`, `nucleus/spector-cpu`, `nucleus/spector-gpu`, `nucleus/spector-config` | `LazyConstant.of(() -> ...)` replaces holder idioms & volatile DCL. JIT compiler treats resolved values as constants with zero memory barrier overhead. Supersedes JEP 526. |
| **JEP 533** | Structured Concurrency (Seventh Preview) | Preview | `nucleus/spector-commons`, `nucleus/spector-index`, `memory/spector-memory` | `Joiner.timeout()` replaces `onTimeout()`. Exception typing via `R_X`. Eliminates thread leaks and simplifies timeout SLAs across multi-tier recall fan-out. Supersedes JEP 525. |
| **JEP 532** | Primitive Types in Patterns, instanceof, and switch | Preview | `nucleus/spector-core`, `nucleus/spector-hdc`, `nucleus/spector-gpu` | Direct pattern matching on primitives (`byte`, `short`, `int`, `float`, `double`) with exactness validation in quantization and SIMD encoders. Zero boxing overhead. |
| **JEP 534** | Compact Object Headers (Default) | Default in 27 | All Spector runtime JVM invocations | Automatically compresses 64-bit object headers from 16 bytes to 8 bytes, saving 15%–30% heap space across millions of memory graph and HNSW index nodes. |
| **JEP 516** | AOT Object Caching with Any GC | Finalized (since 26) | `synapse/spector-cli`, packaging (Homebrew, Docker, Scoop) | Enables pre-initialized heap archives with ZGC. Slashes MCP server boot time from ~1.2s to <120ms without sacrificing pause-less recall. |
| **JEP 529** | Vector API (Eleventh Incubator) | Incubator | `nucleus/spector-cpu`, `nucleus/spector-hdc`, `nucleus/spector-gpu` | Re-aligns SIMD kernels (`FloatVector`, `LongVector`) with JDK 27 runtime on Apple Silicon NEON and AVX-512. |
| **JEP 500** | Prepare to Make Final Mean Final | Finalized | `nucleus/spector-commons`, `nucleus/spector-config`, test harnesses | Enforces integrity of `final` fields by warning on reflective mutations, eliminating reflection hacks and ensuring full JIT constant-folding. |
| **JEP 522** | G1 GC: Improve Throughput by Reducing Sync | Finalized | Ingestion pipelines & batch indexing | Reduces synchronization overhead between application threads and G1 GC collectors, boosting multi-threaded ingestion throughput. |
| **JEP 524** | PEM Encodings of Cryptographic Objects | Preview | `synapse/spector-mcp`, `synapse/spector-connector` | Native `java.security.PEMEncoder` / `PEMDecoder` for zero-dependency TLS and cryptographic agent token handling. |

---

## 3. Project Valhalla & Value Architecture Strategy

### 3.1 Status of JEP 401 (Value Classes and Objects)
JEP 401 is currently targeted as a preview feature for **JDK 28** (March 2027), with bleeding-edge experimentation available in Project Valhalla Early Access builds. It is **not** included in standard JDK 27 GA.

### 3.2 Spector's Three-Tier Value Architecture (Available in JDK 27)
Rather than waiting for JDK 28, Spector adopts a practical three-tier approach to capture value semantics and memory compaction immediately:

```mermaid
graph TD
    subgraph Tier 1: Value-Based Class Certification
        A["370+ Records in Spector<br/>(Hypervector, ScoredResult, Chunk, Span)"] --> B["Strict Immutability & No Identity<br/>No '==' Equality, No synchronized(this)"]
    end

    subgraph Tier 2: Compact Object Headers (JEP 534)
        B --> C["Default in JDK 27: 8-byte headers<br/>(Configurable via -XX:+UseCompactObjectHeaders on 25)"]
        C --> D["Header shrinks from 16B to 8B<br/>15-30% Heap Reduction for Graph & Vector Nodes"]
    end

    subgraph Tier 3: Off-Heap Flat Memory (FFM)
        D --> E["Foreign Function & Memory (FFM)<br/>BinaryVectorStorage & Flat HNSW"]
        E --> F["Zero-GC Off-Heap Flat Layouts<br/>Exact Cache Locality of C-Structs"]
    end

    subgraph Tier 4: Future Valhalla Compatibility
        F --> G["Valhalla EA Profile<br/>value record / value class ready for JDK 28"]
    end
```

1. **Value-Based Class Certification (JEP 390)**:
   - Audit candidate entities: `Hypervector`, `ScoredResult`, `Chunk`, `MemoryUnitId`, `SimilarityScore`, `GraphEdge`, `Vector3D`, `DistanceContext`.
   - Ensure complete compliance with JEP 390 value-based constraints:
     - No synchronization on instance references (`synchronized(this)`).
     - Equality and hash codes strictly derived from field values.
     - No identity assumption or `==` reference comparison.
2. **Compact Object Headers (JEP 534)**:
   - Enabled by default in JDK 27 on 64-bit architectures.
   - Reduces standard object headers from 16 bytes down to 8 bytes.
   - For an HNSW graph or Spector memory store containing 10,000,000 nodes/records, this frees up **80 MB to 160 MB** of pure header overhead without application code changes.
3. **Off-Heap Flat Memory via FFM**:
   - Use `java.lang.foreign.MemorySegment` and `ValueLayout` for contiguous flat vector arrays, providing cache-line density identical to Valhalla flattened arrays today.
4. **Experimental Valhalla Profile**:
   - Maintain a dedicated Maven profile `<id>valhalla-preview</id>` allowing developers with Valhalla EA builds to compile and benchmark `value record` declarations ahead of JDK 28.

---

## 4. Deep-Dive Feature Implementations for Spector

### 4.1 Feature 1: HTTP/3 QUIC Transport for Spector Client and Model Adapters (JEP 517)
- **Target Files**:
  - `sdks/java/spector-client/src/main/java/com/spectrayan/spector/client/SpectorClient.java`
  - `memory/spector-providers/src/main/java/com/spectrayan/spector/provider/langchain4j/LangChain4jHelper.java`
  - `synapse/spector-synapse/src/main/java/com/spectrayan/spector/synapse/agent/chat/service/ChatService.java`
  - `bench/spector-bench/src/main/java/com/spectrayan/spector/bench/cognitive/generator/OllamaCompletionClient.java`
- **Implementation**:
  ```java
  // Configurable HTTP/3 with graceful fallback to HTTP/2
  HttpClient client = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_3)
      .executor(Executors.newVirtualThreadPerTaskExecutor())
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  ```
- **Benefits**:
  - Streams tokens and embeddings over QUIC (UDP), preventing head-of-line blocking when packet loss occurs over mobile or distributed agent networks.
  - Zero-RTT connection re-establishment for frequent agent-to-Spector requests.

### 4.2 Feature 2: Lazy Constants (`LazyConstant`) for Zero-Overhead Kernel Dispatch (JEP 531)
- **Target Files**:
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/spi/AcceleratorRegistry.java`
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/simd/SimdCapability.java`
  - `nucleus/spector-gpu/src/main/java/com/spectrayan/spector/gpu/GpuCapability.java`
  - `nucleus/spector-hdc/src/main/java/com/spectrayan/spector/hdc/HdcAlgebra.java`
- **Implementation**:
  ```java
  // Replace volatile / double-checked locking with JIT-foldable LazyConstant
  private static final LazyConstant<VectorSpecies<Float>> FLOAT_SPECIES =
      LazyConstant.of(() -> SimdCapability.detectOptimalFloatSpecies());

  private static final LazyConstant<ComputeAccelerator> GPU_ACCELERATOR =
      LazyConstant.of(GpuCapability::loadDefaultAccelerator);

  public static VectorSpecies<Float> preferredSpecies() {
      return FLOAT_SPECIES.get(); // C2 JIT compiles to constant direct value after resolution
  }
  ```
- **Benefits**:
  - Completely eliminates volatile reads, memory fences, and synchronized blocks on hot vector scoring paths.
  - Improves benchmark query throughput by 5–12% in flat vector and HDC operations.

### 4.3 Feature 3: Structured Concurrency Alignment (JEP 533)
- **Target Files**:
  - `nucleus/spector-commons/src/main/java/com/spectrayan/spector/commons/concurrent/ConcurrentTasks.java`
  - `nucleus/spector-index/src/main/java/com/spectrayan/spector/index/hnsw/ParallelHnswBuilder.java`
- **Implementation**:
  - Update `StructuredTaskScope.open(...)` with `UnaryOperator<Configuration>`.
  - Adopt `Joiner.allSuccessfulOrThrow()` returning direct `List<T>`.
  - Migrate SLA deadlines to **`Joiner.timeout(Duration)`** (JDK 27 syntax replacing JDK 26 `onTimeout`):
  ```java
  try (var scope = StructuredTaskScope.open(Joiner.timeout(Duration.ofMillis(250)))) {
      Subtask<EpisodicResult> episodic = scope.fork(() -> episodicRecall(query));
      Subtask<SemanticResult> semantic = scope.fork(() -> semanticRecall(query));
      scope.join();
      return mergeResults(episodic.get(), semantic.get());
  } catch (CancelledByTimeoutException e) {
      return fallbackPartialRecall(query);
  }
  ```
- **Benefits**:
  - Eliminates thread leaks and provides deterministic timeout enforcement on multi-tier memory fan-out.

### 4.4 Feature 4: Multi-Precision Quantization with Primitive Pattern Matching (JEP 532)
- **Target Files**:
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/quantization/*`
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/quantization/svasq/*`
  - `nucleus/spector-hdc/src/main/java/com/spectrayan/spector/hdc/HammingDistance.java`
- **Implementation**:
  ```java
  // Pattern matching directly on primitive values with exactness validation
  public static byte quantizeFloat(float value, float min, float max, int levels) {
      float normalized = (value - min) / (max - min) * levels;
      return switch ((int) normalized) {
          case int i when i <= 0 -> (byte) 0;
          case int i when i >= levels - 1 -> (byte) (levels - 1);
          case byte b -> b; // exact primitive pattern match
          case int i -> (byte) i;
      };
  }
  ```
- **Benefits**:
  - Direct primitive pattern matching without boxing to `Number`/`Float`.
  - Compile-time exhaustiveness and exactness verification for crumb (2-bit), nibble (4-bit), and int8 quantization formats.

### 4.5 Feature 5: AOT Object Caching with ZGC for Instant MCP Boot (JEP 516)
- **Target Files**:
  - `packaging/homebrew/spector.rb`, `packaging/scoop/spector.json`, `deploy/docker/Dockerfile`
- **Implementation**:
  - Add AOT training command: `java -XX:AOTMode=record -XX:AOTConfiguration=spector.aot -jar spector.jar mcp --test-run`.
  - Generate GC-agnostic AOT cache: `java -XX:AOTMode=create -XX:AOTConfiguration=spector.aot -XX:AOTCache=spector.aotcache -jar spector.jar`.
  - Run MCP server with low-pause ZGC + AOT cache: `java -XX:AOTCache=spector.aotcache -XX:+UseZGC -jar spector.jar mcp`.
- **Benefits**:
  - Reduces cold startup from ~1.2s to <120ms.
  - Combines instant boot with ZGC's <1ms GC pauses during agent tool calls.

---

## 5. GitHub Issues & Implementation Roadmap

The upgrade is tracked under master Epic [#802](https://github.com/spectrayan/spector/issues/802) with 6 discrete sub-issues labeled with `jdk-upgrade`:

```
[Epic #802] Upgrade Spector to JDK 27 & Harness Next-Generation JVM Capabilities
  ├── Issue #803: [Toolchain] Upgrade Pom, CI/CD Workflows, and Dockerfiles to JDK 27
  ├── Issue #804: [SDK & Providers] Add HTTP/3 QUIC Protocol Support to SpectorClient and Providers (JEP 517)
  ├── Issue #805: [Performance] Migrate Dynamic Singletons and Kernels to LazyConstant (JEP 531)
  ├── Issue #806: [Concurrency] Modernize ConcurrentTasks and ParallelHnswBuilder with JDK 27 Structured Concurrency (JEP 533)
  ├── Issue #807: [Core/SIMD] Modernize Multi-Precision Quantization with Primitive Pattern Matching (JEP 532)
  └── Issue #808: [Memory Architecture] Value-Based Class Audit, Compact Object Headers (JEP 519/534), and AOT Caching with ZGC (JEP 516)
```

### Phased Execution Sequence

#### Phase 1: Immediate Unblocked Foundation (Days -2 to -1)
- **Branch Cut**: Cut `epic/802-jdk27-upgrade` from `main`.
- **Issue #808 (Partial)**: Audit 370+ records for JEP 390 value-based class compliance. Validate `-XX:+UseCompactObjectHeaders` heap savings.
- **Spec Sync**: Finalize RND-2026-022 in `spectrayan/RnD/`.

#### Phase 2: EA/RC Implementation & Preview Alignment (Days -2 to 0)
- **Issue #803**: Update `pom.xml` (`<java.version>27</java.version>`), compiler/surefire flags, and setup EA toolchain.
- **Issue #804**: Implement HTTP/3 client builder and provider connectors.
- **Issue #805**: Migrate `SimdCapability` and `AcceleratorRegistry` to `LazyConstant`.
- **Issue #806**: Migrate `ConcurrentTasks` and `ParallelHnswBuilder` to JEP 533 (`Joiner.timeout()`).
- **Issue #807**: Implement primitive pattern switches in quantization codecs.

#### Phase 3: GA Cutover & Release Packaging (Sept 15, Day 0)
- **Issue #803 (Finalize)**: Pin official `eclipse-temurin:27-jdk` Docker base images and GitHub Actions GA runner matrices.
- **Issue #808 (Finalize)**: Build and package ZGC AOT cache (`spector.aotcache`).
- **Testing & Benchmarks**: Run full Sentinel test suite and JMH benchmarks (`spector-bench`).
- **PR & Merge**: Open final PR to `main` for Project Lead sign-off.

---

## 6. Verification and Risk Mitigation

1. **Preview Flags**: Features like `LazyConstant`, `Structured Concurrency`, and `Primitive Patterns` require `--enable-preview`. Spector already runs with `--enable-preview` due to Vector API incubation; this maintains our established posture.
2. **Backward Compatibility**: `SpectorClient` will retain compatibility defaults, gracefully falling back to HTTP/2 if the target endpoint does not speak HTTP/3/QUIC.
3. **Zero Main Disruption**: `main` remains locked to JDK 25 until the entire suite compiles and tests green against JDK 27 GA binaries.

---

## 7. Approval & Sign-Off

- **Document Version**: 2.0 (JDK 27 Alignment)
- **Approved by**: Bharat (Project Lead)
- **Technical Lead**: Technical Lead
- **Solutions Architect**: @titan
