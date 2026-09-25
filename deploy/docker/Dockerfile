# ═══════════════════════════════════════════════════════════════════
# Spector — Multi-Stage Self-Contained Dockerfile
# ═══════════════════════════════════════════════════════════════════
# Builds both Angular Cortex dashboard and Spector Synapse Spring Boot
# backend inside Docker. No pre-built host artifacts required.
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Build Angular Cortex Dashboard ───────────────────────────
FROM --platform=$BUILDPLATFORM node:22.22.3-alpine@sha256:e58326d0d441090181ac150dc2078d3e2cf6a0d42e809aebba3ef5880935ffdd AS builder-cortex

WORKDIR /build
COPY cortex/spector-cortex/package*.json ./
RUN npm ci --prefer-offline --no-audit

COPY cortex/spector-cortex/ ./
RUN npm run build -- --configuration production

# ── Stage 2: Build Java Synapse Backend ───────────────────────────────
FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-26@sha256:c30718f442eb39c55b2d45440bb849d8755ad8d9d6ef67aadefcce150267116f AS builder-synapse

WORKDIR /build

# Copy Maven reactor modules
COPY pom.xml .
COPY nucleus/ nucleus/
COPY memory/ memory/
COPY cluster/ cluster/
COPY synapse/ synapse/
COPY sdks/java/ sdks/java/
COPY bench/ bench/

# Package Synapse executable fat JAR (cache Maven repo to survive transient TLS errors)
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -pl synapse/spector-synapse -am -DskipTests --no-transfer-progress && \
    cp /build/synapse/spector-synapse/target/spector-synapse-*-exec.jar /build/spector-synapse.jar

# ═══════════════════════════════════════════════════════════════════
# Gateway Target — Dedicated Reactive Cell Ingress Router (ADR-0081)
# ═══════════════════════════════════════════════════════════════════
# Lean image: no Nginx, no Cortex dashboard, no /data volume,
# no Panama incubator flags. Pure WebFlux + Reactor Netty forwarder.
# ═══════════════════════════════════════════════════════════════════

# ── Stage 3: Build Gateway JAR ────────────────────────────────────
FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-26@sha256:c30718f442eb39c55b2d45440bb849d8755ad8d9d6ef67aadefcce150267116f AS builder-gateway

WORKDIR /build

# Copy Maven reactor modules (full reactor for module resolution; -pl -am limits compilation)
COPY pom.xml .
COPY nucleus/ nucleus/
COPY memory/ memory/
COPY cluster/ cluster/
COPY synapse/ synapse/
COPY sdks/java/ sdks/java/
COPY bench/ bench/

# Package Gateway executable fat JAR
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -pl synapse/spector-gateway -am -DskipTests --no-transfer-progress && \
    cp /build/synapse/spector-gateway/target/spector-gateway-*-exec.jar /build/spector-gateway.jar

# ── Stage 4: Gateway Runtime (lean JRE, no data-plane deps) ──────
FROM eclipse-temurin:25-jre@sha256:bb036ed6cfdc57e3da7c22634d15f1b840d2caf76183861c80e81ca4b5104abb AS gateway

LABEL maintainer="Spectrayan <team@spectrayan.com>"
LABEL org.opencontainers.image.title="Spector Gateway"
LABEL org.opencontainers.image.description="Dedicated Reactive Cell Ingress Router — WebFlux + Reactor Netty streaming forwarder with waterfall routing cache and bounded retries (ADR-0081)."
LABEL org.opencontainers.image.url="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.source="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.licenses="Apache-2.0 AND BSL-1.1"

# Install tini and curl (health checks), no Nginx needed
RUN apt-get update && apt-get install -y --no-install-recommends \
    tini \
    curl \
    ca-certificates \
    && apt-get --only-upgrade install -y perl-base gpgv \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user spector (UID/GID 1000)
RUN (userdel -r ubuntu 2>/dev/null || true) && \
    (groupdel ubuntu 2>/dev/null || true) && \
    (groupadd -g 1000 spector 2>/dev/null || true) && \
    (id -u 1000 >/dev/null 2>&1 || useradd -u 1000 -g 1000 -m -s /bin/bash spector) && \
    rm -f /usr/bin/pebble

WORKDIR /app
RUN chown -R 1000:1000 /app

# Copy gateway fat JAR only — no Cortex frontend, no /data volume
COPY --chown=1000:1000 --from=builder-gateway /build/spector-gateway.jar /app/spector-gateway.jar

# Gateway JVM: lightweight heap, no Panama/incubator, no native-access
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Cell identity (overridden at runtime by orchestrator)
ENV SPECTOR_CELL_ROLE=gateway \
    SPECTOR_CELL_ID=cell-1

USER 1000:1000

EXPOSE 7070

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
    CMD curl -sf http://127.0.0.1:7070/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "java"]
CMD ["-Xms64m", "-Xmx256m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-jar", "/app/spector-gateway.jar"]

# ═══════════════════════════════════════════════════════════════════
# Primary Runtime Target (Default) — All-in-One Spector Node
# ═══════════════════════════════════════════════════════════════════
# Must remain the FINAL stage so un-targeted `docker build` commands
# default to the full Spector runtime with Nginx, Cortex, and Synapse.
# ═══════════════════════════════════════════════════════════════════

# ── Stage 5: Runtime (glibc Debian Bookworm base) ─────────────────────
FROM eclipse-temurin:25-jre@sha256:bb036ed6cfdc57e3da7c22634d15f1b840d2caf76183861c80e81ca4b5104abb AS runtime

LABEL maintainer="Spectrayan <team@spectrayan.com>"
LABEL org.opencontainers.image.title="Spector"
LABEL org.opencontainers.image.description="Spector is a zero-overhead, agent-ready AI memory backbone and search engine with Project Panama FFM and Vector API SIMD acceleration."
LABEL org.opencontainers.image.url="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.source="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.licenses="Apache-2.0 AND BSL-1.1"

# Install Nginx, tini, and network utilities, and upgrade packages with known vulnerabilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    nginx \
    tini \
    curl \
    wget \
    ca-certificates \
    && apt-get --only-upgrade install -y perl-base gpgv \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user spector (UID/GID 1000) and purge unused pebble binary
RUN (userdel -r ubuntu 2>/dev/null || true) && \
    (groupdel ubuntu 2>/dev/null || true) && \
    (groupadd -g 1000 spector 2>/dev/null || true) && \
    (id -u 1000 >/dev/null 2>&1 || useradd -u 1000 -g 1000 -m -s /bin/bash spector) && \
    rm -f /usr/bin/pebble

WORKDIR /app

# Prepare filesystem directories for non-root execution (UID 1000)
RUN mkdir -p /data/memory /data/identity /data/db /data/tmp /tmp/nginx /var/log/nginx /var/lib/nginx /app \
    && chown -R 1000:1000 /data /app /etc/nginx /var/log/nginx /var/lib/nginx /tmp/nginx

# Copy backend fat JAR from Stage 2 directly with non-root ownership
COPY --chown=1000:1000 --from=builder-synapse /build/spector-synapse.jar /app/spector-synapse.jar

# Copy frontend distribution from Stage 1 directly with non-root ownership
COPY --chown=1000:1000 --from=builder-cortex /build/dist/spector-cortex/browser/ /usr/share/nginx/html/

# Copy configuration and scripts
COPY --chown=1000:1000 deploy/docker/spector-docker.yml /app/spector.yml
COPY --chown=1000:1000 deploy/docker/nginx.conf /etc/nginx/spector-nginx.conf
COPY --chown=1000:1000 deploy/docker/entrypoint.sh /app/entrypoint.sh
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

USER 1000:1000

EXPOSE 8080 7070 9090

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -sf http://127.0.0.1:7070/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
