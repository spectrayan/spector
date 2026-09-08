# ═══════════════════════════════════════════════════════════════════
# Spector — Multi-Stage Self-Contained Dockerfile
# ═══════════════════════════════════════════════════════════════════
# Builds both Angular Cortex dashboard and Spector Synapse Spring Boot
# backend inside Docker. No pre-built host artifacts required.
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Build Angular Cortex Dashboard ───────────────────────────
FROM node:22-alpine AS builder-cortex

WORKDIR /build
COPY cortex/spector-cortex/package*.json ./
RUN npm ci --prefer-offline --no-audit

COPY cortex/spector-cortex/ ./
RUN npm run build -- --configuration production

# ── Stage 2: Build Java Synapse Backend ───────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder-synapse

WORKDIR /build

# Copy root and all module POMs to leverage Docker layer caching
COPY pom.xml .
COPY nucleus/pom.xml nucleus/
COPY nucleus/spector-core/pom.xml nucleus/spector-core/
COPY nucleus/spector-events/pom.xml nucleus/spector-events/
COPY nucleus/spector-index/pom.xml nucleus/spector-index/
COPY nucleus/spector-metrics/pom.xml nucleus/spector-metrics/
COPY nucleus/spector-simd/pom.xml nucleus/spector-simd/
COPY nucleus/spector-config/pom.xml nucleus/spector-config/
COPY nucleus/spector-benchmark/pom.xml nucleus/spector-benchmark/
COPY memory/pom.xml memory/
COPY memory/spector-memory/pom.xml memory/spector-memory/
COPY memory/spector-providers/pom.xml memory/spector-providers/
COPY cortex/pom.xml cortex/
COPY synapse/pom.xml synapse/
COPY synapse/spector-spring/pom.xml synapse/spector-spring/
COPY synapse/spector-synapse/pom.xml synapse/spector-synapse/
COPY synapse/spector-cli/pom.xml synapse/spector-cli/
COPY synapse/spector-mcp/pom.xml synapse/spector-mcp/
COPY synapse/spector-connectors/pom.xml synapse/spector-connectors/
COPY sdks/pom.xml sdks/
COPY sdks/java/pom.xml sdks/java/
COPY sdks/java/spector-client/pom.xml sdks/java/spector-client/
COPY tools/pom.xml tools/
COPY tools/spector-analyzer/pom.xml tools/spector-analyzer/
COPY plugins/pom.xml plugins/
COPY plugins/odysseus/pom.xml plugins/odysseus/
COPY plugins/odysseus/odysseus-core/pom.xml plugins/odysseus/odysseus-core/
COPY plugins/odysseus/odysseus-spring/pom.xml plugins/odysseus/odysseus-spring/

# Copy sources
COPY nucleus/ nucleus/
COPY memory/ memory/
COPY synapse/ synapse/
COPY sdks/ sdks/
COPY tools/ tools/
COPY plugins/ plugins/

# Package Synapse executable fat JAR
RUN mvn -B package -pl synapse/spector-synapse -am -DskipTests --no-transfer-progress

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
RUN groupadd -g 1000 spector && \
    useradd -u 1000 -g spector -m -s /bin/bash spector

WORKDIR /app

# Copy backend fat JAR from Stage 2
COPY --from=builder-synapse /build/synapse/spector-synapse/target/spector-synapse-*-exec.jar /app/spector-synapse.jar

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

# Prepare filesystem permissions for non-root execution (UID 1000)
RUN mkdir -p /data/index /data/memory /data/tmp /tmp/nginx /var/log/nginx /var/lib/nginx \
    && chown -R 1000:1000 /data /app /usr/share/nginx/html /etc/nginx /var/log/nginx /var/lib/nginx /tmp/nginx

USER 1000:1000

EXPOSE 8080 7070

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -sf http://127.0.0.1:7070/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]

