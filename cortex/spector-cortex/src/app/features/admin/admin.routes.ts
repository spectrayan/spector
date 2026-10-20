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
import { roleGuard } from '../../core/guards/role.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'overview',
    pathMatch: 'full',
  },
  {
    path: 'overview',
    loadComponent: () =>
      import('./pages/admin-overview.component').then(m => m.AdminOverviewComponent),
    title: 'Admin Overview — Spector Admin',
  },
  {
    path: 'tenants',
    canActivate: [roleGuard('super-admin')],
    loadComponent: () =>
      import('./pages/admin-tenants.component').then(m => m.AdminTenantsComponent),
    title: 'Tenants — Spector Admin',
  },
  {
    path: 'accounts',
    loadComponent: () =>
      import('./pages/admin-accounts.component').then(m => m.AdminAccountsComponent),
    title: 'Accounts & Agents — Spector Admin',
  },
  {
    path: 'namespaces',
    loadComponent: () =>
      import('./pages/admin-namespaces.component').then(m => m.AdminNamespacesComponent),
    title: 'Namespaces — Spector Admin',
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./pages/admin-analytics.component').then(m => m.AdminAnalyticsComponent),
    title: 'Analytics — Spector Admin',
  },
  {
    path: 'keys',
    loadComponent: () =>
      import('./pages/admin-keys.component').then(m => m.AdminKeysComponent),
    title: 'Keys & Encryption — Spector Admin',
  },
  {
    path: 'infra',
    loadComponent: () =>
      import('./pages/admin-infra.component').then(m => m.AdminInfraComponent),
    title: 'Infrastructure — Spector Admin',
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./pages/admin-audit.component').then(m => m.AdminAuditComponent),
    title: 'Audit Log — Spector Admin',
  },
  {
    path: 'break-glass',
    loadComponent: () =>
      import('./pages/admin-break-glass.component').then(m => m.AdminBreakGlassComponent),
    title: 'Break-Glass Access — Spector Admin',
  },
];

