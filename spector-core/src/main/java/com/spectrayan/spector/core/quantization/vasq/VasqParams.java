package com.spectrayan.spector.core.quantization.vasq;

import java.util.Arrays;

/**
 * Immutable calibration parameters for the VASQ quantizer.
 *
 * <p>Produced by {@link VasqCalibrator#calibrate} from a representative sample corpus.
 * Contains all parameters needed for encoding vectors and preparing query states.</p>
 *
 * <h3>Parameter Semantics</h3>
 * <p>All arrays are indexed over the <em>padded</em> dimension (length = {@link #paddedDim()}),
 * i.e. over the FWHT-rotated space. Statistics for padded dimensions beyond the original
 * dimension are near-zero (the FWHT distributes zero-padded values uniformly).</p>
 *
 * <ul>
 *   <li>{@link #means()} — per-dimension mean in rotated space (μᵢ)</li>
 *   <li>{@link #scales()} — per-dimension dequantization scale (σᵢ = clipSigmas·σᵢ/127)</li>
 *   <li>{@link #invScales()} — per-dimension quantization scale (1/σᵢ, precomputed for encode speed)</li>
 * </ul>
 *
 * <p>Instances are immutable and safe for concurrent use.</p>
 */
public final class VasqParams {

    /** Number of ±1 sign-flip seed to use when no explicit seed is provided. */
    public static final long DEFAULT_SEED = 42L;

    private final int originalDim;
    private final int paddedDim;
    private final float[] means;      // μᵢ per rotated dim  [paddedDim]
    private final float[] scales;     // scaleᵢ = clipSigmas·σᵢ/127  [paddedDim]
    private final float[] invScales;  // invScaleᵢ = 127/(clipSigmas·σᵢ)  [paddedDim]
    private final VasqFwht fwht;

    /**
     * Package-private constructor — created exclusively by {@link VasqCalibrator}.
     */
    VasqParams(int originalDim, int paddedDim,
               float[] means, float[] scales, float[] invScales,
               VasqFwht fwht) {
        this.originalDim = originalDim;
        this.paddedDim   = paddedDim;
        this.means       = means;
        this.scales      = scales;
        this.invScales   = invScales;
        this.fwht        = fwht;
    }

    /**
     * The original (unpadded) vector dimensionality.
     *
     * @return original dimension count
     */
    public int originalDim() { return originalDim; }

    /**
     * The FWHT-padded dimension (next power-of-two ≥ originalDim).
     *
     * @return padded dimension
     */
    public int paddedDim() { return paddedDim; }

    /**
     * Per-dimension means in the rotated space (μᵢ).
     *
     * <p><strong>Do not modify the returned array.</strong></p>
     *
     * @return means array of length {@link #paddedDim()}
     */
    public float[] means() { return means; }

    /**
     * Per-dimension dequantization scales (scaleᵢ = clipSigmas·σᵢ/127).
     *
     * <p>Used in query preparation: {@code q̃ᵢ = q_rot_i × scaleᵢ}.<br>
     * <strong>Do not modify the returned array.</strong></p>
     *
     * @return scales array of length {@link #paddedDim()}
     */
    public float[] scales() { return scales; }

    /**
     * Per-dimension quantization inverse-scales (invScaleᵢ = 127/(clipSigmas·σᵢ)).
     *
     * <p>Used in encoding: {@code zᵢ = round((x_rot_i - μᵢ) × invScaleᵢ)}.<br>
     * Precomputed to avoid division in the encode hot path.<br>
     * <strong>Do not modify the returned array.</strong></p>
     *
     * @return invScales array of length {@link #paddedDim()}
     */
    public float[] invScales() { return invScales; }

    /**
     * The FWHT rotator configured with this calibration's seed.
     *
     * @return FWHT instance
     */
    public VasqFwht fwht() { return fwht; }

    /**
     * Returns the number of bytes required to store one encoded vector in a MemorySegment:
     * {@code 4 (float32 exact norm header) + paddedDim (one signed INT8 per padded dim)}.
     *
     * @return bytes per vector
     */
    public int bytesPerVector() { return 4 + paddedDim; }

    @Override
    public String toString() {
        return String.format(
                "VasqParams{originalDim=%d, paddedDim=%d, bytesPerVector=%d}",
                originalDim, paddedDim, bytesPerVector());
    }
}
