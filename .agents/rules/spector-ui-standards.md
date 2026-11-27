---
trigger: model_decision
description: Enforces Spector end-user branding, unified design tokens, layout/Material hygiene, and canvas/WebGL hang prevention when modifying cortex/spector-cortex or UI-facing Synapse APIs.
---

# Spector UI & End-User Surface Standards

Apply these rules whenever editing `cortex/spector-cortex/**` or UI-facing endpoints/payloads in `synapse/spector-synapse/**`.

## 1. End-User Branding (Zero Internal Codenames)
- End-user UI copy, HTML `<title>` tags, route titles, sidebar/header branding, login/signup error copy, onboarding tours, graph watermarks, and audit reason strings must refer strictly to **Spector** (or **Spector Admin**).
- **NEVER** expose internal repository/module codenames (`Cortex`, `Synapse`) to end users.
- Keep public/UI-facing API paths, SSE topics, event types, and node IDs standardized on `spector` (e.g., `/api/v1/events?filter=spector,ingestion`, `spector.*` event types, `spector-0`), preserving backward-compatible aliases only where required.

## 2. Unified Design Tokens & Material M3 Alignment
- Use the single global design-token system in `cortex/spector-cortex/src/styles.scss` (Obsidian-Navy dark / Crisp-Slate light surfaces, `--bg-card`, `--bg-surface`, `--border-subtle`, `--primary`).
- **NEVER** hardcode legacy purple/eggplant hex values (`#7c4dff`, `#bb86fc`, `#c4b5fd`, `#1e1e2e`, `#2a2a3e`, `#ab47bc`) or define competing CSS variable namespaces inside feature components.
- Override `--mat-sys-*` and `--mdc-*` tokens globally in `styles.scss` after `@include mat.theme(...)` so Material cards, sub-cards, tabs, dialogs, menus, and selects inherit the active theme consistently.
- When changing backend telemetry/event DTOs (e.g., `ProjectedPointDto` in `spector-events`), immediately update consuming TypeScript models/services and run `npm run build` in `cortex/spector-cortex`.

## 3. Layout, Header Alignment & Motion
- **No Horizontal-Split Scroll Traps**: Overview/dashboard pages must scroll as a single unified canvas — do not nest `overflow-y: auto; flex: 1; min-height: 0;` scroll regions inside the main page grid.
- **No `mat-button` on Multi-Element Pills**: Angular Material's `mat-button` content projection (`ng-content select="mat-icon:not([iconPositionEnd])"`) reorders trailing chevron icons and breaks block-level avatars onto new lines. Use native `<button type="button">` with `display: inline-flex; align-items: center; line-height: 1;` and `<span>` avatars for header pills (Account/Settings, Namespace Switcher, Command Palette).
- **Compositor-Friendly Transitions**: Never use `transition: all`; explicitly transition compositor properties (`opacity`, `transform`, `background-color`, `border-color`).

## 4. Preventing Chromium `RESULT_CODE_HUNG` on Canvas / WebGL Components
- **No `ResizeObserver` Feedback Loops**: Never combine `grid-auto-rows: minmax(..., auto)` with a padded `ResizeObserver` container that sets `canvas.height = parent.clientHeight` (each callback grows the row by the vertical padding until the renderer hangs). Use fixed grid row heights, `box-sizing: border-box; overflow: hidden;`, clamped canvas dimensions, and a `> 1px` delta guard (`Math.abs(canvas.width - w) > 1 || Math.abs(canvas.height - h) > 1`).
- **No Unconditional `requestAnimationFrame` Loops in `NgZone`**: Use on-demand single-frame redraws (`scheduleDraw()`) triggered by signal/theme/size changes, stop frame scheduling when idle/converged, run continuous WebGL loops outside `NgZone`, and always disconnect `ResizeObserver` instances on component destroy.

## 5. Mandatory Local UI Verification
- `.github/workflows/ci-synapse.yml` runs `npm run build` but does **not** run unit tests. Before committing or opening a PR touching `cortex/spector-cortex` or shared DTOs, always run **both**:
  ```bash
  cd cortex/spector-cortex
  npm run build && npx ng test --watch=false
  ```
