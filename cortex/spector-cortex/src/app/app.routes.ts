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
import { ShellComponent } from './core/layout/shell/shell.component';
import { featureGuard } from './core/guards/feature.guard';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
    title: 'Sign In — Spector Cortex',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        title: 'Dashboard — Spector Cortex',
      },
      {
        path: 'playground',
        loadChildren: () =>
          import('./features/playground/playground.routes').then((m) => m.PLAYGROUND_ROUTES),
        canActivate: [featureGuard('playgroundEnabled', 'chatEnabled')],
        title: 'Playground — Spector Cortex',
      },
      {
        path: 'chat',
        redirectTo: 'playground/chat',
        pathMatch: 'full',
      },
      {
        path: 'query',
        loadComponent: () =>
          import('./features/query/query.component').then((m) => m.QueryComponent),
        title: 'Query — Spector Cortex',
      },
      {
        path: 'memories',
        loadChildren: () =>
          import('./features/memories/memories.routes').then((m) => m.MEMORIES_ROUTES),
      },
      {
        path: 'graph',
        loadComponent: () =>
          import('./features/graph/graph-explorer.component').then((m) => m.GraphExplorerComponent),
        title: 'Graph — Spector Cortex',
      },
      {
        path: 'memory-health',
        loadComponent: () =>
          import('./features/health/health.component').then((m) => m.HealthComponent),
        title: 'Health — Spector Cortex',
      },
      {
        path: 'sharing',
        loadComponent: () =>
          import('./features/sharing/sharing.component').then((m) => m.SharingComponent),
        title: 'Sharing & Access — Spector Cortex',
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
        title: 'Settings — Spector Cortex',
      },
      {
        path: 'admin',
        canActivate: [roleGuard('admin', 'super-admin')],
        loadChildren: () =>
          import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];

