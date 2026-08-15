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
package com.spectrayan.spector.connector.core;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.connector.model.ConnectorType;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.ingestion.IngestionTarget;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RouteLifecycleService} with descriptor-driven validation.
 */
@ExtendWith(MockitoExtension.class)
class RouteLifecycleServiceTest {

    @Mock
    private IngestionTarget target;
    @Mock
    private EmbeddingProvider embeddingProvider;

    private CamelConnectorEngine engine;
    private RouteLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        var configProvider = new InMemoryRouteConfigProvider();
        var executionLogger = new InMemoryExecutionLogger();
        var sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);
        var templateRegistry = new TemplateRegistry(null);
        engine = new CamelConnectorEngine(sink, configProvider, templateRegistry);

        var credentialProvider = CredentialProvider.fromEnvironment();
        lifecycleService = new RouteLifecycleService(engine, templateRegistry, credentialProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isStarted()) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Activate and deactivate a direct route")
    void activateAndDeactivate() throws Exception {
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config = RouteConfig.builder("lc-route", "Lifecycle Route", "direct")
                .connectorType(ConnectorType.DIRECT)
                .build();
        lifecycleService.activateRoute(config);

        assertThat(engine.activeRouteIds()).contains("lc-route");

        boolean removed = lifecycleService.deactivateRoute("lc-route");
        assertThat(removed).isTrue();
        assertThat(engine.activeRouteIds()).doesNotContain("lc-route");
    }

    @Test
    @DisplayName("Validation rejects missing required parameters")
    void validationRejectsMissingRequired() throws Exception {
        engine.start();

        // S3 template requires 'bucketName' — omit it
        var config = RouteConfig.builder("bad-s3", "Bad S3", "s3-poll")
                .connectorType(ConnectorType.S3)
                .build();

        assertThatExceptionOfType(SpectorValidationException.class)
                .isThrownBy(() -> lifecycleService.activateRoute(config))
                .withMessageContaining("validation failed");
    }

    @Test
    @DisplayName("Validation rejects missing credential ref when required")
    void validationRejectsMissingCredential() throws Exception {
        engine.start();

        // S3 requires credential but none set
        var config = RouteConfig.builder("no-cred-s3", "No Cred S3", "s3-poll")
                .connectorType(ConnectorType.S3)
                .properties(Map.of("bucketName", "my-bucket"))
                .build();

        assertThatExceptionOfType(SpectorValidationException.class)
                .isThrownBy(() -> lifecycleService.activateRoute(config))
                .withMessageContaining("credential");
    }

    @Test
    @DisplayName("Validation rejects invalid URL type")
    void validationRejectsInvalidUrl() throws Exception {
        engine.start();

        var config = RouteConfig.builder("bad-rest", "Bad REST", "rest-api-poll")
                .connectorType(ConnectorType.REST_API)
                .properties(Map.of("url", "ftp://not-http"))
                .build();

        assertThatExceptionOfType(SpectorValidationException.class)
                .isThrownBy(() -> lifecycleService.activateRoute(config))
                .withMessageContaining("http");
    }

    @Test
    @DisplayName("Validation rejects non-numeric value for number type")
    void validationRejectsNonNumeric() throws Exception {
        engine.start();

        var config = RouteConfig.builder("bad-poll", "Bad Poll", "rest-api-poll")
                .connectorType(ConnectorType.REST_API)
                .properties(Map.of("url", "https://api.test.com", "pollIntervalMs", "not-a-number"))
                .build();

        assertThatExceptionOfType(SpectorValidationException.class)
                .isThrownBy(() -> lifecycleService.activateRoute(config))
                .withMessageContaining("number");
    }

    @Test
    @DisplayName("Reload deactivates then reactivates")
    void reloadRoute() throws Exception {
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config = RouteConfig.builder("reload-route", "Reload Test", "direct").build();
        lifecycleService.activateRoute(config);
        assertThat(engine.activeRouteIds()).contains("reload-route");

        lifecycleService.reloadRoute(config);
        assertThat(engine.activeRouteIds()).contains("reload-route");
    }

    @Test
    @DisplayName("testConnection returns valid for correct config")
    void testConnectionValid() {
        var config = RouteConfig.builder("file-test", "File Test", "file-watch")
                .connectorType(ConnectorType.FILE_WATCH)
                .properties(Map.of("path", "."))
                .build();

        var result = lifecycleService.testConnection(config);
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("testConnection returns failure for invalid config")
    void testConnectionInvalid() {
        var config = RouteConfig.builder("rest-bad", "REST Bad", "rest-api-poll")
                .connectorType(ConnectorType.REST_API)
                .build();

        var result = lifecycleService.testConnection(config);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Validation failed");
    }

    @Test
    @DisplayName("Route with no specific validation (direct) passes")
    void directRoutePassesValidation() throws Exception {
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config = RouteConfig.builder("no-type", "No Type", "direct").build();
        lifecycleService.activateRoute(config);
        assertThat(engine.activeRouteIds()).contains("no-type");
    }
}
