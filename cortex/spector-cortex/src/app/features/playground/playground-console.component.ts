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

import { Component, ChangeDetectionStrategy, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { MemoryTableService, RecallResult } from '../../core/services/memory-table.service';

export type ConsoleOperation = 'recall' | 'remember' | 'reinforce' | 'why-not';

@Component({
  selector: 'cortex-playground-console',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="console-container">
      <!-- Left Column: Operation Builder -->
      <section class="console-panel builder-panel">
        <div class="panel-header">
          <div class="op-tabs" role="tablist">
            <button
              type="button"
              class="op-tab"
              [class.active]="operation() === 'recall'"
              (click)="setOperation('recall')"
              data-testid="op-tab-recall"
            >
              <mat-icon class="inline-icon">search</mat-icon>
              <span>Recall</span>
            </button>
            <button
              type="button"
              class="op-tab"
              [class.active]="operation() === 'remember'"
              (click)="setOperation('remember')"
              data-testid="op-tab-remember"
            >
              <mat-icon class="inline-icon">add_circle_outline</mat-icon>
              <span>Remember</span>
            </button>
            <button
              type="button"
              class="op-tab"
              [class.active]="operation() === 'reinforce'"
              (click)="setOperation('reinforce')"
              data-testid="op-tab-reinforce"
            >
              <mat-icon class="inline-icon">bolt</mat-icon>
              <span>Reinforce</span>
            </button>
            <button
              type="button"
              class="op-tab"
              [class.active]="operation() === 'why-not'"
              (click)="setOperation('why-not')"
              data-testid="op-tab-why-not"
            >
              <mat-icon class="inline-icon">help_outline</mat-icon>
              <span>Why-Not</span>
            </button>
          </div>
        </div>

        <div class="panel-body">
          <!-- RECALL FORM -->
          @if (operation() === 'recall') {
            <div class="form-grid" data-testid="form-recall">
              <div class="form-field full-width">
                <label for="recall-query">Semantic Query</label>
                <textarea
                  id="recall-query"
                  rows="3"
                  [ngModel]="recallQuery()"
                  (ngModelChange)="recallQuery.set($event)"
                  placeholder="Enter query to probe the 6-phase cognitive scoring pipeline..."
                  data-testid="input-recall-query"
                ></textarea>
              </div>
              <div class="form-field">
                <label for="recall-profile">Cognitive Profile</label>
                <select
                  id="recall-profile"
                  [ngModel]="recallProfile()"
                  (ngModelChange)="recallProfile.set($event)"
                  data-testid="select-recall-profile"
                >
                  @for (p of profiles; track p) {
                    <option [value]="p">{{ p }}</option>
                  }
                </select>
              </div>
              <div class="form-field">
                <label for="recall-topk">Top-K Limit</label>
                <input
                  id="recall-topk"
                  type="number"
                  min="1"
                  max="100"
                  [ngModel]="recallTopK()"
                  (ngModelChange)="recallTopK.set(+$event)"
                  data-testid="input-recall-topk"
                />
              </div>
              <div class="form-field full-width">
                <label for="recall-valence">
                  Mood-Congruent Query Valence (optional: -1.0 to +1.0)
                </label>
                <input
                  id="recall-valence"
                  type="number"
                  step="0.1"
                  min="-1"
                  max="1"
                  [ngModel]="recallValence()"
                  (ngModelChange)="recallValence.set($event === '' || $event === null ? null : +$event)"
                  placeholder="Leave blank for neutral valence"
                  data-testid="input-recall-valence"
                />
              </div>
            </div>
          }

          <!-- REMEMBER FORM -->
          @if (operation() === 'remember') {
            <div class="form-grid" data-testid="form-remember">
              <div class="form-field full-width">
                <label for="remember-text">Memory Content</label>
                <textarea
                  id="remember-text"
                  rows="3"
                  [ngModel]="rememberText()"
                  (ngModelChange)="rememberText.set($event)"
                  placeholder="Observation, fact, or episode to encode..."
                  data-testid="input-remember-text"
                ></textarea>
              </div>
              <div class="form-field">
                <label for="remember-id">Memory ID (optional)</label>
                <input
                  id="remember-id"
                  type="text"
                  [ngModel]="rememberId()"
                  (ngModelChange)="rememberId.set($event)"
                  placeholder="Auto-generated if blank"
                  data-testid="input-remember-id"
                />
              </div>
              <div class="form-field">
                <label for="remember-tier">Memory Tier</label>
                <select
                  id="remember-tier"
                  [ngModel]="rememberTier()"
                  (ngModelChange)="rememberTier.set($event)"
                  data-testid="select-remember-tier"
                >
                  <option value="EPISODIC">EPISODIC</option>
                  <option value="SEMANTIC">SEMANTIC</option>
                  <option value="WORKING">WORKING</option>
                  <option value="PROCEDURAL">PROCEDURAL</option>
                </select>
              </div>
              <div class="form-field">
                <label for="remember-source">Encoding Source</label>
                <select
                  id="remember-source"
                  [ngModel]="rememberSource()"
                  (ngModelChange)="rememberSource.set($event)"
                >
                  <option value="OBSERVED">OBSERVED</option>
                  <option value="TOLD">TOLD</option>
                  <option value="INFERRED">INFERRED</option>
                  <option value="CONSOLIDATED">CONSOLIDATED</option>
                </select>
              </div>
              <div class="form-field">
                <label for="remember-tags">Synaptic Tags (CSV)</label>
                <input
                  id="remember-tags"
                  type="text"
                  [ngModel]="rememberTags()"
                  (ngModelChange)="rememberTags.set($event)"
                  placeholder="architecture,redis,sla"
                  data-testid="input-remember-tags"
                />
              </div>
              <div class="form-field">
                <label for="remember-interest">Importance / Interest (0..1)</label>
                <input
                  id="remember-interest"
                  type="number"
                  step="0.05"
                  min="0"
                  max="1"
                  [ngModel]="rememberInterest()"
                  (ngModelChange)="rememberInterest.set(+$event)"
                />
              </div>
              <div class="form-field">
                <label for="remember-valence">Emotional Valence (-1..+1)</label>
                <input
                  id="remember-valence"
                  type="number"
                  step="0.1"
                  min="-1"
                  max="1"
                  [ngModel]="rememberValence()"
                  (ngModelChange)="rememberValence.set(+$event)"
                />
              </div>
            </div>
          }

          <!-- REINFORCE FORM -->
          @if (operation() === 'reinforce') {
            <div class="form-grid" data-testid="form-reinforce">
              <div class="form-field full-width">
                <label for="reinforce-id">Target Memory ID</label>
                <input
                  id="reinforce-id"
                  type="text"
                  [ngModel]="reinforceId()"
                  (ngModelChange)="reinforceId.set($event)"
                  placeholder="e.g. mem-12345"
                  data-testid="input-reinforce-id"
                />
              </div>
              <div class="form-field full-width">
                <label for="reinforce-valence">Reinforcement Valence Delta (-1.0 to +1.0)</label>
                <input
                  id="reinforce-valence"
                  type="number"
                  step="0.1"
                  min="-1"
                  max="1"
                  [ngModel]="reinforceValence()"
                  (ngModelChange)="reinforceValence.set(+$event)"
                  data-testid="input-reinforce-valence"
                />
              </div>
            </div>
          }

          <!-- WHY-NOT FORM -->
          @if (operation() === 'why-not') {
            <div class="form-grid" data-testid="form-why-not">
              <div class="form-field full-width">
                <label for="whynot-id">Candidate Memory ID</label>
                <input
                  id="whynot-id"
                  type="text"
                  [ngModel]="whyNotMemoryId()"
                  (ngModelChange)="whyNotMemoryId.set($event)"
                  placeholder="ID of memory that was excluded or ranked lower"
                  data-testid="input-whynot-id"
                />
              </div>
              <div class="form-field full-width">
                <label for="whynot-query">Comparison Query</label>
                <input
                  id="whynot-query"
                  type="text"
                  [ngModel]="whyNotQuery()"
                  (ngModelChange)="whyNotQuery.set($event)"
                  placeholder="Query to diagnose against"
                  data-testid="input-whynot-query"
                />
              </div>
              <div class="form-field">
                <label for="whynot-topk">Top-K Threshold</label>
                <input
                  id="whynot-topk"
                  type="number"
                  min="1"
                  max="100"
                  [ngModel]="whyNotTopK()"
                  (ngModelChange)="whyNotTopK.set(+$event)"
                  data-testid="input-whynot-topk"
                />
              </div>
            </div>
          }

          <div class="action-bar">
            <button
              type="button"
              class="execute-btn"
              [disabled]="loading()"
              (click)="execute()"
              data-testid="btn-execute-console"
            >
              <mat-icon class="inline-icon">play_arrow</mat-icon>
              <span>{{ loading() ? 'Executing...' : 'Execute ' + operation().toUpperCase() }}</span>
            </button>
            @if (lastLatencyMs() !== null) {
              <span class="latency-badge" data-testid="console-latency-badge">
                {{ lastLatencyMs() }} ms
              </span>
            }
          </div>

          <!-- Request JSON Preview -->
          <div class="json-block">
            <div class="json-header">
              <mat-icon class="inline-icon">code</mat-icon>
              <span>Request Payload ({{ cortexState.currentNamespace() }})</span>
            </div>
            <pre class="json-pre" data-testid="console-request-json">{{ requestPreviewJson() }}</pre>
          </div>
        </div>
      </section>

      <!-- Right Column: 6-Phase Scoring Breakdown & Raw Response JSON -->
      <section class="console-panel results-panel">
        @if (errorMessage()) {
          <div class="error-banner" data-testid="console-error-banner">
            {{ errorMessage() }}
          </div>
        }

        @if (operation() === 'recall' && recallResults().length > 0) {
          <div class="breakdown-section" data-testid="console-scoring-breakdown">
            <div class="section-title">
              <mat-icon class="inline-icon">analytics</mat-icon>
              <span>6-Phase Cognitive Scoring Breakdown ({{ recallResults().length }} hits)</span>
            </div>

            <div class="results-list">
              @for (item of recallResults(); track item.id) {
                <div class="result-card" data-testid="recall-result-card">
                  <div class="result-top">
                    <div class="result-meta">
                      <code class="result-id">{{ item.id }}</code>
                      <span class="tier-pill">{{ item.memoryType }}</span>
                      @for (tag of item.synapticTags; track tag) {
                        <span class="tag-pill">#{{ tag }}</span>
                      }
                    </div>
                    <div class="result-score-actions">
                      <span class="final-score" title="Final Fused Cognitive Score">
                        Score: {{ item.score | number:'1.4-4' }}
                      </span>
                      <button
                        type="button"
                        class="mini-action-btn"
                        (click)="inspectWhyNot(item.id)"
                        title="Diagnose with Why-Not"
                      >
                        Why-Not
                      </button>
                      <button
                        type="button"
                        class="mini-action-btn"
                        (click)="quickReinforce(item.id)"
                        title="Reinforce this engram"
                      >
                        Reinforce
                      </button>
                    </div>
                  </div>

                  <p class="result-text">{{ item.text }}</p>

                  <div class="phases-grid">
                    <div class="phase-cell">
                      <span class="phase-label">P1 Similarity</span>
                      <span class="phase-val">{{ item.breakdown.similarity | number:'1.3-3' }}</span>
                    </div>
                    <div class="phase-cell">
                      <span class="phase-label">P2 Decay × Imp</span>
                      <span class="phase-val">{{ item.breakdown.importanceDecay | number:'1.3-3' }}</span>
                    </div>
                    <div class="phase-cell">
                      <span class="phase-label">P3 Tag Boost</span>
                      <span class="phase-val">{{ item.breakdown.tagBoostFactor | number:'1.3-3' }}×</span>
                    </div>
                    <div class="phase-cell">
                      <span class="phase-label">P4 Habituation</span>
                      <span class="phase-val">{{ item.breakdown.habituationPenalty | number:'1.3-3' }}×</span>
                    </div>
                    <div class="phase-cell">
                      <span class="phase-label">P5 Hebbian Graph</span>
                      <span class="phase-val">{{ item.breakdown.graphBoost | number:'1.3-3' }}×</span>
                    </div>
                    <div class="phase-cell">
                      <span class="phase-label">P6 Valence Align</span>
                      <span class="phase-val">{{ item.breakdown.valenceAlignment | number:'1.3-3' }}×</span>
                    </div>
                  </div>
                </div>
              }
            </div>
          </div>
        }

        @if (whyNotReport()) {
          <div class="whynot-section" data-testid="console-whynot-report">
            <div class="section-title">
              <mat-icon class="inline-icon">help_outline</mat-icon>
              <span>Why-Not Diagnostic Report</span>
            </div>
            <pre class="whynot-pre">{{ whyNotReport() }}</pre>
          </div>
        }

        <div class="json-block response-block">
          <div class="json-header">
            <mat-icon class="inline-icon">code</mat-icon>
            <span>Raw Response JSON</span>
          </div>
          <pre class="json-pre" data-testid="console-response-json">{{ responseJson() }}</pre>
        </div>
      </section>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }

    .console-container {
      display: grid;
      grid-template-columns: minmax(380px, 1fr) minmax(440px, 1.25fr);
      gap: 20px;
      padding: 20px 24px;
      height: 100%;
      box-sizing: border-box;
    }

    @media (max-width: 1024px) {
      .console-container {
        grid-template-columns: 1fr;
      }
    }

    .console-panel {
      display: flex;
      flex-direction: column;
      gap: 16px;
      background: var(--bg-surface-1, #111620);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
      border-radius: 12px;
      padding: 18px;
      overflow-y: auto;
    }

    .inline-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .op-tabs {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 6px;
      background: var(--bg-void, #0b0e14);
      padding: 4px;
      border-radius: 9px;
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.07));
    }

    .op-tab {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 8px 10px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: var(--text-secondary, #8b949e);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .op-tab:hover {
      color: var(--text-primary, #e6edf3);
    }

    .op-tab.active {
      background: rgba(99, 102, 241, 0.18);
      color: #818cf8;
      border: 1px solid rgba(99, 102, 241, 0.35);
    }

    .panel-body {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }

    .form-field {
      display: flex;
      flex-direction: column;
      gap: 5px;
    }

    .form-field.full-width {
      grid-column: 1 / -1;
    }

    .form-field label {
      font-size: 11.5px;
      font-weight: 600;
      color: var(--text-secondary, #94a3b8);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .form-field input,
    .form-field select,
    .form-field textarea {
      background: var(--bg-void, #0b0e14);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.12));
      border-radius: 8px;
      padding: 8px 11px;
      color: var(--text-primary, #e6edf3);
      font-size: 13px;
      font-family: inherit;
    }

    .action-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    .execute-btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 9px 18px;
      border-radius: 8px;
      border: none;
      background: var(--accent-primary, #6366f1);
      color: #fff;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }

    .execute-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .latency-badge {
      font-family: var(--font-mono, monospace);
      font-size: 12px;
      padding: 4px 9px;
      border-radius: 6px;
      background: rgba(16, 185, 129, 0.12);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
    }

    .json-block {
      display: flex;
      flex-direction: column;
      border-radius: 8px;
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
      background: var(--bg-void, #0b0e14);
      overflow: hidden;
    }

    .json-header {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 7px 12px;
      font-size: 11.5px;
      font-weight: 600;
      color: var(--text-secondary, #8b949e);
      background: rgba(255, 255, 255, 0.03);
      border-bottom: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.06));
    }

    .json-pre,
    .whynot-pre {
      margin: 0;
      padding: 12px;
      font-family: var(--font-mono, monospace);
      font-size: 12px;
      line-height: 1.45;
      color: #cbd5e1;
      overflow-x: auto;
      max-height: 320px;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .section-title {
      display: flex;
      align-items: center;
      gap: 7px;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-primary, #e6edf3);
      margin-bottom: 10px;
    }

    .results-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .result-card {
      background: var(--bg-void, #0b0e14);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.09));
      border-radius: 9px;
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .result-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
    }

    .result-meta {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .result-id {
      font-family: var(--font-mono, monospace);
      font-size: 11.5px;
      color: #38bdf8;
    }

    .tier-pill {
      font-size: 10.5px;
      font-weight: 600;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(99, 102, 241, 0.15);
      color: #a5b4fc;
    }

    .tag-pill {
      font-size: 10.5px;
      padding: 1px 6px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.06);
      color: var(--text-secondary, #94a3b8);
    }

    .result-score-actions {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .final-score {
      font-family: var(--font-mono, monospace);
      font-size: 12px;
      font-weight: 700;
      color: #34d399;
    }

    .mini-action-btn {
      font-size: 11px;
      padding: 3px 8px;
      border-radius: 5px;
      border: 1px solid rgba(255, 255, 255, 0.14);
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-primary, #e6edf3);
      cursor: pointer;
    }

    .result-text {
      margin: 0;
      font-size: 13px;
      color: var(--text-primary, #e6edf3);
      line-height: 1.4;
    }

    .phases-grid {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 6px;
      padding-top: 6px;
      border-top: 1px solid rgba(255, 255, 255, 0.06);
    }

    @media (max-width: 768px) {
      .phases-grid {
        grid-template-columns: repeat(3, 1fr);
      }
    }

    .phase-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
      background: rgba(255, 255, 255, 0.025);
      padding: 5px 7px;
      border-radius: 5px;
    }

    .phase-label {
      font-size: 10px;
      color: var(--text-secondary, #8b949e);
    }

    .phase-val {
      font-family: var(--font-mono, monospace);
      font-size: 11.5px;
      font-weight: 600;
      color: #f8fafc;
    }

    .error-banner {
      padding: 10px 12px;
      border-radius: 8px;
      background: rgba(239, 68, 68, 0.14);
      border: 1px solid rgba(239, 68, 68, 0.35);
      color: #fca5a5;
      font-size: 12.5px;
    }
  `],
})
export class PlaygroundConsoleComponent {
  readonly cortexState = inject(CortexStateService);
  private readonly memoryService = inject(MemoryTableService);

  readonly profiles = [
    'BALANCED',
    'SEMANTIC',
    'RECENT',
    'EMOTIONAL',
    'EXACT',
    'GRAPH',
    'PROCEDURAL',
    'HYPERFOCUS',
  ];

  readonly operation = signal<ConsoleOperation>('recall');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastLatencyMs = signal<number | null>(null);

  // Recall inputs
  readonly recallQuery = signal('cognitive memory architecture');
  readonly recallProfile = signal('BALANCED');
  readonly recallTopK = signal(5);
  readonly recallValence = signal<number | null>(null);

  // Remember inputs
  readonly rememberId = signal('');
  readonly rememberText = signal('');
  readonly rememberTier = signal('EPISODIC');
  readonly rememberSource = signal('OBSERVED');
  readonly rememberTags = signal('playground,test');
  readonly rememberInterest = signal(0.75);
  readonly rememberValence = signal(0.0);

  // Reinforce inputs
  readonly reinforceId = signal('');
  readonly reinforceValence = signal(0.5);

  // Why-Not inputs
  readonly whyNotMemoryId = signal('');
  readonly whyNotQuery = signal('');
  readonly whyNotTopK = signal(5);

  // Results state
  readonly recallResults = signal<RecallResult[]>([]);
  readonly whyNotReport = signal<string | null>(null);
  readonly responseJson = signal<string>('// Execute an operation to inspect the raw response JSON');

  readonly requestPreviewJson = computed(() => {
    const ns = this.cortexState.currentNamespace();
    const op = this.operation();
    if (op === 'recall') {
      const body: Record<string, any> = {
        query: this.recallQuery(),
        topK: this.recallTopK(),
        profile: this.recallProfile(),
      };
      if (this.recallValence() !== null) {
        body['queryValence'] = this.recallValence();
      }
      return JSON.stringify(
        {
          method: 'POST',
          endpoint: '/api/v1/memory/recall',
          headers: { 'X-Spector-Namespace': ns },
          body,
        },
        null,
        2
      );
    }
    if (op === 'remember') {
      return JSON.stringify(
        {
          method: 'POST',
          endpoint: '/api/v1/memory/remember',
          headers: { 'X-Spector-Namespace': ns },
          body: {
            id: this.rememberId() || '<auto-generated>',
            text: this.rememberText(),
            tier: this.rememberTier(),
            source: this.rememberSource(),
            tags: this.rememberTags(),
            interest: this.rememberInterest(),
            valence: this.rememberValence(),
          },
        },
        null,
        2
      );
    }
    if (op === 'reinforce') {
      const id = this.reinforceId() || ':id';
      return JSON.stringify(
        {
          method: 'POST',
          endpoint: `/api/v1/memory/${id}/reinforce`,
          headers: { 'X-Spector-Namespace': ns },
          body: { valence: this.reinforceValence() },
        },
        null,
        2
      );
    }
    return JSON.stringify(
      {
        method: 'POST',
        endpoint: '/api/v1/memory/why-not',
        headers: { 'X-Spector-Namespace': ns },
        body: {
          memoryId: this.whyNotMemoryId(),
          query: this.whyNotQuery(),
          topK: this.whyNotTopK(),
        },
      },
      null,
      2
    );
  });

  setOperation(op: ConsoleOperation): void {
    this.operation.set(op);
    this.errorMessage.set(null);
  }

  execute(): void {
    const op = this.operation();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.whyNotReport.set(null);
    const start = Date.now();

    if (op === 'recall') {
      this.memoryService
        .recall(this.recallQuery(), this.recallTopK(), this.recallProfile(), this.recallValence())
        .subscribe({
          next: (res) => {
            this.lastLatencyMs.set(Date.now() - start);
            this.recallResults.set(res.results);
            this.responseJson.set(JSON.stringify(res, null, 2));
            this.loading.set(false);
          },
          error: (err) => this.handleError(err, start),
        });
      return;
    }

    if (op === 'remember') {
      const id = this.rememberId().trim() || `mem-${Date.now().toString(36)}`;
      const payload = {
        id,
        text: this.rememberText(),
        tier: this.rememberTier(),
        source: this.rememberSource(),
        tags: this.rememberTags(),
        interest: this.rememberInterest(),
        valence: this.rememberValence(),
      };
      this.memoryService.remember(payload).subscribe({
        next: (res) => {
          this.lastLatencyMs.set(Date.now() - start);
          this.responseJson.set(JSON.stringify(res, null, 2));
          this.loading.set(false);
        },
        error: (err) => this.handleError(err, start),
      });
      return;
    }

    if (op === 'reinforce') {
      const id = this.reinforceId().trim();
      if (!id) {
        this.errorMessage.set('Target Memory ID is required for reinforce.');
        this.loading.set(false);
        return;
      }
      this.memoryService.reinforce(id, this.reinforceValence()).subscribe({
        next: (res) => {
          this.lastLatencyMs.set(Date.now() - start);
          this.responseJson.set(
            JSON.stringify({ status: 'REINFORCED', memoryId: id, valence: this.reinforceValence(), message: res }, null, 2)
          );
          this.loading.set(false);
        },
        error: (err) => this.handleError(err, start),
      });
      return;
    }

    if (op === 'why-not') {
      const id = this.whyNotMemoryId().trim();
      const query = this.whyNotQuery().trim();
      if (!id || !query) {
        this.errorMessage.set('Both Candidate Memory ID and Comparison Query are required for Why-Not.');
        this.loading.set(false);
        return;
      }
      this.memoryService.whyNot(id, query, this.whyNotTopK()).subscribe({
        next: (res) => {
          this.lastLatencyMs.set(Date.now() - start);
          this.whyNotReport.set(res.diagnosticReport);
          this.responseJson.set(JSON.stringify(res, null, 2));
          this.loading.set(false);
        },
        error: (err) => this.handleError(err, start),
      });
    }
  }

  inspectWhyNot(memoryId: string): void {
    this.whyNotMemoryId.set(memoryId);
    this.whyNotQuery.set(this.recallQuery());
    this.whyNotTopK.set(this.recallTopK());
    this.operation.set('why-not');
    this.execute();
  }

  quickReinforce(memoryId: string): void {
    this.reinforceId.set(memoryId);
    this.operation.set('reinforce');
    this.execute();
  }

  private handleError(err: any, start: number): void {
    this.lastLatencyMs.set(Date.now() - start);
    const msg = err?.error?.message || err?.message || 'Request failed';
    this.errorMessage.set(msg);
    this.responseJson.set(JSON.stringify(err?.error ?? { error: msg }, null, 2));
    this.loading.set(false);
  }
}
