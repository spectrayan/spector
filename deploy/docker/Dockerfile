# ═══════════════════════════════════════════════════════════════════
# Spector OSS — Single Docker Image (Spring Boot 4 + Nginx)
# ═══════════════════════════════════════════════════════════════════
#
# Packages pre-built artifacts (built on host) into a runtime image:
#   - Spector Synapse (Spring Boot 4 fat JAR — cognitive memory engine)
#   - Nginx serving Angular Cortex dashboard + reverse proxy on :3000
#
# Prerequisites (done by deploy.ps1):
#   1. mvn package -DskipTests   (builds Spring Boot fat JAR)
#   2. npm run build             (builds Angular production bundle)
#
# Build:
#   docker build -f deploy/docker/Dockerfile -t spector .
#
# ═══════════════════════════════════════════════════════════════════

FROM eclipse-temurin:25-jre-alpine

# Install Nginx and tini for proper signal handling
RUN apk add --no-cache nginx tini

# Create non-root user
RUN addgroup -S spector && adduser -S spector -G spector

WORKDIR /app

# Copy pre-built Spring Boot fat JAR (self-contained)
COPY synapse/spector-synapse/target/spector-synapse-*.jar /app/spector-synapse.jar

# Copy YAML config
COPY deploy/docker/spector-docker.yml /app/spector.yml

# Copy frontend (Angular dist — built on host)
COPY cortex/spector-cortex/dist/spector-cortex/browser/ /usr/share/nginx/html/

# Copy Nginx config
RUN rm -f /etc/nginx/http.d/default.conf
COPY deploy/docker/nginx.conf /etc/nginx/http.d/spector.conf

# Copy entrypoint (sed strips Windows CRLF — critical for Windows-built images)
COPY deploy/docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

# Set data root to Docker volume mount point
ENV SPECTOR_DATA_DIR=/data

# JVM tuning — right-sized for Panama + Virtual Threads
ENV JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=8m -XX:MaxDirectMemorySize=2g \
--enable-preview --add-modules=jdk.incubator.vector \
--enable-native-access=ALL-UNNAMED -Djava.io.tmpdir=/data/tmp"

# Create data directories
RUN mkdir -p /data/index /data/memory /data/tmp \
    && chown -R spector:spector /data /app

# Fix Nginx PID directory permissions
RUN mkdir -p /run/nginx

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:7070/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
