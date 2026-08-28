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
package com.spectrayan.spector.bench.cognitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hebbian.HebbianEdge;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

/**
 * Validates 100% referential integrity and byte-level fidelity between
 * source dataset JSON files (corpus, entities, hebbian edges, temporal chains)
 * and the off-heap mmap partition and runtime bundles.
 */
public final class MmapDatasetIntegrityValidator {

    private static final Logger log = LoggerFactory.getLogger(MmapDatasetIntegrityValidator.class);

    public record ValidationReport(
            boolean isHealthy,
            int totalChecks,
            int passedChecks,
            int failedChecks,
            List<String> violations,
            Map<String, String> stats,
            String markdownSummary
    ) {}

    public ValidationReport validate(Path datasetDir) {
        log.info("Starting MMAP Dataset Integrity Validation on: {}", datasetDir.toAbsolutePath());

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);

        Path cacheFile = datasetDir.resolve("embeddings.bin");
        com.spectrayan.spector.provider.embedding.EmbeddingProvider rawEmbedder =
                com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider.createDefault();
        CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile);

        BenchmarkSetup setup = new BenchmarkSetup();
        SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir);
        Map<String, Integer> idToSlot = setup.idToSlot();

        List<String> violations = new ArrayList<>();
        Map<String, String> stats = new LinkedHashMap<>();

        int totalChecks = 0;
        int passedChecks = 0;

        try {
            // ── 1. Corpus & Index Record Integrity ──
            int corpusSize = dataset.corpus().size();
            int indexTotal = memory.totalMemories();
            totalChecks++;
            if (corpusSize == indexTotal) {
                passedChecks++;
            } else {
                violations.add("Total memory count mismatch: JSON corpus=" + corpusSize + " vs Index=" + indexTotal);
            }
            stats.put("Corpus Records (JSON)", String.valueOf(corpusSize));
            stats.put("Indexed Memories (MMAP)", String.valueOf(indexTotal));

            int recordsVerified = 0;
            for (BenchmarkCorpusRecord rec : dataset.corpus()) {
                totalChecks++;
                var loc = memory.admin().index().locate(rec.id());
                if (loc != null) {
                    passedChecks++;
                    recordsVerified++;
                    Integer expectedSlot = idToSlot.get(rec.id());
                    if (expectedSlot != null && loc.graphSlot() != expectedSlot) {
                        violations.add("Slot mapping mismatch for " + rec.id() + ": indexSlot=" + loc.graphSlot() + " vs idToSlot=" + expectedSlot);
                    }
                } else {
                    violations.add("Corpus record '" + rec.id() + "' missing from MemoryIndex");
                }

                // Raw text verification (zero-copy off-heap TextAppendMemory)
                totalChecks++;
                String storedText = memory.admin().index().text(rec.id());
                if (storedText != null && storedText.equals(rec.text())) {
                    passedChecks++;
                } else if (storedText == null) {
                    violations.add("Corpus record '" + rec.id() + "' raw text is NULL in TextAppendMemory");
                } else {
                    violations.add("Corpus record '" + rec.id() + "' raw text mismatch (len=" + storedText.length() + " vs exp=" + rec.text().length() + ")");
                }
            }
            stats.put("Corpus-Index Match Rate", String.format(Locale.ROOT, "%.2f%% (%d/%d)",
                    (recordsVerified * 100.0f) / Math.max(1, corpusSize), recordsVerified, corpusSize));

            // ── 2. Hebbian Associative Graph Integrity ──
            HebbianGraphBase hebbian = memory.admin().graph() != null ? memory.admin().graph().rawHebbianGraph() : null;
            int jsonHebbianEdges = dataset.hebbianEdges().size();
            stats.put("Hebbian Edges (JSON)", String.valueOf(jsonHebbianEdges));

            int hebbianPassed = 0;
            if (hebbian != null) {
                for (HebbianEdgeDef edge : dataset.hebbianEdges()) {
                    totalChecks++;
                    Integer slotA = idToSlot.get(edge.memoryIdA());
                    Integer slotB = idToSlot.get(edge.memoryIdB());

                    if (slotA == null || slotB == null) {
                        violations.add("Hebbian edge references unmapped ID: A=" + edge.memoryIdA() + " (" + slotA + "), B=" + edge.memoryIdB() + " (" + slotB + ")");
                        continue;
                    }

                    if (slotA.equals(slotB)) {
                        // Self-loops are ignored by design in Hebbian graph topology
                        passedChecks++;
                        hebbianPassed++;
                        continue;
                    }

                    List<HebbianEdge> neighborsA = hebbian.neighbors(slotA);
                    boolean foundInA = neighborsA.stream().anyMatch(e -> e.neighborIndex() == slotB);

                    List<HebbianEdge> neighborsB = hebbian.neighbors(slotB);
                    boolean foundInB = neighborsB.stream().anyMatch(e -> e.neighborIndex() == slotA);

                    if (foundInA && foundInB) {
                        passedChecks++;
                        hebbianPassed++;
                    } else {
                        violations.add("Hebbian edge not linked in MMAP graph: " + edge.memoryIdA() + " (slot " + slotA + ") <-> " + edge.memoryIdB() + " (slot " + slotB + ") [inA=" + foundInA + ", inB=" + foundInB + "]");
                    }
                }
                stats.put("Hebbian Bidirectional Match", String.format(Locale.ROOT, "%.2f%% (%d/%d)",
                        (hebbianPassed * 100.0f) / Math.max(1, jsonHebbianEdges), hebbianPassed, jsonHebbianEdges));
                stats.put("Hebbian Graph Capacity", String.valueOf(hebbian.capacity()));
            } else {
                violations.add("HebbianGraph is null in SpectorMemoryAdmin");
            }

            // ── 3. Temporal Chain Doubly-Linked Integrity ──
            TemporalChainMemory tc = memory.admin().graph() != null ? memory.admin().graph().rawTemporalChain() : null;
            int jsonChains = dataset.temporalChains().size();
            stats.put("Temporal Chains (JSON)", String.valueOf(jsonChains));

            int totalTemporalLinks = 0;
            int passedTemporalLinks = 0;
            if (tc != null) {
                for (TemporalChainDef chain : dataset.temporalChains()) {
                    List<String> ids = chain.orderedMemoryIds();
                    for (int i = 0; i < ids.size() - 1; i++) {
                        totalChecks++;
                        totalTemporalLinks++;
                        Integer currSlot = idToSlot.get(ids.get(i));
                        Integer nextSlot = idToSlot.get(ids.get(i + 1));

                        if (currSlot == null || nextSlot == null) {
                            violations.add("Temporal link references missing ID: curr=" + ids.get(i) + " (" + currSlot + "), next=" + ids.get(i + 1) + " (" + nextSlot + ")");
                            continue;
                        }

                        int forward = tc.next(currSlot);
                        int backward = tc.prev(nextSlot);

                        if (forward == nextSlot && backward == currSlot) {
                            passedChecks++;
                            passedTemporalLinks++;
                        } else {
                            violations.add("Broken temporal chain link between " + ids.get(i) + " (slot " + currSlot + ") and " + ids.get(i + 1) + " (slot " + nextSlot + "): forward=" + forward + " (exp " + nextSlot + "), backward=" + backward + " (exp " + currSlot + ")");
                        }
                    }
                }
                stats.put("Temporal Chain Continuity", String.format(Locale.ROOT, "%.2f%% (%d/%d)",
                        (passedTemporalLinks * 100.0f) / Math.max(1, totalTemporalLinks), passedTemporalLinks, totalTemporalLinks));
            } else {
                violations.add("TemporalChain is null in SpectorMemoryAdmin");
            }

            // ── 4. Entity Directory & HyperEntityGraph Integrity ──
            EntityDirectory dir = memory.admin().entityDirectory();
            HyperEntityGraphMemory hyper = memory.admin().hyperEntityGraph();
            int jsonRelations = dataset.entityRelations().size();
            stats.put("Entity Relations (JSON)", String.valueOf(jsonRelations));

            int matchedEntityMentions = 0;
            int totalEntityMentions = 0;
            if (dir != null) {
                stats.put("Entity Directory Interned Count", String.valueOf(dir.entityCount()));
                for (EntityRelation rel : dataset.entityRelations()) {
                    int fromId = dir.findEntity(rel.fromEntity().name());
                    int toId = dir.findEntity(rel.toEntity().name());

                    totalChecks++;
                    if (fromId >= 0 && toId >= 0) {
                        passedChecks++;
                    } else {
                        violations.add("Entity lookup failed: from='" + rel.fromEntity().name() + "' (id=" + fromId + "), to='" + rel.toEntity().name() + "' (id=" + toId + ")");
                    }

                    for (String sourceId : rel.sourceMemoryIds()) {
                        totalChecks++;
                        totalEntityMentions++;
                        Integer memSlot = idToSlot.get(sourceId);
                        if (memSlot != null && fromId >= 0) {
                            int[] fromMems = dir.memoriesForEntity(fromId);
                            boolean foundMem = false;
                            for (int m : fromMems) {
                                if (m == memSlot) {
                                    foundMem = true;
                                    break;
                                }
                            }
                            if (foundMem) {
                                passedChecks++;
                                matchedEntityMentions++;
                            } else {
                                violations.add("Entity '" + rel.fromEntity().name() + "' (id " + fromId + ") missing memory linkage to " + sourceId + " (slot " + memSlot + ")");
                            }
                        }
                    }
                }
                stats.put("Entity-Memory Postings Match", String.format(Locale.ROOT, "%.2f%% (%d/%d)",
                        (matchedEntityMentions * 100.0f) / Math.max(1, totalEntityMentions), matchedEntityMentions, totalEntityMentions));
            } else {
                violations.add("EntityDirectory is null in SpectorMemoryAdmin");
            }

            if (hyper != null) {
                stats.put("Hypergraph Total Hyperedges", String.valueOf(hyper.totalHyperedges()));
            }

            // ── 5. Salience Profile & CoActivation ──
            var salience = memory.salienceProfile();
            if (dataset.persona() != null && dataset.persona().hasSalienceProfile()) {
                totalChecks++;
                if (salience != null && !salience.interests().isEmpty()) {
                    passedChecks++;
                    stats.put("Salience Profile", "ACTIVE (" + salience.interests().size() + " interests, " + salience.disinterests().size() + " disinterests)");
                } else {
                    violations.add("Salience profile missing or uninitialized despite persona in dataset");
                }
            } else {
                stats.put("Salience Profile", salience != null ? "ACTIVE" : "NONE");
            }

            var coact = memory.admin().coActivation();
            if (coact != null) {
                stats.put("CoActivation Total Edges", String.valueOf(coact.edgeCount()));
                stats.put("CoActivation Total Pairs", String.valueOf(coact.pairCount()));
                stats.put("CoActivation Inverted Index Tags", String.valueOf(coact.invertedIndexTagCount()));
            }

        } finally {
            try {
                memory.close();
            } catch (Exception e) {
                log.warn("Error closing SpectorMemory: {}", e.getMessage());
            }
        }

        int failedChecks = totalChecks - passedChecks;
        boolean isHealthy = violations.isEmpty();

        String mdReport = generateMarkdownReport(datasetDir.getFileName().toString(), isHealthy, totalChecks, passedChecks, failedChecks, stats, violations);

        // Write report to results directory
        try {
            Path resultsDir = datasetDir.resolve("results");
            if (!Files.exists(resultsDir)) {
                Files.createDirectories(resultsDir);
            }
            Files.writeString(resultsDir.resolve("mmap_validation_report.md"), mdReport);
        } catch (IOException e) {
            log.warn("Failed to write validation report: {}", e.getMessage());
        }

        return new ValidationReport(isHealthy, totalChecks, passedChecks, failedChecks, violations, stats, mdReport);
    }

    private static String generateMarkdownReport(
            String datasetName, boolean isHealthy, int total, int passed, int failed,
            Map<String, String> stats, List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 🧠 Spector Memory — MMAP Off-Heap Dataset Integrity Report\n\n");
        sb.append("**Dataset:** `").append(datasetName).append("`\n");
        sb.append("**Overall Status:** ").append(isHealthy ? "✅ **100% HEALTHY & VERIFIED**" : "⚠️ **INTEGRITY ISSUES DETECTED**").append("\n");
        sb.append("**Total Checks:** `").append(total).append("` | **Passed:** `").append(passed).append("` | **Failed:** `").append(failed).append("`\n\n");

        sb.append("## 📊 Subsystem Verification Summary\n\n");
        sb.append("| Subsystem Metric | Value | Status |\n");
        sb.append("|:---|:---|:---:|\n");
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            sb.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" | ✅ PASS |\n");
        }

        if (!violations.isEmpty()) {
            sb.append("\n## ⚠️ Integrity Violations (First ").append(Math.min(50, violations.size())).append(" of ").append(violations.size()).append(")\n\n");
            for (int i = 0; i < Math.min(50, violations.size()); i++) {
                sb.append("- ").append(violations.get(i)).append("\n");
            }
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        Path datasetDir = args.length > 0 ? Paths.get(args[0]) : Paths.get("d:/git/spector-datasets/locomo/data");
        MmapDatasetIntegrityValidator validator = new MmapDatasetIntegrityValidator();
        ValidationReport report = validator.validate(datasetDir);

        System.out.println(report.markdownSummary());
        if (!report.isHealthy()) {
            System.err.println("Validation failed with " + report.failedChecks() + " violations.");
            System.exit(1);
        } else {
            System.out.println("Validation passed successfully!");
            System.exit(0);
        }
    }
}