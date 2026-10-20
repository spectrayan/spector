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
import { MelWhyNotDrawerComponent } from './mel-why-not-drawer.component';

describe('MelWhyNotDrawerComponent (Milestone 4)', () => {
  let component: MelWhyNotDrawerComponent;
  let fixture: ComponentFixture<MelWhyNotDrawerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MelWhyNotDrawerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MelWhyNotDrawerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('memoryId', 'ep-redis-pool-01');
    fixture.componentRef.setInput('queryCue', 'database connection failover');
    fixture.detectChanges();
  });

  it('renders memory ID badge and cue in header', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ep-redis-pool-01');
    expect(compiled.textContent).toContain('database connection failover');
  });

  it('renders all 6 cascading filter phase evaluations', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const tiles = compiled.querySelectorAll('.phase-tile');
    expect(tiles.length).toBe(6);
    expect(compiled.textContent).toContain('Tombstone & Contradiction Filter');
    expect(compiled.textContent).toContain('128-bit Bloom Synaptic Tag Filter');
    expect(compiled.textContent).toContain('Affective Valence Range Check');
    expect(compiled.textContent).toContain('Stale Pre-Screen & Cognitive Mass (Mi)');
    expect(compiled.textContent).toContain('SIMD Vector Distance Calculation');
    expect(compiled.textContent).toContain('Min-Heap Top-K Cutoff');
  });

  it('emits close output when close button is clicked', () => {
    let closed = false;
    component.close.subscribe(() => (closed = true));

    component.onClose();
    expect(closed).toBe(true);
  });

  it('emits applySuggestion output when suggestion button is clicked', () => {
    let suggestion = '';
    component.applySuggestion.subscribe((s) => (suggestion = s));

    component.onSelectSuggestion('TOP 10');
    expect(suggestion).toBe('TOP 10');
  });
});
