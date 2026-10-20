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
import { MelExplorerRailComponent } from './mel-explorer-rail.component';
import { MEL_SNIPPET_TEMPLATES } from '../../models/mel-templates';

describe('MelExplorerRailComponent (Milestone 4)', () => {
  let component: MelExplorerRailComponent;
  let fixture: ComponentFixture<MelExplorerRailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MelExplorerRailComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MelExplorerRailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders 4 cognitive tiers with live engram counts', () => {
    fixture.componentRef.setInput('workingCount', 12);
    fixture.componentRef.setInput('episodicCount', 48);
    fixture.componentRef.setInput('semanticCount', 130);
    fixture.componentRef.setInput('proceduralCount', 16);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const tierButtons = compiled.querySelectorAll('.tier-btn');
    expect(tierButtons.length).toBe(4);
    expect(compiled.textContent).toContain('WORKING');
    expect(compiled.textContent).toContain('12');
    expect(compiled.textContent).toContain('EPISODIC');
    expect(compiled.textContent).toContain('48');
    expect(compiled.textContent).toContain('SEMANTIC');
    expect(compiled.textContent).toContain('130');
    expect(compiled.textContent).toContain('PROCEDURAL');
    expect(compiled.textContent).toContain('16');
  });

  it('emits projectTier output when a tier button is clicked', () => {
    let emittedTier = '';
    component.projectTier.subscribe((t) => (emittedTier = t));

    component.onProjectTier('SEMANTIC');
    expect(emittedTier).toBe('SEMANTIC');
  });

  it('emits selectSnippet output when a snippet template is selected', () => {
    let selectedSnippet: any = null;
    component.selectSnippet.subscribe((s) => (selectedSnippet = s));

    const tpl = MEL_SNIPPET_TEMPLATES[0];
    component.onSelectSnippet(tpl);
    expect(selectedSnippet).toBe(tpl);
  });
});
