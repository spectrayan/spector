# ═══════════════════════════════════════════════════════════════════
# Spector — Multi-Stage Self-Contained Dockerfile
# ═══════════════════════════════════════════════════════════════════
# Builds both Angular Cortex dashboard and Spector Synapse Spring Boot
# backend inside Docker. No pre-built host artifacts required.
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Build Angular Cortex Dashboard ───────────────────────────
FROM --platform=$BUILDPLATFORM node:22.22.3-alpine AS builder-cortex

WORKDIR /build
COPY cortex/spector-cortex/package*.json ./
RUN npm ci --prefer-offline --no-audit

COPY cortex/spector-cortex/ ./
RUN npm run build -- --configuration production

# ── Stage 2: Build Java Synapse Backend ───────────────────────────────
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS builder-synapse

WORKDIR /build

# Copy Maven reactor modules
COPY pom.xml .
COPY nucleus/ nucleus/
COPY memory/ memory/
COPY synapse/ synapse/
COPY sdks/java/ sdks/java/
COPY bench/ bench/

# Package Synapse executable fat JAR (cache Maven repo to survive transient TLS errors)
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -pl synapse/spector-synapse -am -DskipTests --no-transfer-progress && \
    cp /build/synapse/spector-synapse/target/spector-synapse-*-exec.jar /build/spector-synapse.jar

# ── Stage 3: Runtime (glibc Debian Bookworm base) ─────────────────────
FROM eclipse-temurin:25-jre AS runtime

LABEL maintainer="Spectrayan <team@spectrayan.com>"
LABEL org.opencontainers.image.title="Spector"
LABEL org.opencontainers.image.description="Spector is a zero-overhead, agent-ready AI memory backbone and search engine with Project Panama FFM and Vector API SIMD acceleration."
LABEL org.opencontainers.image.url="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.source="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.licenses="Apache-2.0 AND BSL-1.1"

# Install Nginx, tini, and network utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    nginx \
    tini \
    curl \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user spector (UID/GID 1000)
RUN (userdel -r ubuntu 2>/dev/null || true) && \
    (groupdel ubuntu 2>/dev/null || true) && \
    (groupadd -g 1000 spector 2>/dev/null || true) && \
    (id -u 1000 >/dev/null 2>&1 || useradd -u 1000 -g 1000 -m -s /bin/bash spector)

WORKDIR /app

# Copy backend fat JAR from Stage 2
COPY --from=builder-synapse /build/spector-synapse.jar /app/spector-synapse.jar

# Copy frontend distribution from Stage 1
COPY --from=builder-cortex /build/dist/spector-cortex/browser/ /usr/share/nginx/html/

# Copy configuration and scripts
COPY deploy/docker/spector-docker.yml /app/spector.yml
COPY deploy/docker/nginx.conf /etc/nginx/spector-nginx.conf
COPY deploy/docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

# Environment settings
ENV SPECTOR_DATA_DIR=/data
ENV JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=8m -XX:MaxDirectMemorySize=2g \
--enable-preview --add-modules=jdk.incubator.vector \
--enable-native-access=ALL-UNNAMED -Djava.io.tmpdir=/data/tmp"

# ── Provider Configuration ──
# Supported types: ollama | openai | google | anthropic | mistral | azure | bedrock | onnx
ENV SPECTOR_EMBEDDING_PROVIDER=ollama \
    SPECTOR_EMBEDDING_MODEL=nomic-embed-text \
    SPECTOR_EMBEDDING_BASE_URL=http://host.docker.internal:11434 \
    SPECTOR_EMBEDDING_API_KEY="" \
    SPECTOR_EMBEDDING_DIMS=768 \
    SPECTOR_EMBEDDING_TIMEOUT=120s \
    SPECTOR_GENERATION_PROVIDER=ollama \
    SPECTOR_GENERATION_MODEL=llama3.2 \
    SPECTOR_GENERATION_BASE_URL=http://host.docker.internal:11434 \
    SPECTOR_GENERATION_API_KEY=""

# ── Memory Configuration ──
ENV SPECTOR_MEMORY_CAPACITY=10000 \
    SPECTOR_ENTITY_EXTRACTION_MODE=NONE \
    SPECTOR_TAG_EXTRACTOR=content \
    SPECTOR_TEXT_SEARCH_MODE=HYBRID \
    SPECTOR_LLM_TEMPERATURE=0.3 \
    SPECTOR_LLM_MAX_TOKENS=1024

# Prepare filesystem permissions for non-root execution (UID 1000)
RUN mkdir -p /data/index /data/memory /data/tmp /tmp/nginx /var/log/nginx /var/lib/nginx \
    && chown -R 1000:1000 /data /app /usr/share/nginx/html /etc/nginx /var/log/nginx /var/lib/nginx /tmp/nginx

USER 1000:1000

EXPOSE 8080 7070

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -sf http://127.0.0.1:7070/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]

