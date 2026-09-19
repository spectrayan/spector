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

import { Routes } from '@angular/router';

export const MEMORIES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/memory-list/memory-table.component').then(m => m.MemoryTableComponent),
    title: 'Memories — Spector Cortex',
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/memory-detail/memory-detail.component').then(m => m.MemoryDetailComponent),
    title: 'Memory Detail — Spector Cortex',
  },
];
