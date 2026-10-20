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

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  MelExecuteRequest,
  MelExecuteResponse,
  MelValidateRequest,
  MelValidateResponse,
  MelSchemaResponse,
} from '../models/mel.models';

/**
 * Service for communicating with the backend MEL (Memory Engine Language) endpoints:
 * - POST /api/v1/mel/execute
 * - POST /api/v1/mel/validate
 * - GET  /api/v1/mel/schema
 */
@Injectable({ providedIn: 'root' })
export class MelApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * Executes a single MEL statement or multi-statement script.
   */
  execute(script: string, namespace?: string): Observable<MelExecuteResponse> {
    const payload: MelExecuteRequest = { script, namespace };
    return this.http.post<MelExecuteResponse>(`${this.baseUrl}/mel/execute`, payload);
  }

  /**
   * Validates MEL script syntax without executing it (dry-run lex/parse).
   */
  validate(script: string): Observable<MelValidateResponse> {
    const payload: MelValidateRequest = { script };
    return this.http.post<MelValidateResponse>(`${this.baseUrl}/mel/validate`, payload).pipe(
      catchError((err): Observable<MelValidateResponse> => {
        return of({
          valid: false,
          statement_count: 0,
          statements: [],
          diagnostics: [
            {
              line: 1,
              column: 1,
              message: err?.error?.message || err?.message || 'Syntax validation failed',
              severity: 'ERROR' as const,
            },
          ],
        });
      })
    );
  }

  /**
   * Retrieves MEL language schema: keywords, tiers, clause templates, snippets.
   */
  getSchema(): Observable<MelSchemaResponse> {
    return this.http.get<MelSchemaResponse>(`${this.baseUrl}/mel/schema`);
  }

  /**
   * Reinforces a memory engram candidate (increments Hebbian association or recall count).
   */
  reinforce(memoryId: string, delta: number = 0.1): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/memory/${encodeURIComponent(memoryId)}/reinforce`, {
      delta,
    });
  }
}
