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
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { QueryComponent } from './query.component';
import { MelApiService } from '@core/services/mel-api.service';
import { CortexStateService } from '@core/services/cortex-state.service';
import { MEL_SNIPPET_TEMPLATES } from './models/mel-templates';

describe('QueryComponent — MEL Studio & 6-Phase Recall Workbench (Milestone 4)', () => {
  let component: QueryComponent;
  let fixture: ComponentFixture<QueryComponent>;
  let melApiMock: Partial<MelApiService>;
  let stateMock: Partial<CortexStateService>;

  beforeEach(async () => {
    melApiMock = {
      execute: vi.fn().mockReturnValue(
        of({
          status: 'OK',
          combined_output: 'Execution complete',
          elapsed_ms: 8,
          total_elapsed_ms: 8,
          duration_micros: 8000,
          namespace: 'default',
          statement_type: 'RECALL',
          affected_ids: [],
          candidates: [
            {
              id: 'mem-redis-01',
              rank: 1,
              fusedScore: 0.945,
              tier: 'EPISODIC',
              content: 'Redis pool connection timeout',
              vectorSimilarity: 0.95,
              bm25Score: 0.88,
              valenceFactor: -0.2,
              decayFactor: 0.82,
              cognitiveMass: 1.1,
              storageStrength: 1.0,
              hebbianBoost: 0.5,
              synapticTags: ['redis', 'infra'],
            },
          ],
          explain_rows: [],
          funnel: {
            totalScanned: 100,
            phase1Alive: 80,
            phase2TagSurvivors: 40,
            phase3ValenceSurvivors: 30,
            phase4MassSurvivors: 25,
            phase5SimdEvaluated: 15,
            phase6FinalReturned: 1,
          },
          funnel_phases: [],
          diagnostics: [],
          statements: [],
        })
      ),
      validate: vi.fn().mockReturnValue(
        of({
          valid: true,
          statement_count: 1,
          statements: [],
          diagnostics: [],
        })
      ),
      reinforce: vi.fn().mockReturnValue(of({ success: true })),
    };

    stateMock = {
      currentNamespace: vi.fn().mockReturnValue('research') as any,
      currentNamespaceItem: vi.fn().mockReturnValue({ slug: 'research' }) as any,
      workingMemoryCount: vi.fn().mockReturnValue(10) as any,
      episodicMemoryCount: vi.fn().mockReturnValue(45) as any,
      semanticMemoryCount: vi.fn().mockReturnValue(120) as any,
      proceduralMemoryCount: vi.fn().mockReturnValue(8) as any,
    };

    await TestBed.configureTestingModule({
      imports: [QueryComponent, HttpClientTestingModule],
      providers: [
        { provide: MelApiService, useValue: melApiMock },
        { provide: CortexStateService, useValue: stateMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QueryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders top banner with MEL Studio title and MEL-001 SPEC badge', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('MEL Studio & 6-Phase Recall Workbench');
    expect(compiled.textContent).toContain('MEL-001 SPEC');
  });

  it('renders 12-column layout with left explorer rail and right workbench', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.mel-left-column')).toBeTruthy();
    expect(compiled.querySelector('.mel-right-column')).toBeTruthy();
    expect(compiled.querySelector('cortex-mel-explorer-rail')).toBeTruthy();
    expect(compiled.querySelector('cortex-mel-visual-assist')).toBeTruthy();
    expect(compiled.querySelector('cortex-mel-editor')).toBeTruthy();
    expect(compiled.querySelector('cortex-mel-results-dock')).toBeTruthy();
  });

  it('executes MEL script and populates candidates and history', () => {
    component.onRunScript();
    expect(melApiMock.execute).toHaveBeenCalled();
    expect(component.candidates().length).toBe(1);
    expect(component.candidates()[0].id).toBe('mem-redis-01');
    expect(component.melHistory().length).toBe(1);
  });

  it('loads snippet template into editor when snippet is selected from rail', () => {
    const snippet = MEL_SNIPPET_TEMPLATES[1]; // Filtered Recall
    component.onSelectSnippet(snippet);
    expect(component.script()).toBe(snippet.code);
    expect(melApiMock.validate).toHaveBeenCalled();
  });

  it('opens and closes Why-Not drawer', () => {
    component.onDiagnose('mem-redis-01');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.diagnosticMemoryId()).toBe('mem-redis-01');
    expect(compiled.querySelector('cortex-why-not-drawer')).toBeTruthy();

    // Close
    component.diagnosticMemoryId.set(null);
    fixture.detectChanges();
    expect(compiled.querySelector('cortex-why-not-drawer')).toBeFalsy();
  });

  it('applies counterfactual suggestions to workbench state', () => {
    component.onDiagnose('mem-redis-01');
    component.onApplySuggestion('TOP 20');
    expect(component.diagnosticMemoryId()).toBeNull();
    expect(component.assistState().topK).toBe(20);
  });
});
