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
import { catchError, throwError } from 'rxjs';

/**
 * Functional HTTP interceptor that injects the `X-API-Key` header
 * on every outgoing request. On 401 responses, shows a snackbar
 * notification with a link to the Settings page.
 */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = localStorage.getItem('spector_api_key') || 'spector-dev-key';

  const cloned = req.clone({
    setHeaders: { 'X-API-Key': apiKey },
  });

  return next(cloned).pipe(
    catchError((err) => {
      if (err.status === 401) {
        const router = inject(Router);
        const snack = inject(MatSnackBar);
        snack
          .open('API key is invalid or expired', 'Settings', { duration: 5000 })
          .onAction()
          .subscribe(() => router.navigate(['/settings']));
      }
      return throwError(() => err);
    })
  );
};
