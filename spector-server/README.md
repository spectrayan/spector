# spector-server 🌐

> **Javalin-based HTTP/REST server and metrics dashboard for Spector Search.**

`spector-server` implements a lightweight, secure REST API for Spector Search. Using the Javalin web framework bound directly to JVM Virtual Threads, it handles thousands of concurrent requests, stream responses, and provides comprehensive metrics dashboards.

---

## 🏗️ Core Architecture & Roles

1. **Javalin REST Controller (`SpectorServer`):** Direct REST mappings for document ingestion (`/api/v1/ingest`), vector search (`/api/v1/search`), and cluster state.
2. **Server Security (`ServerSecurity`):** Standard, thread-safe API Key validation filtering for all incoming endpoints.
3. **Metrics Streaming (`MetricsController`):** Server-Sent Events (SSE) streaming of live search statistics, cache hits, QPS, and JVM/ZGC memory metrics.

---

## 🚀 Operations

### Launching Server via CLI
```bash
# Start on port 7070, 384 dimensions, no authentication
mvn exec:java -pl spector-server -Dexec.mainClass="com.spectrayan.spector.server.SpectorServer"

# Start on port 8080, 768 dimensions, with API Key authentication
mvn exec:java -pl spector-server \
  -Dexec.mainClass="com.spectrayan.spector.server.SpectorServer" \
  -Dexec.args="8080 768 my-highly-secure-api-key"
```

### Ingestion endpoint
```bash
curl -X POST http://localhost:7070/api/v1/ingest \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-highly-secure-api-key" \
  -d '{
    "id": "doc-45",
    "title": "REST Server",
    "content": "Javalin REST web service utilizing Project Loom",
    "vector": [...]
  }'
```
