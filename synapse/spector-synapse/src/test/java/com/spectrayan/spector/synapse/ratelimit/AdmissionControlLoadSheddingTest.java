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
package com.spectrayan.spector.synapse.ratelimit;

import com.spectrayan.spector.config.properties.RateLimitProperties.AdmissionControlProperties;
import com.spectrayan.spector.config.properties.RateLimitProperties.RouteConcurrencyPolicy;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.ratelimit.RequestAdmissionController.MemorySnapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class AdmissionControlLoadSheddingTest {

    @Test
    @DisplayName("Default configuration does not shed requests under normal load")
    void defaultConfigurationDoesNotShedUnderNormalLoad() throws Exception {
        SynapseProperties properties = new SynapseProperties();
        CaffeineRateLimitStateStore stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties);
        RateLimitFilter filter = new RateLimitFilter(properties, stateStore, keyResolver);

        for (int i = 0; i < 16; i++) {
            for (String path : List.of("/api/v1/memory/recall", "/api/v1/memory/remember", "/api/v1/admin/namespaces")) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
                req.setRemoteAddr("10.0.0." + (i + 1));
                MockHttpServletResponse resp = new MockHttpServletResponse();
                filter.doFilter(req, resp, new MockFilterChain());
                assertThat(resp.getStatus()).isEqualTo(200);
            }
        }
        assertThat(filter.getAdmissionController().globalInFlight()).isZero();
    }

    @Test
    @DisplayName("Exceeding per-route concurrency limit returns 503 with Retry-After and releases slots cleanly")
    void perRouteConcurrencyLimitShedsWith503AndReleasesSlot() throws Exception {
        SynapseProperties properties = new SynapseProperties();
        AdmissionControlProperties admission = properties.getRateLimit().getAdmission();
        admission.setMaxConcurrentRequests(8);
        admission.setRecallMaxConcurrentRequests(2);
        admission.setWriteMaxConcurrentRequests(1);
        admission.setManagementMaxConcurrentRequests(2);
        admission.setRetryAfterSeconds(3);
        admission.setRoutes(List.of(
                new RouteConcurrencyPolicy("custom-export", "/api/v1/export/**", List.of("POST"), 1)
        ));

        CaffeineRateLimitStateStore stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties);
        RateLimitFilter filter = new RateLimitFilter(properties, stateStore, keyResolver);

        CountDownLatch inFlightEntered = new CountDownLatch(2);
        CountDownLatch releaseInFlight = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            FilterChain blockingChain = (req, res) -> {
                inFlightEntered.countDown();
                try {
                    releaseInFlight.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            Future<Integer> r1 = pool.submit(() -> {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
                req.setRemoteAddr("10.10.0.1");
                MockHttpServletResponse resp = new MockHttpServletResponse();
                filter.doFilter(req, resp, blockingChain);
                return resp.getStatus();
            });
            Future<Integer> r2 = pool.submit(() -> {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
                req.setRemoteAddr("10.10.0.2");
                MockHttpServletResponse resp = new MockHttpServletResponse();
                filter.doFilter(req, resp, blockingChain);
                return resp.getStatus();
            });

            assertThat(inFlightEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // 3rd recall request exceeds recall route concurrency limit (2) -> 503 + Retry-After
            MockHttpServletRequest excessRecall = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
            excessRecall.setRemoteAddr("10.10.0.3");
            MockHttpServletResponse excessResp = new MockHttpServletResponse();
            filter.doFilter(excessRecall, excessResp, new MockFilterChain());

            assertThat(excessResp.getStatus()).isEqualTo(503);
            assertThat(excessResp.getHeader("Retry-After")).isEqualTo("3");
            assertThat(excessResp.getContentAsString())
                    .contains("\"type\":\"urn:spector:error:service-overloaded\"")
                    .contains("\"status\":503")
                    .contains("\"reason\":\"route_concurrency_limit_exceeded\"")
                    .contains("\"route\":\"recall\"")
                    .contains("\"retryAfterSeconds\":3");

            // Write route has its own separate concurrency budget and still succeeds while recall is full
            MockHttpServletRequest writeReq = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
            writeReq.setRemoteAddr("10.10.0.4");
            MockHttpServletResponse writeResp = new MockHttpServletResponse();
            filter.doFilter(writeReq, writeResp, new MockFilterChain());
            assertThat(writeResp.getStatus()).isEqualTo(200);

            // Health and metrics endpoints remain reachable while recall route is shedding
            for (String exemptPath : List.of(
                    "/actuator/health",
                    "/actuator/health/readiness",
                    "/actuator/metrics",
                    "/actuator/prometheus",
                    "/health",
                    "/api/v1/system/health",
                    "/api/v1/system/metrics")) {
                MockHttpServletRequest healthReq = new MockHttpServletRequest("GET", exemptPath);
                healthReq.setRemoteAddr("10.10.0.5");
                MockHttpServletResponse healthResp = new MockHttpServletResponse();
                filter.doFilter(healthReq, healthResp, new MockFilterChain());
                assertThat(healthResp.getStatus()).isEqualTo(200);
            }

            releaseInFlight.countDown();
            assertThat(r1.get(5, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(r2.get(5, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(filter.getAdmissionController().routeInFlight("recall")).isZero();
            assertThat(filter.getAdmissionController().globalInFlight()).isZero();

            // After in-flight requests complete, slot is released and next recall succeeds
            MockHttpServletRequest afterRelease = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
            afterRelease.setRemoteAddr("10.10.0.6");
            MockHttpServletResponse afterResp = new MockHttpServletResponse();
            filter.doFilter(afterRelease, afterResp, new MockFilterChain());
            assertThat(afterResp.getStatus()).isEqualTo(200);
        } finally {
            releaseInFlight.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Global concurrency limit sheds across routes and releases slots when downstream throws")
    void globalConcurrencyLimitShedsAcrossRoutesAndReleasesOnException() throws Exception {
        SynapseProperties properties = new SynapseProperties();
        AdmissionControlProperties admission = properties.getRateLimit().getAdmission();
        admission.setMaxConcurrentRequests(2);
        admission.setRecallMaxConcurrentRequests(5);
        admission.setWriteMaxConcurrentRequests(5);
        admission.setManagementMaxConcurrentRequests(5);
        admission.setRetryAfterSeconds(4);

        CaffeineRateLimitStateStore stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties);
        RateLimitFilter filter = new RateLimitFilter(properties, stateStore, keyResolver);

        CountDownLatch inFlightEntered = new CountDownLatch(2);
        CountDownLatch releaseInFlight = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            FilterChain blockingChain = (req, res) -> {
                inFlightEntered.countDown();
                try {
                    releaseInFlight.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            Future<Integer> recallFuture = pool.submit(() -> {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
                req.setRemoteAddr("10.11.0.1");
                MockHttpServletResponse resp = new MockHttpServletResponse();
                filter.doFilter(req, resp, blockingChain);
                return resp.getStatus();
            });
            Future<Integer> writeFuture = pool.submit(() -> {
                MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
                req.setRemoteAddr("10.11.0.2");
                MockHttpServletResponse resp = new MockHttpServletResponse();
                filter.doFilter(req, resp, blockingChain);
                return resp.getStatus();
            });

            assertThat(inFlightEntered.await(5, TimeUnit.SECONDS)).isTrue();

            MockHttpServletRequest thirdReq = new MockHttpServletRequest("GET", "/api/v1/admin/tenants");
            thirdReq.setRemoteAddr("10.11.0.3");
            MockHttpServletResponse thirdResp = new MockHttpServletResponse();
            filter.doFilter(thirdReq, thirdResp, new MockFilterChain());

            assertThat(thirdResp.getStatus()).isEqualTo(503);
            assertThat(thirdResp.getHeader("Retry-After")).isEqualTo("4");
            assertThat(thirdResp.getContentAsString())
                    .contains("\"reason\":\"global_concurrency_limit_exceeded\"");

            releaseInFlight.countDown();
            assertThat(recallFuture.get(5, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(writeFuture.get(5, TimeUnit.SECONDS)).isEqualTo(200);

            // Verify downstream exception still releases slot cleanly
            FilterChain failingChain = (req, res) -> {
                throw new ServletException("Simulated downstream failure");
            };
            MockHttpServletRequest failReq = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
            failReq.setRemoteAddr("10.11.0.4");
            assertThatThrownBy(() -> filter.doFilter(failReq, new MockHttpServletResponse(), failingChain))
                    .isInstanceOf(ServletException.class);
            assertThat(filter.getAdmissionController().globalInFlight()).isZero();
            assertThat(filter.getAdmissionController().routeInFlight("recall")).isZero();
        } finally {
            releaseInFlight.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Heap and off-heap memory pressure shed with 503 until usage drops below recovery threshold")
    void memoryPressureHighWaterMarkShedsUntilBelowRecoveryThreshold() throws Exception {
        SynapseProperties properties = new SynapseProperties();
        AdmissionControlProperties admission = properties.getRateLimit().getAdmission();
        admission.setHeapHighWaterMarkRatio(0.80);
        admission.setHeapRecoveryRatio(0.70);
        admission.setOffHeapHighWaterMarkRatio(0.85);
        admission.setOffHeapRecoveryRatio(0.75);
        admission.setRetryAfterSeconds(5);

        long totalBytes = 1_000_000_000L;
        AtomicReference<MemorySnapshot> currentMemory = new AtomicReference<>(
                new MemorySnapshot(600_000_000L, totalBytes, 500_000_000L, totalBytes));

        RequestAdmissionController admissionController =
                new RequestAdmissionController(properties, currentMemory::get);
        CaffeineRateLimitStateStore stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties);
        RateLimitFilter filter = new RateLimitFilter(properties, stateStore, keyResolver, admissionController);

        // 1. Normal memory (60% heap, 50% off-heap) -> admitted (200 OK)
        assertThat(executeRequest(filter, "POST", "/api/v1/memory/recall").getStatus()).isEqualTo(200);

        // 2. Heap breaches high-water mark (85% >= 80%) -> shed with 503 + Retry-After
        currentMemory.set(new MemorySnapshot(850_000_000L, totalBytes, 500_000_000L, totalBytes));
        MockHttpServletResponse heapShedResp = executeRequest(filter, "POST", "/api/v1/memory/recall");
        assertThat(heapShedResp.getStatus()).isEqualTo(503);
        assertThat(heapShedResp.getHeader("Retry-After")).isEqualTo("5");
        assertThat(heapShedResp.getContentAsString()).contains("\"reason\":\"heap_memory_pressure\"");

        // Health and metrics endpoints remain reachable during heap memory shedding
        assertThat(executeRequest(filter, "GET", "/actuator/health").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/actuator/metrics").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/actuator/prometheus").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/health").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/api/v1/system/health").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/api/v1/system/metrics").getStatus()).isEqualTo(200);

        // 3. Heap drops to 75% (below 80% high-water mark, but above 70% recovery threshold) -> still shedding (503)
        currentMemory.set(new MemorySnapshot(750_000_000L, totalBytes, 500_000_000L, totalBytes));
        assertThat(executeRequest(filter, "POST", "/api/v1/memory/recall").getStatus()).isEqualTo(503);

        // 4. Heap drops to 68% (<= 70% recovery threshold) -> recovers (200 OK)
        currentMemory.set(new MemorySnapshot(680_000_000L, totalBytes, 500_000_000L, totalBytes));
        assertThat(executeRequest(filter, "POST", "/api/v1/memory/recall").getStatus()).isEqualTo(200);

        // 5. Off-heap breaches high-water mark (90% >= 85%) -> shed with 503 + Retry-After
        currentMemory.set(new MemorySnapshot(600_000_000L, totalBytes, 900_000_000L, totalBytes));
        MockHttpServletResponse offHeapShedResp = executeRequest(filter, "POST", "/api/v1/memory/remember");
        assertThat(offHeapShedResp.getStatus()).isEqualTo(503);
        assertThat(offHeapShedResp.getHeader("Retry-After")).isEqualTo("5");
        assertThat(offHeapShedResp.getContentAsString()).contains("\"reason\":\"off_heap_memory_pressure\"");
        assertThat(executeRequest(filter, "GET", "/actuator/health").getStatus()).isEqualTo(200);
        assertThat(executeRequest(filter, "GET", "/actuator/prometheus").getStatus()).isEqualTo(200);

        // 6. Off-heap in hysteresis band (80% > 75% recovery) -> still shedding (503)
        currentMemory.set(new MemorySnapshot(600_000_000L, totalBytes, 800_000_000L, totalBytes));
        assertThat(executeRequest(filter, "POST", "/api/v1/memory/remember").getStatus()).isEqualTo(503);

        // 7. Off-heap drops to 72% (<= 75% recovery threshold) -> recovers (200 OK)
        currentMemory.set(new MemorySnapshot(600_000_000L, totalBytes, 720_000_000L, totalBytes));
        assertThat(executeRequest(filter, "POST", "/api/v1/memory/remember").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Benchmark overload burst: bounds in-flight concurrency and sheds excess with 503")
    void overloadBurstBoundsConcurrencyAndShedsFast() throws Exception {
        SynapseProperties properties = new SynapseProperties();
        AdmissionControlProperties admission = properties.getRateLimit().getAdmission();
        admission.setMaxConcurrentRequests(4);
        admission.setRecallMaxConcurrentRequests(4);
        admission.setWriteMaxConcurrentRequests(4);
        admission.setManagementMaxConcurrentRequests(4);
        admission.setRetryAfterSeconds(2);

        CaffeineRateLimitStateStore stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties);
        RateLimitFilter filter = new RateLimitFilter(properties, stateStore, keyResolver);

        int totalRequests = 32;
        AtomicInteger currentInFlight = new AtomicInteger();
        AtomicInteger peakInFlight = new AtomicInteger();
        AtomicInteger status200 = new AtomicInteger();
        AtomicInteger status503 = new AtomicInteger();
        AtomicLong aggregateLatencyNanos = new AtomicLong();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(totalRequests);
        try {
            FilterChain slowDownstream = (req, res) -> {
                int cur = currentInFlight.incrementAndGet();
                peakInFlight.accumulateAndGet(cur, Math::max);
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    currentInFlight.decrementAndGet();
                }
            };

            List<Future<?>> futures = new ArrayList<>();
            long t0 = System.nanoTime();
            for (int i = 0; i < totalRequests; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    long reqStart = System.nanoTime();
                    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/memory/recall");
                    req.setRemoteAddr("10.20.0." + (idx + 1));
                    MockHttpServletResponse resp = new MockHttpServletResponse();
                    filter.doFilter(req, resp, slowDownstream);
                    aggregateLatencyNanos.addAndGet(System.nanoTime() - reqStart);
                    if (resp.getStatus() == 200) {
                        status200.incrementAndGet();
                    } else if (resp.getStatus() == 503) {
                        status503.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }

            assertThat(peakInFlight.get()).isLessThanOrEqualTo(4);
            assertThat(status503.get()).isGreaterThanOrEqualTo(28);
            assertThat(status200.get()).isLessThanOrEqualTo(4);
            assertThat(aggregateLatencyNanos.get()).isPositive();
        } finally {
            pool.shutdownNow();
        }
    }

    private static MockHttpServletResponse executeRequest(RateLimitFilter filter, String method, String path)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr("10.30.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        return resp;
    }
}
