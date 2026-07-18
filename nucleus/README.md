# Nucleus Layer (`/nucleus`)

This directory contains the core foundation modules of the Spector headless engine. The nucleus modules provide compile-time utilities, configuration management, telemetry instrumentation, event messaging, and low-level memory/storage block allocations that support the rest of the Spector ecosystem.

## Modules

* **[`spector-bom`](/nucleus/spector-bom)**: Bill of Materials (BOM) Pom that manages unified dependency versions for the entire Spector reactor.
* **[`spector-commons`](/nucleus/spector-commons)**: Common utilities, standard constants, and the global `ErrorCode` exception registry.
* **[`spector-config`](/nucleus/spector-config)**: Wires runtime configurations via the `SpectorConfigFactory` (parses `spector.yml` and environment variables).
* **[`spector-core`](/nucleus/spector-core)**: Low-level primitive vector operations, SIMD math bounds, and JDK Project Panama FFM off-heap layout contracts.
* **[`spector-events`](/nucleus/spector-events)**: Internal pub/sub event pipeline that coordinates async notifications (e.g. consolidation signals).
* **[`spector-metrics`](/nucleus/spector-metrics)**: Micrometer-based telemetry and performance metrics trackers for retrieval latency.
* **[`spector-storage`](/nucleus/spector-storage)**: Off-heap byte allocation, memory block management, and the Write-Ahead Log (WAL) persistence engine.
* **[`spector-test-support`](/nucleus/spector-test-support)**: Common test harnesses and mocks used for testing memory and search components.

## Dependency Rules

* **Layer Constraint**: Nucleus modules represent the lowest layer of the stack. They must **never** depend on any search, intelligence, runtime, or infrastructure modules.
* **Flow**: Dependencies flow downwards. Any higher-level module in `/memory`, `/synapse`, or `/bench` can freely depend on `/nucleus` modules.
