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

import '@angular/compiler';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { DashboardComponent } from './dashboard.component';
import { CortexStateService } from '@core/services/cortex-state.service';
import { MemoryTableService } from '@core/services/memory-table.service';
import { NeuralGraphComponent } from './components/neural-graph/neural-graph.component';
import { VectorSpaceComponent } from './components/vector-space/vector-space.component';
import { of } from 'rxjs';

if (typeof (globalThis as any).ResizeObserver === 'undefined') {
  (globalThis as any).ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };
}

// Robust mock Canvas 2D context Proxy for jsdom test environment
if (typeof HTMLCanvasElement !== 'undefined') {
  const dummyCtx: any = new Proxy(
    {
      measureText: () => ({ width: 0 }),
      createLinearGradient: () => ({ addColorStop: () => {} }),
      createRadialGradient: () => ({ addColorStop: () => {} }),
    },
    {
      get(target: any, prop: string | symbol) {
        if (prop in target) return target[prop];
        return () => {};
      },
    }
  );
  HTMLCanvasElement.prototype.getContext = function (type: string): any {
    if (type === '2d') return dummyCtx;
    return null;
  };
}

@Component({
  selector: 'cortex-neural-graph',
  standalone: true,
  template: '<div class="mock-neural-graph"></div>',
})
class MockNeuralGraphComponent {}

@Component({
  selector: 'cortex-vector-space',
  standalone: true,
  template: '<div class="mock-vector-space"></div>',
})
class MockVectorSpaceComponent {}

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let state: CortexStateService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.resetTestingModule();

    const memoryServiceMock = {
      getMemoryDiagnostics: vi.fn().mockReturnValue(of({
        workingCount: 10,
        episodicCount: 50,
        semanticCount: 200,
        proceduralCount: 30,
        entityNodes: 12,
        hebbianEdges: 48,
      })),
      getDecayCurve: vi.fn().mockReturnValue(of([])),
      getConsolidationDiff: vi.fn().mockReturnValue(of([])),
      getLiveMetrics: vi.fn().mockReturnValue(of([])),
      getStatus: vi.fn().mockReturnValue(of({ totalMemories: 290 })),
      reflect: vi.fn().mockReturnValue(of({ success: true })),
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        CortexStateService,
        { provide: MemoryTableService, useValue: memoryServiceMock },
      ],
    })
      .overrideComponent(DashboardComponent, {
        remove: { imports: [NeuralGraphComponent, VectorSpaceComponent] },
        add: { imports: [MockNeuralGraphComponent, MockVectorSpaceComponent] },
      })
      .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    state = TestBed.inject(CortexStateService);

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Satisfy initial /system/status call triggered by ngOnInit
    const req = httpMock.expectOne((r) => r.url.includes('/system/status'));
    req.flush({ status: 'UP' });
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('should create component', () => {
    expect(component).toBeTruthy();
  });

  it('should render action bar, KPI row, and event ticker', () => {
    expect(fixture.nativeElement.querySelector('.dashboard-action-bar')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('cortex-kpi-row')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('cortex-event-ticker')).toBeTruthy();
  });

  it('should toggle view mode between dashboard grid and cluster ring', () => {
    const gridBtn = fixture.nativeElement.querySelector('[data-testid="mode-grid-btn"]');
    const clusterBtn = fixture.nativeElement.querySelector('[data-testid="mode-cluster-btn"]');

    expect(state.viewMode()).toBe('dashboard');
    expect(gridBtn.classList).toContain('active');

    clusterBtn.click();
    fixture.detectChanges();

    expect(state.viewMode()).toBe('cluster');
    expect(clusterBtn.classList).toContain('active');

    gridBtn.click();
    fixture.detectChanges();

    expect(state.viewMode()).toBe('dashboard');
  });

  it('should handle cognitive triggers', () => {
    const dreamBtn = fixture.nativeElement.querySelector('[data-testid="trigger-dream-btn"]');
    const reflectBtn = fixture.nativeElement.querySelector('[data-testid="trigger-reflect-btn"]');
    const wanderBtn = fixture.nativeElement.querySelector('[data-testid="trigger-wander-btn"]');

    dreamBtn.click();
    const dreamReq = httpMock.expectOne((r) => r.url.includes('/memory/dream'));
    expect(dreamReq.request.method).toBe('POST');
    dreamReq.flush({ success: true });

    reflectBtn.click();
    // reflect uses memoryServiceMock.reflect()

    wanderBtn.click();
    const wanderReq = httpMock.expectOne((r) => r.url.includes('/memory/wander'));
    expect(wanderReq.request.method).toBe('POST');
    wanderReq.flush({ success: true });
  });
});
