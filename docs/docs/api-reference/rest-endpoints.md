# 🌐 REST API Reference

> **Complete reference for all Spector REST endpoints.** The API runs on an embedded Armeria server with virtual threads, accepting and returning JSON. Every request gets its own virtual thread — no connection limits to worry about.

---

## 🔧 Base Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Base URL | `http://localhost:7070` | Configurable port |
| Content-Type | `application/json` | All requests and responses |
| Auth Header | `X-API-Key: <key>` | Optional, configured at startup |
| CORS | Enabled | All origins by default |

> [!NOTE]
> When an API key is configured, requests without a valid key receive `401 Unauthorized`.

---

## 💚 System & Diagnostics

### `GET /api/v1/system/status`

Quick status check returning system uptime, version, and configured Ollama details.

```bash
curl http://localhost:7070/api/v1/system/status
```

**Response `200`:**
```json
{
  "status": "UP",
  "version": "1.0.0-SNAPSHOT",
  "uptime": "2h 15m 30s",
  "uptimeSeconds": 8130,
  "port": 7070,
  "ollamaUrl": "http://localhost:11434",
  "ollamaModel": "nomic-embed-text"
}
```

---

### `GET /api/v1/system/health`

Detailed diagnostics of each subsystem component.

```bash
curl http://localhost:7070/api/v1/system/health
```

**Response `200`:**
```json
{
  "status": "UP",
  "uptime": "PT2H15M30S",
  "timestamp": "2026-07-18T18:23:00Z",
  "components": {
    "memory": {
      "status": "UP",
      "engine": "SpectorMemory"
    },
    "llm": {
      "status": "CONFIGURED",
      "model": "llama3.2",
      "baseUrl": "http://localhost:11434"
    },
    "tools": {
      "status": "UP",
      "count": 16
    }
  }
}
```

---

### `GET /api/v1/system/metrics`

Uptime, JVM heap stats, processors count, and Synapse configuration.

```bash
curl http://localhost:7070/api/v1/system/metrics
```

**Response `200`:**
```json
{
  "jvm": {
    "version": "25",
    "vendor": "Oracle Corporation",
    "uptime": "PT2H15M30S",
    "heap": {
      "used": "142 MB",
      "max": "4096 MB",
      "total": "512 MB"
    },
    "processors": 8
  },
  "synapse": {
    "tools": 16,
    "memoryEngine": "active",
    "llmModel": "llama3.2"
  }
}
```

---

## 📥 Ingestion Endpoints

### `POST /api/v1/memory`

Ingest a single document with pre-computed vector and metadata.

```bash
curl -X POST http://localhost:7070/api/v1/memory \
  -H "Content-Type: application/json" \
  -d '{
    "id": "doc-1",
    "text": "Spector hybrid search engine",
    "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
    "tags": ["documentation"]
  }'
```

---

## 🧠 Memory Endpoints

> [!NOTE]
> Memory endpoints are available when `spector.mode` is `MEMORY` or `HYBRID`. Note that some older engine paths have been consolidated under `/api/v1/memory`.

### `POST /api/v1/memory/remember`

Store a cognitive memory with tags and source provenance.

```bash
curl -X POST http://localhost:7070/api/v1/memory/remember \
  -H "Content-Type: application/json" \
  -d '{
    "id": "pref-dark-mode",
    "text": "The user prefers dark mode for all editors",
    "type": "EPISODIC",
    "source": "USER_STATED",
    "tags": ["ui", "preferences"]
  }'
```

### `POST /api/v1/memory/recall`

Cognitive recall with fused scoring across all memory tiers.

```bash
curl -X POST http://localhost:7070/api/v1/memory/recall \
  -H "Content-Type: application/json" \
  -d '{"query": "dark theme settings", "topK": 5}'
```

### `DELETE /api/v1/memory/{id}`

Tombstone (forget) a memory by ID.

### `POST /api/v1/memory/{id}/reinforce`

Report positive/negative outcome for a memory.

### `POST /api/v1/memory/{id}/suppress`

Suppress or unsuppress a memory from recall results.

### `POST /api/v1/memory/{id}/resolve`

Mark a memory as resolved.

### `POST /api/v1/memory/introspect`

Metamemory self-analysis — how well does the system know a topic?

```bash
curl -X POST http://localhost:7070/api/v1/memory/introspect \
  -H "Content-Type: application/json" \
  -d '{"topic": "kubernetes"}'
```

### `POST /api/v1/memory/reminder`

Schedule a time-triggered reminder.

```bash
curl -X POST http://localhost:7070/api/v1/memory/reminder \
  -H "Content-Type: application/json" \
  -d '{"text": "Check build logs", "delaySeconds": 3600, "tags": "ci"}'
```

### `POST /api/v1/memory/scratchpad`

Quick-write to working memory scratchpad.

### `POST /api/v1/memory/why-not`

Explain why a memory was not recalled for a given query.

```bash
curl -X POST http://localhost:7070/api/v1/memory/why-not \
  -H "Content-Type: application/json" \
  -d '{"memoryId": "fact-42", "query": "pool config", "topK": 5}'
```

### `POST /api/v1/memory/reflect`

Manually trigger a sleep consolidation cycle.

### `GET /api/v1/memory/status`

Memory tier counts, partition info, and persistence status.

---

## ❌ Error Responses

| Status | Meaning |
|--------|---------|
| `200` | ✅ Success |
| `400` | Bad request (validation error, dimension mismatch) |
| `401` | Unauthorized (invalid or missing API key) |
| `404` | Resource not found |
| `503` | Service unavailable (embedding provider down) |

---

## 🔗 See Also

- [Getting Started](../getting-started/quickstart.md) — Quick start with curl examples

- [Java SDK Guide](../sdk-usage/java-client.md) — Type-safe programmatic access

- [CLI Reference](../cli-reference/spectorctl.md) — Command-line access to the API

- [Configuration Guide](../configuration/parameters.md) — Server and auth configuration