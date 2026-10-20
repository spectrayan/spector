/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Routes } from '@angular/router';

export const MEMORIES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/memory-list/memory-table.component').then(m => m.MemoryTableComponent),
    title: 'Memories — Spector',
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/memory-detail/memory-detail.component').then(m => m.MemoryDetailComponent),
    title: 'Memory Detail — Spector',
  },
];
