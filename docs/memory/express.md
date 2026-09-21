---
title: "Express — Memory Synthesis (Experimental)"
description: "The Express tool synthesizes natural language from retrieved memories. It generates text, not stored facts."
---

!!! warning "Experimental"
    This pathway is not part of the default memory contract.
    Enable only with the documented flag. Traces it writes use a
    distinct provenance and must not be mixed with user-stated facts
    at recall time unless you opt in.

# Express — Memory Synthesis

The `memory_express` MCP tool synthesizes natural-language responses grounded in retrieved memory traces. Express is a generation step, not a storage step — it reads from memory but does not write new traces.

## MCP Tool

```json
{
  "name": "memory_express",
  "description": "Synthesize natural language responses grounded in retrieved memories"
}
```

## When to use

- Summarizing a conversation's memory context for a user
- Generating a narrative from episodic traces
- Producing a context pack for prompt injection

→ See also: [Experimental overview](experimental.md)
