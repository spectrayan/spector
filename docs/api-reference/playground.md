---
title: "⚡ Interactive REST API Playground"
description: "Interactive OpenAPI 3.1 explorer and testing sandbox for Spector Cognitive Memory & Search REST endpoints."
---

# ⚡ Interactive REST API Playground

> **Explore, test, and generate code for every Spector REST endpoint directly from your browser.**

---

<div class="grid cards" markdown>

-   :material-play-box-multiple: **Live Execution Sandbox**

    ---

    Test live requests against your running Spector daemon (`http://localhost:7070`).

    [Open Fullscreen Explorer ↗](../scalar-playground.html){ .md-button .md-button--primary target="_blank" }

-   :material-code-tags-check: **Zero-Setup Code Generation**

    ---

    Browse schemas, models, and generate dynamic **cURL**, **Python**, **TypeScript**, and **Java** snippets 100% offline.

</div>

!!! info "💡 Local Server Sandbox Guidance"
    - **Offline Browsing & Code Generation**: Inspecting parameters, request/response models, and generating client code operates completely client-side in your browser without requiring a running server.
    - **Live Endpoint Execution ("Test Request")**: When clicking **Send** or **Test Request**, your browser dispatches an HTTP request directly to your configured server (default: `http://localhost:7070`). Ensure your Spector daemon or Docker container is active (`docker run -p 7070:7070 spectrayan/spector` or `npx -y @spectrayan/spector mcp`).

---

## 🚀 Interactive Explorer

<iframe
  src="../scalar-playground.html"
  title="Spector Interactive REST API Explorer"
  style="width: 100%; height: 85vh; border: 1px solid var(--md-default-fg-color--lightest); border-radius: 8px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);"
  loading="lazy">
</iframe>
