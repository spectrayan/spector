# ADR-0052: Differential Privacy and Edge Anonymization

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-08-24 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---


## 1. Context

Continuous cognitive perception captures multi-modal sensory observations comprising text, biometric vectors, and contextual metadata. This whitepaper establishes the mathematical and architectural foundations for $(\epsilon, \delta)$-Differential Privacy (DP) on continuous vector representations and zero-dependency deterministic salted pseudonymization on edge runtime clients.

---

## 2. Problem Statement

Cognitive memory platforms store extensive autobiographical episodes, identity markers, behavioral telemetry, and relational knowledge graphs. Transmitting or consolidating memories across untrusted edge devices, federated nodes, or cloud analytics introduces major privacy hazards:

1. **Embedding Inversion Attacks**: High-dimensional vector embeddings can be inverted via reconstruction models to recover raw conversational text.
2. **PII and Identifier Leakage**: Graph nodes and edge attributes containing real-world identities can deanonymize users across conversational episodes.
3. **Traceability in Scalar Telemetry**: Unperturbed affective signals (valence, arousal, salience) leak cognitive behavioral signatures across sessions.

## 3. Decision Drivers

- **Formal Mathematical Guarantees**: Enforce strict $(\epsilon, \delta)$-differential privacy bounds on all exported vectors and scalar metrics.
- **Irreversible Entity Pseudonymization**: Edge entity identifiers must be replaced with salted, keyed hashes preventing rainbow-table deanonymization.
- **Bounded Information Loss**: Calibrate noise injection parameters to preserve semantic clustering accuracy and nearest-neighbor search recall while guaranteeing mathematical unobservability.
- **Zero-Allocation Execution**: Differential privacy noise addition must execute in $<15\mu s$ off-heap in `spector-core`.

## 4. Considered Options

### Option 1: Heuristic Attribute Masking & Regex Redaction
- Redact recognized regex patterns (emails, phone numbers, SSNs) and strip names.
- **Verdict**: Rejected. Inadequate against reconstruction attacks on vector embeddings and graph structural linkage attacks.

### Option 2: Heavy Homomorphic Encryption
- Perform all queries and graph traversals entirely in ciphertext using fully homomorphic encryption (FHE).
- **Verdict**: Rejected. 1000x slowdown makes interactive sub-millisecond retrieval impossible.

### Option 3: Gaussian and Laplace Differential Privacy with Salted HMAC Edge Anonymization (Selected)
- Add calibrated Gaussian noise to vector embeddings and Laplace noise to scalar cognitive telemetry in `DifferentialPrivacyKernel`.
- Apply deterministic salted HMAC pseudonymization to graph edges in `EdgeAnonymizer` and `EdgeAnonymizationRelay`.
- **Verdict**: Accepted. Delivers provable $(\epsilon, \delta)$ privacy guarantees with negligible retrieval latency impact.

## 5. Decision Outcome

### Mathematical Formulations & Noise Mechanisms

### 2.1 Gaussian Mechanism for Vector Embeddings
Let $\boldsymbol{v} \in \mathbb{R}^D$ represent an embedding vector. To guarantee $(\epsilon, \delta)$-differential privacy with $L_2$ sensitivity bounded by clipping threshold $C$:
$$\bar{\boldsymbol{v}} = \frac{\boldsymbol{v}}{\max\left(1, \frac{\|\boldsymbol{v}\|_2}{C}\right)}$$
$$\tilde{\boldsymbol{v}} = \bar{\boldsymbol{v}} + \mathcal{N}\left(0, \sigma^2 \mathbf{I}_D\right)$$
where the standard deviation $\sigma$ is calibrated via the analytical Gaussian mechanism bound:
$$\sigma = \frac{C \sqrt{2\ln(1.25/\delta)}}{\epsilon}$$

For standard production defaults ($C = 1.0, \epsilon = 2.0, \delta = 10^{-5}$):
$$\sigma = \frac{1.0 \cdot \sqrt{2\ln(125000)}}{2.0} = \frac{\sqrt{2 \cdot 11.736}}{2.0} \approx 2.422$$

### 2.2 Laplace Mechanism for Scalar Cognitive Metrics
For scalar metrics $s \in [0, 1]$ (such as salience, prediction surprisal, or episodic frame counts) with $L_1$ sensitivity $\Delta_1 = 1.0$:
$$\tilde{s} = s + \text{Laplace}\left(0, \frac{\Delta_1}{\epsilon}\right)$$

### 2.3 Deterministic Salted HMAC Pseudonymization
To allow associative link retrieval across autobiographical episodes while preventing re-identification:
$$\text{Pseudonym}(\text{entity}, \text{type}) = \text{type} + \text{"\_"} + \text{HMAC-SHA256}(\text{entity}, \text{salt})[0..8]$$
Transforms sensitive tokens into deterministic hashed identifiers that persist within the entity's personal memory graph without exposing cleartext PII.

---

### Synaptic Pathway Architecture & Relay Integration

1. **`EdgeAnonymizationRelay`**: Intercepts `RememberSignal` before cognitive indexing, scrubs PII tokens via regex patterns, and replaces them with salted pseudonyms.
2. **`DifferentialPrivacyRelay`**: Applies $L_2$ clipping and Gaussian noise injection to `RememberSignal.vector()` and Laplace noise to salience metrics before long-term storage and cross-device sync.

## 6. Pros and Cons of the Options

### Positive
- **Provable Privacy Guarantees**: Formal mathematical bounds against arbitrary post-processing and side-channel linkage attacks.
- **Preserved Utility**: Calibrated Gaussian noise maintains cosine distance fidelity for top-$k$ recall within acceptable margins.
- **Off-Heap Speed**: SIMD-friendly Gaussian RNG evaluated in off-heap memory segments without garbage collection overhead.

### Negative / Trade-offs
- **Hyperparameter Calibration**: Privacy budget $\epsilon$ must be budgeted across multiple queries to prevent budget exhaustion.
- **Minor Vector Drift**: Noise injection slightly perturbs embedding positions, requiring a slight increase in recall expansion factor.

## 7. Implementation Plan

1. **Kernel Implementation**: Author `DifferentialPrivacyKernel` in `nucleus/spector-core` providing vector and scalar perturbation routines.
2. **Relay & Engine Layer**: Author `DifferentialPrivacyEngine`, `EdgeAnonymizer`, and corresponding Synapse relays in `memory/spector-memory`.
3. **Pipeline Wiring**: Attach privacy relays to external sync and backup pathways to sanitize exported memory bundles.
4. **Validation Suite**: Unit and property tests (`DifferentialPrivacyKernelTest`, `DifferentialPrivacyRelayTest`).

## 8. Code Reference & Verification

All privacy kernels, anonymizers, and relays are verified in the repository:
- **Core Math Kernel**:
  - `nucleus/spector-core/src/main/java/com/spectrayan/spector/core/privacy/DifferentialPrivacyKernel.java`
  - `nucleus/spector-core/src/test/java/com/spectrayan/spector/core/similarity/DifferentialPrivacyKernelTest.java`
- **Memory Privacy Engine & Relays**:
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/privacy/DifferentialPrivacyEngine.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/privacy/EdgeAnonymizer.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/relay/DifferentialPrivacyRelay.java`
  - `memory/spector-memory/src/main/java/com/spectrayan/spector/memory/aisme/relay/EdgeAnonymizationRelay.java`
