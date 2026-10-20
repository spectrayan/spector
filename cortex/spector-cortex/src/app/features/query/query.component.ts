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

import { Component, inject, signal, computed, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CortexStateService } from '@core/services/cortex-state.service';
import { MelApiService } from '@core/services/mel-api.service';
import {
  MelCandidateItem,
  MelDiagnostic,
  MelExecuteResponse,
  MelExecutionHistoryItem,
  MelSnippetTemplate,
} from '@core/models/mel.models';
import {
  DEFAULT_MEL_SCRIPT,
  DEFAULT_VISUAL_ASSIST_STATE,
  VisualRecallAssistState,
  buildMelFromVisualState,
  parseMelToVisualState,
} from './models/mel-templates';
import { MelExplorerRailComponent } from './components/mel-explorer-rail/mel-explorer-rail.component';
import { MelVisualAssistComponent } from './components/mel-visual-assist/mel-visual-assist.component';
import { MelEditorComponent } from './components/mel-editor/mel-editor.component';
import { MelResultsDockComponent, DockTab } from './components/mel-results-dock/mel-results-dock.component';
import { MelWhyNotDrawerComponent } from './components/mel-why-not-drawer/mel-why-not-drawer.component';

@Component({
  selector: 'cortex-query',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatSnackBarModule,
    MelExplorerRailComponent,
    MelVisualAssistComponent,
    MelEditorComponent,
    MelResultsDockComponent,
    MelWhyNotDrawerComponent,
  ],
  templateUrl: './query.component.html',
  styleUrl: './query.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryComponent implements OnInit {
  protected readonly state = inject(CortexStateService);
  private readonly melApi = inject(MelApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly script = signal<string>(DEFAULT_MEL_SCRIPT);
  readonly assistState = signal<VisualRecallAssistState>(DEFAULT_VISUAL_ASSIST_STATE);
  readonly isAssistOpen = signal<boolean>(true);
  readonly activeTab = signal<DockTab>('candidates');
  readonly diagnostics = signal<MelDiagnostic[]>([]);
  readonly isRunning = signal<boolean>(false);
  readonly melResponse = signal<MelExecuteResponse | null>(null);
  readonly candidates = signal<MelCandidateItem[]>([]);
  readonly melHistory = signal<MelExecutionHistoryItem[]>([]);
  readonly diagnosticMemoryId = signal<string | null>(null);

  readonly activeNamespace = computed(() => {
    return this.state.currentNamespaceItem()?.slug || this.state.currentNamespace() || 'default';
  });

  ngOnInit(): void {
    // Initial validation
    this.onValidate();
  }

  onRunScript(): void {
    const code = this.script().trim();
    if (!code) return;

    this.isRunning.set(true);
    const ns = this.activeNamespace();
    const start = performance.now();

    this.melApi.execute(code, ns).subscribe({
      next: (res) => {
        this.isRunning.set(false);
        this.melResponse.set(res);
        this.candidates.set(res.candidates || []);
        this.diagnostics.set(res.diagnostics || []);

        const elapsed = performance.now() - start;
        const verb = this.extractPrimaryVerb(code);

        const historyItem: MelExecutionHistoryItem = {
          id: 'hist-' + Date.now(),
          script: code,
          verb,
          status: res.status,
          elapsedMs: elapsed,
          timestamp: new Date().toLocaleTimeString(),
          candidateCount: res.candidates?.length,
        };
        this.melHistory.update((h) => [historyItem, ...h.slice(0, 19)]);

        if (res.status === 'OK') {
          if (res.candidates && res.candidates.length > 0) {
            this.activeTab.set('candidates');
          } else if (res.explain_rows && res.explain_rows.length > 0) {
            this.activeTab.set('explain');
          } else {
            this.activeTab.set('console');
          }
        } else {
          this.activeTab.set('console');
          this.snackBar.open('MEL execution reported errors', 'Dismiss', { duration: 3500 });
        }
      },
      error: (err) => {
        this.isRunning.set(false);
        const elapsed = performance.now() - start;
        const errMsg = err?.error?.message || err?.message || 'Execution failed';
        const diag: MelDiagnostic = {
          line: 1,
          column: 1,
          message: errMsg,
          severity: 'ERROR',
        };
        this.diagnostics.set([diag]);
        this.melResponse.set({
          status: 'ERROR',
          combined_output: `[ERROR] ${errMsg}`,
          elapsed_ms: Math.round(elapsed),
          total_elapsed_ms: Math.round(elapsed),
          duration_micros: Math.round(elapsed * 1000),
          namespace: ns,
          statement_type: 'ERROR',
          affected_ids: [],
          candidates: [],
          explain_rows: [],
          funnel: {
            totalScanned: 0,
            phase1Alive: 0,
            phase2TagSurvivors: 0,
            phase3ValenceSurvivors: 0,
            phase4MassSurvivors: 0,
            phase5SimdEvaluated: 0,
            phase6FinalReturned: 0,
          },
          funnel_phases: [],
          diagnostics: [diag],
          statements: [],
        });
        this.activeTab.set('console');
        this.snackBar.open(`MEL Error: ${errMsg}`, 'Dismiss', { duration: 4000 });
      },
    });
  }

  onValidate(): void {
    const code = this.script().trim();
    if (!code) {
      this.diagnostics.set([]);
      return;
    }
    this.melApi.validate(code).subscribe({
      next: (res) => {
        this.diagnostics.set(res.diagnostics || []);
      },
      error: () => {
        // Validation endpoint error fallback
      },
    });
  }

  onScriptChange(newScript: string): void {
    this.script.set(newScript);
    // Sync to visual assist controls
    const syncedState = parseMelToVisualState(newScript, this.assistState());
    this.assistState.set(syncedState);
    this.onValidate();
  }

  onAssistStateChange(newState: VisualRecallAssistState): void {
    this.assistState.set(newState);
    // Build updated script from visual assist controls
    const updatedScript = buildMelFromVisualState(newState);
    this.script.set(updatedScript);
    this.onValidate();
  }

  onSelectSnippet(snippet: MelSnippetTemplate): void {
    this.script.set(snippet.code);
    const synced = parseMelToVisualState(snippet.code, this.assistState());
    this.assistState.set(synced);
    this.onValidate();
    this.snackBar.open(`Loaded template: ${snippet.label}`, undefined, { duration: 2000 });
  }

  onProjectTier(tierName: string): void {
    const current = this.assistState();
    const exists = current.tiers.includes(tierName);
    const nextTiers = exists ? current.tiers : [...current.tiers, tierName];
    this.onAssistStateChange({
      ...current,
      tiers: nextTiers,
    });
  }

  onSelectHistory(item: MelExecutionHistoryItem): void {
    this.script.set(item.script);
    const synced = parseMelToVisualState(item.script, this.assistState());
    this.assistState.set(synced);
    this.onValidate();
  }

  onQuickExplain(): void {
    const current = this.assistState();
    const next = { ...current, explain: true };
    this.onAssistStateChange(next);
    this.onRunScript();
  }

  onDiagnose(memoryId: string): void {
    this.diagnosticMemoryId.set(memoryId);
  }

  onReinforce(memoryId: string): void {
    this.melApi.reinforce(memoryId, 0.1).subscribe({
      next: () => {
        this.snackBar.open(`Reinforced engram ${memoryId} (+0.1)`, undefined, { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(`Reinforce failed: ${err.message}`, 'Dismiss', { duration: 3000 });
      },
    });
  }

  onApplySuggestion(action: string): void {
    this.diagnosticMemoryId.set(null);
    if (action.startsWith('TOP ')) {
      const topK = parseInt(action.replace('TOP ', ''), 10);
      if (!isNaN(topK)) {
        this.onAssistStateChange({ ...this.assistState(), topK });
      }
    } else if (action.startsWith('IMPORTANCE >=')) {
      const imp = parseFloat(action.replace('IMPORTANCE >=', ''));
      if (!isNaN(imp)) {
        this.onAssistStateChange({ ...this.assistState(), minImportance: imp });
      }
    } else {
      // Append statement or load
      const current = this.script().trim();
      this.script.set(current + '\n\n' + action);
      this.onValidate();
    }
    this.snackBar.open(`Applied suggestion: ${action}`, undefined, { duration: 2000 });
  }

  private extractPrimaryVerb(script: string): string {
    const match = script.match(/^\s*(EXPLAIN\s+RECALL|RECALL|REMEMBER|CONSOLIDATE|FORGET|INTROSPECT|DREAM|ASSOCIATE|SIMULATE)/im);
    return match ? match[1].toUpperCase() : 'MEL SCRIPT';
  }
}
