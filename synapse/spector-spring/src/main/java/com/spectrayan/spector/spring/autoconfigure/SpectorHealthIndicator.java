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
package com.spectrayan.spector.spring.autoconfigure;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Spring Boot Actuator health indicator for Spector.
 *
 * <p>Reports SIMD capability, cognitive memory tier counts, filesystem validation,
 * kernel map-count headroom, and cell ownership readiness at {@code /actuator/health}.</p>
 *
 * <p>Enforces readiness invariants (Req R2.6, R6.5, R6.6, T5):
 * <ul>
 *   <li><b>Volume writability:</b> Data directory must exist (or be creatable) and be writable.</li>
 *   <li><b>Filesystem type:</b> Off-heap mmap behaviour is validated on XFS and ext4; warns otherwise.</li>
 *   <li><b>V4 map count:</b> Required map count is computed using V4 arithmetic (~2 maps per open namespace
 *       for {@code runtime.bundle} and active {@code partition.bundle}, plus WAL and libc/JVM overhead).
 *       Fails readiness if host {@code vm.max_map_count} is insufficient.</li>
 *   <li><b>Cell Ring:</b> Fails readiness if cluster mode is active and the consistent hash ring is not loaded.</li>
 * </ul>
 * </p>
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
public class SpectorHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SpectorHealthIndicator.class);

    /**
     * Maps per namespace in V4 layout: runtime.bundle and active partition.bundle (ADR-0004).
     */
    public static final int MAPS_PER_NAMESPACE_V4 = 2;

    /** Default WAL segments across active namespaces. */
    public static final int DEFAULT_WAL_SEGMENTS = 64;

    /** Default libc/JVM and headroom map count overhead. */
    public static final int DEFAULT_MAP_COUNT_OVERHEAD = 256;

    /** Default pager hotCap. */
    public static final int DEFAULT_HOT_CAP = 2000;

    /** Default Linux sysctl max_map_count file path. */
    public static final String DEFAULT_SYSCTL_MAX_MAP_COUNT_PATH = "/proc/sys/vm/max_map_count";

    /** Default data directory when spector.data-dir is not injected. */
    public static final String DEFAULT_DATA_DIR = SpectorPropertyConstants.DEFAULT_SERVER_DATA_DIR;

    private final SpectorMemory memory;
    private Path dataDir;
    private int hotCap = DEFAULT_HOT_CAP;
    private int walSegments = DEFAULT_WAL_SEGMENTS;
    private int mapCountOverhead = DEFAULT_MAP_COUNT_OVERHEAD;
    private Path sysctlMaxMapCountPath = Path.of(DEFAULT_SYSCTL_MAX_MAP_COUNT_PATH);
    private BooleanSupplier ringLoadedSupplier;
    private boolean clusterMode = false;

    public SpectorHealthIndicator(SpectorMemory memory) {
        this(memory, Path.of(DEFAULT_DATA_DIR), DEFAULT_HOT_CAP, null, false);
    }

    public SpectorHealthIndicator(
            SpectorMemory memory,
            Path dataDir,
            int hotCap,
            BooleanSupplier ringLoadedSupplier,
            boolean clusterMode) {
        this.memory = memory;
        this.dataDir = dataDir != null ? dataDir : Path.of(DEFAULT_DATA_DIR);
        this.hotCap = hotCap > 0 ? hotCap : DEFAULT_HOT_CAP;
        this.ringLoadedSupplier = ringLoadedSupplier;
        this.clusterMode = clusterMode;
    }

    @Autowired
    public SpectorHealthIndicator(
            ObjectProvider<SpectorMemory> memoryProvider,
            @Value("${spector.data-dir:./spector-data}") String dataDir,
            @Value("${spector.pager.hot-cap:2000}") int hotCap,
            @Value("${spector.cluster-mode-enabled:false}") boolean clusterMode,
            ObjectProvider<com.spectrayan.spector.cluster.OwnershipResolver> ownershipResolverProvider) {
        this(
                memoryProvider != null ? memoryProvider.getIfAvailable() : null,
                Path.of(dataDir != null && !dataDir.isBlank() ? dataDir : DEFAULT_DATA_DIR),
                hotCap,
                resolveRingSupplier(ownershipResolverProvider),
                clusterMode
        );
    }

    private static BooleanSupplier resolveRingSupplier(
            ObjectProvider<com.spectrayan.spector.cluster.OwnershipResolver> ownershipResolverProvider) {
        if (ownershipResolverProvider == null) {
            return null;
        }
        var resolver = ownershipResolverProvider.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        return () -> resolver.ring().isPresent();
    }

    /**
     * Computes the required map count using V4 layout arithmetic (Req R6.6).
     *
     * <p>{@code requiredMaps = (hotCap * 2) + walSegments + overhead}</p>
     *
     * @param hotCap maximum active namespaces in memory
     * @param walSegments WAL file descriptor/map count estimate
     * @param overhead libc, JVM thread stacks, and headroom allowance
     * @return minimum required max_map_count
     */
    public static long computeRequiredMaps(int hotCap, int walSegments, int overhead) {
        return ((long) Math.max(1, hotCap) * MAPS_PER_NAMESPACE_V4) + walSegments + overhead;
    }

    /**
     * Computes the required map count using default WAL and overhead constants.
     */
    public static long computeRequiredMaps(int hotCap) {
        return computeRequiredMaps(hotCap, DEFAULT_WAL_SEGMENTS, DEFAULT_MAP_COUNT_OVERHEAD);
    }

    /**
     * Verifies if the given filesystem type string is validated for memory-mapped operation.
     */
    public static boolean isSupportedFilesystem(String fsType) {
        if (fsType == null || fsType.isBlank()) {
            return false;
        }
        String lower = fsType.toLowerCase(Locale.ROOT);
        return lower.contains("xfs") || lower.contains("ext4");
    }

    @Override
    public Health health() {
        try {
            boolean isDown = false;
            var builder = Health.up()
                    .withDetail("simd", SimdCapability.report());

            if (memory != null) {
                builder.withDetail("memory.total", memory.totalMemories());
                builder.withDetail("memory.working", memory.memoryCount(MemoryType.WORKING));
                builder.withDetail("memory.episodic", memory.memoryCount(MemoryType.EPISODIC));
                builder.withDetail("memory.semantic", memory.memoryCount(MemoryType.SEMANTIC));
                builder.withDetail("memory.procedural", memory.memoryCount(MemoryType.PROCEDURAL));
            }

            // 1. Data directory mount & writability check (Req R2.6, T5)
            Path resolvedDir = dataDir != null ? dataDir : Path.of(DEFAULT_DATA_DIR);
            builder.withDetail("disk.path", resolvedDir.toAbsolutePath().toString());
            try {
                if (!Files.exists(resolvedDir)) {
                    Files.createDirectories(resolvedDir);
                }
                if (!Files.isWritable(resolvedDir)) {
                    isDown = true;
                    builder.withDetail("disk.status", "UNWRITABLE");
                    builder.withDetail("disk.writable", false);
                    builder.withDetail("disk.error", "Data directory is not writable: " + resolvedDir.toAbsolutePath());
                    log.error("[SpectorHealthIndicator] Data directory is not writable: {}", resolvedDir.toAbsolutePath());
                } else {
                    builder.withDetail("disk.status", "OK");
                    builder.withDetail("disk.writable", true);

                    // Filesystem type check (Req R6.5, Task 5.6)
                    try {
                        FileStore store = Files.getFileStore(resolvedDir);
                        String fsType = store.type();
                        builder.withDetail("filesystem.type", fsType);
                        if (isSupportedFilesystem(fsType)) {
                            builder.withDetail("filesystem.status", "OK");
                        } else {
                            builder.withDetail("filesystem.status", "WARN_UNSUPPORTED_TYPE");
                            String warnMsg = String.format(
                                    "Filesystem type '%s' is not XFS or ext4. Off-heap mmap working set behaviour is only validated on XFS and ext4.",
                                    fsType);
                            builder.withDetail("filesystem.warning", warnMsg);
                            log.warn("[SpectorHealthIndicator] {}", warnMsg);
                        }
                    } catch (Exception e) {
                        builder.withDetail("filesystem.status", "UNKNOWN");
                        builder.withDetail("filesystem.error", e.getMessage());
                    }
                }
            } catch (Exception e) {
                isDown = true;
                builder.withDetail("disk.status", "UNWRITABLE");
                builder.withDetail("disk.writable", false);
                builder.withDetail("disk.error", e.getMessage());
                log.error("[SpectorHealthIndicator] Failed to access data directory: {}", e.getMessage());
            }

            // 2. V4 Map-Count check (Req R6.6, Task 5.7, 5.8, 5.10, T5)
            long requiredMaps = computeRequiredMaps(hotCap, walSegments, mapCountOverhead);
            builder.withDetail("map_count.required", requiredMaps);
            builder.withDetail("map_count.hot_cap", hotCap);
            builder.withDetail("map_count.wal_segments", walSegments);
            builder.withDetail("map_count.overhead", mapCountOverhead);

            if (sysctlMaxMapCountPath != null && Files.exists(sysctlMaxMapCountPath) && Files.isReadable(sysctlMaxMapCountPath)) {
                try {
                    String content;
                    try (var reader = Files.newBufferedReader(sysctlMaxMapCountPath)) {
                        content = reader.readLine();
                    }
                    if (content != null && !content.isBlank()) {
                        long actualMapCount = Long.parseLong(content.trim());
                        builder.withDetail("map_count.actual", actualMapCount);

                        if (actualMapCount < requiredMaps) {
                            isDown = true;
                            builder.withDetail("map_count.status", "INSUFFICIENT");
                            String warningMsg = String.format(
                                    "Insufficient vm.max_map_count (%d) for pager.hotCap=%d (requires at least %d maps: (%d * %d) + %d WAL + %d overhead)",
                                    actualMapCount, hotCap, requiredMaps, hotCap, MAPS_PER_NAMESPACE_V4, walSegments, mapCountOverhead);
                            builder.withDetail("map_count.error", warningMsg);
                            log.error("[SpectorHealthIndicator] {}", warningMsg);
                        } else {
                            builder.withDetail("map_count.status", "OK");
                        }
                    } else {
                        builder.withDetail("map_count.status", "EMPTY");
                    }
                } catch (Exception e) {
                    builder.withDetail("map_count.status", "ERROR_READING");
                    builder.withDetail("map_count.error", e.getMessage());
                }
            } else {
                builder.withDetail("map_count.status", "UNAVAILABLE_NON_LINUX");
            }

            // 3. Ring Loaded check (Req R2.6, T5)
            if (ringLoadedSupplier != null) {
                boolean ringLoaded = ringLoadedSupplier.getAsBoolean();
                if (!ringLoaded) {
                    isDown = true;
                    builder.withDetail("ring.status", "NOT_LOADED");
                    builder.withDetail("ring.error", "Ownership ring is not loaded; node cannot take partition ownership");
                    log.error("[SpectorHealthIndicator] Ownership ring is not loaded; failing readiness");
                } else {
                    builder.withDetail("ring.status", "OK");
                }
            } else if (clusterMode) {
                isDown = true;
                builder.withDetail("ring.status", "NOT_LOADED");
                builder.withDetail("ring.error", "Cluster mode is enabled but no ownership ring supplier is configured");
                log.error("[SpectorHealthIndicator] Cluster mode enabled with no ownership ring supplier");
            } else {
                builder.withDetail("ring.status", "STANDALONE");
            }

            if (isDown) {
                return builder.down().build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }

    public SpectorHealthIndicator setDataDir(Path dataDir) {
        this.dataDir = dataDir;
        return this;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public SpectorHealthIndicator setHotCap(int hotCap) {
        this.hotCap = hotCap;
        return this;
    }

    public int getHotCap() {
        return hotCap;
    }

    public SpectorHealthIndicator setWalSegments(int walSegments) {
        this.walSegments = walSegments;
        return this;
    }

    public int getWalSegments() {
        return walSegments;
    }

    public SpectorHealthIndicator setMapCountOverhead(int mapCountOverhead) {
        this.mapCountOverhead = mapCountOverhead;
        return this;
    }

    public int getMapCountOverhead() {
        return mapCountOverhead;
    }

    public SpectorHealthIndicator setSysctlMaxMapCountPath(Path path) {
        this.sysctlMaxMapCountPath = path;
        return this;
    }

    public Path getSysctlMaxMapCountPath() {
        return sysctlMaxMapCountPath;
    }

    public SpectorHealthIndicator setRingLoadedSupplier(BooleanSupplier ringLoadedSupplier) {
        this.ringLoadedSupplier = ringLoadedSupplier;
        return this;
    }

    public SpectorHealthIndicator setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
        return this;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }
}
