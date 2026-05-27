---
title: "Synapse — Tags & Scoring"
description: "The 32-byte synaptic header, 64-bit inline Bloom filter, and CognitiveRecordLayout binary format."
---

# 🔗 Synapse — Tags & Scoring

> **Package**: `com.spectrayan.spector.memory.synapse`
>
> **Biological Analog**: In neuroscience, the **Synaptic Tagging and Capture (STC)** hypothesis (Frey & Morris, 1997) describes how synapses are "tagged" during learning with lightweight chemical markers. These tags don't contain the memory itself — they identify *what* the memory is about and *when* it was formed, enabling the brain to route consolidation activity efficiently.

---

## The 32-Byte Synaptic Header

Every cognitive memory record begins with a 32-byte aligned header — the digital equivalent of a synaptic tag:

```
 Offset   Size   Field                Description
 ──────   ────   ─────                ───────────
    0      8B    timestamp            Unix epoch ms when memory was formed
    8      8B    synaptic_tags        64-bit Bloom filter of contextual markers
   16      4B    exact_norm           L2 norm of original float vector (for residual scoring)
   20      4B    importance           Cognitive importance [0.0 – 1.0]
   24      4B    centroid_id          IVF centroid assignment (-1 if unassigned)
   28      2B    recall_count         Times recalled (reconsolidation counter)
   30      1B    valence              Emotional coloring [-128 to +127]
   31      1B    flags                Bit flags: [0] tombstone, [1] pinned, ...
```

**Total**: 32 bytes, naturally aligned for SIMD access.

!!! info "Why 32 bytes?"
    The header is exactly one **AVX2 register width** (256 bits). This means the entire header can be loaded in a single SIMD instruction for bulk scanning operations.

---

## SynapticTagEncoder — The Inline Bloom Filter

The `synaptic_tags` field is a **64-bit inline Bloom filter** rather than a discrete bitmap. This enables encoding thousands of unique tag strings across the system while each individual record holds 5-50 tags with negligible false positive rates.

### How It Works

```java
public static long encode(String... tags) {
    long filter = 0L;
    for (String tag : tags) {
        filter |= encodeTag(tag);
    }
    return filter;
}

private static long encodeTag(String tag) {
    long h = murmurHash64(tag);
    long h1 = h;
    long h2 = h >>> 32 | h << 32; // Swap halves for second hash
    
    long filter = 0L;
    for (int i = 0; i < K; i++) {  // K = 3 hash functions
        int bitIndex = Math.abs((int) ((h1 + (long) i * h2) % M)); // M = 64
        filter |= (1L << bitIndex);
    }
    return filter;
}
```

**Key properties**:

| Property | Value |
|---|---|
| Filter size | 64 bits (fits in a single CPU register) |
| Hash functions | k = 3 (MurmurHash3-inspired double hashing) |
| Bits per tag | 3 |
| Match operation | `(record & query) == query` (containment check) |
| Cost | **1 CPU cycle** (single `long` read + bitwise AND) |

### False Positive Rates

| Tags per Record | FPR | Assessment |
|---|---|---|
| 5 tags | 0.03% | Excellent — 1 false match per 3,000 records |
| 10 tags | 0.2% | Excellent — 1 false match per 500 records |
| 20 tags | 2.3% | Good — vector distance rejects false matches |
| 50 tags | 12% | Acceptable — still useful for coarse gating |

!!! tip "System vs. Record Tags"
    The system can have **thousands** of unique tag strings. But any single record should have at most **10-50 tags** for the Bloom filter to remain effective. This is a natural fit — a single memory rarely has more than 5-15 contextual associations.

### Usage in Recall

```java
// At query time: encode query tags into a mask
RecallOptions options = RecallOptions.builder()
    .synapticFilter("java", "debugging", "performance")
    .build();
// options.synapticTagMask() is now a 64-bit Bloom filter with 9 bits set (3 per tag)

// In CognitiveScorer hot-loop (Phase 2):
long recordTags = segment.get(LAYOUT_SYNAPTIC_TAGS, offset + OFFSET_SYNAPTIC_TAGS);
if ((recordTags & queryTagMask) != queryTagMask) continue; // Not a match
```

---

## CognitiveRecordLayout — Binary Format

The `CognitiveRecordLayout` class manages reading/writing the 32-byte header and quantized vector to/from off-heap `MemorySegment`:

```java
public final class CognitiveRecordLayout {
    private final int quantizedVecBytes;
    
    // Stride = header + vector, aligned to 32 bytes
    public int stride() {
        return HEADER_BYTES + quantizedVecBytes;
    }
    
    // Write header to segment at given offset
    public void writeHeader(MemorySegment segment, long offset, CognitiveHeader header) {
        segment.set(LAYOUT_TIMESTAMP, offset + OFFSET_TIMESTAMP, header.timestampMs());
        segment.set(LAYOUT_SYNAPTIC_TAGS, offset + OFFSET_SYNAPTIC_TAGS, header.synapticTags());
        segment.set(LAYOUT_EXACT_NORM, offset + OFFSET_EXACT_NORM, header.exactNorm());
        segment.set(LAYOUT_IMPORTANCE, offset + OFFSET_IMPORTANCE, header.importance());
        // ... remaining fields
    }
    
    // Read individual fields
    public long readSynapticTags(MemorySegment segment, long offset) {
        return segment.get(LAYOUT_SYNAPTIC_TAGS, offset + OFFSET_SYNAPTIC_TAGS);
    }
    
    // Merge tags (bitwise OR for co-activation)
    public void mergeSynapticTags(MemorySegment segment, long offset, long additionalTags) {
        long existing = readSynapticTags(segment, offset);
        segment.set(LAYOUT_SYNAPTIC_TAGS, offset + OFFSET_SYNAPTIC_TAGS, existing | additionalTags);
    }
}
```

### CognitiveHeader Record

The header data is represented as a Java `record` for type safety:

```java
public record CognitiveHeader(
    long timestampMs,
    long synapticTags,
    float exactNorm,
    float importance,
    int centroidId,
    short recallCount,
    byte valence,
    byte flags
) {}
```

---

## DecayStrategy — SIMD-Friendly Temporal Decay

!!! warning "The `exp()` Problem"
    The naive decay formula `Math.exp(-λ·age)` costs 50-100ns per call and is a **scalar operation** — it cannot be SIMD-vectorized. At 1M memories, this adds 50-100ms of pure overhead, destroying the SIMD advantage.

### The Solution: Precomputed Decay Buckets

`DecayStrategy` quantizes time into discrete buckets and uses a precomputed lookup table:

```java
// Precomputed — zero Math.exp() calls at query time
private static final float[] DECAY_TABLE = {
    1.00f,  // Bucket 0: 0-1 hours
    0.95f,  // Bucket 1: 1-6 hours
    0.85f,  // Bucket 2: 6-24 hours
    0.70f,  // Bucket 3: 1-3 days
    0.50f,  // Bucket 4: 3-7 days
    0.30f,  // Bucket 5: 1-2 weeks
    0.15f,  // Bucket 6: 2-4 weeks
    0.05f,  // Bucket 7: 1-3 months
    0.01f   // Bucket 8+: 3+ months
};

public static float decay(int bucket) {
    return DECAY_TABLE[Math.min(bucket, DECAY_TABLE.length - 1)];
}
```

### Reconsolidation Adjustment

Every 3 recalls shifts the bucket back by 1, simulating Long-Term Potentiation:

```java
public static int adjustForReconsolidation(int rawBucket, short recallCount) {
    return Math.max(0, rawBucket - (recallCount / 3));
}
```

A memory recalled 12 times is 4 buckets "younger" than its actual age — it resists forgetting.

---

## Next Steps

- :material-head-cog: [**Dopamine — Surprise Detection**](dopamine.md) — auto-importance scoring
- :material-brain: [**Cortex — Tier Stores**](cortex.md) — the 4-tier architecture
- :material-lightning-bolt: [**6-Phase Scoring Pipeline**](scoring-pipeline.md) — how scoring uses the header
