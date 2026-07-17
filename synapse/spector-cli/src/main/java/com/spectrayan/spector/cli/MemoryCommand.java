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
package com.spectrayan.spector.cli;

import com.spectrayan.spector.client.SpectorClient;
import com.spectrayan.spector.client.SpectorClientException;
import com.spectrayan.spector.client.SpectorConnectionException;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picocli subcommand group for cognitive memory operations.
 */
@Command(
        name = "memory",
        description = "Manage and interact with Spector's cognitive memory subsystem.",
        mixinStandardHelpOptions = true,
        subcommands = {
                MemoryCommand.RememberSubcommand.class,
                MemoryCommand.RecallSubcommand.class,
                MemoryCommand.ForgetSubcommand.class,
                MemoryCommand.ReinforceSubcommand.class,
                MemoryCommand.SuppressSubcommand.class,
                MemoryCommand.UnsuppressSubcommand.class,
                MemoryCommand.ResolveSubcommand.class,
                MemoryCommand.StatusSubcommand.class,
                MemoryCommand.IntrospectSubcommand.class,
                MemoryCommand.ReminderSubcommand.class,
                MemoryCommand.ScratchpadSubcommand.class,
                MemoryCommand.WhyNotSubcommand.class,
                MemoryCommand.ReflectSubcommand.class,
                MemoryCommand.SalienceSubcommand.class
        }
)
public class MemoryCommand extends BaseCommand {

    @Override
    public void run() {
        spec.commandLine().usage(out());
    }

    @Command(name = "remember", description = "Store a memory with optional cognitive parameters.", mixinStandardHelpOptions = true)
    static class RememberSubcommand extends BaseCommand {
        @CommandLine.Option(names = {"--id"}, required = true, description = "Unique memory identifier.")
        private String id;

        @CommandLine.Option(names = {"--text"}, required = true, description = "Memory content text.")
        private String text;

        @CommandLine.Option(names = {"--tier"}, description = "Memory tier: WORKING, EPISODIC, SEMANTIC, PROCEDURAL.", defaultValue = "SEMANTIC")
        private String tier;

        @CommandLine.Option(names = {"--source"}, description = "Provenance: USER_STATED, OBSERVED, INFERRED, PROCEDURAL.", defaultValue = "OBSERVED")
        private String source;

        @CommandLine.Option(names = {"--tags"}, description = "Comma-separated tag strings.")
        private String tags;

        @CommandLine.Option(names = {"--interest"}, description = "ICNU Interest hint (0.0 to 1.0).")
        private Float interest;

        @CommandLine.Option(names = {"--challenge"}, description = "ICNU Challenge hint (0.0 to 1.0).")
        private Float challenge;

        @CommandLine.Option(names = {"--urgency"}, description = "ICNU Urgency hint (0.0 to 1.0).")
        private Float urgency;

        @CommandLine.Option(names = {"--valence"}, description = "Emotional valence (-128 to +127).")
        private Integer valence;

        @CommandLine.Option(names = {"--arousal"}, description = "Emotional intensity (0 to 255).")
        private Integer arousal;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("id", id);
                req.put("text", text);
                req.put("tier", tier);
                req.put("source", source);
                if (tags != null) req.put("tags", tags);
                if (interest != null) req.put("interest", interest);
                if (challenge != null) req.put("challenge", challenge);
                if (urgency != null) req.put("urgency", urgency);
                if (valence != null) req.put("valence", valence);
                if (arousal != null) req.put("arousal", arousal);

                String response = client.remember(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "recall", description = "Perform fused cognitive recall query across relevant memory tiers.", mixinStandardHelpOptions = true)
    static class RecallSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "Natural language recall query.")
        private String query;

        @CommandLine.Option(names = {"-k", "--top-k"}, description = "Max results (default: 10).", defaultValue = "10")
        private int topK;

        @CommandLine.Option(names = {"-p", "--profile"}, description = "Cognitive profile: BALANCED, HYPERFOCUS, PARANOID, DIVERGENT.", defaultValue = "BALANCED")
        private String profile;

        @CommandLine.Option(names = {"-v", "--valence"}, description = "Optional query valence filter (-128 to +127).")
        private Integer queryValence;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("query", query);
                req.put("topK", topK);
                req.put("profile", profile);
                if (queryValence != null) req.put("queryValence", queryValence);

                Map<String, Object> response = client.recall(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    out().println("Recall results (" + response.get("totalMemories") + " total memories, " + response.get("queryTimeMs") + "ms, profile=" + response.get("profile") + "):");
                    out().println();

                    if (results == null || results.isEmpty()) {
                        out().println("  No recalled memories.");
                    } else {
                        String[] headers = {"#", "ID", "TIER", "SCORE", "TEXT", "TAGS"};
                        List<String[]> rows = new ArrayList<>();
                        int rank = 1;
                        for (var r : results) {
                            List<String> tagsList = (List<String>) r.get("synapticTags");
                            String tagsStr = tagsList != null ? String.join(",", tagsList) : "";
                            rows.add(new String[]{
                                    String.valueOf(rank++),
                                    String.valueOf(r.get("id")),
                                    String.valueOf(r.get("memoryType")),
                                    String.format("%.4f", ((Number) r.get("score")).floatValue()),
                                    String.valueOf(r.get("text")),
                                    tagsStr
                            });
                        }
                        OutputFormatter.printTable(out(), headers, rows);
                    }
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "forget", description = "Tombstone a memory by ID.", mixinStandardHelpOptions = true)
    static class ForgetSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "ID of the memory to forget.")
        private String id;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.forgetMemory(id);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "reinforce", description = "Report outcome feedback (positive/negative) for a recalled memory.", mixinStandardHelpOptions = true)
    static class ReinforceSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "ID of the memory to reinforce.")
        private String id;

        @CommandLine.Option(names = {"-v", "--valence"}, required = true, description = "Emotional valence outcome feedback (-128 to +127).")
        private int valence;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.reinforceMemory(id, valence);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "suppress", description = "Suppress a memory from future recall.", mixinStandardHelpOptions = true)
    static class SuppressSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "ID of the memory to suppress.")
        private String id;

        @CommandLine.Option(names = {"-r", "--reason"}, description = "Suppress reason for audit purposes.")
        private String reason = "";

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.suppressMemory(id, "SUPPRESS", reason);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "unsuppress", description = "Unsuppress a previously suppressed memory.", mixinStandardHelpOptions = true)
    static class UnsuppressSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "ID of the memory to unsuppress.")
        private String id;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.suppressMemory(id, "UNSUPPRESS", "");
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "resolve", description = "Mark a memory as resolved (Zeigarnik Effect).", mixinStandardHelpOptions = true)
    static class ResolveSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "ID of the memory to resolve.")
        private String id;

        @CommandLine.Option(names = {"--unresolve"}, description = "Mark the memory as unresolved instead of resolved.")
        private boolean unresolve;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.resolveMemory(id, !unresolve);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "status", description = "Retrieve counts and stats of cognitive memory tiers and graphs.", mixinStandardHelpOptions = true)
    static class StatusSubcommand extends BaseCommand {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> status = client.memoryStatus();
                if (isJson()) {
                    OutputFormatter.printJson(out(), status);
                } else {
                    out().println("Cognitive Memory Subsystem Status:");
                    out().println();

                    var tierCounts = (Map<String, Object>) status.get("tierCounts");
                    String[][] entries = {
                            {"Total Memories", String.valueOf(status.get("totalMemories"))},
                            {"  Working Tier", String.valueOf(tierCounts != null ? tierCounts.get("WORKING") : 0)},
                            {"  Episodic Tier", String.valueOf(tierCounts != null ? tierCounts.get("EPISODIC") : 0)},
                            {"  Semantic Tier", String.valueOf(tierCounts != null ? tierCounts.get("SEMANTIC") : 0)},
                            {"  Procedural Tier", String.valueOf(tierCounts != null ? tierCounts.get("PROCEDURAL") : 0)},
                            {"Hebbian Edges", String.valueOf(status.get("hebbianEdges"))},
                            {"Temporal Sequence Links", String.valueOf(status.get("temporalLinks"))},
                            {"Semantic Entity Nodes", String.valueOf(status.get("entityNodes"))},
                            {"Semantic Entity Relations", String.valueOf(status.get("entityEdges"))}
                    };
                    OutputFormatter.printKeyValue(out(), entries);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    // ── New subcommands (API parity with MCP tools) ─────────────────────

    @Command(name = "introspect", description = "Introspect the agent's knowledge about a topic.", mixinStandardHelpOptions = true)
    static class IntrospectSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "Topic to introspect (e.g., 'kubernetes', 'user preferences').")
        private String topic;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> response = client.introspect(topic);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("🔍 Memory Introspection: '" + topic + "'");
                    out().println();
                    String[][] entries = {
                            {"Known", String.valueOf(response.get("known"))},
                            {"Confidence", String.valueOf(response.get("confidence"))},
                            {"Total Memories", String.valueOf(response.get("totalMemories"))},
                            {"Avg Importance", String.valueOf(response.get("avgImportance"))},
                            {"Avg Age (days)", String.valueOf(response.get("avgAgeDays"))},
                            {"Staleness", String.valueOf(response.get("staleness"))},
                            {"Stale", String.valueOf(response.get("stale"))},
                            {"Recommendation", String.valueOf(response.get("recommendation"))}
                    };
                    OutputFormatter.printKeyValue(out(), entries);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "reminder", description = "Schedule a prospective memory reminder.", mixinStandardHelpOptions = true)
    static class ReminderSubcommand extends BaseCommand {
        @CommandLine.Option(names = {"--text"}, required = true, description = "The reminder text.")
        private String text;

        @CommandLine.Option(names = {"--delay"}, required = true, description = "Delay in seconds until the reminder triggers.")
        private int delaySeconds;

        @CommandLine.Option(names = {"--tags"}, description = "Comma-separated contextual tags.")
        private String tags;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("text", text);
                req.put("delaySeconds", delaySeconds);
                if (tags != null) req.put("tags", tags);

                Map<String, Object> response = client.scheduleReminder(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("⏰ Reminder scheduled: \"" + text + "\"");
                    out().println("Triggers in: " + delaySeconds + "s");
                    out().println("Tags: " + (tags != null ? tags : "none"));
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "scratchpad", description = "Store a note in the working memory scratchpad.", mixinStandardHelpOptions = true)
    static class ScratchpadSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "The scratchpad note text.")
        private String text;

        @Override
        public void run() {
            try (SpectorClient client = createClient()) {
                String response = client.scratchpad(text);
                if (isJson()) {
                    OutputFormatter.printJson(out(), Map.of("message", response, "status", "success"));
                } else {
                    out().println(response);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "why-not", description = "Explain why a specific memory was NOT recalled for a query.", mixinStandardHelpOptions = true)
    static class WhyNotSubcommand extends BaseCommand {
        @CommandLine.Option(names = {"--id"}, required = true, description = "ID of the memory to investigate.")
        private String memoryId;

        @CommandLine.Option(names = {"--query"}, required = true, description = "The query it was expected to match.")
        private String query;

        @CommandLine.Option(names = {"-k", "--top-k"}, description = "The topK used in the original recall.", defaultValue = "5")
        private int topK;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("memoryId", memoryId);
                req.put("query", query);
                req.put("topK", topK);

                Map<String, Object> response = client.whyNot(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("🔍 Why-Not Analysis for memory '" + memoryId + "'");
                    out().println("Query: '" + query + "'");
                    out().println();
                    String[][] entries = {
                            {"Reason", String.valueOf(response.get("reason"))},
                            {"Exists", String.valueOf(response.get("exists"))},
                            {"Suppressed", String.valueOf(response.get("suppressed"))},
                            {"Score Gap", String.valueOf(response.get("scoreGap"))},
                            {"Summary", String.valueOf(response.get("summary"))}
                    };
                    OutputFormatter.printKeyValue(out(), entries);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "reflect", description = "Trigger a manual memory reflection/consolidation cycle.", mixinStandardHelpOptions = true)
    static class ReflectSubcommand extends BaseCommand {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> response = client.reflect();
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("🧠 Reflection Cycle Complete");
                    out().println();
                    String[][] entries = {
                            {"Tombstoned", String.valueOf(response.get("tombstonedCount"))},
                            {"Temporal Pruned", String.valueOf(response.get("temporalPrunedCount"))},
                            {"Duration (ms)", String.valueOf(response.get("durationMs"))}
                    };
                    OutputFormatter.printKeyValue(out(), entries);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    // ── Salience Profile Management ─────────────────────────────────

    @Command(name = "salience", description = "Manage the active salience profile (interests, persona, ICNU weights).",
            mixinStandardHelpOptions = true,
            subcommands = {
                    MemoryCommand.SalienceGetSubcommand.class,
                    MemoryCommand.SalienceComputeSubcommand.class,
                    MemoryCommand.SalienceAddInterestSubcommand.class,
                    MemoryCommand.SalienceSetPersonaSubcommand.class
            })
    static class SalienceSubcommand extends BaseCommand {
        @Override
        public void run() {
            spec.commandLine().usage(out());
        }
    }

    @Command(name = "get", description = "Show the current active salience profile.", mixinStandardHelpOptions = true)
    static class SalienceGetSubcommand extends BaseCommand {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> response = client.getSalienceProfile();
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    String status = String.valueOf(response.get("status"));
                    if ("neutral".equals(status)) {
                        out().println("🧠 Salience Profile: NEUTRAL (no personalization active)");
                        out().println("Use 'salience add-interest' or 'salience set-persona' to configure.");
                    } else {
                        out().println("🧠 Active Salience Profile");
                        out().println();
                        List<Map<String, Object>> interests = (List<Map<String, Object>>) response.get("interests");
                        if (interests != null && !interests.isEmpty()) {
                            out().println("Interests:");
                            for (var i : interests) {
                                out().println("  • " + i.get("topic") + " → " + i.get("level")
                                        + " (" + i.get("multiplier") + "×)");
                            }
                        }
                        List<Map<String, Object>> disinterests = (List<Map<String, Object>>) response.get("disinterests");
                        if (disinterests != null && !disinterests.isEmpty()) {
                            out().println("Disinterests:");
                            for (var d : disinterests) {
                                out().println("  • " + d.get("topic") + " → " + d.get("level")
                                        + " (" + d.get("multiplier") + "×)");
                            }
                        }
                        out().println();
                        out().println("Persona: " + response.get("hasPersona"));
                        out().println("ICNU Override: " + response.get("hasIcnuOverride"));
                        Object agentBoost = response.get("agentRelevanceBoost");
                        if (agentBoost != null && !agentBoost.equals(1.0) && !agentBoost.equals(1)) {
                            out().println("Agent Relevance Boost: " + agentBoost + "×");
                        }
                    }
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "compute", description = "Compute salience boost for a text without ingesting.", mixinStandardHelpOptions = true)
    static class SalienceComputeSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "Text to compute boost for.")
        private String text;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("text", text);
                Map<String, Object> response = client.computeSalienceBoost(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("🔍 Salience Boost Preview");
                    out().println();
                    String[][] entries = {
                            {"Text", String.valueOf(response.get("text"))},
                            {"Topic Boost", String.format("%.4f", ((Number) response.get("topicBoost")).floatValue())},
                            {"Self-Relevance", String.format("%.4f", ((Number) response.get("selfRelevanceBoost")).floatValue())},
                            {"Combined", String.format("%.4f", ((Number) response.get("combinedBoost")).floatValue())}
                    };
                    OutputFormatter.printKeyValue(out(), entries);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "add-interest", description = "Add an interest or disinterest to the salience profile.", mixinStandardHelpOptions = true)
    static class SalienceAddInterestSubcommand extends BaseCommand {
        @CommandLine.Parameters(index = "0", description = "Interest topic in natural language.")
        private String topic;

        @CommandLine.Option(names = {"--level"}, description = "Interest level: CRITICAL, HIGH, MEDIUM, LOW, IGNORE.", defaultValue = "HIGH")
        private String level;

        @CommandLine.Option(names = {"--disinterest"}, description = "Add as disinterest (dampener) instead of interest.")
        private boolean disinterest;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                req.put("topic", topic);
                req.put("level", level);
                Map<String, Object> response = disinterest
                        ? client.addDisinterest(req)
                        : client.addInterest(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    String label = disinterest ? "Disinterest" : "Interest";
                    out().println("✅ " + label + " added: \"" + topic + "\" → " + level);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "set-persona", description = "Set persona context (personality, occupation) on the salience profile.", mixinStandardHelpOptions = true)
    static class SalienceSetPersonaSubcommand extends BaseCommand {
        @CommandLine.Option(names = {"--occupation"}, description = "Occupation text.")
        private String occupation;

        @CommandLine.Option(names = {"--about"}, description = "Self-description/bio text.")
        private String about;

        @CommandLine.Option(names = {"--openness"}, description = "Big Five Openness (0-100).", defaultValue = "50")
        private int openness;

        @CommandLine.Option(names = {"--conscientiousness"}, description = "Big Five Conscientiousness (0-100).", defaultValue = "50")
        private int conscientiousness;

        @CommandLine.Option(names = {"--extraversion"}, description = "Big Five Extraversion (0-100).", defaultValue = "50")
        private int extraversion;

        @CommandLine.Option(names = {"--agreeableness"}, description = "Big Five Agreeableness (0-100).", defaultValue = "50")
        private int agreeableness;

        @CommandLine.Option(names = {"--neuroticism"}, description = "Big Five Neuroticism (0-100).", defaultValue = "50")
        private int neuroticism;

        @CommandLine.Option(names = {"--stress"}, description = "Stress response: ADAPTIVE, FIGHT, FLIGHT, FREEZE, FAWN.", defaultValue = "ADAPTIVE")
        private String stress;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (SpectorClient client = createClient()) {
                Map<String, Object> req = new HashMap<>();
                if (occupation != null) req.put("occupation", occupation);
                if (about != null) req.put("about", about);
                req.put("bigFive", Map.of(
                        "openness", openness,
                        "conscientiousness", conscientiousness,
                        "extraversion", extraversion,
                        "agreeableness", agreeableness,
                        "neuroticism", neuroticism));
                req.put("stressResponse", stress);

                Map<String, Object> response = client.setPersonaContext(req);
                if (isJson()) {
                    OutputFormatter.printJson(out(), response);
                } else {
                    out().println("✅ Persona set successfully.");
                    if (occupation != null) out().println("Occupation: " + occupation);
                    out().println("BigFive: O=" + openness + " C=" + conscientiousness
                            + " E=" + extraversion + " A=" + agreeableness + " N=" + neuroticism);
                    out().println("Stress: " + stress);
                }
            } catch (SpectorConnectionException e) {
                handleConnectionError(e);
            } catch (SpectorClientException e) {
                err().println("Error: " + e.getMessage());
            }
        }
    }
}

