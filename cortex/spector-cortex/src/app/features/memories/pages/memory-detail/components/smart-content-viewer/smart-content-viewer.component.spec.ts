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
import { SmartContentViewerComponent } from './smart-content-viewer.component';

describe('SmartContentViewerComponent', () => {
  let component: SmartContentViewerComponent;
  let fixture: ComponentFixture<SmartContentViewerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SmartContentViewerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SmartContentViewerComponent);
    component = fixture.componentInstance;
  });

  it('should create component', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should detect JSON content format', () => {
    fixture.componentRef.setInput('content', '{"name": "Spector", "tier": "SEMANTIC", "score": 0.98}');
    fixture.detectChanges();

    expect(component.detectedFormat()).toBe('json');
    expect(component.effectiveFormat()).toBe('json');

    const badge = fixture.nativeElement.querySelector('[data-testid="format-badge"]');
    expect(badge?.textContent).toContain('JSON');

    const jsonCode = fixture.nativeElement.querySelector('.json-code');
    expect(jsonCode).toBeTruthy();
    expect(jsonCode.textContent).toContain('"name": "Spector"');
  });

  it('should detect Markdown content format', () => {
    fixture.componentRef.setInput('content', '## Memory Concept\n\nThis is a **key engram** with *salience*.');
    fixture.detectChanges();

    expect(component.detectedFormat()).toBe('markdown');
    expect(component.effectiveFormat()).toBe('markdown');

    const badge = fixture.nativeElement.querySelector('[data-testid="format-badge"]');
    expect(badge?.textContent).toContain('MARKDOWN');
    expect(fixture.nativeElement.querySelector('cortex-markdown-preview')).toBeTruthy();
  });

  it('should fallback to Raw format for plain text', () => {
    fixture.componentRef.setInput('content', 'Simple plain memory note without markup');
    fixture.detectChanges();

    expect(component.detectedFormat()).toBe('raw');
    expect(component.effectiveFormat()).toBe('raw');

    const rawCode = fixture.nativeElement.querySelector('.raw-code');
    expect(rawCode).toBeTruthy();
    expect(rawCode.textContent).toContain('Simple plain memory note');
  });

  it('should allow manually switching tabs', () => {
    fixture.componentRef.setInput('content', '{"valid": true}');
    fixture.detectChanges();

    expect(component.effectiveFormat()).toBe('json');

    component.selectTab('raw');
    fixture.detectChanges();

    expect(component.selectedTab()).toBe('raw');
    expect(component.effectiveFormat()).toBe('raw');
    expect(fixture.nativeElement.querySelector('.raw-code')).toBeTruthy();
  });

  it('should correctly compute word and character counts', () => {
    fixture.componentRef.setInput('content', 'One two three four five');
    fixture.detectChanges();

    expect(component.wordCount()).toBe(5);
    expect(component.charCount()).toBe(23);
  });
});
