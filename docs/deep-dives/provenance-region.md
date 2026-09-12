# Provenance Region

> **Status**: Accepted — implemented in [#731](https://github.com/spectrayan/spector/issues/731)
> **Since**: v1.5.0

The **Provenance Region** records the lineage between episodic conversation turns
and the consolidated semantic (or procedural) memories they produce during
the [Reflect pathway](../architecture/overview.md) consolidation cycle.

## Motivation

When an AI agent consolidates raw episodic turns into semantic facts, the
origin of each fact is lost. This makes it impossible to:

- **Explain** a memory: "Where did this fact come from?"
- **Audit** a session: "What memories were derived from this conversation?"
- **Detect multi-pass consolidation**: A session can grow with new turns after
  an earlier consolidation pass — provenance tracks each pass independently.

## Physical Layout

Each provenance record is a **72-byte fixed-stride** CRC32C-protected slot
defined in `ProvenanceLayout`:

| Field             | Type   | Offset | Size | Description |
|:------------------|:-------|-------:|-----:|:------------|
| `flags`           | byte   |     0  |   1  | `0x01` = live, `0x02` = partial run, `0xFF` = tombstone |
| `source_kind`     | byte   |     1  |   1  | Source type (1 = episodic log) |
| `target_kind`     | byte   |     2  |   1  | Target type (1 = semantic, 2 = procedural) |
| `prefix_kind`     | byte   |     3  |   1  | Target ID prefix registry ordinal |
| `pass_number`     | short  |     4  |   2  | 1-indexed consolidation pass counter |
| `turn_count`      | short  |     6  |   2  | Number of episodic turns covered |
| `session_id`      | long   |     8  |   8  | Episodic session ID |
| `target_tsid`     | long   |    16  |   8  | TSID of consolidated memory |
| `consolidated_at` | long   |    24  |   8  | Epoch millis when consolidation occurred |
| `partition_seq`   | int    |    32  |   4  | Partition holding source turns |
| `first_seq`       | int    |    36  |   4  | First episodic sequence_id |
| `last_seq`        | int    |    40  |   4  | Last episodic sequence_id |
| `first_offset`    | int    |    44  |   4  | Byte offset hint for first turn |
| `last_offset`     | int    |    48  |   4  | Byte offset hint for last turn |
| `fact_index`      | byte   |    52  |   1  | Index within batch (0-indexed) |
| `batch_count`     | byte   |    53  |   1  | Total facts in batch |
| `content_hash_hi` | short  |    54  |   2  | Upper 16 bits of fact CRC32C |
| *(reserved)*      | -      |    56  |  12  | Reserved for future use |
| `crc32c`          | int    |    68  |   4  | CRC32C integrity checksum |

**Total**: 72 bytes per record. Default capacity: **8,192 records**.

## API

### Forward Lookup - `explain(memoryId)`

Given a consolidated memory ID, return its provenance:

```java
Optional<MemoryProvenance> prov = memory.explain("0ABCDEF1234GH");
prov.ifPresent(p -> {
    System.out.printf("Session: 0x%x, turns %d-%d, pass %d%n",
        p.sessionId(), p.firstSeq(), p.lastSeq(), p.passNumber());
});
```

### Reverse Lookup - `sessionProvenance(sessionId)`

Given an episodic session ID, return all derived memories:

```java
List<MemoryProvenance> derived = memory.sessionProvenance(sessionId);
for (var p : derived) {
    System.out.printf("  -> %s (pass %d, fact %d/%d)%n",
        p.targetId(), p.passNumber(), p.factIndex(), p.batchFactCount());
}
```

## Configuration

| Property | Default | Description |
|:---------|--------:|:------------|
| `spector.memory.provenance-capacity` | `8192` | Maximum number of provenance records |

## Multi-Pass Consolidation

When new turns arrive in a session that was already consolidated, the next
consolidation cycle assigns `pass_number = max(existing) + 1`. This allows
distinct lineage per pass without overwriting earlier provenance records.

## Bug Fixes (shipped with this feature)

Six bugs in the consolidation pipeline were discovered and fixed during
provenance implementation:

1. **Offset corruption in EpisodicSessionIndex.rebuild()** - used absolute
   offsets instead of region-relative.
2. **HashMap key collision** in consolidation relay - `EpisodeRecord` is a
   value type; identical turns collided in `Map<EpisodeRecord, Long>`.
3. **Discarded affect metadata** - valence, arousal, and importance were
   hardcoded to zero instead of using fact-level values.
4. **Missing session tag** - consolidated facts lost their session lineage tag.
5. **Duplicate facts on retry** - turns were marked consolidated *after*
   ingestion, causing duplicates on crash/retry.
6. **PartitionHandle not threaded** - `processLogStore` used a stale partition
   sequence instead of the handle's actual sequence.
