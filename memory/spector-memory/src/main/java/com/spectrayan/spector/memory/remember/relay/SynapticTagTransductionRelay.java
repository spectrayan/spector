/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.remember.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

/**
 * Transduces content and metadata tags into 64-bit synaptic Bloom filter bitmasks.
 */
public final class SynapticTagTransductionRelay implements SynapticRelay<RememberSignal> {

    private final TagExtractor tagExtractor;
    private final DataEncryptor encryptor;
    private final boolean normalizeAtIngest;

    public SynapticTagTransductionRelay(
            final TagExtractor tagExtractor,
            final DataEncryptor encryptor) {
        this(tagExtractor, encryptor, true);
    }

    public SynapticTagTransductionRelay(
            final TagExtractor tagExtractor,
            final DataEncryptor encryptor,
            final boolean normalizeAtIngest) {
        this.tagExtractor = tagExtractor;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.normalizeAtIngest = normalizeAtIngest;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (normalizeAtIngest && signal.vector() != null) {
            signal.normalizedVector(l2Normalize(signal.vector()));
        }

        String[] tags = signal.tags();
        if (tags == null && tagExtractor != null) {
            tags = tagExtractor.extract(signal.id(), signal.text());
            signal.tags(tags);
        }

        final long synapticTags = encodeTags(tags != null ? tags : new String[0]);
        signal.synapticTags(synapticTags);
        return true;
    }

    private static float[] l2Normalize(final float[] v) {
        float norm = com.spectrayan.spector.core.similarity.VectorOps.magnitude(v);
        if (norm == 0f || Math.abs(norm - 1.0f) < 1e-6f) {
            return v;
        }
        final float[] copy = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            copy[i] = v[i] / norm;
        }
        return copy;
    }

    @Override
    public String relayName() {
        return RelayNames.TAG_TRANSDUCTION;
    }

    private long encodeTags(final String[] tags) {
        if (encryptor.isEnabled()) {
            long filter = 0L;
            for (final String tag : tags) {
                filter |= encryptor.encodeTag(tag);
            }
            return filter;
        }
        return SynapticTagEncoder.encode(tags);
    }
}
