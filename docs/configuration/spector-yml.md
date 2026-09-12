# 📄 spector.yml Master Reference

> **The definitive configuration property reference for Spector.** Every section, nested key, data type, default value, and valid range supported by `spector-config` is cataloged here.

---

## 1. Global & Concurrency

| Property | Type | Default | Options / Range | Description |
|:---|:---|:---|:---|:---|
| `spector.mode` | String | `MEMORY` | `MEMORY`, `SEARCH_ONLY` | Primary operating mode. `MEMORY` activates the unified cognitive kernel; `SEARCH_ONLY` restricts execution to raw vector retrieval. |
| `spector.concurrency.structured` | Boolean | `true` | `true`, `false` | Enables Java Project Loom structured concurrency (`StructuredTaskScope`) across async worker threads. |
| `spector.events.async` | Boolean | `false` | `true`, `false` | Asynchronous event bus dispatching for background memory lifecycle notifications. |
| `spector.embedding.sequential` | Boolean | `false` | `true`, `false` | Disables parallel vector batching; forces single-threaded deterministic vector embeddings. |

---

## 2. Multi-LLM Embedding Provider

Configuration block for embedding model providers under `spector.provider.embedding.*`:

| Property | Type | Default | Options / Range | Description |
|:---|:---|:---|:---|:---|
| `type` | String | `ollama` | `ollama`, `openai`, `google`, `anthropic`, `mistral`, `azure`, `bedrock`, `onnx` | Active embedding provider adapter. |
| `model` | String | `nomic-embed-text` | Any valid model ID | Identifier of the embedding model (e.g., `text-embedding-3-small`, `text-embedding-004`). |
| `base-url` | String | `http://localhost:11434` | Valid HTTP/S URI | Base URL of the provider endpoint (required for Ollama, Azure, LocalAI, vLLM). |
| `api-key` | String | `""` | Secret string | API key for authentication. Can also be injected via environment variable or Docker Secret. |
| `dimensions` | Integer | `768` | 1–4096 | Vector dimensionality. Must strictly match the output vector length of the specified model. |
| `timeout` | Duration | `30s` | Standard ISO duration | HTTP client socket and connection timeout (e.g., `10s`, `1m`, `2m`). |
| `batch-size` | Integer | `32` | 1–512 | Max vectors per remote embedding API call. |
| `max-retries` | Integer | `3` | 0–10 | Exponential backoff retry attempts on HTTP 429 / 5xx responses. |
| `max-concurrent` | Integer | `0` | 0–128 | Maximum concurrent outbound embedding calls (0 = unbounded virtual thread dispatch). |
| `cache.enabled` | Boolean | `true` | `true`, `false` | In-memory LRU cache for computed text embeddings to eliminate redundant LLM calls. |
| `cache.max-size` | Integer | `1000` | 100–1,000,000 | Maximum cached embedding vectors in memory. |
| `cache.ttl` | Duration | `60m` | Standard ISO duration | Time-to-live for cached embeddings before expiration. |
| `cache.stats-log-interval` | Duration | `5m` | Standard ISO duration | Log interval for embedding cache hit/miss telemetry. |
| `model-path` | Path | `""` | File path | Filesystem path to local ONNX model file (only used when `type: onnx`). |
| `execution-provider` | String | `CPU` | `CPU`, `CUDA`, `TENSOR_RT` | Execution provider hardware accelerator for local ONNX inference. |
| `intra-op-threads` | Integer | `0` | 0–64 | Number of internal math threads for ONNX CPU runtime (0 = auto-detect cores). |
| `vocab-path` | Path | `""` | File path | Path to tokenizer vocabulary file for local ONNX tokenization. |

---

## 3. Multi-LLM Generation Provider

Configuration block for generative LLMs under `spector.provider.generation.*`:

| Property | Type | Default | Options / Range | Description |
|:---|:---|:---|:---|:---|
| `type` | String | `ollama` | `ollama`, `openai`, `google`, `anthropic`, `mistral`, `azure`, `bedrock` | Active text generation provider. |
| `model` | String | `llama3.2` | Model identifier | Model name used for cognitive reflection, AISME dreaming, and entity synthesis. |
| `base-url` | String | `http://localhost:11434` | Valid HTTP/S URI | Provider endpoint base URL. |
| `api-key` | String | `""` | Secret string | Authentication API token for generation provider. |
| `timeout` | Duration | `60s` | Standard ISO duration | Timeout window for generative completion calls. |
| `fallback-model` | String | `qwen3:0.6b` | Model identifier | Low-latency fallback model invoked when primary generation encounters rate limits or errors. |
| `spector.ssl.insecure` | Boolean | `false` | `true`, `false` | Disables TLS certificate validation (development and self-signed proxy use only). |

---

## 4. Cognitive Memory Core

Core memory engine parameters located under `spector.memory.*`:

| Property | Type | Default | Options / Range | Description |
|:---|:---|:---|:---|:---|
| `enabled` | Boolean | `true` | `true`, `false` | Master toggle for the cognitive memory subsystem. |
| `persistence-mode` | String | `DISK` | `DISK`, `MEMORY` | Persistence backend. `DISK` uses zero-GC memory-mapped files; `MEMORY` is ephemeral in-RAM only. |
| `persistence-path` | Path | `.spector/memory` | Path string | Base filesystem directory where Panama FFM off-heap memory bundles and WAL logs are persisted. |
| `dimensions` | Integer | `384` | 1–4096 | Dimensionality of stored cognitive memory vectors. Must match embedding provider dimensions. |
| `capacity` | Integer | `100000` | 100–100,000,000 | Global upper capacity limit across all memory partitions. |
| `id-strategy` | String | `TSID` | `TSID`, `UUID_V4`, `UUID_V7` | ID generation algorithm for new memories. `TSID` (Time-Sorted ID) ensures chronological index locality. |
| `nodes-per-partition` | Integer | `10000` | 1,000–100,000 | Number of memory records per rolling partitioned chunk file (`semantic-xxx.mem`). |
| `checkpoint-interval-seconds` | Integer | `30` | 5–3600 | Off-heap dirty page flush interval to persistent storage. |
| `max-namespaces` | Integer | `100` | 1–10,000 | Maximum tenant namespaces supported concurrently. |
| `namespace-id` | String | `default` | 1–63 chars | Default active namespace identifier for memory isolation. |
| `persist-working-memory` | Boolean | `false` | `true`, `false` | If true, flushes volatile working memory ring buffers to disk during graceful shutdown. |
| `pin-source-episodes` | Boolean | `false` | `true`, `false` | Prevents source episodic memories from being garbage collected when consolidated into semantic abstractions. |
| `edge-importance` | String | `DEFAULT` | `DEFAULT`, `SALIENCE`, `DECAY_WEIGHTED` | Weighting regime used for cognitive graph edge traversal. |

---

## 5. Cognitive Tiers & Capacity Quotas

Capacity quotas for Spector's 4 memory tiers under `spector.memory.*`:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `working-capacity` | Integer | `100` | 10–1,000 | Size of the volatile circular buffer representing immediate short-term context. |
| `episodic-partition-capacity` | Integer | `1000` | 100–50,000 | Record capacity per chronological episodic memory partition. |
| `semantic-capacity` | Integer | `10000` | 1,000–10,000,000 | Capacity of general declarative knowledge and consolidated abstractions. |
| `procedural-capacity` | Integer | `1000` | 100–100,000 | Storage capacity for agent tools, workflows, executable skills, and rules. |
| `entity-graph-capacity` | Integer | `50000` | 1,000–1,000,000 | Maximum nodes and relational edges held within the entity knowledge graph. |
| `pinned-quota` | Integer | `10000` | 0–1,000,000 | Reserved memory slots immune to circadian decay, pruning, and tombstones. |
| `provenance-capacity` | Integer | `8192` | 512–65,536 | Maximum audit trail entries tracking memory lineage, merges, and generative synthesis. |
| `default-ingestion-tier` | String | `SEMANTIC` | `WORKING`, `EPISODIC`, `SEMANTIC`, `PROCEDURAL` | Destination tier assigned to incoming text records when not explicitly specified. |

---

## 6. Synaptic Dynamics, Habituation & Inhibition

Biomimetic synaptic decay, habituation, and lateral inhibition under `spector.memory.*`:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `decay-enabled` | Boolean | `true` | `true`, `false` | Activates Ebbinghaus power-law forgetting curves across memory records. |
| `consolidation-interval` | Duration | `60s` | Standard ISO duration | Cadence of the background Hippocampus sleep consolidation and replay thread. |
| `consolidation.eager-queue-capacity` | Integer | `256` | 16–4096 | In-memory priority queue limit for memories flagged for urgent immediate consolidation. |
| `surprise-warmup` | Integer | `10` | 0–100 | Warmup query count before Bayesian surprise detection activates. |
| `flashbulb-threshold` | Float | `3.0` | 1.0–10.0 | Surprise standard deviations above mean required to create an indelible "flashbulb" memory. |
| `valence-learning-rate` | Float | `0.3` | 0.01–1.0 | Learning rate ($\alpha$) for updating emotional valence upon human feedback. |
| `deduplication-radius` | Float | `0.05` | 0.001–0.5 | Maximum cosine distance threshold to treat two memories as duplicate concepts. |
| `inhibition-ttl-ms` | Long | `300000` | $\ge 0$ (ms) | Time-to-live for active lateral inhibition tags (prevents repetitive agent recall loops). |
| `inhibition-floor` | Float | `0.1` | 0.0–1.0 | Minimum retrieval score floor for suppressed memories. |
| `habituation-decay-rate` | Float | `0.2` | 0.01–1.0 | Rate at which frequently recalled memories lose novelty boost (anti-filter-bubble). |
| `ltp-cooldown-ms` | Long | `300000` | $\ge 0$ (ms) | Minimum cooldown between Long-Term Potentiation (Auto-LTP) synaptic weight increments. |

---

## 7. Hebbian & STDP Synaptic Plasticity

Configurations for associative Hebbian learning and Spike-Timing-Dependent Plasticity:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `hebbian.max-degree` | Integer | `24` | 4–128 | Maximum synaptic co-occurrence connections permitted per memory node. |
| `hebbian.session-boundary-ms` | Long | `300000` | $\ge 0$ (ms) | Time window within which co-occurring memories trigger Hebbian fire-together wire-together bonding. |
| `hebbian.promotion-min-weight` | Float | `3.0` | 0.5–20.0 | Cumulative edge weight required to promote an episodic connection to permanent semantic status. |
| `hebbian.decay-factor` | Float | `0.9` | 0.1–1.0 | Synaptic edge weight retention multiplier applied during circadian sleep cycles. |
| `stdp.a-plus` | Float | `0.1` | 0.01–1.0 | Positive STDP potentiation factor for causal pre-then-post activations. |
| `stdp.a-minus` | Float | `0.05` | 0.01–1.0 | Negative STDP depression factor for acausal post-then-pre activations. |
| `stdp.tau-plus` | Long | `30000` | $\ge 1$ (ms) | Temporal time constant ($\tau_+$) for causal STDP potentiation window. |
| `stdp.tau-minus` | Long | `30000` | $\ge 1$ (ms) | Temporal time constant ($\tau_-$) for acausal STDP depression window. |

---

## 8. 4-Layer Cognitive Graph & Entity Resolution

Parameters governing graph expansion, entity linkage, and semantic bridging:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `graph.expansion-mode` | String | `GATED` | `GATED`, `OPEN`, `DISABLED` | Multi-hop associative graph expansion strategy during retrieval. |
| `graph.causal-boost` | Float | `0.3` | 0.0–2.0 | Retrieval score multiplier for directed causal graph relationships. |
| `graph.hebbian-boost` | Float | `0.3` | 0.0–2.0 | Retrieval score multiplier for learned associative Hebbian edges. |
| `graph.temporal-forward` | Float | `0.8` | 0.0–1.0 | Directional weight discount for traversing forwards in time. |
| `graph.temporal-backward` | Float | `0.7` | 0.0–1.0 | Directional weight discount for traversing backwards in time. |
| `graph.entity-attenuation` | Float | `0.25` | 0.0–1.0 | Score dampening factor per hop across the entity knowledge graph. |
| `graph.expansion-threshold` | Float | `0.40` | 0.0–1.0 | Minimum composite edge weight required to traverse a graph connection. |
| `entity.extraction-mode` | String | `NONE` | `NONE`, `HEURISTIC`, `LLM` | Method used to extract named entities from newly remembered text. |
| `entity.resolution-enabled` | Boolean | `false` | `true`, `false` | Enables entity deduplication and canonical alias resolution. |
| `entity.shadow-mode` | Boolean | `true` | `true`, `false` | Runs entity extraction in shadow mode without modifying active graph state. |
| `entity.max-degree` | Integer | `16` | 2–64 | Maximum relationship connections allowed per extracted entity node. |
| `entity.max-per-memory` | Integer | `10` | 1–50 | Maximum entity mentions extracted per memory record. |
| `entity.cosine-threshold` | Float | `0.85` | 0.5–1.0 | Embedding similarity threshold for merging entity mentions into a single entity. |
| `entity.retention-days` | Integer | `7` | 1–365 | Time before unreferenced ephemeral entity mentions are pruned from the graph. |
| `entity.decay-factor` | Float | `0.95` | 0.5–1.0 | Daily retention factor for entity node salience. |
| `entity.prune-threshold` | Float | `0.5` | 0.0–1.0 | Salience cutoff below which stale entity nodes are removed. |
| `bridge.sample-count` | Integer | `15` | 5–100 | Number of intermediate bridge nodes sampled for lateral cognitive jumps. |
| `bridge.budget-ms` | Long | `500` | 10–5000 (ms) | Time budget allocated for lateral exploratory retrieval. |

---

## 9. Autonomous Identity, Dreaming & Circadian Cycles

Configurations for AISME (Autonomous Identity & State Maintenance Engine) and dreaming:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `circadian.volume-trigger` | Integer | `100` | 10–10,000 | Number of newly remembered items before a circadian sleep consolidation pass triggers. |
| `circadian.time-trigger` | Duration | `1h` | Standard ISO duration | Periodic sleep consolidation interval when volume threshold is not met. |
| `circadian.tombstone-threshold` | Float | `0.30` | 0.01–1.0 | Activation score below which decayed episodic memories are tombstoned. |
| `circadian.decay-prune-threshold` | Float | `0.05` | 0.001–0.5 | Absolute floor score below which tombstoned memories are permanently purged. |
| `circadian.interference-threshold` | Float | `0.12` | 0.01–0.5 | Cosine distance threshold triggering retroactive proactive interference dampening. |
| `circadian.interference-decay-factor` | Float | `0.7` | 0.1–1.0 | Retention multiplier applied to conflicting or obsolete prior memories. |
| `reflect.min-cluster-size` | Integer | `5` | 2–50 | Minimum episodic memory cluster size required to synthesize a semantic belief. |
| `session.buffer-size` | Integer | `64` | 8–512 | Ring buffer capacity for immediate conversation session turn history. |
| `session.buffer-ttl-ms` | Long | `5000` | $\ge 0$ (ms) | Turn buffer debounce window in milliseconds. |
| `namespace.max-id-length` | Integer | `63` | 16–255 | Maximum character length allowed for tenant namespace identifiers. |
| `namespace.soft-warning-threshold` | Float | `0.70` | 0.1–1.0 | Percentage of namespace capacity quota that generates audit warnings. |
| `hyperfocus.ttl-ms` | Long | `1800000` | $\ge 0$ (ms) | Duration (30m) of elevated attention bias towards a specific tag cluster. |
| `icnu.threshold` | Float | `0.2` | 0.0–1.0 | Minimum ICNU composite score required for auto-promotion to semantic tier. |
| `icnu.steepness` | Float | `8.0` | 1.0–20.0 | Logistic sigmoid slope for nonlinear importance curve mapping. |
| `icnu.weight-interest` | Float | `0.30` | 0.0–1.0 | Relative weight ($w_I$) for intrinsic agent interest. |
| `icnu.weight-challenge` | Float | `0.10` | 0.0–1.0 | Relative weight ($w_C$) for cognitive difficulty / challenge. |
| `icnu.weight-novelty` | Float | `0.40` | 0.0–1.0 | Relative weight ($w_N$) for semantic novelty / unexpectedness. |
| `icnu.weight-urgency` | Float | `0.20` | 0.0–1.0 | Relative weight ($w_U$) for execution urgency / time-sensitivity. |

---

## 10. Vector Index: HNSW, IVF/PQ & SPECTRUM

Configurations for vector indexing under `spector.hnsw.*`, `spector.ivf.*`, and `spector.spectrum.*`:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `hnsw.m` | Integer | `16` | 4–64 | Maximum bidirectional connections per node per layer in the HNSW graph. |
| `hnsw.ef-construction` | Integer | `200` | 16–800 | Size of dynamic candidate list during graph index construction. |
| `hnsw.ef-search` | Integer | `50` | 10–500 | Size of dynamic candidate list during query retrieval. |
| `memory.hnsw-prefilter` | String | `auto` | `auto`, `none`, `first` | HNSW pre-filtering mode when combining vector search with synaptic tag bitmasks. |
| `ivf.nlist` | Integer | `0` | 0–65536 | Number of Voronoi partitioning centroids for IVF indexing (0 = disabled). |
| `ivf.nprobe` | Integer | `0` | 0–1024 | Number of IVF centroids inspected per vector query. |
| `ivf.pq-subspaces` | Integer | `0` | 0–256 | Number of Product Quantization sub-vectors (0 = unquantized). |
| `spectrum.n-centroids` | Integer | `256` | 16–4096 | Number of coarse quantization centroids for SPECTRUM adaptive indexing. |
| `spectrum.n-probe` | Integer | `16` | 1–256 | Centroids probed during SPECTRUM retrieval. |
| `spectrum.shard-threshold` | Integer | `20000` | 1,000–1,000,000 | Vector count threshold triggering dynamic shard split. |
| `spectrum.oversampling-factor` | Integer | `3` | 1–10 | Multiplier for coarse candidates retrieved before fine-grained distance rescoring. |
| `spectrum.kmeans-iterations` | Integer | `25` | 5–100 | Max iterations for centroid convergence during K-Means clustering. |

---

## 11. SVASQ Quantization & HDC Hypervectors

Configurations for zero-GC SIMD vector compression and hyperdimensional computing:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `quantization.svasq.seed` | Long | `42` | Any 64-bit int | Pseudorandom seed for repeatable SVASQ calibration and randomized projection. |
| `quantization.svasq.clip-percentile` | Float | `0.001` | 0.00001–0.05 | Tail percentile outlier clipping threshold during quantizer calibration. |
| `quantization.svasq.clip-sigmas` | Float | `3.0` | 1.5–5.0 | Standard deviation cutoff for 8-bit scalar quantization bounding. |
| `quantization.svasq.clip-sigmas-4bit` | Float | `2.5` | 1.5–4.0 | Standard deviation cutoff for 4-bit scalar quantization bounding. |
| `quantization.svasq.max-sample-size` | Integer | `10000` | 1,000–100,000 | Maximum vector sample size used to compute calibration quantiles. |
| `quantization.svasq.min-std` | Float | `1e-6` | $> 0$ | Minimum standard deviation floor to prevent division by zero on uniform dimensions. |
| `hdc.dimensions` | Integer | `10000` | 1,000–100,000 | Hyperdimensional computing binary / bipolar vector dimensionality. |
| `hdc.ngram-size` | Integer | `3` | 1–8 | Character n-gram window size for HDC text encoding and holographic projection. |

---

## 12. Query, Hybrid Retrieval & Recall Pipeline

Configurations for multi-stage search, hybrid fusion, and cognitive recall:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `query.default-top-k` | Integer | `10` | 1–500 | Default number of ranked memory candidates returned when unspecified. |
| `query.rrf-k` | Integer | `60` | 1–1000 | Reciprocal Rank Fusion constant ($k$) for balancing dense and sparse result lists. |
| `query.reranker.max-candidates` | Integer | `20` | 5–200 | Maximum candidates passed to the neural cross-encoder or ColBERT reranker. |
| `query.hybrid.fanout-multiplier` | Integer | `2` | 1–10 | Multiplier applied to $K$ when gathering candidates from individual search indexes. |
| `query.hybrid.min-retrieval-k` | Integer | `50` | 10–1000 | Minimum candidate pool size required before reciprocal rank fusion. |
| `recall.text-search.enabled` | Boolean | `true` | `true`, `false` | Enables full hybrid text retrieval alongside pure vector similarity. |
| `recall.text-search.mode` | String | `HYBRID` | `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, `SPLADE`, `SPLADE_HYBRID`, `LI_LSR`, `COLBERT_RERANK`, `FULL_STACK` | Active retrieval layers executing during recall. |
| `recall.scoring-mode` | String | `COGNITIVE` | `COGNITIVE`, `SIMILARITY`, `ASSOCIATIVE` | Scoring formula applied: `COGNITIVE` applies the 6-phase scoring equation ($S_{composite}$); `SIMILARITY` isolates cosine distance. |
| `recall.trace.enabled` | Boolean | `false` | `true`, `false` | Emits detailed phase-by-phase scoring math breakdown in REST API responses. |
| `recall.reranker.enabled` | Boolean | `false` | `true`, `false` | Enables post-retrieval neural reranking step. |
| `recall.reranker.depth` | Integer | `50` | 5–200 | Depth of candidates evaluated during neural reranking. |
| `recall.mmr.enabled` | Boolean | `false` | `true`, `false` | Enables Maximal Marginal Relevance diversification to prevent redundant results. |
| `recall.mmr.lambda` | Float | `0.5` | 0.0–1.0 | MMR tradeoff parameter: 1.0 = pure relevance, 0.0 = maximal novelty/diversity. |
| `recall.auto-profile.enabled` | Boolean | `false` | `true`, `false` | Dynamically adapts cognitive retrieval weights based on query intent analysis. |
| `recall.include-contradictions` | Boolean | `false` | `true`, `false` | Whether to surface memories flagged with opposing belief tags for dialectic reasoning. |
| `recall.lateral.enabled` | Boolean | `false` | `true`, `false` | Enables creative multi-hop lateral retrieval through weak associative bridges. |
| `recall.strictness-coefficient` | Float | `1.0` | 0.1–5.0 | Exponent applied to composite scores to sharpen top-1 margin. |
| `recall.valence-alignment.enabled` | Boolean | `false` | `true`, `false` | Biases candidate selection towards memories matching current emotional state. |
| `recall.mode` | String | `LEARN` | `LEARN`, `OBSERVE`, `REPLAY` | `LEARN` strengthens synaptic pathways upon retrieval; `OBSERVE` reads passively without modifying memory weights. |
| `recall.max-replay-events` | Integer | `100000` | 1,000–10,000,000 | Maximum event capacity for retrospective replay evaluation. |

---

## 13. Persistence, WAL & Compaction

Storage file paths, Write-Ahead Logging (WAL), and compaction configurations:

| Property | Type | Default | Options / Range | Description |
|:---|:---|:---|:---|:---|
| `persistence.files.index` | String | `index.spct` | File name | File name for the serialized vector index structure. |
| `persistence.files.vectors` | String | `vectors.mmap` | File name | File name for the zero-GC off-heap Panama memory-mapped vector region. |
| `persistence.files.documents` | String | `documents.dat` | File name | File name for raw document texts and JSON payloads. |
| `persistence.files.id-mappings` | String | `id-mappings.dat` | File name | File name for external key to internal offset bidirectional mapping. |
| `persistence.files.shard-dir-name` | String | `index_shards` | Directory name | Subfolder name storing partition shards in distributed cluster mode. |
| `memory.wal.max-chunk-bytes` | Long | `8388608` (8MB) | 1MB–1GB | Maximum size of an active WAL segment file before rotating to a new chunk. |
| `memory.vacuum.threshold` | Float | `0.20` | 0.05–0.80 | Fragmentation ratio (tombstones / total nodes) that triggers background vacuum compaction. |

---

## 14. Ingestion & Document Chunking

Configurations for batch document ingestion and recursive text chunking:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `ingestion.root-directory` | Path | `.` | Directory path | Root folder recursively scanned for ingestible files. |
| `ingestion.file-pattern` | String | `**/*.md` | Glob expression | Comma-separated glob patterns matching target documents (e.g. `**/*.md,**/*.txt,**/*.pdf`). |
| `ingestion.skip-dirs` | String | `.git,.idea,.mvn,target,node_modules,.github` | Comma-separated strings | Directory names bypassed during file discovery. |
| `ingestion.chunk-size` | Integer | `2500` | 100–32,000 | Character length per recursive text chunk. |
| `ingestion.chunk-overlap` | Integer | `200` | 0–1,000 | Overlapping character boundary preserved between adjacent chunks. |
| `ingestion.parallelism` | Integer | `4` | 1–64 | Number of parallel worker threads processing and embedding documents. |
| `ingestion.max-retries` | Integer | `3` | 0–10 | Retry attempts for files encountering transient I/O or tokenization errors. |
| `ingestion.retry-delay-ms` | Long | `2000` | $\ge 0$ (ms) | Delay between ingestion retries. |
| `chunking.text.size` | Integer | `512` | 64–8192 | Standard character count for baseline sentence chunker. |
| `chunking.text.overlap` | Integer | `64` | 0–1024 | Standard character overlap for baseline chunker. |
| `chunking.token.limit` | Integer | `128` | 16–2048 | Max token length enforced when chunking for fixed-window embedding models. |
| `chunking.token.overlap` | Integer | `16` | 0–256 | Token overlap between adjacent token windows. |
| `chunking.document.max-size` | Long | `104857600` (100MB) | 1MB–1GB | Maximum single file size permitted for batch ingestion. |

---

## 15. Multimodal Sensory Media

Configurations for image, audio, and video sensory ingestion under `spector.multimodal.*`:

| Property | Type | Default | Range | Description |
|:---|:---|:---|:---|:---|
| `enabled` | Boolean | `false` | `true`, `false` | Master toggle for multimodal processing and sensory asset ingestion. |
| `vision.model` | String | `moondream` | Model identifier | Vision-language model for image captioning and visual semantic feature extraction. |
| `vision.base-url` | String | `http://localhost:11434` | HTTP/S URI | Endpoint URL for the vision model provider. |
| `vision.timeout` | Integer | `120` | 10–600 (s) | Request timeout in seconds for visual inference. |
| `vision.max-image-size` | Long | `20971520` (20MB) | 1MB–100MB | Maximum image upload file size. |
| `audio.model` | String | `gemma4` | Model identifier | Audio transcription and acoustic feature model. |
| `audio.timeout` | Integer | `180` | 10–600 (s) | Request timeout in seconds for audio processing. |
| `audio.max-file-size` | Long | `52428800` (50MB) | 1MB–500MB | Maximum audio upload file size. |
| `video.keyframe-interval-seconds` | Integer | `10` | 1–60 (s) | Video sampling cadence for extracting sensory keyframe images. |
| `video.max-keyframes` | Integer | `30` | 1–300 | Maximum keyframes extracted from any single video file. |
| `asset-store.type` | String | `local` | `local`, `s3`, `gcs` | Storage engine for raw binary media assets. |
| `asset-store.base-path` | Path | `.spector/assets` | Path string | Base filesystem folder for locally stored media assets. |
| `tika.max-content-length` | Long | `104857600` (100MB) | 1MB–1GB | Content length limit for Apache Tika document text extraction. |

---

## 16. Complete Production `spector.yml` Template

Here is a full, production-ready `spector.yml` template configured for an enterprise deployment with Ollama, Panama FFM zero-GC persistence, and hybrid cognitive search:

```yaml
# ═══════════════════════════════════════════════════════════════════
# Spector Enterprise Production Configuration — spector.yml
# ═══════════════════════════════════════════════════════════════════

spector:
  mode: MEMORY

  concurrency:
    structured: true

  events:
    async: true

  # ── Multi-LLM Providers ──
  provider:
    embedding:
      type: ollama
      model: nomic-embed-text
      base-url: http://localhost:11434
      dimensions: 768
      batch-size: 64
      timeout: 30s
      max-retries: 3
      cache:
        enabled: true
        max-size: 10000
        ttl: 120m
    generation:
      type: ollama
      model: llama3.2
      base-url: http://localhost:11434
      timeout: 60s
      fallback-model: qwen3:0.6b

  # ── Cognitive Memory Engine ──
  memory:
    enabled: true
    persistence-mode: DISK
    persistence-path: /data/memory
    dimensions: 768
    capacity: 500000
    id-strategy: TSID
    nodes-per-partition: 10000
    checkpoint-interval-seconds: 30

    # Capacities
    working-capacity: 200
    episodic-partition-capacity: 5000
    semantic-capacity: 100000
    procedural-capacity: 5000
    entity-graph-capacity: 100000
    pinned-quota: 25000

    # Synaptic Dynamics & Plasticity
    decay-enabled: true
    consolidation-interval: 60s
    default-ingestion-tier: SEMANTIC
    surprise-warmup: 10
    flashbulb-threshold: 3.0
    valence-learning-rate: 0.3
    deduplication-radius: 0.05
    inhibition-ttl-ms: 300000
    habituation-decay-rate: 0.2

    # Hebbian Plasticity
    hebbian:
      max-degree: 32
      session-boundary-ms: 300000
      promotion-min-weight: 3.0
      decay-factor: 0.90

    # 4-Layer Cognitive Graph
    graph:
      expansion-mode: GATED
      causal-boost: 0.35
      hebbian-boost: 0.30
      expansion-threshold: 0.40

    # Circadian Sleep Cycle
    circadian:
      volume-trigger: 150
      time-trigger: 1h
      tombstone-threshold: 0.30
      decay-prune-threshold: 0.05
      interference-threshold: 0.12

    # ICNU Saliency Tuning
    icnu:
      threshold: 0.25
      steepness: 8.0
      weight-interest: 0.30
      weight-challenge: 0.10
      weight-novelty: 0.40
      weight-urgency: 0.20

  # ── HNSW Vector Index ──
  hnsw:
    m: 24
    ef-construction: 300
    ef-search: 80

  # ── Multi-Stage Recall ──
  recall:
    text-search:
      enabled: true
      mode: HYBRID
    scoring-mode: COGNITIVE
    trace:
      enabled: false
    mmr:
      enabled: true
      lambda: 0.65
    mode: LEARN

  # ── Batch Ingestion ──
  ingestion:
    root-directory: /data/docs
    file-pattern: "**/*.md,**/*.txt,**/*.pdf"
    skip-dirs: ".git,node_modules,target"
    chunk-size: 2000
    chunk-overlap: 200
    parallelism: 8
```
