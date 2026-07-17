import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { FeatureFlagService } from '../services/feature-flag.service';

/**
 * Functional route guard that gates navigation on a feature flag.
 *
 * Usage in routes:
 * ```typescript
 * {
 *   path: 'chat',
 *   loadComponent: () => import('./chat.component').then(m => m.ChatComponent),
 *   canActivate: [featureGuard('chatEnabled')],
 * }
 * ```
 *
 * Redirects to `/memories` when the flag is disabled.
 */
export function featureGuard(flag: string): CanActivateFn {
  return () => {
    const featureService = inject(FeatureFlagService);
    const router = inject(Router);

    if (featureService.isEnabled(flag)) {
      return true;
    }

    return router.createUrlTree(['/memories']);
  };
}
