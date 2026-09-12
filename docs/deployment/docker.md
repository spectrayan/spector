---
title: Docker & Compose Deployment
description: "Deploy Spector Cognitive Memory using multi-stage Docker containers and Docker Compose with profiles."
---

# 🐳 Docker & Docker Compose Deployment

> **Containerized deployment for Spector Synapse and the Cortex 3D neural explorer.**

Spector provides official multi-architecture container images (`linux/amd64`, `linux/arm64`) published to the GitHub Container Registry (`ghcr.io/spectrayan/spector`).

---

## Quick Start with Docker Compose

Spector includes an out-of-the-box [`docker-compose.yml`](https://github.com/spectrayan/spector/blob/main/docker-compose.yml) with automatic named volume provisioning.

> [!NOTE]
> **Architecture Split: Daemon vs. CLI Runner**
> The Docker container runs the full **Spector Synapse server daemon** (`spector-synapse.jar`), exposing the REST API, SSE event streams, HTTP MCP endpoints, and the embedded Cortex Neural Dashboard.
> In contrast, the standalone CLI installer and `npx @spectrayan/spector` launcher run the lightweight **Spector CLI runner** (`spector.jar`), designed for direct MCP stdio communication and ad-hoc administration commands (`spector doctor`, `spector inspect`).

### 1. Launch Core Memory Engine & Dashboard
Starts the Spector Synapse daemon (REST, SSE, and MCP HTTP endpoints on port `:7070`) and Cortex Neural Dashboard (on port `:7700`):

```bash
docker compose up -d
```

Verify the service is healthy:
```bash
curl http://localhost:7070/actuator/health
```

### 2. Available Compose Profiles

Spector uses modular Docker Compose profiles for optional subsystems:

| Profile | Command | Description | Ports |
|:---|:---|:---|:---|
| **Default** | `docker compose up -d` | Core engine + Cortex Neural Dashboard | `7070`, `7700:8080` |
| **`embeddings`** | `docker compose --profile embeddings up -d` | Adds a bundled local Ollama container (`OLLAMA_HOST=http://ollama:11434`) | `11434`, `7070`, `7700` |
| **`gpu`** | `docker compose --profile gpu up -d` | Enables GPU passthrough to Ollama for hardware-accelerated embeddings | `11434`, `7070`, `7700` |

> [!TIP]
> When using the `embeddings` profile, initialize the embedding model inside the Ollama container:
> ```bash
> docker exec -it spector-ollama ollama pull nomic-embed-text
> ```

Combine profiles as needed:
```bash
# Launch engine + dashboard + GPU Ollama embeddings:
docker compose --profile embeddings --profile gpu up -d
```

---

## Persistent Storage

Spector persists off-heap memory partitions, Write-Ahead Logs (WAL), and indexes in the named volume `spector-data` mounted at `/data`.

To inspect or backup persistent memory files:
```bash
docker run --rm -v spector_spector-data:/data alpine ls -la /data
```

---

## Multi-Stage Hermetic Dockerfile

The official [`deploy/docker/Dockerfile`](https://github.com/spectrayan/spector/blob/main/deploy/docker/Dockerfile) compiles both frontend and backend entirely within containers, requiring no local Java or Node toolchains:

1. **Stage 1 (`builder-cortex`)**: `node:22-alpine` compiles the Angular Cortex dashboard into static distribution files.
2. **Stage 2 (`builder-synapse`)**: `maven:3.9-eclipse-temurin-25` compiles the full reactor and produces the Synapse fat JAR.
3. **Stage 3 (`runtime`)**: `eclipse-temurin:25-jre` (glibc bookworm) combines the JRE 25 runtime with Nginx, tini process supervisor, and a non-root `spector` user (UID 1000).

### Building Locally

```bash
docker build -t spector:local -f deploy/docker/Dockerfile .
```

Run the locally built image:
```bash
docker run -d \
  --name spector \
  -p 7070:7070 \
  -p 7700:8080 \
  -v spector-data:/data \
  spector:local
```

---

## Environment Variables

Configure container behavior via environment variables in `.env` or Compose:

| Variable | Default | Description |
|:---|:---|:---|
| `SPECTOR_PORT` | `7070` | Synapse HTTP REST & SSE listen port |
| `SPECTOR_DATA_DIR` | `/data` | Persistence directory for memory, index, and WAL storage |
| `SPECTOR_DIMS` | `384` | Embedding vector dimensionality |
| `JAVA_OPTS` | `-Xms512m -Xmx2g` | JVM memory parameters |
