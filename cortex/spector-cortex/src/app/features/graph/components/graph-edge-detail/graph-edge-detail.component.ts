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
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ExplorerEdge } from '../../strategies/view-strategy.interface';

@Component({
  selector: 'cortex-graph-edge-detail',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
  ],
  templateUrl: './graph-edge-detail.component.html',
  styleUrl: './graph-edge-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphEdgeDetailComponent {
  readonly edge = input<ExplorerEdge | null>(null);

  edgeIcon(type: string): string {
    switch (type) {
      case 'HEBBIAN':
        return 'link';
      case 'TEMPORAL':
        return 'timeline';
      case 'ENTITY':
        return 'category';
      default:
        return 'device_hub';
    }
  }
}
