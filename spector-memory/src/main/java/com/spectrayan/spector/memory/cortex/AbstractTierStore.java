package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Abstract base class for single-segment tier stores.
 *
 * <h3>Template Method Pattern</h3>
 * <p>Provides common infrastructure shared by {@link WorkingMemoryStore},
 * {@link SemanticMemoryStore}, and {@link ProceduralMemoryStore}:</p>
 * <ul>
 *   <li>Arena lifecycle (shared Arena for thread-safe access)</li>
 *   <li>Layout creation from vector byte count</li>
 *   <li>Capacity tracking and size reporting</li>
 *   <li>Segment allocation with 32-byte alignment</li>
 *   <li>Close/cleanup lifecycle</li>
 * </ul>
 *
 * <p>{@link EpisodicMemoryStore} implements {@link TierStore} directly because
 * it uses mmap-backed partitions rather than a single Arena-allocated segment.</p>
 *
 * @see TierStore for the common interface
 */
public abstract class AbstractTierStore implements TierStore {

    private static final Logger log = LoggerFactory.getLogger(AbstractTierStore.class);

    protected final CognitiveRecordLayout layout;
    protected final int capacity;
    protected final Arena arena;
    protected final MemorySegment segment;
    protected int count = 0;

    /**
     * Allocates a single contiguous off-heap segment for the store.
     *
     * @param quantizedVecBytes bytes per quantized vector
     * @param capacity          maximum number of records
     * @param segmentBytes      total bytes to allocate (caller decides header-only vs full)
     */
    protected AbstractTierStore(int quantizedVecBytes, int capacity, long segmentBytes) {
        this.layout = new CognitiveRecordLayout(quantizedVecBytes);
        this.capacity = capacity;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(segmentBytes, SynapticHeaderConstants.HEADER_BYTES);
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public CognitiveRecordLayout layout() {
        return layout;
    }

    @Override
    public MemorySegment primarySegment() {
        return segment;
    }

    /**
     * Returns the maximum capacity of this store.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the backing memory segment for direct scorer access.
     */
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public void close() {
        log.info("{} closing ({} records)", getClass().getSimpleName(), count);
        arena.close();
    }
}
