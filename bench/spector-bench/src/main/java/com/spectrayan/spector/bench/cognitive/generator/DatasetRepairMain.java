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
package com.spectrayan.spector.bench.cognitive.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.bench.cognitive.model.RelevanceJudgment;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Repair and enrichment script for the cognitive benchmark dataset.
 *
 * <p>Identifies memories missing synaptic tags/metadata due to prior LLM truncation failures,
 * re-runs the CognitiveAnnotator specifically for those memories, rebuilds all graph relations,
 * and re-validates the dataset.</p>
 */
public final class DatasetRepairMain {

    private static final Logger log = LoggerFactory.getLogger(DatasetRepairMain.class);

    public static void main(String[] args) {
        log.info("=== Starting Dataset Repair Main ===");

        // Default configurations
        Path outputDir = Path.of("d:\\git\\spector-datasets\\entity-dense-baseline\\data");
        Path personaPath = outputDir.resolve("persona.json");
        String modelName = "qwen3:1.7b";
        String ollamaUrl = "http://localhost:11434";

        // Parse optional arguments
        for (String arg : args) {
            if (arg.startsWith("--output=")) {
                outputDir = Path.of(arg.substring(9));
            } else if (arg.startsWith("--persona=")) {
                personaPath = Path.of(arg.substring(10));
            } else if (arg.startsWith("--model=")) {
                modelName = arg.substring(8);
            } else if (arg.startsWith("--ollama-url=")) {
                ollamaUrl = arg.substring(13);
            }
        }

        try {
            // 1. Load Persona
            log.info("Loading persona from {}", personaPath);
            PersonaLoader personaLoader = new PersonaLoader();
            PersonaDef persona = personaLoader.load(personaPath);

            // 2. Load Corpus from corpus.jsonl
            Path corpusFile = outputDir.resolve("corpus.jsonl");
            if (!Files.exists(corpusFile)) {
                log.error("corpus.jsonl not found at {}", corpusFile);
                System.exit(1);
            }

            log.info("Loading corpus from {}", corpusFile);
            DatasetLoader loader = new DatasetLoader();
            List<BenchmarkCorpusRecord> corpus = loader.loadCorpus(corpusFile);
            log.info("Loaded {} total corpus records", corpus.size());

            // 3. Identify records needing repair
            List<BenchmarkCorpusRecord> toRepair = new ArrayList<>();
            List<Integer> repairIndices = new ArrayList<>();

            for (int i = 0; i < corpus.size(); i++) {
                BenchmarkCorpusRecord record = corpus.get(i);
                // Check if record is missing synaptic tags/metadata (indicating annotation fallback)
                if (record.synapticTags() == null || record.synapticTags().isEmpty()) {
                    toRepair.add(record);
                    repairIndices.add(i);
                }
            }

            log.info("Found {} records needing repair (missing synaptic tags)", toRepair.size());

            if (toRepair.isEmpty()) {
                log.info("No repair needed! All records are fully annotated.");
            } else {
                // 4. Initialize Ollama Client and Annotator
                log.info("Initializing Ollama Completion Client with model {} at {}", modelName, ollamaUrl);
                try (OllamaCompletionClient client = new OllamaCompletionClient(ollamaUrl, modelName, 3)) {
                    if (!client.isAvailable()) {
                        log.error("Ollama server is not available at {}", ollamaUrl);
                        System.exit(1);
                    }

                    log.info("Starting targeted annotation for {} records...", toRepair.size());
                    CognitiveAnnotator annotator = new CognitiveAnnotator(client, persona);
                    List<BenchmarkCorpusRecord> repaired = annotator.annotateAll(toRepair);

                    log.info("Merging repaired records back into corpus...");
                    for (int i = 0; i < repaired.size(); i++) {
                        int index = repairIndices.get(i);
                        corpus.set(index, repaired.get(i));
                    }

                    // Save updated corpus back to corpus.jsonl
                    log.info("Saving updated corpus.jsonl");
                    writeJsonl(corpusFile, corpus);
                    log.info("Wrote updated corpus.jsonl");
                }
            }

            // 5. Programmatically rebuild all graphs (Phase 4)
            log.info("Rebuilding graph relations based on updated corpus...");
            GraphBuilder graphBuilder = new GraphBuilder();

            List<EntityRelation> entityRelations = graphBuilder.buildEntityGraph(corpus);
            writeJsonl(outputDir.resolve("entities.jsonl"), entityRelations);
            log.info("entities.jsonl rewritten ({} relations)", entityRelations.size());

            List<TemporalChainDef> temporalChains = graphBuilder.buildTemporalChains(corpus);
            writeJsonl(outputDir.resolve("temporal_chains.jsonl"), temporalChains);
            log.info("temporal_chains.jsonl rewritten ({} chains)", temporalChains.size());

            List<HebbianEdgeDef> hebbianEdges = graphBuilder.buildHebbianEdges(corpus);
            writeJsonl(outputDir.resolve("hebbian_edges.jsonl"), hebbianEdges);
            log.info("hebbian_edges.jsonl rewritten ({} edges)", hebbianEdges.size());

            // 6. Run dataset validation
            log.info("Loading queries and relevance judgments for validation...");
            List<BenchmarkQuery> queries = loader.loadQueries(outputDir.resolve("queries.jsonl"));
            Map<String, Map<String, Integer>> qrelsMap = loader.loadQrels(outputDir.resolve("qrels.tsv"));
            List<RelevanceJudgment> judgments = new ArrayList<>();
            qrelsMap.forEach((queryId, targetMap) -> {
                targetMap.forEach((corpusId, grade) -> {
                    judgments.add(new RelevanceJudgment(queryId, corpusId, grade));
                });
            });

            log.info("Running dataset validator...");
            DatasetValidator validator = new DatasetValidator();
            DatasetValidator.ValidationResult validationResult = validator.validate(
                    corpus, queries, judgments, entityRelations, temporalChains, hebbianEdges);

            validator.writeReport(outputDir, validationResult);
            log.info("Validation complete. Report written to validation-report.txt");

            if (validationResult.isValid()) {
                log.info("=== SUCCESS: Dataset is fully valid and repaired! ===");
                System.exit(0);
            } else {
                log.error("=== FAILURE: Dataset is STILL invalid ({} errors, {} warnings) ===",
                        validationResult.errors().size(), validationResult.warnings().size());
                System.exit(1);
            }

        } catch (Exception e) {
            log.error("Dataset repair failed due to unexpected error", e);
            System.exit(1);
        }
    }

    private static void writeJsonl(Path file, List<?> records) throws IOException {
        ObjectMapper jsonlMapper = JsonMapper.builder().build(); // compact for JSONL
        StringBuilder sb = new StringBuilder();
        for (Object record : records) {
            sb.append(jsonlMapper.writeValueAsString(record)).append('\n');
        }
        Files.writeString(file, sb.toString());
        log.debug("Wrote {} records to {}", records.size(), file.getFileName());
    }
}
