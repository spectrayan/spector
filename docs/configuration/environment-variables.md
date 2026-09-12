# 🔐 Environment Variables & Secrets Reference

> **Complete guide to configuring Spector via environment variables, container secrets, Java system properties, and low-level JVM runtime flags.** Learn the canonical mapping rules, convenience aliases, and production security patterns.

---

## 🧭 Systematic Canonical Mapping Rule

Spector's configuration loader (`SpectorConfigSource`) automatically translates any configuration property from `spector.yml` into a canonical environment variable using three simple rules:

1. **Prefix**: Every property begins with `SPECTOR_`.
2. **Path Replacement**: Nested YAML dots (`.`) and kebab-case hyphens (`-`) become underscores (`_`).
3. **Casing**: All characters are converted to uppercase.

### Canonical Mapping Examples

| `spector.yml` Dot Path | Canonical Environment Variable | Example Value |
|:---|:---|:---|
| `spector.mode` | `SPECTOR_MODE` | `MEMORY` |
| `spector.memory.dimensions` | `SPECTOR_MEMORY_DIMENSIONS` | `768` |
| `spector.memory.persistence-path` | `SPECTOR_MEMORY_PERSISTENCE_PATH` | `/data/memory` |
| `spector.provider.embedding.type` | `SPECTOR_PROVIDER_EMBEDDING_TYPE` | `openai` |
| `spector.provider.embedding.api-key` | `SPECTOR_PROVIDER_EMBEDDING_API_KEY` | `sk-proj-xxxx` |
| `spector.provider.generation.model` | `SPECTOR_PROVIDER_GENERATION_MODEL` | `llama3.2` |
| `spector.hnsw.ef-construction` | `SPECTOR_HNSW_EF_CONSTRUCTION` | `300` |
| `spector.circadian.time-trigger` | `SPECTOR_CIRCADIAN_TIME_TRIGGER` | `2h` |
| `spector.icnu.weight-novelty` | `SPECTOR_ICNU_WEIGHT_NOVELTY` | `0.45` |

> [!NOTE]
> Because this rule is dynamic and universal, **any** configuration property listed in the [spector.yml Master Reference](spector-yml.md) can be supplied via an environment variable without needing special code handlers.

---

## ⚡ Convenience Aliases vs. Canonical Mappings

To simplify Docker commands, Kubernetes manifests, and CLI scripts, Spector's container entrypoint (`entrypoint.sh`) and Spring auto-configuration support short, memorable convenience aliases.

If an alias is defined and its canonical equivalent is empty, the alias value is automatically promoted to the canonical configuration key during startup:

| Short Alias | Canonical Config Property / Variable | Description |
|:---|:---|:---|
| `SPECTOR_EMBEDDING_PROVIDER` | `SPECTOR_PROVIDER_EMBEDDING_TYPE` | Embedding provider adapter (`ollama`, `openai`, etc.) |
| `SPECTOR_EMBEDDING_MODEL` | `SPECTOR_PROVIDER_EMBEDDING_MODEL` | Embedding model identifier (`nomic-embed-text`) |
| `SPECTOR_EMBEDDING_BASE_URL` | `SPECTOR_PROVIDER_EMBEDDING_BASE_URL` | Endpoint URL for remote embedding provider |
| `SPECTOR_EMBEDDING_API_KEY` | `SPECTOR_PROVIDER_EMBEDDING_API_KEY` | API secret key for embedding provider |
| `SPECTOR_EMBEDDING_DIMS` | `SPECTOR_PROVIDER_EMBEDDING_DIMENSIONS`<br/>`SPECTOR_MEMORY_DIMENSIONS` | Synchronously sets dimensions for both provider and cognitive memory |
| `SPECTOR_EMBEDDING_TIMEOUT` | `SPECTOR_PROVIDER_EMBEDDING_TIMEOUT` | Embedding request timeout (`30s`, `1m`) |
| `SPECTOR_GENERATION_PROVIDER` | `SPECTOR_PROVIDER_GENERATION_TYPE` | Text generation provider adapter |
| `SPECTOR_GENERATION_MODEL` | `SPECTOR_PROVIDER_GENERATION_MODEL` | Generation model identifier (`llama3.2`) |
| `SPECTOR_GENERATION_BASE_URL` | `SPECTOR_PROVIDER_GENERATION_BASE_URL` | Endpoint URL for text generation provider |
| `SPECTOR_GENERATION_API_KEY` | `SPECTOR_PROVIDER_GENERATION_API_KEY` | API secret key for text generation provider |
| `SPECTOR_PORT` | `spector.port` (server port) | HTTP API listen port (default: `7070`) |
| `SPECTOR_DATA_DIR` | `spector.memory.persistence-path` | Root directory for vector indexes and memory partitions |
| `SPECTOR_NODE_ID` | `spector.cluster.node-id` | Unique instance identifier in a multi-node cluster |
| `SPECTOR_API_KEY` | `spector.api-key` | Master API key required for client REST / MCP authentication |
| `SPECTOR_AUTH_JWT_SECRET` | `spector.auth.jwt.secret` | HMAC-SHA256 secret key for signing auth tokens |

---

## 🛡️ Docker Secrets Integration (`/run/secrets/`)

In production environments, storing sensitive credentials (such as OpenAI, Anthropic, or Azure API keys) in plain-text environment variables poses security risks (visible in `docker inspect` and `/proc/$PID/environ`).

Spector natively supports Docker Secrets and Kubernetes Secret volume mounts at `/run/secrets/`. During container startup, `entrypoint.sh` automatically detects and exports secrets mounted in this directory:

```
/run/secrets/
├── spector_embedding_api_key     → Exports to SPECTOR_EMBEDDING_API_KEY
├── spector_generation_api_key    → Exports to SPECTOR_GENERATION_API_KEY
├── spector_api_key               → Exports to SPECTOR_API_KEY
└── spector_auth_jwt_secret       → Exports to SPECTOR_AUTH_JWT_SECRET
```

### Docker Compose Example with Secrets

```yaml
version: '3.8'

services:
  spector:
    image: ghcr.io/spectrayan/spector:latest
    ports:
      - "8080:8080" # Cortex UI & API Proxy
      - "7070:7070" # Synapse REST API
    environment:
      - SPECTOR_EMBEDDING_PROVIDER=openai
      - SPECTOR_EMBEDDING_MODEL=text-embedding-3-small
      - SPECTOR_EMBEDDING_DIMS=1536
    secrets:
      - spector_embedding_api_key
      - spector_api_key
    volumes:
      - spector-data:/data

secrets:
  spector_embedding_api_key:
    file: ./secrets/openai_key.txt
  spector_api_key:
    file: ./secrets/spector_token.txt

volumes:
  spector-data:
```

---

## ☕ Java System Properties (`-Dspector.*`)

Any configuration key can be passed to the JVM runtime as a standard system property. System properties take precedence over environment variables:

```bash
java \
  --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
  -Dspector.memory.dimensions=768 \
  -Dspector.memory.capacity=500000 \
  -Dspector.provider.embedding.type=ollama \
  -jar spector-synapse.jar
```

---

## 🚀 Mandatory JVM Panama & Vector API Flags

Spector leverages Java 23+ **Project Panama** (Foreign Function & Memory API - JEP 454) and the **Vector API** (JEP 448) to achieve zero-GC off-heap vector search with native SIMD instructions (AVX-512, AVX2, ARM Neon).

Because these features utilize incubator modules and off-heap memory access, the JVM **must** be launched with the following arguments:

```bash
--enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED
```

### What Each Flag Does

| JVM Argument | Purpose | Why It Is Mandatory |
|:---|:---|:---|
| `--enable-preview` | Unlocks preview language and runtime features. | Required by Java 23/24 for incubating Panama memory structures. |
| `--add-modules=jdk.incubator.vector` | Loads the hardware SIMD vector accelerator module. | Enables vectorized dot product, cosine, and L2 distance computations. Without it, Spector falls back to slower scalar loops. |
| `--enable-native-access=ALL-UNNAMED` | Grants unrestricted off-heap memory mapping permissions. | Enables `Arena.ofShared()` and zero-copy `MemorySegment` off-heap memory-mapped files (`vectors.mmap`). |

> [!IMPORTANT]
> The official Spector Docker image (`ghcr.io/spectrayan/spector`) configures these flags automatically inside `entrypoint.sh`. If you build your own containers or run bare-metal JARs, you must include these flags in `JAVA_OPTS`.

---

## 🐧 Linux Kernel & OS-Level Tuning

When managing millions of vector records via memory-mapped off-heap segments (`vectors.mmap`), the host operating system's default memory-map and file-descriptor limits will cause `OutOfMemoryError: Map failed` or `IOException: Too many open files` if not adjusted.

### Required Kernel Parameters

```ini
# /etc/sysctl.d/99-spector.conf
vm.max_map_count=262144
fs.file-max=1048576
```

Apply immediately on Linux host nodes:
```bash
sudo sysctl -w vm.max_map_count=262144
sudo sysctl -w fs.file-max=1048576
```

### User Limit Configurations (`/etc/security/limits.conf`)

For bare-metal and systemd services, raise the process descriptor limits for the `spector` user:

```ini
spector soft nofile 65536
spector hard nofile 1048576
spector soft memlock unlimited
spector hard memlock unlimited
```

In Kubernetes environments, the official Spector Helm chart automatically applies `vm.max_map_count` and `fs.file-max` using a privileged initContainer. See the [Deployment & Cloud Config Guide](deployment-config.md) for full Helm values.
