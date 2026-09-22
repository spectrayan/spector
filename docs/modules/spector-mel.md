# 🧪 Spector MEL — Memory Engine Language

> **A diagnostic REPL for developers and testers to interact with the Spector memory engine.**

MEL is a statement language over a single memory state $M$. It maps 1:1 to [MF-001 algebra](https://github.com/spectrayan/memory-fundamentals/blob/main/spec/MF-001-Memory-Model.md) operations. Think of it as `redis-cli` or `psql` for cognitive memory.

## Quick Start

```bash
# Build the MEL module
mvn package -pl synapse/spector-mel -am -DskipTests

# Launch the REPL (via CLI)
java -jar synapse/spector-cli/target/spector.jar mel
```

```
╔══════════════════════════════════════════════════════════════╗
║  Spector MEL — Memory Engine Language Shell                 ║
║  Type MEL statements ending with ;                          ║
║  \q to quit, \h for help, \c to clear buffer                ║
╚══════════════════════════════════════════════════════════════╝
Connected to memory engine (0 memories).

mel>
```

---

## Statement Reference

### `REMEMBER` — Store a Memory

```sql
REMEMBER TEXT 'The user prefers dark mode'
INTO SEMANTIC
TAGS ['preferences', 'ui']
IMPORTANCE 0.8
VALENCE 5;
```

**Clauses:**

| Clause | Type | Required | Description |
|:---|:---|:---|:---|
| `TEXT` / `BYTES` / `RULE` | payload | ✅ | Content type and value |
| `INTO` | tier | ✅ | `WORKING`, `EPISODIC`, `SEMANTIC`, or `PROCEDURAL` |
| `ID` | string | | Custom identifier (auto-generated if omitted) |
| `TAGS` | list | | Synaptic tags for Bloom filter encoding |
| `IMPORTANCE` | number | | Base importance weight (0.0–10.0) |
| `VALENCE` | number | | Emotional valence (-128 to +127) |
| `AROUSAL` | number | | Emotional intensity (0–255) |
| `SOURCE` | literal | | `EXPERIENCED`, `DISTILLED`, or `SIMULATED` |
| `PIN` | bool | | Prevent decay eviction |
| `UNRESOLVED` | bool | | Mark as needing resolution |

---

### `RECALL` — Query Memories

```sql
RECALL 'debugging tips for concurrency'
TOP 5
TAGS CONTAIN ['java', 'threading']
IMPORTANCE >= 0.3
TIERS SEMANTIC, PROCEDURAL;
```

**Clauses:**

| Clause | Type | Default | Description |
|:---|:---|:---|:---|
| query string | string | | Natural language cue |
| `TOP` | integer | 10 | Max results |
| `TAGS CONTAIN` | list | | Required tag filter |
| `IMPORTANCE >=` | number | 0.0 | Minimum importance threshold |
| `VALENCE BETWEEN ... AND` | range | | Valence window filter |
| `TIME BETWEEN ... AND` | range | | Temporal window (ISO-8601) |
| `TIERS` | list | all | Restrict to specific tiers |
| `ALLOW SIMULATED` | bool | `FALSE` | Include simulated traces |
| `CONTEXT` | string | | Additional context cue |

---

### `CONSOLIDATE` — Lift Traces

```sql
CONSOLIDATE 'trace-001', 'trace-002', 'trace-003'
INTO SEMANTIC
AS TEXT 'Summarized knowledge about Java concurrency';
```

Consolidates episodic traces into semantic or procedural tiers with lineage tracking.

---

### `FORGET` — Remove Memories

```sql
-- Permanently remove
FORGET 'memory-id' TOMBSTONE;

-- Temporarily hide from recall
FORGET 'memory-id' SUPPRESS;

-- Lower decay strength
FORGET 'memory-id' WEAKEN;
```

| Mode | Effect |
|:---|:---|
| `TOMBSTONE` | Permanent removal — memory is no longer available |
| `SUPPRESS` | Hidden from default recall — can be unsuppressed |
| `WEAKEN` | Lowers decay strength without removing |

---

### `EXPLAIN RECALL` — Diagnostic Scoring

```sql
EXPLAIN RECALL 'architecture decisions' TOP 3;
```

Returns the same results as `RECALL`, plus a per-signal scoring breakdown for each hit showing how similarity, decay, importance, and other cognitive signals contributed to the final score.

---

### `INTROSPECT` — Memory Catalog

```sql
INTROSPECT;
INTROSPECT TIER SEMANTIC;
```

Returns memory tier counts and catalog information without reading payloads.

---

## Special Commands

| Command | Description |
|:---|:---|
| `\q` or `exit` | Quit the REPL |
| `\h` or `help` | Show statement reference |
| `\c` | Clear the multi-line input buffer |

---

## Multi-Line Input

Statements can span multiple lines. Input continues until a semicolon (`;`) is encountered:

```
mel> REMEMBER TEXT 'This is a long memory
 ..>   that spans multiple lines'
 ..> INTO SEMANTIC
 ..> TAGS ['multiline', 'example'];
✅ Remembered as 0HQJK... into SEMANTIC tier with tags multiline, example
(3 ms)
```

---

## Comments

MEL supports two comment styles:

```sql
-- Line comment: everything after -- is ignored
RECALL 'query'; -- inline comment

/* Block comment:
   spans multiple lines */
RECALL 'another query';
```

---

## Scripting

MEL scripts are plain text files with `.mel` extension containing semicolon-terminated statements:

```bash
# example.mel
REMEMBER TEXT 'test data' INTO SEMANTIC TAGS ['test'];
RECALL 'test' TOP 3;
INTROSPECT;
```

---

## Grammar Specification

The full EBNF grammar is specified in [MEL-001-Grammar.md](https://github.com/spectrayan/memory-fundamentals/blob/main/mel/MEL-001-Grammar.md).

### Phase 1 Scope

| Class | Statements | Status |
|:---|:---|:---|
| **Required** | `REMEMBER`, `RECALL`, `CONSOLIDATE`, `FORGET` | ✅ Implemented |
| **Diagnostic** | `EXPLAIN RECALL` | ✅ Implemented |
| **Optional** | `INTROSPECT` | ✅ Implemented |
| **Optional** | `REHEARSE`, `ASSOCIATE`, `DREAM`, etc. | 🔜 Future |

---

## Architecture

```
MelLexer  →  MelParser  →  MelStatement (AST)  →  MelEvaluator  →  MelResult
   ↑                                                     ↓
 source                                            SpectorMemory
 string                                              (engine)
```

| Component | Responsibility |
|:---|:---|
| `MelLexer` | Tokenizes source into keyword, string, number, and punctuation tokens |
| `MelParser` | Recursive-descent parser producing sealed AST nodes |
| `MelStatement` | Sealed interface with 6 record permits (immutable AST) |
| `MelEvaluator` | Bridges AST to `SpectorMemory` engine calls |
| `MelResult` | Value record with status, output text, and timing |
| `MelRepl` | Interactive REPL loop with multi-line input |

---

## What MEL Is Not

- **Not a production API** — use MCP tools or the Java SDK for production integrations
- **Not SQL** — no joins, no schemas, no transactions
- **Not an embedding API** — the engine owns embedding on `REMEMBER` and `RECALL`
- **Not a permission bypass** — engine gates (tombstone, tier mask, etc.) still apply
