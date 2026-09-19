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

import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-query-result-card',
  standalone: true,
  imports: [
    RouterLink,
    MatChipsModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './query-result-card.component.html',
  styleUrl: './query-result-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryResultCardComponent {
  readonly result = input.required<any>();
  readonly rank = input.required<number>();

  protected formatScore(score: number): string {
    return score < 0.01 ? score.toExponential(2) : score.toFixed(4);
  }
}
