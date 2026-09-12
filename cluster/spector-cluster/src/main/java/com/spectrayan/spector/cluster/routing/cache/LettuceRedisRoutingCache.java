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
package com.spectrayan.spector.cluster.routing.cache;

import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Lettuce-based implementation of {@link RedisRoutingCache} with fast-fail timeout,
 * conditional write-behind, and state-transition logging (ADR-0034 §8, Req R1, R3, R6).
 */
public class LettuceRedisRoutingCache implements RedisRoutingCache {

    private static final Logger log = LoggerFactory.getLogger(LettuceRedisRoutingCache.class);

    private static final String CONDITIONAL_PUT_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 0 then\n" +
            "  redis.call('HSET', KEYS[1], 'owner', ARGV[1], 'epoch', ARGV[2], 'cell', ARGV[3], 'mode', ARGV[4], 'updated_at', ARGV[5])\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[6])\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    public static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofSeconds(5);

    private final RedisClient client;
    private final boolean ownsClientLifecycle;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final long timeoutMs;
    private final RoutingMetricsListener metricsListener;
    private final String sanitizedUri;
    private final AtomicBoolean isDegraded = new AtomicBoolean(false);
    private final AtomicLong degradedSinceNanos = new AtomicLong(0L);
    private final long probeIntervalNanos;
    private final LongSupplier nanoTimeSupplier;

    public LettuceRedisRoutingCache(String redisUri, long timeoutMs, RoutingMetricsListener metricsListener) {
        this(redisUri, timeoutMs, metricsListener, DEFAULT_PROBE_INTERVAL, System::nanoTime);
    }

    public LettuceRedisRoutingCache(
            String redisUri,
            long timeoutMs,
            RoutingMetricsListener metricsListener,
            Duration probeInterval,
            LongSupplier nanoTimeSupplier
    ) {
        Objects.requireNonNull(redisUri, "redisUri must not be null");
        this.timeoutMs = Math.max(10L, timeoutMs);
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;
        this.sanitizedUri = sanitizeUri(redisUri);
        this.probeIntervalNanos = (probeInterval != null ? probeInterval : DEFAULT_PROBE_INTERVAL).toNanos();
        this.nanoTimeSupplier = nanoTimeSupplier != null ? nanoTimeSupplier : System::nanoTime;
        this.ownsClientLifecycle = true;

        RedisURI uri = RedisURI.create(redisUri);
        uri.setTimeout(Duration.ofMillis(this.timeoutMs));

        this.client = RedisClient.create(uri);
        this.client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(this.timeoutMs)).build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(this.timeoutMs)))
                .build());

        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = this.client.connect();
        } catch (Exception e) {
            handleFailure(e, "initial connection");
        }

        this.connection = conn;
        this.asyncCommands = this.connection != null ? this.connection.async() : null;
    }

    /**
     * Constructor using a shared RedisClient (G49).
     */
    public LettuceRedisRoutingCache(
            RedisClient client,
            String redisUri,
            long timeoutMs,
            RoutingMetricsListener metricsListener
    ) {
        this(client, redisUri, timeoutMs, metricsListener, DEFAULT_PROBE_INTERVAL, System::nanoTime);
    }

    public LettuceRedisRoutingCache(
            RedisClient client,
            String redisUri,
            long timeoutMs,
            RoutingMetricsListener metricsListener,
            Duration probeInterval,
            LongSupplier nanoTimeSupplier
    ) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.timeoutMs = Math.max(10L, timeoutMs);
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;
        this.sanitizedUri = redisUri != null ? sanitizeUri(redisUri) : "shared://redis";
        this.probeIntervalNanos = (probeInterval != null ? probeInterval : DEFAULT_PROBE_INTERVAL).toNanos();
        this.nanoTimeSupplier = nanoTimeSupplier != null ? nanoTimeSupplier : System::nanoTime;
        this.ownsClientLifecycle = false;

        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = this.client.connect();
        } catch (Exception e) {
            handleFailure(e, "initial connection");
        }

        this.connection = conn;
        this.asyncCommands = this.connection != null ? this.connection.async() : null;
    }

    /**
     * Package-private constructor for unit testing with a mock or existing connection.
     */
    LettuceRedisRoutingCache(StatefulRedisConnection<String, String> connection, long timeoutMs, RoutingMetricsListener metricsListener) {
        this(connection, timeoutMs, metricsListener, DEFAULT_PROBE_INTERVAL, System::nanoTime);
    }

    LettuceRedisRoutingCache(
            StatefulRedisConnection<String, String> connection,
            long timeoutMs,
            RoutingMetricsListener metricsListener,
            Duration probeInterval,
            LongSupplier nanoTimeSupplier
    ) {
        this.client = null;
        this.ownsClientLifecycle = false;
        this.connection = connection;
        this.asyncCommands = connection != null ? connection.async() : null;
        this.timeoutMs = Math.max(10L, timeoutMs);
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;
        this.sanitizedUri = "mock://redis";
        this.probeIntervalNanos = (probeInterval != null ? probeInterval : DEFAULT_PROBE_INTERVAL).toNanos();
        this.nanoTimeSupplier = nanoTimeSupplier != null ? nanoTimeSupplier : System::nanoTime;
    }

    @Override
    public Optional<RouteBinding> get(RoutingKey key) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            String redisKey = key.redisHashKey();
            Map<String, String> hash = asyncCommands.hgetall(redisKey).get(timeoutMs, TimeUnit.MILLISECONDS);
            if (hash == null || hash.isEmpty() || !hash.containsKey("owner")) {
                handleSuccess();
                return Optional.empty();
            }

            String ownerId = hash.get("owner");
            if (ownerId == null || ownerId.isBlank()) {
                handleSuccess();
                return Optional.empty();
            }

            // G47: treat missing or unparseable epoch/mode as cache miss
            String epochStr = hash.get("epoch");
            if (epochStr == null || epochStr.isBlank()) {
                handleSuccess();
                return Optional.empty();
            }
            long epoch;
            try {
                epoch = Long.parseLong(epochStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Corrupt epoch '{}' for key {}; treating as cache miss (G47)", epochStr, key.keyMaterial());
                handleSuccess();
                return Optional.empty();
            }

            String modeStr = hash.get("mode");
            if (modeStr == null || modeStr.isBlank()) {
                handleSuccess();
                return Optional.empty();
            }
            RouteMode mode;
            try {
                mode = RouteMode.valueOf(modeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warn("Corrupt route mode '{}' for key {}; treating as cache miss (G47)", modeStr, key.keyMaterial());
                handleSuccess();
                return Optional.empty();
            }

            String fence = hash.get("fence");
            Long hwm = hash.containsKey("hwm") ? parseLongSafely(hash.get("hwm"), 0L) : null;

            handleSuccess();
            return Optional.of(new RouteBinding(key, ownerId, epoch, fence, hwm, mode));
        } catch (Exception e) {
            handleFailure(e, "get " + key.keyMaterial());
            return Optional.empty();
        }
    }

    @Override
    public boolean putIfAbsent(RoutingKey key, RouteBinding binding, long ttlSeconds) {
        if (!isAvailable()) {
            return false;
        }

        try {
            String redisKey = key.redisHashKey();
            String owner = binding.ownerId();
            String epoch = String.valueOf(binding.epoch());
            String cell = key.cellId() != null ? key.cellId() : "default";
            String mode = RouteMode.HASH.name(); // Invariant K3: always write mode=HASH on cache fill
            String updatedAt = Instant.now().toString();
            String ttl = String.valueOf(Math.max(1L, ttlSeconds));

            Object rawResult = asyncCommands.eval(
                    CONDITIONAL_PUT_LUA,
                    ScriptOutputType.INTEGER,
                    new String[]{redisKey},
                    owner, epoch, cell, mode, updatedAt, ttl
            ).get(timeoutMs, TimeUnit.MILLISECONDS);

            Long result = rawResult instanceof Number num ? num.longValue() : 0L;

            handleSuccess();
            return result != null && result == 1L;
        } catch (Exception e) {
            handleFailure(e, "putIfAbsent " + key.keyMaterial());
            return false;
        }
    }

    @Override
    public void invalidate(RoutingKey key) {
        if (!isAvailable()) {
            return;
        }

        try {
            asyncCommands.del(key.redisHashKey()).get(timeoutMs, TimeUnit.MILLISECONDS);
            handleSuccess();
        } catch (Exception e) {
            handleFailure(e, "invalidate " + key.keyMaterial());
        }
    }

    @Override
    public void publishInvalidation(String cellId, String nsKey, long epoch, String reason) {
        if (!isAvailable()) {
            return;
        }

        try {
            String channel = RoutingKey.redisInvalidationChannel(cellId);
            String payload = new com.spectrayan.spector.cluster.routing.invalidation.RoutingInvalidationMessage(
                    nsKey != null ? nsKey : "", epoch, reason != null ? reason : "").toJson();
            asyncCommands.publish(channel, payload).get(timeoutMs, TimeUnit.MILLISECONDS);
            handleSuccess();
        } catch (Exception e) {
            handleFailure(e, "publishInvalidation to cell " + cellId);
        }
    }

    @Override
    public boolean isAvailable() {
        if (connection == null || !connection.isOpen()) {
            return false;
        }
        if (!isDegraded.get()) {
            return true;
        }
        // G38: If degraded, allow a probe retry once probeInterval has elapsed
        long since = degradedSinceNanos.get();
        return since > 0 && (nanoTimeSupplier.getAsLong() - since) >= probeIntervalNanos;
    }

    public boolean isDegraded() {
        return isDegraded.get();
    }

    private void handleSuccess() {
        degradedSinceNanos.set(0L);
        if (isDegraded.compareAndSet(true, false)) {
            log.info("Redis routing cache connectivity restored ({}); resuming distributed L2 lookups", sanitizedUri);
            metricsListener.recordDegradationTransition(false);
        }
    }

    private void handleFailure(Exception e, String op) {
        degradedSinceNanos.set(nanoTimeSupplier.getAsLong());
        if (isDegraded.compareAndSet(false, true)) {
            log.warn("Redis routing cache unreachable during {} ({}): {}; degrading to ConsistentHashRing fallback (Req R6.5)",
                    op, sanitizedUri, e.getMessage());
            metricsListener.recordDegradationTransition(true);
        } else {
            log.debug("Redis routing cache operation {} failed while in degraded state: {}", op, e.getMessage());
        }
    }

    private static String sanitizeUri(String uri) {
        try {
            return uri.replaceAll(":[^@]+@", ":***@");
        } catch (Exception e) {
            return "redis://***";
        }
    }

    private static long parseLongSafely(String val, long fallback) {
        if (val == null || val.isBlank()) return fallback;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static RouteMode parseModeSafely(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) return RouteMode.HASH;
        try {
            return RouteMode.valueOf(modeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RouteMode.HASH;
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {}
        try {
            if (client != null && ownsClientLifecycle) {
                client.shutdown();
            }
        } catch (Exception ignored) {}
    }
}
