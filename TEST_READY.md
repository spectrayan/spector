# Test Readiness Report: Streaming Agentic Chat & Dual-Plane Persistence

> **Tracking**: Issue #263, ADR-0084, Cortex Visualization Overhaul, Requirement R5  
> **Date**: 2026-09-19  
> **Branch**: `feat/issue-263-streaming-agentic-chat`  
> **Test Writer Persona**: Sentinel (`teamwork_preview_test_writer_e2e_1`)

---

## 1. Executive Summary

The automated test infrastructure and visual regression testing suite for Issue #263, ADR-0084, and Requirement R5 are **READY**.

All test assets have been created and verified:
1. **Playwright E2E Visual Regression Suite**: Configured with `@playwright/test`, desktop Chromium (1280x720), mobile Chromium (375x667), mobile Safari (375x667), `toHaveScreenshot` assertions (`maxDiffPixelRatio: 0.02`, animations disabled), and offline mock interception.
2. **Golden Mock Fixtures**: 5 canonical fixtures (`empty-suggestions.json`, `thinking-then-tokens.sse`, `tool-interleave.sse`, `error-mid-stream.sse`, `history-replay.json`) adhering strictly to the ADR-0084 and SSE envelope contracts.
3. **Route Interception Guard**: `chat-fixtures.helper.ts` reliably intercepts `**/api/v1/features` to inject `{ chatEnabled: true }` (preventing redirect to `/memories`), along with all chat REST endpoints and SSE streams.
4. **Visual Regression Specs**: 5 comprehensive specs covering all critical UX and agentic states.
5. **Multi-Tier Quality Gates**: Tiers 1 through 4 test coverage documented and executable via single commands.

---

## 2. Test Runner Commands

### Tier 4: Playwright E2E Visual Regression Suite
```bash
# Run all E2E visual regression tests headlessly across desktop and mobile
cd cortex/spector-cortex
npm run test:e2e

# Run with interactive Playwright UI mode
npx playwright test --ui

# Run specific spec
npx playwright test e2e/specs/empty-chat.spec.ts

# Update visual baseline snapshots
npx playwright test --update-snapshots
```

### Tier 1–3: Backend Unit, Slice, & Integration Tests (Java)
```bash
# Run all Synapse tests
mvn test -pl synapse/spector-synapse

# Run specific chat session & persistence tests
mvn test -Dtest=ChatControllerSessionTest,JdbcChatTranscriptAdapterTest -pl synapse/spector-synapse
```

### Frontend Unit & Reducer Tests (TypeScript / Angular)
```bash
cd cortex/spector-cortex
npm test -- --watch=false
```

---

## 3. Comprehensive Coverage Checklist (Tiers 1–4)

| Tier | Component / Feature | Test Target / Spec | Status | Verification Detail |
|:---|:---|:---|:---:|:---|
| **Tier 1: Unit** | Memory Tag Policy | `MemoryTagPolicyTest` | ✅ READY | Rejects high-cardinality tags (`session:.*`, `id:.*`, `role:.*`, `type:turn`), blocks raw tool JSON dumps, enforces `MemorySource` provenance |
| **Tier 1: Unit** | Token Splitter | `TokenSplitterTest` | ✅ READY | Separates native reasoning parts & `<think>` blocks from visible markdown stream; zero CoT leakage |
| **Tier 1: Unit** | SSE DTO Serialization | `ChatDtoTest` | ✅ READY | Monotonic `seq` numbering, valid timestamp epochs, JSON schema compliance |
| **Tier 1: Unit** | Pure Turn Reducer | `chat-turn.reducer.spec.ts` | ✅ READY | Pure hydration parity between live stream events and historical turns |
| **Tier 2: Slice** | Session Listing | `ChatControllerSessionTest#testListSessions` | ✅ READY | `GET /api/v1/chat/sessions` returns `SessionsResponse` with preview and pagination |
| **Tier 2: Slice** | Session Rename | `ChatControllerSessionTest#testRenameSession` | ✅ READY | `PATCH /api/v1/chat/sessions/{id}` validates blank title, trims whitespace, returns updated summary |
| **Tier 2: Slice** | Session Deletion | `ChatControllerSessionTest#testDeleteSession` | ✅ READY | `DELETE /api/v1/chat/sessions/{id}` returns 204; removes operational records without forgetting cognitive engrams |
| **Tier 2: Slice** | Turn Message Replay | `ChatControllerSessionTest#testSessionMessages` | ✅ READY | `GET /api/v1/chat/sessions/{id}/messages` returns structured `turns[]` with thinking and tool cards |
| **Tier 3: Persistence** | Relational DDL | `Flyway V9 Migration` | ✅ READY | ANSI SQL schema on H2: `CHAT_SESSION`, `CHAT_TURN`, `CHAT_EVENT`, `GRAPH_CHECKPOINT` |
| **Tier 3: Persistence** | Operational Transcript | `JdbcChatTranscriptAdapterTest` | ✅ READY | Durable append, retrieval, and ordering of turns and events |
| **Tier 3: Persistence** | Checkpoint Resume | `JdbcCheckpointSaverTest` | ✅ READY | State serialization and graph resumption under LangGraph4j contracts |
| **Tier 3: Persistence** | Cognitive Priming | `SpectorSalientMemoryAdapterTest` | ✅ READY | Context priming using `RecallMode.OBSERVE` with 0 session tag writes |
| **Tier 4: Visual E2E** | Empty Chat & Suggestions | `e2e/specs/empty-chat.spec.ts` | ✅ READY | Verified empty container, 3 suggestion chips, textarea input, suggestion pre-fill, `empty-chat.png` |
| **Tier 4: Visual E2E** | Live Streaming Thinking | `e2e/specs/thinking-stream.spec.ts` | ✅ READY | Verified thinking trace display, live elapsed duration, assistant stream tokens, `thinking-stream.png` |
| **Tier 4: Visual E2E** | Interleaved Tool Execution | `e2e/specs/tool-interleave.spec.ts` | ✅ READY | Verified tool badge for `memory_recall`, argument viewer, success status chip, `tool-interleave.png` |
| **Tier 4: Visual E2E** | Conversation Drawer | `e2e/specs/drawer-navigation.spec.ts` | ✅ READY | Verified session previews, active session highlight, expanded vs collapsed states, `drawer-*.png` |
| **Tier 4: Visual E2E** | Historical Turn Replay | `e2e/specs/history-replay.spec.ts` | ✅ READY | Verified user prompt, thinking summary, past tool cards, formatted assistant markdown, `history-replay.png` |

---

## 4. Artifact & Fixture Manifest

### Golden Mock Fixtures (`cortex/spector-cortex/e2e/fixtures/`)
- `empty-suggestions.json`: Suggestions payload with empty session list.
- `thinking-then-tokens.sse`: Valid SSE stream with session, thinking events, keepalive comment, token deltas, and done.
- `tool-interleave.sse`: Valid SSE stream interleaving `tool_call` (`memory_recall`), `tool_result`, and resuming token deltas.
- `error-mid-stream.sse`: Valid SSE stream terminating with error code `SPE-700-001` (`retryable: true`).
- `history-replay.json`: Structured `SessionHistoryResponse` with turns containing thinking, tools, and assistant markdown.

### Test Specs (`cortex/spector-cortex/e2e/specs/`)
- `chat-fixtures.helper.ts`: Central mock route orchestrator (`**/api/v1/features`, `/api/v1/chat/*`).
- `empty-chat.spec.ts`: Empty state and suggestion chips visual verification.
- `thinking-stream.spec.ts`: Live thinking accordion and streaming token verification.
- `tool-interleave.spec.ts`: Interleaved tool card execution verification.
- `drawer-navigation.spec.ts`: Conversation drawer expanded and collapsed verification.
- `history-replay.spec.ts`: Historical thread replay verification.

---

## 5. Certification

The E2E test harness and visual regression test assets satisfy all conditions for Milestone R5 / E2E Track. The suite runs 100% deterministically and offline against mock fixtures.
