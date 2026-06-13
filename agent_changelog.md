# Spector — Agent Changelog

This changelog records all updates, refutations, optimizations, and additions made by autonomous agents working on the Spector project. It tracks chronological context to ensure seamless handoffs between agent sessions.

---

## [1.0.0] - 2026-06-13

### Changed — Enterprise Extraction
*   **Cortex Migration**: Removed `spector-cortex/` directory — migrated to `spector-enterprise/ui/spector-cortex`. Core repo is now headless/embeddable.
*   **Docs Update**: Updated `docs/docs/cortex/index.md` with migration banner pointing to spector-enterprise.
*   **Changelog**: Added enterprise extraction entry to `CHANGELOG.md`.

### Impact
*   **Separation of Concerns**: Core Spector is now a pure embeddable engine (like Elasticsearch without Kibana). The full-stack product with UI, connectors, and provider management lives in `spector-enterprise`.
*   **No Maven Changes**: `spector-cortex` was never a Maven module (standalone Angular), so no POM modifications needed.

---

## [1.0.0] - 2026-06-11

### Added
*   **Onboarding Project Context**: Created `PROJECT_CONTEXT.md` in the root mapping the module layers (Foundation, Embedding, Search, Intelligence, Runtime, Infrastructure), JVM args, core libraries, and agent rule/workflow integrations.
*   **Supplemental Guidelines**: Added `.agents/rules/supplemental.md` to cover state management rules (StampedLock, atomic references), uncaught thread-level exception mapping, double-logging avoidance, and environment variable override lookups.
*   **Cognitive Memory Skill**: Added `.agents/skills/cognitive-memory/SKILL.md` to document cognitive memory architecture standards, 64-byte header layouts, the 6-phase fused scoring mathematical formula, and biological logic engines (Dopamine, Hebbian, Hippocampus).
*   **Agent Memory Log**: Initialized this file (`agent_changelog.md`) to track agent interactions.

### Impact
*   **Zero Code Changes**: All updates strictly targeted workspace metadata, guidelines, and context schemas without modifying compile-gated source files.
*   **Context Optimization**: New agent sessions can immediately blend global settings, biological constraints, and architectural layout dependencies.

---

## Changelog Format Schema

When modifying the codebase in future sessions, append new entries at the top under a markdown section in this format:

```markdown
## [Version/Date] - YYYY-MM-DD

### Added / Changed / Fixed / Removed
*   **Short Title**: Detailed list of modified files, reasons, and structural updates.
    *   File references: `[RelativePath](file:///absolute/path/to/file)`
    *   Commit tags: `feat(module): description` or `fix(module): description`

### Verification Results
*   Test suites executed and their outcomes.
*   Performance differences or JMH benchmark logs.
```
