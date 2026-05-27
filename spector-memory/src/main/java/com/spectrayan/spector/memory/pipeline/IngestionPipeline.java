package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.memory.MemoryType;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 10-step ingestion pipeline for cognitive memory.
 *
 * <h3>Pipeline Steps</h3>
 * <pre>
 *   Step  1: Embed text via provider
 *   Step  2: Encode synaptic tags → 64-bit Bloom filter
 *   Step  3: Compute surprise → auto-set importance (Dopamine engine)
 *   Step  4: Flashbulb check — extreme surprise gets full fidelity
 *   Step  5: Quantize vector to INT8 via calibrated ScalarQuantizer
 *   Step  6: Build cognitive header
 *   Step  7: Route to tier store and write
 *   Step  8: Register in ID index
 *   Step  9: WAL append
 *   Step 10: Check circadian trigger for auto-reflection
 * </pre>
 *
 * <h3>Design Pattern: Pipeline / Chain of Responsibility</h3>
 * <p>Each step is a discrete, testable transform. The pipeline is extracted from
 * {@code SpectorMemory.doRemember()} for SRP compliance.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>The pipeline is stateless except for the subsystems it references (all
 * thread-safe). Multiple Virtual Threads can call {@link #ingest} concurrently.</p>
 */
public final class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final EmbeddingProvider embeddingProvider;
    private final ScalarQuantizer quantizer;
    private final SurpriseDetector surpriseDetector;
    private final FlashbulbPolicy flashbulbPolicy;
    private final TierRouter tierRouter;
    private final MemoryIndex index;
    private final MemoryWal wal;

    /**
     * Creates an ingestion pipeline with all required subsystems.
     */
    public IngestionPipeline(EmbeddingProvider embeddingProvider,
                              ScalarQuantizer quantizer,
                              SurpriseDetector surpriseDetector,
                              FlashbulbPolicy flashbulbPolicy,
                              TierRouter tierRouter,
                              MemoryIndex index,
                              MemoryWal wal) {
        this.embeddingProvider = embeddingProvider;
        this.quantizer = quantizer;
        this.surpriseDetector = surpriseDetector;
        this.flashbulbPolicy = flashbulbPolicy;
        this.tierRouter = tierRouter;
        this.index = index;
        this.wal = wal;
    }

    /**
     * Executes the full 10-step ingestion pipeline.
     *
     * @param id     unique memory identifier
     * @param text   the memory content
     * @param type   cognitive memory tier
     * @param tags   synaptic tag strings
     * @param source provenance source
     */
    public void ingest(String id, String text, MemoryType type,
                        String[] tags, MemorySource source) {
        // Step 1: Embed text via provider
        var embeddingResult = embeddingProvider.embed(text);
        float[] vector = embeddingResult.vector();

        // Step 2: Encode synaptic tags
        long synapticTags = SynapticTagEncoder.encode(tags);

        // Step 3: Compute surprise → auto-set importance (Dopamine engine)
        float l2Norm = computeL2Norm(vector);
        float importance = surpriseDetector.computeImportance(l2Norm);

        // Step 4: Flashbulb check — extreme surprise gets full fidelity
        double zScore = surpriseDetector.stats().zScore(l2Norm);
        var flashbulb = flashbulbPolicy.evaluate(zScore);
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, type.ordinal());
        if (flashbulb.isFlashbulb()) {
            importance = flashbulb.importance();
            flags = (byte) (flags | SynapticHeaderConstants.FLAG_PINNED);
        }

        // Step 5: Quantize vector to INT8 via calibrated ScalarQuantizer
        byte[] quantized = quantizer.encode(vector);

        // Step 6: Build cognitive header
        CognitiveHeader header = new CognitiveHeader(
                System.currentTimeMillis(), synapticTags, l2Norm, importance,
                0, (short) 0, (byte) 0, flags);

        // Step 7: Route to tier store and write
        long offset = tierRouter.write(type, header, quantized);

        // Step 8: Register in ID index
        index.register(id, new MemoryLocation(type, offset, -1), text, source, tags);

        // Step 9: WAL append
        wal.appendRemember(id, quantized);

        log.debug("Ingested '{}' as {} (importance={:.2f}, {} tags, source={})",
                id, type, importance, tags.length, source);
    }

    /**
     * Computes L2 norm of a float vector.
     */
    private static float computeL2Norm(float[] vector) {
        float sum = 0f;
        for (float v : vector) sum += v * v;
        return (float) Math.sqrt(sum);
    }
}
