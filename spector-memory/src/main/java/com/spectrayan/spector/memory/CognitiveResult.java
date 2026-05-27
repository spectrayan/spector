package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.MemorySource;

/**
 * Immutable result record returned by {@link SpectorMemory#recall}.
 *
 * <p>Contains the memory text, cognitive scoring metadata, provenance information,
 * and biological state (recall count, valence, decay factor). Designed to give
 * the LLM maximum contextual grounding for reasoning about memory reliability.</p>
 *
 * @param id            unique memory identifier
 * @param text          the memory content text
 * @param score         final fused cognitive score (similarity × decay × importance)
 * @param importance    base importance weight (auto-set by Prediction Error engine)
 * @param ageDays       age of the memory in days
 * @param recallCount   number of times this memory has been recalled (LTP/reconsolidation)
 * @param valence       emotional valence (-128 to +127)
 * @param memoryType    cognitive memory tier (Working, Episodic, Semantic, Procedural)
 * @param source        provenance source (Observed, UserStated, Reflected, etc.)
 * @param synapticTags  decoded synaptic tag labels
 * @param decayFactor   raw decay multiplier (before reconsolidation adjustment)
 * @param ltpAdjustedDecay decay multiplier after reconsolidation adjustment
 */
public record CognitiveResult(
        String id,
        String text,
        float score,
        float importance,
        float ageDays,
        short recallCount,
        byte valence,
        MemoryType memoryType,
        MemorySource source,
        String[] synapticTags,
        float decayFactor,
        float ltpAdjustedDecay
) {

    /**
     * Returns the confidence weight based on source monitoring.
     */
    public float confidenceWeight() {
        return source != null ? source.confidenceWeight() : 0.5f;
    }

    /**
     * Returns true if this memory has been positively reinforced (valence > 10).
     */
    public boolean isPositivelyReinforced() {
        return valence > 10;
    }

    /**
     * Returns true if this memory is associated with a negative outcome (valence < -10).
     */
    public boolean isNegativeOutcome() {
        return valence < -10;
    }
}
