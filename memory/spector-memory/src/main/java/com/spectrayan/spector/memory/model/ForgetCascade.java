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
package com.spectrayan.spector.memory.model;

/**
 * What {@code forget} does to memories that were derived from the forgotten one.
 *
 * <p>A memory is <i>derived</i> when its provenance points back at the forgotten memory: a semantic
 * fact reflected from an episodic turn, or a merged memory whose source was the forgotten one. The
 * walk is transitive, so a merge of a derived fact is derived too.</p>
 *
 * @see ForgetResult#derivedIds()
 */
public enum ForgetCascade {

    /** Leave derived memories untouched; only report them in {@link ForgetResult#derivedIds()}. */
    REPORT,

    /**
     * Mark every derived memory as retracted. Retracted memories stay on disk and keep their lineage,
     * but recall never releases them. This is the default.
     */
    FLAG,

    /**
     * Tombstone derived memories that came from the forgotten memory alone. A derived memory with
     * other sources as well (for example a fact distilled from several turns) is flagged as retracted
     * instead, since forgetting one input does not invalidate the others.
     */
    TOMBSTONE
}
