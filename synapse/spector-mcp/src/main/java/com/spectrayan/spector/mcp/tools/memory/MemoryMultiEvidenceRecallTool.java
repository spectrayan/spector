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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.RecallOptions;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_multi_evidence_recall} — surfaces competing hypothesis clusters and action policies.
 */
public final class MemoryMultiEvidenceRecallTool extends MemoryToolHandler {

    public static final String NAME = "memory_multi_evidence_recall";

    public MemoryMultiEvidenceRecallTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryMultiEvidenceRecallTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String query = requireString(args, "query");
        String subject = optionalString(args, "subject", "");
        String predicate = optionalString(args, "predicate", "");
        int topK = optionalInt(args, "top_k", 5);

        var sb = new StringBuilder();
        sb.append("**Multi-Evidence Cognitive Distribution**\n");
        sb.append("**Query:** `").append(query).append("`\n\n");

        FactHistory factHistory = null;
        if (!subject.isBlank() && !predicate.isBlank()) {
            factHistory = memory.factHistory(subject, predicate);
        }

        // If subject/predicate not specified or empty history, try extracting candidate subject from query
        if (factHistory == null && subject.isBlank()) {
            String[] tokens = query.split("\\s+");
            for (String token : tokens) {
                String clean = token.replaceAll("[^a-zA-Z0-9_]", "");
                if (clean.length() >= 3 && Character.isUpperCase(clean.charAt(0))) {
                    try {
                        FactHistory fh = memory.factHistory(clean, "*");
                        if (fh != null && (fh.activeFact() != null || !fh.supersededFacts().isEmpty())) {
                            factHistory = fh;
                            break;
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
        }

        RecallOptions options = RecallOptions.builder()
                .topK(topK)
                .build();
        List<CognitiveResult> recalled = memory.recall(query, options);

        if (factHistory != null && (factHistory.activeFact() != null || !factHistory.supersededFacts().isEmpty())) {
            sb.append("### Bitemporal Evidence Chain for `").append(factHistory.subject())
              .append("` --[").append(factHistory.predicate()).append("]-->\n");

            float activeConf = factHistory.activeFact() != null ? factHistory.activeFact().confidence() : 0.0f;
            float maxSupersededConf = 0.0f;
            for (FactHistory.FactSnapshot s : factHistory.supersededFacts()) {
                if (s.confidence() > maxSupersededConf) {
                    maxSupersededConf = s.confidence();
                }
            }

            if (factHistory.activeFact() != null) {
                sb.append("- **Consensus Fact:** `").append(factHistory.activeFact().object())
                  .append("` (confidence: ").append(String.format("%.2f", activeConf)).append(")\n");
            }
            for (FactHistory.FactSnapshot s : factHistory.supersededFacts()) {
                sb.append("- **Alternative / Historical:** `").append(s.object())
                  .append("` (confidence: ").append(String.format("%.2f", s.confidence()))
                  .append(", factId: #").append(s.factId()).append(")\n");
            }

            // Conflict classification & recommended action policy
            String policy;
            String rationale;
            float delta = Math.abs(activeConf - maxSupersededConf);

            if (activeConf < 0.3f && maxSupersededConf < 0.3f) {
                policy = "ABSTAIN";
                rationale = "All candidate evidence confidence is below reliability threshold (0.30).";
            } else if (factHistory.hasConflict()) {
                policy = "ASK_CLARIFYING_QUESTION";
                rationale = String.format("Conflicting hypotheses share high confidence spread (delta_conf = %.2f). Clarification advised.", delta);
            } else if (!factHistory.supersededFacts().isEmpty()) {
                policy = "PRESENT_ALTERNATIVES";
                rationale = "Temporal evolution detected. Present historical transitions alongside consensus.";
            } else {
                policy = "ACCEPT_WINNER";
                rationale = "Single dominant consensus fact without unresolved contradictions.";
            }

            sb.append("\n**Recommended Action Policy:** `").append(policy).append("`\n");
            sb.append("**Rationale:** ").append(rationale).append("\n\n");
        } else {
            sb.append("### Cognitive Memory Evidence Candidates\n");
            if (recalled.isEmpty()) {
                sb.append("_No matching memory traces found._\n\n");
                sb.append("**Recommended Action Policy:** `ABSTAIN`\n");
            } else {
                for (int i = 0; i < recalled.size(); i++) {
                    CognitiveResult r = recalled.get(i);
                    sb.append(i + 1).append(". [").append(r.memoryType()).append("] `").append(r.text())
                      .append("` (score: ").append(String.format("%.2f", r.score()))
                      .append(", conf: ").append(String.format("%.2f", r.ltpAdjustedDecay())).append(")\n");
                }
                sb.append("\n**Recommended Action Policy:** `ACCEPT_WINNER`\n");
            }
        }

        return textResult(sb.toString());
    }
}
