# Spector HDC Module

This module (`spector-hdc`) provides the first SIMD-native Hyperdimensional Computing library for Java 25.
It is part of the Spectrayan OSS Spector database ecosystem. 
Implements issue #359.

## Overview
Hyperdimensional Computing (HDC) uses high-dimensional boolean vectors to represent and compare data. 
This library provides:
- **`Hypervector`**: Core representation of an HD vector.
- **`HdcAlgebra`**: Core HDC operations: bind, bundle, permute.
- **`TextEncoder`**: Encodes strings into hypervectors using n-grams.
- **`HdcSimilarity`**: High-level text similarity API.
- **`BinaryVectorStorage`**: Fast, cache-aligned, off-heap vector storage via Panama FFM.

## Architecture

```
Text -> N-Grams -> HypervectorFactory (Seed) -> HdcAlgebra (Permute) -> HdcAlgebra (Bundle) -> Final Hypervector
```

## Performance
- **SIMD**: Accelerates Hamming distance computations via Java 25 Vector API and `VectorOperators.BIT_COUNT`.
- **Off-heap Memory**: Panama FFM is used in `BinaryVectorStorage` to bypass GC and guarantee 64-byte cache-line alignment.

## Limitations
- This calculates **lexical similarity** (based on n-grams) rather than deep semantic embeddings. Suitable for fuzzy matching or fast screening.

## Quick Start
```java
HdcSimilarity sim = HdcSimilarity.builder()
    .dimensions(10_000)
    .ngramSize(3)
    .build();

double score = sim.similarity("hello world", "hello there");
System.out.println("Similarity: " + score); // > 0.5
```
