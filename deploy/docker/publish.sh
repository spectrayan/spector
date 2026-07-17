#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# Spector — Build & Publish Docker Image to Docker Hub
# ═══════════════════════════════════════════════════════════════════
#
# Builds the Spector Docker image (backend + frontend unified) and
# pushes it to Docker Hub as spectrayan/spector.
#
# Uses the same local build pipeline as deploy.sh:
#   1. Maven builds the Spring Boot fat JAR (on host)
#   2. Angular builds the Cortex dashboard (on host)
#   3. Docker packages both into a single image (Nginx + JRE)
#   4. Push to Docker Hub with version + latest tags
#
# Usage:
#   ./publish.sh                          # Build + push (default)
#   ./publish.sh build                    # Build only
#   ./publish.sh push                     # Push only
#   ./publish.sh login                    # Docker Hub login
#   ./publish.sh info                     # Show image details
#   VERSION=0.1.0-alpha ./publish.sh      # Explicit version
#
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────
DOCKER_HUB_ORG="spectrayan"
IMAGE_NAME="spector"
FULL_IMAGE="${DOCKER_HUB_ORG}/${IMAGE_NAME}"
DOCKERFILE="deploy/docker/Dockerfile"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[Spector]${NC} $*"; }
ok()   { echo -e "${GREEN}[Spector]${NC} $*"; }
warn() { echo -e "${YELLOW}[Spector]${NC} $*"; }
err()  { echo -e "${RED}[Spector]${NC} $*" >&2; }

# ── Navigate to project root ──────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# ── Resolve version ───────────────────────────────────────────────
if [ -z "${VERSION:-}" ]; then
    VERSION=$(grep -oP '<revision>\K[^<]+' pom.xml | head -1)
    if [ -z "$VERSION" ]; then
        err "Could not extract version from pom.xml"
        exit 1
    fi
fi

log "Version: $VERSION"

# ── Functions ──────────────────────────────────────────────────────

build() {
    if ! command -v docker &> /dev/null; then
        err "Docker is not installed or not in PATH"
        exit 1
    fi

    log "═══════════════════════════════════════════════════════"
    log "  Spector v${VERSION} — Docker Hub Image Build"
    log "  Image: ${FULL_IMAGE}:${VERSION}"
    log "═══════════════════════════════════════════════════════"

    if [ ! -f "$DOCKERFILE" ]; then
        err "Dockerfile not found at $DOCKERFILE"
        exit 1
    fi

    # ── Step 1: Build Backend (Maven) ──
    log "Step 1/3: Building backend (Maven — Spring Boot fat JAR)..."
    mvn clean package -DskipTests -B -q -Drevision="$VERSION"
    ok "Backend built successfully."

    # ── Step 2: Build Frontend (Angular) ──
    log "Step 2/3: Building frontend (Angular Cortex)..."
    pushd cortex/spector-cortex >/dev/null
    npm ci --prefer-offline
    npm run build
    popd >/dev/null
    ok "Frontend built successfully."

    # ── Step 3: Docker Image ──
    log "Step 3/3: Building Docker image..."
    local start_time=$SECONDS

    docker build \
        -f "$DOCKERFILE" \
        -t "${FULL_IMAGE}:${VERSION}" \
        -t "${FULL_IMAGE}:latest" \
        --build-arg BUILDKIT_INLINE_CACHE=1 \
        .

    local elapsed=$(( SECONDS - start_time ))
    ok "Image built in ${elapsed}s"
    log "Image tags:"
    docker images "$FULL_IMAGE" --format "  {{.Repository}}:{{.Tag}}  Size: {{.Size}}"
}

push() {
    if ! docker image inspect "${FULL_IMAGE}:${VERSION}" &> /dev/null; then
        err "Image '${FULL_IMAGE}:${VERSION}' not found. Run: $0 build"
        exit 1
    fi

    log "Pushing to Docker Hub..."

    # Push version tag
    log "Pushing ${FULL_IMAGE}:${VERSION}..."
    docker push "${FULL_IMAGE}:${VERSION}"
    ok "Pushed ${FULL_IMAGE}:${VERSION}"

    # Push latest tag
    log "Pushing ${FULL_IMAGE}:latest..."
    docker push "${FULL_IMAGE}:latest"
    ok "Pushed ${FULL_IMAGE}:latest"

    ok "═══════════════════════════════════════════════════════"
    ok "  Published: ${FULL_IMAGE}:${VERSION}"
    ok "  Published: ${FULL_IMAGE}:latest"
    ok ""
    ok "  Run it:"
    ok "    docker run -p 7070:7070 -p 7700:3000 ${FULL_IMAGE}:${VERSION}"
    ok "═══════════════════════════════════════════════════════"
}

login() {
    log "Logging in to Docker Hub..."
    docker login
}

info() {
    log "Docker Hub images for ${FULL_IMAGE}:"
    docker images "$FULL_IMAGE" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
}

# ── Main ───────────────────────────────────────────────────────────

case "${1:-}" in
    build)
        build
        ;;
    push)
        push
        ;;
    login)
        login
        ;;
    info)
        info
        ;;
    ""|publish)
        build
        push
        ;;
    *)
        err "Unknown command: $1"
        echo "Usage: $0 {build|push|login|info|publish}"
        exit 1
        ;;
esac
