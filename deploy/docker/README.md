# Spector Docker Deployment

> **Production container images for Spector Cognitive Memory, Synapse backend, and Cortex Neural UI.**

This directory contains containerization definitions, reverse proxy configurations, and deployment scripts for Spector.

---

## Container Images & Build Strategies

Spector provides two Docker build pathways:

| Dockerfile | Build Strategy | Use Case | Included Components |
|:---|:---|:---|:---|
| [`Dockerfile`](Dockerfile) | **Multi-stage hermetic build** | CI/CD pipelines, official releases, clean environments | Angular Cortex UI (Node 22), Java Synapse backend (Temurin 25 Maven), Nginx reverse proxy, Tini supervisor |
| [`Dockerfile.synapse-local`](Dockerfile.synapse-local) | **Host-assisted local build** | Fast local development & testing | Uses host pre-built fat JAR (`synapse/spector-synapse/target/*-exec.jar`) & pre-built Cortex bundle (`cortex/spector-cortex/dist/spector-cortex/browser/`), Nginx, Tini |

---

## Network Ports & Port Architecture

Per ADR-0034 (§10), Spector separates client ingress from intra-cell replication:

| Port | Protocol | Purpose | Access URL |
|:---|:---|:---|:---|
| **`7700`** (or internal `8080`) | HTTP | **Cortex Neural Dashboard** (Angular SPA served by Nginx) | `http://localhost:7700` |
| **`7070`** | HTTP / SSE | **Spector Synapse Backend** (Spring Boot REST API, Actuator health, MCP SSE) | `http://localhost:7070/actuator/health` |
| **`9090`** | TCP / mTLS 1.3 | **Dedicated Replication Engine** (off-heap Panama vector segments and WAL streams) | *Internal node-to-node only* |

> [!NOTE]
> Inside the full container, Nginx listens on port `8080`, serving the Cortex UI static files and reverse-proxying `/api`, `/sse`, and `/mcp` to Synapse on port `7070`. The container exposes both `8080` (mapped externally to `7700`) and `7070`.

---

## Quick Start

### 1. Build and Run via Multi-Stage Build

```bash
# Build complete image from source (no local toolchains required)
docker build -t ghcr.io/spectrayan/spector:latest -f deploy/docker/Dockerfile .

# Run container with persistent storage
docker run -d \
  --name spector \
  -p 7700:8080 \
  -p 7070:7070 \
  -v spector-data:/data \
  ghcr.io/spectrayan/spector:latest
```

### 2. Fast Local Development Build (Host-Assisted)

When you have built the Java backend (`mvn package -DskipTests`) and Cortex UI (`cd cortex/spector-cortex && npm run build`):

```bash
# Fast 5-second image assembly
docker build -t spector:latest -f deploy/docker/Dockerfile.synapse-local .

# Run container
docker run -d \
  --name spector \
  -p 7700:8080 \
  -p 7070:7070 \
  -v spector-data:/data \
  spector:latest
```

### 3. Verify Health

```bash
# Public API and Health Check
curl -s http://localhost:7070/actuator/health | jq .

# Cortex Neural Dashboard
open http://localhost:7700
```

---

## File System & Security Context

- **Non-Root Execution**: Runs as user `spector` with UID `1000` and GID `1000`.
- **Persistent Data Directory**: Mounted at `/data` (subdirectories: `/data/index`, `/data/memory`, `/data/tmp`). When mounting host volumes or PVCs, ensure ownership belongs to UID/GID `1000:1000` or use Kubernetes `fsGroup: 1000`.
- **Configuration**: Injected via `/app/spector.yml` or standard environment variables.

---

## Key Environment Variables

| Variable | Default | Description |
|:---|:---|:---|
| `SPECTOR_PORT` | `7070` | Backend HTTP API listening port |
| `SPECTOR_DATA_DIR` | `/data` | Path to persistent storage volume |
| `JAVA_OPTS` | See Dockerfile | JVM flags (requires `--enable-preview --add-modules=jdk.incubator.vector`) |
| `SPECTOR_EMBEDDING_PROVIDER` | `ollama` | Embedding provider (`ollama`, `openai`, `google`, `anthropic`, `onnx`) |
| `SPECTOR_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model identifier |
| `SPECTOR_EMBEDDING_BASE_URL` | `http://host.docker.internal:11434` | Embedding provider API endpoint |
| `SPECTOR_EMBEDDING_DIMS` | `768` | Embedding vector dimensionality |
| `SPECTOR_GENERATION_PROVIDER` | `ollama` | Generation LLM provider |
| `SPECTOR_GENERATION_MODEL` | `llama3.2` | Generation LLM model identifier |
