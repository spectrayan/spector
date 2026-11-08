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
package com.spectrayan.spector.memory.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.config.properties.ExtractionProperties;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanExtractorFactory;
import com.spectrayan.spector.provider.extraction.SpanMention;
import com.spectrayan.spector.provider.extraction.SpanModelSpec;

/**
 * Shares one thread-safe GLiNER {@link SpanExtractor} per {@link SpanModelSpec} configuration
 * across namespaces and closes pooled sessions on runtime or process shutdown.
 */
public final class GlinerSessions {

    private static final ReentrantLock POOL_LOCK = new ReentrantLock();
    private static final Map<SpanModelSpec, SharedSession> POOL = new LinkedHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(GlinerSessions::closeAll, "spector-gliner-sessions-shutdown"));
    }

    private GlinerSessions() {
    }

    /**
     * Opens or reuses a shared session when {@code tag-extractor=gliner} or {@code extraction-mode=gliner}
     * and the builder does not already supply that extractor. A missing model fails startup.
     *
     * @param builder memory builder
     */
    public static void prepare(SpectorMemoryBuilder builder) {
        if (builder.spanExtractor() != null) {
            return;
        }
        var memory = builder.properties() != null ? builder.properties().memory() : null;
        boolean tags = memory != null && memory.getTagExtractor() == TagExtractorMode.GLINER
                && builder.tagExtractor() == null;
        String mode = memory != null && memory.getEntity() != null ? memory.getEntity().getExtractionMode() : null;
        boolean entities = mode != null && mode.trim().equalsIgnoreCase("gliner") && builder.entityExtractor() == null;
        if (!tags && !entities) {
            return;
        }
        ExtractionProperties extraction = builder.properties().provider() != null
                ? builder.properties().provider().getExtraction()
                : new ExtractionProperties();
        builder.spanExtractor(open(extraction));
    }

    /**
     * Acquires a lease on a shared {@link SpanExtractor} for the given extraction configuration.
     * Closing the returned lease releases the namespace's reference without closing the underlying
     * model session while other namespaces or the pool still hold it.
     *
     * @param extraction provider extraction settings
     * @return a lease over the shared open session
     */
    public static SpanExtractor open(ExtractionProperties extraction) {
        ExtractionProperties props = extraction != null ? extraction : new ExtractionProperties();
        String type = props.getType() == null ? "" : props.getType().trim().toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.equals("gliner")) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "type",
                    "'" + props.getType() + "' is not gliner");
        }
        if (props.getRelationModelPath() != null && !props.getRelationModelPath().isBlank()) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "relation-model-path",
                    "a separate relation model is not loaded; relations are phrase links between spans");
        }
        List<String> labels = List.copyOf(labels(props.getLabels()));
        SpanModelSpec spec = new SpanModelSpec(
                path(props.getModelPath()),
                path(props.getTokenizerPath()),
                path(props.getConfigPath()),
                props.getEntityThreshold(),
                Math.max(1, props.getIntraOpThreads()),
                props.getMaxWidth(),
                props.getMaxTokens(),
                labels);
        POOL_LOCK.lock();
        try {
            SharedSession existing = POOL.get(spec);
            if (existing != null && !existing.closed) {
                existing.activeLeases++;
                existing.closeWhenIdle = false;
                return new SharedSpanExtractor(existing);
            }
            SpanExtractorFactory factory = null;
            for (SpanExtractorFactory candidate : ServiceLoader.load(SpanExtractorFactory.class)) {
                if ("gliner".equals(candidate.name())) {
                    factory = candidate;
                    break;
                }
            }
            if (factory == null) {
                throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "gliner",
                        "no span extractor is on the classpath");
            }
            SpanExtractor created = factory.open(spec);
            SharedSession session = new SharedSession(spec, created);
            session.activeLeases = 1;
            POOL.put(spec, session);
            return new SharedSpanExtractor(session);
        } finally {
            POOL_LOCK.unlock();
        }
    }

    /**
     * Closes pooled GLiNER sessions that have no active namespace leases, and marks any in-use
     * sessions to close once their last lease is released.
     */
    public static void closeIdle() {
        List<SpanExtractor> toClose = new ArrayList<>();
        POOL_LOCK.lock();
        try {
            Iterator<Map.Entry<SpanModelSpec, SharedSession>> it = POOL.entrySet().iterator();
            while (it.hasNext()) {
                SharedSession session = it.next().getValue();
                if (session.activeLeases <= 0) {
                    session.closed = true;
                    it.remove();
                    toClose.add(session.delegate);
                } else {
                    session.closeWhenIdle = true;
                }
            }
        } finally {
            POOL_LOCK.unlock();
        }
        for (SpanExtractor extractor : toClose) {
            closeQuietly(extractor);
        }
    }

    /**
     * Closes all pooled GLiNER sessions unconditionally at process shutdown.
     */
    public static void closeAll() {
        List<SpanExtractor> toClose = new ArrayList<>();
        POOL_LOCK.lock();
        try {
            for (SharedSession session : POOL.values()) {
                session.closed = true;
                session.activeLeases = 0;
                toClose.add(session.delegate);
            }
            POOL.clear();
        } finally {
            POOL_LOCK.unlock();
        }
        for (SpanExtractor extractor : toClose) {
            closeQuietly(extractor);
        }
    }

    /**
     * @return number of distinct pooled GLiNER configurations currently cached
     */
    public static int pooledCount() {
        POOL_LOCK.lock();
        try {
            return POOL.size();
        } finally {
            POOL_LOCK.unlock();
        }
    }

    private static void release(SharedSession session) {
        SpanExtractor toClose = null;
        POOL_LOCK.lock();
        try {
            if (session.closed) {
                return;
            }
            session.activeLeases = Math.max(0, session.activeLeases - 1);
            if (session.activeLeases == 0 && session.closeWhenIdle) {
                session.closed = true;
                POOL.remove(session.spec, session);
                toClose = session.delegate;
            }
        } finally {
            POOL_LOCK.unlock();
        }
        if (toClose != null) {
            closeQuietly(toClose);
        }
    }

    private static void closeQuietly(SpanExtractor extractor) {
        try {
            extractor.close();
        } catch (Exception ignored) {
        }
    }

    private static final class SharedSession {
        private final SpanModelSpec spec;
        private final SpanExtractor delegate;
        private int activeLeases;
        private boolean closeWhenIdle;
        private boolean closed;

        SharedSession(SpanModelSpec spec, SpanExtractor delegate) {
            this.spec = spec;
            this.delegate = delegate;
        }
    }

    private static final class SharedSpanExtractor implements SpanExtractor {
        private final SharedSession session;
        private final SpanExtractor delegate;
        private final AtomicBoolean leaseClosed = new AtomicBoolean(false);

        SharedSpanExtractor(SharedSession session) {
            this.session = session;
            this.delegate = session.delegate;
        }

        @Override
        public List<SpanMention> extract(String text, List<String> labels) {
            if (leaseClosed.get()) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_EXTRACTION_FAILED, "span", "session is closed");
            }
            return delegate.extract(text, labels);
        }

        @Override
        public void close() {
            if (leaseClosed.compareAndSet(false, true)) {
                release(session);
            }
        }
    }

    /**
     * @param raw comma-separated labels
     * @return trimmed labels, in order
     */
    public static List<String> labels(String raw) {
        List<String> labels = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return labels;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                labels.add(trimmed);
            }
        }
        return labels;
    }

    private static Path path(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim()).toAbsolutePath().normalize();
    }
}
