# 🎛️ Engine & Algorithmic Tuning

> **Deep tuning guide for Spector's programmatic Java engine, HNSW graph parameters, quantization profiles, and hybrid search weights.** Learn how to optimize for recall, latency, throughput, or memory footprint using the `SpectorConfig` builder API.

<div class="grid cards" markdown>

-   :material-layers-outline: **Configuration Architecture**

    ---

    6-layer resolution hierarchy, precedence order, and profile mechanism.

    [View Architecture Guide ↗](index.md){ .md-button }

-   :material-file-document-outline: **spector.yml Master Reference**

    ---

    Exhaustive dictionary of all 32 configuration domains and 150+ YAML keys.

    [Explore spector.yml ↗](spector-yml.md){ .md-button }

-   :material-variable: **Environment Variables & Secrets**

    ---

    Canonical screaming snake_case mappings, short aliases, and Docker secrets.

    [Browse Environment Guide ↗](environment-variables.md){ .md-button }

-   :material-cloud-outline: **Deployment & Cloud Config**

    ---

    Docker container matrices, Helm `values.yaml`, and multi-cloud Terraform modules.

    [View Cloud Config ↗](deployment-config.md){ .md-button }

-   :material-api: **REST API & Runtime Parameters**

    ---

    Complete request schemas, headers, query parameters, and cognitive modifiers.

    [Explore API Parameters ↗](api-parameters.md){ .md-button }

</div>

---

## 🎯 Programmatic Core Parameters

When initializing Spector via the Java API, `SpectorConfig` provides the foundational builder for vector memory:

| Parameter | Default | Range | Description |
|:---|:---|:---|:---|
| `dimensions` | 384 | 1–4096 | Vector dimensionality (must match your embedding model) |
| `capacity` | 100,000 | 1–10,000,000 | Maximum document count |
| `similarityFunction` | COSINE | COSINE, DOT_PRODUCT, EUCLIDEAN | Distance metric |


> [!TIP]
> **Quick model reference:**
> | Model | Dimensions |
> |-------|-----------|
> | all-MiniLM-L6-v2 | 384 |
> | e5-base-v2 | 768 |
> | text-embedding-ada-002 | 1536 |
> | nomic-embed-text | 768 |

**Choosing a similarity function:**

- **COSINE** — Normalized embeddings (most models)

- **DOT_PRODUCT** — Unnormalized embeddings where magnitude matters

- **EUCLIDEAN** — Spatial/geometric data

---

## 🗜️ Quantization Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `quantization` | NONE | NONE, SCALAR_INT8, SCALAR_INT4, SCALAR_INT2, IVF_PQ | Quantization type |
| `oversamplingFactor` | auto | 1–20 | Rescore oversampling (auto: INT8→1, INT4→3, INT2→5) |

### 🎛️ Quantization Profiles

| Priority | Type | Oversampling | Compression | Recall | Use Case |
|----------|------|--------------|-------------|--------|----------|
| 🎯 Max recall | INT8 | 1 (none) | 4× | 95–99% | Quality-critical search |
| ⚖️ Balanced | INT4 | 3 | 8× | 85–95% | Best compression/recall ratio |
| 💾 Memory-first | INT2 | 5 | 16× | 75–90% | Fit large datasets in RAM |
| 🚀 Billion-scale | IVF_PQ | — | 32× | 75–90% | Massive datasets |

> [!TIP]
> **Start with INT4** for most workloads. It gives 8× compression with excellent recall when paired with the default 3× rescore. Only go to INT2 if memory is the binding constraint, or IVF-PQ if you're at billion scale.

### Oversampling Tuning

The `oversamplingFactor` controls how many extra candidates are retrieved before rescoring with exact distances:

- **1** — No rescore (fastest, quantized scores returned directly)

- **3** — Good balance for INT4 (retrieves 3×K candidates, rescores to top-K)

- **5** — Recommended for INT2 (compensates for aggressive quantization)

- **10+** — Diminishing returns; use only if recall is still insufficient

```java
// INT4 with custom oversampling
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withCapacity(50_000_000)
    .withQuantization(QuantizationType.SCALAR_INT4)
    .withRescore(5);  // Higher oversampling = better recall, slightly slower
```

---

## 🌐 HNSW Index Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `M` | 16 | 4–64 | Max connections per node per layer |
| `efConstruction` | 200 | 16–800 | Construction beam width |
| `efSearch` | 50 | 10–500 | Search beam width |

### 🎛️ Tuning Profiles

| Priority | M | efConstruction | efSearch | Trade-off |
|----------|---|----------------|----------|-----------|
| 🎯 High recall | 32–64 | 400–800 | 200–500 | More memory, slower build/search |
| ⚖️ Balanced | 16 | 200 | 50 | Good recall with fast performance |
| ⚡ Low latency | 8–12 | 100 | 20–30 | Faster search, lower recall |
| 💾 Memory-constrained | 4–8 | 100 | 20 | Minimal memory, lower recall |

> [!IMPORTANT]
> `efSearch` should be ≥ `topK` for meaningful results. Setting `efSearch < topK` means you're asking for more results than the algorithm explores.

---

## 📝 BM25 Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `k1` | 1.2 | 0.0–3.0 | Term frequency saturation |
| `b` | 0.75 | 0.0–1.0 | Document length normalization |

| Corpus Type | Recommended k1 | Recommended b |
|-------------|----------------|---------------|
| Short docs (tweets, titles) | 1.2 | 0.3 |
| Medium docs (articles) | 1.2 | 0.75 |
| Long docs (books, papers) | 1.5–2.0 | 0.75 |
| Mixed lengths | 1.2 | 0.5 |

---

## 🧬 Hybrid Search (RRF)

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `RRF k` | 60 | 1–1000 | Reciprocal Rank Fusion constant |

- `k = 60` — Original paper recommendation, works well generally

- Lower `k` (10–30) — Emphasizes top-ranked results more strongly

- Higher `k` (100+) — Flattens rank importance

---

## 🎮 GPU Configuration

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `gpuEnabled` | false | true/false | Enable CUDA GPU acceleration |
| `gpuMemoryBudget` | 256 MB | 256 MB – GPU max | Maximum GPU memory allocation |
| `gpuBatchWindow` | 10 ms | 1–100 ms | Batching window for query collection |
| `gpuMaxBatchSize` | 1024 | 1–1024 | Maximum queries per GPU batch |

> [!NOTE]
> Enable GPU for batch workloads with >10K vectors. Single queries are often faster on CPU SIMD due to zero kernel launch overhead.
> For INT4/INT2 quantization, GPU acceleration requires dimensions to be a multiple of 32. Non-aligned dimensions automatically fall back to CPU/SIMD.

---

## 🚀 ColBERT v2 Reranker Configuration

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `enableReranker` | false | true/false | Toggle token-level late interaction reranking |
| `rerankerDepth` | 20 | 5–100 | Number of first-stage candidates to rerank |

> [!TIP]
> ColBERT reranking executes in off-heap memory using Panama SIMD vector kernels. It typically adds less than 1ms of latency, providing cross-encoder precision with minimal overhead.

---

## 🖥️ Server Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `port` | 7070 | HTTP server port |
| `apiKey` | — | Optional API key (empty = no auth) |
| `corsOrigins` | * | Allowed CORS origins |

```bash
# Start Spector Synapse server on port 7070 with custom API key
SPECTOR_API_KEY=my-secret-key mvn -Psynapse -pl synapse/spector-synapse spring-boot:run
```

---

## 🌐 Cluster Configuration

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `shardCount` | 2 | 2–256 | Number of data shards |
| `replicaCount` | 1 | 1–5 | Replicas per shard |
| `heartbeatInterval` | 2s | 500ms–30s | Cluster heartbeat interval |
| `heartbeatTimeout` | 10s | 3s–120s | Node unavailability timeout |
| `queryTimeout` | 10s | 1s–60s | Per-shard query timeout |

> [!TIP]
> Rule of thumb: **100K–500K docs per shard** for optimal balance. Set `heartbeatTimeout` to at least 5× `heartbeatInterval`.

---

## 🧠 Memory Configuration

### Operating Mode

| Parameter | Default | Options | Description |
|-----------|---------|---------|-------------|
| `mode` | `MEMORY` | `MEMORY` | Unified memory operating mode (incorporates search and cognitive retrieval) |

### Memory Tier Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `nodesPerPartition` | 10,000 | 1,000–1,000,000 | Records per semantic partition file |
| `workingCapacity` | 100 | 10–10,000 | Working memory slots (volatile circular buffer) |
| `episodicPartitionCapacity` | 10,000 | 1,000–100,000 | Records per episodic partition |
| `semanticCapacity` | 5,000 | 100–1,000,000 | Single-file semantic capacity (in-memory mode) |
| `proceduralCapacity` | 500 | 10–100,000 | Procedural memory slots |

### Strength & Recall Telemetry Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `spector.memory.strength.enabled` | `true` | true/false | Enable the dedicated off-heap strength memory region (`RegionId.STRENGTH`) |
| `spector.memory.strength.stride-bytes` | `96` | 64–256 | Record stride in bytes for each strength entry (`DEFAULT_MEMORY_STRENGTH_STRIDE_BYTES`) |
| `spector.memory.strength.auto-ltp-cooldown-ms` | `300000` (5 min) | ≥ 0 | Cooldown window in ms between passive Auto-LTP reinforcement passes |
| `spector.memory.strength.auto-ltp-storage-increment` | `0.05` | 0.0–1.0 | Storage strength $S(t)$ increment applied during Auto-LTP passes |
| `spector.memory.strength.reserved-bytes` | `16` | 0–64 | Trailing reserved bytes per strength record for forward compatibility |

> [!NOTE]
> These configuration keys configure the dedicated strength region (`RegionId.STRENGTH`). Default values are supplied via `SpectorPropertyConstants.DEFAULT_MEMORY_STRENGTH_*`.

### 🗂️ Namespace Storage Layout & Routing

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `spector.namespace.tenant-rooted.enabled` | `true` | true/false | Enables tenant-rooted namespace sharding layout (`tenants/XX/YY/tenantId/namespaces/ZZ/WW/namespaceId`) for tenanted accounts (ADR-0034). Untenanted accounts (`tenantId == null`) always resolve to legacy flat sharded path (`namespaces/XX/YY/namespaceId`). |
| `spector.namespace.dual-read.enabled` | `true` | true/false | Enables dual-read fallback from layout B (tenant-rooted) to layout A (flat) during migration window (Req R5.3). Never dual-writes (I6). Fallbacks increment `spector.namespace.layout.fallback`. |

> [!NOTE]
> **Data Plane vs. Identity Plane Root Distinction**:
> - `spector.data-dir`: The node-level data directory holding `db/synapse.mv.db` (catalog) and `identity/` (character-prefix sharded soul & salience bundles per ADR-0029 §23).
> - `spector.memory.persistence-path` (or `persistence-path`): The rememberer persistence root (defaults to `${spector.data-dir}/cognitive` in Synapse, or `.spector/memory` in embedded kernel). All data plane rememberers (`NamespacePathResolver`) resolve relative to this directory.

### Retrieval Stack Parameters

| Parameter | Default | Options | Description |
|-----------|---------|---------|-------------|
| `text-search-mode` | `HYBRID` | `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, `SPLADE`, `SPLADE_HYBRID`, `LI_LSR`, `COLBERT_RERANK`, `FULL_STACK` | Active retrieval layers and paths |
| `bm25-enabled` | `true` | true/false | Enable SIMD-accelerated BM25 keyword matching |
| `splade-enabled` | `true` | true/false | Enable SPLADE learned sparse retrieval |
| `colbert-enabled` | `true` | true/false | Enable ColBERT v2 late-interaction reranking |

### Partitioned Semantic Storage

When using DISK persistence mode, semantic memories are stored in rolling partition files:

```
.spector/memory/semantic/
  semantic-000.mem     ← partition 0 (oldest, immutable)
  semantic-001.mem     ← partition 1 (immutable)
  semantic-002.mem     ← partition 2 (active, accepts writes)
```

**Tuning `nodesPerPartition`:**

- **Smaller partitions** (1K–5K) → faster compaction, more parallel search threads, more files
- **Larger partitions** (10K–50K) → fewer files, slightly lower overhead per partition
- **Default (10K)** → good balance for most workloads

> [!TIP]
> Existing single-file `semantic.mem` stores are automatically migrated to the partitioned format on first startup. No manual migration needed.

### Cluster Replication for Partitions

| Parameter | Default | Description |
|-----------|---------|-------------|
| `partitionReplicationEnabled` | false | Enable file-level partition snapshot shipping |
| `replicaCount` | 1 | Replicas per shard (1–5) |

When enabled, immutable semantic partitions are shipped as snapshots to replica nodes. Only the active (mutable) partition requires WAL-based delta replication.

---

## 📥 Ingestion & Chunking Configuration

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| `maxTokens` | 512 | 1–8192 | Max tokens per chunk |
| `overlapTokens` | 50 | 0–maxTokens-1 | Overlap between chunks |
| `embeddingBatchSize` | 32 | 1–256 | Batch size for embedding generation |
| `embeddingRetries` | 3 | 0–10 | Retry count for failed batches |

---

## 🎯 Configuration Examples

### 🎯 High-Recall Setup

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withCapacity(500_000)
    .withQuantization(QuantizationType.SCALAR_INT8)
    .withM(32)
    .withEfConstruction(400)
    .withEfSearch(200);
```

### 🗜️ Balanced Compression (INT4)

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withCapacity(50_000_000)
    .withQuantization(QuantizationType.SCALAR_INT4)
    .withRescore(3);  // default for INT4
```

### 💾 Maximum Compression (INT2)

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withCapacity(200_000_000)
    .withQuantization(QuantizationType.SCALAR_INT2)
    .withRescore(5);  // default for INT2
```

### ⚡ Low-Latency Setup

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(128)
    .withCapacity(100_000)
    .withM(12)
    .withEfConstruction(100)
    .withEfSearch(30);
```

### 🎮 GPU-Accelerated Batch Processing

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(768)
    .withCapacity(1_000_000)
    .withGpu(true)
    .withGpuMemoryBudget(2048);  // 2 GB
```

### 🤖 RAG Pipeline

```java
var config = SpectorConfig.DEFAULT
    .withDimensions(384)
    .withMaxTokens(1024)
    .withOverlapTokens(100)
    .withEmbeddingBatchSize(64);
```

---

## 🌐 Cell High Availability & Ownership Ring (`spector.cell.*`)

Spector Synapse supports multi-node Cell High Availability via a Ketama consistent hash ring (ADR-0034, Phase 1).

| Property | Environment Variable | Default | Allowed Values | Description |
|:---|:---|:---|:---|:---|
| `spector.cell.id` | `SPECTOR_CELL_ID` | `null` | String (e.g. `us-east-1`) | Cell identifier. Required when `role != standalone`. |
| `spector.cell.role` | `SPECTOR_CELL_ROLE` | `standalone` | `standalone`, `owner`, `replica`, `gateway` | Operational role. `standalone` short-circuits the ring and owns all namespaces locally. |
| `spector.cell.node-id` | `SPECTOR_CELL_NODE_ID` | Hostname | String (e.g. `pod-0`) | Node identity in the cell. Defaults to pod hostname for stable StatefulSet ordinals. |
| `spector.cell.ring.version` | `SPECTOR_CELL_RING_VERSION` | `1` | Integer $\ge 1$ | Generation of the Ketama hash ring. |
| `spector.cell.ring.members` | `SPECTOR_CELL_RING_MEMBERS` | `[]` | List of Strings | Explicit canonical member list of nodes participating in the hash ring. |
| `spector.cell.ring.members-file`| `SPECTOR_CELL_RING_MEMBERS_FILE`| `null` | Path string | Path to newline-delimited member list file (used in Docker Compose). |

> [!IMPORTANT]
> **Phase 1 Reload Semantics & Single-Writer Invariants**:
> - **Restart-Only**: In Phase 1, membership and ring configuration are loaded at startup and are **restart-only** (Req R7.4). Dynamic ring membership and coordinator-managed leases arrive in Phase 4.
> - **Ownership vs. Placement**: Namespace **ownership** (which server process is the authoritative single writer) is decided purely in-memory by the Ketama ring prior to namespace open. **Placement** (which filesystem directory holds the partition bundle) is determined by storage tenant paths (`StoragePaths`) and remains completely invariant to cluster routing changes (Invariant J6).
> - **Fail-Closed**: Non-owner nodes refuse both writes and recall with HTTP `421 Misdirected Request` (`NamespaceNotOwnedException`) identifying the authoritative owner node and ring epoch. Non-owner nodes **never** invoke `runtime.attach` or map files into memory.

---

## ⚡ Cell Routing Cache & Gateway Resilience (`spector.routing.*`)

Spector Synapse Phase 2 accelerates routing resolution via a three-tier waterfall (`L1 Caffeine` $\to$ `L2 Redis` $\to$ `L3 Ketama Hash Ring fallback`), provides pub/sub invalidation, and implements gateway forwarding with bounded retry (ADR-0034, Phase 2).

| Property | Environment Variable | Default | Allowed Values | Description |
|:---|:---|:---|:---|:---|
| `spector.routing.redis.enabled` | `SPECTOR_ROUTING_REDIS_ENABLED` | `false` | Boolean | Enables distributed Redis L2 routing cache and pub/sub invalidation bus. When `false`, cell operates seamlessly in degraded L1+L3 mode. |
| `spector.routing.redis.uri` | `SPECTOR_ROUTING_REDIS_URI` | `redis://localhost:6379` | URI string | Redis connection URI with cluster hash-tag support. Credentials should be supplied via environment variable placeholders only. |
| `spector.routing.redis.timeout-ms` | `SPECTOR_ROUTING_REDIS_TIMEOUT_MS` | `100` | Integer $\ge 10$ | Short lookup timeout with fail-fast. A lookup slower than the recall it precedes defeats its purpose. |
| `spector.routing.redis.ttl-seconds` | `SPECTOR_ROUTING_REDIS_TTL_SECONDS` | `30` | Integer $\ge 1$ | **Load-bearing staleness bound**. Missed invalidations self-heal within one TTL. Not merely a hit-rate tuning knob. |
| `spector.routing.caffeine.ttl-seconds` | `SPECTOR_ROUTING_CAFFEINE_TTL_SECONDS` | `5` | Integer $\ge 1$ | Node-local and gateway L1 in-process cache TTL. |
| `spector.routing.caffeine.max-size` | `SPECTOR_ROUTING_CAFFEINE_MAX_SIZE` | `200000` | Integer $\ge 1$ | Maximum entries retained in the L1 Caffeine cache. |
| `spector.routing.gateway.retry-max` | `SPECTOR_ROUTING_GATEWAY_RETRY_MAX` | `2` | Integer $\ge 0$ | Maximum retry attempts on `STALE_ROUTE` (HTTP 421). Prevents retry storms under rebalances. |

> [!NOTE]
> **Operational Note — Redis Outage Behavior (Req R9.2)**:
> - **What breaks when Redis is down**: Latency increases slightly as lookups fall back to local pure-computation Ketama ring evaluation. Failover overrides (Phase 4) are temporarily masked until the control store republishes.
> - **What does NOT break**: **Single-writer correctness and write availability never break.** The cell functions continuously as a single-writer system even if Redis crashes entirely (Invariant K1). Zero dual-writers, zero data loss. Operators should not page on transient Redis hiccups.
> - **Production Topology (Req R9.3, R9.4)**: Managed cloud offerings (AWS ElastiCache, GCP Memorystore, Azure Cache for Redis) are recommended for production; containerized Redis is for local testing. Redis instances **must be provisioned per cell**, never shared across multi-region cells.

---

## 🔗 See Also

- [Performance Tuning](../operations/performance-tuning.md) — Benchmarks and optimization strategies

- [Architecture Overview](../architecture/overview.md) — How configuration affects system behavior

- [Distributed Mode](../architecture/distributed-mode.md) — Cluster-specific configuration

- [GPU Acceleration](../architecture/gpu-acceleration.md) — GPU setup requirements