/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
