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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.EngramMemory;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Default implementation of {@link EngramMemory} providing polymorphic tier dispatch (R16.4).
 *
 * <p>Private fields follow the D11 vocabulary rule: {@code *Memory}, not {@code *Store}.</p>
 */
public class DefaultEngramMemory implements EngramMemory {

    protected final EnumMap<MemoryType, EngramRegion> memories = new EnumMap<>(MemoryType.class);

    protected final WorkingMemory workingMemory;
    protected final SemanticMemory semanticMemory;
    protected final ProceduralMemory proceduralMemory;
    protected final EpisodicMemory episodicMemory;
    protected final StrengthMemory strengthMemory;

    public DefaultEngramMemory(WorkingMemory workingMemory,
                               SemanticMemory semanticMemory,
                               ProceduralMemory proceduralMemory,
                               EpisodicMemory episodicMemory,
                               StrengthMemory strengthMemory) {
        this.workingMemory = workingMemory;
        this.semanticMemory = semanticMemory;
        this.proceduralMemory = proceduralMemory;
        this.episodicMemory = episodicMemory;
        this.strengthMemory = strengthMemory;

        if (workingMemory != null) memories.put(MemoryType.WORKING, workingMemory);
        if (semanticMemory != null) memories.put(MemoryType.SEMANTIC, semanticMemory);
        if (proceduralMemory != null) memories.put(MemoryType.PROCEDURAL, proceduralMemory);
        if (episodicMemory != null) memories.put(MemoryType.EPISODIC, episodicMemory);
    }

    public DefaultEngramMemory(WorkingMemory workingMemory,
                               SemanticMemory semanticMemory,
                               ProceduralMemory proceduralMemory,
                               EpisodicMemory episodicMemory) {
        this(workingMemory, semanticMemory, proceduralMemory, episodicMemory, null);
    }

    /**
     * Package-private accessor for internal region lookup.
     */

    @Override
    public com.spectrayan.spector.kernel.api.HeaderCursor cursor(MemoryType tier) {
        EngramRegion region = regionFor(tier);
        if (tier == MemoryType.EPISODIC && region instanceof EpisodicMemory em) {
            return em.cursor(strengthMemory);
        }
        if (region instanceof AbstractEngramMemory<?> aem) {
            return aem.cursor(strengthMemory);
        }
        throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "tier", "Cursor not supported for " + tier);
    }

    EngramRegion regionFor(MemoryType type) {
        EngramRegion region = memories.get(type);
        if (region == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "memoryType", type);
        }
        return region;
    }

    @Override
    public long write(MemoryType type, EncodingHeader header, byte[] quantized) {
        if (type == MemoryType.EPISODIC) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "type",
                    "Cannot route fixed-stride write to variable-length EPISODIC tier; use EpisodicMemory.appendTurn");
        }
        long offset = regionFor(type).write(header, quantized);
        if (strengthMemory != null && type != MemoryType.WORKING) {
            FixedEngramLayout layout = layoutFor(type);
            if (layout != null) {
                int slotIndex = (int) ((offset - regionFor(type).dataOffset()) / layout.stride());
                strengthMemory.initializeDefault(type, slotIndex, header.importance(), header.storageStrength(), header.agentRecallCount());
            }
        }
        return offset;
    }

    public FixedEngramLayout layoutFor(MemoryType type) {
        EngramRegion region = memories.get(type);
        return region != null && region.layout() instanceof FixedEngramLayout fel ? fel : null;
    }

    @Override
    public EncodingHeader readHeader(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        EngramRegion region = regionFor(loc.type());
        return region.readHeader(loc.offset());
    }

    @Override
    public void readVector(MemoryLocation loc, byte[] dest) {
        Objects.requireNonNull(loc, "loc cannot be null");
        Objects.requireNonNull(dest, "dest cannot be null");
        EngramRegion region = regionFor(loc.type());
        byte[] v = region.readVector(loc.offset());
        if (v != null) {
            System.arraycopy(v, 0, dest, 0, Math.min(v.length, dest.length));
        }
    }

    @Override
    public int countFor(MemoryType tier) {
        EngramRegion region = memories.get(tier);
        return region != null ? region.size() : 0;
    }

    @Override
    public int totalCount() {
        int total = 0;
        for (EngramRegion region : memories.values()) {
            if (region != null) {
                total += region.size();
            }
        }
        return total;
    }

    @Override
    public void tombstone(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicMemory != null) {
                episodicMemory.tombstone(loc.offset());
            }
            return;
        }
        EngramRegion region = memories.get(loc.type());
        if (region != null) {
            region.tombstone(loc.offset());
        }
        if (strengthMemory != null && loc.type() != MemoryType.WORKING && layoutFor(loc.type()) != null && region != null) {
            int slotIndex = (int) ((loc.offset() - region.dataOffset()) / layoutFor(loc.type()).stride());
            strengthMemory.resetRecord(loc.type(), slotIndex);
        }
    }

    @Override
    public boolean isTombstoned(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        if (loc.type() == MemoryType.EPISODIC) {
            return episodicMemory != null && episodicMemory.isTombstoned(loc.offset());
        }
        EngramRegion region = memories.get(loc.type());
        return region != null && region.isTombstoned(loc.offset());
    }

    @Override
    public void markResolved(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicMemory != null) {
                episodicMemory.markResolved(loc.offset());
            }
            return;
        }
        EngramRegion region = memories.get(loc.type());
        if (region != null) {
            region.markResolved(loc.offset());
        }
    }

    @Override
    public void markUnresolved(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicMemory != null) {
                episodicMemory.markUnresolved(loc.offset());
            }
            return;
        }
        EngramRegion region = memories.get(loc.type());
        if (region != null) {
            region.markUnresolved(loc.offset());
        }
    }

    @Override
    public void markContradicted(MemoryLocation loc) {
        Objects.requireNonNull(loc, "loc cannot be null");
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicMemory != null) {
                episodicMemory.markContradicted(loc.offset());
            }
            return;
        }
        EngramRegion region = memories.get(loc.type());
        if (region != null) {
            region.markContradicted(loc.offset());
        }
    }

    @Override
    public float nearestWorkingDistance(float[] vector, float[] mins, float[] scales) {
        if (workingMemory != null && workingMemory.visibleCount() > 0) {
            return workingMemory.nearestDistance(vector, mins, scales);
        }
        return -1.0f;
    }

    @Override
    public void force() {
        for (EngramRegion region : memories.values()) {
            if (region != null) {
                region.force();
            }
        }
        if (strengthMemory != null && strengthMemory.segment() != null) {
            strengthMemory.segment().force();
        }
    }

    @Override
    public void close() {
        force();
    }
}
