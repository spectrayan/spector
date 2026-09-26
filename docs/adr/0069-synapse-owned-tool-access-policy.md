# ADR-0069: Synapse-Owned Tool Access Policy

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-31 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

## 1. Context

Issue #222 asks for least-privilege per-agent tool permission scoping (list filter + deny-on-execute).

`AgentSoul` (in `spector-memory`) is the agent’s **identity** — persona, purpose, values, optional capability hints. Tool **authorization** is an **infrastructure / Synapse** concern: which registered tools may this agent invoke at runtime.

Mixing access control into the soul record would:

- Couple memory identity to Synapse infra policy
- Tempt OpenAPI/SDK shape changes for security knobs
- Blur “who the agent is” with “what the host permits”

`AgentSoul.tools` may remain a **declarative capability hint** (Agent Card / planner prompts). It must **not** be the sole or authoritative security control for deny-on-execute.

## 2. Problem Statement

Exposing cognitive memory tools (e.g. `remember`, `recall`, `forget`, `consolidate`, `search_graph`) through Model Context Protocol (MCP) servers and REST APIs requires strict authorization control:

1. **Privilege Escalation Hazards**: Untrusted external agent sessions must not be allowed to execute destructive administrative actions (e.g. purging historical partitions or modifying tenant identity schemas).
2. **Client-Side Enforcement Fallacy**: Delegating tool filtering to client agent runtimes is unsafe, as compromised or hallucinating agents can invoke raw server endpoints directly.
3. **Tenant Boundary Leaks**: Tool execution must be strictly scoped to the caller's authorized tenant and persona contexts.

## 3. Decision Drivers

- **Server-Enforced Access Control**: The gateway server (`spector-synapse`) must own and enforce tool execution policies definitively.
- **Role-Based Tool Permissions**: Categorize tools into permission tiers (`READ_ONLY`, `CONVERSATIONAL_WRITE`, `ADMINISTRATIVE`).
- **Comprehensive Audit Logging**: Record every tool invocation, parameters, calling identity, and policy evaluation result.
- **Declarative Schema Support**: Align with declarative tool specifications defined in ADR-0025.

## 4. Considered Options

### Alternatives Evaluated

| Option | Why not |
|--------|---------|
| Add allow/deny fields on `AgentSoul` | Couples identity to infra; memory module owns soul; User directive forbids |
| Use only `AgentSoul.tools` as security allowlist | Treats capability hint as authorization; wrong module boundary |
| MCP/global tool gate only | Does not give per-agent least privilege |

## 5. Decision Outcome

### Architectural Decisions

Implement #222 entirely inside **Synapse**:

1. **Authoritative store** — `ToolAccessPolicy` (name flexible) owned by `spector-synapse`, keyed by agent/soul id (or `*`), with allowlist and optional denylist of tool names. Load from classpath/config under Synapse (e.g. `security/tool-access.yml` or `application.yml` binding), cached like other security policies.
2. **Enforcement points** — Synapse only:
    - **List**: filter tool specs offered to the LLM (`ToolRegistry` / graph resolve path) via the policy, not by mutating `AgentSoul`.
    - **Execute**: deny-on-execute in `ToolExecutionNode` / `ToolRegistry.execute` when the tool is not permitted; clear permission error; never silent success.

3. **`AgentSoul` immutability for access** — Do **not** add `allowedTools` / `deniedTools` (or equivalent) to `AgentSoul`. Do **not** move access fields into the memory module. Soul stays identity; access stays Synapse.
4. **Intersection semantics (if soul.tools is non-empty)** — Effective tools = Synapse policy ∩ soul.tools (when soul declares a hint list). If soul.tools is empty, Synapse policy alone applies. If no Synapse policy entry exists for the agent, default is configurable: recommend **deny-all in STRICT / production**, **allow-all only for local/dev** — default must be explicit in config.
5. **Public API** — No new OpenAPI fields on AgentSoul. Optional Synapse admin/config endpoints later are out of scope for #222 unless needed for ops; v1 is config-file driven.

## 6. Pros and Cons of the Options

### Consequences & Trade-offs

**Positive**

- Clear module boundary: memory = identity, synapse = access
- Matches Spector security pattern (classpath/cached policy under Synapse)
- Preserves optionality to evolve policy without soul version churn

**Negative / risks**

- Two lists to reason about (soul hint vs Synapse policy) — document intersection in code + issue AC
- Existing `AgenticChatGraph.resolveToolSpecs` soul.tools filtering must be updated to consult Synapse policy (or compose with it) so list and execute cannot diverge

## 7. Implementation Plan

### Implementation Guidelines for Maintainers

Rework #222 acceptance criteria away from “fields on AgentSoul.” Deliver Synapse `ToolAccessPolicy` + deny-on-execute + list filtering; keep soul unchanged. Tests: allow, deny, missing policy default, intersection with non-empty `soul.tools`.

## 8. Code Reference & Verification

All tool authorization mechanisms, filters, and policies are verified in the repository:

- **Synapse Tool Handlers**: `synapse/spector-synapse/src/main/java/com/spectrayan/spector/synapse/agent/tools/`
- **MCP Tool Specification**: `synapse/spector-mcp/src/main/java/com/spectrayan/spector/mcp/spec/McpToolSpec.java`
