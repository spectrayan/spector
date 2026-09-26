# Security Assurance Case & Threat Model

> **Repository:** `spectrayan/spector`  
> **Status:** Active  
> **Standards Alignment:** OpenSSF Best Practices (Gold/Silver Criteria), Linux Foundation Security Verification

---

## 1. Executive Summary

This document presents the formal **Security Assurance Case** for Spector, justifying how and why its security requirements are satisfied. The assurance argument is structured across four pillars:

1. Formal Threat Model and Attacker Personas
2. Rigorous Trust Boundary Identification
3. Architectural Verification of Secure Design Principles
4. Evidence-based Mitigation of Common Implementation Security Weaknesses (CWEs)

---

## 2. Threat Model & Attacker Personas

Spector operates as a cognitive memory engine and Model Context Protocol (MCP) server for autonomous AI agents. The threat model addresses five primary adversary profiles:

| Attacker Profile | Objective | Attack Vector | System Defense |
|:---|:---|:---|:---|
| **Malicious Multi-Tenant Agent** | Read or corrupt another agent's episodic or semantic memory | Transmitting malicious namespace identifiers or cross-namespace queries | Physical filesystem directory isolation, strict path canonicalization, per-tenant AES-256-GCM encryption keys |
| **Network Eavesdropper / MitM** | Intercept or manipulate memories or API tokens in flight | Sniffing or altering traffic on gRPC, REST, or cluster replication ports | Mandatory TLS 1.2/1.3 with Perfect Forward Secrecy (PFS) and mutual TLS (mTLS) for inter-node replication |
| **Data Exfiltrator / Cold-Disk Thief** | Extract confidential text from stored memory files | Direct access to underlying disk, volume backups, or storage snapshots | AES-256-GCM payload encryption for raw text (`text.dat`) and WAL, HMAC-SHA256 blind indexing for synaptic tags |
| **Denial of Service (DoS) Adversary** | Exhaust CPU, native memory, or JVM threads via complex queries | Flooding large vector scans, graph explosions, or large malformed batches | Inline 128-bit Bloom filter pre-gates, recall visit budgets, memory slab allocation ceilings, bounded task queues |
| **Binary Tampering / Corruptor** | Inject crafted binary files to induce memory corruption or arbitrary execution | Providing malformed `.bundle` storage files or import archives | Custom binary formats with magic byte checks, format version allowlists, and hardware-accelerated CRC32C checksums |

---

## 3. Trust Boundaries

Spector establishes five distinct, non-overlapping trust boundaries:

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Untrusted Zone] External Clients, Agents, Network Ingress             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Boundary 1: TLS 1.3 / API Key Gate
┌───────────────────────────────────▼────────────────────────────────────┐
│ [Edge Gateway] Armeria REST / gRPC / MCP Transport                     │
│ - Request schema validation, rate-limiting, constant-time auth        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Boundary 2: Process & Sandbox Boundary
┌───────────────────────────────────▼────────────────────────────────────┐
│ [Cognitive Engine] Spector Memory Core                                 │
│ - Pathway routing, 6-phase scoring, Bloom filters, visit budgets       │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ Boundary 3: Tenant Boundary     │ Boundary 4: Memory API
┌───────────────────▼────────────────┐   ┌────────────▼──────────────────┐
│ [On-Disk Storage] Namespace Jails  │   │ [Native Off-Heap] Panama FFM  │
│ - Separate directories, per-tenant │   │ - Managed Arenas, 64-byte     │
│   AES-256-GCM encryption keys      │   │   aligned segments, bounds    │
└────────────────────────────────────┘   └───────────────────────────────┘
                    │ Boundary 5: Host Infrastructure
┌───────────────────▼────────────────────────────────────────────────────┐
│ [Host System] OS Page Cache, File Permissions (0600), Volume Storage   │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Boundary 1: Network to Gateway**: All inbound network traffic is untrusted. Access requires authenticated TLS 1.2/1.3 with constant-time API key or OAuth2 JWT evaluation.
2. **Boundary 2: Client to MCP Memory Server**: AI agent tool requests cross a process boundary via stdio or streamable HTTP. All inputs are strictly checked against declarative JSON Schemas before reaching domain logic.
3. **Boundary 3: Tenant Isolation Boundary**: Different tenants and agent personas never share file descriptors or memory segments. Storage directories are physically partitioned by tenant, forbidding cross-directory traversal.
4. **Boundary 4: JVM to Native Off-Heap Slabs**: Memory-mapped binary slabs are managed through OpenJDK 25 Project Panama `Arena.ofConfined()` and `Arena.ofShared()` with hardware-enforced spatial and temporal bounds checks.
5. **Boundary 5: Application to Host Storage**: Relies on POSIX file permissions (`chmod 0600`) and operator-enforced full-disk or cloud volume encryption (LUKS, BitLocker, EBS) to protect raw memory-mapped vector slabs.

---

## 4. Application of Secure Design Principles

Spector's architecture demonstrates adherence to foundational secure design principles:

- **Defense in Depth**: Memory recall incorporates multiple gating layers: (1) live tombstone bit check, (2) 128-bit Bloom tag containment, (3) valence range filter, (4) SIMD scoring, and (5) recall visit budgets. A failure in any single layer does not compromise system security or leak non-admitted memories.
- **Least Privilege**: The core engine kernel executes without elevated privileges. Agent tokens and API keys are scoped strictly to individual namespaces.
- **Fail-Safe / Fail-Closed Defaults**: Binary bundle decoders halt with an error if format version magic or CRC32C checksums fail. Network gateways default to `FailClosedAuthenticationEntryPoint` on any auth error.
- **Complete Mediation**: Every memory read, write, or reinforce operation is mediated by namespace access guards and permission filters.
- **Economy of Mechanism**: The core engine kernel maintains **zero third-party dependencies** (no Spring, Netty, Jackson, or Guava in `spector-core`, `spector-kernel`, `spector-memory`), eliminating supply chain vulnerabilities and third-party transitive dependencies from the critical scoring path.

---

## 5. Countering Common Implementation Security Weaknesses

| Weakness & CWE | Concrete Risk | Spector Countermeasure & Implementation |
|:---|:---|:---|
| **Buffer Overflows & Memory Corruption (CWE-119, CWE-416)** | Native off-heap memory corruption on vector scoring hot paths | Built on Java 25 Project Panama Foreign Function & Memory (FFM). Uses bounded `MemorySegment` and managed `Arena` lifecycles that enforce JVM-level spatial and temporal bounds checks, throwing `IndexOutOfBoundsException` rather than memory faults. |
| **Path Traversal & Arbitrary File Access (CWE-22)** | Malicious namespace names escaping the tenant root (`../../etc/passwd`) | Strict namespace validation enforcing alphanumeric allowlists (`^[a-zA-Z0-9_-]+$`) combined with `Path.normalize()` and canonical root prefix checks before opening file channels. |
| **Insecure Deserialization (CWE-502)** | Malicious serialized Java objects executing arbitrary code | Java native serialization (`Serializable`) is completely banned. All persistence uses custom binary layouts with 4-byte magic headers, format version allowlists, and CRC32C integrity checksums. |
| **Command & SQL Injection (CWE-78, CWE-89)** | Unsanitized input from agent tools executing host commands or queries | Zero execution of shell or eval commands. All MCP tools and REST endpoints accept typed, schema-validated JSON records with parameterized input handling. |
| **Denial of Service / Algorithmic Complexity (CWE-400)** | Pathological graph queries or vector storms exhausting CPU/RAM | Inline 128-bit Bloom filters eliminate >99% of non-matching records before SIMD vector math; recall visit budgets limit graph traversal depth; off-heap memory slabs enforce bounded allocation ceilings. |
| **Timing Attacks (CWE-208)** | Cryptographic side-channel leaking authentication keys via comparison time | API keys and security tokens are compared strictly using constant-time string and byte equality algorithms (`MessageDigest.isEqual`). |

---

## 6. Verification and Evidence

- **Static Verification**: Automated GitHub CodeQL analysis (`queries: security-extended`) executes on every commit and PR.
- **Dynamic Verification**: `jqwik` property-based testing suites execute thousands of randomized invariants on data structures and codecs.
- **Memory Safety Verification**: `PanamaMemoryDetector` continuously asserts off-heap arena lifecycles and detects native leaks during test execution.
- **Vulnerability Response**: Formal Coordinated Vulnerability Disclosure process codified in [SECURITY.md](https://github.com/spectrayan/spector/blob/main/SECURITY.md) with defined response SLAs.
