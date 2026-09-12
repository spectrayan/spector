# 🌐 REST API & Runtime Parameters Reference

> **Complete parameter dictionary for developers integrating with the Spector Synapse REST API.** Outlines required and optional HTTP request headers, JSON body payloads, query parameters, data types, validation rules, and cognitive scoring parameters.

---

## 1. HTTP Headers & Authentication

All requests to Spector Synapse must include appropriate headers for content negotiation and tenant security:

| Header Name | Type | Required | Default | Description |
|:---|:---|:---|:---|:---|
| `Content-Type` | String | Yes (for POST/PUT) | `application/json` | Request payload MIME type. |
| `Accept` | String | No | `application/json` | Expected response MIME type. |
| `X-API-Key` | String | Optional* | — | Static API key token configured via `SPECTOR_API_KEY`. Required if authentication is enabled. |
| `Authorization` | String | Optional* | — | Bearer JWT authentication header (`Bearer <token>`) when running with OAuth / JWT security. |
| `X-Spector-Namespace` | String | No | `default` | Overrides the target tenant namespace for multi-tenant isolation. |

---

## 2. Memory Operations Endpoints

### `POST /api/v1/memory/remember`

Ingests and memorizes new text content, generating embeddings, computing initial ICNU importance, and establishing synaptic graph linkages.

#### JSON Body Parameters

| Field | Type | Required | Default | Range / Options | Description |
|:---|:---|:---|:---|:---|:---|
| `text` | String | **Yes** | — | Non-empty string | The textual content to remember. Will be automatically embedded. |
| `id` | String | No | Auto TSID | String (max 64) | Optional caller-specified unique identifier. If omitted, a time-sorted TSID is generated. |
| `tier` | String | No | `SEMANTIC` | `WORKING`, `EPISODIC`, `SEMANTIC`, `PROCEDURAL` | Target memory tier. |
| `source` | String | No | `OBSERVED` | `USER_STATED`, `OBSERVED`, `INFERRED`, `PROCEDURAL` | Provenance category for lineage tracking and epistemic confidence. |
| `tags` | String | No | `""` | Comma-separated | Contextual tags (e.g. `"project:spector, priority:high"`). Indexed into off-heap Bloom filters. |
| `interest` | Float | No | `0.0` | 0.0–1.0 | ICNU Interest score: intrinsic relevance to the agent's core mission. |
| `challenge` | Float | No | `0.0` | 0.0–1.0 | ICNU Challenge score: cognitive complexity or unresolved questions. |
| `urgency` | Float | No | `0.0` | 0.0–1.0 | ICNU Urgency score: time-criticality of the information. |
| `valence` | Integer | No | `0` | -128 to +127 | Emotional valence: negative (-128 to -1) or positive (+1 to +127). |
| `arousal` | Integer | No | `0` | 0 to 255 | Emotional arousal / intensity level (0 = calm, 255 = extreme). |
| `timestampMs` | Long | No | Current Epoch | Milliseconds | Timestamp of the event. Defaults to current system time. |

#### Request Example

```bash
curl -X POST http://localhost:7070/api/v1/memory/remember \
  -H "Content-Type: application/json" \
  -d '{
    "text": "The production database maintenance window is scheduled for Sunday 02:00 UTC.",
    "tier": "SEMANTIC",
    "source": "USER_STATED",
    "tags": "infra, maintenance, schedule",
    "urgency": 0.85,
    "interest": 0.50
  }'
```

---

### `POST /api/v1/memory/recall`

Performs multi-stage cognitive retrieval, combining vector similarity, BM25 keyword matching, synaptic tag filtering, associative Hebbian graph expansion, and Ebbinghaus decay scoring.

#### JSON Body Parameters

| Field | Type | Required | Default | Range / Options | Description |
|:---|:---|:---|:---|:---|:---|
| `query` | String | **Yes** | — | Non-empty string | Natural language query text. Embedded and matched across semantic indexes. |
| `topK` | Integer | No | `10` | 1–100 | Number of top-ranked memory items to return. |
| `depth` | Integer | No | `1` | 1–5 | Associative graph expansion depth. `1` = direct semantic hits; `2+` = multi-hop relational traversal. |
| `tags` | Array[String] | No | `null` | String array | Synaptic tag filter (AND semantics). Evaluated off-heap using Bloom bitmasks. |
| `scoringMode` | String | No | `COGNITIVE` | `COGNITIVE`, `SIMILARITY`, `ASSOCIATIVE` | Scoring formula applied: `COGNITIVE` executes full 6-phase scoring ($S_{composite}$); `SIMILARITY` isolates pure cosine distance. |
| `recallMode` | String | No | `LEARN` | `LEARN`, `OBSERVE`, `REPLAY` | `LEARN` strengthens recalled synaptic paths via Auto-LTP; `OBSERVE` reads passively without weight modification. |

#### Request Example

```bash
curl -X POST http://localhost:7070/api/v1/memory/recall \
  -H "Content-Type: application/json" \
  -d '{
    "query": "When is the next database maintenance window?",
    "topK": 5,
    "depth": 2,
    "tags": ["infra", "maintenance"],
    "scoringMode": "COGNITIVE",
    "recallMode": "LEARN"
  }'
```

---

### `POST /api/v1/memory/search`

Pure vector nearest-neighbor search, bypassing the 6-phase cognitive scoring pipeline and graph traversal.

#### JSON Body Parameters

| Field | Type | Required | Default | Range / Options | Description |
|:---|:---|:---|:---|:---|:---|
| `query` | String | **Yes** | — | String | Query text to be embedded and searched in the HNSW index. |
| `topK` | Integer | No | `10` | 1–500 | Max results to retrieve. |
| `threshold` | Double | No | `0.0` | 0.0–1.0 | Minimum cosine similarity threshold (scores below are pruned). |

---

### `GET /api/v1/memory/browse` & `/table`

Paginated inspection endpoint designed for data tables and administrative explorer views.

#### URL Query Parameters

| Parameter | Type | Required | Default | Range / Options | Description |
|:---|:---|:---|:---|:---|:---|
| `page` | Integer | No | `0` | $\ge 0$ | Zero-indexed page number. |
| `pageSize` | Integer | No | `20` | 1–100 | Number of records per page. |
| `tier` | String | No | `null` | `WORKING`, `EPISODIC`, `SEMANTIC`, `PROCEDURAL` | Filter results by memory tier. |
| `tag` | String | No | `null` | String | Filter results containing a specific tag. |
| `includeTombstones` | Boolean | No | `false` | `true`, `false` | Whether to include soft-deleted/decayed tombstoned memories. |

---

### `POST /api/v1/memory/reflect`

Triggers an on-demand cognitive reflection sweep to cluster recent episodic memories and synthesize semantic generalizations.

#### JSON Body Parameters

| Field | Type | Required | Default | Range / Options | Description |
|:---|:---|:---|:---|:---|:---|
| `tier` | String | No | `EPISODIC` | `EPISODIC`, `SEMANTIC` | Source tier to cluster and reflect upon. |
| `minClusterSize` | Integer | No | `5` | 2–50 | Minimum episodic co-occurrence count required to produce an abstraction. |

---

## 3. Targeted Lifecycle & Feedback Endpoints

### `POST /api/v1/memory/reinforce/{id}`

Applies positive or negative emotional feedback to an existing memory, adjusting its valence and storage strength ($S$).

- **Path Parameter**: `id` — Unique memory identifier.
- **Request Body**:
  ```json
  {
    "valence": 50
  }
  ```
  `valence` is an integer between `-128` (strong negative punishment) and `+127` (strong positive reward).

---

### `POST /api/v1/memory/suppress/{id}`

Suppresses a memory from being recalled during normal queries or restores a previously suppressed memory.

- **Path Parameter**: `id` — Unique memory identifier.
- **Request Body**:
  ```json
  {
    "action": "SUPPRESS",
    "reason": "Information superseded by customer policy update."
  }
  ```
  `action` can be either `SUPPRESS` or `UNSUPPRESS`.

---

### `POST /api/v1/memory/resolve/{id}`

Flags an unresolved question, goal, or prospective task memory as completed.

- **Path Parameter**: `id` — Unique memory identifier.
- **Request Body**:
  ```json
  {
    "resolved": true
  }
  ```

---

### `POST /api/v1/memory/vacuum`

Forces an immediate storage compaction and defragmentation pass on a specific memory tier, purging soft-deleted tombstones and consolidating off-heap pages.

- **Request Body**:
  ```json
  {
    "tier": "SEMANTIC"
  }
  ```

---

## 4. Multi-Tenant Namespace Parameters

Spector supports isolated multi-tenant memory spaces managed via the `/api/v1/namespaces` endpoints:

| Endpoint | Method | Path / Query Parameters | Request Body | Description |
|:---|:---|:---|:---|:---|
| `/api/v1/namespaces` | `GET` | — | — | Lists all registered tenant namespaces with vector counts. |
| `/api/v1/namespaces/{id}` | `POST` | `id` (1–63 alphanumeric characters) | Optional config overrides (`{"capacity": 50000}`) | Registers a new isolated namespace partition. |
| `/api/v1/namespaces/{id}` | `DELETE` | `id` | — | Permanently drops a namespace and deletes its on-disk partition files. |
| `/api/v1/namespaces/{id}/stats` | `GET` | `id` | — | Retrieves telemetry for a specific namespace (tier counts, memory usage, query rate). |
