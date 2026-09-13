---
title: "Spector Glossary — Cognitive, Indexing & Systems Terms"
description: "Plain-language definitions of the neuroscience, math, indexing, and systems terms used across the Spector documentation: engrams, Hebbian plasticity, LTP, SVASQ, FWHT, HNSW, BM25, SPLADE, ColBERT, Panama FFM, WAL, fencing tokens, and more."
tags:
  - glossary
  - reference
---

# 📖 Glossary

> **Spector borrows vocabulary from three worlds: cognitive neuroscience, information retrieval, and low-level systems engineering.** This page defines each term in plain language, explains what it means *inside Spector*, and links to the page where it is covered in depth.

Terms are listed alphabetically. Each entry is tagged with a category:

| Tag | Category | Covers |
|:---|:---|:---|
| 🧠 | **Cognitive** | Biological memory concepts and the Spector subsystems modeled on them |
| 📐 | **Math & Indexing** | Quantization, vector indexes, and retrieval models |
| ⚙️ | **Systems** | Off-heap memory, durability, and distributed coordination |

---

## Alphabetical Index

| Letter | Terms |
|:---:|:---|
| **A** | [ACT-R](#act-r) · [Amygdala Valence](#amygdala-valence) · [Approximate Nearest Neighbor (ANN)](#approximate-nearest-neighbor-ann) |
| **B** | [Bloom Filter (Synaptic Tags)](#bloom-filter-synaptic-tags) · [BM25](#bm25) |
| **C** | [ColBERT v2](#colbert-v2) · [Consistent Hash Ring](#consistent-hash-ring) · [Consolidation](#consolidation) · [Cortex Memory Tiers](#cortex-memory-tiers) · [CSR (Compressed Sparse Row)](#csr-compressed-sparse-row) |
| **D** | [Default Mode Network (DMN)](#default-mode-network-dmn) · [Dopamine Surprise](#dopamine-surprise) |
| **E** | [Ebbinghaus Forgetting Curve](#ebbinghaus-forgetting-curve) · [Engram](#engram) |
| **F** | [Fast Walsh-Hadamard Transform (FWHT)](#fast-walsh-hadamard-transform-fwht) · [Fencing Token](#fencing-token) · [Flashbulb Memory](#flashbulb-memory) |
| **H** | [Habituation](#habituation) · [Hebbian Plasticity](#hebbian-plasticity) · [High-Water Mark (HWM)](#high-water-mark-hwm) · [HNSW](#hnsw) |
| **I** | [ICNU Importance Fusion](#icnu-importance-fusion) · [Inhibition (Suppression)](#inhibition-suppression) · [Interference (Deduplication)](#interference-deduplication) · [IVF (Inverted File Index)](#ivf-inverted-file-index) |
| **L** | [Long-Term Potentiation (LTP)](#long-term-potentiation-ltp) |
| **M** | [MaxSim](#maxsim) · [Model Context Protocol (MCP)](#model-context-protocol-mcp) |
| **O** | [Off-Heap MemorySegment](#off-heap-memorysegment) |
| **P** | [Panama FFM](#panama-ffm) · [Permastore](#permastore) |
| **Q** | [Quantization](#quantization) |
| **S** | [Salience](#salience) · [SIMD](#simd) · [Single-Writer Invariant](#single-writer-invariant) · [SPLADE](#splade) · [Spreading Activation](#spreading-activation) · [SVASQ](#svasq) |
| **T** | [Truncation Trap](#truncation-trap) · [TurboQuant](#turboquant) |
| **W** | [Write-Ahead Log (WAL)](#write-ahead-log-wal) |
| **Z** | [Zero-GC Arena](#zero-gc-arena) |

---

## A

### ACT-R { #act-r }

🧠 **Cognitive**

**Adaptive Control of Thought—Rational** is a cognitive architecture from psychology that predicts how likely a memory is to be retrieved based on how often and how recently it was used, plus how strongly it relates to the current context.

**In Spector:** The recall scoring formula is a simplified form of the ACT-R activation equation, combining similarity, importance, and temporal decay into one score.

**See also:** [Theoretical Foundations](memory/theoretical-foundations.md) · [The 6-Phase Scoring Pipeline](memory/scoring-pipeline.md)

---

### Amygdala Valence { #amygdala-valence }

🧠 **Cognitive**

The **amygdala** is the part of the brain that attaches emotional significance to experiences. **Valence** is the direction of that emotion: positive (joy, relief) or negative (fear, frustration). Emotionally charged memories tend to be encoded more strongly and remembered longer.

**In Spector:** Every memory carries a single-byte valence score from `-128` (strongly negative, e.g. data loss) to `+127` (strongly positive, e.g. a successful launch). Agents can recall by mood or outcome, and persona settings can bias how valence is assigned.

**See also:** [Amygdala — Emotional Valence](memory/amygdala.md) · [Salience & Persona Profiles](memory/salience-importance.md)

---

### Approximate Nearest Neighbor (ANN) { #approximate-nearest-neighbor-ann }

📐 **Math & Indexing**

A family of search algorithms that find vectors *close to* a query vector without comparing against every stored vector. They trade a small amount of accuracy (recall) for very large speedups.

**In Spector:** [HNSW](#hnsw) and the IVF-HNSW-SVASQ **SpectorIndex** are ANN structures.

**See also:** [ANN Search Primer](deep-dives/ann-search-primer.md)

---

## B

### Bloom Filter (Synaptic Tags) { #bloom-filter-synaptic-tags }

📐 **Math & Indexing**

A **Bloom filter** is a compact bit array that answers "is this item possibly in the set?" It can return false positives but never false negatives, which makes it a very cheap pre-filter.

**In Spector:** Each memory's tags are hashed into a 128-bit Bloom filter stored in its header. Tag matching is two 64-bit bitwise `AND` operations, so non-matching memories are rejected in under a nanosecond before any vector math runs.

**See also:** [Synapse — Tags & Scoring](memory/synapse.md)

---

### BM25 { #bm25 }

📐 **Math & Indexing**

**Okapi BM25** ("Best Matching 25") is the classic keyword-ranking formula used by search engines. It scores a document higher when it contains rare query terms many times, while dampening the effect of repeated terms and very long documents.

**In Spector:** BM25 is the lexical layer of the retrieval stack. It catches exact matches that dense embeddings often miss, such as error codes, variable names, and UUIDs.

**See also:** [Keyword Retrieval (BM25)](memory/bm25.md) · [Retrieval Stack Overview](memory/retrieval-overview.md)

---

## C

### ColBERT v2 { #colbert-v2 }

📐 **Math & Indexing**

**ColBERT** ("Contextualized Late Interaction over BERT") is a retrieval model that produces **one vector per token** instead of one vector per document. A query and document are compared token-by-token *after* both are encoded ("late interaction"), using the [MaxSim](#maxsim) operator. Version 2 adds residual compression to keep the token vectors small.

**In Spector:** ColBERT v2 is an optional high-precision **reranking** stage. The MaxSim kernel is SIMD-vectorized and token embeddings are cached off-heap, avoiding the latency of a cross-encoder reranker.

**See also:** [Late-Interaction Reranking (ColBERT v2)](memory/colbert.md)

---

### Consistent Hash Ring { #consistent-hash-ring }

⚙️ **Systems**

A way to assign keys to servers so that adding or removing a server only moves a small fraction of keys. Servers and keys are hashed onto the same circular number space, and each key belongs to the next server clockwise on the ring.

**In Spector:** Multi-node **Cell High Availability** uses a **Ketama** consistent hash ring to decide which node *owns* (is the single writer for) each namespace. Non-owner nodes refuse writes and recall with HTTP `421 Misdirected Request`, naming the correct owner. Explicit failover override leases take precedence over the ring.

**See also:** [Engine & Algorithmic Tuning — Cell HA](configuration/parameters.md#cell-high-availability-ownership-ring-spectorcell) · [Fencing Token](#fencing-token)

---

### Consolidation { #consolidation }

🧠 **Cognitive**

During sleep, the brain's **hippocampus** replays recent experiences and gradually transfers them into long-term, generalized knowledge. Unused connections are pruned at the same time.

**In Spector:** A background consolidation daemon clusters episodic memories (K-Means) and promotes the stable patterns into the Semantic tier, compacts tombstoned records, and decays [Hebbian](#hebbian-plasticity) edges.

**See also:** [Hippocampus — Sleep Consolidation](memory/hippocampus.md) · [Generative Dreaming](memory/dreaming.md)

---

### Cortex Memory Tiers { #cortex-memory-tiers }

🧠 **Cognitive**

The idea that different kinds of memory have different lifetimes and purposes, for example short-lived working memory versus long-lived factual knowledge.

**In Spector:** Memories live in one of four tiers: **Working** (current context), **Episodic** (events), **Semantic** (consolidated facts), and **Procedural** (learned rules and how-tos). Each tier has its own retention and scoring behavior.

**See also:** [Cortex — 4-Tier Memory](memory/cortex.md)

---

### CSR (Compressed Sparse Row) { #csr-compressed-sparse-row }

📐 **Math & Indexing**

A compact format for storing sparse graphs or matrices. Instead of reserving a fixed number of slots per node, it stores all edges in one flat array plus an offset array marking where each node's edges begin.

**In Spector:** The Hebbian association graph is stored in CSR form, using roughly 90% less memory than a fixed-width layout.

**See also:** [4-Layer Cognitive Graph](memory/hebbian.md)

---

## D

### Default Mode Network (DMN) { #default-mode-network-dmn }

🧠 **Cognitive**

A set of brain regions that becomes active when a person is *not* focused on an external task: resting, daydreaming, or letting the mind wander. It is associated with drawing on deep, long-term knowledge and making unexpected connections ("shower thoughts").

**In Spector:** The name appears in two places:

- **`DEFAULT_MODE_NETWORK` cognitive profile.** Searches only the Semantic and Procedural tiers and weights importance far above similarity (α = 0.2, β = 0.8), surfacing what the agent "knows deeply" about a topic.
- **DMN wander pathway (AISME).** A scheduled background job performs spontaneous mind-wandering, synthesizing new associative edges between memories while the agent is idle. It is controlled by `spector.memory.aisme.dmn.enabled`.

**See also:** [Cognitive Profiles](memory/cognitive-profiles.md) · [Autonomous Identity & AISME](memory/aisme.md) · [Generative Dreaming](memory/dreaming.md)

---

### Dopamine Surprise { #dopamine-surprise }

🧠 **Cognitive**

In the brain, **dopamine** signals *prediction error*: the gap between what was expected and what happened. Surprising events trigger stronger memory encoding, which is why unexpected moments are remembered vividly.

**In Spector:** A surprise detector measures each new memory's L2 distance to its nearest existing memory or centroid, and keeps a running mean and variance of those distances (Welford's online algorithm). The distance is converted to a z-score against that distribution; the more of an outlier it is, the higher its initial importance. Extreme outliers become [flashbulb memories](#flashbulb-memory).

**See also:** [Dopamine — Surprise Detection](memory/dopamine.md) · [Salience & Importance](memory/salience-importance.md)

---

## E

### Ebbinghaus Forgetting Curve { #ebbinghaus-forgetting-curve }

🧠 **Cognitive**

In 1885 Hermann Ebbinghaus measured how quickly people forget, showing that retention drops steeply at first and then levels off. Later research found that a **power law** fits real forgetting data better than exponential models, because old memories fade very slowly.

**In Spector:** Temporal decay follows a power-law curve, `R(t) = a · t^-d` (default `d = 0.15`, floor `0.10`), precomputed into a 12-bucket lookup table so the decay cost at scoring time is a single array read. `DecayProperties` provides `SLOW_FORGET` (`d = 0.08`) and `FAST_FORGET` (`d = 0.30`) presets alongside the defaults.

**See also:** [Theoretical Foundations — Power Law of Forgetting](memory/theoretical-foundations.md#power-law-of-forgetting) · [Long-Term Potentiation (LTP)](#long-term-potentiation-ltp) · [Permastore](#permastore)

---

### Engram { #engram }

🧠 **Cognitive**

In neuroscience, an **engram** is the physical trace a memory leaves in the brain: the set of neurons and connections that together store one experience.

**In Spector:** An engram is a single stored memory record. Its layout is a 64-byte, cache-line-aligned header (timestamp, importance, valence, arousal, flags, 128-bit Bloom-filtered tags) followed by the quantized vector payload, all stored off-heap. The header is defined by `EncodingHeaderLayout` and the full record by `EngramLayout`. Recall counts and strength live in a separate strength region, and the decay bucket is computed at scoring time from the timestamp.

**See also:** [Off-Heap Panama Design](memory/panama-design.md) · [Binary Layouts & Tags](kernel/layouts.md) · [Core Concepts](architecture/core-concepts.md)

---

## F

### Fast Walsh-Hadamard Transform (FWHT) { #fast-walsh-hadamard-transform-fwht }

📐 **Math & Indexing**

An orthogonal transform, similar in spirit to the Fourier transform but using only `+1` and `-1` coefficients. It can be computed in place with only additions and subtractions in O(n log n) time. Because it is orthogonal, it **preserves distances** between vectors.

**In Spector:** FWHT is the "rotate first" step of [SVASQ](#svasq). Real embeddings often have a few outlier dimensions with huge ranges; FWHT spreads that energy evenly across all dimensions, so INT8 quantization loses far less precision. Vectors are zero-padded to the next power of two before the transform.

**See also:** [SVASQ Quantization](deep-dives/svasq-deep-dive.md) · [SpectorIndex Architecture](deep-dives/spector-index-architecture.md)

---

### Fencing Token { #fencing-token }

⚙️ **Systems**

A number that increases every time ownership of a resource changes hands. Each write must carry the current token, and the storage layer rejects writes with an older token. This stops a "zombie" node, for example one that was partitioned away and has since been replaced, from corrupting data.

**In Spector:** Every write carries the namespace's monotonic fence token in the `X-Spector-Fence` header. Writes to a stale or superseded owner are rejected with HTTP `409 Conflict` (`FENCED`). The check is in-memory and allocation-free.

**See also:** [Engine & Algorithmic Tuning — Failover & Fencing](configuration/parameters.md#cell-failover-fencing-coordination-spectorfailover-spectorcoordinator-spectorcontrol-store) · [Single-Writer Invariant](#single-writer-invariant) · [Consistent Hash Ring](#consistent-hash-ring)

---

### Flashbulb Memory { #flashbulb-memory }

🧠 **Cognitive**

An unusually vivid, long-lasting memory of a surprising or emotionally significant event.

**In Spector:** When a memory's [surprise](#dopamine-surprise) z-score exceeds the flashbulb threshold (default `3.0`), it is pinned at maximum importance so it keeps surfacing in relevant future recalls.

**See also:** [Salience & Importance](memory/salience-importance.md) · [Dopamine — Surprise Detection](memory/dopamine.md)

---

## H

### Habituation { #habituation }

🧠 **Cognitive**

The simplest form of learning: a response weakens when the same stimulus repeats. You stop noticing a ticking clock after a few minutes, which frees attention for new things.

**In Spector:** Memories that are returned over and over within a session have their recall scores gradually reduced. This prevents a "filter bubble" where the same top result dominates every query, and lets slightly less similar but useful memories surface.

**See also:** [Habituation — Anti-Filter Bubble](memory/habituation.md)

---

### Hebbian Plasticity { #hebbian-plasticity }

🧠 **Cognitive**

Donald Hebb's 1949 principle, often summarized as *"neurons that fire together, wire together."* When two neurons are active at the same time, the connection between them strengthens.

**In Spector:** When a memory is ingested, the weighted association edge between it and the memory ingested just before it is strengthened (explicit edge hints can add further edges). At recall time, a spreading-activation walk (default depth 3) from the top results pulls in associated memories that vector similarity alone would miss. Edges decay by 0.9× per reflection cycle, and each memory keeps up to 24 neighbors by default.

**See also:** [4-Layer Cognitive Graph](memory/hebbian.md) · [Spreading Activation](#spreading-activation) · [CSR](#csr-compressed-sparse-row)

---

### High-Water Mark (HWM) { #high-water-mark-hwm }

⚙️ **Systems**

The highest sequence number known to be safely applied or persisted. Anything at or below the mark is durable; anything above it may still be in flight.

**In Spector:** WAL replication ships events after a follower's HWM, and chunks below a snapshot HWM can be compacted. Replicas advance their HWM *last*, after verifying and applying data, so a crash mid-transfer never exposes partial state.

**See also:** [WAL Design](memory/wal-design.md) · [Write-Ahead Log (WAL)](#write-ahead-log-wal)

---

### HNSW { #hnsw }

📐 **Math & Indexing**

**Hierarchical Navigable Small World** is a graph-based [ANN](#approximate-nearest-neighbor-ann) index. Vectors are nodes in a multi-layer graph: sparse upper layers act like highways for long jumps, and the dense bottom layer supports fine-grained search. Its main tuning knobs are `M` (max connections per node), `efConstruction` (build quality), and `efSearch` (query quality).

**In Spector:** HNSW is available as a standalone index and is also used inside the adaptive shards of **SpectorIndex**.

**See also:** [HNSW Explained](deep-dives/hnsw-explained.md) · [SpectorIndex Architecture](deep-dives/spector-index-architecture.md)

---

## I

### ICNU Importance Fusion { #icnu-importance-fusion }

🧠 **Cognitive**

**Interest, Challenge, Novelty, Urgency.** Four signals that together describe why something is worth remembering.

**In Spector:** At ingestion, ICNU blends these four scores, then applies [salience](#salience) profile topic boosts and persona modulation to produce a single importance value. This covers cases the surprise detector misses, such as an urgent but unsurprising deadline.

**See also:** [Importance Fusion (ICNU)](memory/importance-fusion.md)

---

### Inhibition (Suppression) { #inhibition-suppression }

🧠 **Cognitive**

The brain actively suppresses competing memories during recall (retrieval-induced forgetting). Recalling where you parked *today* inhibits memories of where you parked *yesterday*.

**In Spector:** Suppression is an explicit, **reversible** block that hides a memory from recall without deleting it. It differs from `forget`, which permanently tombstones the record.

**See also:** [Inhibition — Suppression](memory/inhibition.md)

---

### Interference (Deduplication) { #interference-deduplication }

🧠 **Cognitive**

**Proactive interference** happens when an old memory gets in the way of a newer, similar one, such as recalling an old address when you want the new one.

**In Spector:** Near-duplicate memories are detected and merged during consolidation, so redundant copies don't crowd out recall results. At ingestion, a memory whose ID is already indexed is skipped.

**See also:** [Interference — Deduplication](memory/interference.md)

---

### IVF (Inverted File Index) { #ivf-inverted-file-index }

📐 **Math & Indexing**

A vector index that first clusters vectors around centroids. A query is compared only against the vectors in the few clusters closest to it, instead of against the whole collection.

**In Spector:** IVF is the coarse partitioning layer of SpectorIndex. Each vector's residual (vector minus its centroid) is then rotated with [FWHT](#fast-walsh-hadamard-transform-fwht) and quantized with [SVASQ](#svasq).

**See also:** [SpectorIndex Architecture](deep-dives/spector-index-architecture.md)

---

## L

### Long-Term Potentiation (LTP) { #long-term-potentiation-ltp }

🧠 **Cognitive**

A lasting increase in the strength of a synapse after repeated activation. LTP is widely considered the cellular basis of learning: the more a pathway is used, the easier it becomes to activate.

**In Spector:** Each explicit `memory.reinforce(id)` call raises the memory's recall count, and its decay-bucket index is shifted right by one bit per reinforcement (up to 5). The memory therefore decays as if it were much younger, so critical knowledge resists the [forgetting curve](#ebbinghaus-forgetting-curve). Separately, passive **Auto-LTP** on recall adds a small storage-strength increment, rate-limited by `spector.memory.strength.auto-ltp-cooldown-ms`.

**See also:** [Synapse — Tags & Scoring](memory/synapse.md) · [The 6-Phase Scoring Pipeline](memory/scoring-pipeline.md) · [REST API & Runtime Parameters](configuration/api-parameters.md)

---

## M

### MaxSim { #maxsim }

📐 **Math & Indexing**

The scoring operator used by [ColBERT](#colbert-v2). For each query token, it finds the most similar document token, then sums those maximum similarities across all query tokens.

**In Spector:** MaxSim runs as a vectorized SIMD kernel over cached, off-heap token embeddings.

**See also:** [Late-Interaction Reranking (ColBERT v2)](memory/colbert.md)

---

### Model Context Protocol (MCP) { #model-context-protocol-mcp }

⚙️ **Systems**

An open, JSON-RPC-based standard for connecting AI agents to external tools and data sources.

**In Spector:** Spector ships a built-in MCP server exposing its memory operations as agent tools. It can run in-process, so tool calls go straight to the memory kernel without a network hop.

**See also:** [MCP Integration](architecture/mcp-integration.md) · [MCP Server](sdk-usage/mcp-server.md)

---

## O

### Off-Heap MemorySegment { #off-heap-memorysegment }

⚙️ **Systems**

`java.lang.foreign.MemorySegment` is the Java API for a contiguous block of memory that lives **outside** the JVM heap. The garbage collector does not scan or move it, and there are no per-object headers.

**In Spector:** Every engram header and vector payload is stored in `MemorySegment` buffers, often memory-mapped directly from bundle files. Fields are read and written at fixed byte offsets with no deserialization and no Java objects created on the hot path.

**See also:** [Off-Heap Panama Design](memory/panama-design.md) · [Memory Kernel Overview](kernel/index.md) · [Panama FFM](#panama-ffm) · [Zero-GC Arena](#zero-gc-arena)

---

## P

### Panama FFM { #panama-ffm }

⚙️ **Systems**

**Project Panama's Foreign Function & Memory API** (`java.lang.foreign`) lets Java code safely allocate and access native memory and call native libraries without JNI. It provides [`MemorySegment`](#off-heap-memorysegment), [`Arena`](#zero-gc-arena), and memory layouts.

**In Spector:** The Memory Kernel is built on Panama FFM, which is what makes the storage layer zero-GC. Spector's launch scripts and Docker images pass `--enable-native-access=ALL-UNNAMED` so restricted FFM calls run without JVM warnings.

**See also:** [Off-Heap Panama Design](memory/panama-design.md) · [JDK API Status](getting-started/jdk-api-status.md) · [Environment Variables & Secrets](configuration/environment-variables.md)

---

### Permastore { #permastore }

🧠 **Cognitive**

Harry Bahrick's finding that some very old memories, such as a language learned decades ago, stabilize and stop fading. The forgetting curve flattens out after years.

**In Spector:** The decay table has a floor (`0.10` by default) in its oldest bucket, so long-lived memories never decay to zero.

**See also:** [Theoretical Foundations](memory/theoretical-foundations.md) · [Ebbinghaus Forgetting Curve](#ebbinghaus-forgetting-curve)

---

## Q

### Quantization { #quantization }

📐 **Math & Indexing**

Compressing vectors by storing each coordinate with fewer bits, for example 8-bit integers instead of 32-bit floats. It trades a little precision for large savings in memory and bandwidth.

**In Spector:** The main schemes are [SVASQ](#svasq) (INT8/INT4 with FWHT rotation) and [TurboQuant](#turboquant) (4-bit with random rotation).

**See also:** [Understanding Quantization](deep-dives/understanding-quantization.md) · [Quantization Comparison](deep-dives/quantization-comparison.md)

---

## S

### Salience { #salience }

🧠 **Cognitive**

How much something stands out and deserves attention. Salient information is more likely to be noticed, encoded, and remembered.

**In Spector:** Salience shapes each memory's **importance** score (0.05–10.0), the most influential signal in recall ranking. It combines automatic [surprise detection](#dopamine-surprise) with **salience profiles**, where users declare interests, disinterests, and a persona. Profiles merge hierarchically (tenant → agent → user), and existing memories can be re-scored when preferences change.

**See also:** [Salience & Persona Profiles](memory/salience-importance.md) · [Salience & Importance Architecture](architecture/salience-importance.md) · [ICNU Importance Fusion](#icnu-importance-fusion)

---

### SIMD { #simd }

⚙️ **Systems**

**Single Instruction, Multiple Data.** A CPU feature (such as AVX2 or AVX-512) that applies one operation to many numbers at once, which greatly speeds up vector math like distance calculations.

**In Spector:** Recall scoring, distance kernels, BM25, and ColBERT MaxSim all use the Java Vector API to run as SIMD loops over off-heap data.

**See also:** [Performance & SIMD](memory/performance.md) · [Performance Tuning](operations/performance-tuning.md)

---

### Single-Writer Invariant { #single-writer-invariant }

⚙️ **Systems**

The guarantee that at any moment, exactly one process is allowed to modify a given piece of data. This avoids conflicting writes and split-brain corruption.

**In Spector:** Each namespace has one authoritative owner node, chosen by the [consistent hash ring](#consistent-hash-ring) or a failover override lease and enforced with [fencing tokens](#fencing-token). Replicas never accept writes. The guarantee holds even if the Redis routing cache is unavailable.

**See also:** [Engine & Algorithmic Tuning — Cell HA](configuration/parameters.md#cell-high-availability-ownership-ring-spectorcell)

---

### SPLADE { #splade }

📐 **Math & Indexing**

**Sparse Lexical and Expansion Model.** A neural retrieval model that encodes text as a sparse, vocabulary-sized vector. Unlike [BM25](#bm25), it also adds weight to related terms that don't literally appear in the text (**term expansion**). The result keeps the speed of an inverted index while matching synonyms.

**In Spector:** SPLADE and the faster, inference-free **Li-LSR** variant form the learned sparse layer of the retrieval stack.

**See also:** [Learned Sparse Search (SPLADE)](memory/splade.md) · [Retrieval Stack Overview](memory/retrieval-overview.md)

---

### Spreading Activation { #spreading-activation }

🧠 **Cognitive**

A model of associative memory: activating one concept partially activates its neighbors, and that activation fades with each step away.

**In Spector:** After scoring, a spreading-activation walk through the [Hebbian](#hebbian-plasticity) graph (default depth 3, weight multiplied by 0.7 per hop, stopping below 0.1) adds associated memories to the results with an attenuated score.

**See also:** [4-Layer Cognitive Graph](memory/hebbian.md) · [Explorer — Lateral Retrieval](memory/lateral-retrieval.md)

---

### SVASQ { #svasq }

📐 **Math & Indexing**

<!-- TODO(maintainers): confirm the canonical expansion of SVASQ. docs/deep-dives/svasq-deep-dive.md uses "Spector Vector-Aligned Scalar Quantization", while docs/about.md and other pages use "Vectorized Affine Scalar Quantization". -->
**SVASQ** is Spector's own quantization scheme: rotate the vector with the [Fast Walsh-Hadamard Transform](#fast-walsh-hadamard-transform-fwht) so every dimension has similar variance, then apply per-dimension affine INT8 (or INT4) quantization. Because the rotation removes outlier dimensions, INT8 SVASQ loses far less precision than standard INT8 quantization (the SVASQ deep dive describes it as rivaling INT12–INT16).

**In Spector:** SVASQ-8 gives about 4× compression with roughly 97–99.5% recall. SVASQ-4 gives 6–8× compression, with rescoring recommended. It quantizes IVF residuals inside SpectorIndex.

**See also:** [SVASQ Quantization](deep-dives/svasq-deep-dive.md) · [Whitepaper: SVASQ + SpectorIndex](deep-dives/svasq-spectorindex-whitepaper.md) · [Quantization Comparison](deep-dives/quantization-comparison.md)

---

## T

### Truncation Trap { #truncation-trap }

📐 **Math & Indexing**

A failure mode of "retrieve top-K by similarity, then filter or re-rank." An important memory that is slightly less similar than K recent, trivial ones is cut off at the first step, before recency, importance, or tags are ever considered.

**In Spector:** Similarity, decay, importance, and tag filters are evaluated together in a single off-heap pass, so the important memory can still win.

**See also:** [FAQ](faq.md) · [Why Spector?](why-spector.md)

---

### TurboQuant { #turboquant }

📐 **Math & Indexing**

A quantization scheme that multiplies each vector by a fixed **random orthogonal matrix** and then quantizes each coordinate to a small number of bits. The random rotation makes coordinates nearly independent, so simple per-coordinate quantization becomes close to optimal for any data distribution without heavy training.

**In Spector:** TurboQuant supports 2, 4, or 8 bits per coordinate. At 4 bits (nibble-packed) it gives about 8× compression with roughly 97%+ recall. Compared with [SVASQ](#svasq), it uses a random dense rotation instead of the structured FWHT.

**See also:** [TurboQuant](deep-dives/turbo-quant.md) · [Quantization Comparison](deep-dives/quantization-comparison.md)

---

## W

### Write-Ahead Log (WAL) { #write-ahead-log-wal }

⚙️ **Systems**

An append-only file that records every change *before* it is applied to the main data structures. After a crash, replaying the log rebuilds the exact state.

**In Spector:** Off-heap memory state (importance, valence, recall counts, tags) is made durable through a chunked binary WAL. Records have monotonic sequence numbers and dual CRC-32 checksums. The same log drives crash recovery, compaction below a snapshot [HWM](#high-water-mark-hwm), and pull-based replication.

**See also:** [WAL Design](memory/wal-design.md) · [WAL & Durability](kernel/wal-recovery.md) · [Sync — Persistence & Replication](memory/sync.md)

---

## Z

### Zero-GC Arena { #zero-gc-arena }

⚙️ **Systems**

In Panama FFM, an `Arena` controls the lifetime of off-heap memory: every `MemorySegment` allocated from it is freed at once when the arena is closed. Because the garbage collector never manages this memory, off-heap data adds **no GC pressure**, however many records are stored (the JVM still collects its ordinary heap objects). "Zero-GC Arena" is a descriptive term for this use of `Arena`, not a separate API.

**In Spector:** Most kernel allocations use **shared arenas** (`Arena.ofShared()`), which support concurrent access from virtual threads; confined arenas (`Arena.ofConfined()`) are also used for single-thread scopes. Arenas must be closed explicitly: `SpectorMemory` implements `AutoCloseable` and releases all arenas in `close()`, so use try-with-resources.

**See also:** [Off-Heap Panama Design — Arena Lifecycle](memory/panama-design.md#arena-lifecycle) · [Memory Kernel Overview](kernel/index.md) · [Off-Heap MemorySegment](#off-heap-memorysegment)
