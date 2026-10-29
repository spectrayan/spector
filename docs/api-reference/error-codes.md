# Spector Error Code Reference

All Spector errors follow the `SPE-XXX-YYY` schema where `XXX` identifies the
error category and `YYY` identifies the specific error within that category.

**Stability guarantee:** Error codes are immutable once assigned. They will never
be reassigned or removed, even if deprecated.

---

## How to Read Error Codes

```
SPE-100-001
│   │    │
│   │    └── Specific error (001–999)
│   └─────── Category (100–900)
└─────────── Spector prefix
```

| Category Range | Subsystem |
|---|---|
| `SPE-100-xxx` | Input Validation |
| `SPE-110-xxx` | Configuration |
| `SPE-200-xxx` | Index |
| `SPE-210-xxx` | Storage |
| `SPE-300-xxx` | Embedding |
| `SPE-310-xxx` | Memory |
| `SPE-400-xxx` | GPU |
| `SPE-500-xxx` | Server (REST/gRPC/MCP) |
| `SPE-510-xxx` | Client SDK |
| `SPE-600-xxx` | Ingestion |
| `SPE-700-xxx` | Cluster |
| `SPE-900-xxx` | Internal |

---

## Validation Errors (SPE-100)

These errors indicate invalid input provided by the caller.

| Code | Message | Common Cause |
|---|---|---|
| `SPE-100-001` | Vector dimensions must be positive | Dimensions set to 0 or negative in config |
| `SPE-100-002` | Expected {n} dimensions but received {m} | Query vector has different dimensionality than the index |
| `SPE-100-003` | Vector must not be null | Null vector passed to ingest or search |
| `SPE-100-004` | Vector length does not match expected dimensions | Float array length ≠ configured dimensions |
| `SPE-100-005` | top_k must be between 1 and max | top_k set to 0, negative, or exceeding index capacity |
| `SPE-100-006` | Document ID must not be null or empty | Empty string or null passed as document ID |
| `SPE-100-007` | Required argument must not be null | A required method parameter was null |
| `SPE-100-008` | Argument out of range | A numeric parameter is outside valid bounds |
| `SPE-100-009` | Unsupported quantization type | Quantization type not recognized |
| `SPE-100-010` | Capacity exceeded | Collection or buffer exceeds maximum size |
| `SPE-100-011` | SimilarityFunction must not be null | Null similarity function in config |
| `SPE-100-012` | Collection must not be empty | Empty list/array passed where non-empty required |
| `SPE-100-013` | Invalid value for parameter | General argument validation failure |
| `SPE-100-014` | Argument must be non-negative | Negative value for a non-negative parameter |
| `SPE-100-015` | Length mismatch | Two arrays that must be same length differ |
| `SPE-100-016` | Bit width invalid | Quantization bit width not 2, 4, or 8 |
| `SPE-100-020` | Invalid Idempotency-Key header | `Idempotency-Key` header is empty, contains whitespace or non-printable ASCII characters, or conflicts with `X-Idempotency-Key` |
| `SPE-100-021` | Idempotency-Key header exceeds maximum length | `Idempotency-Key` header exceeds 256 ASCII bytes |
| `SPE-100-022` | Idempotency-Key reused with different fingerprint | Same `(namespace, Idempotency-Key)` reused with a different HTTP method, path, or payload SHA-256 (HTTP 422) |

---

## Configuration Errors (SPE-110)

These errors indicate problems with Spector configuration files or values.

| Code | Message | Resolution |
|---|---|---|
| `SPE-110-001` | Configuration file not found | Verify the config file path. Check `spector.yml` or `spector.properties` exists. |
| `SPE-110-002` | Failed to parse configuration | Check YAML/properties syntax. Validate with a YAML linter. |
| `SPE-110-003` | Invalid configuration value | Verify the reported field value is within documented bounds. |
| `SPE-110-004` | Configuration profile not found | Check available profiles in your config file. |
| `SPE-110-005` | Required configuration key missing | Add the missing key to your config file. |
| `SPE-110-006` | No text generation provider available | A feature such as `tag-extractor: llm` needs an LLM. Set `spector.provider.generation.type`/`model` and its API key (for OpenAI, `OPENAI_API_KEY`), or switch the feature off. |
| `SPE-110-007` | An agent token is required to start the MCP server | `spector mcp` (stdio) needs `SPECTOR_TOKEN_FILE` pointing at a token from `spector token issue`, and `SPECTOR_AUTH_AGENT_JWKS_FILE` pointing at the issuer's public key set. |
| `SPE-110-008` | The agent token must pin exactly one namespace | Issue the token with a single `--namespace`. MCP calls are bound to that namespace only. |
| `SPE-110-009` | The agent token was rejected | The signature, issuer, expiry or subject check failed. Issue a fresh token with `spector token issue` and check that the server has the matching public key set. |
| `SPE-110-010` | JWT key is unusable | HS256 secrets must be at least 32 bytes; agent-token keys must be a valid EC P-256 JWK (private, issuer only) or JWK set (public, servers). |
| `SPE-110-011` | Embedding model is unusable | A configured local ONNX embedding model cannot be used: `model-path` or `vocab-path` is missing, unreadable or empty, `pooling-mode` is invalid, the probe vector length differs from `dimensions`, or a named model is not available. There is no fallback model. |
| `SPE-110-012` | Extraction model is unusable | `tag-extractor: gliner` or `entity.extraction-mode: gliner` is set, but the GLiNER model, `tokenizer.json` or `gliner_config.json` under `spector.provider.extraction.*` is missing, unreadable or not a supported GLiNER span model. Nothing is downloaded and there is no fallback extractor. |

---

## Index Errors (SPE-200)

These errors relate to vector index operations.

| Code | Message | Resolution |
|---|---|---|
| `SPE-200-001` | HNSW index construction failed | Check available memory. Reduce `capacity` or `dimensions`. |
| `SPE-200-002` | HNSW graph integrity check failed | Index file may be corrupted. Re-build from source data. |
| `SPE-200-003` | Index has reached maximum capacity | Increase `capacity` in config, or delete old documents. |
| `SPE-200-004` | Index is read-only | Index was opened in read-only mode. |
| `SPE-200-005` | IVF centroid training failed | Provide more training vectors or reduce `nlist`. |
| `SPE-200-006` | BM25 text tokenization failed | Check text encoding. Ensure input is valid UTF-8. |
| `SPE-200-007` | Index serialization to disk failed | Check disk space and write permissions. |
| `SPE-200-008` | Index deserialization from disk failed | Index file may be corrupted or incompatible version. |
| `SPE-200-009` | Index not trained | Call `train()` before searching an IVF-PQ index. |
| `SPE-200-010` | Centroid count must be positive | Set `nlist` to a positive integer. |
| `SPE-200-011` | HNSW graph connectivity below threshold | Index quality degraded. Rebuild with higher `efConstruction`. |

---

## Storage Errors (SPE-210)

These errors relate to vector storage and disk I/O.

| Code | Message | Resolution |
|---|---|---|
| `SPE-210-001` | Memory segment is closed | Don't use the store after calling `close()`. |
| `SPE-210-002` | Memory-mapped file creation failed | Check disk space, file permissions, and OS mmap limits. |
| `SPE-210-003` | Vector store has reached capacity | Increase `capacity` or delete old vectors. |
| `SPE-210-004` | Disk I/O operation failed | Check disk health, space, and permissions. |
| `SPE-210-005` | Write-ahead log write failed | Check disk space. WAL directory may be full. |
| `SPE-210-006` | Write-ahead log replay failed | WAL file may be corrupted. Check logs for details. |
| `SPE-210-007` | Vector store not initialized | Ensure the store is opened before operations. |
| `SPE-210-008` | Invalid index file format | File was created by an incompatible version. |

---

## Embedding Errors (SPE-300)

These errors relate to embedding provider connectivity.

| Code | Message | Resolution |
|---|---|---|
| `SPE-300-001` | Embedding provider is unavailable | Check that Ollama (or your provider) is running. Verify the URL. |
| `SPE-300-002` | Embedding request failed | Check provider logs for details. |
| `SPE-300-003` | Embedding request timed out | Increase timeout or check provider load. |
| `SPE-300-004` | Embedding model not found | Pull the model: `ollama pull <model-name>`. |
| `SPE-300-005` | Embedding dimension mismatch | Model returns different dimensions than index expects. Change model or recreate index. |

---

## Memory Errors (SPE-310)

These errors relate to the cognitive memory subsystem.

| Code | Message | Resolution |
|---|---|---|
| `SPE-310-001` | Memory tier has reached capacity | Configure higher capacity or enable consolidation. |
| `SPE-310-002` | Cognitive recall pathway failed | Check logs for underlying cause. |
| `SPE-310-003` | Memory consolidation failed | Check disk space and WAL integrity. |
| `SPE-310-004` | Memory ID not found | The specified memory ID does not exist in any tier. |
| `SPE-310-005` | Memory WAL file corrupted | WAL file is unreadable. Recovery may require reinitialization. |
| `SPE-310-029` | LLM tag extraction failed | `tag-extractor: llm` is set and the LLM call failed or returned nothing usable. The write is refused rather than silently tagged by keywords. Check the generation provider, its API key and quota, or set `tag-extractor: content`. |
| `SPE-310-030` | Local extraction failed | GLiNER inference failed for a memory. A write tagged by `tag-extractor: gliner` is refused; an entity-extraction failure is logged with this code. Memory text is never logged. |
| `SPE-310-031` | Store already open | Another process (or another memory in the same process) holds the exclusive lock `.spector-store.lock` in the persistence directory. Stop the other writer; the OS releases the lock when that process exits. |
| `SPE-310-032` | Active namespace capacity exhausted | All active namespaces in `NamespaceRegistry` currently hold active leases when `maxActiveNamespaces` is reached. Release active leases or increase `maxActiveNamespaces`. |

---

## GPU Errors (SPE-400)

These errors relate to GPU acceleration via CUDA/Panama FFM.

| Code | Message | Resolution |
|---|---|---|
| `SPE-400-001` | CUDA driver not found | Install NVIDIA CUDA drivers. GPU features will fall back to CPU. |
| `SPE-400-002` | GPU memory allocation failed | Reduce batch size or free GPU memory from other processes. |
| `SPE-400-003` | GPU kernel launch failed | Check CUDA compatibility. Update GPU drivers. |
| `SPE-400-004` | GPU device error | Hardware issue or driver crash. Restart and check `nvidia-smi`. |
| `SPE-400-005` | GPU memory budget exceeded | Reduce `gpuMemoryBudget` or free GPU memory. |

---

## Server Errors (SPE-500)

These errors are returned by the Spector REST API, gRPC, or MCP server.

| Code | HTTP Status | Message | Resolution |
|---|---|---|---|
| `SPE-500-001` | 400 | Bad request | Fix the request body or parameters. |
| `SPE-500-002` | 404 | Resource not found | Verify the document/collection ID exists. |
| `SPE-500-003` | 409 | Resource conflict | Document with this ID already exists. |
| `SPE-500-004` | 401 | Unauthorized | Provide a valid API key. |
| `SPE-500-005` | 503 | Service unavailable | Backend service is down. Retry after delay. |
| `SPE-500-006` | 500 | MCP tool execution failed | Check MCP tool logs for details. |
| `SPE-500-007` | 500 | gRPC transport error | Check network connectivity between nodes. |
| `SPE-500-014` | 403 | MCP namespace mismatch | MCP calls are bound to the single namespace pinned by the caller's token. A `namespace` argument, `X-Spector-Namespace` header or `?namespace=` parameter may only repeat that namespace; omit it. |
| `SPE-500-015` | 409 | Idempotency-Key request in flight | An identical mutating request with the same `(namespace, Idempotency-Key)` is currently executing. Wait for the `Retry-After` interval before retrying. |

---

## Client SDK Errors (SPE-510)

These errors are raised by the Spector client SDK.

| Code | Message | Resolution |
|---|---|---|
| `SPE-510-001` | Failed to connect to Spector server | Verify server URL and that the server is running. |
| `SPE-510-002` | Client request timed out | Increase timeout or check server load. |
| `SPE-510-003` | Invalid server response | Server may be returning unexpected format. Check version compatibility. |

---

## Ingestion Errors (SPE-600)

These errors relate to the document ingestion pipeline.

| Code | Message | Resolution |
|---|---|---|
| `SPE-600-001` | Unsupported document format | Use a supported format (PDF, TXT, MD, HTML, DOCX). |
| `SPE-600-002` | Document chunking failed | Check document encoding and content. |
| `SPE-600-003` | Ingestion pipeline failed | Check logs for the underlying cause. |
| `SPE-600-004` | Failed to read document | Check file path, read permissions, or ensure the file is not corrupted. |

---

## Cluster Errors (SPE-700)

These errors relate to distributed mode operations.

| Code | Message | Resolution |
|---|---|---|
| `SPE-700-001` | Shard is unavailable | Check that all cluster nodes are running. |
| `SPE-700-002` | Cluster membership operation failed | Check network connectivity between nodes. |
| `SPE-700-003` | Request routing failed | Shard map may be stale. Wait for rebalance. |

---

## Internal Errors (SPE-900)

These errors indicate a bug in Spector itself. **If you encounter a 900-series
error, please report it** with the full error code and any available log context.

| Code | Message | What It Means |
|---|---|---|
| `SPE-900-001` | Internal error | An unexpected condition occurred. This is a bug. |
| `SPE-900-002` | Internal invariant violated | A data structure is in an invalid state. This is a bug. |
| `SPE-900-003` | Reached unreachable code path | A switch/if exhaustiveness gap. This is a bug. |
| `SPE-900-004` | Concurrent execution failed | A virtual thread subtask failed unexpectedly. |

---

## JSON Error Response Format

All REST API errors return a structured JSON response:

```json
{
  "code": "SPE-100-002",
  "category": "Validation",
  "message": "[SPE-100-002] Expected 384 dimensions but received 768",
  "status": 400,
  "path": "/api/v1/ingest",
  "timestamp": "2026-05-30T12:00:00Z"
}
```

Legacy errors (without error codes) omit the `code` and `category` fields.
