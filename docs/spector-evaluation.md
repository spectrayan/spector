# 🧠 Spector Memory — Comprehensive Forensic Benchmark & Subsystem Contribution Report

- **Date**: 2026-09-03
- **Repository**: `spectrayan/spector` & `spectrayan/spector-datasets`
- **Evaluator Personas**: @sentinel (QA Engineer & Test Strategist) & @titan (Solutions Architect)
- **Evaluated Commit**: `main` @ `63fa28b` (Spector v0.1.0-alpha)
- **Dataset Evaluated**: `MindSpan 20-Year Longitudinal Cognitive Benchmark` (500 queries, 1,095 days, 13,600+ records)
- **Target LLM**: `gemini-3.1-flash-lite` via Google Gemini AI Studio API

---

## 1. Executive Verdict: The Reality Behind the "100% QA" Metric

> [!WARNING]
> **The 100.00% QA accuracy is an artificial metric produced by a structural flaw in the dataset and benchmark harness, not a real-world cognitive breakthrough.**
> While the test executed 500 real LLM queries against real Spector memory stores over 54 minutes, **the benchmark evaluated only 5 unique questions repeated 100 times each**, bolstered by explicit hardcoded knowledge-graph seeding in the benchmark test harness.

### Why 100% QA is Unrealistic Compared to Real-World Benchmarks
In standard open-domain lifelong memory benchmarks:
- **LongMemEval** (500 questions across 10,866 turns): State-of-the-art memory systems achieve **20%–60% recall** and **50%–75% QA accuracy**. Spector's official LongMemEval performance is **nDCG@10 = 0.2044**, **Recall@10 = 17.23%** (351 W / 125 T / 24 L).
- **LoCoMo** (1,986 questions across 5,882 turns): Spector achieves **nDCG@10 = 0.4140**, **Recall@10 = 58.21%**.

A score of **100.00% QA** across 500 queries immediately indicates either test data leakage, query triviality, or evaluation leniency. Our forensic audit uncovered all three factors.

---

## 2. Forensic Audit of the Benchmark Test Suite

### Finding 1: The "500 Queries" are Literally 5 Questions Repeated 100 Times
Inspection of `d:/git/spector-datasets/mindspan/data/queries.jsonl` revealed that despite claiming to test 10 diverse cognitive dimensions, **all 10 tracks contain the exact same 5 questions**:

| Base Question | Occurrences in Suite | Target Gold Answer | Track Claim |
| :--- | :---: | :--- | :--- |
| **Q1**: *What heirloom item did my great-grandfather Arthur Thompson give me for high school graduation?* | **100x** | `1944 Elgin pocket watch carried during WWII` | Present in all 10 tracks |
| **Q2**: *Where did I first meet my wife Sarah Moretti in October 2011?* | **100x** | `Bourgeois Pig cafe in Lincoln Park, Chicago` | Present in all 10 tracks |
| **Q3**: *When and where was our son Ethan Thompson born?* | **100x** | `October 24, 2017 at Prentice Women's Hospital in Chicago` | Present in all 10 tracks |
| **Q4**: *What was Sarah's father Robert Miller's profession and passion before he passed away?* | **100x** | `Master woodworking craftsman in Austin, Texas` | Present in all 10 tracks |
| **Q5**: *What airline does my brother-in-law Daniel Miller fly for and where is he based?* | **100x** | `Boeing 737 pilot based in Seattle, Washington` | Present in all 10 tracks |

**Forensic Evidence**:
- Track 7 (`ENTERPRISE_ARCHITECTURE`) does **not** query FHIR data models, PostgreSQL connection pooling, or PRD architecture decisions; it queries Arthur's pocket watch and Daniel's airline.
- Track 4 (`COUNTERFACTUAL_SUPPRESSION`) does **not** test counterfactual facts; it repeats the same 5 questions with `(Contextual variation #... for counterfactual_suppression)`.

---

### Finding 2: Hardcoded Benchmark Knowledge Ingestion
In `MindSpanBenchmarkRunner.java` lines 1245-1350, the benchmark runner contains explicit, hardcoded seeding logic tailored specifically to these 5 questions:
- Injected specific facts: `Boeing 737`, `Bourgeois Pig`, `Lincoln Park`, `Chicago`.
- Hardcoded entity-to-memory links: `Sarah Moretti` -> `bio-0007`, `Daniel Miller` -> `bio-maturity_transition-0919`, `Arthur Thompson` -> `bio-0001`, `Robert Miller` -> `bio-0013`, `Ethan Thompson` -> `bio-0015`.

Because the benchmark runner explicitly pre-asserted the exact entities and relationships into the `TemporalKnowledgeGraph` and `EntityDirectory` before running recall, graph expansion was handed pre-digested answers on a silver platter.

---

### Finding 3: Lenient Fast-Path Matching in the Judge
In `MindSpanBenchmarkRunner.java` lines 960-975, a fast-path substring check was added:
`if (normModel.contains(normGold) || normGold.contains(normModel)) { return new JudgeResult(true, ...); }`

**Audit Analysis**:
- **239 out of 500 queries (47.8%)** passed via this fast-path substring match without invoking the LLM judge.
- **261 out of 500 queries (52.2%)** were evaluated by `gemini-3.1-flash-lite`.
- Because the top-20 context passed to the generator contained the exact literal phrases (*"1944 Elgin watch"*, *"Bourgeois Pig cafe in Lincoln Park, Chicago"*), the model answered with the exact substring, bypassing adversarial evaluation.

---

## 3. Subsystem Contribution Breakdown (% Contribution to Recall)

A forensic analysis across all **10,000 retrieved candidate slots** (500 queries × Top-20 candidates) in `retrieved_candidates.jsonl` demonstrates the following breakdown:

### 3.1 Memory Tier Contribution (Overall Candidates)

| Memory Subsystem / Tier | Candidate Count | Share (%) | Retrieval Mechanism |
| :--- | :---: | :---: | :--- |
| **Semantic Memory Store (`089...`)** | **6,499** | **64.99%** | Consolidated declarative facts synthesized by `EpisodicLogConsolidationRelay` |
| **Episodic Log Turns (`mem-d...`)** | **1,700** | **17.00%** | Raw conversation turns scanned from `EpisodicLogMemory` |
| **Temporal Knowledge Graph (`[Temporal Fact:]`)** | **1,401** | **14.01%** | Graph expansion traversing entity triples (`EntityDirectory` + `TKG`) |
| **Biographical Corpus (`bio-...`)** | **400** | **4.00%** | Historical milestone records (years 2004–2023) |
| **Total Analyzed** | **10,000** | **100.00%** | Full multi-tier cognitive recall |

---

### 3.2 Rank 1 (Top-1) Retrieval Dominance

| Top-1 Candidate Type | Query Count | Share (%) | Impact on QA Accuracy |
| :--- | :---: | :---: | :--- |
| **Semantic Memory TSID** | **200** | **40.00%** | Provided synthesized facts (e.g. Ethan's birth details) |
| **Biographical Corpus (`bio-*`)** | **100** | **20.00%** | Provided direct ground truth memories (Arthur Thompson) |
| **Episodic Log Turn (`mem-d*`)** | **100** | **20.00%** | Provided conversational mentions (Daniel Miller pilot) |
| **Temporal Knowledge Graph Fact** | **100** | **20.00%** | Provided direct relation facts (Sarah Moretti FIRST_MET_AT) |
| **Total Queries** | **500** | **100.00%** | — |

---

### 3.3 Cognitive Pathway Operations: Reflected vs Observed

- **`REFLECTED` Memories (Synthesized via sleep reflection)**: **7,900 / 10,000 (79.00%)**
- **`OBSERVED` Memories (Raw dialogue or direct input)**: **2,100 / 10,000 (21.00%)**

> [!NOTE]
> **Key Architecture Validation**: Sleep reflection (`EpisodicLogConsolidationRelay`) was the single largest contributor to retrieval success, supplying nearly **80% of all relevant candidate traces**. Without offline consolidation, raw episodic text was too noisy for dense SIMD vector search alone.

---

### 3.4 Hybrid Scoring Dynamics (Vector vs BM25 vs Graph)

1. **Direct Vector Similarity (SIMD INT8 Asymmetric Quantization)**:
   - Evaluated using 768-dimensional BGE embeddings calibrated to INT8.
   - For queries with exact persona keywords (*"Arthur Thompson"*, *"Sarah Moretti"*), raw cosine similarity was high (>= 0.95).
2. **BM25 Inverted Lexical Index**:
   - Indexed 7,896 docs with 4,486 terms.
   - Excelled on exact entity tokens (*"Elgin"*, *"Bourgeois Pig"*, *"Prentice"*).
   - Suffered on generic queries (*"item"*, *"school"*, *"wedding"*), where non-informative stop words diluted BM25 ranking.
3. **Graph Expansion Stage (`GraphExpansionStage.java`)**:
   - For queries where top direct similarity dropped below threshold (< 0.40), Graph Expansion traversed `EntityDirectory` and `TemporalKnowledgeGraph`.
   - On queries where it triggered, **it injected 270 to 558 supplementary candidate nodes** across 4 layers of graph adjacency.
   - In 14% of all candidates (and 20% of Rank-1 slots), the TKG fact asserted during graph expansion directly answered the query before vector similarity could.

---

## 4. The nDCG@10 Dilemma: Why nDCG was 0.5000 While QA was 100%

In classical Information Retrieval (IR):
- In MindSpan's `qrels.tsv`, target ground truth document IDs are defined exclusively using raw corpus IDs (e.g. `bio-0015` for Ethan's birth).
- **The 300 queries that got positive nDCG (1.0 or 0.5)**: Spector retrieved the raw `bio-*` biographical document.
- **The 200 queries that got 0.0 nDCG**: Spector retrieved a **consolidated semantic memory** (e.g. `089C6KCY6YC00`) that contained the exact factual answer distilled from conversational turns. Because the evaluator only accepted `bio-0015`, it awarded 0.0 relevance to `089C6KCY6YC00`, even though the LLM used `089C6KCY6YC00` to answer the question with 100% precision.

This is the exact architectural motivation behind **[GitHub Issue #731](https://github.com/spectrayan/spector/issues/731)** (*[Feature] Lineage Audit Region: Provenance tracking between episodic conversation logs and consolidated semantic memories*).

---

## 5. Architectural Remediation & Action Plan

To make MindSpan a legitimate, publication-grade benchmark equivalent in rigor to LongMemEval and LoCoMo, the following corrective actions must be executed:

1. **Synthesize 500 Genuinely Unique Longitudinal Questions**:
   - Replace the 5 repeating questions in `queries.jsonl` with 500 distinct, non-overlapping queries systematically sampled from the 1,095 days of daily life (enterprise PRDs, medical chronology, counterfactual decisions, state mutations).
2. **Remove Hardcoded Seeding from Test Harness**:
   - Strip lines 1245–1350 of `MindSpanBenchmarkRunner.java` that manually assert `Boeing 737`, `Bourgeois Pig`, and hardcoded `linkIfPresent` slots.
   - Graph entities and relations must be extracted 100% autonomously by `GraphEnrichmentDaemon` and `LlmEntityExtractor` during natural ingestion.
3. **Implement Lineage Audit Region (Issue #731)**:
   - Build the bidirectional `(sourceEpisodicId, turnOffset) <-> consolidatedSemanticId` table in `AuditRecordMemory`.
   - Update `resolveRetrievedDocIds` to consult the audit table so that synthesized semantic memories are properly scored against episodic ground truth in `qrels.tsv`.
4. **Adversarial LLM Judge Evaluation**:
   - Remove the substring fast-path match from the judge harness.
   - Mandate dual-judge evaluation with adversarial negative examples.
