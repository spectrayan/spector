# Agentic AI Foundation (AAIF) Project Proposal — Spector

> **Application Form**: Ready-to-submit proposal for `https://github.com/aaif/project-proposals/issues/new?template=project-proposal.yml`  
> **Target Stage**: Sandbox Stage  
> **Target Foundation**: Agentic AI Foundation (AAIF) under The Linux Foundation

---

## Issue Title
```
[Project Proposal] Spector: Cognitive Memory Engine for AI Agents
```

---

## Form Fields & Responses

### Project Name
`Spector`

---

### Project Description
Spector is an open-source cognitive memory engine for autonomous AI agents and multi-agent systems. It provides agents with a durable, stateful memory substrate that is fundamentally different from a stateless vector database or key-value store: traces are stored across working, episodic, semantic, and procedural tiers, scored at recall time under power-law decay, importance, and associative graph relationships, and physically isolated per tenant or agent in on-disk namespaces with zero cross-tenant leakage.

#### Why it is valuable
As autonomous AI agents evolve from single-turn chatbots to long-running, multi-session actors, context degradation and memory loss become the primary failure modes. Stateless vector search returns nearest neighbors regardless of whether an experience occurred five minutes ago or six months ago, suffers from retrieval truncation traps, and cannot consolidate experiences into general rules. Spector provides agents with true cognitive retention: remembering important observations, decaying transient noise, consolidating episodes into durable semantic knowledge, and forming associative links across turns. 

Under the hood, Java Project Panama Foreign Function & Memory (FFM) and the Vector API deliver SIMD scoring with measured near-zero garbage-collection overhead and sub-millisecond in-process recall, requiring zero external database infrastructure. Agents connect through a native, built-in Model Context Protocol (MCP) server, REST/gRPC gateways, Python, TypeScript, and Java SDKs, or directly in-process on the JVM.

#### Origin and History
Spector originated in 2025 as the reference implementation of the open Memory Fundamentals specification (MF-001) developed by the Spectrayan engineering team to address context thrashing, noise accumulation, and memory loss in autonomous coding agents. Over 1,000+ commits and 85+ formal Architecture Decision Records (ADRs), the codebase grew into a modular 27-module Java reactor featuring an off-heap Panama memory kernel (`spector-kernel`), an MCP server (`spector-mcp`), and full multi-agent orchestration gateways. In 2026, the project underwent comprehensive open-source hardening, aligning all public interfaces to Apache 2.0 with DCO 1.1 sign-offs and Linux Foundation meritocratic governance to serve as neutral, shared infrastructure for the global agentic ecosystem.

---

### Alignment with AAIF Mission
The Agentic AI Foundation (AAIF) exists to establish an open, interoperable, and vendor-neutral stack for agentic AI. Today, the foundational layers are taking shape: protocols for tool interactions (MCP), instructions (AGENTS.md), agent execution runtimes (goose), and inter-agent networking (A2A, AgentGateway).

**Spector addresses the missing pillar of this stack: a standardized, vendor-neutral cognitive memory and state substrate.**

Without an open memory engine, developers and enterprises are pushed toward proprietary closed-schema cloud memory silos or fragile DIY vector store wrappers. Spector aligns with AAIF's mission across four core dimensions:
1. **Open Standards First**: Built natively on MCP as an in-process memory server and implementing the open MF-001 memory model.
2. **True Interoperability**: Connects out of the box with any AAIF-compatible runtime (including Goose, OpenClaw, Claude Code, Cursor) and exposes cross-language bindings (Python, TypeScript, Java, and C/FFI).
3. **Democratized Infrastructure**: Operates embedded with zero external infrastructure requirements (no Docker or cloud database required for local developers), while scaling seamlessly to multi-tenant Kubernetes deployments.
4. **Open Governance & Community**: 100% Apache 2.0 licensed, DCO 1.1 compliant, with meritocratic governance based on Linux Foundation guidelines.

---

### Relation to Existing AAIF Projects
Spector directly complements and integrates with existing AAIF hosted projects and specifications:

- **Model Context Protocol (MCP)**: Native integration. Spector includes a dedicated, built-in MCP server (`synapse/spector-mcp`) exposing 16 cognitive memory tools (`memory_remember`, `memory_recall`, `memory_reinforce`, `memory_introspect`, `memory_why_not`, `memory_status`). Any MCP client immediately gains stateful, multi-tier associative memory without glue code.
- **goose**: Direct runtime synergy. Goose agent configurations can declare Spector as an stdio or HTTP MCP extension (`~/.config/goose/config.yaml`), providing local autonomous Goose agents with persistent cross-session episodic memory and knowledge consolidation.
- **AGENTS.md**: Complementary lifecycle. While AGENTS.md defines the static, declarative instructions, roles, and behavioral constraints for agents at repository setup, Spector dynamically records, scores, and retrieves the *runtime experiential traces* generated while agents operate under those AGENTS.md rules.
- **Agent-to-Agent (A2A) & AgentGateway**: Layer separation. A2A and AgentGateway standardize wire transport, routing, and message passing between distributed agents. Spector provides the memory state plane that persists agent context, conversation histories, and shared knowledge bases between message hops.

---

### Example Use Cases and Evidence of Adoption

#### Target Stage
**Sandbox Stage** — filling the critical cognitive memory infrastructure need in the AAIF landscape with rapid community momentum.

#### Real-World Use Cases
1. **Autonomous Software Engineering Agents**: Long-running coding agents (e.g. Goose, Claude Code, Cursor) working in large codebases. Spector maintains the working memory of active tasks, records episodic failure/success traces across debugging cycles, and consolidates domain facts into semantic memory while pruning transient compiler logs via power-law decay.
2. **Multi-Agent Collaboration with Hard Namespace Isolation**: Teams of specialized agents (architect, developer, security auditor) collaborating on enterprise tasks. Spector's physical on-disk namespaces guarantee that sensitive agent context and tenant data have hard hardware and file boundaries, eliminating cross-tenant leakage.
3. **Embedded Local Agent Runtimes**: Developers running local AI agents on workstations without Docker or cloud vector databases. Spector embeds directly inside the runtime process via Java Panama FFM, executing vector search and 6-phase scoring in microseconds off-heap with zero GC stutter.

#### Evidence of Adoption & Momentum
- **Engineering Depth**: 1,000+ commits across 27 reactor modules, with an extensive test suite (85%+ coverage across core kernels).
- **Distribution Channels**:
  - Container images published to GitHub Packages (GHCR): `ghcr.io/spectrayan/spector`
  - Python SDK published on PyPI: `spector-client`
  - TypeScript / Node.js SDK on npm: `@spectrayan/spector-client`
  - Homebrew Tap: `spectrayan/homebrew-spector`
- **Community Contributions**: Active external contributions merged from community developers (e.g., contributor Timothy Kim across diagnostics, provider architectures, and Prometheus observability).

---

### Technical Committee Sponsor (if identified)
*None currently identified* (We welcome TC mentorship and sponsorship from interested Technical Committee members).

---

### GitHub Repository URL
`https://github.com/spectrayan/spector`

---

### License
`Apache License 2.0` (OSI-approved permissive license)

---

### Governance Model
- **Link**: [https://github.com/spectrayan/spector/blob/main/GOVERNANCE.md](https://github.com/spectrayan/spector/blob/main/GOVERNANCE.md)
- **Summary**: Meritocratic, transparent open governance modeled on the Linux Foundation Minimum Viable Governance framework. Features a 4-Tier Contributor Ladder (Contributor → Committer/Reviewer → Maintainer → Technical Steering Committee), lazy consensus for routine engineering (72-hour review window), simple majority for component-level changes, and 2/3 supermajority for architectural decision records (ADRs) and breaking changes. Corporate titles hold no standing; governance is strictly based on sustained technical merit and open community collaboration. Developer Certificate of Origin (DCO 1.1) is enforced on all pull requests.

---

### CI/CD & Release Workflow
- **CI System**: GitHub Actions running on every pull request and push to `main`.
  - Matrix builds across OpenJDK 25 (Ubuntu, macOS, multi-arch x86_64 / aarch64).
  - Strict license header verification (`mvn license:check`) enforcing Apache 2.0 headers.
  - Core module test suites (`mvn test -pl memory/spector-memory,nucleus/spector-kernel`).
  - Strict documentation verification (`mkdocs build --strict`).
  - Mermaid diagram syntax validation.
- **Release Cadence**: SemVer 2.0.0 release train. Release candidates tagged in GitHub, triggering automated builds and multi-platform publishing:
  - Container images to GitHub Container Registry (`ghcr.io/spectrayan/spector`).
  - Python packages to PyPI.
  - TypeScript/npm packages to npm registry.
  - Release notes automatically compiled from conventional commit history and signed with DCO.

---

### Public-Facing Contribution Process for Specifications
- **Specification**: Memory Fundamentals Specification (MF-001) hosted openly at [https://github.com/spectrayan/memory-fundamentals](https://github.com/spectrayan/memory-fundamentals).
- **Contribution Process**: Outlined in `CONTRIBUTING.md` ([https://github.com/spectrayan/spector/blob/main/CONTRIBUTING.md](https://github.com/spectrayan/spector/blob/main/CONTRIBUTING.md)).
  - All specification and architecture proposals must be submitted as Architecture Decision Records (ADRs) using the standard 8-section template (`docs/adr/0000-template.md`).
  - RFCs and ADRs are debated publicly in GitHub Issues and Discussions.
  - Revisions to MF-001 or core scoring algebras require formal TSC review and open community consensus.
  - All commits must include DCO 1.1 sign-off (`git commit -s`).

---

### Publicly Accessible Issue Tracker
`https://github.com/spectrayan/spector/issues`

---

### External Project Dependencies
- **Core Engine Invariant**: `nucleus/spector-core`, `nucleus/spector-cpu`, `memory/spector-memory`, `nucleus/spector-kernel`, and `nucleus/spector-index` have **ZERO third-party external dependencies**. They run purely on standard OpenJDK 25 APIs (`java.lang.foreign`, `jdk.incubator.vector`, `java.lang.ScopedValue`).
- **Outer Gateway / Synapse / SDK Dependencies**:
  - Armeria (Apache 2.0) — High-performance asynchronous HTTP/2, REST, and gRPC gateway.
  - Spring Boot 3.x (Apache 2.0) — Standalone gateway application packaging.
  - SLF4J / Logback (MIT / EPL 1.0) — Structured logging abstraction.
  - Jackson (Apache 2.0) — JSON serialization for MCP stdio protocol frames.
  - RoaringBitmap (Apache 2.0) — Inverted index bitmap operations.
  - Python Client: `requests`, `numpy`, `pydantic` (Apache 2.0 / BSD / MIT).
  - TypeScript Client: standard Fetch API / `ws` (MIT).
- All dependencies are under standard, permissive OSI-approved licenses compatible with Apache 2.0.

---

### Maintainers & Contributors
- **Core Maintainers**:
  - Bharat Joshi ([@sbharatjoshi](https://github.com/sbharatjoshi)) — Spectrayan (Project Lead & Core Maintainer)
- **Active Reviewers & Community Contributors**:
  - Timothy Kim ([@timothytkim](https://github.com/timothytkim)) — Independent / Committer (Vector index diagnostics, kernel docs, Prometheus observability)
  - Additional open-source contributors documented in `ACKNOWLEDGMENTS.md`.
- *Maintainer Diversity*: In accordance with the Sandbox Stage guidelines, we are actively expanding the maintainer base and invite AAIF member organizations to join the Technical Steering Committee and subsystem maintainer seats.

---

### Leadership Team & Decision Process
- **Leadership Structure**:
  - Project Lead: Ecosystem partnerships, charter stewardship, trademark coordination.
  - Technical Steering Committee (TSC): Technical governance, ADR approvals, release sign-offs.
  - Maintainers: Subsystem domain leads with commit/merge authority.
- **Decision Process**: Documented in `GOVERNANCE.md` §4:
  - *Lazy Consensus*: Default for regular pull requests and bug fixes (72-hour window without objection).
  - *Simple Majority (>50%)*: Appointing committers, dependency updates, non-breaking deprecations.
  - *Supermajority (2/3)*: Breaking changes, governance amendments, specification alterations, and TSC appointments.

---

### Roadmap
- **Link**: [https://github.com/spectrayan/spector/blob/main/ROADMAP.md](https://github.com/spectrayan/spector/blob/main/ROADMAP.md)
- **12-Month Key Milestones**:
  - *Q4 2025 / Q1 2026 (Completed)*: Panama FFM off-heap kernel, 128-bit Bloom tag gating, in-process MCP server, sub-millisecond 6-phase scoring scan, multi-tier consolidation.
  - *Q2 2026 (Active)*: Standardized Memory Portability Manifest & Codec (cross-framework agent memory export/import); native Goose MCP agent bundle; streaming agentic chat memory integration.
  - *Q3 2026*: Cell-based Kubernetes clustering; snapshot replication; A2A memory protocol bindings; distributed consensus.
  - *Q4 2026*: Hardware offload extensions (GPU Panama FFM bindings); extended SDKs (Go, Rust); automated continuous cognitive benchmark suite against standard agent workloads.

---

### Security
- **Security Posture**: Documented in `SECURITY.md` ([https://github.com/spectrayan/spector/blob/main/SECURITY.md](https://github.com/spectrayan/spector/blob/main/SECURITY.md)).
- Coordinated Vulnerability Disclosure (CVD) process with private reporting via GitHub Security Advisories and `security@spectrayan.com`.
- 24-hour initial response SLA with automated security triage by the TSC Security Taskforce.
- Static application security testing (SAST) and automated dependency vulnerability scanning via GitHub Dependabot and CodeQL workflows.
- Off-heap memory safety guaranteed by Project Panama `Arena.ofConfined()` / `Arena.ofShared()` bounded lifetimes, preventing memory leaks, buffer overruns, and unaligned reads.
- OpenSSF Best Practices Gold Badge achieved: [https://www.bestpractices.dev/projects/14829](https://www.bestpractices.dev/projects/14829).

---

### Website URL
- Documentation Portal: [https://spectrayan.github.io/spector/](https://spectrayan.github.io/spector/)
- Organization Portal: [https://spectrayan.com](https://spectrayan.com)

---

### Documented Governance Practices (if any)
- Governance: [https://github.com/spectrayan/spector/blob/main/GOVERNANCE.md](https://github.com/spectrayan/spector/blob/main/GOVERNANCE.md)
- Contributing: [https://github.com/spectrayan/spector/blob/main/CONTRIBUTING.md](https://github.com/spectrayan/spector/blob/main/CONTRIBUTING.md)
- Code of Conduct: [https://github.com/spectrayan/spector/blob/main/CODE_OF_CONDUCT.md](https://github.com/spectrayan/spector/blob/main/CODE_OF_CONDUCT.md)
- Security Policy: [https://github.com/spectrayan/spector/blob/main/SECURITY.md](https://github.com/spectrayan/spector/blob/main/SECURITY.md)
- Licensing Notice: [https://github.com/spectrayan/spector/blob/main/NOTICE](https://github.com/spectrayan/spector/blob/main/NOTICE)

---

### Links to Social Media Accounts
- GitHub: [https://github.com/spectrayan](https://github.com/spectrayan)
- LinkedIn: [https://www.linkedin.com/company/spectrayan](https://www.linkedin.com/company/spectrayan)
- X / Twitter: [https://x.com/spectrayan](https://x.com/spectrayan)

---

### Trademark and accounts
- [x] If the project is accepted, I agree to donate all project trademarks and accounts to the AAIF.

---

### Details of Existing Financial Sponsorship
Bootstrapped and self-funded by Spectrayan Inc. No restrictive equity or intellectual property obligations. The project is completely independent and unencumbered.

---

### Infrastructure Needs or Requests
- GitHub Actions CI/CD runner compute (specifically multi-architecture ARM64 / aarch64 Linux and macOS runners to test SIMD vector paths and Panama FFM performance across architectures).
- Domain and DNS hosting support under the Linux Foundation IT infrastructure.
- Community meeting hosting and communication channels (Slack/Discord channels under AAIF umbrella).

---

### Additional Information
Spector was specifically refactored and audited to adhere to the highest standards of Linux Foundation and AAIF hygiene: mechanism-over-analogy documentation, zero corporate-title bias in governance, rigorous DCO 1.1 sign-offs, zero external dependencies in core storage/scoring algorithms, and open specification backing via MF-001. We view Spector as the natural, missing cognitive memory layer for the Agentic AI Foundation.

---

## Contact Information

### Application contact name(s) and email(s)
Bharat Joshi, `bharatjoshi@spectrayan.com`

### Contributing or sponsoring entity signatory information

| Name | Address | Type (e.g., Delaware corporation) | Signatory name and title | Email address |
|:---|:---|:---|:---|:---|
| Spectrayan Inc. | 2261 Market Street STE 86326, San Francisco, CA 94114 | Corporation | Bharat Joshi, Founder | bharatjoshi@spectrayan.com |
