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

import { Component, ChangeDetectionStrategy, input, output, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

@Component({
  selector: 'cortex-memory-advanced-filters',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSliderModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './memory-advanced-filters.component.html',
  styleUrl: './memory-advanced-filters.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryAdvancedFiltersComponent {
  readonly searchText = model<string>('');
  readonly selectedTagFilter = model<string | null>(null);
  readonly availableTags = input<string[]>([]);
  readonly minImportance = model<number>(0);
  readonly minValence = model<number>(-128);
  readonly maxValence = model<number>(127);
  readonly filterDateFrom = model<Date | null>(null);
  readonly filterDateTo = model<Date | null>(null);

  readonly resetFilters = output<void>();
}
