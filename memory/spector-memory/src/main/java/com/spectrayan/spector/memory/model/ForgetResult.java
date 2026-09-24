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
 * Outcome of a {@code forget} call.
 *
 * <p>Exists so a caller can tell the difference between "this memory was tombstoned" and "there was no such
 * memory". Before issue #983, {@code forget} logged a warning and returned normally when the id was absent
 * from the index, and the MCP tool reported {@code "has been forgotten (tombstoned)"} either way — a false
 * confirmation on a deletion path.</p>
 *
 * <h3>Why a result rather than an exception</h3>
 *
 * <p>Idempotent delete is a contract three production callers depend on, so throwing on a missing id was
 * rejected:</p>
 * <ul>
 *   <li>{@code SpectorVectorStore.delete(List)} — Spring AI's {@code VectorStore} delete contract</li>
 *   <li>{@code SpectorChatMemoryRepository} and {@code SpectorMemoryChatAdapter} — both bulk-forget over
 *       {@code browse} results, where an id can legitimately disappear between the browse and the forget</li>
 * </ul>
 * <p>{@code NegativeTestingE2ETest} also asserts a second forget does not throw. The caller can now branch
 * on {@link #found()} without any of those breaking.</p>
 *
 * <h3>What forget does not do</h3>
 *
 * <p>Forget is <b>logical</b>: it sets a tombstone flag. Payload bytes remain in the memory-mapped store and
 * in every snapshot and DR export taken since. It does not consult legal hold — that is enforced only at
 * namespace granularity in the catalog plane. Physical erasure with byte zeroing, edge removal and
 * record-level legal hold arrives with the {@code purge} verb in
 * {@code spectrayan/.kiro/specs/memory-durability-contract} R1.</p>
 *
 * @param id       the memory id that was requested
 * @param found    whether the memory existed and was tombstoned. When {@code false} nothing changed
 * @param tombstonedAtEpochMs when the tombstone was applied, or {@code 0} if {@code found} is {@code false}
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
public record ForgetResult(String id, boolean found, long tombstonedAtEpochMs) {

    /**
     * The memory existed and has been tombstoned.
     *
     * @param id the memory id
     * @return a result with {@code found = true}
     */
    public static ForgetResult tombstoned(String id) {
        return new ForgetResult(id, true, System.currentTimeMillis());
    }

    /**
     * No memory with this id is present in the index, so nothing was changed.
     *
     * @param id the memory id
     * @return a result with {@code found = false}
     */
    public static ForgetResult notFound(String id) {
        return new ForgetResult(id, false, 0L);
    }
}
