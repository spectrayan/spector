/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

import { Component, ChangeDetectionStrategy, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSliderModule } from '@angular/material/slider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

@Component({
  selector: 'cortex-graph-filter-deck',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatSliderModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './graph-filter-deck.component.html',
  styleUrl: './graph-filter-deck.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphFilterDeckComponent {
  readonly importanceMin = model<number>(0);
  readonly importanceMax = model<number>(10);
  readonly valenceMin = model<number>(-128);
  readonly valenceMax = model<number>(127);

  readonly timestampMin = input<number>(0);
  readonly timestampMax = input<number>(Date.now());
  readonly fromDate = input<Date | null>(null);
  readonly toDate = input<Date | null>(null);
  readonly visibleNodeCount = input<number>(0);

  readonly fromDateChange = output<any>();
  readonly toDateChange = output<any>();
  readonly setTemporalPreset = output<'1h' | '24h' | '7d' | '30d' | 'all'>();
  readonly resetFilters = output<void>();

  formatTimestamp(ms: number): string {
    if (ms <= 0) return '—';
    const d = new Date(ms);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
}
