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

import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let mockAuthService: {
    whenReady: Promise<void>;
    isAuthEnabled: ReturnType<typeof vi.fn>;
    isAuthenticated: ReturnType<typeof vi.fn>;
  };
  let mockRouter: {
    createUrlTree: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockAuthService = {
      whenReady: Promise.resolve(),
      isAuthEnabled: vi.fn().mockReturnValue(true),
      isAuthenticated: vi.fn().mockReturnValue(false),
    };

    mockRouter = {
      createUrlTree: vi.fn().mockImplementation((commands, navExtras) => ({
        commands,
        queryParams: navExtras?.queryParams,
      } as unknown as UrlTree)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });
  });

  const runGuard = async (url: string = '/dashboard') => {
    const route = {} as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state));
  };

  it('allows navigation when auth is disabled on backend (dev mode)', async () => {
    mockAuthService.isAuthEnabled.mockReturnValue(false);
    mockAuthService.isAuthenticated.mockReturnValue(false);

    const result = await runGuard('/memories');
    expect(result).toBe(true);
  });

  it('allows navigation when user is authenticated with a valid token', async () => {
    mockAuthService.isAuthEnabled.mockReturnValue(true);
    mockAuthService.isAuthenticated.mockReturnValue(true);

    const result = await runGuard('/dashboard');
    expect(result).toBe(true);
  });

  it('redirects unauthenticated users to /login with returnUrl query param', async () => {
    mockAuthService.isAuthEnabled.mockReturnValue(true);
    mockAuthService.isAuthenticated.mockReturnValue(false);

    const result = (await runGuard('/admin/config')) as any;
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/admin/config' },
    });
    expect(result.commands).toEqual(['/login']);
    expect(result.queryParams).toEqual({ returnUrl: '/admin/config' });
  });
});
