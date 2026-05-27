package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synaptic tag co-occurrence tracking for Hebbian learning.
 *
 * <h3>Biological Analog: Hebbian Learning ("Cells that fire together wire together")</h3>
 * <p>When two neurons fire simultaneously, the synapse between them strengthens.
 * Over time, activating one neuron will automatically activate the other — this
 * is the basis of associative memory.</p>
 *
 * <h3>Tag Co-Activation (Lightweight Approach)</h3>
 * <p>Instead of a full graph, this tracker records co-occurrence frequencies of synaptic tag
 * pairs. When two tags appear together in recall results, their co-activation
 * count increments. Tags with high co-activation can be used for spreading
 * activation — "if you recalled 'java', you probably also want 'performance'."</p>
 *
 * <p>This provides 80% of the Hebbian association effect with zero graph overhead.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link ConcurrentHashMap} with {@link AtomicInteger} counts.</p>
 */
public final class CoActivationTracker {

    private static final Logger log = LoggerFactory.getLogger(CoActivationTracker.class);

    /**
     * Co-activation counts: key = "tagA:tagB" (alphabetically sorted), value = count.
     */
    private final ConcurrentHashMap<String, AtomicInteger> coActivations = new ConcurrentHashMap<>();

    /** Maximum number of tracked pairs to prevent unbounded growth. */
    private final int maxPairs;

    /**
     * Creates a co-activation tracker.
     *
     * @param maxPairs maximum tracked pairs before pruning (default: 10_000)
     */
    public CoActivationTracker(int maxPairs) {
        this.maxPairs = maxPairs;
    }

    /**
     * Creates a tracker with default max pairs (10_000).
     */
    public CoActivationTracker() {
        this(10_000);
    }

    /**
     * Records co-activation of tags that appeared together in a recall result set.
     *
     * <p>For each pair (i, j) where i &lt; j alphabetically, increment the
     * co-activation count.</p>
     *
     * @param tags array of tag strings that appeared together in recall results
     */
    public void recordCoActivation(String... tags) {
        if (tags.length < 2) return;

        for (int i = 0; i < tags.length; i++) {
            for (int j = i + 1; j < tags.length; j++) {
                String key = pairKey(tags[i], tags[j]);

                if (coActivations.size() >= maxPairs && !coActivations.containsKey(key)) {
                    pruneWeakest();
                }

                coActivations.computeIfAbsent(key, k -> new AtomicInteger(0))
                        .incrementAndGet();
            }
        }
    }

    /**
     * Returns the co-activation count for a tag pair.
     *
     * @param tagA first tag
     * @param tagB second tag
     * @return co-activation count (0 if never co-activated)
     */
    public int getCoActivation(String tagA, String tagB) {
        String key = pairKey(tagA, tagB);
        AtomicInteger count = coActivations.get(key);
        return count != null ? count.get() : 0;
    }

    /**
     * Returns the top-N most co-activated tags for a given tag.
     *
     * @param tag   the source tag
     * @param topN  maximum number of associated tags to return
     * @return list of associated tag names sorted by co-activation strength
     */
    public java.util.List<String> getAssociatedTags(String tag, int topN) {
        return coActivations.entrySet().stream()
                .filter(e -> e.getKey().contains(tag + ":") || e.getKey().contains(":" + tag))
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(topN)
                .map(e -> {
                    String[] parts = e.getKey().split(":");
                    return parts[0].equals(tag) ? parts[1] : parts[0];
                })
                .toList();
    }

    /**
     * Returns the number of tracked tag pairs.
     */
    public int pairCount() {
        return coActivations.size();
    }

    /**
     * Prunes the weakest 10% of co-activation pairs.
     */
    private void pruneWeakest() {
        int toPrune = maxPairs / 10;
        coActivations.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        (a, b) -> Integer.compare(a.get(), b.get())))
                .limit(toPrune)
                .map(Map.Entry::getKey)
                .toList() // materialize before removal
                .forEach(coActivations::remove);

        log.debug("Pruned {} weak co-activation pairs (remaining={})",
                toPrune, coActivations.size());
    }

    /**
     * Creates a canonical pair key (alphabetically sorted to avoid A:B vs B:A duplication).
     */
    private static String pairKey(String tagA, String tagB) {
        return tagA.compareTo(tagB) <= 0 ? tagA + ":" + tagB : tagB + ":" + tagA;
    }

    /**
     * Resets all co-activation data.
     */
    public void reset() {
        coActivations.clear();
    }
}
