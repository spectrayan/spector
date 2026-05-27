package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.memory.CognitiveResult;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;

import java.util.List;

/**
 * Hebbian co-activation listener — records tag co-occurrence from recall results.
 *
 * <h3>Biological Analog: Hebbian Learning</h3>
 * <p>"Cells that fire together wire together." When multiple memories are recalled
 * together, their synaptic tags form co-activation pairs. Over time, recalling
 * one tag automatically surfaces associated tags — spreading activation.</p>
 *
 * <h3>Design Pattern: Observer</h3>
 * <p>Previously hardcoded in SpectorMemory.recall() Step 8, now a standalone
 * listener registered with {@link RecallPipeline#addListener}.</p>
 */
public final class HebbianCoActivationListener implements RecallListener {

    private final CoActivationTracker tracker;

    public HebbianCoActivationListener(CoActivationTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void onRecallComplete(List<CognitiveResult> results) {
        if (results.size() < 2) return;

        String[] resultTags = results.stream()
                .flatMap(r -> r.synapticTags() != null
                        ? java.util.Arrays.stream(r.synapticTags())
                        : java.util.stream.Stream.<String>empty())
                .distinct()
                .limit(10)
                .toArray(String[]::new);

        if (resultTags.length >= 2) {
            tracker.recordCoActivation(resultTags);
        }
    }
}
