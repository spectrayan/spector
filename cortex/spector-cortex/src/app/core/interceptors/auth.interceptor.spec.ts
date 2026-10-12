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
import { HttpRequest, HttpHandlerFn, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError, firstValueFrom } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let mockAuthService: {
    getAccessToken: ReturnType<typeof vi.fn>;
    isAuthEnabled: ReturnType<typeof vi.fn>;
    refreshToken: ReturnType<typeof vi.fn>;
    handleUnauthorized: ReturnType<typeof vi.fn>;
  };
  let mockRouter: { navigate: ReturnType<typeof vi.fn> };
  let mockSnackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    mockAuthService = {
      getAccessToken: vi.fn().mockReturnValue(null),
      isAuthEnabled: vi.fn().mockReturnValue(true),
      refreshToken: vi.fn().mockResolvedValue(undefined),
      handleUnauthorized: vi.fn(),
    };

    mockRouter = {
      navigate: vi.fn(),
    };

    mockSnackBar = {
      open: vi.fn().mockReturnValue({
        onAction: () => of(void 0),
      }),
    };

    const mockStorage: Record<string, string> = {};
    (globalThis as any).localStorage = {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, val: string) => {
        mockStorage[key] = val;
      },
      removeItem: (key: string) => {
        delete mockStorage[key];
      },
      clear: () => {
        Object.keys(mockStorage).forEach((k) => delete mockStorage[k]);
      },
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    if (typeof localStorage !== 'undefined' && localStorage.clear) {
      localStorage.clear();
    }
  });

  const runInterceptor = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    return TestBed.runInInjectionContext(() => authInterceptor(req, next));
  };

  it('attaches Authorization: Bearer when an access token is available', async () => {
    mockAuthService.getAccessToken.mockReturnValue('valid-jwt-token');

    const req = new HttpRequest('GET', '/api/v1/memory/recall');
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(runInterceptor(req, next));

    expect(capturedReq).not.toBeNull();
    expect(capturedReq!.headers.get('Authorization')).toBe('Bearer valid-jwt-token');
    expect(capturedReq!.headers.has('X-API-Key')).toBe(false);
  });

  it('attaches X-API-Key when no access token is available', async () => {
    mockAuthService.getAccessToken.mockReturnValue(null);
    localStorage.setItem('spector_api_key', 'my-custom-api-key');

    const req = new HttpRequest('GET', '/api/v1/memory/recall');
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(runInterceptor(req, next));

    expect(capturedReq).not.toBeNull();
    expect(capturedReq!.headers.get('X-API-Key')).toBe('my-custom-api-key');
    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  it('bypasses authorization headers on login and refresh endpoints', async () => {
    mockAuthService.getAccessToken.mockReturnValue('valid-jwt-token');

    const req = new HttpRequest('POST', '/api/v1/auth/login', {});
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(runInterceptor(req, next));

    expect(capturedReq).not.toBeNull();
    expect(capturedReq!.headers.has('Authorization')).toBe(false);
    expect(capturedReq!.headers.has('X-API-Key')).toBe(false);
  });

  it('attempts token refresh on 401 and retries original request', async () => {
    mockAuthService.getAccessToken
      .mockReturnValueOnce('expired-token')
      .mockReturnValueOnce('fresh-token');

    const req = new HttpRequest('GET', '/api/v1/memory/recall');
    let callCount = 0;
    const capturedReqs: HttpRequest<unknown>[] = [];

    const next: HttpHandlerFn = (r) => {
      callCount++;
      capturedReqs.push(r);
      if (callCount === 1) {
        return throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));
      }
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(runInterceptor(req, next));

    expect(mockAuthService.refreshToken).toHaveBeenCalled();
    expect(callCount).toBe(2);
    expect(capturedReqs[0].headers.get('Authorization')).toBe('Bearer expired-token');
    expect(capturedReqs[1].headers.get('Authorization')).toBe('Bearer fresh-token');
  });

  it('calls handleUnauthorized without refresh loop when 401 occurs on /auth/refresh', async () => {
    const req = new HttpRequest('POST', '/api/v1/auth/refresh', {});
    const next: HttpHandlerFn = () =>
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));

    await expect(firstValueFrom(runInterceptor(req, next))).rejects.toThrow();

    expect(mockAuthService.refreshToken).not.toHaveBeenCalled();
    expect(mockAuthService.handleUnauthorized).toHaveBeenCalled();
  });
});
