---
name: Performance report
about: Report a performance regression or suggest an optimization
title: '[PERF] '
labels: 'performance'
assignees: ''

---

**Describe the performance issue**
What operation is slow or regressed? (e.g. HNSW search, vector ingestion, BM25 scoring)

**Benchmark data**
Please include JMH or timing results:
- **Before:** [ops/s or latency]
- **After:** [ops/s or latency]
- **Dataset:** [size, dimensions, similarity function]

**Environment:**
- OS: [e.g. Ubuntu 22.04]
- JDK version: [e.g. OpenJDK 25]
- CPU: [e.g. Intel i9-13900K, Apple M3 Pro]
- SIMD capability: [e.g. S_512_BIT / AVX-512]
- RAM: [e.g. 64 GB]

**Proposed optimization**
If you have ideas for improvement, describe them here.

**Additional context**
Add any JMH output, flame graphs, or profiler screenshots.
