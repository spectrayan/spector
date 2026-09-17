# ADR-0037: Ingestion Boundary and Sensory Relocation

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-09-14 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Two unrelated-looking problems share one root cause.

**First:** `RememberPathway implements IngestionTarget, AutoCloseable` (`RememberPathway.java` L71). `IngestionTarget.ingest(String, String, float[])` returns `void`, which blocks ADR-0035's `Pathway<RememberSignal, RememberResult>` and forces that ADR to hedge — "existing `ingest*` methods stay `void` and ignore the result."

**Second:** `spector-ingestion` carries Apache Tika at compile and runtime scope, and `spector-memory` depends on `spector-ingestion` at compile scope. Tika's transitive closure therefore reaches every embedded and CLI consumer.

The root cause is that `spector-ingestion` accreted two unrelated jobs — a *storage-target SPI* and a *multi-modal extraction toolkit* — on top of its stated one. Its own POM description is now false:

> "Pure ingestion utilities: file discovery, text chunking, title extraction, and ingestion pipelines."

### 1.1 `IngestionTarget` is a fossil

Its javadoc names two implementations:

```
 *   <li><b>EngineIngestionTarget</b> (spector-engine): VectorStore → HNSW → BM25</li>
 *   <li><b>CognitiveIngestionTarget</b> (spector-memory): quantize → surprise → tier route → WAL</li>
```

**Neither exists.** `spector-engine` was deleted. `CognitiveIngestionTarget` was collapsed into `RememberPathway` — `DefaultImportanceProvider` L87 still carries `// Gaming detection logging (matches original CognitiveIngestionTarget)`.

So the interface is a single-implementor abstraction whose polymorphism requirement is gone.

| Fact | Evidence |
|---|---|
| One production implementor | `RememberPathway` L71. All other references are test mocks. |
| Two production consumers | `IngestionPipeline` (`spector-ingestion`), `SpectorIngestionSink` L144 (`spector-connector`). |
| Exactly one call site in the sink | `target.ingest(docId, scrubbedContent, vector)` — L144. |
| **Two of three methods are silently dead** | `storeParentMetadata` and `onBatchComplete` are `default {}`. `RememberPathway` overrides **neither**. `IngestionPipeline` calls them at L192, L193, L211, L246, L247 — all no-ops. Parent-document tracking and batch WAL flush **do not happen today**. |
| The accessor leaks the concrete type | `SpectorMemory.target()` L149 returns `RememberPathway`, not `IngestionTarget`. |
| The accessor is also a duplicate | `SpectorMemoryAdmin` declares **both** `target()` *and* `rememberPathway()`, both returning `RememberPathway`. |

Consumers already reach through the front door to get the side window: `ConnectorAutoConfiguration` L87–93 holds a `SpectorMemory`, then calls `memory.target()` to extract an internal pathway.

### 1.2 Tika sits in the core dependency chain

```xml
<!-- memory/spector-ingestion/pom.xml -->
<dependency><groupId>org.apache.tika</groupId><artifactId>tika-core</artifactId></dependency>
<dependency><groupId>org.apache.tika</groupId>
            <artifactId>tika-parsers-standard-package</artifactId>
            <scope>runtime</scope></dependency>
```

`spector-memory` → `spector-ingestion` at compile scope, so `tika-parsers-standard-package` (transitively PDFBox, POI, Jackcess, commons-compress, …) lands in the runtime closure of `spector-mcp`, `spector-cli` (the standalone `spector.jar`), `spector-batch`, `spector-spring`, and `bench`.

Every embedded and CLI user carries tens of MB and a large CVE surface for document parsing they may never invoke.

Meanwhile `spector-memory`'s **entire** main-source dependency on `spector-ingestion` is four types:

```
com.spectrayan.spector.ingestion.sensory.SensoryExtractor          (2 files)
com.spectrayan.spector.ingestion.sensory.SensoryExtractor.ExtractionChunk
com.spectrayan.spector.ingestion.sensory.AssetStore                (2 files)
com.spectrayan.spector.ingestion.IngestionTarget                   (1 file)
```

### 1.3 Multi-modal is not actually wired

Verified, and it is what makes this ADR low-risk:

- **No production code instantiates any concrete sensory extractor.** `TikaTextExtractor`, `OllamaVisionExtractor`, `OllamaAudioExtractor`, `FFmpegKeyframeExtractor`, and `LocalAssetStore` have **zero** main-source consumers outside `spector-ingestion`. The only apparent hits are javadoc mentions in `AudioChunker` L29 / `VideoChunker` L26 and an unrelated `MultimodalProperties.isLocalAssetStore()` method name.
- The only main-source references are the SPI-typed builder setters `SpectorMemoryBuilder.sensoryExtractors(...)` L416 / `assetStore(...)` L421, which nothing calls.
- Attachment processing is **already opt-in and no-op by default**:

```java
// SpectorMemoryFactory L573-577
if (!builder.sensoryExtractors().isEmpty()) {
    attachmentProcessor = new AttachmentProcessor(builder.sensoryExtractors(), builder.assetStore());
} else {
    attachmentProcessor = null;
}
```

```java
// DefaultSpectorMemory L822-825
if (attachmentProcessor == null) {
    log.debug("No AttachmentProcessor configured  --  skipping attachments for '{}'", parentId);
    return;
}
```

Concrete extractors are exercised only from tests (`AudioIngestionE2ETest`, `VideoIngestionE2ETest`, `AttachmentProcessorTest`, `AttachmentProcessorFileTest`).

---

## 2. Problem Statement



## 3. Decision Drivers

### Goals

- Unblock ADR-0035 M1.5 by freeing `RememberPathway` of `IngestionTarget`.
- Remove Tika's transitive closure from `spector-memory` and everything downstream of it.
- One ingestion entry point (`SpectorMemory`) rather than two (`SpectorMemory` + `IngestionTarget`).
- Preserve the extraction **SPI seam** so multi-modal can be built properly later.
- Zero production behavior change.

### Non-goals

- Building out multi-modal support. This ADR relocates and deprecates; it does not implement.
- Changing MCP / REST / connector request shapes.
- Rewriting `AttachmentProcessor`.
- Deleting the extractor implementations. They move and are deprecated, not removed.
- Introducing a new Maven module (explicitly rejected — §4.1).

---

## 4. Considered Options

### 4.1 Destination for the relocated code

| Option | Verdict |
|---|---|
| Reinstate `CognitiveIngestionTarget` as an adapter in `spector-memory` | **Rejected.** Preserves a single-implementor abstraction for polymorphism that no longer exists. Deleting the interface is more consistent than adapting it. |
| Keep `IngestionTarget`, just rename it | **Rejected.** Keeps the thing we want gone, and the sink still cannot use the front door. |
| Pipeline takes a `BiConsumer` callback; caller closes over `SpectorMemory` | Rejected as the *primary* fix — avoids the cycle without addressing the fact that the pipeline sits in a module with no remaining reason to exist. Retained as the fallback if the move in §5.2 proves disruptive. |
| **New Apache-2.0 leaf module** `synapse/spector-sensory` | Considered and **not chosen**. It would preserve Apache-2.0 licensing and keep extraction reachable from `spector-mcp` / `spector-cli`. Rejected in favour of not adding a 23rd reactor module for code that is currently test-only. Recorded as the revisit path (§11). |
| **Existing modules: pipeline → `spector-cli`, extractors → `spector-synapse`** | **Chosen.** No new module. Both destinations already depend on `spector-memory` and `spector-ingestion`. Accepted costs in §10. |

### 4.2 Why `SpectorMemory` needs a vector-accepting overload

`SpectorMemory` today has **no** entry point taking a pre-computed embedding — a repo-wide check finds not one `float[]` in either `SpectorMemory` or `SpectorMemoryAdmin`. Every `remember` overload takes text and embeds internally.

`IngestionTarget.ingest(id, text, vector)` exists precisely to accept an already-embedded chunk, and **both** consumers depend on that:

```java
// SpectorIngestionSink L131-144
if (chunkChangeDetector != null && pipelineId != null && chunkIndex >= 0) {
    if (!chunkChangeDetector.hasChunkChanged(pipelineId, docId, chunkIndex, scrubbedContent)) {
        totalSkippedUnchanged.incrementAndGet();
        log.debug("[Sink] Chunk {}:{} unchanged, skipping re-embedding (delta upsert)", docId, chunkIndex);
        return;
    }
}
EmbeddingResult embeddingResult = embeddingProvider.embed(scrubbedContent);
float[] vector = embeddingResult.vector();
target.ingest(docId, scrubbedContent, vector);
```

Naively redirecting these to `remember(id, text, type, source, tags)` would make memory re-embed text the caller already embedded. Consequences:

- **Double embedding cost on every bulk ingest.** Embedding is the dominant cost of ingestion; this would roughly double it for connector and CLI paths.
- **The delta-upsert optimisation is defeated.** The sink's whole point in skipping unchanged chunks is avoiding the embed call.
- The pipeline has the same shape: embed per chunk, then hand over the vector.

So the deletion of `IngestionTarget` is conditional on adding:

```java
/**
 * Remembers a chunk whose embedding has already been computed by the caller.
 *
 * <p>Replaces the deleted {@code IngestionTarget.ingest}. Use when the caller
 * owns embedding — bulk pipelines, connectors with PII scrubbing, and delta
 * upsert paths that must not re-embed.</p>
 */
void remember(String id, String text, float[] vector, MemoryType type,
              MemorySource source, RememberContext context, String... tags);
```

This is strictly narrower than the SPI it replaces: one method on the existing front door instead of a three-method interface in another module.

### 4.3 What happens to the two dead methods

| Method | Today | Decision |
|---|---|---|
| `storeParentMetadata(parentId, chunkCount)` | `default {}`, not overridden ⇒ no-op. Called at `IngestionPipeline` L192, L211, L246. | **Dropped.** No behavior is lost because none exists. Recorded as a known capability gap (§12) rather than silently discarded — parent-document registry is a real want, and `AttachmentProcessor` already does parent linking via Hebbian edges for the attachment case. |
| `onBatchComplete()` | `default {}`, not overridden ⇒ no-op. Called at L193, L247. | **Dropped.** `SpectorMemory` exposes no flush/checkpoint method to map it onto (verified). Recorded as a gap; if batch WAL flush is wanted it should be an explicit `SpectorMemory` method, designed as such. |

Both are called today and do nothing. Deleting them makes an existing no-op honest instead of implied.

---

## 5. Decision Outcome

1. **Delete `IngestionTarget`.** Do not replace it with an adapter. Callers use `SpectorMemory`, the single entry point MCP and REST already use.
2. **Add the one entry point that is genuinely missing** — a `remember` overload accepting a pre-computed vector (§4.2). This is not optional; see the regression it prevents.
3. **Move `IngestionPipeline` + `FileDiscoveryService` to `spector-cli`**, their only consumer. This is what breaks the module cycle that made `IngestionTarget` necessary.
4. **Split the sensory package**: SPIs stay, concrete implementations move to `spector-synapse`.
5. **Drop both Tika dependencies from `spector-ingestion`.**
6. **Leave `AttachmentProcessor` alone.** It is already optional, already no-op, and has no heavy dependencies.

---

---

### 5.1 The cycle that forced `IngestionTarget`

```
spector-ingestion  →  spector-memory     0   (no dependency)
spector-memory     →  spector-ingestion  1   (compile scope)
```

`IngestionPipeline` lives in `spector-ingestion`. Giving it a `SpectorMemory` closes the cycle and Maven rejects the reactor. **This is the real reason the SPI exists** — it is a layering artifact, not gratuitous indirection. Rewiring the sink alone does not fix it; the pipeline has to move.

### 5.2 `IngestionPipeline` + `FileDiscoveryService` → `spector-cli`

Their only production consumer is `RememberCommand` L195–207:

```java
IngestionPipeline pipeline = IngestionPipeline.builder()
        .target(memory.target())
        .embeddingProvider(embedder)
        ...
var discovery = FileDiscoveryService.builder()
        .rootDirectory(root)
        ...
```

Both classes land next to that call site. `.target(...)` becomes `.memory(memory)`, and the pipeline calls the §4.2 overload. `spector-cli` already depends on `spector-memory`, so no new wiring.

`IngestionResult` moves with them.

### 5.3 Sensory splits three ways

The package is not homogeneous. 1767 lines across 11 files:

| Kind | Files | Lines | Destination |
|---|---|---|---|
| **SPI** | `SensoryExtractor` (+ `ExtractionChunk`), `AssetStore`, `AudioTranscriptExtractor`, `ImageEmbeddingProvider` | 429 | **stay** in `spector-ingestion` |
| **Pure utility** | `TemporalChainLinker` (static, operates on `ExtractionChunk`, no third-party deps) | 169 | **stay** |
| **Implementation** | `FFmpegKeyframeExtractor` (311), `TikaTextExtractor` (253), `OllamaVisionExtractor` (218), `OllamaAudioExtractor` (160), `LocalAssetStore` (151), `AudioExtractorConfig` (76) | 1169 | **move** to `spector-synapse` |

The SPIs **cannot** move. `spector-memory` main consumes them:

- `AttachmentProcessor` L57–58 (`List<SensoryExtractor>`, `AssetStore`), L142, L259–260
- `SpectorMemoryBuilder` L106–107, L416, L421, L638–639

Moving them to the synapse layer would require `spector-memory → synapse/*`, inverting the reactor's layer order (nucleus → memory → synapse).

### 5.4 `AttachmentProcessor` stays, unchanged

Two reasons, and the second is the important one.

**It buys no dependency reduction.** `AttachmentProcessor` depends only on the SPIs and MIME strings. All third-party weight is in the implementations.

**What it orchestrates is cognitive, not extraction:**

```java
// DefaultSpectorMemory.processAttachments L836-841
var subContext = RememberContext.builder()
        .metadata(result.metadata())
        .hebbianEdge(parentId, 0.8f)   // strong link to parent
        .build();
float[] vector = embeddingProvider.embed(result.text()).vector();
rememberPathway.ingestCognitive(result.chunkId(), result.text(), vector, type, tags, source, subContext);
```

Embedding, tier routing, and Hebbian parent-linking at 0.8f are memory-domain decisions. The correct seam is: **synapse hands over extracted chunks; memory decides how to store and associate them.** That seam is `SensoryExtractor`, which is already an interface.

Explicitly **not** doing: commenting out attachment code. It is already dormant by default (§1.3), so disabling it changes no behavior and would only create rotting code.

---

## 6. Target module shape

```
memory/spector-ingestion/                    Apache-2.0
    ingestion/sensory/
        SensoryExtractor.java                SPI  ← spector-memory consumes
        AssetStore.java                      SPI  ← spector-memory consumes
        AudioTranscriptExtractor.java        SPI
        ImageEmbeddingProvider.java          SPI
        TemporalChainLinker.java             pure utility
    NO Tika. NO IngestionTarget. NO pipeline. NO FileDiscoveryService.

memory/spector-memory/                       BSL-1.1
    RememberPathway     implements Pathway<RememberSignal, RememberResult>   (ADR-0035)
                        NO LONGER implements IngestionTarget
    SpectorMemory       + remember(id, text, vector, type, source, ctx, tags)
                        − target()                       (deprecate → remove)
    SpectorMemoryAdmin  − target()                       (duplicate of rememberPathway())
    AttachmentProcessor unchanged, still optional

synapse/spector-cli/                         Apache-2.0
    IngestionPipeline, FileDiscoveryService, IngestionResult
    RememberCommand → pipeline.memory(memory)

synapse/spector-connector/                   Apache-2.0
    SpectorIngestionSink(SpectorMemory, …)   ← was IngestionTarget

synapse/spector-synapse/                     BSL-1.1
    sensory impls: Tika, FFmpeg, Ollama vision/audio, LocalAssetStore
    owns the Tika dependencies
    wires extractors into SpectorMemoryBuilder.sensoryExtractors(...)
```

All four synapse-layer modules already declare both `spector-memory` and `spector-ingestion`, so no new inter-module dependencies are introduced by any of this.

---

## 7. `spector-ingestion` POM after

```xml
<dependencies>
    <dependency><groupId>com.spectrayan</groupId><artifactId>spector-commons</artifactId></dependency>
    <dependency><groupId>com.spectrayan</groupId><artifactId>spector-provider-api</artifactId></dependency>
    <!-- tika-core            REMOVED -> spector-synapse -->
    <!-- tika-parsers-*       REMOVED -> spector-synapse -->
    <!-- spector-config       REMOVED if only IngestionPipeline.fromProperties used it -->
    <!-- spector-providers    REMOVED if only the Ollama extractors used it -->
</dependencies>
```

Update the module description, which is currently false:

> Sensory extraction SPIs (`SensoryExtractor`, `AssetStore`) shared by `spector-memory` and the synapse layer. Implementations live in `spector-synapse`.

**Verification gate:** `mvn dependency:tree -pl memory/spector-memory` must contain no `org.apache.tika` artifact. That single assertion is the primary success criterion of this ADR.

---

## 6. Pros and Cons of the Options

| Alternative | Pros | Cons |
|:---|:---|:---|
| **Status Quo (`RememberPathway implements IngestionTarget`)** | No moves | Blocks typed `Pathway<S,R>`, circular dependency, crawls in memory layer |
| **Merge Ingestion into Memory** | Fewer modules | Memory module bloat with web crawlers and file extractors |
| **Invert Boundary & Sensory Relocation (Selected)** | Pure downward layering, typed results, clean sensory separation | Requires module moves across 3 modules |

## 7. Implementation Plan

Each step is independently shippable and revertible.

| Step | Change | Rollback |
|---|---|---|
| **I1** | Add `SpectorMemory.remember(id, text, vector, …)` (§4.2) + `DefaultSpectorMemory` impl. Additive only; nothing uses it yet. | Remove the method. |
| **I2** | `SpectorIngestionSink` takes `SpectorMemory`; L144 becomes the new overload. `ConnectorAutoConfiguration` passes `memory` instead of `memory.target()`. | One-file revert. |
| **I3** | Move `IngestionPipeline`, `FileDiscoveryService`, `IngestionResult` → `spector-cli`. Pipeline takes `SpectorMemory`. Drop `storeParentMetadata` / `onBatchComplete` calls (§4.3). Update `RememberCommand`. | `git mv` back. |
| **I4** | **Delete `IngestionTarget`.** `RememberPathway` drops the `implements`. Deprecate `SpectorMemory.target()`; delete `SpectorMemoryAdmin.target()` (duplicate of `rememberPathway()`). ⇒ **unblocks ADR-0035 M1.5.** | Restore the interface; one implementor. |
| **I5** | Move the 6 sensory impls → `spector-synapse`, with Tika deps. Mark `@Deprecated(forRemoval = true)`. Move the 4 test classes that use them. | `git mv` back. |
| **I6** | Remove Tika from `spector-ingestion`; prune now-unused deps; update module description. Assert via `dependency:tree`. | Restore POM entries. |

Ordering constraints:

- **I1 before I2 and I3.** Both need the vector overload to exist or they regress to double-embedding.
- **I2 + I3 before I4.** `IngestionTarget` cannot be deleted while consumers remain.
- **I4 before ADR-0035 M2**, since M2 declares `implements Pathway<I,O>`.
- **I5 before I6.** Tika cannot leave the POM while `TikaTextExtractor` is still in the module.

`SpectorMemory.target()` gets one minor version of `@Deprecated(forRemoval = true)` before removal, per repo convention for public-interface changes. `SpectorMemoryAdmin.target()` may be deleted immediately — `rememberPathway()` on the same interface is an exact replacement.

---

## 9. Testing

| Case | Expected |
|---|---|
| `remember(id, text, vector, …)` | stores the caller's vector verbatim; no embed call (assert with a counting `EmbeddingProvider`) |
| Sink delta-upsert | unchanged chunk ⇒ zero embed calls, zero remembers |
| Sink PII path | vector corresponds to the **scrubbed** text, not the raw text |
| CLI `spector remember <dir>` | same chunk count and memory count as before the move |
| Connector E2E (7 tests) | pass with `SpectorMemory` in place of `memory.target()` |
| `spector-memory` dependency tree | contains no `org.apache.tika` artifact — **the primary gate** |
| Standalone memory, no extractors | `AttachmentProcessor == null`, attachments skipped, no behavior change |
| `AttachmentProcessorTest` / `…FileTest` | pass unchanged — they mock the SPI, not the impls |
| Reactor build | no cycle; `mvn -q clean install -Psynapse -DskipTests` succeeds |

The seven connector E2E tests already assign into an `IngestionTarget`-typed local (`IngestionTarget target = memory.target();`), so they change to holding `SpectorMemory` — mechanical.

---

## 8. Code Reference & Verification

## 10. Consequences

### Positive

- **`RememberPathway` becomes a pathway and nothing else**, unblocking ADR-0035.
- Tika's closure (PDFBox, POI, Jackcess, commons-compress) leaves `spector-memory`, `spector-mcp`, `spector-cli`, `spector-batch`, `spector-spring`, and `bench`. Material CVE-surface and artifact-size reduction, which matters given recent Trivy / CodeQL remediation.
- One ingestion entry point. No more reaching through `SpectorMemory` to grab an internal pathway.
- Two silently-dead SPI methods stop pretending to work.
- `spector-ingestion` regains a truthful, single responsibility.
- The extraction SPI seam survives, so multi-modal can be built properly in synapse later.

### Negative / accepted

- **The extractor implementations relocate from Apache-2.0 into BSL-1.1 `spector-synapse`.** This is a real licensing change to previously Apache-2.0 code, accepted as a consequence of choosing existing modules over a new one. It is reversible while the code is still test-only.
- **Extraction becomes unreachable from `spector-mcp` and `spector-cli`** without depending on `spector-synapse` (and thus Spring Boot 4). Acceptable only because nothing wires extractors today (§1.3). This is the constraint most likely to force the revisit in §11.
- `SpectorMemory` gains a method. The interface is already wide; this one is load-bearing.
- Public API break on `SpectorMemory.target()`, mitigated by a deprecation window.
- Parent-document metadata and batch-complete hooks are formally dropped rather than implemented (§4.3) — no behavior change, but the capability gap is now explicit.

### Risks

| Risk | Mitigation |
|---|---|
| Double embedding after rewiring | I1 lands first; tests assert embed-call counts on the sink and pipeline paths. |
| Reactor cycle if the pipeline move is skipped | `spector-ingestion` must never gain a `spector-memory` dependency. Add an enforcer rule / CI assertion on the POM. |
| Someone re-adds Tika to `spector-ingestion` | `dependency:tree` assertion in CI, not just a one-time check. |
| BSL relicensing objected to later | Code is test-only and moved with `git mv`; reverting to a new Apache-2.0 leaf module (§4.1) is a rename, not a rewrite. |
| Multi-modal restarted in the wrong place | The SPI seam and §5.4's rule — synapse extracts, memory associates — are the contract to build against. |

---

## 11. Revisit when

- **MCP or CLI needs file extraction.** That is the trigger to extract the implementations into an Apache-2.0 `synapse/spector-sensory` leaf module (§4.1). Treat this as likely rather than hypothetical: `spector remember <dir>` currently reads files with `Files.readString` and would want Tika the moment it handles PDFs.
- Multi-modal support is actually implemented — at which point the `@Deprecated(forRemoval)` markers from I5 are either removed or the code is rewritten in its new home.
- A parent-document registry or batch-flush hook is genuinely needed, so the §4.3 gaps get designed as first-class `SpectorMemory` methods.
- A second ingestion target appears (a pure vector-store mode, an external sink). Only then does a target abstraction earn its place back — and it should be reintroduced deliberately, not inherited.

---

## 12. Known capability gaps created

Recorded so they are not rediscovered as bugs:

1. **Parent-document metadata** — `storeParentMetadata` deleted. It was a no-op; no regression. If wanted, design it as `SpectorMemory` API.
2. **Batch completion hook** — `onBatchComplete` deleted. Was a no-op; `SpectorMemory` has no flush/checkpoint to map it to. If WAL flush on batch boundaries is wanted, add it explicitly.
3. **Multi-modal extraction is deprecated-in-place, not functional.** Unchanged from today (§1.3), but now labelled as such.

---

### Code Reference & Verification Gate
- **Primary Module(s)**: `memory/spector-memory`, `memory/spector-ingestion`, `synapse/spector-connector`
- **Key Packages**: `com.spectrayan.spector.memory.pathway.remember`, `com.spectrayan.spector.ingestion`
- **Classes**: `RememberPathway.java`, `SpectorMemory.java`, `SpectorMemoryBuilder.java`
- **Verification Tests**: `RememberPathwayTest.java`, `IngestionBoundaryTest.java`
