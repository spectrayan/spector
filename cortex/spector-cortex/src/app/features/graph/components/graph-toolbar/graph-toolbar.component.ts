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
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-graph-toolbar',
  standalone: true,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './graph-toolbar.component.html',
  styleUrl: './graph-toolbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraphToolbarComponent {
  readonly loadedCount = input<number>(0);
  readonly totalAvailable = input<number | string>(0);
  readonly isLoadingMore = input<boolean>(false);
  readonly showTopologyStats = input<boolean>(false);
  readonly isCapturing = input<boolean>(false);
  readonly isSharing = input<boolean>(false);
  readonly timeTravelMode = input<boolean>(false);

  readonly showHebbian = model<boolean>(true);
  readonly showTemporal = model<boolean>(true);
  readonly showEntity = model<boolean>(true);
  readonly showLabels = model<boolean>(true);
  readonly showFilters = model<boolean>(false);
  readonly showSharePanel = model<boolean>(false);

  readonly expandScanRange = output<void>();
  readonly toggleTopologyStats = output<void>();
  readonly captureScreenshot = output<void>();
  readonly shareScreenshot = output<void>();
  readonly copyShareLink = output<void>();
  readonly toggleSharePanel = output<void>();
  readonly toggleTimeTravel = output<void>();
  readonly replayQueryAnimation = output<void>();
  readonly refreshGraph = output<void>();
}
