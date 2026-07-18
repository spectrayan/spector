# Implementation Plan: Cognitive Memory Benchmark

## Overview

This plan implements a comprehensive cognitive memory benchmark framework for Spector Memory. The system consists of dataset models, a parsing/validation layer, IR metrics computation, statistical tests, baseline and cognitive retrievers, the benchmark harness orchestrator, subsystem contribution detection, report generation, a dataset generation pipeline, CI integration, and comprehensive property-based and integration tests.

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Foundation — Dataset Models",
      "tasks": ["1"]
    },
    {
      "name": "Parsing and Test Data",
      "tasks": ["2", "3"],
      "dependsOn": ["1"]
    },
    {
      "name": "Core Metrics and Statistics",
      "tasks": ["4", "5"],
      "dependsOn": ["2"]
    },
    {
      "name": "Benchmark Infrastructure",
      "tasks": ["6", "7", "8", "9"],
      "dependsOn": ["3", "4", "5"]
    },
    {
      "name": "Orchestration and Detection",
      "tasks": ["10", "11"],
      "dependsOn": ["6", "7", "8", "9"]
    },
    {
      "name": "Generator and CI",
      "tasks": ["12", "13"],
      "dependsOn": ["2", "10"]
    },
    {
      "name": "Integration and Property Tests",
      "tasks": ["14"],
      "dependsOn": ["10", "11", "13"]
    }
  ]
}
```

## Tasks

- [x] 1. Dataset Model Records (POJOs)
  Create the Java record classes that represent the cognitive benchmark dataset schema in `com.spectrayan.spector.bench.cognitive.model`.
  - [x] 1.1 Create `BenchmarkCorpusRecord` record with all fields: id, text, title, synapticTags, valence (byte), importance (float), arousal (int 0-255), sessionId, timestampMs, entityMentions, memoryType, recallCount
  - [x] 1.2 Create `EntityMention` record with name and type fields
  - [x] 1.3 Create `BenchmarkQuery` record with all fields: id, text, cognitiveProfile, synapticFilterTags, minValence (nullable Byte), maxValence (nullable Byte), expectedSubsystem, temporalHint
  - [x] 1.4 Create `RelevanceJudgment` record with queryId, corpusId, and grade fields
  - [x] 1.5 Create `EntityRelation` record with fromEntity, toEntity, relationType, and sourceMemoryIds fields
  - [x] 1.6 Create `TemporalChainDef` record with sessionId and orderedMemoryIds fields
  - [x] 1.7 Create `HebbianEdgeDef` record with memoryIdA, memoryIdB, and coActivationCount fields
  - [x] 1.8 Create `PersonaDef` record with name, age, occupation, interests, lifeContext, personalityTraits, and companionRelationship fields
  - [x] 1.9 Create `BenchmarkExitCode` enum with SUCCESS(0), EFFECT_SIZE_INSUFFICIENT(1), NDCG_REGRESSION(2), DATASET_VALIDATION_FAILED(3), SETUP_FAILED(4), PARTIAL_EXECUTION(5)

- [x] 2. DatasetLoader — JSONL/TSV Parsing and Validation
  Implement the dataset loading and validation logic with referential integrity checks.
  - [x] 2.1 Create `DatasetLoader` class with `LoadedDataset` inner record aggregating all parsed data (corpus, queries, qrels, entityRelations, temporalChains, hebbianEdges, persona)
  - [x] 2.2 Implement `loadCorpus(Path)` — streaming JSONL parser for corpus.jsonl using Jackson, mapping JSON fields to `BenchmarkCorpusRecord`, clamping out-of-range values with logged warnings
  - [x] 2.3 Implement `loadQueries(Path)` — streaming JSONL parser for queries.jsonl, defaulting unknown CognitiveProfile names to BALANCED with logged warning
  - [x] 2.4 Implement `loadQrels(Path)` — TSV parser for qrels.tsv producing `Map<String, Map<String, Integer>>` (queryId → corpusId → grade)
  - [x] 2.5 Implement `loadEntities(Path)` — streaming JSONL parser for entities.jsonl, defaulting unknown RelationType to OTHER with warning
  - [x] 2.6 Implement `loadTemporalChains(Path)` and `loadHebbianEdges(Path)` JSONL parsers
  - [x] 2.7 Implement `loadPersona(Path)` — JSON parser for persona.json with schema validation (age 18-100, interests 3-20 items, etc.)
  - [x] 2.8 Implement `load(Path datasetDir)` orchestrator that calls all individual loaders and runs referential integrity validation
  - [x] 2.9 Implement referential integrity validator: verify all qrels query_ids exist in queries, all qrels corpus_ids exist in corpus, temporal chain memory_ids exist in corpus in ascending timestamp order, hebbian edge memory_ids exist in corpus (skip edges with missing IDs per Req 5.5), entity relation source_memory_ids exist in corpus
  - [x] 2.10 Create `DatasetValidationException` and `DatasetParseException` exception classes with descriptive error messages listing all violations

- [x] 3. Mini-Dataset for Testing
  Create a small hand-crafted 50-memory, 20-query dataset for fast CI testing without embedding service dependency.
  - [x] 3.1 Create directory `spector-bench/src/test/resources/cognitive-benchmark-mini/` and write `persona.json` for the test persona (Jordan Chen, senior software engineer)
  - [x] 3.2 Write `corpus.jsonl` with 50 hand-crafted memories spanning 8 scenario categories: work/debugging (10), personal life (10), health/fitness (5), biographical/childhood (8), knowledge facts (7), emotional events (5), daily routine (5) — each with realistic cognitive annotations
  - [x] 3.3 Write `queries.jsonl` with 20 queries covering: vector-only (4), tag-gating (3), valence-filtered (3), importance-dependent (3), Hebbian-traversal (3), temporal-chain (2), entity-graph (2) — each tagged with expected_subsystem
  - [x] 3.4 Write `qrels.tsv` with graded relevance judgments (0-3) for all query-corpus pairs, ensuring at least 5 judgments per query
  - [x] 3.5 Write `entities.jsonl` with at least 15 entity relations connecting corpus memories
  - [x] 3.6 Write `temporal_chains.jsonl` defining at least 5 session chains linking memories in timestamp order
  - [x] 3.7 Write `hebbian_edges.jsonl` defining at least 10 co-activation edges between thematically related memories

- [x] 4. MetricsComputer — IR Evaluation Metrics
  Implement nDCG@K, MRR@K, and Recall@K per the design document formulas.
  - [x] 4.1 Create `MetricsComputer` class with `ndcgAtK(List<String> rankedIds, Map<String, Integer> qrels, int k)` implementing DCG/IDCG: DCG = Σ (2^rel - 1) / log2(i+2), nDCG = DCG/IDCG
  - [x] 4.2 Implement `mrrAtK(List<String> rankedIds, Map<String, Integer> qrels, int k)` — reciprocal rank of first result with grade ≥ 1
  - [x] 4.3 Implement `recallAtK(List<String> rankedIds, Map<String, Integer> qrels, int k)` — fraction of relevant docs (grade ≥ 1) in top-K
  - [x] 4.4 Handle edge cases: empty result lists return 0.0, IDCG=0 returns 0.0, k larger than result list uses actual list size
  - [x] 4.5 Write unit tests (`MetricsComputerTest`) verifying known nDCG values, perfect ranking returns 1.0, empty results return 0.0, partial results compute correctly without penalizing empty positions (Req 11.7)
  - [x] 4.6 Write property test (`MetricsComputerPropertyTest`): Property 4 — all metrics ∈ [0,1], ideal ranking produces nDCG=1.0

- [x] 5. StatisticalTests — Cohen's d and Paired t-test
  Implement effect size and significance testing for benchmark pass/fail determination.
  - [x] 5.1 Create `StatisticalTests` class with `cohensD(double[] baseline, double[] cognitive)` computing mean(diffs)/stdev(diffs) for paired samples
  - [x] 5.2 Implement `pairedTTestPValue(double[] baseline, double[] cognitive)` computing t-statistic and approximating two-tailed p-value using normal CDF (Abramowitz & Stegun approximation)
  - [x] 5.3 Handle edge cases: stdev=0 returns d=0.0, SE=0 returns p=1.0, arrays must be same length (throw IllegalArgumentException otherwise)
  - [x] 5.4 Write unit tests (`StatisticalTestsTest`) verifying Cohen's d with known inputs (identical arrays → d=0, large difference → d>0.8) and p-value with known statistical examples

- [x] 6. BenchmarkSetup — Memory Instance Bootstrap
  Implement the setup logic that creates a fully-populated SpectorMemory instance from the dataset.
  - [x] 6.1 Create `BenchmarkSetup` class implementing `AutoCloseable` with `createMemoryInstance(LoadedDataset, EmbeddingProvider)` that ingests all corpus records into a SpectorMemory instance using RecallMode.OBSERVE
  - [x] 6.2 Implement `loadHebbianEdges(HebbianGraph, List<HebbianEdgeDef>, Map<String,Integer> idToSlot)` — populates bidirectional weighted edges, skipping edges with missing IDs (Req 5.5)
  - [x] 6.3 Implement `loadTemporalChains(TemporalChain, List<TemporalChainDef>, Map<String,Integer> idToSlot)` — establishes doubly-linked lists for each session chain in specified order
  - [x] 6.4 Implement `loadEntityGraph(EntityGraph, List<EntityRelation>, List<BenchmarkCorpusRecord>)` — constructs typed entity nodes and typed edges matching specified relation types
  - [x] 6.5 Implement the close() method to properly clean up off-heap resources

- [x] 7. BaselineRetriever — Vector-Only Scoring
  Implement the baseline retriever that uses only L2 distance without cognitive scoring.
  - [x] 7.1 Create `BaselineRetriever` class with constructor accepting MemorySegment, CognitiveRecordLayout, record count, and calibration parameters
  - [x] 7.2 Implement `retrieve(float[] queryVector, int topK)` using a min-heap of size topK, scoring each non-tombstoned record as similarity = 1/(1 + L2_distance), returning results sorted by descending score
  - [x] 7.3 Create `ScoredResult` record with memoryId and score fields
  - [x] 7.4 Write unit test (`BaselineRetrieverTest`) verifying: results ordered by descending similarity, tombstoned records excluded, only L2 distance affects ranking
  - [x] 7.5 Write property test (`BaselineRetrieverPropertyTest`): Property 3 — results strictly ordered by ascending L2 distance (descending similarity)

- [x] 8. ReportWriter — JSON and CSV Output
  Implement the benchmark report generation with all required output formats.
  - [x] 8.1 Create `ReportWriter` class and define inner records: `BenchmarkReport`, `RetrieverMetrics`, `SubsystemContributions`, `WinTieLoss`, `QueryResult`
  - [x] 8.2 Implement `writeSummary(Path outputDir, BenchmarkReport report)` — writes summary.json with all required fields per Req 16.1
  - [x] 8.3 Implement `writeDetail(Path outputDir, List<QueryResult> results)` — writes detail.csv with columns: query_id, baseline_nDCG, cognitive_nDCG, delta, contributing_subsystems, profile, latency_ms
  - [x] 8.4 Implement stdout summary logging: "Cognitive nDCG@10={X} vs Baseline nDCG@10={Y} (Δ={Z}, p={P})" per Req 16.3
  - [x] 8.5 Write unit tests (`ReportWriterTest`) verifying: summary.json schema correctness, CSV column format, stdout format string

- [x] 9. CognitiveRetriever — Full Pipeline Wrapper
  Implement the cognitive retriever wrapping the full RecallPipeline with OBSERVE mode.
  - [x] 9.1 Create `CognitiveRetriever` class with constructor accepting SpectorMemory instance
  - [x] 9.2 Implement `buildOptions(BenchmarkQuery)` — constructs RecallOptions with topK=10, RecallMode.OBSERVE, profile, synaptic filter, valence constraints
  - [x] 9.3 Implement `retrieve(String queryText, BenchmarkQuery query)` — executes memory.recall() with built options, maps CognitiveResult to ScoredResult
  - [x] 9.4 Handle edge cases: empty filter tags → no tag filter, null valence bounds → no valence filter, DEFAULT_MODE_NETWORK → restrict to SEMANTIC/PROCEDURAL memory types

- [x] 10. CognitiveBenchmarkHarness — Main Entry Point
  Implement the main harness that orchestrates the end-to-end benchmark run.
  - [x] 10.1 Create `CognitiveBenchmarkHarness` class with `main(String[] args)` parsing CLI args: dataset dir, output dir, optional regression threshold, optional topK (default 10)
  - [x] 10.2 Implement the main benchmark loop: for each query, execute both retrievers, compute per-query metrics (nDCG@10, MRR@10, Recall@10), measure latency, compute delta
  - [x] 10.3 Implement win/tie/loss counting: wins (delta > 0.001), ties (|delta| ≤ 0.001), losses (delta < -0.001), verify partition completeness
  - [x] 10.4 Compute aggregate metrics: mean nDCG@10, MRR@10, Recall@10 for both retrievers, average latency per query
  - [x] 10.5 Invoke StatisticalTests.cohensD() and pairedTTestPValue() on per-query nDCG arrays
  - [x] 10.6 Implement exit code logic: exit(1) if Cohen's d < 0.5, exit(2) if nDCG < regression threshold, exit(0) on success
  - [x] 10.7 Implement per-profile nDCG computation: group queries by cognitiveProfile, compute mean nDCG@10 per group
  - [x] 10.8 Implement timeout handling: skip queries exceeding 30 seconds, log skipped query ID, report skipped count
  - [x] 10.9 Write property test (`WinTieLossPropertyTest`): Property 5 — wins + ties + losses always equals total query count

- [x] 11. Subsystem Contribution Detection
  Implement the logic to determine which cognitive subsystem contributed to each result uplift.
  - [x] 11.1 Create `ContributingSubsystem` enum with values: HEBBIAN_GRAPH, TEMPORAL_CHAIN, ENTITY_GRAPH, IMPORTANCE_DECAY, VALENCE_FILTER, TAG_GATING
  - [x] 11.2 Implement `detect(String memoryId, Set<String> baselineTop10, HebbianGraph, TemporalChain, EntityGraph, ScoreBreakdown)` — determines contributing subsystem(s) for results in cognitive top-10 but not baseline top-10
  - [x] 11.3 Implement per-subsystem contribution percentage: count queries where subsystem contributed a result with relevance ≥ 2, divide by total queries
  - [x] 11.4 Integrate subsystem detection into the harness main loop, populating SubsystemContributions in the report

- [x] 12. CI Configuration
  Add benchmark test targets to the CI workflow.
  - [x] 12.1 Add Maven test execution for property tests (`*PropertyTest`) to `.github/workflows/ci.yml`
  - [x] 12.2 Add Maven test execution for unit tests (excluding property and integration tests) to CI workflow
  - [x] 12.3 Add nightly scheduled job for full dataset benchmark run using CognitiveBenchmarkHarness main class
  - [x] 12.4 Configure JVM arguments for off-heap memory access in benchmark execution (--add-opens, --enable-preview flags)

- [x] 13. Dataset Generator Pipeline (Java)
  Implement the Java-based dataset generation pipeline using Ollama for LLM-powered corpus expansion.
  - [x] 13.1 Create `GeneratorConfig` record with fields: ollamaUrl, modelName, maxRetries, personaPath, seedPath, approvedPath, outputDir, totalCorpusSize, numDays, conversationsPerDay, biographicalDepthYears — with `fromArgs(String[])` CLI parser
  - [x] 13.2 Create `OllamaCompletionClient` wrapping spector-embed-ollama or direct HTTP for chat completions, with retry logic (3×, exponential backoff 2/4/8s), timeout, JSON response parsing
  - [x] 13.3 Create `PersonaLoader` with `load(Path)` for persona.json schema validation, returning `PersonaDef`
  - [x] 13.4 Create `ConversationGenerator` with `generateDay(int dayIndex, List<BenchmarkCorpusRecord>)` — generates daily conversations via Ollama maintaining temporal coherence (morning/work/evening, weekday/weekend patterns)
  - [x] 13.5 Implement `generateBiographical(List<BenchmarkCorpusRecord>)` — biographical/historical memories (childhood, school, career, family) with timestamps spanning biographical depth into the past
  - [x] 13.6 Create `CognitiveAnnotator` with `annotateAll(List<BenchmarkCorpusRecord>)` — uses Ollama to assign valence, importance, arousal, synaptic tags, entity mentions
  - [x] 13.7 Create `GraphBuilder` with methods to build entity graph, temporal chains, and Hebbian edges from annotated corpus
  - [x] 13.8 Create `QueryGenerator` with `generate(...)` — generates queries with graded relevance judgments covering all expected_subsystem types and scenario categories
  - [x] 13.9 Create `DatasetValidator` with `validate(...)` — cross-file consistency checks; writes error report and exits non-zero on failures
  - [x] 13.10 Create `DatasetGeneratorMain` with `main(String[])` orchestrating: load persona → load seed/approved → generate conversations → generate biographical → annotate → build graphs → generate queries → validate → write output
  - [x] 13.11 Implement incremental generation: load approved corpus as read-only context, generate only new memories extending timeline, preserve approved records byte-identical in output (Req 18.9)
  - [x] 13.12 Implement error handling: Ollama unreachable → retry 3× with backoff → save partial + exit non-zero; invalid LLM JSON → retry with rephrased prompt → skip + log; schema violations → auto-repair or skip

- [x] 14. Integration and Property-Based Tests
  Implement the comprehensive test suite validating all correctness properties and integration scenarios.
  - [x] 14.1 Write `DatasetLoaderTest` — unit tests for valid persona parsing, invalid persona rejection, corpus loading with clamping, referential integrity violation detection
  - [x] 14.2 Write `DatasetRoundTripPropertyTest` — Property 1: serialize/deserialize round-trip for all model records (jqwik, min 100 iterations)
  - [x] 14.3 Write `ReferentialIntegrityPropertyTest` — Property 2: injected violations always detected without false negatives
  - [x] 14.4 Write `MetricsComputerPropertyTest` — Property 4: all metrics ∈ [0,1], ideal ranking → nDCG=1.0
  - [x] 14.5 Write `BaselineRetrieverPropertyTest` — Property 3: results strictly ordered by ascending L2 distance
  - [x] 14.6 Write `TombstoneExclusionPropertyTest` — Property 6: tombstoned memories never appear in results
  - [x] 14.7 Write `TagGatingPropertyTest` — Property 7: all results satisfy containment (record_tags & query_mask) == query_mask
  - [x] 14.8 Write `ValenceFilterPropertyTest` — Property 8: all results have valence within [min, max]
  - [x] 14.9 Write `ImportancePreScreenPropertyTest` — Property 9: low-importance + MAX_BUCKET + unpinned + resolved → excluded
  - [x] 14.10 Write `FusedScoreFormulaPropertyTest` — Property 10: verify fused scoring formula against independently computed values
  - [x] 14.11 Write `ReconsolidationPropertyTest` — Property 11: adjustedBucket = rawBucket >> min(recallCount, 5), always ∈ [0, rawBucket]
  - [x] 14.12 Write `ResultOrderingPropertyTest` — Property 12: results sorted descending, all scores > 0, count ≤ topK
  - [x] 14.13 Write `ImportanceDominancePropertyTest` — Property 13: equal similarity + higher importance → higher rank (beta > 0)
  - [x] 14.14 Write `GraphLoadingPropertyTest` — Properties 14, 16, 18: Hebbian bidirectional edges, temporal doubly-linked list, entity typed edges
  - [x] 14.15 Write `GraphTraversalPropertyTest` — Properties 15, 17, 19: attenuation factors (Hebbian 0.3×/hop, temporal 0.8×fwd/0.7×bwd, entity 0.25×/hop)
  - [x] 14.16 Write `SuppressionPropertyTest` — Property 20: suppressed excluded, unsuppressed eligible again
  - [x] 14.17 Write `ArousalOrderingPropertyTest` — Property 21: higher arousal → higher rank (equal age/importance/similarity)
  - [x] 14.18 Write `CognitiveProfilePropertyTest` — Properties 22, 23: DEBUGGING valence constraint, DEFAULT_MODE_NETWORK type restriction, HYPERFOCUS alpha=1/beta=0/decay=1
  - [x] 14.19 Write `PartialNdcgPropertyTest` — Property 24: fewer than K results → no penalty for empty positions
  - [x] 14.20 Write `RecencyPropertyTest` — Property 25: more recent → higher rank (equal similarity/importance/recallCount)
  - [x] 14.21 Write `ZeigarnickPropertyTest` — Property 26: unresolved flag → decay bucket clamped to 0
  - [x] 14.22 Write `BloomDeterminismPropertyTest` — Property 27: same tags → same 64-bit encoding
  - [x] 14.23 Write `BloomFalsePositivePropertyTest` — Property 28: empirical FP rate < 0.5% for ≤ 10 tags/record
  - [x] 14.24 Write `TagOverlapPropertyTest` — Property 29: overlap = bitCount(tags & mask) / bitCount(mask)
  - [x] 14.25 Write `ConsolidationPropertyTest` — Property 30: non-pruned memories retrievable with unchanged scores after consolidation
  - [x] 14.26 Write `HabituationPropertyTest` — Property 31: score attenuated by 0.85^(N-1) after N recalls
  - [x] 14.27 Write `CognitiveBenchmarkIntegrationTest` — end-to-end harness with mini-dataset: full pipeline, cognitive beats baseline, all profiles differ, reports generated
  - [x] 14.28 Write `ScoringPipelineValidationTest` — example-based tests for each scoring phase independently

## Notes

- All benchmark code is placed in the existing `spector-bench` module under a new `cognitive` sub-package
- The mini-dataset (Task 3) uses pre-computed embedding placeholders and does not require an embedding service for CI
- Integration tests requiring Ollama are gated behind `@EnabledIf` annotations (OLLAMA_LIVE environment variable)
- Property-based tests use jqwik 1.9.x with minimum 100 iterations per property
- The dataset generation pipeline (Task 13) is a later-wave task that depends on the harness being functional first
- Cohen's d ≥ 0.5 (medium effect size) is the pass/fail gate for the benchmark
