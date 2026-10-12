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

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Functional route guard that gates access based on user roles.
 *
 * Usage:
 * ```typescript
 * {
 *   path: 'admin',
 *   canActivate: [roleGuard('admin', 'super-admin')],
 *   loadChildren: () => import('./features/admin/admin.routes'),
 * }
 * ```
 *
 * Behavior:
 * - Awaits `authService.whenReady`.
 * - When backend reports `auth.enabled=false` (dev mode), access is permitted.
 * - When unauthenticated, redirects to `/login` with `returnUrl`.
 * - When user holds any of the specified roles, access is permitted.
 * - When user lacks the required role (e.g. editor accessing /admin), redirects to `/dashboard`.
 */
export function roleGuard(...allowedRoles: string[]): CanActivateFn {
  return async (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    await authService.whenReady;

    if (!authService.isAuthEnabled()) {
      return true;
    }

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url },
      });
    }

    const hasAllowedRole = allowedRoles.some((role) => authService.hasRole(role));
    if (hasAllowedRole) {
      return true;
    }

    return router.createUrlTree(['/dashboard']);
  };
}
