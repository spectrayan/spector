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
import com.spectrayan.spector.kernel.bundle.BundleDirectory;
import com.spectrayan.spector.kernel.bundle.BundleSubHeader;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader;
import com.spectrayan.spector.kernel.storage.PayloadEncryptor;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.provider.ProviderFingerprint;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Persists and validates the embedding fingerprint ({@code embeddingProvider}, {@code embeddingModel},
 * {@code embeddingDimensions}) of a persistent store in {@code namespace.json} ({@link StoragePaths#FILE_NAMESPACE}),
 * with legacy store vector dimension inference from existing mmap bundle headers.
 *
 * <p>All methods are pure JDK with zero third-party serialization dependencies.</p>
 */
public final class StoreEmbeddingIdentity {

    private static final Logger log = LoggerFactory.getLogger(StoreEmbeddingIdentity.class);

    /** Marker field recording the embedding provider type a store's vectors were produced with. */
    public static final String KEY_EMBEDDING_PROVIDER = "embeddingProvider";

    /** Marker field recording the embedding model a store's vectors were produced with. */
    public static final String KEY_EMBEDDING_MODEL = "embeddingModel";

    /** Marker field recording the dimensionality a store's vectors were produced at. */
    public static final String KEY_EMBEDDING_DIMENSIONS = "embeddingDimensions";

    private static final String DEFAULT_SUPPLIED_PROVIDER = "supplied";
    private static final int DIRECTORY_HEADER_PEEK_BYTES = 4096;

    /** Tracks legacy stores for which the one-time missing-model warning has already been emitted. */
    private static final Set<Path> WARNED_LEGACY_STORES = ConcurrentHashMap.newKeySet();

    /** Guards read-modify-write updates to {@code namespace.json} without pinning virtual threads. */
    private static final ReentrantLock RECORD_LOCK = new ReentrantLock();

    private StoreEmbeddingIdentity() {}

    /**
     * Embedding identity recorded in {@code namespace.json}.
     *
     * @param provider   recorded provider type (may be null or empty)
     * @param model      recorded model identifier (may be null or empty on legacy stores)
     * @param dimensions recorded vector dimensions ({@code <= 0} if unrecorded)
     */
    public record RecordedIdentity(String provider, String model, int dimensions) {

        public boolean hasModel() {
            return hasKnownModel(model);
        }

        public boolean hasDimensions() {
            return dimensions > 0;
        }

        public String describe() {
            return formatFingerprint(provider, model, dimensions);
        }
    }

    /**
     * Result of validating a store's embedding identity on open.
     */
    public enum ValidationOutcome {
        /** Fresh store directory with no existing fingerprint or bundle data. */
        NEW_STORE,
        /** Store fingerprint matched the configured embedder. */
        VERIFIED,
        /** Legacy store without recorded model name; stored vector dimensions matched. */
        LEGACY_DIMENSIONS_ONLY,
        /** Model mismatch permitted via explicit {@code allowEmbeddingMismatch} migration flag. */
        MISMATCH_ALLOWED
    }

    /**
     * Validates the store at {@code basePath} against the builder's configured embedding provider and dimensions.
     *
     * @param basePath       store root directory
     * @param builder        memory builder supplying the embedder, properties, and encryptor
     * @param configuredDims configured vector dimensionality
     * @return the validation outcome
     * @throws SpectorConfigException with {@link ErrorCode#EMBEDDING_FINGERPRINT_MISMATCH} on mismatch
     */
    public static ValidationOutcome validateOnOpen(
            Path basePath,
            SpectorMemoryBuilder builder,
            int configuredDims) {
        if (basePath == null || builder == null) {
            return ValidationOutcome.NEW_STORE;
        }
        String providerType = resolveProviderType(builder, builder.embeddingProvider());
        String modelName = probeModelName(builder.embeddingProvider());
        int dims = configuredDims > 0 ? configuredDims : probeDimensions(builder.embeddingProvider(), -1);
        return validateOnOpen(
                basePath,
                providerType,
                modelName,
                dims,
                builder.allowEmbeddingMismatch(),
                builder.dataEncryptor());
    }

    /**
     * Validates the store at {@code basePath} against an explicit embedder identity.
     *
     * @param basePath        store root directory
     * @param providerType    configured provider type
     * @param configuredModel configured embedding model name (may be null/unknown if offline)
     * @param configuredDims  configured vector dimensions
     * @param allowMismatch   whether to permit opening across a model mismatch at identical dimensions
     * @param encryptor       optional payload encryptor for reading encrypted bundle headers
     * @return the validation outcome
     * @throws SpectorConfigException with {@link ErrorCode#EMBEDDING_FINGERPRINT_MISMATCH} on mismatch
     */
    public static ValidationOutcome validateOnOpen(
            Path basePath,
            String providerType,
            String configuredModel,
            int configuredDims,
            boolean allowMismatch,
            PayloadEncryptor encryptor) {
        if (basePath == null) {
            return ValidationOutcome.NEW_STORE;
        }

        Optional<RecordedIdentity> recordedOpt = read(basePath);
        OptionalInt storedDimsOpt = inferStoredDimensions(basePath, encryptor);

        if (recordedOpt.isPresent() && recordedOpt.get().hasModel()) {
            RecordedIdentity recorded = recordedOpt.get();
            int effectiveRecordedDims = recorded.hasDimensions()
                    ? recorded.dimensions()
                    : storedDimsOpt.orElse(-1);

            boolean physicalDimsMismatch = storedDimsOpt.isPresent()
                    && configuredDims > 0
                    && storedDimsOpt.getAsInt() != configuredDims;
            boolean recordedDimsMismatch = effectiveRecordedDims > 0
                    && configuredDims > 0
                    && effectiveRecordedDims != configuredDims;
            boolean modelMismatch = hasKnownModel(configuredModel)
                    && !recorded.model().equals(configuredModel);

            if (physicalDimsMismatch || recordedDimsMismatch) {
                int reportedStoredDims = physicalDimsMismatch ? storedDimsOpt.getAsInt() : effectiveRecordedDims;
                String recordedDesc = formatFingerprint(recorded.provider(), recorded.model(), reportedStoredDims);
                String configuredDesc = formatFingerprint(providerType, configuredModel, configuredDims);
                throw new SpectorConfigException(
                        ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH,
                        basePath,
                        recordedDesc,
                        configuredDesc,
                        basePath);
            }

            if (modelMismatch) {
                String recordedDesc = recorded.describe();
                String configuredDesc = formatFingerprint(providerType, configuredModel, configuredDims);
                if (allowMismatch) {
                    log.warn("[Spector] Store '{}' was written with embedder [{}] but is being opened with [{}] "
                                    + "because allowEmbeddingMismatch=true.",
                            basePath, recordedDesc, configuredDesc);
                    return ValidationOutcome.MISMATCH_ALLOWED;
                }
                throw new SpectorConfigException(
                        ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH,
                        basePath,
                        recordedDesc,
                        configuredDesc,
                        basePath);
            }

            return ValidationOutcome.VERIFIED;
        }

        int existingDims = storedDimsOpt.isPresent()
                ? storedDimsOpt.getAsInt()
                : (recordedOpt.isPresent() && recordedOpt.get().hasDimensions()
                        ? recordedOpt.get().dimensions()
                        : -1);

        if (existingDims <= 0) {
            return ValidationOutcome.NEW_STORE;
        }

        if (configuredDims > 0 && existingDims != configuredDims) {
            String recordedDesc = formatFingerprint(
                    recordedOpt.map(RecordedIdentity::provider).orElse(null),
                    null,
                    existingDims);
            String configuredDesc = formatFingerprint(providerType, configuredModel, configuredDims);
            throw new SpectorConfigException(
                    ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH,
                    basePath,
                    recordedDesc,
                    configuredDesc,
                    basePath);
        }

        // Legacy store (or store without recorded model name) whose stored vector dimensions match.
        Path normalizedPath = basePath.toAbsolutePath().normalize();
        if (WARNED_LEGACY_STORES.add(normalizedPath)) {
            log.warn("[Spector] Opening legacy store '{}' with no recorded embedding model in {}; "
                            + "inferred vector dimensions ({}d) match configured embedder [{}]. "
                            + "The embedding fingerprint will be recorded on the next write.",
                    basePath,
                    StoragePaths.FILE_NAMESPACE,
                    existingDims,
                    formatFingerprint(providerType, configuredModel, configuredDims));
        }
        return ValidationOutcome.LEGACY_DIMENSIONS_ONLY;
    }

    /**
     * Reads the recorded embedding identity from {@code basePath/namespace.json}, if present.
     *
     * @param basePath store root directory
     * @return the recorded identity if at least model or dimensions are present
     */
    public static Optional<RecordedIdentity> read(Path basePath) {
        if (basePath == null) {
            return Optional.empty();
        }
        Path markerFile = basePath.resolve(StoragePaths.FILE_NAMESPACE);
        if (!Files.isRegularFile(markerFile)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(markerFile, StandardCharsets.UTF_8);
            Map<String, String> fields = parseTopLevelJsonObject(content);
            String provider = unquoteJsonString(fields.get(KEY_EMBEDDING_PROVIDER));
            String model = unquoteJsonString(fields.get(KEY_EMBEDDING_MODEL));
            int dims = parseJsonInt(fields.get(KEY_EMBEDDING_DIMENSIONS), -1);
            if (!hasKnownModel(model) && dims <= 0) {
                return Optional.empty();
            }
            return Optional.of(new RecordedIdentity(provider, model, dims));
        } catch (IOException | RuntimeException e) {
            log.warn("[Spector] Failed to read embedding identity from {}: {}", markerFile, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Records the embedding fingerprint in {@code basePath/namespace.json} if not already recorded.
     *
     * @param basePath     store root directory
     * @param providerType embedding provider type
     * @param modelName    embedding model name
     * @param dimensions   embedding vector dimensions
     * @return {@code true} if the identity is now recorded on disk
     */
    public static boolean recordIfAbsent(
            Path basePath,
            String providerType,
            String modelName,
            int dimensions) {
        if (basePath == null) {
            return false;
        }
        Optional<RecordedIdentity> existing = read(basePath);
        if (existing.isPresent()) {
            RecordedIdentity rec = existing.get();
            if (rec.hasModel() && rec.hasDimensions()) {
                return true;
            }
            if (!hasKnownModel(modelName) && rec.hasDimensions()) {
                return true;
            }
        }
        return record(basePath, providerType, modelName, dimensions);
    }

    /**
     * Writes or updates the embedding fingerprint in {@code basePath/namespace.json}, preserving
     * any existing JSON fields in the file.
     *
     * @param basePath     store root directory
     * @param providerType embedding provider type
     * @param modelName    embedding model name
     * @param dimensions   embedding vector dimensions
     * @return {@code true} if written successfully
     */
    public static boolean record(
            Path basePath,
            String providerType,
            String modelName,
            int dimensions) {
        if (basePath == null || (!hasKnownModel(modelName) && dimensions <= 0)) {
            return false;
        }
        Path markerFile = basePath.resolve(StoragePaths.FILE_NAMESPACE);
        RECORD_LOCK.lock();
        try {
            Files.createDirectories(basePath);
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            if (Files.isRegularFile(markerFile)) {
                String existingContent = Files.readString(markerFile, StandardCharsets.UTF_8);
                fields.putAll(parseTopLevelJsonObject(existingContent));
            }
            String effectiveProvider = (providerType != null && !providerType.isBlank())
                    ? providerType
                    : unquoteJsonString(fields.get(KEY_EMBEDDING_PROVIDER));
            if (effectiveProvider == null || effectiveProvider.isBlank()) {
                effectiveProvider = DEFAULT_SUPPLIED_PROVIDER;
            }
            fields.put(KEY_EMBEDDING_PROVIDER, quoteJsonString(effectiveProvider));
            if (hasKnownModel(modelName)) {
                fields.put(KEY_EMBEDDING_MODEL, quoteJsonString(modelName));
            }
            if (dimensions > 0) {
                fields.put(KEY_EMBEDDING_DIMENSIONS, Integer.toString(dimensions));
            }

            String serialized = formatTopLevelJsonObject(fields);
            Path tempFile = basePath.resolve(StoragePaths.FILE_NAMESPACE + ".tmp");
            Files.writeString(tempFile, serialized, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tempFile, markerFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicEx) {
                Files.move(tempFile, markerFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            log.warn("[Spector] Could not record embedding identity in {}: {}", markerFile, e.getMessage());
            return false;
        } finally {
            RECORD_LOCK.unlock();
        }
    }

    /**
     * Infers the stored vector dimensions from existing {@code partition.bundle} or {@code runtime.bundle}
     * files under {@code basePath} by subtracting the 64-byte {@code EncodingHeader} from the region record stride.
     *
     * @param basePath  store root directory
     * @param encryptor optional payload encryptor
     * @return the inferred vector dimensions, or {@link OptionalInt#empty()} if no vector-bearing bundle exists
     */
    public static OptionalInt inferStoredDimensions(Path basePath, PayloadEncryptor encryptor) {
        if (basePath == null || !Files.isDirectory(basePath)) {
            return OptionalInt.empty();
        }

        // 1. Check partition bundles (newest first) for SEMANTIC / PROCEDURAL stride.
        Path partitionsDir = StoragePaths.partitionsDir(basePath);
        if (Files.isDirectory(partitionsDir)) {
            try (Stream<Path> stream = Files.list(partitionsDir)) {
                List<Path> partitionDirs = stream
                        .filter(Files::isDirectory)
                        .filter(p -> StoragePaths.isPartitionDir(p.getFileName().toString()))
                        .sorted(Comparator.comparingInt(
                                (Path p) -> StoragePaths.parsePartitionSeqNo(p.getFileName().toString())).reversed())
                        .toList();
                for (Path partDir : partitionDirs) {
                    Path bundleFile = StoragePaths.partitionBundleFile(partDir);
                    if (Files.isRegularFile(bundleFile)) {
                        OptionalInt dims = readBundleVectorDimensions(
                                bundleFile,
                                BundleSubHeader.MAGIC_PARTITION,
                                encryptor,
                                RegionId.SEMANTIC,
                                RegionId.PROCEDURAL);
                        if (dims.isPresent()) {
                            return dims;
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("[Spector] Could not list partitions in {}: {}", partitionsDir, e.getMessage());
            }
        }

        // 2. Fallback: check runtime/runtime.bundle for WORKING stride.
        Path runtimeBundleFile = StoragePaths.runtimeBundleFile(basePath);
        if (Files.isRegularFile(runtimeBundleFile)) {
            return readBundleVectorDimensions(
                    runtimeBundleFile,
                    BundleSubHeader.MAGIC_RUNTIME,
                    encryptor,
                    RegionId.WORKING);
        }

        return OptionalInt.empty();
    }

    /**
     * Resolves the provider type string from a builder and its active {@link EmbeddingProvider}.
     */
    public static String resolveProviderType(SpectorMemoryBuilder builder, EmbeddingProvider provider) {
        if (builder != null
                && builder.properties() != null
                && builder.properties().provider() != null
                && builder.properties().provider().getEmbedding() != null) {
            String configuredType = builder.properties().provider().getEmbedding().getType();
            if (configuredType != null && !configuredType.isBlank()) {
                return configuredType;
            }
        }
        return resolveProviderType(provider);
    }

    /**
     * Resolves a provider type label from an {@link EmbeddingProvider} instance.
     */
    public static String resolveProviderType(EmbeddingProvider provider) {
        if (provider == null) {
            return DEFAULT_SUPPLIED_PROVIDER;
        }
        String simpleName = provider.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simpleName.contains("ollama")) return "ollama";
        if (simpleName.contains("openai")) return "openai";
        if (simpleName.contains("onnx")) return "onnx";
        if (simpleName.contains("voyage")) return "voyage";
        if (simpleName.contains("cohere")) return "cohere";
        if (simpleName.contains("vertex") || simpleName.contains("gemini")) return "vertex";
        if (simpleName.contains("bedrock")) return "bedrock";
        if (simpleName.contains("azure")) return "azure";
        return DEFAULT_SUPPLIED_PROVIDER;
    }

    /**
     * Safely probes {@link EmbeddingProvider#modelName()}, returning {@code null} if offline or unstubbed.
     */
    public static String probeModelName(EmbeddingProvider provider) {
        if (provider == null) {
            return null;
        }
        try {
            String model = provider.modelName();
            return hasKnownModel(model) ? model.trim() : null;
        } catch (RuntimeException e) {
            log.debug("[Spector] Could not probe embedding provider modelName eagerly: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely probes {@link EmbeddingProvider#dimensions()}, returning {@code fallback} if offline or {@code <= 0}.
     */
    public static int probeDimensions(EmbeddingProvider provider, int fallback) {
        if (provider == null) {
            return fallback;
        }
        try {
            int dims = provider.dimensions();
            return dims > 0 ? dims : fallback;
        } catch (RuntimeException e) {
            log.debug("[Spector] Could not probe embedding provider dimensions eagerly: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Formats a human-readable embedding fingerprint description.
     */
    public static String formatFingerprint(String provider, String model, int dimensions) {
        String modelPart = hasKnownModel(model) ? model.trim() : "unknown";
        String providerPart = (provider != null && !provider.isBlank()
                && !"supplied".equalsIgnoreCase(provider)
                && !"default".equalsIgnoreCase(provider)
                && !"unknown".equalsIgnoreCase(provider))
                ? provider.trim() + "/" + modelPart
                : modelPart;
        String dimsPart = dimensions > 0 ? dimensions + "d" : "unknown dims";
        return providerPart + " (" + dimsPart + ")";
    }

    static boolean hasKnownModel(String model) {
        return model != null && !model.isBlank() && !"unknown".equalsIgnoreCase(model.trim());
    }

    private static OptionalInt readBundleVectorDimensions(
            Path bundleFile,
            int expectedMagic,
            PayloadEncryptor encryptor,
            RegionId... regionIds) {
        try {
            if (EncryptedSegmentHeader.isEncryptedFile(bundleFile)) {
                if (encryptor == null || !encryptor.isEnabled()) {
                    return OptionalInt.empty();
                }
                if (expectedMagic == BundleSubHeader.MAGIC_PARTITION) {
                    try (PartitionBundle bundle = PartitionBundle.Init.open(bundleFile, encryptor)) {
                        if (!hasAnyStoredRecords(bundle)) {
                            return OptionalInt.empty();
                        }
                        return extractDimsFromDirectory(bundle.directory(), regionIds);
                    }
                } else {
                    try (RuntimeBundle bundle = RuntimeBundle.Init.open(bundleFile, encryptor)) {
                        if (!hasAnyStoredRecords(bundle)) {
                            return OptionalInt.empty();
                        }
                        return extractDimsFromDirectory(bundle.directory(), regionIds);
                    }
                }
            }

            long fileSize = Files.size(bundleFile);
            int headerBytes = (int) Math.min(DIRECTORY_HEADER_PEEK_BYTES, fileSize);
            if (headerBytes < BundleDirectory.ENTRIES_OFFSET + RegionEntry.ENTRY_BYTES) {
                return OptionalInt.empty();
            }
            try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                 FileChannel fc = FileChannel.open(bundleFile, StandardOpenOption.READ)) {
                MemorySegment seg = arena.allocate(headerBytes, 64);
                ByteBuffer bb = seg.asByteBuffer();
                while (bb.hasRemaining()) {
                    if (fc.read(bb) < 0) {
                        break;
                    }
                }
                if (!RegionPreamble.isValid(seg, BundleDirectory.HEADER_OFFSET)
                        || !BundleSubHeader.isValid(seg)) {
                    return OptionalInt.empty();
                }
                int magic = BundleSubHeader.readBundleMagic(seg);
                if (expectedMagic != 0 && magic != expectedMagic) {
                    return OptionalInt.empty();
                }
                int regionCount = BundleSubHeader.readRegionCount(seg);
                int inferredDims = -1;
                boolean hasStoredRecords = false;
                MemorySegment regionPreambleSeg = arena.allocate(RegionPreamble.PREAMBLE_BYTES, 64);

                for (int i = 0; i < regionCount; i++) {
                    long offset = BundleDirectory.ENTRIES_OFFSET + (long) i * RegionEntry.ENTRY_BYTES;
                    if (offset + RegionEntry.ENTRY_BYTES > seg.byteSize()) {
                        break;
                    }
                    RegionEntry entry = RegionEntry.read(seg, offset);
                    if (!entry.isLive()) {
                        continue;
                    }
                    if (inferredDims <= 0) {
                        for (RegionId targetId : regionIds) {
                            if (entry.regionId() == targetId && entry.stride() > EncodingHeaderFields.HEADER_BYTES) {
                                inferredDims = entry.stride() - EncodingHeaderFields.HEADER_BYTES;
                                break;
                            }
                        }
                    }
                    if (!hasStoredRecords && isCognitiveDataRegion(entry.regionId())
                            && entry.offset() >= 0
                            && entry.offset() + RegionPreamble.PREAMBLE_BYTES <= fileSize) {
                        ByteBuffer preambleBb = regionPreambleSeg.asByteBuffer();
                        preambleBb.clear();
                        long filePos = entry.offset();
                        while (preambleBb.hasRemaining()) {
                            int n = fc.read(preambleBb, filePos);
                            if (n < 0) {
                                break;
                            }
                            filePos += n;
                        }
                        if (!preambleBb.hasRemaining()
                                && RegionPreamble.isValid(regionPreambleSeg, 0)
                                && RegionPreamble.readCount(regionPreambleSeg, 0) > 0) {
                            hasStoredRecords = true;
                        }
                    }
                }
                if (inferredDims > 0 && hasStoredRecords) {
                    return OptionalInt.of(inferredDims);
                }
            }
        } catch (Exception e) {
            log.debug("[Spector] Could not inspect bundle header in {}: {}", bundleFile, e.getMessage());
        }
        return OptionalInt.empty();
    }

    private static boolean isCognitiveDataRegion(RegionId id) {
        return id == RegionId.SEMANTIC
                || id == RegionId.EPISODIC
                || id == RegionId.PROCEDURAL
                || id == RegionId.TEXT
                || id == RegionId.WORKING;
    }

    private static boolean hasAnyStoredRecords(PartitionBundle bundle) {
        for (RegionId id : new RegionId[]{RegionId.SEMANTIC, RegionId.EPISODIC, RegionId.PROCEDURAL, RegionId.TEXT}) {
            if (bundle.hasRegion(id)) {
                MemorySegment slice = bundle.currentSlice(id);
                if (RegionPreamble.isValid(slice, 0) && RegionPreamble.readCount(slice, 0) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnyStoredRecords(RuntimeBundle bundle) {
        if (bundle.hasRegion(RegionId.WORKING)) {
            MemorySegment slice = bundle.currentSlice(RegionId.WORKING);
            return RegionPreamble.isValid(slice, 0) && RegionPreamble.readCount(slice, 0) > 0;
        }
        return false;
    }

    private static OptionalInt extractDimsFromDirectory(BundleDirectory dir, RegionId... regionIds) {
        if (dir == null) {
            return OptionalInt.empty();
        }
        for (RegionId targetId : regionIds) {
            RegionEntry entry = dir.findRegion(targetId);
            if (entry != null && entry.stride() > EncodingHeaderFields.HEADER_BYTES) {
                return OptionalInt.of(entry.stride() - EncodingHeaderFields.HEADER_BYTES);
            }
        }
        return OptionalInt.empty();
    }

    // ── Minimal Pure-JDK Top-Level JSON Object Parser & Formatter ──

    static LinkedHashMap<String, String> parseTopLevelJsonObject(String json) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }
        int len = json.length();
        int i = skipWhitespace(json, 0, len);
        if (i >= len || json.charAt(i) != '{') {
            return result;
        }
        i++; // skip '{'
        while (i < len) {
            i = skipWhitespace(json, i, len);
            if (i >= len || json.charAt(i) == '}') {
                break;
            }
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != '"') {
                break;
            }
            int keyEnd = findStringEnd(json, i, len);
            if (keyEnd < 0) {
                break;
            }
            String rawKey = json.substring(i, keyEnd + 1);
            String key = unquoteJsonString(rawKey);
            i = skipWhitespace(json, keyEnd + 1, len);
            if (i >= len || json.charAt(i) != ':') {
                break;
            }
            i = skipWhitespace(json, i + 1, len);
            if (i >= len) {
                break;
            }
            int valEnd = findValueEnd(json, i, len);
            if (valEnd < i) {
                break;
            }
            String rawValue = json.substring(i, valEnd).trim();
            if (key != null) {
                result.put(key, rawValue);
            }
            i = valEnd;
        }
        return result;
    }

    static String formatTopLevelJsonObject(Map<String, String> rawFields) {
        StringBuilder sb = new StringBuilder("{\n");
        int idx = 0;
        int size = rawFields.size();
        for (Map.Entry<String, String> entry : rawFields.entrySet()) {
            sb.append("  ").append(quoteJsonString(entry.getKey())).append(": ").append(entry.getValue());
            if (++idx < size) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static int skipWhitespace(String s, int idx, int len) {
        while (idx < len && Character.isWhitespace(s.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static int findStringEnd(String s, int startQuote, int len) {
        boolean escaped = false;
        for (int i = startQuote + 1; i < len; i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findValueEnd(String s, int start, int len) {
        char first = s.charAt(start);
        if (first == '"') {
            int endQuote = findStringEnd(s, start, len);
            return endQuote >= 0 ? endQuote + 1 : len;
        }
        if (first == '{' || first == '[') {
            int depth = 0;
            boolean inStr = false;
            boolean escaped = false;
            for (int i = start; i < len; i++) {
                char c = s.charAt(i);
                if (inStr) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else {
                    if (c == '"') {
                        inStr = true;
                    } else if (c == '{' || c == '[') {
                        depth++;
                    } else if (c == '}' || c == ']') {
                        depth--;
                        if (depth == 0) {
                            return i + 1;
                        }
                    }
                }
            }
            return len;
        }
        int i = start;
        while (i < len) {
            char c = s.charAt(i);
            if (c == ',' || c == '}') {
                break;
            }
            i++;
        }
        return i;
    }

    static String unquoteJsonString(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if ("null".equals(trimmed) || trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder(trimmed.length() - 2);
        for (int i = 1; i < trimmed.length() - 1; i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length() - 1) {
                char next = trimmed.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < trimmed.length() - 1) {
                            String hex = trimmed.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String quoteJsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static int parseJsonInt(String raw, int fallback) {
        if (raw == null || raw.isBlank() || "null".equals(raw.trim())) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
