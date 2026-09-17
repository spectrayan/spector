# ADR-0023-RND: Cognitive Continuity Layer & Decoded Mind Streams

| Field | Value |
|:---|:---|
| **Status** | Withdrawn |
| **Date** | 2026-09-10 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

**Spec Identifier**: RND-2026-023  
**Category**: Speculative Cognitive Architecture / BCI-Adjacent Continuity  
**Authors**: Technical Lead  
**Status**: Draft — **Science-fiction until proven** (explicitly non-product, non-clinical)  
**Date**: 2026-09-10  
**Related**: Spector memory kernel / AISME; prior Neuralink×Spector fit assessment (chat, 2026-09-10)

---

## 0. Classification & Anti-Claims (read first)

This document is an **internal speculative ladder**, not a partnership pitch, medical claim, or product roadmap commitment.

**We do NOT claim that Spector or Neuralink (or any public BCI) can today:**
- Read or dump autobiographical “brain memories” as biological engrams
- Transfer thoughts or identity between brains
- Write memories back into neural tissue
- Act as an on-implant or FDA device memory layer
- Replace electrophysiology stores (NWB / DAQ lakes)

**What this *does* explore:** storing and reactivating **decoded cognitive artifacts** (symbols, latents, episodes) derived from a BCI (or any high-bandwidth intent channel), using Spector as a **software continuity layer** — with a far-horizon research ladder toward hypothetical write-back that remains unsolved science.

---

## 1. Motivation

Bharat’s framing: if a BCI can surface what a brain is “thinking” (intent / content), can Spector **store that state** and later **transfer** it?

Public BCIs (e.g. Neuralink Telepathy / PRIME materials) today primarily decode **movement or communication intention** into actions (cursor, clicks, typing; speech and vision as future/BDD directions). That is **intent I/O**, not mind upload.

Still, the strategic question is legitimate as a **north star**:

> Can a cognitive memory engine provide continuity for a stream of decoded mind content — across sessions, devices, agents, and eventually (if science allows) back toward the biological substrate?

Spector’s existing strengths (off-heap SIMD engrams, ACT-R-ish activation/decay, graph/Hebbian association, AISME kernels, import/export) map cleanly to the **middle** of that pipeline — not to raw spike storage or biological write-back.

---

## 2. Conceptual Pipeline

```
brain
  │
  ▼
BCI read (sparse neural sampling)
  │
  ▼
decoder  ──►  intent / text / speech hyp. / latent z_t
  │
  ▼
┌─────────────────────────────────────────┐
│  Spector Cognitive Continuity Layer     │
│  episodes · latents · graph · decay     │
│  activation · export / import           │
└─────────────────────────────────────────┘
  │
  ├──► software agent / co-pilot / other Spector store   ✅ near-term
  │
  └──► decoder⁻¹ / stim policy → brain                   ❓ far-horizon
```

| Hop | Status |
|-----|--------|
| Brain → decoder → symbols/latents | Partial today (intent/comms); content richness TBD by decoder science |
| Symbols/latents → Spector store | **Buildable** (Spector’s lane) |
| Spector → software agent / restore context | **Buildable** |
| Spector → brain (memory write-back) | **Unsolved**; requires stimulation of memory circuits + safety + ethics + regulation |

**Transfer** therefore splits into two meanings:
1. **Software transfer** — export/import a continuity store between devices/agents. Feasible.
2. **Biological transfer** — induce the same memory in neural tissue. Not available; do not market.

---

## 3. What We Would Actually Store

Not “the brain.” Store **versioned cognitive artifacts**:

| Artifact | Description | Spector fit |
|----------|-------------|-------------|
| **Event tokens** | Decoded commands, typed text, speech hypotheses, app focus | Engrams + BM25/HNSW |
| **Latent frames** `z_t` | Fixed-dim vectors from a named decoder model + model hash | Vector engrams + SIMD similarity |
| **Episodes** | Contiguous segments (change-point / BOCPD on summary metrics) | Episode graph + AISME BOCPD |
| **Associations** | Co-activation edges between episodes/entities | Hebbian / bridge graph |
| **Affective tags** | Arousal/valence if available from app telemetry (not raw LFP) | Valence / neuromod *as agent policy* |
| **Provenance** | Decoder id, channel montage hash, consent scope, retention class | Required metadata — non-negotiable |

**Invariant:** raw kHz×channel electrophysiology stays in a proper neuro data lake (NWB etc.). Spector holds **summaries and decoded products** only, unless a future specialized binary layout is explicitly designed for binned features (still not “memories of the brain”).

---

## 4. Spector Mapping (near-term)

Reuse, don’t reinvent:

- **Kernel / engrams** — off-heap records for episodes + latents  
- **Activation / decay / cognitive mass** — which continuity traces stay “alive”  
- **HNSW + BM25** — recall by similarity and lexical content  
- **Graph** — associative walk (“what was I doing when…”)  
- **AISME** — free-energy / surprise / change-point as *software* segmentation & salience (metaphorical vs biology)  
- **Import/export** (see existing memory import/export ADRs) — software “transfer”  
- **Synapse** — on-device or user-sovereign deployment profile

**New thin pieces (if we prototype):**
1. `DecodedMindEvent` schema (JSON/Avro) — no medical device types  
2. Adapter: intent/HID/text stream → remember pipeline  
3. Continuity export bundle (encrypted, user-keyed)  
4. Explicit **non-clinical** packaging + anti-claim banner in docs

---

## 5. Ten-Year Ladder (phases)

Phases are **gates**, not calendar promises. Each gate requires evidence before the next is funded as more than paper RnD.

### Phase A — Intent continuity log (0–18 months) · **Engineering**
- Ingest decoded events only (synthetic + public BCI *feature* datasets, or assistive HID streams).
- Spector stores episodes; user can “resume context.”
- **Success:** measurable assistive UX gain vs key-value prefs; zero neural PHI in multi-tenant cloud by default (on-device first).
- **Exit anti-pattern:** any marketing that says “brain memory backup.”

### Phase B — Latent mind-stream store (1–3 years) · **Research + product-adjacent**
- Persist decoder latents `z_t` with model provenance; reactivation = feed latents to *software* agents (not brains).
- Study stability: does the same “thought neighborhood” reappear under decoder drift?
- **Success:** latent neighborhoods remain meaningful across decoder versions via adapters; export/import works across machines.
- **Blocker:** decoder drift, non-identifiability of latents, consent for continuous capture.

### Phase C — Cross-agent / cross-device continuity (2–5 years) · **Platform**
- Transfer continuity bundles between Spector instances (user-controlled).
- Optional: “digital twin” agent that speaks/acts with user’s stored prefs + episodic context.
- **Success:** user recognizes continuity; revocation and selective amnesia work.
- **Still not:** biological write-back.

### Phase D — Hypothetical write-back research (5–10+ years) · **Science fiction until proven**
- Only with academic/clinical partners: can patterned stimulation (or future write BCIs) bias recall or percepts in a controlled task?
- Spector’s role would be **policy + content sequencing** for stimulation recipes derived from stored latents — never unsupervised “memory injection.”
- **Hard blockers:** inverse problem (latent → safe stim), specificity of memory circuits, adverse effects, ethics, FDA/device pathway, irreversibility.
- **Default posture:** do not staff this phase as product engineering; revisit only if peer-reviewed write primitives exist.

---

## 6. Threats, Ethics, and Governance

| Risk | Mitigation |
|------|------------|
| Overclaim / hype | Section 0 anti-claims; legal review of all external wording |
| PHI / neural data as sensitive biometric | On-device default; encryption; retention limits; no secondary sale |
| Coercion / employer access to “mind logs” | User-held keys; no silent cloud replica |
| Identity harm from bad write-back (future) | Human-in-the-loop; reversible protocols only; IRB |
| Decoder bias encoded as “self” | Provenance + user edit/delete of episodes |

Treat continuous decoded streams as **more sensitive than chat logs** even when not clinically labeled.

---

## 7. What Success Looks Like (honest)

**Near-term win:** Spector becomes the best **cognitive continuity store for high-bandwidth human intent channels** (BCI decode, AAC, wearable agents) — software transfer of context and identity *artifacts*.

**Long-term dream (unproven):** those artifacts become inputs to safe, consented write pathways. That dream does not authorize present-tense claims.

---

## 8. Recommended Immediate Actions

1. Accept this doc as **north-star speculative** under Jarvis ownership; status remains Draft.  
2. If prototyping: Phase A only — decoded-event schema + on-device Synapse profile + synthetic streams.  
3. Partner path: assistive-tech / AAC / BCI-*app* vendors — **not** implant OEMs as first call.  
4. Do **not** open a Spector epic titled “Neuralink integration” without a signed research boundary note.

---

## 9. References (public orientation)

- Neuralink Technology / PRIME materials — https://neuralink.com/technology/  
- ClinicalTrials.gov PRIME NCT06429735  
- Musk & Neuralink 2019 JMIR research platform paper (wired, animal)  
- NWB — https://nwb.org/  
- Industry motor BCI decode literature (BrainGate lineage) — intent features, not memory engrams  

---

## 10. One-Line Summary

**Spector can be the continuity disk for decoded mind *streams*; it cannot yet be — and must not claim to be — the transferable substrate of a biological mind.**
