# Spector Test Infrastructure & Architecture Guide

> **Scope**: Issue #263 (Streaming Agentic Chat), ADR-0084 (Dual-Plane Persistence), Cortex Visualization, and Automated Playwright Visual Regression Testing (Requirement R5).

---

## 1. Overview & Test Strategy

Spector employs a multi-tiered test pyramid designed to guarantee sub-500ms TTFT streaming fidelity, dual-plane data integrity (H2 relational operational plane vs. Spector Memory off-heap cognitive plane), and pixel-perfect Angular presentation.

```
                  ┌──────────────────────┐
                  │ Tier 4: Playwright   │  ← Deterministic visual regression,
                  │ E2E Visual Testing   │     golden SSE/JSON fixtures, no live LLM
                 ┌┴──────────────────────┴┐
                 │ Tier 3: Integration &  │  ← H2 Flyway V9, JdbcChatTranscriptAdapter,
                 │ Persistence Tests      │     LangGraph4j checkpoint persistence
                ┌┴────────────────────────┴┐
                │ Tier 2: Controller Slice │  ← Spring WebMvc / MockMvc slice tests for
                │ & SSE Endpoint Tests     │     session CRUD & stream protocol
               ┌┴──────────────────────────┴┐
               │ Tier 1: Unit Tests & Pure  │  ← MemoryTagPolicy guard, TokenSplitter state
               │ State Machine Reducers     │     machine, reduceChatEvents pure reducer
               └────────────────────────────┘
```

---

## 2. Test Tiers & Matrix

### Tier 1: Unit Tests & Pure Reducers
- **Java (`synapse/spector-synapse`)**:
  - `MemoryTagPolicy`: Invariant verification enforcing rejection of high-cardinality tags (`session:.*`, `id:.*`, `role:.*`, `type:turn`), prevention of raw tool JSON dumps, and validation of `MemorySource` provenance (`USER_STATED`, `OBSERVED`, `INFERRED`, `REFLECTED`).
  - `TokenSplitter`: Finite-state machine parsing `<think>...</think>` tags and streaming tokens cleanly without leaking Chain-of-Thought into visible tokens.
  - `ChatDto` / `ChatStreamEvent`: Serialization, deserialization, sequence ordering (`seq` monotonic per turn).
- **TypeScript (`cortex/spector-cortex`)**:
  - `reduceChatEvents`: Pure reducer function tested with discrete SSE streams and historical turn structures to ensure bit-identical state hydration.
  - `MarkdownPipe`: Safe sanitization via DOMPurify and GFM markdown parsing.

### Tier 2: Controller & REST Slice Tests
- **Module**: `synapse/spector-synapse`
- **Scope**:
  - `GET /api/v1/chat/sessions?limit=`: Session listing with pagination and preview text.
  - `PATCH /api/v1/chat/sessions/{id}`: Session title rename validation (trimming, max length, 404 handling).
  - `DELETE /api/v1/chat/sessions/{id}`: Operational data deletion leaving cognitive memory engrams intact.
  - `GET /api/v1/chat/sessions/{id}/messages`: Structured turn retrieval (`SessionHistoryResponse`).
  - `POST /api/v1/chat/stream`: SSE handshake, header validation (`text/event-stream`), keepalive comments (`:keepalive`).

### Tier 3: Integration & Persistence Tests
- **Module**: `synapse/spector-synapse`
- **Scope**:
  - Flyway migration `V9__chat_operational_plane.sql` schema verification on H2:
    - Tables: `CHAT_SESSION`, `CHAT_TURN`, `CHAT_EVENT`, `GRAPH_CHECKPOINT`.
  - `JdbcChatTranscriptAdapter`: Relational persistence of sessions, turns, and ordered chronological events.
  - `JdbcCheckpointSaver`: State checkpoint serialization and thread resumption under LangGraph4j contracts.
  - `SpectorSalientMemoryAdapter`: Safe context priming via `RecallMode.OBSERVE` with zero session tag pollution.

### Tier 4: E2E Visual Regression Suite (Playwright)
- **Module**: `cortex/spector-cortex/e2e`
- **Framework**: `@playwright/test` (v1.63+)
- **Scope**:
  - Offline, deterministic testing without live backend or Ollama.
  - Golden mock fixtures providing exact wire SSE and REST payloads.
  - Viewports: Desktop Chromium (1280x720), Mobile Chromium (375x667), Mobile Safari (375x667).
  - Visual comparison: `toHaveScreenshot` with `maxDiffPixelRatio: 0.02` and `animations: 'disabled'`.

---

## 3. Playwright E2E Architecture & Mocking Strategy

### 3.1 Critical Route Interception Guard
In Spector Cortex, `/chat` is gated by `canActivate: [featureGuard('chatEnabled')]`. In `FeatureFlagService`, `chatEnabled` defaults to `false`. Without intercepting `/api/v1/features`, navigation to `/chat` redirects immediately to `/memories`.

All Playwright specs utilize `setupChatMocks(page, options)` from `e2e/specs/chat-fixtures.helper.ts`, which guarantees:
1. `**/api/v1/features` returns `{ chatEnabled: true, agentChatEnabled: true }`.
2. `**/api/v1/chat/models` returns active Ollama model mock.
3. `**/api/v1/chat/config` returns agent config mock.
4. `**/api/v1/chat/sessions*` returns session summaries.
5. `**/api/v1/chat/stream` serves golden SSE fixtures.
6. `**/api/v1/chat/sessions/*/messages` serves structured historical turns.

### 3.2 Golden Mock Fixtures Inventory (`cortex/spector-cortex/e2e/fixtures/`)

| Fixture File | Protocol / Format | Description |
|:---|:---|:---|
| `empty-suggestions.json` | JSON | Suggestions response and empty session state |
| `thinking-then-tokens.sse` | SSE (`text/event-stream`) | Stream emitting `session` -> `thinking` deltas -> `:keepalive` -> `token` deltas -> `done` |
| `tool-interleave.sse` | SSE (`text/event-stream`) | Stream emitting `session` -> `thinking` -> `tool_call` (`memory_recall`) -> `tool_result` -> `token` deltas -> `done` |
| `error-mid-stream.sse` | SSE (`text/event-stream`) | Stream emitting `session` -> `thinking` -> `error` (`SPE-700-001`, `retryable: true`) |
| `history-replay.json` | JSON (`SessionHistoryResponse`) | Structured turns containing user prompt, thinking trace (`elapsedMs: 1380`), tool cards, and assistant markdown |

### 3.3 Visual Regression Specs Inventory (`cortex/spector-cortex/e2e/specs/`)

1. **`empty-chat.spec.ts`**:
   - Tests clean empty state with Copilot-style suggestion chips.
   - Verifies input field visibility and chip click prompt pre-fill.
   - Captures `empty-chat.png` across desktop and mobile viewports.
2. **`thinking-stream.spec.ts`**:
   - Tests live streaming flow with thinking accordion open.
   - Verifies live elapsed duration and streaming assistant tokens.
   - Captures `thinking-stream.png`.
3. **`tool-interleave.spec.ts`**:
   - Tests tool call execution during streaming.
   - Verifies tool badge, arguments display, status chip (`success`), and resumed assistant response.
   - Captures `tool-interleave.png`.
4. **`drawer-navigation.spec.ts`**:
   - Tests operational conversation drawer.
   - Verifies session preview list, active session highlighting, expanded state, and collapsed state.
   - Captures `drawer-expanded.png` and `drawer-collapsed.png`.
5. **`history-replay.spec.ts`**:
   - Tests hydration of historical conversation from `GET /api/v1/chat/sessions/{id}/messages`.
   - Verifies user prompt, thinking summary, past tool cards, and rendered markdown formatting.
   - Captures `history-replay.png`.

---

## 4. Execution Commands

### Run Full E2E Test Suite (Headless)
```bash
cd cortex/spector-cortex
npm run test:e2e
# or
npx playwright test
```

### Run Specific Test Spec
```bash
cd cortex/spector-cortex
npx playwright test e2e/specs/empty-chat.spec.ts
```

### Run on a Specific Project / Device
```bash
cd cortex/spector-cortex
npx playwright test --project=desktop-chromium
npx playwright test --project=mobile-chromium
```

### Update Golden Visual Snapshots (Baseline Update)
```bash
cd cortex/spector-cortex
npx playwright test --update-snapshots
```

### Run Backend Unit & Integration Tests
```bash
# Synapse tests (Tiers 1-3)
mvn test -pl synapse/spector-synapse
```
