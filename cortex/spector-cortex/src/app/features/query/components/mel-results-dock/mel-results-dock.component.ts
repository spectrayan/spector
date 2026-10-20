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

import { Component, input, output, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MelCandidateItem,
  MelExplainRow,
  MelFunnelPhase,
  FunnelTelemetry,
  MelExecuteResponse,
} from '@core/models/mel.models';

export type DockTab = 'candidates' | 'funnel' | 'explain' | 'console';

@Component({
  selector: 'cortex-mel-results-dock',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './mel-results-dock.component.html',
  styleUrl: './mel-results-dock.component.scss',
})
export class MelResultsDockComponent {
  readonly activeTab = input<DockTab>('candidates');
  readonly candidates = input<MelCandidateItem[]>([]);
  readonly melResponse = input<MelExecuteResponse | null>(null);

  readonly tabChange = output<DockTab>();
  readonly diagnose = output<string>();
  readonly reinforce = output<string>();

  protected readonly copied = signal<boolean>(false);
  protected readonly Math = Math;

  readonly explainRows = computed<MelExplainRow[]>(() => {
    const res = this.melResponse();
    if (!res) return [];
    if (res.explain_rows && res.explain_rows.length > 0) {
      return res.explain_rows;
    }
    return res.statements?.flatMap((s) => s.explain_rows || []) ?? [];
  });

  readonly funnelPhases = computed<MelFunnelPhase[]>(() => {
    const res = this.melResponse();
    if (!res) return [];
    if (res.funnel_phases && res.funnel_phases.length > 0) {
      return res.funnel_phases;
    }
    const stmt = res.statements?.find((s) => (s.funnel_phases || []).length > 0);
    if (stmt?.funnel_phases && stmt.funnel_phases.length > 0) {
      return stmt.funnel_phases;
    }
    // Generate fallback phases from funnel telemetry if available
    const f = res.funnel;
    if (f && (f.totalScanned > 0 || f.phase6FinalReturned > 0)) {
      return this.synthesizeFunnelPhases(f);
    }
    return [];
  });

  readonly consoleText = computed<string>(() => {
    const res = this.melResponse();
    if (!res) return 'No script executed yet.';
    if (res.combined_output) return res.combined_output;
    if (res.output) return res.output;
    if (res.statements && res.statements.length > 0) {
      return res.statements.map((s) => s.output).filter(Boolean).join('\n\n');
    }
    return 'Execution completed with no output.';
  });

  private synthesizeFunnelPhases(f: FunnelTelemetry): MelFunnelPhase[] {
    const total = f.totalScanned || 1;
    return [
      {
        phaseIndex: 1,
        name: 'Phase 1: Tombstone & Contradiction Filter',
        survivors: f.phase1Alive,
        pruned: total - f.phase1Alive,
        total,
        survivalPct: (f.phase1Alive / total) * 100,
        description: 'Drop deleted engrams and contradictory traces',
      },
      {
        phaseIndex: 2,
        name: 'Phase 2: 128-bit Bloom Synaptic Tag Filter',
        survivors: f.phase2TagSurvivors,
        pruned: f.phase1Alive - f.phase2TagSurvivors,
        total: f.phase1Alive,
        survivalPct: f.phase1Alive > 0 ? (f.phase2TagSurvivors / f.phase1Alive) * 100 : 0,
        description: 'Fast bitwise AND-mask against query tag Bloom hash',
      },
      {
        phaseIndex: 3,
        name: 'Phase 3: Affective Valence Alignment Gate',
        survivors: f.phase3ValenceSurvivors,
        pruned: f.phase2TagSurvivors - f.phase3ValenceSurvivors,
        total: f.phase2TagSurvivors,
        survivalPct: f.phase2TagSurvivors > 0 ? (f.phase3ValenceSurvivors / f.phase2TagSurvivors) * 100 : 0,
        description: 'Bower mood-congruent emotional valence filtering',
      },
      {
        phaseIndex: 4,
        name: 'Phase 4: Stale Pre-screen & Cognitive Mass Exemption',
        survivors: f.phase4MassSurvivors,
        pruned: f.phase3ValenceSurvivors - f.phase4MassSurvivors,
        total: f.phase3ValenceSurvivors,
        survivalPct: f.phase3ValenceSurvivors > 0 ? (f.phase4MassSurvivors / f.phase3ValenceSurvivors) * 100 : 0,
        description: 'Prune decayed traces unless protected by cognitive mass Mi',
      },
      {
        phaseIndex: 5,
        name: 'Phase 5: SIMD AVX-512 / NEON Vector Calculation',
        survivors: f.phase5SimdEvaluated,
        pruned: f.phase4MassSurvivors - f.phase5SimdEvaluated,
        total: f.phase4MassSurvivors,
        survivalPct: f.phase4MassSurvivors > 0 ? (f.phase5SimdEvaluated / f.phase4MassSurvivors) * 100 : 0,
        description: 'Evaluate compute-heavy dot products only on surviving candidates',
      },
      {
        phaseIndex: 6,
        name: 'Phase 6: Fused Score & In-Place Min-Heap Top-K',
        survivors: f.phase6FinalReturned,
        pruned: f.phase5SimdEvaluated - f.phase6FinalReturned,
        total: f.phase5SimdEvaluated,
        survivalPct: f.phase5SimdEvaluated > 0 ? (f.phase6FinalReturned / f.phase5SimdEvaluated) * 100 : 0,
        description: 'Rank by fused score and select top K candidates',
      },
    ];
  }

  onSelectTab(tab: DockTab): void {
    this.tabChange.emit(tab);
  }

  onDiagnose(id: string): void {
    this.diagnose.emit(id);
  }

  onReinforce(id: string): void {
    this.reinforce.emit(id);
  }

  copyConsole(): void {
    const text = this.consoleText();
    navigator.clipboard.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}
