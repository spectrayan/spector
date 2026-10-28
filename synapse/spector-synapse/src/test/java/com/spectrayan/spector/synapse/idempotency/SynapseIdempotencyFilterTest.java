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
package com.spectrayan.spector.synapse.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.cluster.idempotency.IdempotencyReservation;
import com.spectrayan.spector.cluster.idempotency.InMemoryIdempotencyStore;
import com.spectrayan.spector.cluster.idempotency.RequestFingerprint;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Synapse Owner/Standalone IdempotencyFilter & Durable Persistence Tests")
class SynapseIdempotencyFilterTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private SynapseProperties properties;
    private JdbcIdempotencyPersistenceBackend persistenceBackend;
    private InMemoryIdempotencyStore store;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:idem_test_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        jdbcTemplate = new JdbcTemplate(ds);
        objectMapper = new ObjectMapper();
        properties = new SynapseProperties();
        persistenceBackend = new JdbcIdempotencyPersistenceBackend(jdbcTemplate, objectMapper);
        store = new InMemoryIdempotencyStore(Duration.ofMinutes(10), 100, Clock.systemUTC(), persistenceBackend);
        filter = new IdempotencyFilter(properties, store, objectMapper);
    }

    @Test
    @DisplayName("Owner/standalone mutating endpoint executes once and replays identical 2xx response")
    void mutatingEndpointExecutesOnceAndReplaysResponse() throws Exception {
        AtomicInteger controllerInvocations = new AtomicInteger(0);
        FilterChain chain = (req, res) -> {
            byte[] reqBody = req.getInputStream().readAllBytes();
            assertThat(new String(reqBody, StandardCharsets.UTF_8)).isEqualTo("{\"content\":\"durable fact\"}");
            int n = controllerInvocations.incrementAndGet();
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(201);
            httpRes.setContentType("application/json");
            httpRes.setHeader("X-Memory-Id", "mem-" + n);
            httpRes.getOutputStream().write(("{\"id\":\"mem-" + n + "\"}").getBytes(StandardCharsets.UTF_8));
        };

        byte[] payload = "{\"content\":\"durable fact\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("X-Spector-Namespace", "ns-owner-1");
        req1.addHeader("Idempotency-Key", "owner-idem-1");
        req1.setContentType("application/json");
        req1.setContent(payload);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        filter.doFilter(req1, resp1, chain);
        assertThat(resp1.getStatus()).isEqualTo(201);
        assertThat(resp1.getContentAsString()).isEqualTo("{\"id\":\"mem-1\"}");
        assertThat(resp1.getHeader("X-Memory-Id")).isEqualTo("mem-1");

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req2.setRequestURI("/api/v1/memory/remember");
        req2.addHeader("X-Spector-Namespace", "ns-owner-1");
        req2.addHeader("Idempotency-Key", "owner-idem-1");
        req2.setContentType("application/json");
        req2.setContent(payload);
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilter(req2, resp2, chain);
        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":\"mem-1\"}");
        assertThat(resp2.getHeader("X-Memory-Id")).isEqualTo("mem-1");
        assertThat(resp2.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(controllerInvocations.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Committed idempotency record survives in-memory store restart via JDBC persistence backend")
    void committedRecordSurvivesStoreRestart() throws Exception {
        AtomicInteger controllerInvocations = new AtomicInteger(0);
        FilterChain chain = (req, res) -> {
            int n = controllerInvocations.incrementAndGet();
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(200);
            httpRes.setContentType("application/json");
            httpRes.getOutputStream().write(("{\"stored\":" + n + "}").getBytes(StandardCharsets.UTF_8));
        };

        byte[] payload = "{\"content\":\"persisted\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/store");
        req1.setRequestURI("/api/v1/memory/store");
        req1.addHeader("X-Spector-Namespace", "ns-durable");
        req1.addHeader("Idempotency-Key", "idem-durable-key");
        req1.setContent(payload);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        filter.doFilter(req1, resp1, chain);
        assertThat(resp1.getStatus()).isEqualTo(200);
        assertThat(resp1.getContentAsString()).isEqualTo("{\"stored\":1}");

        // Simulate process restart by constructing a brand-new InMemoryIdempotencyStore and IdempotencyFilter
        // backed by the same JDBC persistence backend.
        InMemoryIdempotencyStore restartedStore =
                new InMemoryIdempotencyStore(Duration.ofMinutes(10), 100, Clock.systemUTC(), persistenceBackend);
        IdempotencyFilter restartedFilter = new IdempotencyFilter(properties, restartedStore, objectMapper);

        MockHttpServletRequest reqAfterRestart = new MockHttpServletRequest("POST", "/api/v1/memory/store");
        reqAfterRestart.setRequestURI("/api/v1/memory/store");
        reqAfterRestart.addHeader("X-Spector-Namespace", "ns-durable");
        reqAfterRestart.addHeader("Idempotency-Key", "idem-durable-key");
        reqAfterRestart.setContent(payload);
        MockHttpServletResponse respAfterRestart = new MockHttpServletResponse();

        restartedFilter.doFilter(reqAfterRestart, respAfterRestart, chain);
        assertThat(respAfterRestart.getStatus()).isEqualTo(200);
        assertThat(respAfterRestart.getContentAsString()).isEqualTo("{\"stored\":1}");
        assertThat(respAfterRestart.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(controllerInvocations.get()).isEqualTo(1);

        // Reusing the same key with a different payload after restart still returns 422
        MockHttpServletRequest mismatchAfterRestart = new MockHttpServletRequest("POST", "/api/v1/memory/store");
        mismatchAfterRestart.setRequestURI("/api/v1/memory/store");
        mismatchAfterRestart.addHeader("X-Spector-Namespace", "ns-durable");
        mismatchAfterRestart.addHeader("Idempotency-Key", "idem-durable-key");
        mismatchAfterRestart.setContent("{\"content\":\"different\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse mismatchResp = new MockHttpServletResponse();

        restartedFilter.doFilter(mismatchAfterRestart, mismatchResp, chain);
        assertThat(mismatchResp.getStatus()).isEqualTo(422);
        assertThat(mismatchResp.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");

        // Uncommitted in-flight reservation before crash is not persisted, allowing clean retry after restart
        RequestFingerprint uncommittedFp = RequestFingerprint.of("POST", "/api/v1/memory/store", payload);
        assertThat(store.reserve("default:ns-durable", "idem-inflight-crash", uncommittedFp).status())
                .isEqualTo(IdempotencyReservation.Status.ACQUIRED);
        assertThat(restartedStore.reserve("default:ns-durable", "idem-inflight-crash", uncommittedFp).status())
                .isEqualTo(IdempotencyReservation.Status.ACQUIRED);
    }

    @Test
    @DisplayName("Concurrent in-flight requests on owner filter return 409 Conflict with Retry-After")
    void concurrentOwnerRequestsReturn409() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger(0);

        FilterChain chain = (req, res) -> {
            invocations.incrementAndGet();
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ((HttpServletResponse) res).setStatus(200);
            res.getOutputStream().write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        };

        byte[] payload = "{\"content\":\"concurrent\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("X-Spector-Namespace", "ns-owner-1");
        req1.addHeader("Idempotency-Key", "owner-concurrent-1");
        req1.setContent(payload);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> f = pool.submit(() -> {
                filter.doFilter(req1, resp1, chain);
                return null;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
            req2.setRequestURI("/api/v1/memory/remember");
            req2.addHeader("X-Spector-Namespace", "ns-owner-1");
            req2.addHeader("Idempotency-Key", "owner-concurrent-1");
            req2.setContent(payload);
            MockHttpServletResponse resp2 = new MockHttpServletResponse();

            filter.doFilter(req2, resp2, chain);
            assertThat(resp2.getStatus()).isEqualTo(409);
            assertThat(resp2.getHeader("Retry-After")).isEqualTo("1");

            release.countDown();
            f.get(5, TimeUnit.SECONDS);
        }

        assertThat(invocations.get()).isEqualTo(1);
    }
}
