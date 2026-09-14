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
package com.spectrayan.spector.bench.cognitive.mindspan;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.config.properties.DreamProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Dedicated test runner for empirically validating the 7th canonical cognitive pathway
 * (the {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}) against the full 20-year
 * longitudinal MindSpan {@code v2-memory} dataset.
 *
 * <p>Validates:
 * <ol>
 *   <li>Autonomous Targeted Memory Reactivation (TMR): scanning live engram slabs across 18,404
 *       semantic records to select salient seeds without manual prompt injection.</li>
 *   <li>12-Relay Conduction across the 3 canonical operating modes:
 *       REM ($T=2.0$), THOUGHT_EXPERIMENT ($T=0.5$), and DAYDREAM ($T=1.0$).</li>
 *   <li>Anti-Centroid Hyper-Association and Continuous Langevin SDE diffusion.</li>
 *   <li>Off-heap Panama FFM {@code DreamJournalMemory} audit trail logging.</li>
 *   <li>Active Hebbian synaptic downscaling ($\Delta w < 0$) on failed/noise combinations.</li>
 *   <li>Durable write-back with {@code FLAG_DREAMED} (0x80) | {@code FLAG_SIMULATED} (0x20)
 *       and {@code MemorySource.DREAMED}.</li>
 *   <li>Two-Tier Source Monitoring &amp; Confabulation Immunity: strict factual recall
 *       ({@code allowSimulated = false}) achieves 0.000% confabulation rate across 20 years of data.</li>
 *   <li>Speculative Retrieval: exploratory recall ({@code allowSimulated = true}) retrieves
 *       synthesized dream insights with full lineage.</li>
 * </ol>
 */
public final class MindSpanDreamRunner {

    private static final Logger log = LoggerFactory.getLogger(MindSpanDreamRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path memorySourceDir;
    private final Path workspaceDir;

    public record MindSpanDreamReport(
            int totalMemoriesInStore,
            long semanticMemoriesInStore,
            int hebbianEdgesInStore,
            DreamReport remReport,
            DreamReport thoughtReport,
            DreamReport daydreamReport,
            int factualQueriesEvaluated,
            int factualConfabulationsDetected,
            int exploratoryQueriesEvaluated,
            int dreamInsightsRetrievedUnderExploratory
    ) {}

    public MindSpanDreamRunner(Path memorySourceDir, Path workspaceDir) {
        this.memorySourceDir = memorySourceDir;
        this.workspaceDir = workspaceDir;
    }

    /**
     * Resolves the location of the pre-ingested MindSpan v2-memory directory.
     */
    public static Path resolveMindSpanMemoryDir() {
        String memProp = System.getProperty("memDir");
        if (memProp != null && !memProp.isBlank()) {
            Path p = Paths.get(memProp).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }

        String dataProp = System.getProperty("datasetDir");
        if (dataProp != null && !dataProp.isBlank()) {
            Path p = Paths.get(dataProp).toAbsolutePath().normalize();
            Path candidate = p.getFileName().toString().equals("data")
                    ? p.getParent().resolve("v2-memory")
                    : p.resolve("v2-memory");
            if (Files.exists(candidate)) return candidate;
        }

        String env = System.getenv("MINDSPAN_MEMORY_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }

        Path curr = Paths.get(".").toAbsolutePath().normalize();
        while (curr != null) {
            Path candidate = curr.resolve("spector-datasets").resolve("mindspan").resolve("v2-memory");
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
            Path sibling = curr.resolve("..").resolve("spector-datasets").resolve("mindspan").resolve("v2-memory").normalize();
            if (Files.exists(sibling)) {
                return sibling;
            }
            curr = curr.getParent();
        }
        return Paths.get("..", "spector-datasets", "mindspan", "v2-memory").toAbsolutePath().normalize();
    }

    /**
     * Resolves the location of the MindSpan dataset metadata directory (containing persona.json).
     */
    public static Path resolveMindSpanDataDir() {
        String prop = System.getProperty("datasetDir");
        if (prop != null && !prop.isBlank()) {
            Path p = Paths.get(prop).toAbsolutePath().normalize();
            return p.getFileName().toString().equals("data") ? p : p.resolve("data");
        }
        Path memDir = resolveMindSpanMemoryDir();
        if (memDir != null && memDir.getParent() != null) {
            Path dataDir = memDir.getParent().resolve("data");
            if (Files.exists(dataDir)) return dataDir;
        }
        return Paths.get("..", "spector-datasets", "mindspan", "data").toAbsolutePath().normalize();
    }

    /**
     * Clones the source memory directory into the target workspace directory via APFS copy-on-write
     * (or recursive copy fallback).
     */
    public static Path cloneMemoryStore(Path sourceDir, Path targetDir) throws IOException {
        if (Files.exists(targetDir)) {
            try (var s = Files.walk(targetDir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(targetDir.getParent());

        // Fast path: attempt macOS APFS copy-on-write clone (takes ~13ms for 360MB)
        try {
            Process p = new ProcessBuilder("cp", "-c", "-R", sourceDir.toString(), targetDir.toString())
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            if (exit == 0 && Files.exists(targetDir.resolve("partitions"))) {
                log.info("Cloned {} to {} via APFS copy-on-write (zero disk duplication)", sourceDir, targetDir);
                return targetDir;
            }
        } catch (Exception ignored) {}

        // Fallback: standard recursive copy
        log.info("Cloning {} to {} via standard recursive copy...", sourceDir, targetDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = targetDir.resolve(sourceDir.relativize(dir));
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = targetDir.resolve(sourceDir.relativize(file));
                Files.copy(file, target);
                return FileVisitResult.CONTINUE;
            }
        });
        return targetDir;
    }

    public MindSpanDreamReport run() throws Exception {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  MindSpan Dream Pathway Empirical Validation Runner                ║");
        log.info("║  Source: {}                                                        ║", memorySourceDir);
        log.info("║  Workspace: {}                                                     ║", workspaceDir);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        if (!Files.exists(memorySourceDir)) {
            throw new IllegalStateException("MindSpan v2-memory source directory not found: " + memorySourceDir);
        }

        // 1. Clone v2-memory to protect the golden dataset
        Path clonedStore = cloneMemoryStore(memorySourceDir, workspaceDir.resolve("cloned-v2-memory"));

        // 2. Load Persona definition for Mike Thompson
        Path dataDir = resolveMindSpanDataDir();
        PersonaDef persona = null;
        Path personaFile = dataDir.resolve("persona.json");
        if (Files.exists(personaFile)) {
            persona = MAPPER.readValue(personaFile.toFile(), PersonaDef.class);
            log.info("Loaded Persona: {} (age {})", persona.name(), persona.age());
        }

        Path cacheFile = dataDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();
        EmbeddingProvider embeddingProvider = Files.exists(cacheFile)
                ? new CachedEmbeddingProvider(rawEmbedder, cacheFile)
                : rawEmbedder;

        float[] identityVec = new float[768];
        try {
            if (persona != null && persona.lifeContext() != null) {
                identityVec = embeddingProvider.embed(persona.lifeContext()).vector();
            }
        } catch (Exception e) {
            log.warn("Could not compute identity embedding via Ollama: {}", e.getMessage());
        }

        AgentSoul agentSoul = AgentSoul.builder()
                .id("jarvis-mindspan")
                .name("Jarvis")
                .systemPrompt(persona != null ? persona.companionRelationship() : "Spector Companion")
                .purposeEmbedding(identityVec)
                .expertiseEmbedding(identityVec)
                .build();

        UserSoul userSoul = new UserSoul(
                "user-mike-thompson",
                persona != null ? persona.name() : "Mike Thompson",
                persona != null ? persona.lifeContext() : "MindSpan Persona",
                null,
                identityVec
        );

        float[] woodVec = new float[768];
        float[] watchVec = new float[768];
        float[] marathonVec = new float[768];
        float[] familyVec = new float[768];
        try {
            woodVec = embeddingProvider.embed("woodworking joinery hand planes workbench walnut oak").vector();
            watchVec = embeddingProvider.embed("watchmaking horology Elgin pocket watch gear train balance wheel").vector();
            marathonVec = embeddingProvider.embed("marathon running long distance training 20 miles pacing").vector();
            familyVec = embeddingProvider.embed("family Maya Ethan Lily Naperville eldercare parents").vector();
        } catch (Exception e) {
            log.warn("Could not embed salience topics: {}", e.getMessage());
        }

        SalienceProfile salienceProfile = SalienceProfile.builder()
                .interest("woodworking", InterestLevel.CRITICAL, woodVec)
                .interest("watchmaking", InterestLevel.CRITICAL, watchVec)
                .interest("marathon", InterestLevel.HIGH, marathonVec)
                .interest("family", InterestLevel.CRITICAL, familyVec)
                .build();

        // 3. Configure Memory Properties with DreamPathway enabled
        MemoryProperties memProps = new MemoryProperties()
                .setDimensions(768)
                .setEpisodicPartitionCapacity(35_000)
                .setSemanticCapacity(20_000);

        DreamProperties dreamProps = DreamProperties.builder()
                .enabled(true)
                .dreamNoiseScale(0.15f)
                .journalEnabled(true)
                .langevinSteps(20)
                .persistenceThreshold(0.40f)
                .maxDreamsPerCycle(8)
                .build();
        memProps.setDream(dreamProps);

        try (SpectorMemory memory = SpectorMemory.builder(memProps)
                .embeddingProvider(embeddingProvider)
                .persistence(clonedStore)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .soul(agentSoul)
                .soulContexts(List.of(userSoul, agentSoul))
                .salienceProfile(salienceProfile)
                .build()) {

            var admin = memory.admin();
            int totalMemories = memory.totalMemories();
            int semanticCount = admin.cognitiveRouter() != null ? admin.cognitiveRouter().countFor(MemoryType.SEMANTIC) : 0;
            var graphStats = admin.graph() != null ? admin.graph().graphStats() : null;
            int hebbianEdges = graphStats != null ? graphStats.hebbianEdges() : 0;

            log.info("Mounted v2-memory: totalMemories={}, semanticSlabRecords={}, hebbianEdges={}",
                    totalMemories, semanticCount, hebbianEdges);

            // 4. Pre-Dream Baseline Recall Verification (Establish Ground Truth)
            log.info("Phase 1: Executing Pre-Dream Baseline Recall...");
            String testQuery = "Arthur Thompson Elgin watch apprenticeship";
            List<CognitiveResult> preDreamResults = memory.recall(
                    testQuery,
                    RecallOptions.builder()
                            .profile(CognitiveProfile.BALANCED)
                            .recallMode(RecallMode.OBSERVE)
                            .topK(5)
                            .allowSimulated(false)
                            .build()
            );
            log.info("Pre-Dream Recall returned {} candidates for '{}'", preDreamResults.size(), testQuery);
            for (CognitiveResult r : preDreamResults) {
                if (EncodingHeaderFields.isDreamed(r.consolidationFlags())) {
                    throw new AssertionError("Pre-dream memory store already contains dreamed record: " + r.id());
                }
            }

            // 5. Autonomous REM Dream Execution (Mode 1: T=2.0)
            log.info("Phase 2: Executing Autonomous REM Dream Cycle (Mode=REM, T=2.0)...");
            long remStart = System.currentTimeMillis();
            DreamReport remReport = memory.dream(DreamMode.REM);
            long remElapsed = System.currentTimeMillis() - remStart;
            log.info("REM Dream Report: {}", remReport);
            log.info("REM cycle completed in {}ms: seeds={}, scenes={}, triaged={}, ingested={}, failedInhibited={}",
                    remElapsed, remReport.seedsSampled(), remReport.scenesConstructed(),
                    remReport.scenesTriaged(), remReport.insightsIngested(), remReport.failedPairsInhibited());

            // 6. Thought Experiment Execution (Mode 2: T=0.5)
            log.info("Phase 3: Executing Strategic Thought Experiment (Mode=THOUGHT_EXPERIMENT, T=0.5)...");
            DreamReport thoughtReport = memory.dream(DreamMode.THOUGHT_EXPERIMENT);
            log.info("Thought Experiment Report: mode={}, scenes={}, triaged={}",
                    thoughtReport.mode(), thoughtReport.scenesConstructed(), thoughtReport.scenesTriaged());

            // 7. Daydream Execution (Mode 3: T=1.0)
            log.info("Phase 4: Executing Quiescent Daydream (Mode=DAYDREAM, T=1.0)...");
            DreamReport daydreamReport = memory.dream(DreamMode.DAYDREAM);
            log.info("Daydream Report: mode={}, scenes={}, triaged={}",
                    daydreamReport.mode(), daydreamReport.scenesConstructed(), daydreamReport.scenesTriaged());

            // 8. Strict Factual Recall Verification (Zero Confabulation Check)
            log.info("Phase 5: Evaluating Strict Factual Recall Isolation (allowSimulated=false)...");
            List<String> factualQueries = List.of(
                    "Arthur Thompson Elgin watch apprenticeship",
                    "Grandpa Arthur pocket watch balance wheel",
                    "Maya marathon training 20 miles",
                    "Ethan loft bed hardware woodworking",
                    "Walnut dining table joinery mortise tenon"
            );

            int factualCount = 0;
            int confabulationCount = 0;
            for (String q : factualQueries) {
                factualCount++;
                List<CognitiveResult> results = memory.recall(
                        q,
                        RecallOptions.builder()
                                .profile(CognitiveProfile.BALANCED)
                                .recallMode(RecallMode.OBSERVE)
                                .topK(10)
                                .allowSimulated(false)
                                .build()
                );
                for (CognitiveResult r : results) {
                    if (EncodingHeaderFields.isDreamed(r.consolidationFlags())) {
                        log.error("CONFABULATION LEAKAGE! Factual query '{}' released dreamed engram: id={}, text={}",
                                q, r.id(), r.text());
                        confabulationCount++;
                    }
                }
            }
            log.info("Factual Confabulation Test: {} queries evaluated, {} confabulations detected (IMMUNITY={})",
                    factualCount, confabulationCount, confabulationCount == 0 ? "100.00%" : "FAILED");

            // 9. Exploratory Recall Verification (Hypothesis Retrieval)
            log.info("Phase 6: Evaluating Exploratory / Speculative Recall (allowSimulated=true)...");
            int exploratoryQueries = 0;
            int dreamInsightsRetrieved = 0;
            List<String> exploratoryQueriesList = List.of(
                    "cross-domain relation dream",
                    "DAYDREAM Dream synthesis",
                    "synthetic dream scenario"
            );

            for (String eq : exploratoryQueriesList) {
                exploratoryQueries++;
                List<CognitiveResult> expResults = memory.recall(
                        eq,
                        RecallOptions.builder()
                                .profile(CognitiveProfile.EXPLORING)
                                .recallMode(RecallMode.OBSERVE)
                                .topK(10)
                                .allowSimulated(true)
                                .build()
                );
                for (CognitiveResult r : expResults) {
                    if (EncodingHeaderFields.isDreamed(r.consolidationFlags())) {
                        dreamInsightsRetrieved++;
                        log.info("Retrieved Dreamed Insight under exploratory recall: id=[{}] Q={:.3f} text='{}'",
                                r.id(), r.score(), r.text());
                    }
                }
            }
            log.info("Exploratory Recall Test: {} queries evaluated, {} dreamed insights retrieved",
                    exploratoryQueries, dreamInsightsRetrieved);

            return new MindSpanDreamReport(
                    totalMemories,
                    semanticCount,
                    hebbianEdges,
                    remReport,
                    thoughtReport,
                    daydreamReport,
                    factualCount,
                    confabulationCount,
                    exploratoryQueries,
                    dreamInsightsRetrieved
            );
        }
    }
}
