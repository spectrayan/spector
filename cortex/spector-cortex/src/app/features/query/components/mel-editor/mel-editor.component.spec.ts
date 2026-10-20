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
import { MelEditorComponent } from './mel-editor.component';

describe('MelEditorComponent (Milestone 4)', () => {
  let component: MelEditorComponent;
  let fixture: ComponentFixture<MelEditorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MelEditorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MelEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders line numbers matching textarea line count', () => {
    fixture.componentRef.setInput('script', "LINE 1\nLINE 2\nLINE 3\nLINE 4");
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const lineNums = compiled.querySelectorAll('.line-num');
    expect(lineNums.length).toBe(4);
  });

  it('emits execute output on Cmd+Enter / Ctrl+Enter', () => {
    let executed = false;
    component.execute.subscribe(() => (executed = true));

    const event = new KeyboardEvent('keydown', {
      key: 'Enter',
      metaKey: true,
      bubbles: true,
      cancelable: true,
    });
    component.onKeyDown(event);
    expect(executed).toBe(true);
  });

  it('renders diagnostics error pills when diagnostics input is provided', () => {
    fixture.componentRef.setInput('diagnostics', [
      {
        line: 2,
        column: 5,
        message: 'Unexpected token IDENTIFIER',
        severity: 'ERROR',
      },
    ]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const diag = compiled.querySelector('.diag-item');
    expect(diag).toBeTruthy();
    expect(diag?.textContent).toContain('[2:5]');
    expect(diag?.textContent).toContain('Unexpected token IDENTIFIER');
  });
});
