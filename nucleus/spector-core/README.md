# spector-core 🌀

> **The high-performance SIMD-accelerated similarity, cognitive, and quantization math core of Spector.**

`spector-core` houses the low-level math kernels, Walsh-Hadamard transforms, and vectorized similarity operators that form the computational engine of the search platform. Written natively for Java 25 utilizing the Panama Vector API (`jdk.incubator.vector`), it compiles hardware-specific SIMD instructions (AVX2, AVX-512, and ARM NEON) on the fly, eliminating native libraries or JNI bindings.

---

## 🏗️ Package Structure

| Package | Purpose | Classes |
|:---|:---|:---|
| `core.similarity` | Vector distance/similarity kernels (float32 + quantized) | `CosineSimilarity`, `DotProduct`, `EuclideanDistance`, `VectorOps`, `SimilarityFunction` |
| `core.cognitive` | Cognitive neuroscience compute kernels | `HopfieldKernel`, `LsrHopfieldKernel`, `FreeEnergyKernel`, `PredictiveCodingKernel`, `IntegratedInformationKernel`, etc. |
| `core.expression` | Embodied expression generators | `KinesicBlendshapeKernel`, `VocalProsodyKernel` |
| `core.privacy` | Privacy-preserving mechanisms | `DifferentialPrivacyKernel` |
| `core.quantization` | Scalar/vector quantization (SVASQ, INT8/4/2, TurboQuant) | `SvasqEncoder`, `ScalarQuantizer`, `TurboQuantizer` |
| `core.quantization.strategy` | Strategy pattern for quantized distance computation | `SvasqStrategy`, `TurboQuantStrategy`, `PackedBitStrategy` |
| `core.simd` | SIMD capability detection | `SimdCapability` |

---

## 🚀 Key APIs

### Similarity Kernels
```java
float[] a = ...;
float[] b = ...;

// SIMD L2 squared distance
float l2 = EuclideanDistance.INSTANCE.compute(a, 0, b, 0, a.length);

// SIMD Cosine similarity
float cos = CosineSimilarity.INSTANCE.compute(a, 0, b, 0, a.length);
```

### Fast Walsh-Hadamard Transform (FWHT)
```java
float[] data = ...; // must be padded to power of 2

// In-place Walsh-Hadamard Butterfly transform
SvasqFwht.applyFwht(data);
```

---

## 🛠️ Performance & SIMD Lanes

The module auto-detects hardware architectures and selects optimal vector lanes at runtime:

- **AVX-512 (512-bit):** 16 float lanes per instruction (Intel Xeon, recent AMD).
- **AVX2 (256-bit):** 8 float lanes per instruction (Most modern x86 desktops/laptops).
- **NEON (128-bit):** 4 float lanes per instruction (Apple Silicon M1/M2/M3, ARM64).
