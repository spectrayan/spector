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
package com.spectrayan.spector.node.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.MediaTypeNames;
import com.linecorp.armeria.common.multipart.MultipartFile;
import com.linecorp.armeria.server.annotation.Consumes;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Delete;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.Put;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.pipeline.TagExtractor;
import com.spectrayan.spector.node.api.ApiModule;
import com.spectrayan.spector.node.api.dto.FileMemoryRequest;
import com.spectrayan.spector.node.api.dto.IntrospectRequest;
import com.spectrayan.spector.node.api.dto.IntrospectResponseDto;
import com.spectrayan.spector.node.api.dto.MemoryRequest;
import com.spectrayan.spector.node.api.dto.RecallRequest;
import com.spectrayan.spector.node.api.dto.RecallResponseDto;
import com.spectrayan.spector.node.api.dto.ReflectResponseDto;
import com.spectrayan.spector.node.api.dto.ReminderRequest;
import com.spectrayan.spector.node.api.dto.ReminderResponseDto;
import com.spectrayan.spector.node.api.dto.ScratchpadRequest;
import com.spectrayan.spector.node.api.dto.WhyNotRequest;
import com.spectrayan.spector.node.api.dto.WhyNotResponseDto;
import com.spectrayan.spector.node.exception.ApiExceptionHandler;
import com.spectrayan.spector.node.service.MemoryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.spectrayan.spector.runtime.IngestionHandler;
import com.spectrayan.spector.ingestion.IngestionResult;
import com.spectrayan.spector.node.service.IngestionTask;
import com.spectrayan.spector.node.service.IngestionTaskService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Armeria endpoints for cognitive memory v1 REST API.
 *
 * <p>Registered under {@code /api/v1/memory}. All mutating endpoints are
 * thread-safe and operate asynchronously on virtual threads via
 * {@link MemoryService}.</p>
 */
@ExceptionHandler(ApiExceptionHandler.class)
public class MemoryEndpoint implements ApiModule {

    private static final Logger log = LoggerFactory.getLogger(MemoryEndpoint.class);

    private final MemoryService memoryService;
    private final IngestionHandler ingestionHandler; // nullable — only when runtime is present
    private final IngestionTaskService taskService;


    public MemoryEndpoint(MemoryService memoryService) {
        this(memoryService, null, new IngestionTaskService(
                new com.spectrayan.spector.node.event.SpectorEventBus(), "local"));
    }

    public MemoryEndpoint(MemoryService memoryService, IngestionHandler ingestionHandler,
                          IngestionTaskService taskService) {
        this.memoryService = memoryService;
        this.ingestionHandler = ingestionHandler;
        this.taskService = taskService;
    }

    @Override
    public String pathPrefix() {
        return "/memory";
    }

    @Post("/remember")
    public HttpResponse remember(MemoryRequest request) {
        MemoryType tier = MemoryType.valueOf(request.effectiveTier());
        MemorySource source = MemorySource.valueOf(request.effectiveSource());
        IngestionHints hints = null;
        if (request.hasCognitiveHints()) {
            float interest = request.interest() != null ? request.interest() : 0f;
            float challenge = request.challenge() != null ? request.challenge() : 0f;
            float urgency = request.urgency() != null ? request.urgency() : 0f;
            int valence = request.valence() != null ? request.valence() : 0;
            int arousal = request.arousal() != null ? request.arousal() : 0;
            hints = new IngestionHints(interest, challenge, urgency,
                    (byte) Math.clamp(valence, -128, 127),
                    (byte) Math.clamp(arousal, 0, 255));
        }

        // Auto-generate tags from text content if none were provided.
        // Use the configured tag extractor (LLM when available, content-based fallback).
        String[] tags = request.tagsArray();
        if (tags.length == 0 && request.text() != null && !request.text().isBlank()) {
            TagExtractor extractor = memoryService.memory().target().tagExtractor();
            com.spectrayan.spector.memory.pipeline.TagExtractionResult extractionResult = 
                    extractor.extractWithContext(request.id(), request.text());
            tags = extractionResult.tags();
            log.info("Auto-generated {} tags for memory via {}: [{}] (valence={}, arousal={})",
                    tags.length, extractor.getClass().getSimpleName(), String.join(", ", tags),
                    extractionResult.valence(), Byte.toUnsignedInt(extractionResult.arousal()));
            
            if (hints == null && extractionResult.hasEmotionalContext()) {
                hints = new IngestionHints(0f, 0f, 0f, extractionResult.valence(), extractionResult.arousal());
            } else if (hints != null && hints.valence() == 0 && hints.effectiveArousal() == 0 && extractionResult.hasEmotionalContext()) {
                hints = new IngestionHints(hints.interest(), hints.challenge(), hints.urgency(),
                        extractionResult.valence(), extractionResult.arousal());
            }
        }

        String effectiveId;
        if (request.id() != null && !request.id().isBlank()) {
            effectiveId = request.id();
        } else {
            effectiveId = new com.spectrayan.spector.memory.id.TsidGenerator().generate();
        }

        // Truncate description for display
        String desc = request.text() != null && request.text().length() > 60
                ? request.text().substring(0, 60) + "\u2026" : request.text();

        var task = new IngestionTask(
                effectiveId, "Remember: " + desc, IngestionTask.TaskType.REMEMBER);
        task.setTotalChunks(1);

        final String[] finalTags = tags;
        final IngestionHints finalHints = hints;
        taskService.submit(task, () -> {
            memoryService.remember(effectiveId, request.text(), tier, source, finalHints, finalTags).join();
            taskService.reportChunkStored(task);
        });

        return HttpResponse.of(HttpStatus.ACCEPTED, MediaType.JSON_UTF_8,
                "{\"taskId\":\"" + task.taskId()
                + "\",\"id\":\"" + effectiveId
                + "\",\"status\":\"accepted\"}");
    }

    // ── File / Directory Ingestion ────────────────────────────────

    @Consumes(MediaTypeNames.MULTIPART_FORM_DATA)
    @Post("/ingest-file")
    public HttpResponse ingestFile(
            @Param("file") MultipartFile file,
            @Param("tier") @Default("SEMANTIC") String tier,
            @Param("source") @Default("OBSERVED") String source) {
        if (ingestionHandler == null) {
            return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.PLAIN_TEXT_UTF_8,
                    "File ingestion requires SpectorRuntime (not available in standalone engine mode)");
        }

        String originalName = file.filename() != null ? file.filename() : "uploaded-file";
        Path tempFile = file.path();
        long fileSize = tempFile.toFile().length();
        log.info("File upload received: name={}, size={} bytes", originalName, fileSize);

        var tsidGen = new com.spectrayan.spector.memory.id.TsidGenerator();
        String documentId = tsidGen.generate();

        var task = new IngestionTask(
                documentId, "Ingest: " + originalName, IngestionTask.TaskType.FILE_INGEST);

        // Read file content on the request thread — Armeria cleans up multipart
        // temp files after the response is sent, so the background thread may
        // find the file already deleted.
        final String content;
        try {
            content = java.nio.file.Files.readString(tempFile);
        } catch (Exception e) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "Failed to read uploaded file: " + e.getMessage());
        }
        if (content.isBlank()) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "Uploaded file is empty");
        }

        // Detect content type from file extension
        String contentType = detectContentType(originalName);

        // Provenance tags only — per-chunk content tags are extracted by
        // CognitiveIngestionTarget's built-in TagExtractor on each chunk's text,
        // not from the full file. This ensures each chunk gets relevant tags.
        String[] provenanceTags = new String[] { originalName };
        log.info("File provenance tags: {} for {}", java.util.Arrays.toString(provenanceTags), originalName);

        // Build rich metadata via IngestionContext — flows through to
        // index.register() so every chunk carries file-level metadata.
        var ingestionContext = com.spectrayan.spector.memory.model.IngestionContext.builder()
                .metadata("fileName", originalName)
                .metadata("file_size_bytes", String.valueOf(fileSize))
                .metadata("content_type", contentType)
                .metadata("ingestion_timestamp", String.valueOf(System.currentTimeMillis()))
                .sourceModality(com.spectrayan.spector.memory.model.SourceModality.TEXT)
                .build();

        taskService.submit(task, () -> {
            try {
                MemoryType memTier = MemoryType.valueOf(tier);
                MemorySource memSource = MemorySource.valueOf(source);

                // Use IngestionContext-based remember() which:
                // 1. Routes through memory() → EnterpriseMemoryService.getActiveMemory()
                //    for per-user namespace isolation
                // 2. Passes metadata through to index.register() via the 6-arg overload
                // 3. Lets CognitiveIngestionTarget extract per-chunk tags internally
                memoryService.memory().remember(documentId, content, memTier, memSource,
                        ingestionContext, provenanceTags).join();

                // Count chunks stored (memory handles chunking internally)
                int chunksStored = 1;
                var memory = memoryService.memory();
                if (memory != null) {
                    var index = memory.admin().index();
                    for (int ci = 0; ci < 1000; ci++) {
                        if (index.locate(documentId + "::chunk-" + ci) != null) {
                            chunksStored = ci + 1;
                        } else {
                            break;
                        }
                    }
                }

                task.setTotalChunks(chunksStored);
                for (int i = 0; i < chunksStored; i++) {
                    taskService.reportChunkStored(task);
                }

                log.info("File ingested: {} (id={}) -> {} chunks, metadata={}", originalName,
                        documentId, chunksStored, ingestionContext.metadata());
            } catch (Exception e) {
                throw new RuntimeException("File ingestion failed for '" + originalName + "': " + e.getMessage(), e);
            }
        });

        return HttpResponse.of(HttpStatus.ACCEPTED, MediaType.JSON_UTF_8,
                "{\"taskId\":\"" + task.taskId()
                + "\",\"fileName\":\"" + originalName
                + "\",\"documentId\":\"" + documentId
                + "\",\"status\":\"accepted\"}");
    }

    @Post("/ingest-directory")
    public HttpResponse ingestDirectory(FileMemoryRequest request) {
        if (ingestionHandler == null) {
            return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.PLAIN_TEXT_UTF_8,
                    "Directory ingestion requires SpectorRuntime (not available in standalone engine mode)");
        }

        Path dirPath = Path.of(request.path());
        if (!Files.isDirectory(dirPath)) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "Path is not a directory: " + request.path());
        }

        String filePattern = request.filePattern() != null ? request.filePattern() : "**/*.md,**/*.txt,**/*.java";
        int chunkSize = request.chunkSize() > 0 ? request.chunkSize() : 800;
        int chunkOverlap = request.chunkOverlap() > 0 ? request.chunkOverlap() : 100;
        String skipDirs = request.skipDirs() != null ? request.skipDirs() : ".git,.idea,.mvn,target,node_modules";

        var tsidGen = new com.spectrayan.spector.memory.id.TsidGenerator();
        String taskId = tsidGen.generate();

        var task = new IngestionTask(
                taskId, "Ingest dir: " + request.path(), IngestionTask.TaskType.DIR_INGEST);

        log.info("Directory ingestion submitted: path={}, pattern={}, chunkSize={}, taskId={}",
                request.path(), filePattern, chunkSize, taskId);

        taskService.submit(task, () -> {
            try {
                List<IngestionResult> results = ingestionHandler.ingest(
                        dirPath, filePattern, chunkSize, chunkOverlap, skipDirs);

                int totalChunks = results.stream().mapToInt(IngestionResult::chunksStored).sum();
                task.setTotalChunks(totalChunks);
                for (int i = 0; i < totalChunks; i++) {
                    taskService.reportChunkStored(task);
                }

                long totalFailures = results.stream()
                        .filter(r -> !r.failures().isEmpty()).count();
                for (int i = 0; i < totalFailures; i++) {
                    task.incrementFailures();
                }

                log.info("Directory ingested: {} files, {} chunks, {} failures",
                        results.size(), totalChunks, totalFailures);
            } catch (Exception e) {
                throw new RuntimeException("Directory ingestion failed: " + e.getMessage(), e);
            }
        });

        return HttpResponse.of(HttpStatus.ACCEPTED, MediaType.JSON_UTF_8,
                "{\"taskId\":\"" + taskId
                + "\",\"path\":\"" + request.path()
                + "\",\"status\":\"accepted\"}");
    }

    @Post("/recall")
    public HttpResponse recall(RecallRequest request) {
        long start = System.currentTimeMillis();
        var profile = com.spectrayan.spector.memory.model.CognitiveProfile.valueOf(request.effectiveProfile());
        var builder = RecallOptions.builder().profile(profile).topK(request.effectiveTopK());
        if (request.queryValence() != null) {
            builder.queryValence((byte) Math.clamp(request.queryValence(), -128, 127));
        }
        var options = builder.build();

        var results = memoryService.recall(request.query(), options);
        long elapsed = System.currentTimeMillis() - start;

        var response = new RecallResponseDto(
                results,
                memoryService.memory().totalMemories(),
                elapsed,
                request.effectiveProfile()
        );
        return HttpResponse.ofJson(response);
    }

    @Delete("/{id}")
    public HttpResponse forget(@Param("id") String id) {
        memoryService.forget(id);
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Forgotten memory: " + id);
    }

    @Post("/{id}/reinforce")
    public HttpResponse reinforce(@Param("id") String id, JsonNode body) {
        int valence = body.has("valence") ? body.get("valence").asInt() : 0;
        memoryService.reinforce(id, (byte) Math.clamp(valence, -128, 127));
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Reinforced memory: " + id + " with valence " + valence);
    }

    @Post("/{id}/suppress")
    public HttpResponse suppress(@Param("id") String id, JsonNode body) {
        String action = body.has("action") ? body.get("action").asText().toUpperCase() : "SUPPRESS";
        String reason = body.has("reason") ? body.get("reason").asText() : "";
        if ("UNSUPPRESS".equals(action)) {
            memoryService.unsuppress(id);
            return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Unsuppressed memory: " + id);
        } else {
            memoryService.suppress(id, reason);
            return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Suppressed memory: " + id + (reason.isEmpty() ? "" : " (reason: " + reason + ")"));
        }
    }

    @Post("/{id}/resolve")
    public HttpResponse resolve(@Param("id") String id, JsonNode body) {
        boolean resolved = body.has("resolved") ? body.get("resolved").asBoolean() : true;
        if (resolved) {
            memoryService.markResolved(id);
            return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Resolved memory: " + id);
        } else {
            memoryService.markUnresolved(id);
            return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Unresolved memory: " + id);
        }
    }

    @Get("/status")
    public HttpResponse status() {
        var status = memoryService.getStatus();
        return HttpResponse.ofJson(status);
    }

    @Get("/topology-stats")
    public HttpResponse topologyStats() {
        var stats = memoryService.getTopologyStats();
        return HttpResponse.ofJson(stats);
    }

    // ── New endpoints (API parity with MCP tools) ───────────────────────

    @Post("/introspect")
    public HttpResponse introspect(IntrospectRequest request) {
        var insight = memoryService.introspect(request.topic());
        return HttpResponse.ofJson(IntrospectResponseDto.from(insight));
    }

    @Post("/reminder")
    public HttpResponse reminder(ReminderRequest request) {
        var reminder = memoryService.scheduleReminder(
                request.text(),
                Duration.ofSeconds(request.delaySeconds()),
                request.tagsArray());
        return HttpResponse.ofJson(ReminderResponseDto.from(reminder, request.delaySeconds()));
    }

    @Post("/scratchpad")
    public HttpResponse scratchpad(ScratchpadRequest request) {
        memoryService.scratchpad(request.text()).join();
        return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8,
                "Stored in working memory scratchpad");
    }

    @Post("/why-not")
    public HttpResponse whyNot(WhyNotRequest request) {
        var options = RecallOptions.builder().topK(request.effectiveTopK()).build();
        var explanation = memoryService.whyNot(request.memoryId(), request.query(), options);
        return HttpResponse.ofJson(WhyNotResponseDto.from(explanation));
    }

    @Post("/reflect")
    public HttpResponse reflect() {
        var report = memoryService.reflect();
        return HttpResponse.ofJson(ReflectResponseDto.from(report));
    }

    // ── Single Memory Detail ────────────────────────────────────

    @Get("/{id}")
    public HttpResponse getMemory(@Param("id") String id) {
        var row = memoryService.getMemoryById(id);
        if (row == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                    "Memory not found: " + id);
        }
        return HttpResponse.ofJson(row);
    }

    // ── Table View & Vacuum (Feature 5) ─────────────────────────

    @Get("/table")
    public HttpResponse table(
            @Param("page") @com.linecorp.armeria.server.annotation.Default("0") int page,
            @Param("pageSize") @com.linecorp.armeria.server.annotation.Default("50") int pageSize,
            @Param("tier") @com.linecorp.armeria.server.annotation.Default("") String tier,
            @Param("tombstoned") @com.linecorp.armeria.server.annotation.Default("false") boolean tombstoned) {
        String tierFilter = tier.isBlank() ? null : tier;
        var table = memoryService.getMemoryTable(page, pageSize, tierFilter, tombstoned);
        return HttpResponse.ofJson(table);
    }

    @Post("/vacuum")
    public HttpResponse vacuum(com.fasterxml.jackson.databind.JsonNode body) {
        String tierName = body.has("tier") ? body.get("tier").asText() : "SEMANTIC";
        MemoryType tier = MemoryType.valueOf(tierName.toUpperCase());
        var result = memoryService.vacuum(tier);
        if (result == null) {
            return HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8,
                    "No compaction needed for tier: " + tierName);
        }
        return HttpResponse.ofJson(result);
    }

    // ── Graph API (Phase 5) ─────────────────────────────────────

    @Get("/{id}/graph")
    public HttpResponse memoryGraph(
            @Param("id") String id,
            @Param("depth") @com.linecorp.armeria.server.annotation.Default("2") int depth) {
        int clampedDepth = Math.max(1, Math.min(3, depth));
        var graph = memoryService.getMemoryGraph(id, clampedDepth);
        if (graph == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                    "Memory not found: " + id);
        }
        return HttpResponse.ofJson(graph);
    }

    @Get("/graph/overview")
    public HttpResponse graphOverview(
            @Param("maxNodes") @com.linecorp.armeria.server.annotation.Default("100") int maxNodes) {
        int clampedMax = Math.max(10, Math.min(500, maxNodes));
        var graph = memoryService.getGraphOverview(clampedMax);
        return HttpResponse.ofJson(graph);
    }

    // ── Update Memory ───────────────────────────────────────────

    @Put("/{id}")
    public HttpResponse updateMemory(@Param("id") String id, JsonNode body) {
        var row = memoryService.getMemoryById(id);
        if (row == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                    "Memory not found: " + id);
        }

        String newText = body.has("text") ? body.get("text").asText() : null;
        String[] newTags = null;
        if (body.has("tags") && body.get("tags").isArray()) {
            var tagsNode = body.get("tags");
            newTags = new String[tagsNode.size()];
            for (int i = 0; i < tagsNode.size(); i++) {
                newTags[i] = tagsNode.get(i).asText();
            }
        }

        // Reconsolidation: delegate to service for direct re-embed + index update
        memoryService.updateMemoryInPlace(id, newText, newTags);

        return HttpResponse.of(HttpStatus.OK, MediaType.JSON,
                "{\"status\":\"reconsolidated\",\"id\":\"" + id + "\"}");
    }

    // ── Vector Embedding ────────────────────────────────────────

    @Get("/{id}/vector")
    public HttpResponse getVector(@Param("id") String id) {
        var memory = memoryService.memory();
        var admin = memory.admin();
        var index = admin.index();
        var loc = index.locate(id);
        if (loc == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                    "Memory not found: " + id);
        }

        var store = admin.tierRouter().get(loc.type());
        if (!(store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats)) {
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.PLAIN_TEXT_UTF_8,
                    "Store not accessible for tier: " + loc.type());
        }

        var layout = ats.layout();
        long vecOffset = layout.vectorOffset(loc.offset());
        // dimension = stride - headerBytes; headerBytes = vectorOffset(0)
        int headerSize = (int) layout.vectorOffset(0);
        int dimension = layout.stride() - headerSize;
        byte[] vecBytes = new byte[dimension];

        // Read INT8 quantized vector from the memory segment
        java.lang.foreign.MemorySegment.copy(
                ats.segment(), java.lang.foreign.ValueLayout.JAVA_BYTE, vecOffset,
                vecBytes, 0, dimension);

        // Convert to int array for JSON
        int[] values = new int[dimension];
        for (int i = 0; i < dimension; i++) {
            values[i] = vecBytes[i];  // signed INT8 (-128 to 127)
        }

        return HttpResponse.ofJson(Map.of(
                "memoryId", id,
                "dimension", dimension,
                "values", values
        ));
    }

    // ── Bulk Import Admin Endpoints ──────────────────────────────

    @Post("/admin/import/hebbian-edges")
    public HttpResponse importHebbianEdges(
            @com.linecorp.armeria.server.annotation.RequestObject
            java.util.List<java.util.Map<String, Object>> edges) {
        var result = memoryService.bulkImportHebbianEdges(edges);
        return HttpResponse.ofJson(result);
    }

    @Post("/admin/import/temporal-chains")
    public HttpResponse importTemporalChains(
            @com.linecorp.armeria.server.annotation.RequestObject
            java.util.List<java.util.Map<String, Object>> chains) {
        var result = memoryService.bulkImportTemporalChains(chains);
        return HttpResponse.ofJson(result);
    }

    @Post("/admin/import/entity-relations")
    public HttpResponse importEntityRelations(
            @com.linecorp.armeria.server.annotation.RequestObject
            java.util.List<java.util.Map<String, Object>> relations) {
        var result = memoryService.bulkImportEntityRelations(relations);
        return HttpResponse.ofJson(result);
    }

    // ── Task Status Endpoints ────────────────────────────────────

    @Get("/tasks")
    public HttpResponse listTasks() {
        var tasks = taskService.getAllTasks().stream().map(t -> Map.of(
                "taskId", (Object) t.taskId(),
                "description", (Object) t.description(),
                "type", (Object) t.type().name(),
                "status", (Object) t.status().name(),
                "chunksStored", (Object) t.chunksStored(),
                "totalChunks", (Object) t.totalChunks(),
                "failures", (Object) t.failures(),
                "progressPercent", (Object) t.progressPercent(),
                "durationMs", (Object) t.durationMs(),
                "startedAt", (Object) t.startedAt().toString()
        )).toList();
        return HttpResponse.ofJson(tasks);
    }

    @Get("/tasks/{taskId}")
    public HttpResponse getTask(@Param("taskId") String taskId) {
        var task = taskService.getTask(taskId);
        if (task == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
                    "Task not found: " + taskId);
        }
        return HttpResponse.ofJson(Map.of(
                "taskId", task.taskId(),
                "description", task.description(),
                "type", task.type().name(),
                "status", task.status().name(),
                "chunksStored", task.chunksStored(),
                "totalChunks", task.totalChunks(),
                "failures", task.failures(),
                "progressPercent", task.progressPercent(),
                "durationMs", task.durationMs(),
                "startedAt", task.startedAt().toString()
        ));
    }

    // ── Salience Profile Endpoints ────────────────────────────────

    @Get("/salience")
    public HttpResponse getSalienceProfile() {
        var profile = memoryService.memory().salienceProfile();
        if (profile == null || profile.isNeutral()) {
            return HttpResponse.ofJson(Map.of(
                    "status", "neutral",
                    "message", "No salience profile active"));
        }

        var interests = profile.interests().stream()
                .map(d -> Map.of("topic", d.topic(), "level", d.level().name(),
                        "multiplier", d.level().multiplier()))
                .toList();
        var disinterests = profile.disinterests().stream()
                .map(d -> Map.of("topic", d.topic(), "level", d.level().name(),
                        "multiplier", d.level().multiplier()))
                .toList();

        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("status", "active");
        result.put("interests", interests);
        result.put("disinterests", disinterests);
        result.put("hasIcnuOverride", profile.hasIcnuOverride());
        result.put("hasPersona", profile.hasPersona());
        result.put("flashbulbThreshold", profile.flashbulbThreshold());
        result.put("recencyWeight", profile.recencyWeight());
        result.put("similarityThreshold", profile.similarityThreshold());

        if (profile.hasIcnuOverride()) {
            result.put("icnuWeights", Map.of(
                    "interest", profile.icnuWeights().interest(),
                    "challenge", profile.icnuWeights().challenge(),
                    "novelty", profile.icnuWeights().novelty(),
                    "urgency", profile.icnuWeights().urgency()));
        }
        if (profile.hasPersona()) {
            var p = profile.persona();
            var persona = new java.util.LinkedHashMap<String, Object>();
            if (p.occupation() != null) persona.put("occupation", p.occupation());
            if (!p.bigFive().isNeutral()) {
                persona.put("bigFive", Map.of(
                        "openness", p.bigFive().openness(),
                        "conscientiousness", p.bigFive().conscientiousness(),
                        "extraversion", p.bigFive().extraversion(),
                        "agreeableness", p.bigFive().agreeableness(),
                        "neuroticism", p.bigFive().neuroticism()));
            }
            persona.put("stressResponse", p.stressResponse().name());
            persona.put("hasEmbeddings", p.hasEmbeddings());
            result.put("persona", persona);
        }
        return HttpResponse.ofJson(result);
    }

    @Post("/salience")
    public HttpResponse setSalienceProfile(JsonNode body) {
        var builder = com.spectrayan.spector.memory.model.SalienceProfile.builder();

        // Parse interests
        if (body.has("interests") && body.get("interests").isArray()) {
            for (var node : body.get("interests")) {
                String topic = node.has("topic") ? node.get("topic").asText() : "";
                String level = node.has("level") ? node.get("level").asText() : "HIGH";
                if (!topic.isBlank()) {
                    builder.interest(topic,
                            com.spectrayan.spector.memory.model.InterestLevel.valueOf(level.toUpperCase()));
                }
            }
        }

        // Parse disinterests
        if (body.has("disinterests") && body.get("disinterests").isArray()) {
            for (var node : body.get("disinterests")) {
                String topic = node.has("topic") ? node.get("topic").asText() : "";
                String level = node.has("level") ? node.get("level").asText() : "LOW";
                if (!topic.isBlank()) {
                    builder.disinterest(topic,
                            com.spectrayan.spector.memory.model.InterestLevel.valueOf(level.toUpperCase()));
                }
            }
        }

        // Parse ICNU weights
        if (body.has("icnuWeights")) {
            var w = body.get("icnuWeights");
            builder.icnuWeights(new com.spectrayan.spector.memory.neurodivergent.IcnuWeights(
                    (float) w.path("interest").asDouble(0.25),
                    (float) w.path("challenge").asDouble(0.15),
                    (float) w.path("novelty").asDouble(0.35),
                    (float) w.path("urgency").asDouble(0.25)));
        }

        // Parse persona
        if (body.has("persona")) {
            var pNode = body.get("persona");
            var pBuilder = com.spectrayan.spector.memory.model.PersonaContext.builder();
            if (pNode.has("occupation")) pBuilder.occupation(pNode.get("occupation").asText());
            if (pNode.has("about")) pBuilder.about(pNode.get("about").asText());
            if (pNode.has("bigFive")) {
                var bf = pNode.get("bigFive");
                pBuilder.bigFive(new com.spectrayan.spector.memory.model.BigFiveTraits(
                        (float) bf.path("openness").asDouble(50),
                        (float) bf.path("conscientiousness").asDouble(50),
                        (float) bf.path("extraversion").asDouble(50),
                        (float) bf.path("agreeableness").asDouble(50),
                        (float) bf.path("neuroticism").asDouble(50)));
            }
            if (pNode.has("stressResponse")) {
                pBuilder.stressResponse(com.spectrayan.spector.memory.model.StressResponse.valueOf(
                        pNode.get("stressResponse").asText().toUpperCase()));
            }
            builder.persona(pBuilder.build());
        }

        var profile = builder.build();
        memoryService.memory().setSalienceProfile(profile);

        return HttpResponse.ofJson(Map.of(
                "status", "success",
                "interests", profile.interests().size(),
                "disinterests", profile.disinterests().size(),
                "hasPersona", profile.hasPersona(),
                "hasIcnuOverride", profile.hasIcnuOverride()));
    }

    @Post("/salience/compute")
    public HttpResponse computeSalienceBoost(JsonNode body) {
        String text = body.has("text") ? body.get("text").asText() : "";
        if (text.isBlank()) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "\"text\" field is required");
        }

        float topicBoost = memoryService.memory().computeTopicBoost(text);
        float selfBoost = memoryService.memory().computeSelfRelevanceBoost(text);

        return HttpResponse.ofJson(Map.of(
                "text", text.length() > 100 ? text.substring(0, 100) + "..." : text,
                "topicBoost", topicBoost,
                "selfRelevanceBoost", selfBoost,
                "combinedBoost", topicBoost * selfBoost));
    }

    @Post("/salience/interest")
    public HttpResponse addInterest(JsonNode body) {
        return modifyInterest(body, true);
    }

    @Post("/salience/disinterest")
    public HttpResponse addDisinterest(JsonNode body) {
        return modifyInterest(body, false);
    }

    @Post("/salience/persona")
    public HttpResponse setPersona(JsonNode body) {
        var pBuilder = com.spectrayan.spector.memory.model.PersonaContext.builder();
        if (body.has("occupation")) pBuilder.occupation(body.get("occupation").asText());
        if (body.has("about")) pBuilder.about(body.get("about").asText());
        if (body.has("bigFive")) {
            var bf = body.get("bigFive");
            pBuilder.bigFive(new com.spectrayan.spector.memory.model.BigFiveTraits(
                    (float) bf.path("openness").asDouble(50),
                    (float) bf.path("conscientiousness").asDouble(50),
                    (float) bf.path("extraversion").asDouble(50),
                    (float) bf.path("agreeableness").asDouble(50),
                    (float) bf.path("neuroticism").asDouble(50)));
        }
        if (body.has("stressResponse")) {
            pBuilder.stressResponse(com.spectrayan.spector.memory.model.StressResponse.valueOf(
                    body.get("stressResponse").asText().toUpperCase()));
        }
        var persona = pBuilder.build();

        // Rebuild profile with persona
        var current = memoryService.memory().salienceProfile();
        var builder = com.spectrayan.spector.memory.model.SalienceProfile.builder();
        for (var d : current.interests()) builder.interest(d);
        for (var d : current.disinterests()) builder.disinterest(d);
        if (current.hasIcnuOverride()) builder.icnuWeights(current.icnuWeights());
        builder.flashbulbThreshold(current.flashbulbThreshold());
        builder.recencyWeight(current.recencyWeight());
        builder.similarityThreshold(current.similarityThreshold());
        builder.persona(persona);

        memoryService.memory().setSalienceProfile(builder.build());

        return HttpResponse.ofJson(Map.of(
                "status", "success",
                "occupation", persona.occupation() != null ? persona.occupation() : "",
                "bigFive", Map.of(
                        "openness", persona.bigFive().openness(),
                        "conscientiousness", persona.bigFive().conscientiousness(),
                        "extraversion", persona.bigFive().extraversion(),
                        "agreeableness", persona.bigFive().agreeableness(),
                        "neuroticism", persona.bigFive().neuroticism()),
                "stressResponse", persona.stressResponse().name()));
    }

    private HttpResponse modifyInterest(JsonNode body, boolean isInterest) {
        String topic = body.has("topic") ? body.get("topic").asText() : "";
        String level = body.has("level") ? body.get("level").asText() : "HIGH";
        if (topic.isBlank()) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "\"topic\" field is required");
        }

        com.spectrayan.spector.memory.model.InterestLevel interestLevel;
        try {
            interestLevel = com.spectrayan.spector.memory.model.InterestLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
                    "Invalid level: " + level + ". Valid: CRITICAL, HIGH, MEDIUM, LOW, IGNORE");
        }

        var current = memoryService.memory().salienceProfile();
        var builder = com.spectrayan.spector.memory.model.SalienceProfile.builder();
        for (var d : current.interests()) builder.interest(d);
        for (var d : current.disinterests()) builder.disinterest(d);
        if (current.hasIcnuOverride()) builder.icnuWeights(current.icnuWeights());
        if (current.hasPersona()) builder.persona(current.persona());
        builder.flashbulbThreshold(current.flashbulbThreshold());
        builder.recencyWeight(current.recencyWeight());
        builder.similarityThreshold(current.similarityThreshold());

        if (isInterest) {
            builder.interest(topic, interestLevel);
        } else {
            builder.disinterest(topic, interestLevel);
        }

        memoryService.memory().setSalienceProfile(builder.build());

        String label = isInterest ? "interest" : "disinterest";
        return HttpResponse.ofJson(Map.of(
                "status", "success",
                "action", "added_" + label,
                "topic", topic,
                "level", interestLevel.name(),
                "multiplier", interestLevel.multiplier()));
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Detects content type from file extension for metadata enrichment. */
    private static String detectContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "text/javascript";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "text/yaml";
        return "application/octet-stream";
    }
}
