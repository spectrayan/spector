/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { CortexStateService } from '../../core/services/cortex-state.service';

@Component({
  selector: 'cortex-playground',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="playground-shell">
      <header class="playground-header">
        <div class="playground-brand">
          <div class="brand-title-row">
            <h1 class="playground-title">Cognitive Playground</h1>
            <span class="exp-badge">Experimental Sandbox</span>
          </div>
          <p class="playground-subtitle">
            Interactive memory console, agent chat harness, and persona calibration scoped to your active namespace.
          </p>
        </div>

        <div class="playground-controls">
          <div class="namespace-pill" data-testid="playground-namespace-badge" title="Active Namespace">
            <mat-icon class="ns-icon">dns</mat-icon>
            <span class="ns-label">Namespace:</span>
            <code class="ns-value">{{ cortexState.currentNamespace() }}</code>
          </div>

          <nav class="playground-tabs" aria-label="Playground sections">
            <a
              routerLink="console"
              routerLinkActive="active"
              class="playground-tab"
              data-testid="tab-playground-console"
            >
              <mat-icon class="tab-icon">terminal</mat-icon>
              <span>Remember / Recall Console</span>
            </a>
            <a
              routerLink="chat"
              routerLinkActive="active"
              class="playground-tab"
              data-testid="tab-playground-chat"
            >
              <mat-icon class="tab-icon">forum</mat-icon>
              <span>Agent Chat</span>
            </a>
            <a
              routerLink="persona"
              routerLinkActive="active"
              class="playground-tab"
              data-testid="tab-playground-persona"
            >
              <mat-icon class="tab-icon">psychology</mat-icon>
              <span>Persona &amp; Soul</span>
            </a>
          </nav>
        </div>
      </header>

      <main class="playground-body">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 0;
      overflow: hidden;
    }

    .playground-shell {
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 0;
      background: var(--bg-void, #0b0e14);
      color: var(--text-primary, #e6edf3);
    }

    .playground-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 16px 24px;
      border-bottom: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
      background: var(--bg-surface-1, #111620);
      flex-shrink: 0;
    }

    .brand-title-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .playground-title {
      font-size: 18px;
      font-weight: 600;
      margin: 0;
      letter-spacing: -0.01em;
    }

    .exp-badge {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 2px 8px;
      border-radius: 999px;
      background: rgba(168, 85, 247, 0.15);
      color: #c084fc;
      border: 1px solid rgba(168, 85, 247, 0.3);
    }

    .playground-subtitle {
      margin: 4px 0 0;
      font-size: 12.5px;
      color: var(--text-secondary, #8b949e);
    }

    .playground-controls {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }

    .namespace-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 8px;
      background: rgba(56, 189, 248, 0.08);
      border: 1px solid rgba(56, 189, 248, 0.25);
      font-size: 12px;
      color: var(--text-secondary, #94a3b8);
    }

    .ns-icon, .tab-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .ns-value {
      font-family: var(--font-mono, monospace);
      font-weight: 600;
      color: #38bdf8;
    }

    .playground-tabs {
      display: flex;
      align-items: center;
      gap: 6px;
      background: var(--bg-void, #0b0e14);
      padding: 4px;
      border-radius: 10px;
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
    }

    .playground-tab {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 7px 14px;
      border-radius: 7px;
      font-size: 13px;
      font-weight: 500;
      color: var(--text-secondary, #8b949e);
      text-decoration: none;
      transition: all 0.15s ease;
    }

    .playground-tab:hover {
      color: var(--text-primary, #e6edf3);
      background: rgba(255, 255, 255, 0.04);
    }

    .playground-tab.active {
      color: #fff;
      background: var(--accent-primary, #6366f1);
      box-shadow: 0 1px 4px rgba(99, 102, 241, 0.35);
    }

    .playground-body {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
    }
  `],
})
export class PlaygroundComponent {
  readonly cortexState = inject(CortexStateService);
}
