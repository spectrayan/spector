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
import { Router, NavigationEnd } from '@angular/router';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ShellComponent } from './shell.component';
import { CommandPaletteComponent } from '../command-palette/command-palette.component';
import { AuthService } from '@core/services/auth.service';
import { FeatureFlagService } from '@core/services/feature-flag.service';
import { EventStreamService } from '@core/services/event-stream.service';
import { ThemeService } from '@core/services/theme.service';
import { routes } from '../../../app.routes';
import { ADMIN_ROUTES } from '../../../features/admin/admin.routes';

describe('Role-Aware Shell & Command Palette (Issue #1062)', () => {
  let routerEvents$: Subject<any>;
  let isAdminSig = signal(false);
  let isSuperAdminSig = signal(false);
  let enabledFlags: Record<string, boolean> = {};

  let mockRouter: {
    url: string;
    events: Subject<any>;
    navigate: ReturnType<typeof vi.fn>;
  };
  let mockAuth: {
    isAdmin: typeof isAdminSig;
    isSuperAdmin: typeof isSuperAdminSig;
    currentUser: ReturnType<typeof signal>;
  };
  let mockFeatureFlags: {
    isEnabled: ReturnType<typeof vi.fn>;
    isAnyEnabled: ReturnType<typeof vi.fn>;
  };
  let mockEventStream: {
    connect: ReturnType<typeof vi.fn>;
  };
  let mockTheme: {
    toggle: ReturnType<typeof vi.fn>;
    isDark: ReturnType<typeof signal>;
    themeIcon: ReturnType<typeof signal>;
  };

  /** Build the complete set of valid concrete route paths from app.routes.ts and admin.routes.ts (excluding '**'). */
  function collectValidRoutePaths(): Set<string> {
    const valid = new Set<string>();
    const shellRoute = routes.find((r) => r.path === '');
    for (const child of shellRoute?.children ?? []) {
      if (!child.path || child.path === '**') continue;
      valid.add(`/${child.path}`);
    }
    for (const adminChild of ADMIN_ROUTES) {
      if (adminChild.path === '**') continue;
      if (adminChild.path === '') {
        valid.add('/admin');
      } else {
        valid.add(`/admin/${adminChild.path}`);
      }
    }
    return valid;
  }

  beforeEach(() => {
    routerEvents$ = new Subject<any>();
    isAdminSig = signal(false);
    isSuperAdminSig = signal(false);
    enabledFlags = {
      playgroundEnabled: false,
      chatEnabled: false,
    };

    mockRouter = {
      url: '/dashboard',
      events: routerEvents$,
      navigate: vi.fn().mockResolvedValue(true),
    };

    mockAuth = {
      isAdmin: isAdminSig,
      isSuperAdmin: isSuperAdminSig,
      currentUser: signal({
        userId: 'usr-1',
        username: 'alice',
        tenantId: 'default',
        roles: ['editor'],
        scopes: [],
      }),
    };

    mockFeatureFlags = {
      isEnabled: vi.fn((flag: string) => Boolean(enabledFlags[flag])),
      isAnyEnabled: vi.fn((...flags: string[]) => flags.some((f) => Boolean(enabledFlags[f]))),
    };

    mockEventStream = {
      connect: vi.fn(),
    };

    mockTheme = {
      toggle: vi.fn(),
      isDark: signal(true),
      themeIcon: signal('dark_mode'),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuth },
        { provide: FeatureFlagService, useValue: mockFeatureFlags },
        { provide: EventStreamService, useValue: mockEventStream },
        { provide: ThemeService, useValue: mockTheme },
      ],
    });
  });

  it('ensures no nav item, header menu link, or command palette link resolves to the ** fallback', () => {
    const validPaths = collectValidRoutePaths();
    const shell = TestBed.runInInjectionContext(() => new ShellComponent());
    const palette = TestBed.runInInjectionContext(() => new CommandPaletteComponent());

    for (const item of shell.workspaceNavItems) {
      expect(validPaths.has(item.route), `Workspace nav route ${item.route} must exist`).toBe(true);
    }

    for (const item of shell.adminNavItems) {
      expect(validPaths.has(item.route), `Admin nav route ${item.route} must exist`).toBe(true);
    }

    for (const item of palette.allItems) {
      if (item.route) {
        expect(validPaths.has(item.route), `Palette route ${item.route} (${item.id}) must exist`).toBe(
          true
        );
      }
    }

    // Header user-menu links
    const headerRoutes = [
      '/admin/overview',
      '/admin/accounts',
      '/admin/infra',
      '/dashboard',
      '/settings',
    ];
    for (const route of headerRoutes) {
      expect(validPaths.has(route), `Header route ${route} must exist`).toBe(true);
    }

    // Explicitly verify retired dead links are absent
    const deadRoutes = ['/users', '/admin/config', '/connectors', '/providers', '/templates', '/replay', '/studio'];
    const allPaletteRoutes = palette.allItems.map((i) => i.route).filter(Boolean);
    for (const dead of deadRoutes) {
      expect(allPaletteRoutes).not.toContain(dead);
    }
  });

  it('shows User Workspace nav without Playground for regular user when playgroundEnabled/chatEnabled are off', () => {
    const shell = TestBed.runInInjectionContext(() => new ShellComponent());

    expect(shell.isAdminConsole()).toBe(false);
    const routesShown = shell.navItems().map((i) => i.route);
    expect(routesShown).toEqual([
      '/dashboard',
      '/memories',
      '/query',
      '/graph',
      '/memory-health',
      '/sharing',
      '/settings',
    ]);
    expect(routesShown).not.toContain('/playground');
  });

  it('shows Playground nav entry when playgroundEnabled or chatEnabled is enabled', () => {
    enabledFlags['playgroundEnabled'] = true;
    const shell = TestBed.runInInjectionContext(() => new ShellComponent());

    const routesShown = shell.navItems().map((i) => i.route);
    expect(routesShown).toContain('/playground');
  });

  it('switches between Admin Console nav (/admin/**) and User Workspace nav (/) for tenant admin, excluding super-admin Tenants page', () => {
    isAdminSig.set(true);
    isSuperAdminSig.set(false);
    mockRouter.url = '/admin/overview';

    const shell = TestBed.runInInjectionContext(() => new ShellComponent());

    expect(shell.isAdminConsole()).toBe(true);
    const adminRoutesShown = shell.navItems().map((i) => i.route);
    expect(adminRoutesShown).toEqual([
      '/admin/overview',
      '/admin/accounts',
      '/admin/namespaces',
      '/admin/analytics',
      '/admin/keys',
      '/admin/infra',
      '/admin/audit',
      '/admin/break-glass',
    ]);
    expect(adminRoutesShown).not.toContain('/admin/tenants');

    // Switch back to Personal Workspace
    routerEvents$.next(new NavigationEnd(1, '/dashboard', '/dashboard'));
    expect(shell.isAdminConsole()).toBe(false);
    expect(shell.navItems().map((i) => i.route)).toContain('/dashboard');
    expect(shell.navItems().map((i) => i.route)).toContain('/memories');
  });

  it('includes /admin/tenants in Admin Console nav and Command Palette for super-admin', () => {
    isAdminSig.set(true);
    isSuperAdminSig.set(true);
    mockRouter.url = '/admin/tenants';

    const shell = TestBed.runInInjectionContext(() => new ShellComponent());
    const palette = TestBed.runInInjectionContext(() => new CommandPaletteComponent());

    expect(shell.isAdminConsole()).toBe(true);
    expect(shell.navItems().map((i) => i.route)).toContain('/admin/tenants');
    expect(palette.filteredItems().map((i) => i.id)).toContain('nav-admin-tenants');
  });
});
