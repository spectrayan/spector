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
import java.util.Set;
import java.util.function.Supplier;
import com.spectrayan.spector.commons.security.SpectorScopes;

import io.modelcontextprotocol.spec.McpSchema;

import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;

/**
 * MCP tool: {@code memory_remember} — store a memory with full cognitive metadata.
 *
 * <p>Supports all 4 memory tiers (WORKING, EPISODIC, SEMANTIC, PROCEDURAL),
 * ICNU importance hints (Interest, Challenge, Urgency — Novelty is computed
 * natively), and emotional context (valence + arousal).</p>
 *
 * <p>All cognitive parameters are optional for backward compatibility.
 * When omitted, the memory is stored as SEMANTIC with novelty-only importance.</p>
 *
 * <p>Maps to {@link SpectorMemory#remember} with optional {@link IngestionHints}.</p>
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MemoryRememberTool extends MemoryToolHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryRememberTool.class);
    public static final String NAME = "memory_remember";

    public MemoryRememberTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryRememberTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String id = optionalString(args, "id", "");
        String text = requireString(args, "text");
        String[] tags = optionalTags(args, "tags");
        String sourceName = optionalString(args, "source", "OBSERVED");
        String tierName = optionalString(args, "tier", "SEMANTIC");

        // Parse tier
        MemoryType type;
        try {
            type = MemoryType.valueOf(tierName.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = MemoryType.SEMANTIC;
        }

        // Parse source
        MemorySource source;
        try {
            source = MemorySource.valueOf(sourceName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source = MemorySource.OBSERVED;
        }

        // Parse ICNU hints + emotional context
        float interest = optionalFloat(args, "interest", 0f);
        float challenge = optionalFloat(args, "challenge", 0f);
        float urgency = optionalFloat(args, "urgency", 0f);
        int valence = optionalInt(args, "valence", 0);
        int arousal = optionalInt(args, "arousal", 0);

        // Build IngestionHints (if any cognitive params were provided)
        IngestionHints hints = null;
        if (interest > 0 || challenge > 0 || urgency > 0 || valence != 0 || arousal != 0) {
            hints = new IngestionHints(interest, challenge, urgency,
                    (byte) Math.clamp(valence, -128, 127),
                    (byte) Math.clamp(arousal, 0, 255));
        }

        // Build IngestionContext with metadata + hints
        var ctxBuilder = IngestionContext.builder();
        if (hints != null) {
            ctxBuilder.hints(hints);
        }

        // Parse metadata JSON (if provided)
        String metadataJson = optionalString(args, "metadata", "");
        if (!metadataJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> raw = new tools.jackson.databind.ObjectMapper()
                        .readValue(metadataJson, java.util.Map.class);
                for (var entry : raw.entrySet()) {
                    ctxBuilder.metadata(entry.getKey(), String.valueOf(entry.getValue()));
                }
            } catch (Exception e) {
                // Non-fatal: metadata parsing failure shouldn't block ingestion
                log.warn("[MemoryRememberTool] Metadata parsing failed (proceeding with text-only ingestion): {}", e.getMessage());
            }
        }

        IngestionContext context = ctxBuilder.build();

        // Ingest: auto-generate ID if not provided
        boolean autoId = id.isEmpty();
        if (autoId) {
            if (context.hasMetadata()) {
                // Multimodal: use context-aware auto-ID path
                id = memory.remember(text, type, source, context, tags);
            } else {
                // Text-only: hints-only path (backward compatible)
                id = memory.remember(text, type, source, hints, tags);
            }
        } else {
            memory.remember(id, text, type, source, context, tags);
        }

        // Retrieve stored cognitive record for importance feedback (zero extra embedding calls)
        com.spectrayan.spector.memory.model.CognitiveRecord storedRecord = null;
        try {
            storedRecord = memory.inspect(id);
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append("✅ Stored ").append(type).append(" memory '").append(id).append("'");
        if (autoId) sb.append(" (auto-generated TSID)");
        if (tags.length > 0) sb.append(" with ").append(tags.length).append(" tags");
        sb.append(" (source=").append(source).append(")");

        if (hints != null) {
            sb.append("\n📊 ICNU: I=").append(interest).append(", C=").append(challenge)
              .append(", U=").append(urgency);
            if (valence != 0) sb.append(" | valence=").append(valence);
            if (arousal != 0) sb.append(" | arousal=").append(arousal);
        }

        // Show computed importance feedback from stored record
        if (storedRecord != null) {
            sb.append(String.format("\n📈 Importance: %.2f / 10.0", storedRecord.importance()));
            if (storedRecord.isPinned()) {
                sb.append(" ⚡ FLASHBULB (Pinned)");
            }
        }

        return textResult(sb.toString());
    }
}
