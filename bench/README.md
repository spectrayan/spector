# Benchmarking & Evaluation Layer (`/bench`)

This directory contains modules dedicated to testing system scaling, accuracy, throughput, and memory consumption.

## Modules

* **[`spector-bench`](/bench/spector-bench)**: Contains JMH (Java Microbenchmark Harness) code and evaluation suites that measure nDCG, Recall, MRR, and search latencies across standard datasets (`balanced-baseline`, `adhd-diversified`, `engineer-persona`).

## Usage

* **Running Benchmarks**: Execution is orchestrated via custom PowerShell scripts. The benchmark runner loads, indices, and queries datasets utilizing local LLM embedding providers, then outputs comparative quality and performance reports.
* **Microbenchmarks**: Focuses on hot-spot allocations (e.g. Panama Vector API SIMD distance lookups vs scalar alternatives).
