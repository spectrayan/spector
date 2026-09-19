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
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'cortex-memory-table-toolbar',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-table-toolbar.component.html',
  styleUrl: './memory-table-toolbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryTableToolbarComponent {
  readonly totalCount = input<number>(0);
  readonly searchText = model<string>('');
  readonly showAdvancedFilters = model<boolean>(false);
  readonly tierFilter = model<string | null>(null);
  readonly showTombstoned = input<boolean>(false);
  readonly tiers = input<string[]>(['WORKING', 'EPISODIC', 'SEMANTIC', 'PROCEDURAL']);
  readonly tierColors = input<Record<string, string>>({});

  readonly toggleTombstoned = output<void>();
  readonly reflect = output<void>();
  readonly addMemory = output<void>();
}
