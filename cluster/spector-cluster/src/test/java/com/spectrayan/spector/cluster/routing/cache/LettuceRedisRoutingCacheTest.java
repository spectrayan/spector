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
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Findings G38 & G47: LettuceRedisRoutingCache Recovery and Parse Hardening")
class LettuceRedisRoutingCacheTest {

    @SuppressWarnings("unchecked")
    private RedisFuture<Map<String, String>> createMockRedisFuture(AtomicBoolean shouldFail, AtomicReference<Map<String, String>> responseHash) {
        return (RedisFuture<Map<String, String>>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RedisFuture.class},
                (proxy, method, args) -> {
                    if ("get".equals(method.getName())) {
                        if (shouldFail != null && shouldFail.get()) {
                            throw new ExecutionException(new RuntimeException("Redis connection timed out"));
                        }
                        return responseHash.get();
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("G38: Transient failure degrades cache but recovers after probe interval")
    void testTransientFailureDegradesAndRecoversAfterProbeInterval() {
        AtomicLong simulatedNanos = new AtomicLong(1_000_000_000L);
        AtomicBoolean shouldFail = new AtomicBoolean(false);
        AtomicReference<Map<String, String>> responseHash = new AtomicReference<>(
                Map.of("owner", "node-1", "epoch", "2", "mode", "HASH")
        );

        @SuppressWarnings("unchecked")
        RedisAsyncCommands<String, String> asyncCommands = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RedisAsyncCommands.class},
                (proxy, method, args) -> {
                    if ("hgetall".equals(method.getName())) {
                        return createMockRedisFuture(shouldFail, responseHash);
                    }
                    return null;
                }
        );

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection = (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{StatefulRedisConnection.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) return true;
                    if ("async".equals(method.getName())) return asyncCommands;
                    return null;
                }
        );

        Duration probeInterval = Duration.ofSeconds(5);
        LettuceRedisRoutingCache cache = new LettuceRedisRoutingCache(
                connection,
                100L,
                RoutingMetricsListener.NOOP,
                probeInterval,
                simulatedNanos::get
        );

        RoutingKey key = RoutingKey.ofUntenanted("cell-1", "ns-test");

        // 1. Initial healthy state
        assertThat(cache.isAvailable()).isTrue();
        assertThat(cache.isDegraded()).isFalse();
        Optional<RouteBinding> initial = cache.get(key);
        assertThat(initial).isPresent();
        assertThat(initial.get().ownerId()).isEqualTo("node-1");

        // 2. Outage strikes: get() throws timeout exception
        shouldFail.set(true);
        Optional<RouteBinding> failed = cache.get(key);
        assertThat(failed).isEmpty();
        assertThat(cache.isDegraded()).isTrue();

        // 3. Immediately after outage, cache is NOT available (probe interval has not elapsed)
        simulatedNanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(cache.isAvailable()).isFalse();

        // 4. Advance time past probe interval (5 seconds)
        simulatedNanos.addAndGet(Duration.ofSeconds(4).toNanos()); // total 6 seconds elapsed
        assertThat(cache.isAvailable()).isTrue(); // Probe window open!

        // 5. Redis has recovered: probe call succeeds
        shouldFail.set(false);
        Optional<RouteBinding> recovered = cache.get(key);
        assertThat(recovered).isPresent();
        assertThat(recovered.get().ownerId()).isEqualTo("node-1");

        // 6. Verification: degradation is cleared without restart!
        assertThat(cache.isDegraded()).isFalse();
        assertThat(cache.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("G47: Missing or unparseable epoch or mode treated as cache miss")
    void testCorruptHashTreatedAsCacheMiss() {
        AtomicReference<Map<String, String>> responseHash = new AtomicReference<>();

        @SuppressWarnings("unchecked")
        RedisAsyncCommands<String, String> asyncCommands = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RedisAsyncCommands.class},
                (proxy, method, args) -> {
                    if ("hgetall".equals(method.getName())) {
                        return createMockRedisFuture(null, responseHash);
                    }
                    return null;
                }
        );

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection = (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{StatefulRedisConnection.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) return true;
                    if ("async".equals(method.getName())) return asyncCommands;
                    return null;
                }
        );

        LettuceRedisRoutingCache cache = new LettuceRedisRoutingCache(
                connection,
                100L,
                RoutingMetricsListener.NOOP
        );

        RoutingKey key = RoutingKey.ofUntenanted("cell-1", "ns-corrupt");

        // Missing epoch -> cache miss
        responseHash.set(Map.of("owner", "node-1", "mode", "HASH"));
        assertThat(cache.get(key)).isEmpty();

        // Malformed epoch -> cache miss
        responseHash.set(Map.of("owner", "node-1", "epoch", "abc", "mode", "HASH"));
        assertThat(cache.get(key)).isEmpty();

        // Missing mode -> cache miss
        responseHash.set(Map.of("owner", "node-1", "epoch", "1"));
        assertThat(cache.get(key)).isEmpty();

        // Malformed mode -> cache miss
        responseHash.set(Map.of("owner", "node-1", "epoch", "1", "mode", "INVALID_MODE"));
        assertThat(cache.get(key)).isEmpty();

        // Valid hash -> cache hit
        responseHash.set(Map.of("owner", "node-1", "epoch", "5", "mode", "OVERRIDE"));
        Optional<RouteBinding> valid = cache.get(key);
        assertThat(valid).isPresent();
        assertThat(valid.get().ownerId()).isEqualTo("node-1");
        assertThat(valid.get().epoch()).isEqualTo(5L);
        assertThat(valid.get().mode()).isEqualTo(RouteMode.OVERRIDE);
    }
}
