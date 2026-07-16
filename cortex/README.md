# Cortex UI Layer (`/cortex`)

This directory houses the web application for the **Cortex Dashboard** — a visual interface for exploring, configuring, and monitoring the Spector cognitive memory engine.

## Modules

* **[`spector-cortex`](/cortex/spector-cortex)**: An Angular 22 standalone application featuring real-time neural graphs, Hebbian edge weight visualizations, cognitive profile tuning, and memory recall testing tools.

## Architecture

* **Framework**: Angular 22 with Signal-based state management and material design component tokens.
* **Telemetry**: Connects via Server-Sent Events (SSE) and HTTP REST APIs exposed by the Synapse backend on port 7070.
* **Visualizations**: Renders dynamic memory graphs and coactivation pathways in three dimensions using Canvas and WebGL (THREE.js).
