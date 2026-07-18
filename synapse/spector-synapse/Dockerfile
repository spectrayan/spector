# ── Stage 1: Build ──
FROM eclipse-temurin:25-jdk AS builder

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY . .

# Build just the synapse module and its dependencies
RUN mvn clean package -pl synapse/spector-synapse -am -DskipTests --no-transfer-progress \
    -Drevision=0.1.0-alpha

# ── Stage 2: Runtime ──
FROM eclipse-temurin:25-jre

LABEL maintainer="Spectrayan <team@spectrayan.com>"
LABEL description="Spector Synapse — Cognitive Memory Server"
LABEL org.opencontainers.image.title="Spector"
LABEL org.opencontainers.image.description="Zero-overhead cognitive memory engine with SIMD-accelerated search, built-in MCP server, and optional agent chat"
LABEL org.opencontainers.image.version="0.1.0-alpha"
LABEL org.opencontainers.image.vendor="Spectrayan"
LABEL org.opencontainers.image.url="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.source="https://github.com/spectrayan/spector"
LABEL org.opencontainers.image.licenses="Apache-2.0 AND BSL-1.1"

# Create non-root user
RUN groupadd -r spector && useradd -r -g spector spector

WORKDIR /app

# Copy the fat JAR
COPY --from=builder /build/synapse/spector-synapse/target/spector-synapse-*.jar app.jar

# Data directory for H2 database and file storage
RUN mkdir -p /data && chown -R spector:spector /data /app

USER spector

# ── Environment defaults ──
# Core
ENV SPECTOR_PORT=7070 \
    SPECTOR_DATA_DIR=/data \
    SPECTOR_CORS_ORIGINS=http://localhost:4200 \
    JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xmx512m"

# Ollama (LLM provider)
ENV SPECTOR_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
    SPECTOR_OLLAMA_MODEL=qwen3.5:latest \
    SPECTOR_OLLAMA_EMBED_MODEL=nomic-embed-text

# ── Feature Flags ──
# LLM-dependent features (auto-enabled when Ollama is detected)
ENV SPECTOR_CHAT_ENABLED=false \
    SPECTOR_AGENT_CHAT_ENABLED=false \
    SPECTOR_AGENT_WORKSPACES_ENABLED=false

# Extraction features (ON by default — require LLM at runtime)
ENV SPECTOR_TAG_EXTRACTION_ENABLED=true \
    SPECTOR_ENTITY_EXTRACTION_ENABLED=true \
    SPECTOR_REFLECTION_ENABLED=true

# Experimental features (OFF by default)
ENV SPECTOR_GPU_ENABLED=false \
    SPECTOR_CLUSTER_ENABLED=false \
    SPECTOR_CONNECTORS_ENABLED=false \
    SPECTOR_BENCHMARKS_ENABLED=false

EXPOSE 7070

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:7070/api/v1/system/status || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED --enable-preview -jar app.jar"]
