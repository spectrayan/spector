package com.spectrayan.spector.memory;

/**
 * Cognitive memory type — determines physical routing and storage backend.
 *
 * <h3>Biological Analogs</h3>
 * <ul>
 *   <li>{@link #WORKING} — Prefrontal Cortex (short-term buffer, volatile RAM)</li>
 *   <li>{@link #EPISODIC} — Hippocampus (event sequences, time-partitioned mmap)</li>
 *   <li>{@link #SEMANTIC} — Neocortex (permanent facts, HNSW/VASQ indexed)</li>
 *   <li>{@link #PROCEDURAL} — Basal Ganglia (motor/habit memory, small indexed store)</li>
 * </ul>
 *
 * <p>The ordinal values (0–3) are encoded as 2 bits in the flags byte of the
 * {@link com.spectrayan.spector.memory.synapse.SynapticHeaderConstants synaptic header}.</p>
 */
public enum MemoryType {

    /**
     * Short-lived scratchpad for in-progress reasoning.
     * Backed by volatile {@code MemorySegment} Arena (RAM only, no mmap).
     * Auto-evicts via FIFO when capacity is reached.
     */
    WORKING,

    /**
     * High-volume, append-only event log.
     * Backed by time-partitioned mmap files.
     * Flat SIMD scan per partition (no HNSW — append-only is faster).
     */
    EPISODIC,

    /**
     * Permanent, deduplicated factual knowledge.
     * Backed by persistent mmap with HNSW/VASQ index.
     * Reuses existing {@code SpectorIndex} infrastructure.
     */
    SEMANTIC,

    /**
     * Prompt templates and tool-usage rules.
     * Small persistent store for microsecond lookups.
     * High importance, low TTL, indexed flat scan.
     */
    PROCEDURAL
}
