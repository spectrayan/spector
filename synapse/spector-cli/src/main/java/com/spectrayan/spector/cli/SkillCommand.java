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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillReport;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Manage and compile procedural skills (playbooks and heuristics) (ADR-0086 §7 Phase 5).
 */
@Component
@Command(
        name = "skill",
        description = "Manage and compile procedural skills (playbooks and heuristics).",
        mixinStandardHelpOptions = true,
        subcommands = {
                SkillCommand.CompileSubcommand.class
        }
)
public class SkillCommand extends BaseCommand {

    private final ObjectProvider<SpectorMemory> memoryProvider;

    public SkillCommand() {
        this(null);
    }

    @Autowired
    public SkillCommand(@Lazy ObjectProvider<SpectorMemory> memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    @Override
    public void run() {
        spec.commandLine().usage(out());
    }

    @Component
    @Command(
            name = "compile",
            description = "Compile, test, or dry-run a procedural skill from parent memories.",
            mixinStandardHelpOptions = true
    )
    public static class CompileSubcommand extends BaseCommand {

        private final ObjectProvider<SpectorMemory> memoryProvider;

        public CompileSubcommand() {
            this(null);
        }

        @Autowired
        public CompileSubcommand(@Lazy ObjectProvider<SpectorMemory> memoryProvider) {
            this.memoryProvider = memoryProvider;
        }

        @CommandLine.Option(names = {"--cue"}, description = "Optional invocation cue, intent, or rule name.")
        private String cue;

        @CommandLine.Option(names = {"--parents"}, description = "Comma-separated list of parent memory IDs.", split = ",")
        private List<String> parents;

        @CommandLine.Option(names = {"--parent-texts"}, description = "Explicit parent observation texts or steps.")
        private List<String> parentTexts;

        @CommandLine.Option(names = {"--commit"}, description = "Persist to procedural memory (COMPILE mode). Default is false (DRY_RUN).")
        private boolean commit;

        @Override
        public void run() {
            SpectorMemory memory = memoryProvider != null ? memoryProvider.getIfAvailable() : null;
            if (memory == null) {
                err().println("Error: SpectorMemory bean is not available in current runtime profile.");
                return;
            }

            SkillSignal.Builder builder = SkillSignal.builder()
                    .mode(commit ? SkillSignal.Mode.COMPILE : SkillSignal.Mode.DRY_RUN)
                    .commit(commit)
                    .cue(cue);

            if (parents != null) {
                for (String pid : parents) {
                    if (pid != null && !pid.isBlank()) {
                        String id = pid.trim();
                        MemoryType type = detectType(id);
                        builder.parent(id, type);
                    }
                }
            }

            if (parentTexts != null) {
                for (String text : parentTexts) {
                    if (text != null && !text.isBlank()) {
                        builder.parentText(text);
                    }
                }
            }

            SkillReport report = memory.compileSkill(builder.build());

            if (isJson()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("mode", report.mode() != null ? report.mode().name() : (commit ? "COMPILE" : "DRY_RUN"));
                result.put("skillId", report.skillId());
                result.put("duplicateOf", report.duplicateOf());
                result.put("reinforced", report.reinforced());
                if (report.extractedBody() != null) {
                    result.put("serializedSkill", report.extractedBody().serialize());
                    if (report.extractedBody().hasMeta()) {
                        var meta = report.extractedBody().meta();
                        result.put("name", meta.name());
                        result.put("kind", meta.kind().name().toLowerCase());
                        result.put("confidence", meta.confidence());
                    }
                }
                OutputFormatter.printJson(out(), result);
            } else {
                out().println("Procedural Skill Compilation Report:");
                out().println("  Mode:         " + (report.mode() != null ? report.mode().name() : (commit ? "COMPILE" : "DRY_RUN")));
                out().println("  Skill ID:     " + (report.skillId() != null ? report.skillId() : "none (dry-run)"));
                if (report.duplicateOf() != null) {
                    out().println("  Duplicate Of: " + report.duplicateOf());
                }
                out().println("  Reinforced:   " + report.reinforced());
                if (report.extractedBody() != null && report.extractedBody().hasMeta()) {
                    var meta = report.extractedBody().meta();
                    out().println("  Name:         " + meta.name());
                    out().println("  Kind:         " + meta.kind().name().toLowerCase());
                    out().printf("  Confidence:   %.2f%n", meta.confidence());
                }
                if (report.extractedBody() != null) {
                    out().println();
                    out().println("--- Serialized Skill ---");
                    out().println(report.extractedBody().serialize());
                }
            }
        }

        private static MemoryType detectType(final String pid) {
            if (pid == null) return MemoryType.EPISODIC;
            String lower = pid.toLowerCase();
            if (lower.startsWith("sem-") || lower.startsWith("fact-") || lower.startsWith("rem-log-")) {
                return MemoryType.SEMANTIC;
            }
            if (lower.startsWith("skill-") || lower.startsWith("proc-")) {
                return MemoryType.PROCEDURAL;
            }
            return MemoryType.EPISODIC;
        }
    }
}
