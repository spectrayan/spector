package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.MemoryType;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

/**
 * Small persistent store for procedural memory — prompt templates and tool-usage rules.
 *
 * <h3>Biological Analog: Basal Ganglia</h3>
 * <p>The basal ganglia stores procedural / motor memory — "how to do things" rather
 * than "what happened." These are habits, skills, and automatic routines that
 * don't require conscious recall.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Extends {@link AbstractTierStore} for common Arena/layout/segment lifecycle</li>
 *   <li>Small store (typically &lt;1000 records)</li>
 *   <li>High importance, low TTL — designed for microsecond lookups</li>
 *   <li>Linear append (no eviction — throws when full)</li>
 *   <li>Flat scan with {@code CognitiveScorer}</li>
 * </ul>
 */
public final class ProceduralMemoryStore extends AbstractTierStore {

    private static final Logger log = LoggerFactory.getLogger(ProceduralMemoryStore.class);

    /**
     * Creates a new Procedural Memory store.
     *
     * @param quantizedVecBytes bytes per quantized vector
     * @param capacity          maximum number of procedural memories (default: 1000)
     */
    public ProceduralMemoryStore(int quantizedVecBytes, int capacity) {
        super(quantizedVecBytes, capacity,
                (long) new com.spectrayan.spector.memory.synapse.CognitiveRecordLayout(quantizedVecBytes).stride() * capacity);

        log.info("ProceduralMemoryStore initialized: capacity={}, stride={}B",
                capacity, layout.stride());
    }

    /**
     * Creates a Procedural Memory store with default capacity (1000).
     */
    public ProceduralMemoryStore(int quantizedVecBytes) {
        this(quantizedVecBytes, 1000);
    }

    @Override
    public MemoryType type() {
        return MemoryType.PROCEDURAL;
    }

    @Override
    public long write(CognitiveHeader header, byte[] quantizedVec) {
        long offset = (long) count * layout.stride();
        append(header, quantizedVec);
        return offset;
    }

    /**
     * Appends a procedural memory.
     *
     * @param header       cognitive header
     * @param quantizedVec quantized vector bytes
     */
    public synchronized void append(CognitiveHeader header, byte[] quantizedVec) {
        if (count >= capacity) {
            throw new IllegalStateException("ProceduralMemoryStore full (capacity=" + capacity + ")");
        }

        long offset = (long) count * layout.stride();
        layout.writeHeader(segment, offset, header);
        MemorySegment.copy(
                MemorySegment.ofArray(quantizedVec), 0,
                segment, layout.vectorOffset(offset),
                quantizedVec.length
        );
        count++;
    }
}
