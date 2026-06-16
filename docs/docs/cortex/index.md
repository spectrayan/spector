---
title: "🧬 Spector Cortex — Neural Dashboard"
description: "Real-time visualization dashboard for Spector's cognitive memory engine — neural graphs, vector spaces, memory heatmaps, and live cognitive metrics."
---

# 🧬 Spector Cortex — Neural Dashboard

!!! quote "The Vision"
    What if you could **watch your AI's brain think?** Spector Cortex is a real-time neural dashboard that visualizes the cognitive memory engine — from SIMD lanes firing to Hebbian edges strengthening to memories decaying along the Ebbinghaus curve. It's the difference between a black box and a living brain.

---

## Visual Showcase

### 🎥 Neural Graph in Action

<video controls width="100%" poster="spector-cortex-graph.png">
  <source src="spector-cortex-neural-graph.mp4" type="video/mp4">
  Your browser does not support the video tag.
</video>

*The 3D neural graph explorer — fly through the cognitive galaxy, explore Hebbian associations, temporal chains, and entity relationships as glowing star constellations.*

---

### 📊 Dashboard — 12+ Live Cognitive Panels

![Spector Cortex Dashboard](spector-cortex-dashboard.png)

Real-time scoring pipeline, SIMD lanes, decay curves, vector space, Hebbian graph, cognitive profiles, and live metrics — all rendered in a single interactive dashboard.

---

### 🌌 Graph Explorer — 3D Neural Galaxy

![Spector Graph Explorer](spector-cortex-graph.png)

Interactive 3D graph with glowing star nodes, Hebbian/temporal/entity edges, fly-to navigation, click-to-explore, and real-time topology stats. Toggle edge types independently with the toolbar.

---

### 🧠 Memory Table — Browse & Manage Memories

![Spector Memory Table](spector-cortex-memory-table.png)

Full CRUD with tier filtering, importance bars, valence indicators, synaptic tags, recall counts, tombstone ratios, and bulk actions. Sortable columns, pagination, and tier summary cards.

---

### 🔬 Memory Detail — Deep Cognitive Inspection

![Spector Memory Detail](spector-memory-detail.png)

Identity card, cognitive state (importance/valence/arousal gauges), synaptic tags, and full relationship graph showing Hebbian associations (weighted), temporal chains (directional), and entity links.

---

## Views

The application includes **5 main views**:

| View | Description |
|:-----|:------------|
| **Dashboard** | 12+ live cognitive panels in a responsive 3-column grid — neural graph, vector space, scoring pipeline, SIMD, memory heatmap, decay curve, and more |
| **Cognitive Query** | Real-time recall against the memory subsystem with score breakdowns, tier badges, and synaptic tags |
| **Memory Table** | Paginated table of all stored memories with sorting, filtering, and CRUD operations |
| **Graph Explorer** | Full-screen 3D neural galaxy with fly-to navigation, edge filtering, and topology stats |
| **Memory Detail** | Deep inspection of individual memories with cognitive state, relationships, and actions |

---

## Dashboard Panels

The dashboard is built around **12+ panels**, each visualizing a different aspect of the cognitive pipeline:

| Panel | What It Shows |
|:------|:--------------|
| **Neural Graph** | 200-node cognitive network with Hebbian, temporal, and entity edges — particles flow along connections during query spreading activation |
| **Vector Space** | 300-point PCA-projected embedding space with query dot and nearest-neighbor lines |
| **Scoring Pipeline** | The 6-phase cognitive scoring funnel — from total records → tombstone → tags → valence → decay → distance → final top-K |
| **Live Metrics** | Real-time recall/remember/reinforce/forget rates plotted as multi-line chart |
| **Cognitive Profile** | 6-axis radar showing current thalamic modulation parameters with animated profile transitions |
| **SIMD & Hardware** | 16-lane SIMD register visualization with intensity and utilization color-coded bars |
| **Memory Heatmap** | Off-heap memory segment utilization across all 4 tier stores + graph structures |
| **Decay Curve** | Ebbinghaus forgetting curve vs. LTP reconsolidation — shows how recall events boost retention |
| **Query History** | Chronological query traces with profile, latency, and augmented result counts |
| **Zeigarnik Effect** | Unresolved memory count and cognitive tension percentage |
| **Habituation** | Inhibition of Return, semantic satiation, and habituation penalty gauges — the anti-filter-bubble mechanisms |
| **Query Input** | Submit queries to see the full pipeline execute in real time |

---

## Panel Highlights

### Neural Graph

The centerpiece of the dashboard — a 3D graph with 200 nodes organized by memory tier:

- **Node colors**: Working (amber), Episodic (green), Semantic (blue), Procedural (purple)
- **Node radius**: Proportional to tier (Working = inner, Procedural = outer shell)
- **3 edge types**:
    - **Hebbian** — solid white lines (co-activation strength)
    - **Temporal** — dashed cyan lines (causal/temporal chains)
    - **Entity** — solid gold lines (entity-relationship knowledge)

**Interactive features:**

- [x] **Layer toggles** — show/hide each edge type independently
- [x] **Query traversal particles** — colored spheres flow along edges during spreading activation
- [x] **Profile visual transforms** — HYPERFOCUS (tunnel vision), PARANOID (red shift), DIVERGENT (rainbow shimmer)
- [x] **Consolidation animation** — edges dim and prune when `reflect()` fires
- [x] **Mouse interaction** — camera follows mouse position for parallax effect

### Vector Space

A point cloud of 300 memory embeddings projected into 3D via PCA:

- Points are colored by tier and sized by importance
- **Query dot** — when a query fires, a white pulsing sphere appears at the query vector position
- **Nearest-neighbor lines** — 5 translucent lines connect the query dot to its closest memories
- **Layer controls** — 4 toggleable layers: Query, k-NN, Axes, Labels

### Scoring Pipeline

Animated horizontal funnel showing the 6-phase cognitive scoring pipeline:

| Phase | Description |
|:------|:------------|
| Total Records | Starting record count |
| After Tombstone | Tombstone-filtered records |
| After Tag Gate | Synaptic tag bloom filter pass |
| After Valence | Emotional valence range filter |
| After Decay | Temporal decay threshold |
| Vector Distance | L2 distance scoring |
| Final Top-K | Final result set |

Each bar animates smoothly to new values and shows the delta percentage from the previous phase.

### Decay Curve

Visualizes the Ebbinghaus forgetting curve alongside LTP reconsolidation:

- **Red dashed line** — raw Ebbinghaus exponential decay (no intervention)
- **Primary solid line** — actual retention with LTP reconsolidation bumps from recall events
- **Filled area** — shows the retention gain from the reconsolidation system

---

## Roadmap

- [x] **Cluster view** — multi-node visualization for distributed deployments
- [x] **GPU acceleration panel** — CUDA kernel execution timeline visualization
- [x] **Memory diff view** — before/after comparison of consolidation cycles
- [x] **Vector Space layer controls** — Query dot, k-NN, Axes grid, Labels toggles
- [ ] **Replay mode** — record and replay cognitive sessions for debugging

