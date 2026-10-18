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

import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PlaygroundComponent } from './playground.component';
import { PlaygroundConsoleComponent } from './playground-console.component';
import { PlaygroundPersonaComponent } from './playground-persona.component';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { MemoryTableService } from '../../core/services/memory-table.service';
import { FeatureFlagService } from '../../core/services/feature-flag.service';
import { featureGuard } from '../../core/guards/feature.guard';
import { SynapseApiService } from '../../core/services/synapse-api.service';
import { AuthService } from '../../core/services/auth.service';
import { CortexSnackbarService } from '../../shared/services/cortex-snackbar.service';

describe('Playground Feature (#1068)', () => {
  describe('featureGuard(playgroundEnabled, chatEnabled)', () => {
    it('blocks /playground and redirects to /dashboard when playgroundEnabled and chatEnabled are false by default', () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          FeatureFlagService,
        ],
      });

      const router = TestBed.inject(Router);
      const guard = featureGuard('playgroundEnabled', 'chatEnabled');
      const result = TestBed.runInInjectionContext(() => guard({} as any, {} as any));

      expect(result).toEqual(router.createUrlTree(['/dashboard']));
    });

    it('allows /playground when playgroundEnabled is true', () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          FeatureFlagService,
        ],
      });

      const flagService = TestBed.inject(FeatureFlagService);
      const httpMock = TestBed.inject(HttpTestingController);

      flagService.loadFlags().subscribe();
      httpMock.expectOne((req) => req.url.endsWith('/features')).flush({
        playgroundEnabled: true,
        chatEnabled: false,
      });

      const guard = featureGuard('playgroundEnabled', 'chatEnabled');
      const result = TestBed.runInInjectionContext(() => guard({} as any, {} as any));

      expect(result).toBe(true);
      httpMock.verify();
    });
  });

  describe('PlaygroundComponent shell', () => {
    it('renders active namespace badge and console/chat/persona navigation tabs', () => {
      TestBed.configureTestingModule({
        imports: [PlaygroundComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
        ],
      });

      const cortexState = TestBed.inject(CortexStateService);
      cortexState.currentNamespace.set('tenant-alpha-ns');

      const fixture = TestBed.createComponent(PlaygroundComponent);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="playground-namespace-badge"]')?.textContent).toContain('tenant-alpha-ns');
      expect(el.querySelector('[data-testid="tab-playground-console"]')?.textContent).toContain('Remember / Recall Console');
      expect(el.querySelector('[data-testid="tab-playground-chat"]')?.textContent).toContain('Agent Chat');
      expect(el.querySelector('[data-testid="tab-playground-persona"]')?.textContent).toContain('Persona & Soul');
    });
  });

  describe('PlaygroundConsoleComponent', () => {
    let memoryServiceMock: {
      recall: ReturnType<typeof vi.fn>;
      remember: ReturnType<typeof vi.fn>;
      reinforce: ReturnType<typeof vi.fn>;
      whyNot: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
      memoryServiceMock = {
        recall: vi.fn(),
        remember: vi.fn(),
        reinforce: vi.fn(),
        whyNot: vi.fn(),
      };

      TestBed.configureTestingModule({
        imports: [PlaygroundConsoleComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: MemoryTableService, useValue: memoryServiceMock },
        ],
      });
    });

    it('executes recall, displays 6-phase scoring breakdown, and renders request/response JSON', () => {
      const cortexState = TestBed.inject(CortexStateService);
      cortexState.currentNamespace.set('ns-research');

      memoryServiceMock.recall.mockReturnValue(
        of({
          results: [
            {
              id: 'mem-42',
              text: 'Ebbinghaus power-law decay stabilizes high-mass engrams.',
              score: 0.9125,
              importance: 0.85,
              ageDays: 2,
              agentRecallCount: 4,
              valence: 0.2,
              memoryType: 'SEMANTIC',
              source: 'OBSERVED',
              synapticTags: ['ebbinghaus', 'decay'],
              breakdown: {
                similarity: 0.88,
                importanceDecay: 0.94,
                tagBoostFactor: 1.15,
                habituationPenalty: 0.98,
                graphBoost: 1.05,
                valenceAlignment: 1.02,
                finalScore: 0.9125,
              },
              lateral: false,
              negativeOutcome: false,
              hyperfocused: false,
              positivelyReinforced: true,
            },
          ],
          totalMemories: 1,
          queryTimeMs: 4,
          profile: 'BALANCED',
        })
      );

      const fixture = TestBed.createComponent(PlaygroundConsoleComponent);
      const component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.requestPreviewJson()).toContain('"X-Spector-Namespace": "ns-research"');

      component.execute();
      fixture.detectChanges();

      expect(memoryServiceMock.recall).toHaveBeenCalledWith('cognitive memory architecture', 5, 'BALANCED', null);
      const el: HTMLElement = fixture.nativeElement;
      const breakdownEl = el.querySelector('[data-testid="console-scoring-breakdown"]');
      expect(breakdownEl).toBeTruthy();
      expect(breakdownEl?.textContent).toContain('0.880');
      expect(breakdownEl?.textContent).toContain('0.940');
      expect(breakdownEl?.textContent).toContain('1.150×');
      expect(el.querySelector('[data-testid="console-response-json"]')?.textContent).toContain('mem-42');
    });

    it('executes why-not diagnostic when inspecting a memory from recall results', () => {
      memoryServiceMock.whyNot.mockReturnValue(
        of({
          memoryId: 'mem-42',
          query: 'cognitive memory architecture',
          topK: 5,
          diagnosticReport: 'Phase 4 Stale Pre-Screen: Survived (cognitive mass M_i = 2.41)',
        })
      );

      const fixture = TestBed.createComponent(PlaygroundConsoleComponent);
      const component = fixture.componentInstance;
      fixture.detectChanges();

      component.inspectWhyNot('mem-42');
      fixture.detectChanges();

      expect(component.operation()).toBe('why-not');
      expect(memoryServiceMock.whyNot).toHaveBeenCalledWith('mem-42', 'cognitive memory architecture', 5);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="console-whynot-report"]')?.textContent).toContain(
        'Phase 4 Stale Pre-Screen: Survived'
      );
    });
  });

  describe('PlaygroundPersonaComponent', () => {
    it('loads and updates persona identity and AgentSoul blueprint', () => {
      const apiMock = {
        getSalienceProfile: vi.fn().mockReturnValue(
          of({
            persona: {
              about: 'Principal Memory Researcher',
              occupation: 'Neuroscientist',
              nationality: 'Canadian',
              languages: ['English', 'French'],
            },
          })
        ),
        saveSalienceProfile: vi.fn().mockReturnValue(of({})),
        getAgentSoul: vi.fn().mockReturnValue(
          of({
            name: 'Spector Companion',
            role: 'Cognitive Analyst',
            directives: ['Cite memory IDs', 'Highlight contradictions'],
          })
        ),
        updateAgentSoul: vi.fn().mockReturnValue(of({})),
        resetAgentSoul: vi.fn().mockReturnValue(of({})),
      };

      const authMock = {
        currentUser: vi.fn().mockReturnValue({
          userId: 'user-1',
          username: 'alice',
          displayName: 'Alice',
          role: 'editor',
          tenantId: 'tenant-a',
          scopes: ['memory:read', 'memory:write'],
        }),
      };

      const snackbarMock = {
        success: vi.fn(),
        error: vi.fn(),
      };

      TestBed.configureTestingModule({
        imports: [PlaygroundPersonaComponent],
        providers: [
          provideAnimationsAsync(),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: SynapseApiService, useValue: apiMock },
          { provide: AuthService, useValue: authMock },
          { provide: CortexSnackbarService, useValue: snackbarMock },
        ],
      });

      const fixture = TestBed.createComponent(PlaygroundPersonaComponent);
      const component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.state.about()).toBe('Principal Memory Researcher');
      expect(component.state.occupation()).toBe('Neuroscientist');
      expect(component.soulName()).toBe('Spector Companion');
      expect(component.soulRole()).toBe('Cognitive Analyst');

      component.soulName.set('Updated Companion');
      component.saveSoul();

      expect(apiMock.updateAgentSoul).toHaveBeenCalledWith({
        name: 'Updated Companion',
        role: 'Cognitive Analyst',
        directives: ['Cite memory IDs', 'Highlight contradictions'],
      });
    });
  });
});
