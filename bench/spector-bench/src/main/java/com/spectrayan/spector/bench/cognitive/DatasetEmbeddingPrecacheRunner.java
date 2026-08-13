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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Pre-caches embeddings and pre-ingests V3 header memory stores for cognitive benchmark datasets.
 *
 * <p>Reads corpus records and queries from a dataset directory, embeds all text
 * via Ollama (caching to {@code embeddings.bin}), and then initializes the memory
 * instance to create persistent V3 partition bundles on disk.</p>
 */
public final class DatasetEmbeddingPrecacheRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasetEmbeddingPrecacheRunner.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java DatasetEmbeddingPrecacheRunner <dataset-dir> [model-name] [build-ingested-memory]");
            System.err.println("Example: java DatasetEmbeddingPrecacheRunner d:\\git\\spector-datasets\\adhd-diversified\\data nomic-embed-text true");
            System.exit(1);
        }

        Path datasetDir = Paths.get(args[0]);
        String modelName = args.length > 1 && !args[1].isBlank() ? args[1] : "nomic-embed-text";
        boolean buildIngestedMemory = args.length <= 2 || Boolean.parseBoolean(args[2]);

        if (!Files.exists(datasetDir)) {
            System.err.println("Error: Dataset directory does not exist: " + datasetDir);
            System.exit(1);
        }

        log.info("Starting embedding pre-caching for dataset: {}", datasetDir);
        log.info("Embedding model: {}", modelName);

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);

        Set<String> uniqueTexts = new LinkedHashSet<>();
        for (BenchmarkCorpusRecord rec : dataset.corpus()) {
            if (rec.text() != null && !rec.text().isBlank()) {
                uniqueTexts.add(rec.text());
            }
        }
        for (BenchmarkQuery query : dataset.queries()) {
            if (query.text() != null && !query.text().isBlank()) {
                uniqueTexts.add(query.text());
            }
        }

        log.info("Collected {} unique text items from corpus ({}) and queries ({})",
                uniqueTexts.size(), dataset.corpus().size(), dataset.queries().size());

        Path cacheFile = datasetDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.create(modelName);

        try (CachedEmbeddingProvider cachedEmbedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {
            List<String> textList = new ArrayList<>(uniqueTexts);
            int batchSize = 32;
            int total = textList.size();
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<String> batch = textList.subList(i, end);
                cachedEmbedder.embedBatch(batch);

                if ((i + batchSize) % 320 == 0 || end == total) {
                    double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
                    double rate = end / Math.max(0.1, elapsedSec);
                    log.info("Progress: {}/{} items embedded ({}) -- rate: {} items/sec",
                            end, total, String.format("%.1f%%", (end * 100.0) / total), String.format("%.1f", rate));
                }
            }

            log.info("Embedding batch generation complete. Flush saved to: {}", cacheFile);

            if (buildIngestedMemory) {
                log.info("Pre-building V3 ingested-memory partition bundles on disk...");
                // Clear existing stale ingested-memory if present
                Path ingestedMemoryDir = datasetDir.resolve("ingested-memory");
                if (Files.exists(ingestedMemoryDir)) {
                    log.info("Clearing stale ingested-memory directory: {}", ingestedMemoryDir);
                    try (var stream = Files.walk(ingestedMemoryDir)) {
                        stream.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> {
                                    try { Files.delete(p); } catch (Exception ignored) {}
                                });
                    }
                }

                try (BenchmarkSetup setup = new BenchmarkSetup()) {
                    setup.createMemoryInstance(dataset, cachedEmbedder, datasetDir);
                    log.info("V3 ingested-memory partition bundles successfully created at {}", ingestedMemoryDir);
                }
            }
        } catch (Exception e) {
            log.error("Pre-caching failed for {}: {}", datasetDir, e.getMessage(), e);
            System.exit(1);
        }

        log.info("Dataset pre-caching complete for {}", datasetDir);
    }
}
