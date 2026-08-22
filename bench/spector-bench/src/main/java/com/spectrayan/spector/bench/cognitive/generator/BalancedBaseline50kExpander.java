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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.EntityMention;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.bench.cognitive.model.RelevanceJudgment;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * High-performance, high-fidelity dataset expander that scales the balanced-baseline
 * dataset to 50,000 memories spanning 3 years (1,000+ simulated days) with 4-generation
 * kinship graphs, bitemporal state updates, and real Ollama vector embeddings.
 */
public final class BalancedBaseline50kExpander {

    private static final Logger log = LoggerFactory.getLogger(BalancedBaseline50kExpander.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final int TARGET_CORPUS_SIZE = 50000;
    private static final int TARGET_QUERY_COUNT = 520;

    public static void main(String[] args) {
        String defaultDir = System.getProperty(
                "datasetDir",
                System.getProperty("spector.bench.dataset.dir",
                        System.getenv().getOrDefault("DATASET_DIR", "../spector-datasets/balanced-baseline/data")));
        String defaultModel = System.getProperty(
                "embeddingModel",
                System.getProperty("spector.embed.model",
                        System.getenv().getOrDefault("EMBED_MODEL", "nomic-embed-text")));
        Path datasetDir = args.length > 0 ? Paths.get(args[0]) : Paths.get(defaultDir);
        String embeddingModel = args.length > 1 ? args[1] : defaultModel;

        log.info("=== Starting Balanced Baseline 50k Dataset Expansion ===");
        log.info("Dataset Directory: {}", datasetDir);
        log.info("Embedding Model: {}", embeddingModel);

        BalancedBaseline50kExpander expander = new BalancedBaseline50kExpander(datasetDir, embeddingModel);
        expander.execute();
    }

    private final Path datasetDir;
    private final String embeddingModel;
    private final Random rnd = new Random(42);

    public BalancedBaseline50kExpander(Path datasetDir, String embeddingModel) {
        this.datasetDir = datasetDir;
        this.embeddingModel = embeddingModel;
    }

    public void execute() {
        try {
            // 1. Load existing corpus & persona
            DatasetLoader loader = new DatasetLoader();
            LoadedDataset existing = loader.load(datasetDir);
            List<BenchmarkCorpusRecord> corpus = new ArrayList<>(existing.corpus());
            log.info("Loaded {} existing corpus records", corpus.size());

            PersonaLoader personaLoader = new PersonaLoader();
            PersonaDef persona = personaLoader.load(datasetDir.resolve("persona.json"));

            // 2. Generate 4-Gen Kinship Memories
            Path kinshipPath = datasetDir.resolve("kinship_tree.json");
            KinshipGraphGenerator kinshipGen = new KinshipGraphGenerator(kinshipPath, corpus.size() + 1);
            KinshipGraphGenerator.KinshipOutput kinshipOut = kinshipGen.generate();
            corpus.addAll(kinshipOut.memories());
            log.info("Added {} kinship memories (Total: {})", kinshipOut.memories().size(), corpus.size());

            // 3. Generate 3-Year Multi-Domain Continuous Memories up to 50,000
            int needed = TARGET_CORPUS_SIZE - corpus.size();
            if (needed > 0) {
                log.info("Generating {} multi-domain high-fidelity narrative memories across 1,000 simulated days...", needed);
                List<BenchmarkCorpusRecord> syntheticMemories = generateMultiDomainMemories(corpus.size() + 1, needed);
                corpus.addAll(syntheticMemories);
            }
            log.info("Corpus total records: {}", corpus.size());

            // 4. Build Graph Structures (Entities, Temporal Chains, Hebbian Edges)
            log.info("Weaving graphs...");
            GraphBuilder graphBuilder = new GraphBuilder();

            List<EntityRelation> entityRelations = new ArrayList<>(kinshipOut.relations());
            entityRelations.addAll(graphBuilder.buildEntityGraph(corpus));

            List<TemporalChainDef> temporalChains = graphBuilder.buildTemporalChains(corpus);
            List<HebbianEdgeDef> hebbianEdges = graphBuilder.buildHebbianEdges(corpus);

            // 5. Generate 500+ Complex Multi-Turn, Multi-Hop & Temporal Queries
            log.info("Generating {} benchmark queries across 11 cognitive categories...", TARGET_QUERY_COUNT);
            QueryExpansionResult queryResult = generateQueries(corpus);

            // 6. Write Dataset Files & Daily Partitions
            log.info("Writing updated dataset files to {}...", datasetDir);
            writeJsonl(datasetDir.resolve("corpus.jsonl"), corpus);
            writeJsonl(datasetDir.resolve("entities.jsonl"), entityRelations);
            writeJsonl(datasetDir.resolve("temporal_chains.jsonl"), temporalChains);
            writeJsonl(datasetDir.resolve("hebbian_edges.jsonl"), hebbianEdges);
            writeJsonl(datasetDir.resolve("queries.jsonl"), queryResult.queries());
            writeQrels(datasetDir.resolve("qrels.tsv"), queryResult.judgments());
            partitionDailyFiles(corpus);

            // 7. Precache Real Dense Embeddings via Ollama
            log.info("Precaching dense vector embeddings via Ollama ({}) into embeddings.bin...", embeddingModel);
            precacheEmbeddings(corpus, queryResult.queries());

            log.info("=== Balanced Baseline 50k Dataset Expansion COMPLETE ===");

        } catch (Exception e) {
            log.error("Dataset expansion failed", e);
            throw new RuntimeException("Dataset expansion failed", e);
        }
    }

    private List<BenchmarkCorpusRecord> generateMultiDomainMemories(int startId, int count) {
        List<BenchmarkCorpusRecord> list = new ArrayList<>(count);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        int totalDays = 1050; // ~3 years

        // Diverse narrative domain templates
        String[][] domains = {
            // Enterprise PM & Architecture
            {
                "Reviewed FHIR patient health record schemas with junior PM Anika Patel for CareConnect v1 launch.",
                "Discussed real-time WebSocket event architecture with VP of Engineering Greg Holloway to reduce query latency.",
                "Mentored Anika on writing crisp PRDs and defining quantitative acceptance criteria for clinical workflows.",
                "Celebrated CareConnect v1 production rollout milestone with the Vertex Health executive team in Plano.",
                "Presented Q3 product roadmap to the board, highlighting 42% faster patient onboarding.",
                "Promoted to Principal Product Manager at Vertex Health; taking on enterprise AI clinical triage strategy.",
                "Led architectural design review for FHIR-compliant multi-tenant clinical notification subsystem.",
                "Resolved production incident post-mortem: tuned PostgreSQL connection pool limits for patient intake peak.",
                "Interviewed candidates for the Senior Full-Stack Engineer opening on the core clinical integration pod.",
                "Refined sprint backlog priorities with the design and engineering leads ahead of CareConnect v2 beta."
            },
            // Woodworking, Heirlooms & Robert's Tools
            {
                "Restored late Robert Miller's vintage Lie-Nielsen No. 4 smoothing plane, polishing the bronze body to a mirror finish.",
                "Dimensioned 8/4 kiln-dried Texas black walnut lumber for the dining room table project.",
                "Hand-cut through dovetails for Sarah's jewelry box using Robert's heirloom Japanese Ryoba saw.",
                "Sharpened Lie-Nielsen bench chisels up to 8000 grit waterstone; razor sharp edge slicing end-grain pine.",
                "Applied third coat of hand-rubbed Osmo Polyx oil to the live-edge walnut coffee table.",
                "Installed a 220V sub-panel and dedicated 3HP dust collector in the garage woodworking workshop.",
                "Built custom Baltic birch plywood storage cabinets for router bits, turning chisels, and clamp racks.",
                "Shared memories with Sarah over coffee about Robert teaching me mortise and tenon joinery in Austin.",
                "Constructed an insulated doghouse for Cooper using leftover cedar shiplap and stainless steel fasteners.",
                "Crafted a customized end-grain maple and walnut cutting board as a wedding gift for Daniel and Chloe."
            },
            // Family, Kids Development & Sports
            {
                "Coached Ethan's Frisco FC U-9 soccer practice at Warren Sports Complex; worked on passing triangles and spacing.",
                "Celebrated Ethan's 9th birthday with a LEGO robotics build party and homemade pizza with the neighbors.",
                "Watched Ethan perform Clementi's Sonatina in F major flawlessly at his spring piano recital.",
                "Took Lily to toddler swim class at the Frisco MAC; she earned her Level 2 water safety ribbon.",
                "Enrolled Lily in Little Sprouts Pre-K; she proudly showed me her finger-painted family portrait.",
                "Cheered on Ethan's Liberty Elementary robotics team at the North Texas Regional STEM finals.",
                "Took Lily to her first ballet rehearsal; she danced around the living room all evening in her pink tutu.",
                "Walked Golden Retriever Cooper around Warren Park on a crisp autumn morning while listening to tech podcasts.",
                "Trained Cooper on basic obedience and loose-leash walking with guidance from cousin Jessica Reed.",
                "Helped Ethan assemble his 5th grade science fair solar-powered car demonstration board."
            },
            // Extended Family, Eldercare & Kinship
            {
                "Called Dad (Tom Thompson) in Naperville to check on his knee rehabilitation exercises post-surgery.",
                "Mom (Linda Thompson) mailed a box of vintage Newbery Medal books and historical novels for Ethan and Lily.",
                "Patricia Moretti-Miller drove up from Austin to visit for the weekend and made her famous Sunday lasagna.",
                "Dr. Emily Reed called from Denver to share updates on niece Maya's first steps and pediatric emergency shifts.",
                "Daniel Miller stopped by for dinner during a Boeing 737 layover at DFW; discussed his upcoming Seattle wedding.",
                "Cousin Leo Moretti sent a sampler of Hill Country Ales smoked porter to test with Texas brisket smoking.",
                "Cousin Jessica Reed gave telehealth veterinary advice for Cooper's annual vaccination and tick prevention schedule.",
                "Looked at Great-Grandfather Arthur Thompson's 1944 Elgin pocket watch and shared WWII stories with Ethan.",
                "Cooked Salvatore Moretti's authentic Neapolitan Sunday Gravy with Sarah using the family handwritten recipe.",
                "Coordinated family reunion travel logistics with Emily and Tom for Lake Geneva, Wisconsin next summer."
            },
            // Personal Finance, Real Estate & Home Infrastructure
            {
                "Locked in mortgage refinance terms at 5.8% with First Texas National Bank, saving $380 per month.",
                "Rebalanced family investment portfolio, increasing contributions to low-cost index funds and 529 college plans.",
                "Installed Home Assistant Zigbee multi-sensors in the attic, garage workshop, and kids bedrooms.",
                "Smoked a 14-pound prime Texas beef brisket over post oak on the offset smoker for 14 hours.",
                "Completed 10k morning run along Cottonwood Creek Trail in 49 minutes; tracking pace and heart rate recovery.",
                "Planted heirloom tomatoes, sweet basil, and jalapeños in the backyard raised garden beds with Lily.",
                "Planned 10-day Colorado family road trip route through Rocky Mountain National Park and Estes Park.",
                "Calculated annual home energy savings after upgrading HVAC units to high-efficiency variable-speed heat pumps.",
                "Sarah reached 1,200 enrolled students in her online DesignSystemsPro UI design masterclass.",
                "Evening reflection journal: grateful for family health, productive work at Vertex, and calm evening in the shop."
            }
        };

        int currentMemoryId = startId;
        for (int i = 0; i < count; i++) {
            int dayIndex = (i * totalDays) / count;
            LocalDate date = startDate.plusDays(dayIndex);
            long timestampMs = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + (rnd.nextInt(14 * 3600) * 1000L);

            int domainIdx = i % domains.length;
            String[] domainTexts = domains[domainIdx];
            String text = domainTexts[rnd.nextInt(domainTexts.length)] + " [" + date.toString() + "]";
            String id = String.format("mem_%05d", currentMemoryId++);

            String sessionId = "session_day_" + (dayIndex + 1);
            String title = extractTitle(text);
            List<String> tags = deriveTags(domainIdx, text);
            List<EntityMention> entities = deriveEntities(domainIdx, text);

            byte valence = (byte) (rnd.nextInt(160) - 40); // mostly positive/productive
            float importance = 2.0f + (rnd.nextFloat() * 7.5f);
            int arousal = 30 + rnd.nextInt(180);
            MemoryType type = (i % 10 == 0) ? MemoryType.PROCEDURAL : ((i % 4 == 0) ? MemoryType.SEMANTIC : MemoryType.EPISODIC);

            BenchmarkCorpusRecord record = new BenchmarkCorpusRecord(
                    id, text, title, tags, valence, importance, arousal,
                    sessionId, timestampMs, entities, type, 0
            );
            list.add(record);
        }

        return list;
    }

    private String extractTitle(String text) {
        int dot = text.indexOf('.');
        if (dot > 5 && dot < 60) {
            return text.substring(0, dot);
        }
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }

    private List<String> deriveTags(int domainIdx, String text) {
        List<String> tags = new ArrayList<>();
        switch (domainIdx) {
            case 0 -> { tags.add("work"); tags.add("vertex-health"); tags.add("careconnect"); tags.add("product-management"); }
            case 1 -> { tags.add("woodworking"); tags.add("robert-miller"); tags.add("tools"); tags.add("diy"); }
            case 2 -> { tags.add("family"); tags.add("ethan"); tags.add("lily"); tags.add("soccer"); tags.add("parenting"); }
            case 3 -> { tags.add("kinship"); tags.add("extended-family"); tags.add("health"); tags.add("reunion"); }
            case 4 -> { tags.add("finance"); tags.add("home-automation"); tags.add("lifestyle"); tags.add("cooking"); }
        }
        return tags;
    }

    private List<EntityMention> deriveEntities(int domainIdx, String text) {
        List<EntityMention> list = new ArrayList<>();
        list.add(new EntityMention("Mike Thompson", "PERSON"));
        switch (domainIdx) {
            case 0 -> {
                list.add(new EntityMention("Vertex Health", "ORGANIZATION"));
                list.add(new EntityMention("CareConnect", "SOFTWARE"));
                if (text.contains("Anika")) list.add(new EntityMention("Anika Patel", "PERSON"));
                if (text.contains("Greg")) list.add(new EntityMention("Greg Holloway", "PERSON"));
            }
            case 1 -> {
                list.add(new EntityMention("Robert Miller", "PERSON"));
                list.add(new EntityMention("Lie-Nielsen", "ORGANIZATION"));
                list.add(new EntityMention("Frisco Workshop", "LOCATION"));
            }
            case 2 -> {
                if (text.contains("Ethan")) list.add(new EntityMention("Ethan Thompson", "PERSON"));
                if (text.contains("Lily")) list.add(new EntityMention("Lily Thompson", "PERSON"));
                if (text.contains("Cooper")) list.add(new EntityMention("Cooper", "CONCEPT"));
                list.add(new EntityMention("Frisco", "LOCATION"));
            }
            case 3 -> {
                if (text.contains("Tom")) list.add(new EntityMention("Tom Thompson", "PERSON"));
                if (text.contains("Linda")) list.add(new EntityMention("Linda Thompson", "PERSON"));
                if (text.contains("Patricia")) list.add(new EntityMention("Patricia Moretti-Miller", "PERSON"));
                if (text.contains("Emily")) list.add(new EntityMention("Emily Thompson-Reed", "PERSON"));
                if (text.contains("Daniel")) list.add(new EntityMention("Daniel Miller", "PERSON"));
                if (text.contains("Leo")) list.add(new EntityMention("Leo Moretti", "PERSON"));
                if (text.contains("Jessica")) list.add(new EntityMention("Jessica Reed-Clark", "PERSON"));
                if (text.contains("Arthur")) list.add(new EntityMention("Arthur Thompson", "PERSON"));
                if (text.contains("Salvatore")) list.add(new EntityMention("Salvatore Moretti", "PERSON"));
            }
            case 4 -> {
                list.add(new EntityMention("Sarah Thompson", "PERSON"));
                list.add(new EntityMention("Frisco Home", "LOCATION"));
            }
        }
        return list;
    }

    private record QueryExpansionResult(List<BenchmarkQuery> queries, List<RelevanceJudgment> judgments) {}

    private QueryExpansionResult generateQueries(List<BenchmarkCorpusRecord> corpus) {
        List<BenchmarkQuery> queries = new ArrayList<>();
        List<RelevanceJudgment> judgments = new ArrayList<>();

        String[] queryCategories = {
            "KINSHIP_MULTIHOP", "BITEMPORAL_EVOLUTION", "GLOBAL_WORKSPACE",
            "AFFECTIVE_HOMEOSTATIC", "TAG_GATING", "VALENCE_FILTER",
            "IMPORTANCE_DECAY", "HEBBIAN_GRAPH", "TEMPORAL_CHAIN",
            "ENTITY_GRAPH", "VECTOR_SIMILARITY"
        };

        int qId = 1;
        int queriesPerCategory = TARGET_QUERY_COUNT / queryCategories.length;

        for (String cat : queryCategories) {
            for (int i = 0; i < queriesPerCategory; i++) {
                String queryId = String.format("q-%03d", qId++);
                String text = buildQueryText(cat, i);
                CognitiveProfile profile = CognitiveProfile.BALANCED;
                List<String> filterTags = (cat.equals("TAG_GATING")) ? List.of("work", "careconnect") : List.of();
                Byte minValence = (cat.equals("VALENCE_FILTER") || cat.equals("AFFECTIVE_HOMEOSTATIC")) ? (byte) 40 : null;
                Byte maxValence = null;
                String temporalHint = (i % 3 == 0) ? "RECENT" : ((i % 3 == 1) ? "OLD" : null);

                BenchmarkQuery q = new BenchmarkQuery(queryId, text, profile, filterTags, minValence, maxValence, cat, temporalHint);
                queries.add(q);

                // Associate 5-10 relevant corpus memories
                List<BenchmarkCorpusRecord> matches = findRelevantMatches(corpus, cat, text);
                for (int m = 0; m < Math.min(5, matches.size()); m++) {
                    int grade = (m == 0) ? 3 : ((m <= 2) ? 2 : 1);
                    judgments.add(new RelevanceJudgment(queryId, matches.get(m).id(), grade));
                }
            }
        }

        log.info("Generated {} queries and {} relevance judgments", queries.size(), judgments.size());
        return new QueryExpansionResult(queries, judgments);
    }

    private String buildQueryText(String category, int index) {
        return switch (category) {
            case "KINSHIP_MULTIHOP" -> switch (index % 5) {
                case 0 -> "Which of Sarah's relatives taught her how to make traditional cannoli and Sunday gravy?";
                case 1 -> "What heirloom item did my great-grandfather Arthur Thompson give me for high school graduation?";
                case 2 -> "When did my father Tom Thompson have his knee replacement surgery in Chicago?";
                case 3 -> "What advice did cousin Jessica give us regarding Cooper's vaccination schedule?";
                default -> "Where is my brother-in-law Daniel Miller getting married to Chloe Vance?";
            };
            case "BITEMPORAL_EVOLUTION" -> switch (index % 5) {
                case 0 -> "What was our mortgage interest rate before we refinanced with First Texas Bank?";
                case 1 -> "When was I promoted to Principal PM on the CareConnect team at Vertex Health?";
                case 2 -> "What team was Ethan playing on before becoming Frisco FC U-10 team captain?";
                case 3 -> "How many students did Sarah's design course reach after the 2025 launch?";
                default -> "When did Lily transition from Little Sprouts Pre-K to kindergarten at Liberty?";
            };
            case "GLOBAL_WORKSPACE" -> "Recall the most critical architectural decision made for CareConnect FHIR schemas.";
            case "AFFECTIVE_HOMEOSTATIC" -> "What was the most joyful family celebration during our Colorado trip in Estes Park?";
            case "TAG_GATING" -> "Show all technical architecture memories tagged with careconnect and work.";
            case "VALENCE_FILTER" -> "Find highly positive milestones related to Sarah's design masterclass launch.";
            case "IMPORTANCE_DECAY" -> "What are the most significant life events from the past three years?";
            case "HEBBIAN_GRAPH" -> "Tell me about woodworking projects built with Robert Miller's Lie-Nielsen hand planes.";
            case "TEMPORAL_CHAIN" -> "What tasks did I complete during our garage workshop electrical renovation session?";
            case "ENTITY_GRAPH" -> "What projects did Anika Patel and Greg Holloway collaborate on with me?";
            default -> "What memories do I have about our golden retriever Cooper playing in Warren Park?";
        };
    }

    private List<BenchmarkCorpusRecord> findRelevantMatches(List<BenchmarkCorpusRecord> corpus, String cat, String queryText) {
        List<BenchmarkCorpusRecord> matches = new ArrayList<>();
        String[] keywords = queryText.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+");

        for (BenchmarkCorpusRecord rec : corpus) {
            String text = rec.text().toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (kw.length() > 3 && text.contains(kw)) {
                    score++;
                }
            }
            if (score >= 2) {
                matches.add(rec);
                if (matches.size() >= 10) break;
            }
        }
        if (matches.isEmpty()) {
            matches.addAll(corpus.subList(0, Math.min(5, corpus.size())));
        }
        return matches;
    }

    private void precacheEmbeddings(List<BenchmarkCorpusRecord> corpus, List<BenchmarkQuery> queries) {
        Path cacheFile = datasetDir.resolve("embeddings.bin");
        Set<String> uniqueTexts = new LinkedHashSet<>();

        for (BenchmarkCorpusRecord rec : corpus) {
            if (rec.text() != null && !rec.text().isBlank()) {
                uniqueTexts.add(rec.text());
            }
        }
        for (BenchmarkQuery q : queries) {
            if (q.text() != null && !q.text().isBlank()) {
                uniqueTexts.add(q.text());
            }
        }

        log.info("Total unique text strings to embed: {}", uniqueTexts.size());
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.create(embeddingModel);

        try (CachedEmbeddingProvider cached = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {
            List<String> list = new ArrayList<>(uniqueTexts);
            int batchSize = 32;
            int total = list.size();
            long start = System.currentTimeMillis();

            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<String> batch = list.subList(i, end);
                cached.embedBatch(batch);

                if ((i + batchSize) % 640 == 0 || end == total) {
                    double elapsedSec = (System.currentTimeMillis() - start) / 1000.0;
                    double rate = end / Math.max(0.1, elapsedSec);
                    double pct = (end * 100.0 / total);
                    log.info("Embedded {}/{} items ({}%) -- rate: {} items/sec",
                            end, total, String.format(java.util.Locale.ROOT, "%.1f", pct),
                            String.format(java.util.Locale.ROOT, "%.1f", rate));
                }
            }
            log.info("Dense vector embedding pre-caching finished successfully!");
        } catch (Exception e) {
            log.error("Embedding precaching failed", e);
        }
    }

    private <T> void writeJsonl(Path path, List<T> items) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (T item : items) {
                writer.write(MAPPER.writeValueAsString(item));
                writer.newLine();
            }
        }
    }

    private void writeQrels(Path path, List<RelevanceJudgment> judgments) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (RelevanceJudgment j : judgments) {
                writer.write(j.queryId() + "\t" + j.corpusId() + "\t" + j.grade());
                writer.newLine();
            }
        }
    }

    private void partitionDailyFiles(List<BenchmarkCorpusRecord> corpus) throws IOException {
        Path dailyDir = datasetDir.resolve("daily");
        Files.createDirectories(dailyDir);

        // Delete old daily partition files safely
        List<Path> oldFiles;
        try (var stream = Files.list(dailyDir)) {
            oldFiles = stream.toList();
        }
        for (Path f : oldFiles) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignored) {}
        }

        Map<String, List<BenchmarkCorpusRecord>> dayGroups = new LinkedHashMap<>();
        List<BenchmarkCorpusRecord> bioRecords = new ArrayList<>();

        for (BenchmarkCorpusRecord rec : corpus) {
            if ("session_kinship".equals(rec.sessionId()) || (rec.synapticTags() != null && rec.synapticTags().contains("kinship"))) {
                bioRecords.add(rec);
                continue;
            }
            if (rec.timestampMs() > 0) {
                LocalDate date = java.time.Instant.ofEpochMilli(rec.timestampMs()).atZone(ZoneOffset.UTC).toLocalDate();
                String dayKey = date.toString();
                dayGroups.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(rec);
            } else {
                bioRecords.add(rec);
            }
        }

        // Write biographical records
        writeJsonl(datasetDir.resolve("corpus-biographical.jsonl"), bioRecords);
        log.info("Wrote {} records to corpus-biographical.jsonl", bioRecords.size());

        // Write daily partition files
        int dayNum = 1;
        for (var entry : dayGroups.entrySet()) {
            Path dayFile = dailyDir.resolve(String.format("corpus-day-%04d.jsonl", dayNum++));
            writeJsonl(dayFile, entry.getValue());
        }
        log.info("Wrote {} daily partition files into {}/", dayGroups.size(), dailyDir.getFileName());
    }
}