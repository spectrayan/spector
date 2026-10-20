#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# Spector OSS — Container Entrypoint (Nginx + Backend)
# ═══════════════════════════════════════════════════════════════════
# Starts Nginx (background) + Spector Server (foreground)
#
# Graceful Shutdown:
#   On SIGTERM (docker stop), the JVM receives the signal directly
#   and runs its shutdown hook to persist data.
#   Nginx is stopped first, then we wait for the JVM to finish.
#
# Docker Secrets:
#   Mount secrets at /run/secrets/<name> to inject API keys:
#     - spector_embedding_api_key → SPECTOR_EMBEDDING_API_KEY
#     - spector_generation_api_key → SPECTOR_GENERATION_API_KEY
#     - spector_api_key → SPECTOR_API_KEY
#     - spector_auth_jwt_secret → SPECTOR_AUTH_JWT_SECRET
# ═══════════════════════════════════════════════════════════════════

set -e

# ── Load Docker Secrets ──
# If mounted at /run/secrets/, export as environment variables
for secret_file in \
    spector_embedding_api_key:SPECTOR_EMBEDDING_API_KEY \
    spector_generation_api_key:SPECTOR_GENERATION_API_KEY \
    spector_api_key:SPECTOR_API_KEY \
    spector_auth_jwt_secret:SPECTOR_AUTH_JWT_SECRET \
    spector_master_encryption_key:SPECTOR_MASTER_ENCRYPTION_KEY; do
    file_name="${secret_file%%:*}"
    env_name="${secret_file##*:}"
    secret_path="/run/secrets/${file_name}"
    if [ -f "$secret_path" ]; then
        export "$env_name"="$(cat "$secret_path")"
        echo "[Spector] Loaded secret: ${env_name} from ${secret_path}"
    fi
done

# ── Map short env var aliases to canonical config keys ──
# SpectorConfigSource.resolveWithEnv() auto-maps dot-path keys to env vars:
#   spector.provider.embedding.type → SPECTOR_PROVIDER_EMBEDDING_TYPE
# Users set short names (SPECTOR_EMBEDDING_PROVIDER); map to canonical names.
alias_env() {
    short="$1"; canonical="$2"
    eval "val=\${$short:-}"
    if [ -n "$val" ]; then
        eval "existing=\${$canonical:-}"
        if [ -z "$existing" ]; then
            export "$canonical"="$val"
        fi
    fi
}

alias_env SPECTOR_EMBEDDING_PROVIDER    SPECTOR_PROVIDER_EMBEDDING_TYPE
alias_env SPECTOR_EMBEDDING_MODEL       SPECTOR_PROVIDER_EMBEDDING_MODEL
alias_env SPECTOR_EMBEDDING_BASE_URL    SPECTOR_PROVIDER_EMBEDDING_BASE_URL
alias_env SPECTOR_EMBEDDING_API_KEY     SPECTOR_PROVIDER_EMBEDDING_API_KEY
# One destination only. This used to fan out to SPECTOR_MEMORY_DIMENSIONS as well, because
# dimensionality was configured twice; that property has been removed and is now refused at startup.
alias_env SPECTOR_EMBEDDING_DIMS        SPECTOR_PROVIDER_EMBEDDING_DIMENSIONS
alias_env SPECTOR_EMBEDDING_TIMEOUT     SPECTOR_PROVIDER_EMBEDDING_TIMEOUT
alias_env SPECTOR_GENERATION_PROVIDER   SPECTOR_PROVIDER_GENERATION_TYPE
alias_env SPECTOR_GENERATION_MODEL      SPECTOR_PROVIDER_GENERATION_MODEL
alias_env SPECTOR_GENERATION_BASE_URL   SPECTOR_PROVIDER_GENERATION_BASE_URL
alias_env SPECTOR_GENERATION_API_KEY    SPECTOR_PROVIDER_GENERATION_API_KEY
alias_env SPECTOR_NODE_ROLE             SPECTOR_CELL_ROLE
alias_env SPECTOR_NODE_ID               SPECTOR_CELL_NODE_ID

# Ensure data directories exist (if writable)
mkdir -p /data/memory /data/identity /data/db /data/tmp 2>/dev/null || true

# ── Auto-generate persistent Master Encryption Key if not supplied ──
if [ -z "${SPECTOR_MASTER_ENCRYPTION_KEY:-}" ]; then
    MASTER_KEY_FILE="/data/identity/master_encryption_key"
    if [ ! -w "/data/identity" ]; then
        MASTER_KEY_FILE="/tmp/master_encryption_key"
    fi
    if [ -f "$MASTER_KEY_FILE" ] && [ -s "$MASTER_KEY_FILE" ]; then
        export SPECTOR_MASTER_ENCRYPTION_KEY="$(cat "$MASTER_KEY_FILE")"
        echo "[Spector] Loaded persistent master encryption key from ${MASTER_KEY_FILE}"
    else
        export SPECTOR_MASTER_ENCRYPTION_KEY="$(head -c 32 /dev/urandom | base64 | tr -d '\n\r')"
        printf "%s" "$SPECTOR_MASTER_ENCRYPTION_KEY" > "$MASTER_KEY_FILE" 2>/dev/null || true
        chmod 600 "$MASTER_KEY_FILE" 2>/dev/null || true
        echo "[Spector] Generated and persisted master encryption key at ${MASTER_KEY_FILE}"
    fi
fi

# ── Multi-User Auth Defaults for Docker ──
export SPECTOR_AUTH_ENABLED="${SPECTOR_AUTH_ENABLED:-true}"
if [ "${SPECTOR_AUTH_ENABLED}" = "true" ]; then
    if [ -z "${SPECTOR_AUTH_JWT_SECRET:-}" ]; then
        JWT_SECRET_FILE="/data/identity/jwt_secret"
        if [ ! -w "/data/identity" ]; then
            JWT_SECRET_FILE="/tmp/jwt_secret"
        fi
        if [ -f "$JWT_SECRET_FILE" ] && [ -s "$JWT_SECRET_FILE" ]; then
            export SPECTOR_AUTH_JWT_SECRET="$(cat "$JWT_SECRET_FILE")"
            echo "[Spector] Loaded persistent JWT secret from ${JWT_SECRET_FILE}"
        else
            export SPECTOR_AUTH_JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n\r')"
            printf "%s" "$SPECTOR_AUTH_JWT_SECRET" > "$JWT_SECRET_FILE" 2>/dev/null || true
            chmod 600 "$JWT_SECRET_FILE" 2>/dev/null || true
            echo "[Spector] Generated and persisted JWT secret at ${JWT_SECRET_FILE}"
        fi
    fi
    if [ -z "${SPECTOR_ADMIN_PASSWORD:-}" ]; then
        export SPECTOR_ADMIN_PASSWORD="admin"
        echo "[Spector] Default admin credentials: username=admin (set SPECTOR_ADMIN_PASSWORD to override)"
    fi
fi

# Start Nginx in background (serves dashboard + proxies API on port 8080)
echo "[Spector] Starting Nginx on port 8080..."
nginx -c /etc/nginx/spector-nginx.conf

# Trap signals for graceful shutdown
cleanup() {
    echo "[Spector] Received shutdown signal, draining..."
    # Stop accepting new HTTP connections
    nginx -c /etc/nginx/spector-nginx.conf -s quit 2>/dev/null || true
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

# Start Spector Server in background so trap can catch signals
echo "[Spector] Starting Spector Server..."
echo "[Spector] Dashboard → http://localhost:8080"
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
