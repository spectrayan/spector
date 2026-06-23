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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.BigFiveTraits;
import com.spectrayan.spector.memory.model.CommunicationStyle;
import com.spectrayan.spector.memory.model.CulturalIdentity;
import com.spectrayan.spector.memory.model.EmotionalIntelligence;
import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.StressResponse;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_salience} — manage the active salience profile.
 *
 * <p>Allows agents to get, set, and compute salience profiles at runtime.
 * This enables dynamic personality and interest configuration without
 * restarting the memory system.</p>
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@code get} — Returns the current SalienceProfile as JSON</li>
 *   <li>{@code set} — Sets a new SalienceProfile from provided parameters</li>
 *   <li>{@code compute_boost} — Computes topic + self-relevance boost for text</li>
 *   <li>{@code add_interest} — Adds an interest domain to the active profile</li>
 *   <li>{@code add_disinterest} — Adds a disinterest domain to the active profile</li>
 *   <li>{@code set_persona} — Sets PersonaContext on the active profile</li>
 * </ul>
 */
public final class MemorySalienceTool extends MemoryToolHandler {

    public MemorySalienceTool(SpectorMemory memory) {
        super(memory);
    }

    @Override public String name() { return "memory_salience"; }

    @Override public Set<String> requiredScopes() {
        return Set.of(SpectorScopes.MEMORY_WRITE);
    }

    @Override
    public String description() {
        return "Manage the active salience profile — controls what matters to the agent. "
                + "Operations: 'get' (view current profile), 'set' (replace profile), "
                + "'compute_boost' (preview importance boost for text), "
                + "'add_interest' (add interest topic), 'add_disinterest' (add dampened topic), "
                + "'set_persona' (set personality/identity context). "
                + "Use this to personalize memory scoring for different users or agents.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                .requiredString("operation",
                        "Operation to perform: get, set, compute_boost, add_interest, "
                        + "add_disinterest, set_persona.")
                .optionalString("text",
                        "Text to compute boost for (required for compute_boost).", "")
                .optionalString("topic",
                        "Interest/disinterest topic in natural language "
                        + "(required for add_interest, add_disinterest).", "")
                .optionalString("level",
                        "Interest level: CRITICAL, HIGH, MEDIUM, LOW, IGNORE "
                        + "(for add_interest/add_disinterest).", "HIGH")
                .optionalString("occupation",
                        "Occupation text for set_persona.", "")
                .optionalString("about",
                        "Self-description/bio for set_persona.", "")
                .optionalInt("openness",
                        "Big Five Openness (0-100) for set_persona.", 50)
                .optionalInt("conscientiousness",
                        "Big Five Conscientiousness (0-100) for set_persona.", 50)
                .optionalInt("extraversion",
                        "Big Five Extraversion (0-100) for set_persona.", 50)
                .optionalInt("agreeableness",
                        "Big Five Agreeableness (0-100) for set_persona.", 50)
                .optionalInt("neuroticism",
                        "Big Five Neuroticism (0-100) for set_persona.", 50)
                .optionalString("stress_response",
                        "Stress response: ADAPTIVE, FIGHT, FLIGHT, FREEZE, FAWN (for set_persona).", "ADAPTIVE")
                .optionalString("icnu_interest",
                        "ICNU Interest weight (0.0-1.0) for set operation.", "")
                .optionalString("icnu_challenge",
                        "ICNU Challenge weight (0.0-1.0) for set operation.", "")
                .optionalString("icnu_novelty",
                        "ICNU Novelty weight (0.0-1.0) for set operation.", "")
                .optionalString("icnu_urgency",
                        "ICNU Urgency weight (0.0-1.0) for set operation.", "")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       SpectorEngine engine,
                                                       Map<String, Object> args) throws Exception {
        String operation = requireString(args, "operation").strip().toLowerCase();

        return switch (operation) {
            case "get" -> handleGet(memory);
            case "set" -> handleSet(memory, args);
            case "compute_boost" -> handleComputeBoost(memory, args);
            case "add_interest" -> handleAddInterest(memory, args, true);
            case "add_disinterest" -> handleAddInterest(memory, args, false);
            case "set_persona" -> handleSetPersona(memory, args);
            default -> textResult("Unknown operation: '" + operation
                    + "'. Valid: get, set, compute_boost, add_interest, add_disinterest, set_persona.");
        };
    }

    private McpSchema.CallToolResult handleGet(SpectorMemory memory) {
        SalienceProfile profile = memory.salienceProfile();
        if (profile == null || profile.isNeutral()) {
            return textResult("🧠 Salience Profile: NEUTRAL (no personalization active)\n"
                    + "Use 'set', 'add_interest', or 'set_persona' to configure.");
        }

        var sb = new StringBuilder();
        sb.append("🧠 Active Salience Profile\n\n");

        if (!profile.interests().isEmpty()) {
            sb.append("Interests:\n");
            for (InterestDomain d : profile.interests()) {
                sb.append("  • ").append(d.topic()).append(" → ").append(d.level())
                        .append(" (").append(String.format("%.1f×", d.level().multiplier())).append(")\n");
            }
        }

        if (!profile.disinterests().isEmpty()) {
            sb.append("Disinterests:\n");
            for (InterestDomain d : profile.disinterests()) {
                sb.append("  • ").append(d.topic()).append(" → ").append(d.level())
                        .append(" (").append(String.format("%.1f×", d.level().multiplier())).append(")\n");
            }
        }

        if (profile.hasIcnuOverride()) {
            IcnuWeights w = profile.icnuWeights();
            sb.append("\nICNU Weights: I=").append(w.interest())
                    .append(" C=").append(w.challenge())
                    .append(" N=").append(w.novelty())
                    .append(" U=").append(w.urgency()).append("\n");
        }

        if (profile.hasPersona()) {
            PersonaContext p = profile.persona();
            sb.append("\nPersona:\n");
            if (p.occupation() != null) sb.append("  Occupation: ").append(p.occupation()).append("\n");
            if (!p.bigFive().isNeutral()) {
                sb.append("  BigFive: O=").append(p.bigFive().openness())
                        .append(" C=").append(p.bigFive().conscientiousness())
                        .append(" E=").append(p.bigFive().extraversion())
                        .append(" A=").append(p.bigFive().agreeableness())
                        .append(" N=").append(p.bigFive().neuroticism()).append("\n");
            }
            sb.append("  Stress: ").append(p.stressResponse()).append("\n");
            sb.append("  Embeddings: ").append(p.hasEmbeddings()).append("\n");
        }

        sb.append("\nConfig: flashbulb=").append(profile.flashbulbThreshold())
                .append(", recency=").append(profile.recencyWeight())
                .append(", simThreshold=").append(profile.similarityThreshold());

        return textResult(sb.toString());
    }

    private McpSchema.CallToolResult handleSet(SpectorMemory memory, Map<String, Object> args) {
        var builder = SalienceProfile.builder();

        // Parse ICNU weights if provided
        float icnuI = optionalFloat(args, "icnu_interest", -1f);
        float icnuC = optionalFloat(args, "icnu_challenge", -1f);
        float icnuN = optionalFloat(args, "icnu_novelty", -1f);
        float icnuU = optionalFloat(args, "icnu_urgency", -1f);
        if (icnuI >= 0 || icnuC >= 0 || icnuN >= 0 || icnuU >= 0) {
            builder.icnuWeights(new IcnuWeights(
                    icnuI >= 0 ? icnuI : 0.25f,
                    icnuC >= 0 ? icnuC : 0.15f,
                    icnuN >= 0 ? icnuN : 0.35f,
                    icnuU >= 0 ? icnuU : 0.25f));
        }

        SalienceProfile profile = builder.build();
        memory.setSalienceProfile(profile);

        return textResult("✅ Salience profile set successfully."
                + (profile.hasIcnuOverride() ? " ICNU weights overridden." : ""));
    }

    private McpSchema.CallToolResult handleComputeBoost(SpectorMemory memory, Map<String, Object> args) {
        String text = optionalString(args, "text", "");
        if (text.isBlank()) {
            return textResult("❌ 'text' is required for compute_boost operation.");
        }

        float topicBoost = memory.computeTopicBoost(text);
        float selfBoost = memory.computeSelfRelevanceBoost(text);
        float combinedBoost = topicBoost * selfBoost;

        var sb = new StringBuilder();
        sb.append("🔍 Salience Boost Preview\n\n");
        sb.append("Text: \"").append(text.length() > 100 ? text.substring(0, 100) + "..." : text).append("\"\n\n");
        sb.append("Topic Boost:          ").append(String.format("%.4f", topicBoost));
        if (topicBoost > 1.0f) sb.append(" ⬆ (interest match)");
        else if (topicBoost < 1.0f) sb.append(" ⬇ (disinterest match)");
        else sb.append(" — (neutral)");
        sb.append("\n");

        sb.append("Self-Relevance Boost: ").append(String.format("%.4f", selfBoost));
        if (selfBoost > 1.0f) sb.append(" ⬆ (persona match)");
        else if (selfBoost < 1.0f) sb.append(" ⬇ (persona mismatch)");
        else sb.append(" — (no persona)");
        sb.append("\n");

        sb.append("Combined Boost:       ").append(String.format("%.4f", combinedBoost)).append("\n");
        sb.append("\nThis memory's importance would be multiplied by ")
                .append(String.format("%.2f×", combinedBoost)).append(" during ingestion.");

        return textResult(sb.toString());
    }

    private McpSchema.CallToolResult handleAddInterest(SpectorMemory memory,
                                                         Map<String, Object> args,
                                                         boolean isInterest) {
        String topic = optionalString(args, "topic", "");
        if (topic.isBlank()) {
            return textResult("❌ 'topic' is required for "
                    + (isInterest ? "add_interest" : "add_disinterest") + " operation.");
        }

        String levelStr = optionalString(args, "level", "HIGH");
        InterestLevel level;
        try {
            level = InterestLevel.valueOf(levelStr.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return textResult("❌ Invalid level: '" + levelStr
                    + "'. Valid: CRITICAL, HIGH, MEDIUM, LOW, IGNORE.");
        }

        // Rebuild profile with the new interest/disinterest added
        SalienceProfile current = memory.salienceProfile();
        var builder = SalienceProfile.builder();

        // Carry over existing interests
        for (InterestDomain d : current.interests()) {
            builder.interest(d);
        }
        for (InterestDomain d : current.disinterests()) {
            builder.disinterest(d);
        }

        // Carry over existing config
        if (current.hasIcnuOverride()) builder.icnuWeights(current.icnuWeights());
        if (current.alpha() != null) builder.alpha(current.alpha());
        if (current.beta() != null) builder.beta(current.beta());
        if (current.defaultProfile() != null) builder.defaultProfile(current.defaultProfile());
        builder.flashbulbThreshold(current.flashbulbThreshold());
        builder.recencyWeight(current.recencyWeight());
        builder.similarityThreshold(current.similarityThreshold());
        if (current.hasPersona()) builder.persona(current.persona());

        // Add the new domain
        if (isInterest) {
            builder.interest(topic, level);
        } else {
            builder.disinterest(topic, level);
        }

        memory.setSalienceProfile(builder.build());

        String label = isInterest ? "Interest" : "Disinterest";
        return textResult("✅ " + label + " added: \"" + topic + "\" → " + level
                + " (" + String.format("%.1f×", level.multiplier()) + " importance modifier)");
    }

    private McpSchema.CallToolResult handleSetPersona(SpectorMemory memory, Map<String, Object> args) {
        String occupation = optionalString(args, "occupation", "");
        String about = optionalString(args, "about", "");
        int o = optionalInt(args, "openness", 50);
        int c = optionalInt(args, "conscientiousness", 50);
        int e = optionalInt(args, "extraversion", 50);
        int a = optionalInt(args, "agreeableness", 50);
        int n = optionalInt(args, "neuroticism", 50);
        String stressStr = optionalString(args, "stress_response", "ADAPTIVE");

        StressResponse stress;
        try {
            stress = StressResponse.valueOf(stressStr.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            stress = StressResponse.ADAPTIVE;
        }

        var personaBuilder = PersonaContext.builder()
                .bigFive(new BigFiveTraits(o, c, e, a, n))
                .stressResponse(stress);

        if (!occupation.isBlank()) personaBuilder.occupation(occupation);
        if (!about.isBlank()) personaBuilder.about(about);

        PersonaContext persona = personaBuilder.build();

        // Rebuild profile with persona
        SalienceProfile current = memory.salienceProfile();
        var builder = SalienceProfile.builder();
        for (InterestDomain d : current.interests()) builder.interest(d);
        for (InterestDomain d : current.disinterests()) builder.disinterest(d);
        if (current.hasIcnuOverride()) builder.icnuWeights(current.icnuWeights());
        if (current.alpha() != null) builder.alpha(current.alpha());
        if (current.beta() != null) builder.beta(current.beta());
        if (current.defaultProfile() != null) builder.defaultProfile(current.defaultProfile());
        builder.flashbulbThreshold(current.flashbulbThreshold());
        builder.recencyWeight(current.recencyWeight());
        builder.similarityThreshold(current.similarityThreshold());
        builder.persona(persona);

        memory.setSalienceProfile(builder.build());

        var sb = new StringBuilder();
        sb.append("✅ Persona set successfully.\n\n");
        if (!occupation.isBlank()) sb.append("Occupation: ").append(occupation).append("\n");
        sb.append("BigFive: O=").append(o).append(" C=").append(c)
                .append(" E=").append(e).append(" A=").append(a)
                .append(" N=").append(n).append("\n");
        sb.append("Stress Response: ").append(stress).append("\n");
        sb.append("\nNote: Persona embeddings are computed lazily on first ingestion. "
                + "For immediate embedding, re-ingest a memory after setting persona.");

        return textResult(sb.toString());
    }
}
