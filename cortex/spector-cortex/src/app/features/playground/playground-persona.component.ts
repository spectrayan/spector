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

import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { SettingsStateService } from '../settings/services/settings-state.service';
import { PersonalitySettingsComponent } from '../settings/components/personality-settings/personality-settings.component';
import { ValuesSettingsComponent } from '../settings/components/values-settings/values-settings.component';
import { SynapseApiService } from '../../core/services/synapse-api.service';
import { AuthService } from '../../core/services/auth.service';
import { CortexSnackbarService } from '../../shared/services/cortex-snackbar.service';

@Component({
  selector: 'cortex-playground-persona',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    PersonalitySettingsComponent,
    ValuesSettingsComponent,
  ],
  providers: [SettingsStateService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="persona-container" data-testid="playground-persona-view">
      <!-- Top Action Bar -->
      <div class="persona-toolbar">
        <div class="toolbar-info">
          <h2 class="section-heading">Persona, Values &amp; Agent Soul</h2>
          <p class="section-desc">
            Configure synthetic persona identity, Big Five personality traits, cultural values, and the Agent Soul cognitive blueprint for Playground sessions.
          </p>
        </div>
        <div class="toolbar-actions">
          <button
            type="button"
            class="primary-btn"
            [disabled]="state.saving()"
            (click)="state.saveProfile()"
            data-testid="btn-save-persona"
          >
            <mat-icon class="inline-icon">save</mat-icon>
            <span>{{ state.saving() ? 'Saving...' : 'Save Persona Profile' }}</span>
          </button>
        </div>
      </div>

      <div class="persona-grid">
        <!-- Card 1: Persona Identity (Moved from Settings Profile) -->
        <section class="persona-card" data-testid="card-persona-identity">
          <div class="card-header">
            <div class="header-left">
              <mat-icon class="inline-icon">person</mat-icon>
              <h3>Persona Identity</h3>
            </div>
          </div>
          <div class="card-body">
            <div class="field-group">
              <label for="persona-about">About Me</label>
              <textarea
                id="persona-about"
                rows="3"
                [ngModel]="state.about()"
                (ngModelChange)="state.about.set($event)"
                placeholder="Describe background, expertise, or role context..."
                data-testid="input-persona-about"
              ></textarea>
            </div>
            <div class="field-row">
              <div class="field-group">
                <label for="persona-occupation">Occupation</label>
                <input
                  id="persona-occupation"
                  type="text"
                  [ngModel]="state.occupation()"
                  (ngModelChange)="state.occupation.set($event)"
                  placeholder="e.g. Distributed Systems Architect"
                  data-testid="input-persona-occupation"
                />
              </div>
              <div class="field-group">
                <label for="persona-nationality">Nationality</label>
                <input
                  id="persona-nationality"
                  type="text"
                  [ngModel]="state.nationality()"
                  (ngModelChange)="state.nationality.set($event)"
                  placeholder="e.g. American"
                  data-testid="input-persona-nationality"
                />
              </div>
            </div>

            <div class="field-group">
              <label for="persona-lang-input">Languages</label>
              <div class="chip-list">
                @for (lang of state.personaLanguages(); track lang; let i = $index) {
                  <span class="chip" data-testid="persona-language-chip">
                    {{ lang }}
                    <button type="button" class="chip-remove" (click)="state.removeChip('languages', i)">
                      <mat-icon class="chip-icon">close</mat-icon>
                    </button>
                  </span>
                }
              </div>
              <div class="chip-add-row">
                <input
                  id="persona-lang-input"
                  type="text"
                  [ngModel]="state.newLanguage()"
                  (ngModelChange)="state.newLanguage.set($event)"
                  (keydown.enter)="state.addChip('languages')"
                  placeholder="Add a language (e.g. English, Rust)..."
                  data-testid="input-persona-language"
                />
                <button
                  type="button"
                  class="secondary-btn"
                  (click)="state.addChip('languages')"
                  data-testid="btn-add-language"
                >
                  <mat-icon class="inline-icon">add</mat-icon>
                  <span>Add</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- Card 2: Agent Soul Blueprint -->
        <section class="persona-card" data-testid="card-agent-soul">
          <div class="card-header">
            <div class="header-left">
              <mat-icon class="inline-icon">auto_awesome</mat-icon>
              <h3>Agent Soul Blueprint</h3>
            </div>
            <div class="soul-actions">
              <button
                type="button"
                class="secondary-btn"
                [disabled]="soulSaving()"
                (click)="resetSoul()"
                data-testid="btn-reset-soul"
              >
                <mat-icon class="inline-icon">restart_alt</mat-icon>
                <span>Reset</span>
              </button>
              <button
                type="button"
                class="primary-btn compact"
                [disabled]="soulSaving()"
                (click)="saveSoul()"
                data-testid="btn-save-soul"
              >
                <mat-icon class="inline-icon">save</mat-icon>
                <span>{{ soulSaving() ? 'Saving...' : 'Save Soul' }}</span>
              </button>
            </div>
          </div>

          <div class="card-body">
            <div class="field-row">
              <div class="field-group">
                <label for="soul-name">Agent Name</label>
                <input
                  id="soul-name"
                  type="text"
                  [ngModel]="soulName()"
                  (ngModelChange)="soulName.set($event)"
                  placeholder="e.g. Spector Cognitive Companion"
                  data-testid="input-soul-name"
                />
              </div>
              <div class="field-group">
                <label for="soul-role">Core Role / Archetype</label>
                <input
                  id="soul-role"
                  type="text"
                  [ngModel]="soulRole()"
                  (ngModelChange)="soulRole.set($event)"
                  placeholder="e.g. Autonomous Research Partner"
                  data-testid="input-soul-role"
                />
              </div>
            </div>
            <div class="field-group">
              <label for="soul-directives">Core Directives &amp; Voice (one per line)</label>
              <textarea
                id="soul-directives"
                rows="4"
                [ngModel]="soulDirectives()"
                (ngModelChange)="soulDirectives.set($event)"
                placeholder="Ground claims in recalled memories&#10;Flag low-confidence inferences explicitly"
                data-testid="input-soul-directives"
              ></textarea>
            </div>
          </div>
        </section>
      </div>

      <!-- Existing Standalone Personality & Values Components -->
      <div class="embedded-settings-stack">
        <cortex-personality-settings />
        <cortex-values-settings />
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      padding: 20px 24px 40px;
    }

    .persona-container {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .inline-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .chip-icon {
      font-size: 13px;
      width: 13px;
      height: 13px;
    }

    .persona-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
      padding: 16px 20px;
      background: var(--bg-surface-1, #111620);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
      border-radius: 12px;
    }

    .section-heading {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary, #e6edf3);
    }

    .section-desc {
      margin: 4px 0 0;
      font-size: 12.5px;
      color: var(--text-secondary, #8b949e);
    }

    .persona-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }

    @media (max-width: 960px) {
      .persona-grid {
        grid-template-columns: 1fr;
      }
    }

    .persona-card {
      background: var(--bg-surface-1, #111620);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
      border-radius: 12px;
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 14px;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .card-header h3 {
      margin: 0;
      font-size: 14.5px;
      font-weight: 600;
      color: var(--text-primary, #e6edf3);
    }

    .soul-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .card-body {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }

    .field-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
    }

    .field-group label {
      font-size: 11.5px;
      font-weight: 600;
      color: var(--text-secondary, #94a3b8);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .field-group input,
    .field-group textarea {
      background: var(--bg-void);
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.12));
      border-radius: 8px;
      padding: 8px 11px;
      color: var(--text-primary, #e6edf3);
      font-size: 13px;
      font-family: inherit;
    }

    .chip-list {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 4px;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 9px;
      border-radius: 999px;
      font-size: 12px;
      background: rgba(99, 102, 241, 0.15);
      color: #c7d2fe;
      border: 1px solid rgba(99, 102, 241, 0.3);
    }

    .chip-remove {
      display: inline-flex;
      border: none;
      background: transparent;
      color: inherit;
      cursor: pointer;
      padding: 0;
    }

    .chip-add-row {
      display: flex;
      gap: 8px;
    }

    .chip-add-row input {
      flex: 1;
    }

    .primary-btn {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 8px 16px;
      border-radius: 8px;
      border: none;
      background: var(--accent-primary, #6366f1);
      color: #fff;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }

    .primary-btn.compact {
      padding: 6px 12px;
      font-size: 12px;
    }

    .secondary-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 8px;
      border: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.14));
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-primary, #e6edf3);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
    }

    .embedded-settings-stack {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
  `],
})
export class PlaygroundPersonaComponent implements OnInit {
  readonly state = inject(SettingsStateService);
  private readonly api = inject(SynapseApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(CortexSnackbarService);

  readonly soulName = signal('');
  readonly soulRole = signal('');
  readonly soulDirectives = signal('');
  readonly soulSaving = signal(false);

  ngOnInit(): void {
    this.state.profileId.set(this.auth.currentUser()?.userId || 'default');
    this.state.loadProfile();
    this.loadSoul();
  }

  loadSoul(): void {
    this.api.getAgentSoul().subscribe({
      next: (soul) => {
        if (!soul) return;
        this.soulName.set(soul.name ?? '');
        this.soulRole.set(soul.role ?? soul.archetype ?? '');
        const directives = Array.isArray(soul.directives)
          ? soul.directives.join('\n')
          : Array.isArray(soul.values)
            ? soul.values.join('\n')
            : soul.directives ?? '';
        this.soulDirectives.set(directives);
      },
      error: () => {
        // Optional endpoint; ignore load error in offline/mock environments
      },
    });
  }

  saveSoul(): void {
    this.soulSaving.set(true);
    const directives = this.soulDirectives()
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    this.api
      .updateAgentSoul({
        name: this.soulName(),
        role: this.soulRole(),
        directives,
      })
      .subscribe({
        next: () => {
          this.soulSaving.set(false);
          this.toast.success('Agent Soul updated');
        },
        error: () => {
          this.soulSaving.set(false);
          this.toast.error('Failed to update Agent Soul');
        },
      });
  }

  resetSoul(): void {
    this.soulSaving.set(true);
    this.api.resetAgentSoul().subscribe({
      next: (soul) => {
        this.soulSaving.set(false);
        this.soulName.set(soul?.name ?? '');
        this.soulRole.set(soul?.role ?? '');
        this.soulDirectives.set('');
        this.toast.success('Agent Soul reset to default');
      },
      error: () => {
        this.soulSaving.set(false);
        this.toast.error('Failed to reset Agent Soul');
      },
    });
  }
}
