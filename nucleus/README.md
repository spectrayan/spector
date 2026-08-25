# Nucleus Layer (`/nucleus`)

This directory contains the core foundation modules of the Spector headless engine. The nucleus modules provide compile-time utilities, configuration management, telemetry instrumentation, event messaging, and low-level memory/storage block allocations that support the rest of the Spector ecosystem.

## Modules

* **[`spector-bom`](/nucleus/spector-bom)**: Bill of Materials (BOM) POM that manages unified dependency versions for the entire Spector reactor.
* **[`spector-commons`](/nucleus/spector-commons)**: Common utilities, standard constants, and the global `ErrorCode` exception registry.
* **[`spector-config`](/nucleus/spector-config)**: Wires runtime configurations via the `SpectorConfigFactory` (parses `spector.yml` and environment variables).
* **[`spector-core`](/nucleus/spector-core)**: Low-level compute SPIs (Similarity, HNSW, SVASQ, MaxSim) and quantization algorithms (Scalar, Turbo, SVASQ).
* **[`spector-cpu`](/nucleus/spector-cpu)**: Java 25 Panama Vector SIMD hardware acceleration kernel implementations.
* **[`spector-gpu`](/nucleus/spector-gpu)**: Java 25 Panama FFM + CUDA GPU hardware acceleration kernel implementations.
* **[`spector-index`](/nucleus/spector-index)**: In-memory vector indexes (HNSW, Quantized HNSW, SpectorIndex) and sparse retrieval indexes (BM25, Splade).
* **[`spector-hdc`](/nucleus/spector-hdc)**: Hyperdimensional computing vectors and operations.
* **[`spector-events`](/nucleus/spector-events)**: Internal pub/sub event pipeline that coordinates async notifications.
* **[`spector-test-support`](/nucleus/spector-test-support)**: Common test harnesses and mocks used for testing memory and search components.

## Dependency Rules

* **Layer Constraint**: Nucleus modules represent the lowest layer of the stack. They must **never** depend on any search, intelligence, runtime, or infrastructure modules.
* **Flow**: Dependencies flow downwards. Any higher-level module in `/memory`, `/synapse`, or `/bench` can freely depend on `/nucleus` modules.
