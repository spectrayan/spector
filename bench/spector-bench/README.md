# spector-bench 📊

> **JMH microbenchmarks, performance sweeps, cognitive benchmarks (LoCoMo, LongMemEval), and large-scale real-embedding performance runners.**

`spector-bench` handles empirical performance testing, SIMD kernel validation, cognitive memory evaluation, and large-scale index sweeps for Spector. It is designed to run locally, generating interactive HTML reports with latency charts.

---

## 🏗️ Core Architecture & Runners

1. **Cognitive Memory Benchmarks:**
   - **LoCoMo (`com.spectrayan.spector.bench.cognitive.locomo.LoCoMoBenchmarkHarness`):** Long-Term Conversation Memory benchmark evaluating multi-session dialogue recall, attribute tracking, and temporal event ordering.
   - **LongMemEval (`com.spectrayan.spector.bench.cognitive.longmemeval.LongMemEvalBenchmarkHarness`):** Long-horizon memory evaluation testing information updates, temporal reasoning, and prompt-ready `UserContext` assembly.
2. **JMH Microbenchmarks (`SpectorMicrobench`):** Microsecond-level isolation checks for the Panama Vector similarity kernels (AVX2 vs. AVX-512 vs. ARM NEON).
3. **Real-Embedding Sweeps (`RealEmbeddingScaleBench`):** Implements multi-centroid sweeps ($C \in \{32, 64, 128, 256\}$) using real Qwen3 text embeddings from local Ollama providers.
4. **Promotion Benchmarks (`SpectorIndexPromotionBench`):** Head-to-head comparisons of Flat Shard SIMD scans vs. Promoted HNSW Shards at 100K scale.
5. **Longitudinal Agent Evaluation (`com.spectrayan.spector.bench.longitudinal`):** Multi-session evaluation harness testing downstream agent outcome metrics (task completion rate, preference stability, bug non-repetition) over multi-day horizons using MemoryArena datasets. Includes Python MCP adapter (`spector_memoryarena_adapter.py`).

---

## 🚀 Running Benchmarks

### Running LoCoMo & LongMemEval Cognitive Benchmarks
```powershell
# Run LoCoMo Benchmark
.\scripts\run-locomo-benchmark.ps1 -DatasetDir D:\git\spector-datasets\locomo\data

# Run LongMemEval Benchmark
.\scripts\run-longmemeval-benchmark.ps1 -DatasetDir D:\git\spector-datasets\longmemeval\data
```

### Generate Dependencies Classpath
Ensure the classpath is compiled before running:
```bash
mvn clean compile -pl spector-bench
```

### Running the Real-Embedding Scale Sweep
Run Ollama qwen3-embedding benchmarking at a scale of 10,000 vectors:
```powershell
$cp = "spector-bench/target/classes;" + (Get-Content spector-bench/target/cp.txt)
java --add-modules jdk.incubator.vector -Xmx12g -cp $cp com.spectrayan.spector.bench.RealEmbeddingScaleBench 10000
```

### Running the Shard Promotion Comparison
Run Flat vs Promoted HNSW comparison at 100K scale:
```powershell
java --add-modules jdk.incubator.vector -Xmx12g -cp $cp com.spectrayan.spector.bench.SpectorIndexPromotionBench
```

