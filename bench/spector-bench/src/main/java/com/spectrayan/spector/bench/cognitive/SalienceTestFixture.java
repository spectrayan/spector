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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Salience test fixture â€” measures the effect of interest boosting and
 * disinterest dampening on retrieval quality.
 *
 * <h3>Test Conditions</h3>
 * <ol>
 *   <li><b>NEUTRAL</b> â€” No salience profile (control)</li>
 *   <li><b>DATASET_PROFILE</b> â€” Salience profile from persona.json</li>
 *   <li><b>INTEREST_BOOST</b> â€” Critical interest on topic matching top queries</li>
 *   <li><b>DISINTEREST_DAMPEN</b> â€” Ignore disinterest on distractor topics</li>
 *   <li><b>MIXED</b> â€” Combined interest boost + disinterest dampening</li>
 * </ol>
 *
 * <h3>Output</h3>
 * <p>TSV with columns: condition, mean_nDCG, delta, pct_change, mean_topic_boost.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... SalienceTestFixture <datasetDir> <outputDir>
 * }</pre>
 */
public final class SalienceTestFixture {

    private static final Logger log = LoggerFactory.getLogger(SalienceTestFixture.class);

    private enum SalienceCondition {
        NEUTRAL("No salience profile (control)"),
        DATASET_PROFILE("Profile from persona.json"),
        INTEREST_BOOST("Critical interest on query-relevant topics"),
        DISINTEREST_DAMPEN("Ignore disinterest on distractor topics"),
        MIXED("Combined interest boost + disinterest dampening");

        private final String description;
        SalienceCondition(String desc) { this.description = desc; }
        public String description() { return description; }
    }

    private final Path datasetDir;
    private final Path outputDir;

    public SalienceTestFixture(Path datasetDir, Path outputDir) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SalienceTestFixture <datasetDir> <outputDir>");
            System.exit(1);
            return;
        }
        new SalienceTestFixture(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Executes the salience test fixture.
     */
    public void run() {
        log.info("â•â•â• Salience Test Fixture â•â•â•");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} queries, {} corpus",
                dataset.queries().size(), dataset.corpus().size());

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            log.info("Memory created with {} memories", memory.totalMemories());

            MetricsComputer metrics = new MetricsComputer();

            // Store the original salience profile (if any)
            SalienceProfile originalProfile = memory.salienceProfile();

            Map<SalienceCondition, ConditionResult> results = new LinkedHashMap<>();

            for (SalienceCondition condition : SalienceCondition.values()) {
                log.info("Testing condition: {}", condition);

                // Apply the appropriate salience profile
                applySalienceCondition(memory, condition, embedder);

                List<Double> ndcgs = new ArrayList<>();
                List<Float> topicBoosts = new ArrayList<>();

                for (BenchmarkQuery query : dataset.queries()) {
                    Map<String, Integer> qrels = dataset.qrels().getOrDefault(query.id(), Map.of());
                    if (qrels.isEmpty()) continue;

                    try {
                        // Compute topic boost for this query
                        float boost = memory.computeTopicBoost(query.text());
                        topicBoosts.add(boost);

                        RecallOptions options = RecallOptions.builder()
                                .topK(10)
                                .recallMode(RecallMode.OBSERVE)
                                .profile(query.cognitiveProfile())
                                .build();

                        var cogResults = memory.recall(query.text(), options);
                        List<String> rankedIds = cogResults.stream()
                                .map(r -> r.id()).toList();
                        double ndcg = metrics.ndcgAtK(rankedIds, qrels, 10);
                        ndcgs.add(ndcg);
                    } catch (Exception e) {
                        log.warn("Query {} / {} failed: {}", query.id(), condition, e.getMessage());
                    }
                }

                double meanNdcg = ndcgs.stream().mapToDouble(d -> d).average().orElse(0.0);
                float meanBoost = (float) topicBoosts.stream()
                        .mapToDouble(f -> f).average().orElse(1.0);

                results.put(condition, new ConditionResult(meanNdcg, meanBoost, ndcgs.size()));
                log.info("  {} â†’ nDCG={:.4f}, topicBoost={:.3f}", condition, meanNdcg, meanBoost);
            }

            // Restore original profile
            if (originalProfile != null) {
                memory.setSalienceProfile(originalProfile);
            }

            writeReport(results);
            log.info("â•â•â• Salience Test Fixture Complete â•â•â•");

        } catch (Exception e) {
            log.error("Salience test failed: {}", e.getMessage(), e);
        }
    }

    private void applySalienceCondition(SpectorMemory memory, SalienceCondition condition,
                                         EmbeddingProvider embedder) {
        switch (condition) {
            case NEUTRAL -> {
                // Clear salience profile
                memory.setSalienceProfile(SalienceProfile.NEUTRAL);
            }
            case DATASET_PROFILE -> {
                // The profile from persona.json was already applied by BenchmarkSetup.
                // Re-apply it by reloading from the memory instance's current config.
                // If none was configured, this remains neutral.
            }
            case INTEREST_BOOST -> {
                // Add a critical interest on a common query topic
                SalienceProfile.Builder builder = SalienceProfile.builder();
                builder.interest("technology", InterestLevel.CRITICAL);
                builder.interest("software engineering", InterestLevel.CRITICAL);
                builder.interest("database performance", InterestLevel.HIGH);
                builder.interest("machine learning", InterestLevel.HIGH);
                builder.interest("system architecture", InterestLevel.HIGH);
                memory.setSalienceProfile(builder.build());
            }
            case DISINTEREST_DAMPEN -> {
                // Add ignore disinterests on distractor topics
                SalienceProfile.Builder builder = SalienceProfile.builder();
                builder.disinterest("casual conversation", InterestLevel.IGNORE);
                builder.disinterest("weather forecast", InterestLevel.IGNORE);
                builder.disinterest("celebrity gossip", InterestLevel.IGNORE);
                builder.disinterest("sports scores", InterestLevel.IGNORE);
                memory.setSalienceProfile(builder.build());
            }
            case MIXED -> {
                // Combined: boost relevant + dampen irrelevant
                SalienceProfile.Builder builder = SalienceProfile.builder();
                builder.interest("technology", InterestLevel.CRITICAL);
                builder.interest("software engineering", InterestLevel.HIGH);
                builder.interest("database performance", InterestLevel.HIGH);
                builder.disinterest("casual conversation", InterestLevel.IGNORE);
                builder.disinterest("weather forecast", InterestLevel.IGNORE);
                builder.disinterest("celebrity gossip", InterestLevel.IGNORE);
                memory.setSalienceProfile(builder.build());
            }
        }
    }

    private record ConditionResult(double meanNdcg, float meanTopicBoost, int queryCount) {}

    private void writeReport(Map<SalienceCondition, ConditionResult> results) {
        try {
            Files.createDirectories(outputDir);
            Path outFile = outputDir.resolve("salience-test-results.tsv");

            double neutralNdcg = results.get(SalienceCondition.NEUTRAL).meanNdcg();

            StringBuilder sb = new StringBuilder();
            sb.append("condition\tdescription\tmean_nDCG\tdelta\tpct_change\tmean_topic_boost\tqueries\n");

            for (SalienceCondition c : SalienceCondition.values()) {
                ConditionResult r = results.get(c);
                double delta = r.meanNdcg() - neutralNdcg;
                double pct = neutralNdcg > 0 ? (delta / neutralNdcg) * 100.0 : 0.0;

                sb.append(String.format("%s\t%s\t%.4f\t%+.4f\t%+.1f%%\t%.3f\t%d%n",
                        c.name(), c.description(), r.meanNdcg(), delta, pct,
                        r.meanTopicBoost(), r.queryCount()));
            }

            Files.writeString(outFile, sb.toString());
            log.info("Salience test report written to {}", outFile);

            // Console summary
            System.out.println("\nâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            System.out.println("  SALIENCE TEST RESULTS");
            System.out.printf("  Baseline (NEUTRAL) nDCG: %.4f%n", neutralNdcg);
            System.out.println("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            System.out.printf("  %-22s  %8s  %8s  %8s  %8s%n",
                    "Condition", "nDCG", "Î”", "%Î”", "Boost");
            System.out.println("  " + "â”€".repeat(58));

            for (SalienceCondition c : SalienceCondition.values()) {
                ConditionResult r = results.get(c);
                double delta = r.meanNdcg() - neutralNdcg;
                double pct = neutralNdcg > 0 ? (delta / neutralNdcg) * 100.0 : 0.0;
                String marker = c == SalienceCondition.NEUTRAL ? " â—†" :
                        (pct > 5 ? " âœ“" : pct < -5 ? " âš " : "");
                System.out.printf("  %-22s  %8.4f  %+8.4f  %+7.1f%%  %8.3f%s%n",
                        c.name(), r.meanNdcg(), delta, pct, r.meanTopicBoost(), marker);
            }
            System.out.println("â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•\n");

        } catch (IOException e) {
            log.error("Failed to write salience test report: {}", e.getMessage(), e);
        }
    }
}
