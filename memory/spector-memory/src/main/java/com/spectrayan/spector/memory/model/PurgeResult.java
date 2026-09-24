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

import java.time.Instant;
import java.util.List;

/**
 * Record-level audit report for a {@code purge}, in the same idiom as
 * {@code ErasureAuditReport}: what mechanism ran, what it covered, and — explicitly — what it did not.
 *
 * <p>The disclosure fields are the point of this type, not decoration. "Purged" is a strong word and the
 * operation does not earn all of it: a purge overwrites the bytes in <b>this</b> store, and has no reach into
 * DR exports, replica disks, cold-tier objects or filesystem snapshots. The namespace-level equivalent
 * already takes the position that an erasure which leaves the DR export in place has not erased anything, it
 * has erased the fast copy. That applies identically at record granularity, so the report says so rather than
 * letting a caller infer completeness from a success flag.</p>
 *
 * <p>Deliberately no crypto-erase field or language. No data-encryption key exists in this system, so any
 * claim of cryptographic erasure would be false.</p>
 *
 * @param memoryId              the record's id
 * @param namespaceId           the namespace it lived in
 * @param found                 whether the record existed; everything below is zero when false
 * @param purgedAt              when the purge ran
 * @param payloadBytesZeroed    vector payload bytes overwritten with zeros
 * @param textBytesZeroed       off-heap text bytes overwritten with zeros in {@code text.dat}; zero when
 *                              the store keeps no off-heap text copy, which is not the same as text
 *                              surviving — see {@code inlineTextDropped}
 * @param inlineTextDropped     whether an on-heap text copy held by the index was dropped. In-memory stores
 *                              keep text only here, so this is the whole erasure for them; disk stores keep
 *                              both copies and need both cleared
 * @param textRetainedShared    text bytes left in place because identical content is deduplicated with
 *                              another live record; see {@link #textSharedWith}
 * @param textSharedWith        number of other live records sharing the retained text bytes
 * @param hebbianEdgesRemoved   Hebbian association edges dropped
 * @param temporalUnlinked      whether the record was spliced out of the temporal chain
 * @param entityLinksRemoved    entity→memory adjacency references dropped
 * @param hyperedgesRemoved     hyperedges derived from this record that were deleted
 * @param walRecorded           whether the purge was written to the WAL, making it survive a crash
 * @param retainedHeaderFields  header fields deliberately left intact, so the erasure stays auditable
 * @param unreachableCopies     classes of copy this operation cannot reach at all
 * @param disclosure            plain-language statement of what was and was not achieved
 */
public record PurgeResult(
        String memoryId,
        String namespaceId,
        boolean found,
        Instant purgedAt,
        int payloadBytesZeroed,
        int textBytesZeroed,
        boolean inlineTextDropped,
        int textRetainedShared,
        int textSharedWith,
        int hebbianEdgesRemoved,
        boolean temporalUnlinked,
        int entityLinksRemoved,
        int hyperedgesRemoved,
        boolean walRecorded,
        List<String> retainedHeaderFields,
        List<String> unreachableCopies,
        String disclosure
) {

    /** What the purge actually does to the bytes. */
    public static final String MECHANISM = "IN_PLACE_ZERO_OVERWRITE_AND_GRAPH_DETACH";

    /**
     * Header fields a purge deliberately leaves intact.
     *
     * <p>A record with no trace at all cannot be audited — there would be no evidence that anything was ever
     * there, nor that it was deliberately destroyed. None of these can reconstruct content.</p>
     */
    public static final List<String> RETAINED_HEADER_FIELDS = List.of(
            "header_version",
            "flags (tombstone, purged, memory type, modality)",
            "timestamp_ms",
            "importance",
            "valence",
            "arousal",
            "encoding_profile",
            "encoding_alpha",
            "encoding_beta",
            "soul_version"
    );

    /**
     * Copies this operation cannot reach.
     *
     * <p>Every entry is a place the purged content may still exist. Listing them is the difference between an
     * honest report and a false one.</p>
     */
    public static final List<String> UNREACHABLE_COPIES = List.of(
            "dr_exports",
            "replica_disks",
            "cold_tier_objects",
            "filesystem_snapshots_and_backups",
            "wal_segments_prior_to_the_last_checkpoint",
            "os_page_cache_and_ssd_wear_levelling_remnants"
    );

    /**
     * The disclosure text accompanying every purge.
     *
     * <p>Says plainly what the operation achieved and what it did not, so nobody has to infer completeness
     * from the absence of a warning.</p>
     */
    public static final String DISCLOSURE_TEXT =
            "Purge overwrote the record's payload with zeros in this store and detached it from the "
            + "association graphs. It does NOT reach DR exports, replica disks, cold-tier objects, "
            + "filesystem snapshots or backups, or WAL segments written before the last checkpoint; copies "
            + "in any of those survive and must be handled separately. No cryptographic erasure was "
            + "performed — no data-encryption key exists in this system, so no such claim is made. The "
            + "record's lifecycle metadata (existence, formation time, importance) is retained on purpose so "
            + "that the erasure itself remains auditable. On-disk space is not reclaimed by this operation; "
            + "that is compaction's job.";

    /** A purge of a record that did not exist. Not an error — delete is idempotent by contract. */
    public static PurgeResult notFound(String memoryId, String namespaceId) {
        return new PurgeResult(memoryId, namespaceId, false, Instant.now(),
                0, 0, false, 0, 0,
                0, false, 0, 0, false,
                List.of(), UNREACHABLE_COPIES, DISCLOSURE_TEXT);
    }

    /**
     * Returns whether the record's text was removed from every copy this store holds.
     *
     * <p>True when off-heap bytes were zeroed, or when the only copy was on-heap and was dropped. Distinguish
     * this from {@code textBytesZeroed() > 0}: an in-memory store legitimately zeroes no bytes because it
     * never wrote any, and reading a zero there as "the text survived" would be wrong in the other
     * direction.</p>
     */
    public boolean textDestroyed() {
        return textRetainedShared == 0 && (textBytesZeroed > 0 || inlineTextDropped);
    }

    /** Total graph references removed across all four planes. */
    public int graphReferencesRemoved() {
        return hebbianEdgesRemoved + (temporalUnlinked ? 1 : 0) + entityLinksRemoved + hyperedgesRemoved;
    }

    /**
     * Returns whether any content bytes were left behind in this store.
     *
     * <p>True when the record's text was deduplicated with another live record's identical text, which makes
     * the text physically unremovable without destroying the co-tenant's copy. A caller reporting to a data
     * subject needs to know this; it is the one case where "purged" is materially incomplete even locally.</p>
     */
    public boolean hasLocalRetention() {
        return textRetainedShared > 0;
    }
}
