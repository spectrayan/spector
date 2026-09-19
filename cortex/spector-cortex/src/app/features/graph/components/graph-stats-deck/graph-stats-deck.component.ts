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

import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EntityTypeStats, RelationTypeStats } from '@core/services/memory-table.service';

@Component({
  selector: 'cortex-graph-stats-deck',
  standalone: true,
  imports: [
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './graph-stats-deck.component.html',
  styleUrl: './graph-stats-deck.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphStatsDeckComponent {
  readonly loading = input<boolean>(false);
  readonly error = input<string | null>(null);
  readonly entityTypesStats = input<EntityTypeStats[]>([]);
  readonly relationTypesStats = input<RelationTypeStats[]>([]);

  readonly close = output<void>();
}
