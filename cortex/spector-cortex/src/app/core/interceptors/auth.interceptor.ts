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

import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Functional HTTP interceptor that manages request authorization:
 *
 * 1. Attaches `Authorization: Bearer <accessToken>` when an access token is available.
 * 2. When no token is available or `auth.enabled=false`, falls back to `X-API-Key`
 *    (from `localStorage.getItem('spector_api_key') || 'spector-dev-key'`).
 * 3. Bypasses authorization headers on public auth endpoints (`/login`, `/refresh`, `/config`).
 * 4. On 401 Unauthorized responses:
 *    - For protected endpoints, attempts refresh token rotation via `AuthService.refreshToken()`.
 *    - Retries the failed request with the newly issued access token.
 *    - If refresh fails or was already on refresh/login, triggers `AuthService.handleUnauthorized()`.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const snack = inject(MatSnackBar);

  const url = req.url;
  const isAuthBypass =
    url.endsWith('/auth/login') ||
    url.endsWith('/auth/refresh') ||
    url.endsWith('/auth/config') ||
    url.includes('/assets/');

  let authReq = req;

  if (!isAuthBypass) {
    const token = authService.getAccessToken();
    if (token) {
      authReq = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });
    } else {
      const apiKey = localStorage.getItem('spector_api_key') || 'spector-dev-key';
      authReq = req.clone({
        setHeaders: { 'X-API-Key': apiKey },
      });
    }
  }

  return next(authReq).pipe(
    catchError((err) => {
      if (err.status === 401) {
        // If 401 occurred on refresh or login itself, fail immediately
        if (url.endsWith('/auth/refresh') || url.endsWith('/auth/login')) {
          authService.handleUnauthorized();
          return throwError(() => err);
        }

        // If auth is enabled and a bearer token was attached, attempt refresh
        if (authService.isAuthEnabled() && authReq.headers.has('Authorization')) {
          return from(authService.refreshToken()).pipe(
            switchMap(() => {
              const newToken = authService.getAccessToken();
              if (newToken) {
                const retried = req.clone({
                  setHeaders: { Authorization: `Bearer ${newToken}` },
                });
                return next(retried);
              }
              authService.handleUnauthorized();
              return throwError(() => err);
            }),
            catchError((refreshErr) => {
              authService.handleUnauthorized();
              return throwError(() => refreshErr);
            })
          );
        }

        // Fallback for API key mode (auth disabled or missing token)
        snack
          .open('Authentication failed or expired', 'Sign In', { duration: 5000 })
          .onAction()
          .subscribe(() => {
            if (authService.isAuthEnabled()) {
              router.navigate(['/login']);
            } else {
              router.navigate(['/settings']);
            }
          });
      }
      return throwError(() => err);
    })
  );
};
