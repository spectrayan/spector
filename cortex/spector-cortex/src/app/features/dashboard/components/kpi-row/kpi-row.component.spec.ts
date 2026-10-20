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
import { KpiRowComponent } from './kpi-row.component';

describe('KpiRowComponent', () => {
  let component: KpiRowComponent;
  let fixture: ComponentFixture<KpiRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiRowComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(KpiRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create component', () => {
    expect(component).toBeTruthy();
  });

  it('should render 6 KPI cards with default zero counts', () => {
    const cardEls = fixture.nativeElement.querySelectorAll('.kpi-card');
    expect(cardEls.length).toBe(6);

    const titles = Array.from(fixture.nativeElement.querySelectorAll('.card-title')).map(
      (el: any) => el.textContent?.trim()
    );
    expect(titles).toEqual([
      'Total Engrams',
      'Volatile Working',
      'Episodic Turns',
      'Semantic Concepts',
      'Procedural Rules',
      'Hebbian Network',
    ]);
  });

  it('should update values when input signals change', () => {
    fixture.componentRef.setInput('totalCount', 1420);
    fixture.componentRef.setInput('workingCount', 12);
    fixture.componentRef.setInput('episodicCount', 530);
    fixture.componentRef.setInput('semanticCount', 780);
    fixture.componentRef.setInput('proceduralCount', 98);
    fixture.componentRef.setInput('hebbianCount', '42 / 128');
    fixture.detectChanges();

    const totalEl = fixture.nativeElement.querySelector('[data-testid="kpi-total-engrams"] .card-value');
    expect(totalEl?.textContent?.trim()).toBe('1420');

    const workingEl = fixture.nativeElement.querySelector('[data-testid="kpi-volatile-working"] .card-value');
    expect(workingEl?.textContent?.trim()).toBe('12');

    const hebbianEl = fixture.nativeElement.querySelector('[data-testid="kpi-hebbian-network"] .card-value');
    expect(hebbianEl?.textContent?.trim()).toBe('42 / 128');
  });
});
