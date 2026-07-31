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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility to perform paired statistical significance testing between Binary and Hypergraph recall runs.
 */
public final class CompareGraphsRunner {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: CompareGraphsRunner <binaryDetailCsv> <hyperDetailCsv>");
            System.exit(1);
            return;
        }

        Path binaryCsv = Path.of(args[0]);
        Path hyperCsv = Path.of(args[1]);

        try {
            Map<String, Double> binaryNdcg = parseDetailCsv(binaryCsv);
            Map<String, Double> hyperNdcg = parseDetailCsv(hyperCsv);

            List<String> commonQueries = new ArrayList<>();
            for (String qId : binaryNdcg.keySet()) {
                if (hyperNdcg.containsKey(qId)) {
                    commonQueries.add(qId);
                }
            }

            if (commonQueries.isEmpty()) {
                System.err.println("Error: No overlapping queries found between files!");
                System.exit(1);
                return;
            }

            double[] binaryArray = new double[commonQueries.size()];
            double[] hyperArray = new double[commonQueries.size()];
            int wins = 0;
            int losses = 0;
            int ties = 0;
            double sumBinary = 0.0;
            double sumHyper = 0.0;

            for (int i = 0; i < commonQueries.size(); i++) {
                String qId = commonQueries.get(i);
                double bVal = binaryNdcg.get(qId);
                double hVal = hyperNdcg.get(qId);
                binaryArray[i] = bVal;
                hyperArray[i] = hVal;

                sumBinary += bVal;
                sumHyper += hVal;

                double diff = hVal - bVal;
                if (diff > 0.0001) {
                    wins++;
                } else if (diff < -0.0001) {
                    losses++;
                } else {
                    ties++;
                }
            }

            double meanBinary = sumBinary / commonQueries.size();
            double meanHyper = sumHyper / commonQueries.size();
            double cohensD = StatisticalTests.cohensD(binaryArray, hyperArray);
            double pValue = StatisticalTests.pairedTTestPValue(binaryArray, hyperArray);

            System.out.println("=================================================================");
            System.out.println("  Spector Recall Evaluation: Binary vs. Hypergraph Significance");
            System.out.println("=================================================================");
            System.out.printf("  Total Queries:     %d\n", commonQueries.size());
            System.out.printf("  Mean Binary nDCG:  %.4f\n", meanBinary);
            System.out.printf("  Mean Hyper nDCG:   %.4f\n", meanHyper);
            System.out.printf("  nDCG Delta:        %+.4f (%+.2f%%)\n",
                    (meanHyper - meanBinary), (meanBinary > 0 ? (meanHyper - meanBinary) / meanBinary * 100.0 : 0.0));
            System.out.printf("  Win/Loss/Tie:      %d / %d / %d\n", wins, losses, ties);
            System.out.printf("  Cohen's d (effect): %.4f\n", cohensD);
            System.out.printf("  Paired t-test p:   %.6f\n", pValue);
            System.out.println("-----------------------------------------------------------------");

            if (pValue < 0.05 && cohensD > 0.2) {
                System.out.println("  VERDICT: Hypergraph is significantly superior to Binary Entity Graph!");
            } else if (pValue < 0.05 && cohensD < -0.2) {
                System.out.println("  VERDICT: Legacy Binary Entity Graph is superior (Hypergraph degraded performance).");
            } else {
                System.out.println("  VERDICT: Inconclusive. No statistically significant difference detected.");
            }
            System.out.println("=================================================================");

        } catch (IOException e) {
            System.err.println("IO Error reading files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Map<String, Double> parseDetailCsv(Path csvFile) throws IOException {
        Map<String, Double> ndcgMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(csvFile)) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String queryId = parts[0].trim();
                    try {
                        double cognitiveNdcg = Double.parseDouble(parts[2].trim());
                        ndcgMap.put(queryId, cognitiveNdcg);
                    } catch (NumberFormatException e) {
                        // ignore malformed lines
                    }
                }
            }
        }
        return ndcgMap;
    }
}
