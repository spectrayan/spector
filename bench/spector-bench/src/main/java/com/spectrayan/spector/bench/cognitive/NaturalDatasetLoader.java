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
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;

/**
 * Lightweight, non-synthetic dataset loader for natural production ingestion.
 * Strictly reads only raw conversation dialogue turns (corpus.jsonl), benchmark queries (queries.jsonl),
 * and persona profiles (persona.json).
 * Intentionally ignores pre-computed hebbian edges, temporal chains, and pre-extracted entity relations.
 */
public final class NaturalDatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(NaturalDatasetLoader.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record NaturalLoadedDataset(
            List<BenchmarkCorpusRecord> corpus,
            List<BenchmarkQuery> queries,
            PersonaDef persona
    ) {}

    public NaturalLoadedDataset load(Path datasetDir) {
        if (!Files.isDirectory(datasetDir)) {
            throw new IllegalArgumentException("Dataset directory does not exist: " + datasetDir);
        }

        List<BenchmarkCorpusRecord> corpus = loadCorpus(datasetDir.resolve("corpus.jsonl"));
        List<BenchmarkQuery> queries = loadQueries(datasetDir.resolve("queries.jsonl"));
        PersonaDef persona = loadPersona(datasetDir.resolve("persona.json"));

        log.info("Natural dataset loaded from {}: {} corpus turns, {} queries, persona={}",
                datasetDir, corpus.size(), queries.size(), persona != null ? persona.name() : "none");

        return new NaturalLoadedDataset(corpus, queries, persona);
    }

    private List<BenchmarkCorpusRecord> loadCorpus(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Corpus file not found: " + path);
        }
        List<BenchmarkCorpusRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    records.add(jsonMapper.readValue(line, BenchmarkCorpusRecord.class));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load corpus file: " + path, e);
        }
        return Collections.unmodifiableList(records);
    }

    private List<BenchmarkQuery> loadQueries(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<BenchmarkQuery> queries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    queries.add(jsonMapper.readValue(line, BenchmarkQuery.class));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load queries file: " + path, e);
        }
        return Collections.unmodifiableList(queries);
    }

    private PersonaDef loadPersona(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return jsonMapper.readValue(path.toFile(), PersonaDef.class);
        } catch (IOException e) {
            log.warn("Failed to load persona file {}: {}", path, e.getMessage());
            return null;
        }
    }
}
