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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.EntityMention;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.memory.model.MemoryType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates structured 4-generation kinship memories and typed entity relationships.
 *
 * <p>Reads {@code kinship_tree.json} to inject rich biographical anecdotes for
 * great-grandparents, grandparents, siblings, cousins, and children into the benchmark
 * corpus, weaving explicit multi-hop entity graph edges for genealogical recall testing.</p>
 */
public final class KinshipGraphGenerator {

    private static final Logger log = LoggerFactory.getLogger(KinshipGraphGenerator.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path kinshipPath;
    private int nextMemoryId;

    public KinshipGraphGenerator(Path kinshipPath, int startingMemoryId) {
        this.kinshipPath = kinshipPath;
        this.nextMemoryId = startingMemoryId;
    }

    public record KinshipOutput(
            List<BenchmarkCorpusRecord> memories,
            List<EntityRelation> relations
    ) {}

    /**
     * Generates genealogical memories and relations from the kinship tree definition.
     */
    public KinshipOutput generate() {
        if (kinshipPath == null || !Files.exists(kinshipPath)) {
            log.info("No kinship_tree.json found at {}; skipping dedicated kinship generation", kinshipPath);
            return new KinshipOutput(List.of(), List.of());
        }

        try {
            String json = Files.readString(kinshipPath);
            Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {});
            String focusPerson = (String) root.getOrDefault("focusPerson", "Mike Thompson");
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> generations =
                    (Map<String, List<Map<String, Object>>>) root.get("generations");

            if (generations == null) {
                return new KinshipOutput(List.of(), List.of());
            }

            List<BenchmarkCorpusRecord> records = new ArrayList<>();
            List<EntityRelation> relations = new ArrayList<>();

            // Process Gen 1: Great-Grandparents
            List<Map<String, Object>> gen1 = generations.get("generation_1_great_grandparents");
            if (gen1 != null) {
                for (Map<String, Object> person : gen1) {
                    processPerson(person, focusPerson, "GREAT_GRANDPARENT_OF", records, relations, -30);
                }
            }

            // Process Gen 2: Grandparents & Parents
            List<Map<String, Object>> gen2 = generations.get("generation_2_grandparents");
            if (gen2 != null) {
                for (Map<String, Object> person : gen2) {
                    processPerson(person, focusPerson, "GRANDPARENT_OF", records, relations, -15);
                }
            }

            // Process Gen 3: Parents, Siblings & Extended
            List<Map<String, Object>> gen3 = generations.get("generation_3_parents_and_extended");
            if (gen3 != null) {
                for (Map<String, Object> person : gen3) {
                    String rel = (String) person.get("relationship");
                    String relationType = "RELATED_TO";
                    if (rel != null) {
                        if (rel.contains("Wife") || rel.contains("Spouse")) relationType = "SPOUSE_OF";
                        else if (rel.contains("Sister") || rel.contains("Brother")) relationType = "SIBLING_OF";
                        else if (rel.contains("Cousin")) relationType = "COUSIN_OF";
                    }
                    processPerson(person, focusPerson, relationType, records, relations, -5);
                }
            }

            // Process Gen 4: Children
            List<Map<String, Object>> gen4 = generations.get("generation_4_children");
            if (gen4 != null) {
                for (Map<String, Object> person : gen4) {
                    processPerson(person, focusPerson, "CHILD_OF", records, relations, -1);
                }
            }

            log.info("Generated {} kinship memories and {} genealogical relations from {}",
                    records.size(), relations.size(), kinshipPath.getFileName());

            return new KinshipOutput(records, relations);

        } catch (IOException e) {
            log.error("Failed to parse kinship tree at {}: {}", kinshipPath, e.getMessage());
            return new KinshipOutput(List.of(), List.of());
        }
    }

    private void processPerson(Map<String, Object> person,
                               String focusPerson,
                               String relationType,
                               List<BenchmarkCorpusRecord> records,
                               List<EntityRelation> relations,
                               int yearsOffset) {
        String name = (String) person.get("name");
        String relationship = (String) person.get("relationship");
        String location = (String) person.get("location");

        if (name == null || name.equals(focusPerson)) {
            return;
        }

        List<String> memoryIdsForPerson = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<String> keyMemories = (List<String>) person.get("keyMemories");
        if (keyMemories != null) {
            for (String mem : keyMemories) {
                String memoryId = String.format("mem_%05d", nextMemoryId++);
                memoryIdsForPerson.add(memoryId);

                long timestamp = LocalDate.now().plusYears(yearsOffset)
                        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

                List<EntityMention> mentions = new ArrayList<>();
                mentions.add(new EntityMention(name, "PERSON"));
                mentions.add(new EntityMention(focusPerson, "PERSON"));
                if (location != null) {
                    mentions.add(new EntityMention(location, "LOCATION"));
                }

                BenchmarkCorpusRecord record = new BenchmarkCorpusRecord(
                        memoryId,
                        mem,
                        "Memory of " + name,
                        List.of("family", "kinship", relationship.toLowerCase().replaceAll("[^a-z0-9]", "-")),
                        (byte) 60,
                        5.5f,
                        50,
                        "session_kinship",
                        timestamp,
                        mentions,
                        MemoryType.EPISODIC,
                        0
                );
                records.add(record);
            }
        }

        // Add explicit entity relation
        if (!memoryIdsForPerson.isEmpty()) {
            EntityMention fromMention = new EntityMention(name, "PERSON");
            EntityMention toMention = new EntityMention(focusPerson, "PERSON");
            relations.add(new EntityRelation(fromMention, toMention, relationType, memoryIdsForPerson));
        }
    }
}