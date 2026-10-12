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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthService } from '@core/services/auth.service';
import { ThemeService } from '@core/services/theme.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let mockAuthService: {
    login: ReturnType<typeof vi.fn>;
    isAuthenticated: ReturnType<typeof vi.fn>;
    authConfig: ReturnType<typeof vi.fn>;
  };
  let mockRouter: { navigateByUrl: ReturnType<typeof vi.fn> };
  let mockActivatedRoute: {
    snapshot: { queryParams: Record<string, string> };
  };
  let mockThemeService: { isDark: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockAuthService = {
      login: vi.fn().mockResolvedValue({
        accessToken: 'mock-token',
        refreshToken: 'mock-refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        userId: 'user-1',
        username: 'alice',
        roles: ['admin'],
        tenantId: 'tenant-1',
        mustChangePassword: false,
      }),
      isAuthenticated: vi.fn().mockReturnValue(false),
      authConfig: vi.fn().mockReturnValue({ authEnabled: true, oidcEnabled: false }),
    };

    mockRouter = {
      navigateByUrl: vi.fn().mockResolvedValue(true),
    };

    mockActivatedRoute = {
      snapshot: { queryParams: {} },
    };

    mockThemeService = {
      isDark: vi.fn().mockReturnValue(true),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('creates the login component with invalid empty form', () => {
    expect(component).toBeTruthy();
    expect(component.loginForm.valid).toBe(false);
  });

  it('redirects to returnUrl on init if already authenticated', () => {
    mockAuthService.isAuthenticated.mockReturnValue(true);
    mockActivatedRoute.snapshot.queryParams = { returnUrl: '/admin/cache' };

    component.ngOnInit();

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/admin/cache');
  });

  it('submits credentials and navigates to default /dashboard on success', async () => {
    component.loginForm.setValue({
      username: 'admin',
      password: 'secretPassword123',
    });
    expect(component.loginForm.valid).toBe(true);

    await component.onSubmit();

    expect(mockAuthService.login).toHaveBeenCalledWith('admin', 'secretPassword123');
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(component.errorMessage()).toBeNull();
  });

  it('navigates to returnUrl query param on successful login', async () => {
    mockActivatedRoute.snapshot.queryParams = { returnUrl: '/memories' };
    component.loginForm.setValue({
      username: 'admin',
      password: 'secretPassword123',
    });

    await component.onSubmit();

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/memories');
  });

  it('displays uniform error message when login fails with 401', async () => {
    mockAuthService.login.mockRejectedValue({ status: 401 });
    component.loginForm.setValue({
      username: 'wronguser',
      password: 'wrongpassword',
    });

    await component.onSubmit();

    expect(component.errorMessage()).toBe('Invalid credentials');
    expect(mockRouter.navigateByUrl).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);
  });
});
