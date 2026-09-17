# ADR-0013-RND: Differential Privacy & Edge Anonymization

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

**Document ID**: RND-2026-015  
**Author**: Architecture Working Group (Systems Architecture) & Architecture Working Group (Cognitive Systems)  
**Status**: APPROVED  
**Date**: 2026-08-24  
**Target Repository**: `spectrayan/spector` (`spector-core`, `spector-config`, `spector-memory`)

---

## 1. Abstract
Continuous cognitive perception captures multi-modal sensory observations comprising text, biometric vectors, and contextual metadata. This whitepaper establishes the mathematical and architectural foundations for $(\epsilon, \delta)$-Differential Privacy (DP) on continuous vector representations and zero-dependency deterministic salted pseudonymization on edge runtime clients.

---

## 2. Mathematical Formulations

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

## 3. Synaptic Pathway Architecture
1. **`EdgeAnonymizationRelay`**: Intercepts `RememberSignal` before cognitive indexing, scrubs PII tokens via regex patterns, and replaces them with salted pseudonyms.
2. **`DifferentialPrivacyRelay`**: Applies $L_2$ clipping and Gaussian noise injection to `RememberSignal.vector()` and Laplace noise to salience metrics before long-term storage and cross-device sync.
