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
package com.spectrayan.spector.memory.neuromod.neurodivergent;

/**
 * @deprecated Use {@link RememberHints} instead.
 */
@Deprecated(forRemoval = true)
public record IngestionHints(RememberHints delegate) {

    public IngestionHints(float interest, float challenge, float urgency, byte valence, byte arousal) {
        this(new RememberHints(interest, challenge, urgency, valence, arousal));
    }

    public IngestionHints(float interest, float challenge, float urgency) {
        this(new RememberHints(interest, challenge, urgency));
    }

    public static final IngestionHints NONE = new IngestionHints(RememberHints.NONE);

    public float interest() { return delegate.interest(); }
    public float challenge() { return delegate.challenge(); }
    public float urgency() { return delegate.urgency(); }
    public byte valence() { return delegate.valence(); }
    public byte arousal() { return delegate.arousal(); }
    public boolean isEmpty() { return delegate.isEmpty(); }
    public boolean hasEmotionalContext() { return delegate.hasEmotionalContext(); }
    public byte effectiveArousal() { return delegate.effectiveArousal(); }
}
