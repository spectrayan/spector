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
import { FeatureFlagService } from '../services/feature-flag.service';

/**
 * Functional route guard that gates navigation on one or more feature flags.
 * Access is granted if ANY of the supplied flags are enabled.
 *
 * Usage in routes:
 * ```typescript
 * {
 *   path: 'playground',
 *   loadComponent: () => import('./features/chat/agent-chat.component').then(m => m.AgentChatComponent),
 *   canActivate: [featureGuard('playgroundEnabled', 'chatEnabled')],
 * }
 * ```
 *
 * Redirects to `/dashboard` when all specified flags are disabled.
 */
export function featureGuard(...flags: string[]): CanActivateFn {
  return () => {
    const featureService = inject(FeatureFlagService);
    const router = inject(Router);

    if (flags.some(flag => featureService.isEnabled(flag))) {
      return true;
    }

    return router.createUrlTree(['/dashboard']);
  };
}

