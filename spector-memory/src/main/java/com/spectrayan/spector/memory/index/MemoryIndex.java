package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.memory.MemoryType;
import com.spectrayan.spector.memory.cortex.MemorySource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized ID → metadata index for cognitive memories.
 *
 * <h3>Responsibility</h3>
 * <p>Owns the concurrent maps that track memory locations, raw text,
 * provenance sources, and synaptic tag strings. Provides O(1) lookup by ID
 * and O(1) reverse-lookup by offset (via dedicated reverse index).</p>
 *
 * <h3>Performance: O(1) Reverse Index</h3>
 * <p>A dedicated {@code reverseIndex} maps {@code (type, offset) → id} for
 * constant-time reverse lookups during recall result assembly. The key is
 * computed as {@code (type.ordinal() << 48) | offset}, packing both into
 * a single {@code long} to avoid String concatenation.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All maps are {@link ConcurrentHashMap} — safe for concurrent ingestion
 * (Virtual Threads) and recall (parallel scans).</p>
 */
public final class MemoryIndex {

    /**
     * Tracks where a memory is physically stored.
     *
     * @param type            cognitive tier
     * @param offset          byte offset within the tier's segment
     * @param partitionIndex  partition index (episodic only, -1 otherwise)
     */
    public record MemoryLocation(MemoryType type, long offset, int partitionIndex) {}

    // ── Forward index: id → metadata ──
    private final ConcurrentHashMap<String, MemoryLocation> locations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> texts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MemorySource> sources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String[]> tags = new ConcurrentHashMap<>();

    // ── Reverse index: (type, offset) → id  [O(1) lookup for recall result assembly] ──
    private final ConcurrentHashMap<Long, String> reverseIndex = new ConcurrentHashMap<>();

    /**
     * Computes the reverse-index key from a memory type and byte offset.
     *
     * <p>Packs type ordinal into the upper 16 bits and offset into the lower 48 bits.
     * This supports offsets up to 256 TB per tier — far beyond any practical limit.</p>
     */
    private static long reverseKey(MemoryType type, long offset) {
        return ((long) type.ordinal() << 48) | (offset & 0x0000_FFFF_FFFF_FFFFL);
    }

    /**
     * Registers a new memory in the index.
     *
     * <p>Maintains both forward (id → metadata) and reverse ((type, offset) → id)
     * indexes for O(1) lookups in both directions.</p>
     *
     * @param id       unique memory identifier
     * @param location physical storage location
     * @param text     raw text content
     * @param source   provenance source
     * @param tagArray synaptic tag strings
     */
    public void register(String id, MemoryLocation location, String text,
                          MemorySource source, String[] tagArray) {
        locations.put(id, location);
        texts.put(id, text);
        sources.put(id, source);
        tags.put(id, tagArray);

        // O(1) reverse index
        reverseIndex.put(reverseKey(location.type(), location.offset()), id);
    }

    /**
     * Removes a memory from both forward and reverse indexes.
     */
    public void remove(String id) {
        MemoryLocation loc = locations.remove(id);
        texts.remove(id);
        sources.remove(id);
        tags.remove(id);

        // Clean reverse index
        if (loc != null) {
            reverseIndex.remove(reverseKey(loc.type(), loc.offset()));
        }
    }

    /**
     * Returns the physical location for a memory ID, or null if not found.
     * O(1) via ConcurrentHashMap.
     */
    public MemoryLocation locate(String id) {
        return locations.get(id);
    }

    /**
     * Returns the raw text for a memory ID, or empty string if not found.
     */
    public String text(String id) {
        return texts.getOrDefault(id, "");
    }

    /**
     * Returns the provenance source for a memory ID.
     */
    public MemorySource source(String id) {
        return sources.getOrDefault(id, MemorySource.OBSERVED);
    }

    /**
     * Returns the synaptic tag strings for a memory ID.
     */
    public String[] tags(String id) {
        return tags.getOrDefault(id, new String[0]);
    }

    /**
     * O(1) reverse-lookup: finds the memory ID stored at a given offset in a given tier.
     *
     * <p>Uses a dedicated reverse index ({@code ConcurrentHashMap<Long, String>})
     * instead of the previous O(n) linear scan over the location map.</p>
     *
     * @param type   memory tier to search
     * @param offset byte offset to match
     * @return the memory ID, or null if not found
     */
    public String findIdByOffset(MemoryType type, long offset) {
        return reverseIndex.get(reverseKey(type, offset));
    }

    /**
     * Returns the text for a memory stored at a given offset.
     * O(1) via reverse index.
     */
    public String findTextByOffset(MemoryType type, long offset) {
        String id = findIdByOffset(type, offset);
        return id != null ? texts.get(id) : null;
    }

    /**
     * Returns the total number of indexed memories.
     */
    public int size() {
        return locations.size();
    }

    /**
     * Returns the raw location map (for iteration in decay, etc.).
     */
    public ConcurrentHashMap<String, MemoryLocation> locationMap() {
        return locations;
    }
}
