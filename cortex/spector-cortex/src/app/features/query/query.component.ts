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

import { Component, inject, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { QueryInputComponent } from '@shared/components/cognitive/query-input/query-input.component';
import { QueryHistoryComponent } from '@shared/components/cognitive/query-history/query-history.component';
import { PipelineFunnelComponent } from '@shared/components/cognitive/pipeline-funnel/pipeline-funnel.component';
import { CortexStateService } from '@core/services/cortex-state.service';
import { QueryResultsMetaComponent } from './components/query-results-meta/query-results-meta.component';
import { QueryResultCardComponent } from './components/query-result-card/query-result-card.component';
import { QueryPlaygroundSettingsComponent } from './components/query-playground-settings/query-playground-settings.component';

@Component({
  selector: 'cortex-query',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    QueryInputComponent,
    QueryHistoryComponent,
    PipelineFunnelComponent,
    QueryResultsMetaComponent,
    QueryResultCardComponent,
    QueryPlaygroundSettingsComponent,
  ],
  templateUrl: './query.component.html',
  styleUrl: './query.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryComponent {
  protected readonly state = inject(CortexStateService);

  protected readonly filterText = signal('');

  readonly filteredRecallResults = computed(() => {
    const list = this.state.recallResults();
    const search = this.filterText().toLowerCase().trim();
    if (!search) return list;
    return list.filter(result =>
      result.text.toLowerCase().includes(search) ||
      result.id.toLowerCase().includes(search) ||
      (result.synapticTags && result.synapticTags.some((t: string) => t.toLowerCase().includes(search))) ||
      result.memoryType.toLowerCase().includes(search)
    );
  });
}
