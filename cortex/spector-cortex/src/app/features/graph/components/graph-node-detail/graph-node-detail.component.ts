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
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ExplorerNode } from '../../strategies/view-strategy.interface';

@Component({
  selector: 'cortex-graph-node-detail',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './graph-node-detail.component.html',
  styleUrl: './graph-node-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphNodeDetailComponent {
  readonly node = input<ExplorerNode | null>(null);
  readonly isExpanding = input<boolean>(false);
  readonly isOverviewMode = input<boolean>(true);
  readonly expansionStatus = input<string | null>(null);

  readonly expansionDepth = model<number>(1);

  readonly expandNodeNeighbors = output<string>();
  readonly collapseToOverview = output<void>();
}
