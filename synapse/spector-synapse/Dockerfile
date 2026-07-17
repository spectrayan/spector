# ── Stage 1: Build ──
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build
COPY . .

# Build just the synapse module and its dependencies
RUN ./mvnw clean package -pl spector-synapse -am -DskipTests --no-transfer-progress \
    -Drevision=1.0.0-SNAPSHOT

# ── Stage 2: Runtime ──
FROM eclipse-temurin:25-jre

LABEL maintainer="Spectrayan <team@spectrayan.com>"
LABEL description="Spector Synapse — Cognitive Memory Server"

# Create non-root user
RUN groupadd -r spector && useradd -r -g spector spector

WORKDIR /app

# Copy the fat JAR
COPY --from=builder /build/synapse/spector-synapse/target/spector-synapse-*.jar app.jar

# Data directory for H2 database and file storage
RUN mkdir -p /data && chown -R spector:spector /data /app

USER spector

# Environment defaults
ENV SPECTOR_PORT=7070 \
    SPECTOR_DATA_DIR=/data \
    SPECTOR_API_KEY=change-me-in-production \
    SPECTOR_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
    SPECTOR_OLLAMA_MODEL=llama3.2 \
    SPECTOR_OLLAMA_EMBED_MODEL=nomic-embed-text \
    SPECTOR_CORS_ORIGINS=http://localhost:4200 \
    JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xmx512m"

EXPOSE 7070

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:7070/api/v1/system/status || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
