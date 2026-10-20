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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { MelResultsDockComponent } from './mel-results-dock.component';
import { MelCandidateItem, MelExecuteResponse } from '@core/models/mel.models';

describe('MelResultsDockComponent (Milestone 4)', () => {
  let component: MelResultsDockComponent;
  let fixture: ComponentFixture<MelResultsDockComponent>;

  const mockCandidate: MelCandidateItem = {
    id: 'ep-auth-01',
    rank: 1,
    fusedScore: 0.892,
    tier: 'EPISODIC',
    content: 'Redis pool connection timeout occurred during cluster failover',
    vectorSimilarity: 0.912,
    bm25Score: 0.845,
    valenceFactor: -0.32,
    decayFactor: 0.78,
    cognitiveMass: 1.45,
    storageStrength: 1.2,
    hebbianBoost: 0.65,
    synapticTags: ['infra', 'redis'],
  };

  const mockResponse: MelExecuteResponse = {
    status: 'OK',
    combined_output: 'RECALL completed in 14.2ms',
    elapsed_ms: 14,
    total_elapsed_ms: 14,
    duration_micros: 14200,
    namespace: 'default',
    statement_type: 'RECALL',
    affected_ids: [],
    candidates: [mockCandidate],
    explain_rows: [
      {
        rank: 1,
        id: 'ep-auth-01',
        tier: 'EPISODIC',
        fusedScore: 0.892,
        vectorSimilarity: 0.912,
        bm25Score: 0.845,
        decayFactor: 0.78,
        cognitiveMass: 1.45,
        storageStrength: 1.2,
        valenceFactor: -0.32,
        hebbianBoost: 0.65,
        freeEnergy: 0.12,
      },
    ],
    funnel: {
      totalScanned: 250,
      phase1Alive: 200,
      phase2TagSurvivors: 80,
      phase3ValenceSurvivors: 60,
      phase4MassSurvivors: 40,
      phase5SimdEvaluated: 20,
      phase6FinalReturned: 1,
    },
    funnel_phases: [],
    diagnostics: [],
    statements: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MelResultsDockComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MelResultsDockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders ranked candidate cards with 7-signal score bars', () => {
    fixture.componentRef.setInput('candidates', [mockCandidate]);
    fixture.componentRef.setInput('melResponse', mockResponse);
    fixture.componentRef.setInput('activeTab', 'candidates');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ep-auth-01');
    expect(compiled.textContent).toContain('0.892');
    expect(compiled.textContent).toContain('Vector Sim');
    expect(compiled.textContent).toContain('BM25 Lexical');
    expect(compiled.textContent).toContain('Affective Valence');
    expect(compiled.textContent).toContain('Recency Decay');
    expect(compiled.textContent).toContain('Cognitive Mass (Mi)');
    expect(compiled.textContent).toContain('Storage Strength (S)');
    expect(compiled.textContent).toContain('Hebbian Co-Act');
  });

  it('emits tabChange when clicking funnel or explain tabs', () => {
    let tab = '';
    component.tabChange.subscribe((t) => (tab = t));

    component.onSelectTab('funnel');
    expect(tab).toBe('funnel');

    component.onSelectTab('explain');
    expect(tab).toBe('explain');
  });

  it('emits diagnose output when Why-Not button is clicked', () => {
    let diagnosedId = '';
    component.diagnose.subscribe((id) => (diagnosedId = id));

    component.onDiagnose('ep-auth-01');
    expect(diagnosedId).toBe('ep-auth-01');
  });

  it('emits reinforce output when Reinforce button is clicked', () => {
    let reinforcedId = '';
    component.reinforce.subscribe((id) => (reinforcedId = id));

    component.onReinforce('ep-auth-01');
    expect(reinforcedId).toBe('ep-auth-01');
  });
});
