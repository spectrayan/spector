# spector-cli 🖥️

> **Terminal-based administration client (`spectorctl`) for Spector Search.**

`spector-cli` implements **`spectorctl`**, a command-line interface tool for managing remote Spector Search server nodes and coordinator clusters. It handles server configuration, sharding controls, document deletions, ingestion tasks, and remote search diagnostics.

---

## 🚀 Key Commands

### 1. Ingestion Control
```bash
# Ingest document from local text file
spectorctl ingest --url http://localhost:7070 --id doc-99 --file sample.txt

# Bulk ingest a directory of JSON records
spectorctl ingest-bulk --url http://localhost:7070 --dir ./data
```

### 2. Search Lookup Diagnostics
```bash
# Execute hybrid search with verbose metrics
spectorctl search --url http://localhost:7070 --text "vector databases" --topK 5 --verbose
```

### 3. Cluster & Shard Controls
```bash
# Query health and active node partitions
spectorctl status --url http://localhost:7070

# Request Server-Sent Event (SSE) metrics stream
spectorctl monitor --url http://localhost:7070
```
