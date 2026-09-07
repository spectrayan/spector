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

### 1. Launch Core Memory Engine
Starts the high-performance Spector Synapse daemon (REST, SSE, and MCP HTTP endpoints on port `:7070`):

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
| **Default** | `docker compose up -d` | Core cognitive memory engine (REST, SSE, MCP HTTP) | `7070` |
| **`ui`** | `docker compose --profile ui up -d` | Adds the Angular 22 Cortex 3D Neural Explorer UI reverse-proxied via Nginx | `80`, `7070` |
| **`embeddings`** | `docker compose --profile embeddings up -d` | Adds a bundled local Ollama container with pre-pulled embedding models | `11434`, `7070` |
| **`gpu`** | `docker compose --profile gpu up -d` | Activates NVIDIA CUDA container runtime for hardware-accelerated embeddings | `7070` |

Combine profiles as needed:
```bash
# Launch core engine + Cortex visual dashboard + Ollama embeddings:
docker compose --profile ui --profile embeddings up -d
```

---

## Persistent Storage

Spector persists off-heap Panama memory partitions and Write-Ahead Logs (WAL) in the named volume `spector-data` mounted at `/var/lib/spector`.

To inspect or backup persistent memory files:
```bash
docker run --rm -v spector_spector-data:/data alpine ls -la /data
```

---

## Multi-Stage Hermetic Dockerfile

The official [`deploy/docker/Dockerfile`](https://github.com/spectrayan/spector/blob/main/deploy/docker/Dockerfile) compiles both frontend and backend entirely within containers, requiring no local Java or Node toolchains:

1. **Stage 1 (`builder-cortex`)**: `node:22-alpine` compiles the Angular Cortex dashboard into static distribution files.
2. **Stage 2 (`builder-synapse`)**: `maven:3.9-eclipse-temurin-25` compiles the full reactor and produces the Synapse fat JAR.
3. **Stage 3 (`runtime`)**: `eclipse-temurin:25-jre-alpine` combines the JRE 25 runtime with Nginx, tini process supervisor, and a non-root `spector` user.

### Building Locally

```bash
docker build -t spector:local -f deploy/docker/Dockerfile .
```

Run the locally built image:
```bash
docker run -d \
  --name spector \
  -p 7070:7070 \
  -p 80:80 \
  -v spector-data:/var/lib/spector \
  spector:local
```

---

## Environment Variables

Configure container behavior via environment variables in `.env` or Compose:

| Variable | Default | Description |
|:---|:---|:---|
| `SPECTOR_PORT` | `7070` | Synapse HTTP REST & SSE listen port |
| `SPECTOR_STORAGE_PATH` | `/var/lib/spector` | Persistence directory for WAL and memory kernels |
| `SPECTOR_EMBEDDER_PROVIDER` | `onnx` | Embedder provider (`onnx`, `ollama`, `openai`, `gemini`) |
| `JAVA_OPTS` | `-Xms512m -Xmx2g` | JVM memory parameters |
