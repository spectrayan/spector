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
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

describe('roleGuard', () => {
  let mockAuthService: {
    whenReady: Promise<void>;
    isAuthEnabled: ReturnType<typeof vi.fn>;
    isAuthenticated: ReturnType<typeof vi.fn>;
    hasRole: ReturnType<typeof vi.fn>;
  };
  let mockRouter: {
    createUrlTree: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockAuthService = {
      whenReady: Promise.resolve(),
      isAuthEnabled: vi.fn().mockReturnValue(true),
      isAuthenticated: vi.fn().mockReturnValue(true),
      hasRole: vi.fn().mockReturnValue(false),
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

  const runGuard = async (allowedRoles: string[], url: string = '/admin') => {
    const route = {} as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    const guardFn = roleGuard(...allowedRoles);
    return TestBed.runInInjectionContext(() => guardFn(route, state));
  };

  it('allows navigation when user possesses one of the allowed roles (admin)', async () => {
    mockAuthService.hasRole.mockImplementation((r: string) => r === 'admin');

    const result = await runGuard(['admin', 'super-admin'], '/admin');
    expect(result).toBe(true);
  });

  it('allows navigation when user possesses super-admin role', async () => {
    mockAuthService.hasRole.mockImplementation((r: string) => r === 'super-admin');

    const result = await runGuard(['super-admin'], '/admin/tenants');
    expect(result).toBe(true);
  });

  it('redirects user lacking required role (e.g. editor) to /dashboard', async () => {
    mockAuthService.hasRole.mockReturnValue(false);

    const result = (await runGuard(['admin', 'super-admin'], '/admin')) as any;
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
    expect(result.commands).toEqual(['/dashboard']);
  });

  it('redirects unauthenticated callers to /login with returnUrl', async () => {
    mockAuthService.isAuthenticated.mockReturnValue(false);

    const result = (await runGuard(['admin'], '/admin/cache')) as any;
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/admin/cache' },
    });
    expect(result.commands).toEqual(['/login']);
    expect(result.queryParams).toEqual({ returnUrl: '/admin/cache' });
  });

  it('bypasses role check when auth is disabled on backend (dev mode)', async () => {
    mockAuthService.isAuthEnabled.mockReturnValue(false);
    mockAuthService.isAuthenticated.mockReturnValue(false);

    const result = await runGuard(['admin'], '/admin');
    expect(result).toBe(true);
  });
});
