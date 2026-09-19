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

import { Component, ChangeDetectionStrategy, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'cortex-query-results-meta',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './query-results-meta.component.html',
  styleUrl: './query-results-meta.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryResultsMetaComponent {
  readonly filteredCount = input<number>(0);
  readonly totalCount = input<number>(0);
  readonly queryTimeMs = input<number>(0);
  readonly totalMemories = input<number>(0);
  readonly profile = input<string>('');
  readonly lastQueryText = input<string>('');

  readonly filterText = model<string>('');
}
