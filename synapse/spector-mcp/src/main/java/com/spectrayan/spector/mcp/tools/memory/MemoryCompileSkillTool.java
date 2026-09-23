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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillReport;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP tool: {@code memory_compile_skill} — compile, test, or dry-run a procedural skill
 * from episodic turns or semantic facts (ADR-0086 §5.6, §7 Phase 5).
 */
public final class MemoryCompileSkillTool extends MemoryToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String NAME = "memory_compile_skill";

    public MemoryCompileSkillTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryCompileSkillTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        boolean commit = Boolean.TRUE.equals(args.get("commit"));
        String cue = (String) args.get("cue");

        SkillSignal.Builder builder = SkillSignal.builder()
                .mode(commit ? SkillSignal.Mode.COMPILE : SkillSignal.Mode.DRY_RUN)
                .commit(commit)
                .cue(cue);

        if (args.get("parents") instanceof List<?> parentList) {
            for (Object item : parentList) {
                if (item != null) {
                    String pid = item.toString().trim();
                    MemoryType type = pid.startsWith("sem-") || pid.startsWith("fact-")
                            ? MemoryType.SEMANTIC
                            : MemoryType.EPISODIC;
                    builder.parent(pid, type);
                }
            }
        }

        if (args.get("parent_texts") instanceof List<?> textList) {
            for (Object item : textList) {
                if (item != null) {
                    builder.parentText(item.toString());
                }
            }
        }

        SkillSignal signal = builder.build();
        SkillReport report = memory.compileSkill(signal);

        Map<String, Object> response = new HashMap<>();
        response.put("mode", report.mode() != null ? report.mode().name() : (commit ? "COMPILE" : "DRY_RUN"));
        response.put("skillId", report.skillId());
        response.put("duplicateOf", report.duplicateOf());
        response.put("reinforced", report.reinforced());
        if (report.extractedBody() != null) {
            response.put("serializedSkill", report.extractedBody().serialize());
            if (report.extractedBody().hasMeta()) {
                var meta = report.extractedBody().meta();
                Map<String, Object> metaMap = new HashMap<>();
                metaMap.put("name", meta.name());
                metaMap.put("kind", meta.kind().name().toLowerCase());
                metaMap.put("confidence", meta.confidence());
                metaMap.put("tools", meta.tools());
                metaMap.put("parents", meta.parents());
                response.put("metadata", metaMap);
            }
        }

        return textResult(MAPPER.writeValueAsString(response));
    }
}
