# Benchmarking & Evaluation Layer (`/bench`)

This directory contains modules and tools dedicated to testing system scaling, accuracy, throughput, and memory consumption.

## Modules

* **[`spector-bench`](/bench/spector-bench)**: Contains JMH (Java Microbenchmark Harness) code and evaluation suites that measure nDCG, Recall, MRR, and search latencies across standard datasets (`balanced-baseline`, `adhd-diversified`, `engineer-persona`).
* **[`k6`](/bench/k6)**: Production-grade **Grafana k6** API performance, load, stress, spike, and multi-user isolation testing framework targeting Spector Synapse Memory REST APIs (`/api/v1/memory/*`).

## Usage

* **Running JMH Benchmarks**: Execution is orchestrated via custom scripts in `spector-bench`.
* **Running k6 API Load Tests**: Run `./bench/k6/run.ps1 -Scenario mixed` or refer to [`bench/k6/README.md`](/bench/k6/README.md).
* **Microbenchmarks**: Focuses on hot-spot allocations (e.g. Panama Vector API SIMD distance lookups vs scalar alternatives).
