# Spector CPU Accelerator

The `spector-cpu` module provides the standard CPU SIMD Hardware Acceleration Layer implementation for the Spector kernel SPI (`ComputeAccelerator`).

## Overview

`spector-cpu` compiles and executes hardware-accelerated vector and matrix operations utilizing the Java Vector API (`jdk.incubator.vector`) across AVX-512, AVX2, and ARM NEON instruction sets.

Key features:
- **`CpuSimdAccelerator`**: Implements `ComputeAccelerator` SPI for CPU compute targets.
- **`CpuSimdSimilarityKernel`**: Vectorized dot product, cosine distance, and Euclidean distance kernels with zero allocations.
- **`CpuSimdMaxSimKernel`**: Vectorized late-interaction MaxSim operations for ColBERT-style retrieval.
- **`CpuSimdSvasqKernel`**: Fast vectorized SVASQ asymmetric scalar quantized distance scoring.
- **`CpuSimdCandidateKernel`**: Batch candidate distance computations.

## Usage

```xml
<dependency>
    <groupId>com.spectrayan</groupId>
    <artifactId>spector-cpu</artifactId>
    <version>${project.version}</version>
</dependency>
```

When placed on the module-path or classpath, `CpuSimdAccelerator` automatically registers via `java.util.ServiceLoader` under `com.spectrayan.spector.core.spi.ComputeAccelerator`.
