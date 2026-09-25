# Security Policy

The Spector project takes security vulnerabilities seriously. We appreciate the responsible disclosure of vulnerabilities by the security community and are committed to addressing reported issues promptly and transparently following Linux Foundation / OpenSSF Coordinated Vulnerability Disclosure (CVD) best practices.

> For our formal threat model, trust boundaries, secure design principles, and CWE countermeasure justifications, see our [Security Assurance Case](docs/architecture/security-assurance.md).

---

## Supported Versions

Only the latest active minor release receives security fixes and backports:

| Version | Supported          | Status |
|:---|:---:|:---|
| `0.1.x` | :white_check_mark: | Current Active Development |
| `< 0.1.0` | :x: | End of Life |

---

## Reporting a Vulnerability

> [!CAUTION]
> **Please do NOT report security vulnerabilities through public GitHub issues, discussions, or pull requests.**

### Preferred Channel: GitHub Private Vulnerability Reporting (PVR)

We recommend using GitHub's **Private Vulnerability Reporting**:
1. Navigate to the [Spector Security Advisories](https://github.com/spectrayan/spector/security/advisories) tab.
2. Click **"Report a vulnerability"**.
3. Fill out the disclosure form detailing reproduction steps, affected modules, and impact.
4. This creates a confidential advisory draft accessible only to the Spector maintainers and the reporter.

### Alternative Channel: Encrypted Email

If you cannot use GitHub PVR, send your report to:  
📧 **security@spectrayan.com**

Please include in your report:
- Affected module(s) and version(s)
- Detailed description of the vulnerability and attack vector
- Minimal reproducible example or proof-of-concept (PoC)
- Potential impact assessment (confidentiality, integrity, availability)
- Any proposed remediation or patch

---

## Response Timeline & Severity SLAs

We adhere to the following coordinated disclosure service levels based on CVSS v3.1 base scores:

| Severity | CVSS v3.1 Range | Acknowledgment SLA | Target Fix Window |
|:---|:---:|:---:|:---:|
| **Critical** | 9.0 – 10.0 | Within 24 hours | 7 calendar days |
| **High** | 7.0 – 8.9 | Within 48 hours | 14 calendar days |
| **Medium** | 4.0 – 6.9 | Within 48 hours | 30 calendar days |
| **Low** | 0.1 – 3.9 | Within 5 business days | Next scheduled release |

---

## Coordinated Disclosure Process

1. **Acknowledgment**: The maintainers confirm receipt and assign a tracking coordinator within the SLA window.
2. **Investigation & Triage**: Maintainers validate the reproduction in a private security fork, assess severity, and assign a CVE identifier if applicable.
3. **Remediation**: A candidate patch is prepared and shared privately with the reporter for validation.
4. **Advisory & Release**: A coordinated release is published alongside a GitHub Security Advisory (GHSA). The reporter is credited in the advisory unless they request anonymity.

## Security Guarantees & Non-Guarantees (Security Requirements)

### What Users CAN Expect
- **Physical Tenant Isolation**: Separate on-disk namespaces guarantee zero cross-tenant memory, graph, or engram leakage.
- **At-Rest Confidentiality**: User text payloads, write-ahead logs (WAL), and entity metadata are encrypted using AES-256-GCM with per-tenant keys.
- **Memory Safety**: Off-heap native memory operations in Java 25 Panama FFM use strictly bounded arenas that throw exceptions rather than allowing buffer overflows or use-after-free corruption.
- **Timing Attack Resistance**: API key validation and HMAC blind index checks execute via constant-time comparisons.
- **Supply Chain Integrity**: Every commit is signed with DCO 1.1; container release digests are pinned; dependencies are scanned continuously with CodeQL and Dependabot.

### What Users CANNOT Expect (Operator Responsibilities)
- **Application-Layer Vector Decryption**: Vector embedding slabs (`.bundle` files) are memory-mapped for microsecond SIMD search and are not encrypted at the application layer. Operators **must** enable full-disk or volume-level encryption (LUKS, BitLocker, or cloud volume encryption) and enforce strict filesystem permissions (`chmod 0600`) to protect raw vector data.
- **Implicit Network Perimeter Security**: While TLS is supported, internal ports must not be exposed directly to the public internet without mutual TLS (mTLS), API key authentication, or an authenticating reverse proxy / API gateway.
- **Protection Against Host Compromise**: Spector cannot defend against attacks if the host operating system, JVM runtime, or a root-privileged host user is compromised.

---

## Security Best Practices for Deployments

- **Memory-Mapped Files**: Secure off-heap slab files (`.spector/memory`) with restricted filesystem permissions (`chmod 0600`) to prevent unauthorized cross-process memory inspection.
- **Network Boundaries**: Do not expose internal gRPC or REST ports (`8080`, `9090`) to the public internet without mutual TLS (mTLS) or an authenticating reverse proxy / API gateway.
- **JVM Runtime Flags**: Run production instances on verified OpenJDK 25 runtimes with bounded container memory flags (`-XX:MaxRAMPercentage`) to guard against native off-heap memory exhaustion.
- **Dependency Hygiene**: All container and application images should scan CycloneDX SBOM manifests against current vulnerability databases.
