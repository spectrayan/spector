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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { EventTickerComponent } from './event-ticker.component';
import { CortexStateService } from '@core/services/cortex-state.service';
import { QueryTraceEvent, SimdLaneEvent } from '@core/models/cortex-events';

describe('EventTickerComponent', () => {
  let component: EventTickerComponent;
  let fixture: ComponentFixture<EventTickerComponent>;
  let state: CortexStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventTickerComponent],
      providers: [CortexStateService],
    }).compileComponents();

    state = TestBed.inject(CortexStateService);
    state.clearRecentEvents();
    state.connectionStatus.set('connected');

    fixture = TestBed.createComponent(EventTickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create component', () => {
    expect(component).toBeTruthy();
  });

  it('should show empty state when there are no events', () => {
    const emptyEl = fixture.nativeElement.querySelector('.empty-state');
    expect(emptyEl).toBeTruthy();
    expect(emptyEl.textContent).toContain('Waiting for memory events');
  });

  it('should render event rows when recentEvents are pushed to state', () => {
    const mockQueryEvent: QueryTraceEvent = {
      eventType: 'spector.query.trace',
      timestamp: Date.now(),
      nodeId: 'spector-0',
      queryText: 'Find user preferences',
      cognitiveProfile: 'balanced',
      synapticTagMask: 0,
      totalRecords: 100,
      afterTombstone: 95,
      afterTagGate: 80,
      afterValence: 70,
      afterDecay: 60,
      afterVectorDistance: 10,
      finalTopK: 5,
      hebbianActivated: 2,
      temporalLinked: 1,
      entityDiscovered: 0,
      latencyMicros: 4200,
    };

    state.pushSseEvent(mockQueryEvent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.event-row');
    expect(rows.length).toBe(1);

    const badge = fixture.nativeElement.querySelector('.event-badge');
    expect(badge?.textContent?.trim()).toBe('QUERY');
  });

  it('should open and close payload drawer on click', () => {
    const mockSimd: SimdLaneEvent = {
      eventType: 'spector.simd.lane',
      timestamp: Date.now(),
      nodeId: 'spector-0',
      kernelName: 'avx512_cosine',
      laneWidth: 16,
      vectorsProcessed: 1000,
      durationMicros: 350,
      fallbackNanos: 0,
    };

    state.pushSseEvent(mockSimd);
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('.event-row');
    row.click();
    fixture.detectChanges();

    const drawer = fixture.nativeElement.querySelector('.payload-drawer');
    expect(drawer).toBeTruthy();
    expect(drawer.textContent).toContain('avx512_cosine');

    const closeBtn = fixture.nativeElement.querySelector('.drawer-close');
    closeBtn.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.payload-drawer')).toBeNull();
  });

  it('should clear events when clear button is clicked', () => {
    state.pushSseEvent({
      eventType: 'spector.metrics.tick',
      timestamp: Date.now(),
      nodeId: 'spector-0',
    } as any);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.event-row').length).toBe(1);

    const clearBtn = fixture.nativeElement.querySelector('.clear-btn');
    clearBtn.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.event-row').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeTruthy();
  });
});
