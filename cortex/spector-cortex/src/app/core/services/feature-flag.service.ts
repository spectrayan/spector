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

import { Injectable, inject, signal, computed, Signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { tap, catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/**
 * Centralized feature-flag service for Spector Cortex.
 *
 * Loads feature flags from the Synapse backend (`GET /api/v1/features`)
 * during app initialization. Falls back to safe defaults when the
 * backend is unreachable (LLM-dependent features off by default).
 *
 * Usage:
 * - `isEnabled('playgroundEnabled')` — synchronous boolean check
 * - `isAnyEnabled('playgroundEnabled', 'chatEnabled')` — synchronous check across aliases
 * - `enabled('playgroundEnabled')` — reactive `Signal<boolean>` for templates
 */
@Injectable({ providedIn: 'root' })
export class FeatureFlagService {
  private readonly http = inject(HttpClient);
  private readonly flags = signal<Record<string, boolean>>({});
  private readonly loaded = signal(false);

  /** Safe defaults — LLM / agent features OFF, core cognitive features ON. */
  private readonly defaults: Record<string, boolean> = {
    playgroundEnabled: false,
    chatEnabled: false,
    agentChatEnabled: false,
    agentWorkspacesEnabled: false,
    tagExtractionEnabled: true,
    entityExtractionEnabled: true,
    reflectionEnabled: true,
    gpuAccelerationEnabled: false,
    clusterModeEnabled: false,
    connectorsEnabled: false,
    benchmarksEnabled: false,
    advancedVisualizationsEnabled: true,
    governanceEnabled: true,
    mcpServerEnabled: true,
  };

  /**
   * Loads feature flags from Synapse backend.
   * Called during `APP_INITIALIZER` — the app won't bootstrap until this resolves.
   */
  loadFlags(): Observable<void> {
    return this.http.get<Record<string, boolean>>(`${environment.apiUrl}/features`).pipe(
      tap(flags => {
        this.flags.set({ ...this.defaults, ...flags });
        this.loaded.set(true);
      }),
      catchError(() => {
        this.flags.set(this.defaults);
        this.loaded.set(true);
        return of(void 0);
      }),
      map(() => void 0),
    );
  }

  /** Synchronous check — returns `false` for unknown flags. */
  isEnabled(flag: string): boolean {
    return this.flags()[flag] ?? false;
  }

  /** Synchronous check — returns `true` if any of the specified flags are enabled. */
  isAnyEnabled(...flags: string[]): boolean {
    return flags.some(flag => this.isEnabled(flag));
  }

  /** Reactive signal — use in templates or `computed()` chains. */
  enabled(flag: string): Signal<boolean> {
    return computed(() => this.flags()[flag] ?? false);
  }

  /** Whether flags have finished loading (regardless of success/failure). */
  isLoaded(): boolean {
    return this.loaded();
  }
}

/**
 * APP_INITIALIZER factory — ensures feature flags are loaded before the app renders.
 */
export function initializeFeatureFlags(service: FeatureFlagService): () => Observable<void> {
  return () => service.loadFlags();
}

