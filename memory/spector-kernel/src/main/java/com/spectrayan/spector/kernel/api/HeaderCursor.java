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
package com.spectrayan.spector.kernel.api;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.FloatUnaryOperator;
import com.spectrayan.spector.kernel.error.StaleRegionException;

/**
 * Thread-confined, reusable cursor over memory record headers (spec R6, design §4.5).
 *
 * <p>Pins the underlying region generation at creation. Calling {@link #seek(int)}
 * validates that no concurrent resize occurred and repositions the cursor.
 * Exposes atomic and delta operations for recall reinforcement (R6.6, R6.8) and plain
 * accessors restricted to single-threaded batch initialization (R6.7).</p>
 */
public interface HeaderCursor extends AutoCloseable {

    /**
     * Positions this cursor at the given record slot index.
     *
     * @param slot zero-based record slot index
     * @return this cursor positioned at the requested slot
     * @throws StaleRegionException if the region was grown or remapped concurrently
     * @throws IndexOutOfBoundsException if slot is negative or exceeds region bounds
     * @throws IllegalStateException if this cursor has been closed
     */
    HeaderCursor seek(int slot);

    /**
     * Positions this cursor at the given raw byte offset within the region segment.
     *
     * @param byteOffset byte offset from the start of the region segment
     * @return this cursor positioned at the requested offset
     * @throws StaleRegionException if the region was grown or remapped concurrently
     * @throws IndexOutOfBoundsException if offset is negative or exceeds region bounds
     * @throws IllegalStateException if this cursor has been closed
     */
    HeaderCursor seekOffset(long byteOffset);

    /**
     * Returns the currently positioned slot index, or -1 if positioned via arbitrary offset.
     */
    int currentSlot();

    /**
     * Returns the current byte offset within the underlying region.
     */
    long currentOffset();

    // ── Plain reads (EncodingHeaderFields entries) ──

    byte headerVersion();

    byte flags();
    void flags(byte flags);

    byte valence();

    byte arousal();
    void arousal(byte arousal);

    float importance();

    /**
     * Plain importance write restricted to single-threaded batch initialization ONLY (R6.7).
     * On the concurrent recall/reinforce path, use {@link #updateImportance(FloatUnaryOperator)}.
     */
    void initializeImportance(float importance);

    long timestampMs();
    void timestampMs(long timestampMs);

    float exactNorm();
    void exactNorm(float exactNorm);

    short centroidId();
    void centroidId(short centroidId);

    long synapticTagsLo();
    long synapticTagsHi();

    /**
     * Plain synaptic tags write restricted to single-threaded batch initialization ONLY (R6.7).
     * On concurrent paths, use {@link #mergeSynapticTags(long, long)}.
     */
    void initializeSynapticTags(long lo, long hi);

    byte consolidationFlags();
    void consolidationFlags(byte flags);

    byte encodingProfile();
    void encodingProfile(byte profile);

    byte encodingAlpha();
    void encodingAlpha(byte alpha);

    byte encodingBeta();
    void encodingBeta(byte beta);

    short soulVersion();
    void soulVersion(short soulVersion);

    byte sourceCode();
    void sourceCode(byte sourceCode);

    EngramSource source();
    void source(EngramSource source);

    float encodingSurprise();
    void encodingSurprise(float surprise);

    // ── Flags helper queries / mutations ──

    boolean isTombstoned();
    void tombstone();

    boolean isPinned();
    void pin();

    boolean isResolved();
    void markResolved();
    void markUnresolved();

    boolean isConsolidated();
    void markConsolidated();

    boolean isContradicted();
    void markContradicted();

    SourceModality sourceModality();
    void sourceModality(SourceModality modality);

    // ── Episodic honest fields ──

    long sessionId();
    void sessionId(long sessionId);

    short modelId();
    void modelId(short modelId);

    byte role();
    void role(byte role);

    int payloadBytes();

    boolean isOptionBRecord();

    /**
     * Reads the session ID from payload metadata if header session ID is zero.
     *
     * @param payloadBytes payload length in bytes
     * @return session ID from payload metadata or 0L if unavailable
     */
    default long fallbackSessionId(int payloadBytes) {
        return 0L;
    }

    // ── Mutable telemetry & strength accessors ──

    int activationCount();

    /**
     * Plain activation counter setter restricted to single-threaded batch initialization ONLY (R6.7).
     * On concurrent recall paths, use {@link #addActivationCount(int)}.
     */
    void activationCount(int count);

    long lastAccessEpochMs();
    void lastAccessEpochMs(long timestampMs);

    float storageStrength();

    /**
     * Plain storage strength setter restricted to single-threaded batch initialization ONLY (R6.7).
     * On concurrent paths, use {@link #updateStorageStrength(FloatUnaryOperator)}.
     */
    void storageStrength(float strength);

    int spectorRecallCount();
    void spectorRecallCount(int count);

    // ── Mandatory atomic and delta operations (R6.6, R6.8) ──

    /**
     * Atomically adds delta to the activation / recall counter.
     *
     * @param delta amount to increment (typically 1)
     * @return updated activation count
     */
    int addActivationCount(int delta);

    /**
     * Conditionally updates valence if current value equals expected.
     *
     * @param expected expected current valence
     * @param update   new valence value
     * @return true if successful
     */
    boolean compareAndSetValence(byte expected, byte update);

    /**
     * Atomically updates importance via CAS retry loop.
     *
     * @param fn update operator
     * @return updated importance value
     */
    float updateImportance(FloatUnaryOperator fn);

    /**
     * Atomically merges synaptic tags into the 128-bit Bloom filter using bitwise OR.
     *
     * @param lo low 64 bits to merge
     * @param hi high 64 bits to merge
     */
    void mergeSynapticTags(long lo, long hi);

    /**
     * Writes valence with release memory ordering.
     *
     * @param v new valence
     */
    void valenceRelease(byte v);

    /**
     * Atomically updates storage strength via CAS retry loop.
     *
     * @param fn update operator
     * @return updated storage strength
     */
    float updateStorageStrength(FloatUnaryOperator fn);

    /**
     * Records an ACT-R recall timestamp into the 8-slot ring buffer.
     *
     * @param creationMs memory creation epoch ms
     * @param recallMs   current recall epoch ms
     */
    void recordActRRecall(long creationMs, long recallMs);

    /**
     * Reads the 8-slot ACT-R recall relative timestamps.
     */
    int[] readActRTimestamps();

    /**
     * Computes the normalized ACT-R base-level activation in [0.0, 1.0], or -1.0f if empty.
     */
    float computeActRActivation(long creationMs, long nowMs);

    /**
     * Decodes the full immutable EncodingHeader snapshot at the current slot.
     */
    EncodingHeader readHeader();

    /**
     * Releases this cursor, dropping the pinned generation and region lease (R6.3).
     */
    @Override
    void close();
}
