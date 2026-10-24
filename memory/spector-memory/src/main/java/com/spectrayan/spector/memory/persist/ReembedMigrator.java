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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.store.ProceduralMemory;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.kernel.store.TextBlobMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Migrates a persisted Spector store from one embedding model / dimensionality to another
 * by reading all live records read-only from the source directory (without requiring the source
 * embedder) and writing them into the target directory re-embedded with the target
 * {@link EmbeddingProvider}.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li>Source directory is opened strictly read-only and is never modified.</li>
 *   <li>Preserves record {@code id}, {@code text}, {@code type} (tier), {@code tags},
 *       {@code valence}, {@code arousal}, fused {@code importance}, {@code source},
 *       created {@code timestampMs}, recall counts, storage strength, and {@code metadata}.</li>
 *   <li>Skips tombstoned (forgotten) and purged records; never resurrects forgotten memories.</li>
 *   <li>Writes the target's {@link StoreEmbeddingIdentity} fingerprint into the target store's
 *       {@code namespace.json}.</li>
 *   <li>Supports {@code dryRun} to inspect and count records without touching the target directory.</li>
 * </ul>
 */
public final class ReembedMigrator {

    private static final Logger log = LoggerFactory.getLogger(ReembedMigrator.class);

    public static final String REASON_TOMBSTONED = "tombstoned";
    public static final String REASON_PURGED = "purged";
    public static final String REASON_RETRACTED = "retracted";
    public static final String REASON_EMPTY_TEXT = "empty_text";
    public static final String REASON_CORRUPT = "corrupt_or_missing";

    private static final List<String> DEFAULT_FIELDS_NOT_CARRIED_OVER = List.of(
            "raw ICNU hints (interest, challenge, urgency — fused into importance at original ingest)",
            "associative graph state (Hebbian co-activation weights and temporal chain links — rebuilt on target ingest)",
            "prior quantized vector payload (re-computed from target embedding model)"
    );

    private ReembedMigrator() {}

    /**
     * Summary of a {@code migrate reembed} execution.
     */
    public record Result(
            Path sourceDir,
            Path targetDir,
            String namespace,
            boolean dryRun,
            int copiedCount,
            Map<String, Integer> skippedByReason,
            List<String> fieldsNotCarriedOver,
            StoreEmbeddingIdentity.RecordedIdentity sourceIdentity,
            StoreEmbeddingIdentity.RecordedIdentity targetIdentity
    ) {
        public int totalSkipped() {
            return skippedByReason.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private record MigratedRecord(
            String id,
            String text,
            MemoryType type,
            MemorySource source,
            String[] tags,
            EncodingHeader header,
            Map<String, String> metadata
    ) {}

    private record SlotKey(int partitionSeq, MemoryType type, long offset) {
        static SlotKey of(int partitionSeq, MemoryType type, long offset) {
            return new SlotKey(type == MemoryType.WORKING ? 0 : partitionSeq, type, offset);
        }
    }

    private record OpenedPartition(
            int seq,
            Path dir,
            PartitionBundle bundle,
            CognitiveMemoryRouter router,
            TextBlobMemory textStore
    ) implements AutoCloseable {
        @Override
        public void close() {
            if (bundle != null) {
                try {
                    bundle.close();
                } catch (Exception e) {
                    log.debug("Error closing read-only partition bundle seq={}: {}", seq, e.getMessage());
                }
            }
        }
    }

    /**
     * Executes the re-embedding migration from {@code fromDir} to {@code toDir}.
     *
     * @param fromDir           source store directory (or multi-namespace root when {@code namespace} is a child dir)
     * @param toDir             target store directory
     * @param namespace         optional namespace name (defaults to {@code "default"} or the source directory's namespace)
     * @param dryRun            when {@code true}, scans the source and reports counts without writing to {@code toDir}
     * @param embeddingProvider target embedding provider used to re-embed live records
     * @param baseProperties    optional base configuration properties (may be {@code null})
     * @param dataEncryptor     optional at-rest data encryptor (may be {@code null})
     * @return migration summary result
     */
    public static Result migrate(
            Path fromDir,
            Path toDir,
            String namespace,
            boolean dryRun,
            EmbeddingProvider embeddingProvider,
            SpectorProperties baseProperties,
            DataEncryptor dataEncryptor) {

        Objects.requireNonNull(fromDir, "fromDir cannot be null");
        Objects.requireNonNull(toDir, "toDir cannot be null");
        if (embeddingProvider == null) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID,
                    "embeddingProvider",
                    "A configured EmbeddingProvider is required for 'spector migrate reembed'");
        }
        if (embeddingProvider.dimensions() <= 0) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID,
                    "embeddingProvider.dimensions",
                    "EmbeddingProvider dimensions must be > 0, got " + embeddingProvider.dimensions());
        }

        DataEncryptor encryptor = dataEncryptor != null ? dataEncryptor : DataEncryptor.NOOP;

        ResolvedPaths resolved = resolveStorePaths(fromDir, toDir, namespace);
        Path sourceStoreDir = resolved.sourceStoreDir();
        Path targetStoreDir = resolved.targetStoreDir();
        String effectiveNamespace = resolved.namespace();

        if (!Files.isDirectory(sourceStoreDir)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "from", "Source store directory does not exist: " + sourceStoreDir);
        }
        Path runtimeBundleFile = StoragePaths.runtimeBundleFile(sourceStoreDir);
        if (!Files.isRegularFile(runtimeBundleFile)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "from", "Source directory does not contain runtime/runtime.bundle: " + sourceStoreDir);
        }

        if (sourceStoreDir.toAbsolutePath().normalize().equals(targetStoreDir.toAbsolutePath().normalize())) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "to", "Source and target directories must be different: " + sourceStoreDir);
        }

        if (!dryRun && isTargetStoreNonEmpty(targetStoreDir, encryptor)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "to", "Target directory already contains a non-empty Spector store: " + targetStoreDir);
        }

        StoreEmbeddingIdentity.RecordedIdentity sourceIdentity = StoreEmbeddingIdentity.read(sourceStoreDir).orElse(null);
        String targetProviderType = (baseProperties != null
                && baseProperties.provider() != null
                && baseProperties.provider().embedding() != null
                && baseProperties.provider().embedding().getType() != null
                && !baseProperties.provider().embedding().getType().isBlank())
                ? baseProperties.provider().embedding().getType()
                : "supplied";
        StoreEmbeddingIdentity.RecordedIdentity targetIdentity = new StoreEmbeddingIdentity.RecordedIdentity(
                targetProviderType,
                embeddingProvider.modelName(),
                embeddingProvider.dimensions()
        );

        Map<String, Integer> skippedByReason = new LinkedHashMap<>();
        skippedByReason.put(REASON_TOMBSTONED, 0);
        skippedByReason.put(REASON_PURGED, 0);

        List<MigratedRecord> liveRecords = new ArrayList<>();

        // 1. Read source store strictly read-only
        readSourceStore(sourceStoreDir, runtimeBundleFile, encryptor, liveRecords, skippedByReason);

        if (dryRun) {
            return new Result(
                    sourceStoreDir,
                    targetStoreDir,
                    effectiveNamespace,
                    true,
                    liveRecords.size(),
                    Collections.unmodifiableMap(skippedByReason),
                    DEFAULT_FIELDS_NOT_CARRIED_OVER,
                    sourceIdentity,
                    targetIdentity
            );
        }

        // 2. Write live records into target store with new embeddings
        int copied = writeTargetStore(
                targetStoreDir,
                effectiveNamespace,
                liveRecords,
                embeddingProvider,
                targetIdentity,
                baseProperties,
                encryptor);

        return new Result(
                sourceStoreDir,
                targetStoreDir,
                effectiveNamespace,
                false,
                copied,
                Collections.unmodifiableMap(skippedByReason),
                DEFAULT_FIELDS_NOT_CARRIED_OVER,
                sourceIdentity,
                targetIdentity
        );
    }

    private static void readSourceStore(
            Path sourceStoreDir,
            Path runtimeBundleFile,
            DataEncryptor encryptor,
            List<MigratedRecord> liveRecords,
            Map<String, Integer> skippedByReason) {

        List<OpenedPartition> openedPartitions = new ArrayList<>();
        MemoryIndex index = null;
        try (RuntimeBundle runtimeBundle = RuntimeBundle.Init.openReadOnly(runtimeBundleFile, encryptor)) {
            WorkingMemory workingStore = runtimeBundle.hasRegion(RegionId.WORKING)
                    ? runtimeBundle.openWorkingFromDirectory()
                    : null;

            Map<Integer, Path> partitionDirs = discoverExistingPartitionsReadOnly(sourceStoreDir);
            Map<Integer, CognitiveMemoryRouter> routersBySeq = new HashMap<>();
            Map<Integer, TextBlobMemory> textStoresBySeq = new HashMap<>();
            CognitiveMemoryRouter activeRouter = null;
            TextBlobMemory activeTextStore = null;

            for (Map.Entry<Integer, Path> entry : partitionDirs.entrySet()) {
                int seq = entry.getKey();
                Path pDir = entry.getValue();
                Path pBundleFile = StoragePaths.partitionBundleFile(pDir);
                if (!Files.isRegularFile(pBundleFile)) {
                    continue;
                }
                PartitionBundle pb = PartitionBundle.Init.openReadOnly(pBundleFile, encryptor);
                SemanticMemory sem = pb.openSemanticFromDirectory();
                ProceduralMemory proc = pb.openProceduralFromDirectory();
                EpisodicMemory epi = pb.openEpisodicFromDirectory();
                StrengthMemory str = pb.openStrengthFromDirectory("ro-partition-" + seq);
                TextBlobMemory txt = pb.openText(encryptor);
                txt.readAll();

                CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingStore, sem, proc, epi, str);
                OpenedPartition op = new OpenedPartition(seq, pDir, pb, router, txt);
                openedPartitions.add(op);
                routersBySeq.put(seq, router);
                textStoresBySeq.put(seq, txt);
                activeRouter = router;
                activeTextStore = txt;
            }

            if (activeRouter == null && workingStore != null) {
                activeRouter = new CognitiveMemoryRouter(
                        workingStore,
                        new SemanticMemory(1, 1),
                        new ProceduralMemory(1, 1),
                        EpisodicMemory.heap(1, 256L));
            }

            if (runtimeBundle.hasRegion(RegionId.INDEX_MIDX) && runtimeBundle.hasRegion(RegionId.INDEX_IDPL)) {
                index = MemoryIndex.fromRegionRefs(
                        runtimeBundle.regionRef(RegionId.INDEX_MIDX),
                        runtimeBundle.regionRef(RegionId.INDEX_IDPL),
                        null,
                        false);
            } else {
                index = new MemoryIndex();
            }
            if (activeTextStore != null) {
                index.setTextDataStore(activeTextStore);
            }
            index.setTextResolver(textStoresBySeq::get);

            Map<String, MemoryLocation> tombstoneLedgerEntries = new TombstoneLedger(sourceStoreDir).load();
            Set<SlotKey> visitedSlots = new HashSet<>();

            // Step A: Inspect all indexed entries in insertion order
            for (String id : index.orderedIds()) {
                MemoryLocation loc = index.locate(id);
                if (loc == null) {
                    continue;
                }
                SlotKey slotKey = SlotKey.of(loc.colocatedPartition(), loc.type(), loc.offset());
                visitedSlots.add(slotKey);

                CognitiveMemoryRouter router = loc.type() == MemoryType.WORKING
                        ? activeRouter
                        : routersBySeq.getOrDefault(loc.colocatedPartition(), activeRouter);
                if (router == null) {
                    incrementSkipped(skippedByReason, REASON_CORRUPT);
                    continue;
                }

                if (router.isPurged(loc)) {
                    incrementSkipped(skippedByReason, REASON_PURGED);
                    continue;
                }
                if (router.isTombstoned(loc) || tombstoneLedgerEntries.containsKey(id)) {
                    incrementSkipped(skippedByReason, REASON_TOMBSTONED);
                    continue;
                }

                var body = router.readRecordBody(loc, false);
                if (body == null || body.header() == null) {
                    incrementSkipped(skippedByReason, REASON_CORRUPT);
                    continue;
                }
                if (EncodingHeaderFields.isRetracted(body.consolidationFlags())) {
                    incrementSkipped(skippedByReason, REASON_RETRACTED);
                    continue;
                }

                String text = index.text(id);
                if ((text == null || text.isEmpty())
                        && loc.type() == MemoryType.EPISODIC
                        && router.episodic() != null) {
                    EpisodeRecord turn = router.episodic().readTurn(loc.offset(), true);
                    if (turn != null && turn.body() != null && turn.body().length > 0) {
                        text = new String(turn.body(), StandardCharsets.UTF_8);
                    }
                }
                if (text == null || text.isBlank()) {
                    incrementSkipped(skippedByReason, REASON_EMPTY_TEXT);
                    continue;
                }

                liveRecords.add(new MigratedRecord(
                        id,
                        text,
                        loc.type(),
                        index.source(id),
                        index.tags(id),
                        body.header(),
                        index.metadata(id)
                ));
            }

            // Step B: Scan unvisited physical slots across WORKING, SEMANTIC, PROCEDURAL, EPISODIC
            // so forgotten (tombstoned) and purged records retired from MemoryIndex are accurately counted.
            if (workingStore != null) {
                scanUnvisitedFixedRegion(0, MemoryType.WORKING, workingStore, visitedSlots, skippedByReason);
            }
            for (OpenedPartition op : openedPartitions) {
                int seq = op.seq();
                CognitiveMemoryRouter router = op.router();
                scanUnvisitedFixedRegion(seq, MemoryType.SEMANTIC, router.semantic(), visitedSlots, skippedByReason);
                scanUnvisitedFixedRegion(seq, MemoryType.PROCEDURAL, router.procedural(), visitedSlots, skippedByReason);
                scanUnvisitedEpisodicRegion(seq, router.episodic(), visitedSlots, skippedByReason);
            }

            // Step C: Count any remaining tombstone ledger entries whose physical slot was already compacted
            for (MemoryLocation loc : tombstoneLedgerEntries.values()) {
                if (loc == null) {
                    incrementSkipped(skippedByReason, REASON_TOMBSTONED);
                    continue;
                }
                SlotKey key = SlotKey.of(loc.colocatedPartition(), loc.type(), loc.offset());
                if (loc.offset() < 0 || visitedSlots.add(key)) {
                    incrementSkipped(skippedByReason, REASON_TOMBSTONED);
                }
            }
        } finally {
            if (index != null) {
                try {
                    index.close();
                } catch (Exception ignored) {
                }
            }
            for (OpenedPartition op : openedPartitions) {
                op.close();
            }
        }
    }

    private static void scanUnvisitedFixedRegion(
            int partitionSeq,
            MemoryType type,
            EngramRegion region,
            Set<SlotKey> visitedSlots,
            Map<String, Integer> skippedByReason) {
        if (region == null) {
            return;
        }
        int totalSlots = region.size();
        for (int i = 0; i < totalSlots; i++) {
            long offset = region.recordOffset(i);
            SlotKey key = SlotKey.of(partitionSeq, type, offset);
            if (!visitedSlots.add(key)) {
                continue;
            }
            if (region.isPurged(offset)) {
                incrementSkipped(skippedByReason, REASON_PURGED);
            } else if (region.isTombstoned(offset)) {
                incrementSkipped(skippedByReason, REASON_TOMBSTONED);
            }
        }
    }

    private static void scanUnvisitedEpisodicRegion(
            int partitionSeq,
            EpisodicMemory episodic,
            Set<SlotKey> visitedSlots,
            Map<String, Integer> skippedByReason) {
        if (episodic == null) {
            return;
        }
        for (long relOffset : episodic.allTurnOffsets()) {
            SlotKey key = SlotKey.of(partitionSeq, MemoryType.EPISODIC, relOffset);
            if (!visitedSlots.add(key)) {
                continue;
            }
            if (episodic.isPurged(relOffset)) {
                incrementSkipped(skippedByReason, REASON_PURGED);
            } else if (episodic.isTombstoned(relOffset)) {
                incrementSkipped(skippedByReason, REASON_TOMBSTONED);
            }
        }
    }

    private static int writeTargetStore(
            Path targetStoreDir,
            String namespace,
            List<MigratedRecord> liveRecords,
            EmbeddingProvider embeddingProvider,
            StoreEmbeddingIdentity.RecordedIdentity targetIdentity,
            SpectorProperties baseProperties,
            DataEncryptor encryptor) {

        SpectorProperties targetProps = clonePropertiesForTarget(baseProperties, embeddingProvider.dimensions());
        int copied = 0;

        SpectorMemory memory = SpectorMemory.builder(targetProps)
                .persistence(targetStoreDir)
                .namespaceId(namespace)
                .embeddingProvider(embeddingProvider)
                .dataEncryptor(encryptor)
                .build();

        try (DefaultSpectorMemory targetMemory = (DefaultSpectorMemory) memory) {
            for (MigratedRecord rec : liveRecords) {
                boolean ingested = targetMemory.ingestMigratedRecord(
                        rec.id(),
                        rec.text(),
                        rec.type(),
                        rec.source(),
                        rec.tags(),
                        rec.header(),
                        rec.metadata()
                );
                if (ingested) {
                    copied++;
                }
            }
            // Explicitly persist the target embedding fingerprint even if 0 records were copied
            StoreEmbeddingIdentity.record(
                    targetStoreDir,
                    targetIdentity.provider(),
                    targetIdentity.model(),
                    targetIdentity.dimensions());
        }

        return copied;
    }

    private static SpectorProperties clonePropertiesForTarget(
            SpectorProperties base,
            int targetDimensions) {
        SpectorProperties props = base != null
                ? base.withDimensions(targetDimensions)
                : SpectorProperties.builder().build().withDimensions(targetDimensions);
        // Disable background entity extraction workers during batch migration
        props.memory().setEntityExtractionParallelism(0);
        return props;
    }

    private static Map<Integer, Path> discoverExistingPartitionsReadOnly(Path basePath) {
        Path partitionsDir = StoragePaths.partitionsDir(basePath);
        if (!Files.isDirectory(partitionsDir)) {
            return Map.of();
        }
        TreeMap<Integer, Path> bySeq = new TreeMap<>();
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String name = dir.getFileName().toString();
                if (StoragePaths.isPartitionDir(name)) {
                    int seq = StoragePaths.parsePartitionSeqNo(name);
                    bySeq.put(seq, dir);
                }
            }
        } catch (IOException e) {
            throw new SpectorStorageException(ErrorCode.DISK_IO_FAILED, e,
                    "Failed to list partitions in " + partitionsDir);
        }
        return bySeq;
    }

    private static boolean isTargetStoreNonEmpty(Path targetStoreDir, DataEncryptor encryptor) {
        if (!Files.isDirectory(targetStoreDir)) {
            return false;
        }
        Path runtimeFile = StoragePaths.runtimeBundleFile(targetStoreDir);
        if (Files.isRegularFile(runtimeFile)
                && RuntimeBundle.inspectStoredVectorDimensions(runtimeFile, encryptor).isPresent()) {
            return true;
        }
        for (Path pDir : discoverExistingPartitionsReadOnly(targetStoreDir).values()) {
            Path pFile = StoragePaths.partitionBundleFile(pDir);
            if (Files.isRegularFile(pFile)
                    && PartitionBundle.inspectStoredVectorDimensions(pFile, encryptor).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private record ResolvedPaths(Path sourceStoreDir, Path targetStoreDir, String namespace) {}

    private static ResolvedPaths resolveStorePaths(Path fromDir, Path toDir, String requestedNamespace) {
        String ns = (requestedNamespace != null && !requestedNamespace.isBlank())
                ? requestedNamespace.trim()
                : null;

        // If --namespace <ns> is specified and <fromDir>/<ns>/runtime/runtime.bundle exists,
        // treat <fromDir> and <toDir> as multi-namespace root directories.
        if (ns != null) {
            Path candidateSource = fromDir.resolve(ns);
            if (Files.isRegularFile(StoragePaths.runtimeBundleFile(candidateSource))
                    && !Files.isRegularFile(StoragePaths.runtimeBundleFile(fromDir))) {
                Path candidateTarget = (toDir.getFileName() != null && toDir.getFileName().toString().equals(ns))
                        ? toDir
                        : toDir.resolve(ns);
                return new ResolvedPaths(candidateSource, candidateTarget, ns);
            }
        }

        // If <fromDir>/runtime/runtime.bundle does not exist directly, check <fromDir>/default
        if (!Files.isRegularFile(StoragePaths.runtimeBundleFile(fromDir))) {
            String fallbackNs = ns != null ? ns : "default";
            Path candidateSource = fromDir.resolve(fallbackNs);
            if (Files.isRegularFile(StoragePaths.runtimeBundleFile(candidateSource))) {
                Path candidateTarget = (toDir.getFileName() != null && toDir.getFileName().toString().equals(fallbackNs))
                        ? toDir
                        : toDir.resolve(fallbackNs);
                return new ResolvedPaths(candidateSource, candidateTarget, fallbackNs);
            }
        }

        String effectiveNs = ns != null ? ns : inferNamespaceFromDirectory(fromDir);
        return new ResolvedPaths(fromDir, toDir, effectiveNs);
    }

    private static String inferNamespaceFromDirectory(Path storeDir) {
        Path nsFile = storeDir.resolve(StoragePaths.FILE_NAMESPACE);
        if (Files.isRegularFile(nsFile)) {
            try {
                String json = Files.readString(nsFile, StandardCharsets.UTF_8);
                int idx = json.indexOf("\"namespaceId\"");
                if (idx >= 0) {
                    int colon = json.indexOf(':', idx);
                    int q1 = json.indexOf('"', colon + 1);
                    int q2 = json.indexOf('"', q1 + 1);
                    if (colon > 0 && q1 > colon && q2 > q1) {
                        String parsed = json.substring(q1 + 1, q2).trim();
                        if (!parsed.isEmpty()) {
                            return parsed;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return "default";
    }

    private static void incrementSkipped(Map<String, Integer> map, String reason) {
        map.merge(reason, 1, Integer::sum);
    }
}
