#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# Spector OSS — Container Entrypoint (Nginx + Backend)
# ═══════════════════════════════════════════════════════════════════
# Starts Nginx (background) + Spector Synapse Server (foreground)
#
# Graceful Shutdown:
#   On SIGTERM (docker stop), the JVM receives the signal directly
#   and runs its shutdown hook to persist data.
#   Nginx is stopped first, then we wait for the JVM to finish.
# ═══════════════════════════════════════════════════════════════════

set -e

# Ensure data directories exist
mkdir -p /data/index /data/memory /data/tmp

# Start Nginx in background (serves dashboard + proxies API)
echo "[Spector] Starting Nginx..."
nginx

# Trap signals for graceful shutdown
cleanup() {
    echo "[Spector] Received shutdown signal, draining..."
    # Stop accepting new HTTP connections
    nginx -s quit 2>/dev/null || true
    # Forward SIGTERM to Java (triggers JVM shutdown hook)
    if [ -n "$JAVA_PID" ]; then
        kill -TERM "$JAVA_PID" 2>/dev/null || true
        # Wait for Java to finish shutdown
        wait "$JAVA_PID" 2>/dev/null || true
    fi
    echo "[Spector] Shutdown complete"
    exit 0
}
trap cleanup TERM INT

# Start Spector Synapse in background so trap can catch signals
echo "[Spector] Starting Spector Synapse..."
echo "[Spector] Dashboard → http://localhost:3000"
echo "[Spector] API Backend → http://localhost:7070"
java \
    ${JAVA_OPTS:---enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED} \
    -jar /app/spector-synapse.jar \
    --spring.config.additional-location=optional:file:/app/spector.yml &

JAVA_PID=$!

# Wait for Java process (if it exits on its own, we exit too)
# The 'wait' will be interrupted by SIGTERM, which triggers cleanup()
wait "$JAVA_PID"
exit_code=$?
echo "[Spector] Java process exited with code $exit_code"
exit $exit_code
