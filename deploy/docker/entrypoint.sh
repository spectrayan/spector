#!/bin/sh
# ═══════════════════════════════════════════════════════════════
# Spector — Container Entrypoint
# Starts Nginx (background) + SpectorNode (foreground)
# ═══════════════════════════════════════════════════════════════

set -e

# Start Nginx in background (serves dashboard + proxies API/MCP)
echo "[Spector] Starting Nginx..."
nginx

# Trap signals for graceful shutdown
cleanup() {
    echo "[Spector] Shutting down..."
    nginx -s quit 2>/dev/null || true
    kill "$JAVA_PID" 2>/dev/null || true
    wait "$JAVA_PID" 2>/dev/null || true
    echo "[Spector] Shutdown complete"
}
trap cleanup TERM INT

# Start SpectorNode (foreground)
echo "[Spector] Starting SpectorNode..."
java \
    --add-modules jdk.incubator.vector \
    --enable-preview \
    -cp "/app/spector-node.jar:/app/lib/*" \
    -Dspector.config=/app/spector.yml \
    com.spectrayan.spector.node.SpectorNode &

JAVA_PID=$!

# Wait for Java process
wait "$JAVA_PID"
