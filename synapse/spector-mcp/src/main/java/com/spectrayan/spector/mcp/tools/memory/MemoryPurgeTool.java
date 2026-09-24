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
package com.spectrayan.spector.mcp.tools.memory;

import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_purge} — physically destroy a memory's content by ID. Irreversible.
 *
 * <p>Deliberately a separate tool from {@code memory_forget} rather than a flag on it. The two do genuinely
 * different things — one hides, one destroys — and an agent choosing between them should have to name which
 * it means. A boolean parameter on a deletion tool is the kind of thing a model sets by accident.</p>
 *
 * <p>The response reports what was destroyed and what could not be reached. It does not say "purged" and stop
 * there: copies in DR exports, replica disks and backups survive, and an agent relaying a deletion
 * confirmation to a user should be relaying that too.</p>
 */
public final class MemoryPurgeTool extends MemoryToolHandler {

    public static final String NAME = "memory_purge";

    public MemoryPurgeTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryPurgeTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String memoryId = requireString(args, "memory_id");
        var result = memory.purge(memoryId);

        if (!result.found()) {
            return textResult("⚠️ No memory found with id '" + memoryId + "'. Nothing was purged.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔥 Memory '").append(memoryId).append("' was PURGED — this is irreversible.\n\n");
        sb.append("Destroyed in this store:\n");
        sb.append("  • ").append(result.payloadBytesZeroed()).append(" payload bytes overwritten with zeros\n");
        if (result.textBytesZeroed() > 0) {
            sb.append("  • ").append(result.textBytesZeroed()).append(" text bytes overwritten with zeros\n");
        } else if (result.inlineTextDropped()) {
            sb.append("  • stored text dropped (this store keeps no on-disk text copy)\n");
        }
        sb.append("  • ").append(result.graphReferencesRemoved())
                .append(" graph references removed (")
                .append(result.hebbianEdgesRemoved()).append(" Hebbian, ")
                .append(result.temporalUnlinked() ? 1 : 0).append(" temporal, ")
                .append(result.entityLinksRemoved()).append(" entity, ")
                .append(result.hyperedgesRemoved()).append(" hyperedge)\n");

        if (result.hasLocalRetention()) {
            // The one case where the local erasure is genuinely incomplete. Saying "purged" without this
            // would be false.
            sb.append("\n⚠️ TEXT RETAINED: identical text is shared with ")
                    .append(result.textSharedWith())
                    .append(" other live record(s) through content deduplication, so those bytes could not "
                            + "be erased without destroying the other record(s)' text.\n");
        }

        sb.append("\nNot reached by this operation: ")
                .append(String.join(", ", result.unreachableCopies()))
                .append(".\nOn-disk space is not reclaimed by a purge — that is what vacuum does.");
        return textResult(sb.toString());
    }
}
