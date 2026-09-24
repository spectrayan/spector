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
package com.spectrayan.spector.memory.policy;

/**
 * Classifies what a deletion actually does to the bytes on disk.
 *
 * <p>A policy needs this distinction because the operations differ in reversibility, and a policy that
 * treated them alike would either block reversible operations needlessly or wave through irreversible
 * ones.</p>
 */
public enum DeletionKind {

    /**
     * Logical deletion only: a tombstone bit is set. Every content byte survives on disk and in backups, so
     * data under legal hold remains available for discovery.
     *
     * <p>Reversible in the sense that matters legally — nothing was destroyed.</p>
     */
    FORGET(false),

    /**
     * Physical destruction of one record's content: the payload is overwritten with zeros and graph edges
     * are dropped. Irreversible.
     */
    PURGE(true),

    /**
     * Compaction: reclaims space occupied by already-tombstoned records by relocating live ones.
     *
     * <p>Irreversible for the tombstoned records whose bytes are overwritten, which is why it is a deletion
     * at all rather than pure maintenance. Live records are moved, not destroyed.</p>
     */
    VACUUM(true),

    /**
     * Destruction of an entire namespace's data. Irreversible.
     */
    ERASE_NAMESPACE(true);

    private final boolean destructive;

    DeletionKind(boolean destructive) {
        this.destructive = destructive;
    }

    /**
     * Returns whether this operation destroys content bytes.
     *
     * <p>{@code false} only for {@link #FORGET}, which sets a bit and leaves the data in place. A policy
     * enforcing legal hold should refuse destructive operations; refusing {@code FORGET} as well is a
     * choice, not a requirement, because hidden-but-retained data is still retained.</p>
     */
    public boolean destructive() {
        return destructive;
    }
}
