# Requirements Document

## Introduction

This document specifies requirements for a comprehensive cognitive memory benchmark — a formal testing and evaluation framework that validates Spector Memory's biologically-inspired subsystems against curated test data and quantitatively demonstrates that the 6-phase fused cognitive scoring pipeline outperforms vanilla semantic (vector-only) search for realistic AI agent memory recall scenarios.

The benchmark extends the existing E2E test framework (11 test classes, 116 tests, 213 seed memories, LLM Judge) with a new cognitive-annotated dataset format, quantitative IR metrics (nDCG, MRR, Recall@K), and comparative evaluation harness.

## Glossary

- **Benchmark_Harness**: The test execution engine that loads the cognitive benchmark dataset, runs queries against both cognitive and baseline retrieval modes, computes IR metrics, and produces comparative result reports.
- **Cognitive_Dataset**: A BEIR-inspired dataset extended with cognitive annotations (valence, importance, synaptic tags, temporal relationships, entity links, session IDs, arousal) that enables testing all Spector Memory subsystems.
- **Baseline_Retriever**: A retrieval mode that uses only SIMD L2 vector distance (Phase 5) without cognitive scoring phases (no tag gating, no valence filter, no importance/decay fusion, no graph augmentation) — equivalent to pure semantic search.
- **Cognitive_Retriever**: The full Spector Memory recall pipeline including all 6 scoring phases plus graph augmentation (Hebbian, Temporal, Entity).
- **nDCG**: Normalized Discounted Cumulative Gain — an IR metric measuring ranking quality with graded relevance.
- **MRR**: Mean Reciprocal Rank — the average of the reciprocal of the rank at which the first relevant document is retrieved.
- **Recall_at_K**: The proportion of relevant documents retrieved in the top-K results.
- **Cognitive_Query**: A benchmark query annotated with cognitive context (mood, profile, synaptic filter tags, valence range, temporal hints) that exercises specific subsystem behaviors.
- **Relevance_Judgment**: A human-authored graded relevance score (0-3) mapping a query to a corpus memory, where 3 indicates the memory is the ideal answer and 0 indicates irrelevance.
- **Scoring_Pipeline**: The 6-phase CognitiveScorer: Tombstone Check → Synaptic Tag Gating → Valence Filter → Importance/Decay Pre-screen → SIMD L2 Distance → Fused Cognitive Score.
- **ICNU_Fusion**: Interest-Challenge-Novelty-Urgency importance computation at ingestion time.
- **Hebbian_Graph**: The off-heap memory-to-memory association graph using co-activation weights and spreading activation.
- **Temporal_Chain**: Doubly-linked list of memories within sessions enabling forward/backward temporal traversal.
- **Entity_Graph**: Typed entity-relationship graph enabling multi-hop knowledge traversal.
- **Cognitive_Profile**: A preset scoring configuration (alpha/beta weights, valence filters, special mechanics) that biases retrieval for specific task contexts.
- **Decay_Bucket**: A precomputed power-law temporal decay multiplier indexed by age range.
- **Synaptic_Tag**: A 64-bit inline Bloom filter encoding contextual markers for fast gating.
- **Habituation_Penalty**: Score attenuation applied to repeatedly-recalled memories to prevent filter bubbles.
- **Tombstone**: A logical deletion flag (bit 0 of the flags byte) that permanently excludes a record from scoring.
- **Suppression_Set**: An in-memory set of memory IDs excluded from recall results (reversible, unlike tombstones).
- **Dataset_Generator**: A Java pipeline (in spector-bench) that uses the existing spector-embed-ollama module to expand a hand-crafted seed dataset into a full-scale benchmark corpus with cognitive annotations.
- **User_Persona**: A JSON document defining the simulated user's identity, background, relationships, interests, life history, and personality traits — used to ground all generated conversations in a consistent character.
- **Seed_Week**: The manually-authored initial set of conversations (approximately 300-500 memories) covering diverse scenarios and life contexts, used as the template and context for LLM-based expansion to larger scale.

## Requirements

### Requirement 1: Cognitive Benchmark Dataset Format

**User Story:** As a developer, I want a benchmark dataset format that includes cognitive annotations beyond raw text and embeddings, so that I can test all Spector Memory subsystems (valence, importance, tags, temporal chains, entities, arousal) against curated ground truth.

#### Acceptance Criteria

1. THE Cognitive_Dataset SHALL define a corpus file named `corpus.jsonl` in JSONL format where each record contains: id (string, unique across corpus), text (string, 1–4096 characters), title (string, 1–256 characters), synaptic_tags (string array, 1–10 tags per record), valence (signed integer -128 to +127), importance (float 0.05 to 10.0), arousal (unsigned integer 0–255), session_id (string matching a session in the temporal chains file), timestamp_ms (long, epoch milliseconds), entity_mentions (array of typed entity references where each contains entity name and EntityType), memory_type (one of: EPISODIC, SEMANTIC, PROCEDURAL, WORKING), and recall_count (integer ≥ 0, default 0).
2. THE Cognitive_Dataset SHALL define a queries file named `queries.jsonl` in JSONL format where each query contains: id (string, unique across queries), text (string, 1–1024 characters), cognitive_profile (one of the 12 CognitiveProfile enum names: BALANCED, EXPLORING, DEBUGGING, RECALLING, CRITICAL, HYPERFOCUS, SYSTEMATIZER, DIVERGENT, PARANOID_SENTINEL, THE_EXECUTOR, HIGHLY_SENSITIVE, DEFAULT_MODE_NETWORK), synaptic_filter_tags (string array, 0–10 tags), min_valence (optional signed integer -128 to +127), max_valence (optional signed integer -128 to +127), expected_subsystem (one of: VECTOR_SIMILARITY, TAG_GATING, VALENCE_FILTER, IMPORTANCE_DECAY, HEBBIAN_GRAPH, TEMPORAL_CHAIN, ENTITY_GRAPH), and temporal_hint (one of: RECENT, OLD, or null).
3. THE Cognitive_Dataset SHALL define a relevance judgments file named `qrels.tsv` in TSV format with columns: query_id, corpus_id, relevance_grade (0 = irrelevant, 1 = marginally relevant, 2 = relevant, 3 = highly relevant), where every query_id exists in `queries.jsonl` and every corpus_id exists in `corpus.jsonl`.
4. THE Cognitive_Dataset SHALL define an entity relations file named `entities.jsonl` in JSONL format where each record contains: from_entity (object with name string and type matching EntityType enum), to_entity (object with name string and type matching EntityType enum), relation_type (one of the 21 RelationType enum values: MANAGES, REPORTS_TO, KNOWS, ASSIGNED_TO, AUTHORED, WORKS_ON, CREATED_BY, OWNS, IMPLEMENTS, PART_OF, CONTAINS, DEPENDS_ON, USES, CAUSES, BLOCKS, SUPERSEDES, PRECEDES, FOLLOWS, LOCATED_AT, RELATED_TO, OTHER), and source_memory_ids (array of 1–50 corpus IDs that establish this relation, each existing in `corpus.jsonl`).
5. THE Cognitive_Dataset SHALL define a temporal chains file named `temporal_chains.jsonl` in JSONL format where each record contains: session_id (string matching at least one corpus record's session_id), ordered_memory_ids (array of 2–200 corpus IDs in ascending timestamp_ms order within that session, each existing in `corpus.jsonl`).
6. THE Cognitive_Dataset SHALL define a hebbian edges file named `hebbian_edges.jsonl` in JSONL format where each record contains: memory_id_a (string existing in `corpus.jsonl`), memory_id_b (string existing in `corpus.jsonl`, different from memory_id_a), and co_activation_count (integer 1–10000 representing co-ingestion frequency).
7. THE Cognitive_Dataset SHALL define a user persona file named `persona.json` in JSON format containing: name (string), age (integer 18–100), occupation (string), interests (array of 3–20 strings), life_context (string, 50–2000 characters describing current life situation), personality_traits (array of 3–10 strings from a recognized trait taxonomy), and companion_relationship (string, 50–500 characters describing the user's relationship with the AI companion) — providing the grounding context for all generated conversations.
8. THE Cognitive_Dataset SHALL contain a minimum of 2000 corpus memories spanning at least 30 distinct daily sessions within a configurable observation window (default: 30 days, configurable range: 7–365 days), at least 100 unique entities appearing in the entity relations file, and at least 15 distinct synaptic tag clusters where two tags are in the same cluster if they co-occur in 5 or more corpus records.
9. THE Cognitive_Dataset SHALL contain memories with timestamps spanning at least 10 years into the past relative to the most recent timestamp, with at least 5% of corpus memories (≥ 100 records) having timestamps older than 1 year, representing biographical memories (childhood, past trips, family history) shared in conversation alongside the recent daily conversation window.
10. THE Cognitive_Dataset SHALL contain a minimum of 200 queries with graded relevance judgments, where at least 60 queries are classified as graph-dependent — defined as queries where all corpus memories with relevance_grade ≥ 2 are outside the vector similarity top-10 results but are reachable via Hebbian edge traversal, temporal chain traversal, or entity graph traversal from a top-10 seed result.

### Requirement 2: Baseline vs. Cognitive Comparative Evaluation

**User Story:** As a developer, I want to run the same queries against both a pure vector search baseline and the full cognitive retrieval pipeline, so that I can quantitatively demonstrate the advantage of fused cognitive scoring.

#### Acceptance Criteria

1. THE Benchmark_Harness SHALL execute each Cognitive_Query against both the Baseline_Retriever and the Cognitive_Retriever using identical corpus embeddings, retrieving the top-10 results from each retriever per query.
2. THE Baseline_Retriever SHALL rank results using only L2 vector distance converted to similarity (1/(1+d)) without applying synaptic tag gating, valence filtering, importance weighting, temporal decay, or graph augmentation.
3. THE Benchmark_Harness SHALL compute nDCG@10, MRR@10, and Recall@10 for both the Baseline_Retriever and the Cognitive_Retriever on every query.
4. THE Benchmark_Harness SHALL compute per-query nDCG@10 deltas (Cognitive minus Baseline) and report the number of queries where cognitive retrieval outperforms (delta > 0.001), ties with (absolute delta ≤ 0.001), or underperforms (delta < -0.001) the baseline.
5. WHEN the benchmark completes, THE Benchmark_Harness SHALL produce a summary JSON report containing: aggregate nDCG@10, MRR@10, Recall@10 for both retrievers, per-subsystem breakdown, win/tie/loss counts, and average latency in milliseconds per query.
6. IF the Cohen's d on per-query nDCG@10 differences is less than 0.5, THEN THE Benchmark_Harness SHALL exit with a non-zero status code and log a message indicating the cognitive retriever failed to demonstrate a medium effect size over the baseline.
7. WHEN the benchmark completes and Cohen's d ≥ 0.5, THE Benchmark_Harness SHALL include in the summary JSON report the computed Cohen's d value and the paired t-test p-value for the per-query nDCG@10 differences.

### Requirement 3: 6-Phase Scoring Pipeline Validation

**User Story:** As a developer, I want to validate each phase of the CognitiveScorer individually, so that I can verify that tombstone checks, tag gating, valence filters, importance pre-screening, SIMD distance, and fused scoring all function correctly.

#### Acceptance Criteria

1. WHEN a corpus memory has its tombstone flag set (flags bit 0 = 1), THE Scoring_Pipeline SHALL exclude that memory from all query results regardless of vector similarity, importance, or recall count.
2. WHEN a Cognitive_Query specifies synaptic_filter_tags and the query_mask is non-zero, THE Scoring_Pipeline SHALL exclude corpus memories whose Bloom filter does not satisfy containment (record_tags & query_mask == query_mask).
3. WHEN a Cognitive_Query specifies a valence range (min_valence and/or max_valence), THE Scoring_Pipeline SHALL exclude corpus memories whose valence falls outside the closed interval [min_valence, max_valence], where min_valence defaults to -128 and max_valence defaults to +127 when unspecified.
4. WHEN a corpus memory has importance below the profile's configured minImportance threshold, THE Scoring_Pipeline SHALL exclude that memory from results. Additionally, WHEN a corpus memory has importance below 1.0, its adjusted decay bucket equals MAX_BUCKET (11), it is not pinned (flags bit 3 = 0), and it is resolved (flags bit 5 = 1), THE Scoring_Pipeline SHALL exclude that memory from results.
5. THE Scoring_Pipeline SHALL produce final scores using the formula: alpha × similarity + beta × importance × decay, where similarity = 1/(1 + L2_distance × strictnessCoefficient), decay = min(1.0, DECAY_BUCKETS[adjustedBucket] × arousalModifier), and alpha + beta are defined by the active Cognitive_Profile.
6. WHEN a corpus memory has been recalled multiple times (recall_count > 0), THE Scoring_Pipeline SHALL adjust its decay bucket by shifting it back by recall_count / 3 positions (Long-Term Potentiation), capping the adjustment at a maximum of 5 positions.
7. WHEN a corpus memory passes all 6 phases, THE Scoring_Pipeline SHALL assign a final score greater than 0.0 and include it in the result set ranked by descending score, returning at most topK results (default: 10).
8. IF no corpus memories pass phases 1 through 4, THEN THE Scoring_Pipeline SHALL return an empty result list within 10 milliseconds for a corpus of up to 100,000 records.

### Requirement 4: Importance-Based Retrieval (ICNU) Validation

**User Story:** As a developer, I want to verify that the ICNU importance fusion correctly prioritizes high-importance memories in retrieval, so that critical information surfaces above routine observations.

#### Acceptance Criteria

1. WHEN two corpus memories have equal vector similarity to a query but different importance scores, THE Cognitive_Retriever SHALL rank the higher-importance memory above the lower-importance memory (given non-zero beta weight).
2. THE Benchmark_Harness SHALL include at least 10 queries where the correct answer has lower vector similarity but higher importance than distractors, validating that importance weighting rescues memories that pure vector search would miss.
3. WHEN a corpus memory has importance computed from ICNU fusion with urgency=1.0, THE Cognitive_Retriever SHALL rank that memory higher than an otherwise-identical memory with urgency=0.0.

### Requirement 5: Hebbian Graph Validation

**User Story:** As a developer, I want to validate that the Hebbian spreading activation discovers associated memories that vector similarity alone cannot find, so that co-activation learning improves recall completeness.

#### Acceptance Criteria

1. WHEN the Cognitive_Dataset defines a hebbian edge between memory A and memory B with a co_activation_count, THE Hebbian_Graph SHALL establish a bidirectional weighted edge between those memories with weight derived from the co_activation_count value after dataset loading.
2. WHEN a query retrieves memory A among the top-K scoring results (seed set) and memory B is a Hebbian neighbor of A within 2 hops, THE Cognitive_Retriever SHALL include memory B in the final results with a score attenuated by 0.3× per hop.
3. THE Benchmark_Harness SHALL include at least 10 queries where the correct answer (relevance_grade ≥ 2) is not in the Baseline_Retriever vector similarity top-10 results but is reachable via Hebbian spreading activation from a seed result.
4. THE Benchmark_Harness SHALL compute a Hebbian contribution metric: the percentage of queries where a result with relevance_grade ≥ 2 was discovered exclusively through Hebbian traversal (present in Cognitive_Retriever top-10 results but absent from Baseline_Retriever top-10 results).
5. IF a hebbian edge in the Cognitive_Dataset references a memory ID that does not exist in the corpus, THEN THE Hebbian_Graph SHALL skip that edge during loading without failing the overall dataset load.

### Requirement 6: Temporal Chain Validation

**User Story:** As a developer, I want to validate that temporal chain traversal retrieves sequentially-related memories (what happened before/after), so that causal and narrative recall works correctly.

#### Acceptance Criteria

1. WHEN the Cognitive_Dataset defines a temporal chain for a session, THE Temporal_Chain SHALL link those memories in their specified order as a doubly-linked list.
2. WHEN a query retrieves a memory that is part of a temporal chain, THE Cognitive_Retriever SHALL include temporally adjacent memories (up to 3 hops forward, 3 hops backward) with appropriate score attenuation (0.8× forward, 0.7× backward).
3. THE Benchmark_Harness SHALL include at least 10 queries of the form "what happened after X" or "what happened before Y" where the correct answer is reachable only through temporal chain traversal.
4. THE Benchmark_Harness SHALL compute a temporal contribution metric: the percentage of queries where a relevant result was discovered exclusively through temporal chain extension.

### Requirement 7: Entity Graph Validation

**User Story:** As a developer, I want to validate that entity graph traversal enables multi-hop knowledge retrieval (e.g., "what project does Alice manage?"), so that relational reasoning augments vector search.

#### Acceptance Criteria

1. WHEN the Cognitive_Dataset defines entity relations, THE Entity_Graph SHALL construct typed entities and typed edges matching the specified relation types.
2. WHEN a query mentions an entity that exists in the Entity_Graph, THE Cognitive_Retriever SHALL traverse up to 2 hops via typed edges and include memories linked to discovered entities (0.25× attenuation per hop).
3. THE Benchmark_Harness SHALL include at least 10 queries requiring multi-hop entity traversal (e.g., Person → MANAGES → Project → memories about that Project) where the answer is not retrievable by vector similarity alone.
4. THE Benchmark_Harness SHALL compute an entity graph contribution metric: the percentage of queries where a relevant result was discovered exclusively through entity graph traversal.

### Requirement 8: Tombstone and Suppression Validation

**User Story:** As a developer, I want to validate that tombstoned and suppressed memories are correctly excluded from retrieval, so that logical deletion and suppression work as specified.

#### Acceptance Criteria

1. WHEN a corpus memory is tombstoned (flags bit 0 = 1), THE Scoring_Pipeline SHALL skip that memory at Phase 1 and never include it in any query results.
2. WHEN a corpus memory ID is added to the Suppression_Set, THE Cognitive_Retriever SHALL exclude that memory from recall results even though it passes all 6 scoring phases.
3. THE Benchmark_Harness SHALL include at least 5 queries where a highly-similar distractor memory is tombstoned, verifying that the correct (non-tombstoned) answer is returned instead.
4. WHEN a suppressed memory is unsuppressed, THE Cognitive_Retriever SHALL include that memory in subsequent recall results.

### Requirement 9: Dopamine/Surprise Detection Validation

**User Story:** As a developer, I want to validate that the surprise detection system correctly identifies novel memories and assigns them elevated importance, so that flashbulb memories and statistically surprising content are properly prioritized.

#### Acceptance Criteria

1. WHEN a corpus memory has a Z-score exceeding the flashbulb threshold (default 3.0), THE Cognitive_Retriever SHALL treat that memory as pinned (exempt from decay) with maximum importance.
2. THE Benchmark_Harness SHALL include at least 5 corpus memories with extreme spatial surprise (high Z-score relative to corpus distribution) and verify they rank higher than temporally-decayed memories of similar vector similarity.
3. THE Benchmark_Harness SHALL include at least 5 queries where temporal surprise (long gap since last matching tag pattern) produces elevated importance, verifying those memories outrank recently-repeated memories.

### Requirement 10: Amygdala/Emotional Valence Validation

**User Story:** As a developer, I want to validate that valence filtering and arousal-modulated decay work correctly, so that mood-congruent recall and emotional memory persistence function as designed.

#### Acceptance Criteria

1. WHEN a Cognitive_Query specifies maxValence=-10 (negative-only recall), THE Cognitive_Retriever SHALL return only corpus memories with valence ≤ -10.
2. WHEN a Cognitive_Query specifies minValence=+10 (positive-only recall), THE Cognitive_Retriever SHALL return only corpus memories with valence ≥ +10.
3. WHEN two corpus memories have equal age and importance but different arousal values, THE Cognitive_Retriever SHALL rank the higher-arousal memory above the lower-arousal memory (due to arousal-modulated decay resistance).
4. THE Benchmark_Harness SHALL include at least 10 queries exercising valence-filtered recall (5 negative-only, 5 positive-only) with relevance judgments that validate correct filtering.

### Requirement 11: Cognitive Profile Comparison

**User Story:** As a developer, I want to compare retrieval behavior across all 12 cognitive profiles on the same query set, so that I can verify each profile produces distinct, contextually-appropriate results.

#### Acceptance Criteria

1. THE Benchmark_Harness SHALL execute a designated subset of queries (at least 20) against all 12 cognitive profiles: BALANCED, EXPLORING, DEBUGGING, RECALLING, CRITICAL, HYPERFOCUS, SYSTEMATIZER, DIVERGENT, PARANOID_SENTINEL, THE_EXECUTOR, HIGHLY_SENSITIVE, DEFAULT_MODE_NETWORK.
2. WHEN the DEBUGGING profile is active (alpha=0.3, beta=0.7, maxValence=-10), THE Cognitive_Retriever SHALL return only memories with valence ≤ -10 and compute fused scores as 0.3×similarity + 0.7×importance×decay.
3. WHEN the HYPERFOCUS profile is active with a synaptic tag mask, THE Cognitive_Retriever SHALL compute scores using pure similarity (alpha=1.0, beta=0.0), clamp decay to 1.0 for tag-matching memories, and apply a boost multiplier to those memories.
4. WHEN the DEFAULT_MODE_NETWORK profile is active, THE Cognitive_Retriever SHALL return results only from SEMANTIC and PROCEDURAL memory types, excluding WORKING and EPISODIC memories from all results.
5. THE Benchmark_Harness SHALL compute per-profile nDCG@10 using profile-specific relevance judgment sets defined in the Cognitive_Dataset queries file, where each profile-query combination has its own graded relevance scores in qrels.tsv.
6. THE Benchmark_Harness SHALL verify that for at least 90% of queries in the comparison subset, no two cognitive profiles produce identical top-10 result rankings (measured by matching result IDs in the same order).
7. IF a cognitive profile returns fewer than 10 results for a query (due to valence filtering or memory type restriction), THEN THE Benchmark_Harness SHALL compute nDCG using only the returned results without penalizing the empty positions.

### Requirement 12: Decay and Reconsolidation Validation

**User Story:** As a developer, I want to validate that power-law decay, LTP reconsolidation, arousal modulation, and the Zeigarnik effect all function correctly, so that temporal memory dynamics match the design specifications.

#### Acceptance Criteria

1. WHEN two corpus memories have identical vector similarity and importance but different timestamps, THE Cognitive_Retriever SHALL rank the more recent memory higher (given equal recall counts and no pinning).
2. WHEN a corpus memory has recall_count=12 and is 30 days old, THE Scoring_Pipeline SHALL compute its effective decay bucket as raw_bucket minus 4 (12/3 = 4 reconsolidation shifts).
3. WHEN a corpus memory has the Zeigarnik flag set (unresolved, bit 5 = 0), THE Scoring_Pipeline SHALL clamp its decay bucket to 0, making it perpetually fresh regardless of actual age.
4. WHEN a corpus memory has arousal=200 (extreme), THE Scoring_Pipeline SHALL apply a 1.65× arousal modifier to its base decay factor, making it decay slower than a neutral-arousal memory of the same age.

### Requirement 13: Synaptic Tag (Bloom Filter) Validation

**User Story:** As a developer, I want to validate the Bloom filter encoding, containment checks, false positive behavior, and tag overlap scoring, so that synaptic gating functions correctly at Phase 2 of the pipeline.

#### Acceptance Criteria

1. THE Benchmark_Harness SHALL verify that SynapticTagEncoder.encode() produces deterministic 64-bit Bloom filters for given tag sets using MurmurHash3 double-hashing with k=3.
2. WHEN a query tag mask is applied, THE Scoring_Pipeline SHALL pass memories whose encoded tags satisfy (record_tags & query_mask) == query_mask (containment) and reject all others.
3. THE Benchmark_Harness SHALL measure empirical false positive rates for tag gating across the benchmark corpus and verify they fall within expected bounds (less than 0.5% for memories with ≤ 10 tags per record).
4. THE Benchmark_Harness SHALL verify that tag overlap scoring computes the correct fractional ratio: bitCount(record_tags & query_mask) / bitCount(query_mask).

### Requirement 14: Sleep Consolidation Validation

**User Story:** As a developer, I want to validate that the hippocampus-inspired sleep consolidation correctly prunes low-importance memories and rebuilds partitions, so that long-term memory maintenance works as designed.

#### Acceptance Criteria

1. WHEN sleep consolidation is triggered on the benchmark corpus, THE Benchmark_Harness SHALL verify that memories with tombstone ratios exceeding 30% trigger partition rebuilds.
2. WHEN sleep consolidation completes, THE Benchmark_Harness SHALL verify that all non-tombstoned, non-pruned memories remain retrievable with unchanged scores.
3. WHEN a memory has importance below the consolidation threshold and has not been recalled, THE Benchmark_Harness SHALL verify that consolidation marks it for pruning.

### Requirement 15: Habituation (Anti-Filter Bubble) Validation

**User Story:** As a developer, I want to validate that habituation attenuates scores for repeatedly-recalled memories, so that diverse results surface over successive queries.

#### Acceptance Criteria

1. WHEN the same Cognitive_Query is executed multiple times, THE Cognitive_Retriever SHALL attenuate the score of previously-returned memories by the habituation decay rate (default 0.85 per additional recall).
2. WHEN a memory has been recalled 5 times, THE Habituation_Penalty SHALL reduce its score to approximately 52% of its original value (0.85^4 ≈ 0.52).
3. THE Benchmark_Harness SHALL execute a repeated-query test demonstrating that after 5 identical queries, new memories appear in the top-K results that were absent from the first query's results.

### Requirement 16: Benchmark Report and Metrics Output

**User Story:** As a developer, I want the benchmark to produce a structured report with per-subsystem metrics and comparative analysis, so that I can track cognitive retrieval quality over time and demonstrate superiority over baseline.

#### Acceptance Criteria

1. WHEN the benchmark completes, THE Benchmark_Harness SHALL write a JSON report file containing: timestamp, corpus size, query count, per-retriever metrics (nDCG@10, MRR@10, Recall@10, average latency), per-subsystem contribution percentages (hebbian, temporal, entity, importance, valence, tag gating), and per-profile nDCG@10 scores.
2. THE Benchmark_Harness SHALL write a per-query detail CSV file with columns: query_id, baseline_nDCG, cognitive_nDCG, delta, contributing_subsystems, profile, latency_ms.
3. WHEN a benchmark run completes, THE Benchmark_Harness SHALL log a one-line summary to stdout in the format: "Cognitive nDCG@10={X} vs Baseline nDCG@10={Y} (Δ={Z}, p={P})" where P is the p-value from a paired t-test on per-query nDCG scores.
4. THE Benchmark_Harness SHALL support an optional regression threshold parameter, and IF the cognitive nDCG@10 drops below the threshold compared to a stored baseline, THEN THE Benchmark_Harness SHALL exit with a non-zero status code for CI integration.

### Requirement 17: Dataset Scenario Coverage

**User Story:** As a developer, I want the benchmark dataset to cover realistic AI agent memory recall scenarios across diverse cognitive contexts, so that the evaluation reflects real-world usage rather than synthetic patterns.

#### Acceptance Criteria

1. THE Cognitive_Dataset SHALL include at least 8 scenario categories: personal life events, professional/work conversations, health and fitness tracking, childhood/biographical memories, coding/debugging sessions, knowledge base facts, emotional/relationship events, and daily routine observations.
2. THE Cognitive_Dataset SHALL include at least 20 queries where the correct answer requires combining multiple cognitive signals (e.g., importance + temporal decay + tag gating) rather than any single signal alone.
3. THE Cognitive_Dataset SHALL include at least 10 adversarial queries designed to exploit weaknesses of pure vector search (e.g., semantically similar distractors that differ on valence, importance, or temporal relevance).
4. THE Cognitive_Dataset SHALL include corpus memories spanning a realistic lifetime timeline: recent daily conversations (configurable window), biographical memories shared in conversation (childhood, school, past relationships, career history, family events, trips), and everything in between — reflecting how a real person shares their life with an AI companion over time.
5. THE Cognitive_Dataset SHALL model realistic conversation patterns: a user sharing daily life events with an AI companion (like OpenClaw), coding sessions with a development agent, health check-ins, work updates, personal reflections, childhood memories shared in biographical context, stories about family members, friends, past trips, and life milestones.
6. THE Cognitive_Dataset SHALL include memories with diverse decay characteristics: pinned flashbulb memories (childhood trauma, major life events), high-importance work deadlines (Zeigarnik unresolved), routine observations (fast decay), frequently-recalled preferences (LTP-reinforced), emotionally intense events (arousal-modulated slow decay), and old biographical memories that the user rarely revisits.

### Requirement 18: Dataset Generation Pipeline

**User Story:** As a developer, I want a Python-based generation pipeline that uses an LLM (Ollama) to expand a hand-crafted seed dataset into a full-scale realistic corpus, so that the benchmark achieves sufficient scale without manual authoring of thousands of memories.

#### Acceptance Criteria

1. THE Dataset_Generator SHALL accept a hand-crafted seed dataset (minimum 300 memories covering diverse scenarios as defined in Requirement 17 criterion 1) as input and produce a full-scale corpus of at least 2000 memories as output.
2. THE Dataset_Generator SHALL use Ollama (local LLM) to generate new conversations consistent with the User_Persona definition, continuing storylines, introducing realistic new events, and maintaining temporal coherence across the configurable time window (default: 1 month of daily conversations).
3. THE Dataset_Generator SHALL automatically annotate generated memories with cognitive metadata: valence (derived via sentiment analysis, mapped to -128 to +127), importance (derived via LLM rating of content significance on the 0.05 to 10.0 scale), synaptic tags (derived via topic extraction), arousal (derived via LLM rating of emotional intensity, mapped to 0-255), and entity mentions (derived via named entity recognition from the generated text).
4. THE Dataset_Generator SHALL maintain temporal coherence: generated conversations follow a daily rhythm (morning check-ins between 07:00-09:00, work sessions between 09:00-17:00, evening reflections between 19:00-23:00), respect weekday/weekend patterns (no work sessions on weekends), and reference at least one event from the prior 7 days per simulated day.
5. THE Dataset_Generator SHALL generate biographical and historical memories (childhood events, family history, past trips, old friendships, school experiences, career milestones) that the user shares with the AI companion over time, with timestamps spanning from the configured biographical memory depth (default: 25 years into the past) up to the present observation window.
6. THE Dataset_Generator SHALL produce all output files conforming to the Cognitive_Dataset format (corpus.jsonl, queries.jsonl, qrels.tsv, entities.jsonl, temporal_chains.jsonl, hebbian_edges.jsonl, persona.json) as specified in Requirement 1.
7. THE Dataset_Generator SHALL include a validation step that checks generated data for consistency: all entity references resolve, temporal chain IDs exist in corpus, hebbian edge IDs exist in corpus, and relevance judgment IDs exist in both queries and corpus. IF validation detects any inconsistency, THEN THE Dataset_Generator SHALL write a validation error report listing all failures by type and affected record IDs, and SHALL exit with a non-zero status code without writing final output files.
8. THE Dataset_Generator SHALL be configurable for: total corpus size (default: 2000), number of simulated conversation days (default: 30), conversation density per day (default: 5 memories per day), biographical memory depth (default: 25 years), LLM model name (default: "llama3"), and Ollama endpoint URL (default: "http://localhost:11434").
9. THE Dataset_Generator SHALL support incremental generation: a user can generate a 1-week seed, mark it as approved by placing it in a designated approved corpus file, then expand to 1 month, then 3 months, without regenerating or modifying any memories present in the approved corpus file. WHEN expanding incrementally, THE Dataset_Generator SHALL load the approved corpus as read-only context and generate only new memories that extend the existing timeline.
10. IF the Ollama endpoint is unreachable or returns an error response during generation, THEN THE Dataset_Generator SHALL retry the request up to 3 times with exponential backoff (2, 4, 8 seconds), and IF all retries fail, THEN THE Dataset_Generator SHALL log the failure with the affected generation step, preserve all successfully generated content to a partial output file, and exit with a non-zero status code indicating incomplete generation.
