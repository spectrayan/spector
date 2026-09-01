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
import java.util.function.Supplier;

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
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
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
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

    public static final String NAME = "memory_salience";

    public MemorySalienceTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemorySalienceTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
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

        record InterestEntry(String topic, String level, float multiplier) {}

        List<InterestEntry> interestList = profile.interests().stream()
                .map(d -> new InterestEntry(d.topic(), d.level().name(), d.level().multiplier()))
                .toList();
        List<InterestEntry> disinterestList = profile.disinterests().stream()
                .map(d -> new InterestEntry(d.topic(), d.level().name(), d.level().multiplier()))
                .toList();

        var model = Map.<String, Object>ofEntries(
                Map.entry("profile", profile),
                Map.entry("hasInterests", !profile.interests().isEmpty()),
                Map.entry("interestsCount", profile.interests().size()),
                Map.entry("interests", interestList),
                Map.entry("hasDisinterests", !profile.disinterests().isEmpty()),
                Map.entry("disinterestsCount", profile.disinterests().size()),
                Map.entry("disinterests", disinterestList),
                Map.entry("hasIcnu", profile.hasIcnuOverride()),
                Map.entry("icnu", profile.hasIcnuOverride() ? profile.icnuWeights() : ""),
                Map.entry("hasPersona", profile.hasPersona()),
                Map.entry("persona", profile.hasPersona() ? profile.persona() : ""),
                Map.entry("personaHasBigFive", profile.hasPersona() && !profile.persona().bigFive().isNeutral()),
                Map.entry("hasAgentRelevanceBoost", profile.hasAgentRelevanceBoost())
        );

        return textResult(McpTemplateEngine.render("memory-salience-profile", model));
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

        // Parse agent relevance boost if provided
        float agentBoost = optionalFloat(args, "agent_relevance_boost", -1f);
        if (agentBoost > 0) {
            builder.agentRelevanceBoost(agentBoost);
        }

        SalienceProfile profile = builder.build();
        memory.setSalienceProfile(profile);

        return textResult("✅ Salience profile set successfully."
                + (profile.hasIcnuOverride() ? " ICNU weights overridden." : "")
                + (profile.hasAgentRelevanceBoost()
                        ? " Agent relevance boost: " + String.format("%.2f×", profile.agentRelevanceBoost())
                        : ""));
    }

    private McpSchema.CallToolResult handleComputeBoost(SpectorMemory memory, Map<String, Object> args) {
        String text = optionalString(args, "text", "");
        if (text.isBlank()) {
            return textResult("❌ 'text' is required for compute_boost operation.");
        }

        float topicBoost = memory.computeTopicBoost(text);
        float selfBoost = memory.computeSelfRelevanceBoost(text);
        float agentBoost = memory.salienceProfile() != null
                ? memory.salienceProfile().agentRelevanceBoost() : 1.0f;
        float combinedBoost = topicBoost * selfBoost * agentBoost;

        String topicIndicator = topicBoost > 1.0f ? " ⬆ (interest match)"
                : topicBoost < 1.0f ? " ⬇ (disinterest match)" : " — (neutral)";
        String selfIndicator = selfBoost > 1.0f ? " ⬆ (persona match)"
                : selfBoost < 1.0f ? " ⬇ (persona mismatch)" : " — (no persona)";
        String agentIndicator = agentBoost > 1.0f ? " ⬆ (expertise match)" : " — (no agent boost)";

        var model = Map.<String, Object>ofEntries(
                Map.entry("text", text),
                Map.entry("topicBoost", topicBoost),
                Map.entry("topicIndicator", topicIndicator),
                Map.entry("selfBoost", selfBoost),
                Map.entry("selfIndicator", selfIndicator),
                Map.entry("agentBoost", agentBoost),
                Map.entry("agentIndicator", agentIndicator),
                Map.entry("combinedBoost", combinedBoost)
        );

        return textResult(McpTemplateEngine.render("memory-salience-boost", model));
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
