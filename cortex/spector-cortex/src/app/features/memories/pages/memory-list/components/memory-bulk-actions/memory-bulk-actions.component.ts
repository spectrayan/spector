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
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-memory-bulk-actions',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-bulk-actions.component.html',
  styleUrl: './memory-bulk-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryBulkActionsComponent {
  readonly selectedCount = input<number>(0);

  readonly bulkReinforce = output<void>();
  readonly bulkSuppress = output<void>();
  readonly bulkForget = output<void>();
  readonly clearSelection = output<void>();
}
