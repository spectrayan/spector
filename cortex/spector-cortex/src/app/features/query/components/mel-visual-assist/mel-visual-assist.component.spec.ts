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
import { MelVisualAssistComponent } from './mel-visual-assist.component';
import { DEFAULT_VISUAL_ASSIST_STATE } from '../../models/mel-templates';

describe('MelVisualAssistComponent (Milestone 4)', () => {
  let component: MelVisualAssistComponent;
  let fixture: ComponentFixture<MelVisualAssistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MelVisualAssistComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MelVisualAssistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders cue input and reflects initial assist state', () => {
    fixture.componentRef.setInput('state', {
      ...DEFAULT_VISUAL_ASSIST_STATE,
      cue: 'auth token expired',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#cue-input') as HTMLInputElement;
    expect(input.value).toBe('auth token expired');
  });

  it('toggles explain mode and emits stateChange', () => {
    let emittedState: any = null;
    component.stateChange.subscribe((s) => (emittedState = s));

    fixture.componentRef.setInput('state', {
      ...DEFAULT_VISUAL_ASSIST_STATE,
      explain: false,
    });
    fixture.detectChanges();

    component.onExplainToggle();
    expect(emittedState.explain).toBe(true);
  });

  it('toggles tier filters and emits stateChange', () => {
    let emittedState: any = null;
    component.stateChange.subscribe((s) => (emittedState = s));

    fixture.componentRef.setInput('state', {
      ...DEFAULT_VISUAL_ASSIST_STATE,
      tiers: ['EPISODIC'],
    });
    fixture.detectChanges();

    component.onTierToggle('SEMANTIC');
    expect(emittedState.tiers).toContain('EPISODIC');
    expect(emittedState.tiers).toContain('SEMANTIC');
  });

  it('emits toggleOpen when hide/show assist button is clicked', () => {
    let toggled = false;
    component.toggleOpen.subscribe(() => (toggled = true));

    component.onToggle();
    expect(toggled).toBe(true);
  });
});
