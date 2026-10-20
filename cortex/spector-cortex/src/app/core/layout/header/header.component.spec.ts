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
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { HeaderComponent } from './header.component';
import { ThemeService } from '@core/services/theme.service';
import { CortexStateService } from '@core/services/cortex-state.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/services/auth.service';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { NewNamespaceModalComponent } from './components/new-namespace-modal.component';

describe('HeaderComponent (Milestone 3)', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  let mockTheme: any;
  let mockState: any;
  let mockNotif: any;
  let mockAuth: any;
  let mockApi: any;
  let mockDialog: any;

  beforeEach(async () => {
    mockTheme = {
      isDark: signal(true),
      themeIcon: signal('dark_mode'),
      toggle: vi.fn(),
    };

    mockState = {
      currentNamespace: signal('default'),
      currentNamespaceItem: signal({
        namespaceId: 'ns-default',
        slug: 'default',
        displayName: 'Default Workspace',
        owned: true,
      }),
      namespaces: signal([
        { namespaceId: 'ns-default', slug: 'default', displayName: 'Default', owned: true },
        { namespaceId: 'ns-research', slug: 'research', displayName: 'Research', owned: false },
      ]),
      connectionStatus: signal('connected'),
      totalMemoryCount: signal(14280),
      currentQueryTrace: signal(null),
      latestLatencyMs: signal('1.45'),
      setCurrentNamespace: vi.fn(),
      setAvailableNamespaces: vi.fn(),
    };

    mockNotif = {
      unreadCount: signal(0),
      notifications: signal([]),
      markAllRead: vi.fn(),
      clearAll: vi.fn(),
      dismiss: vi.fn(),
    };

    mockAuth = {
      isAdmin: signal(true),
      currentUser: signal({
        userId: 'usr-1',
        username: 'alice',
        roles: ['admin'],
      }),
      logout: vi.fn(),
    };

    mockApi = {
      introspectAccount: vi.fn().mockReturnValue(of({ accountId: 'usr-1', namespaces: [] })),
      listNamespaces: vi.fn().mockReturnValue(of([])),
      setDefaultNamespace: vi.fn().mockReturnValue(of({ success: true })),
    };

    mockDialog = {
      open: vi.fn().mockReturnValue({
        afterClosed: () => of({ created: true, slug: 'test-ns' }),
      }),
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ThemeService, useValue: mockTheme },
        { provide: CortexStateService, useValue: mockState },
        { provide: NotificationService, useValue: mockNotif },
        { provide: AuthService, useValue: mockAuth },
        { provide: SynapseApiService, useValue: mockApi },
        { provide: MatDialog, useValue: mockDialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders Spector brand name and version pill v0.2.0', () => {
    const el = fixture.nativeElement as HTMLElement;
    const brandName = el.querySelector('.brand-name')?.textContent?.trim();
    const versionPill = el.querySelector('.version-pill')?.textContent?.trim();

    expect(brandName).toBe('Spector');
    expect(versionPill).toBe('v0.2.0');
  });

  it('renders live SSE status pill with connected engram count', () => {
    const el = fixture.nativeElement as HTMLElement;
    const statusPill = el.querySelector('.status-pill');
    expect(statusPill?.classList.contains('connected')).toBe(true);
    expect(statusPill?.textContent).toContain('Connected (14280 engrams)');
  });

  it('renders active namespace slug and ownership badge in switcher button', () => {
    const el = fixture.nativeElement as HTMLElement;
    const activeSlug = el.querySelector('.ns-active-slug')?.textContent?.trim();
    const badge = el.querySelector('.ns-badge')?.textContent?.trim();

    expect(activeSlug).toBe('Default Workspace');
    expect(badge).toBe('Owned');
  });

  it('opens NewNamespaceModalComponent when openNewNamespaceModal is called', () => {
    const loadSpy = vi.spyOn(component, 'loadNamespaces');
    component.openNewNamespaceModal();

    expect(mockDialog.open).toHaveBeenCalledWith(
      NewNamespaceModalComponent,
      expect.objectContaining({ width: '480px' }),
    );
    expect(loadSpy).toHaveBeenCalled();
  });

  it('dispatches keydown event on Command Palette button click', () => {
    let keydownDispatched = false;
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        keydownDispatched = true;
      }
    };
    document.addEventListener('keydown', handler);

    try {
      component.openCommandPalette();
      expect(keydownDispatched).toBe(true);
    } finally {
      document.removeEventListener('keydown', handler);
    }
  });

  it('toggles theme when theme toggle button is clicked', () => {
    const el = fixture.nativeElement as HTMLElement;
    const toggleBtn = el.querySelector('#header-btn-theme-toggle') as HTMLButtonElement;
    toggleBtn?.click();

    expect(mockTheme.toggle).toHaveBeenCalled();
  });

  it('renders role-badged user profile pill', () => {
    const el = fixture.nativeElement as HTMLElement;
    const userName = el.querySelector('.user-name-text')?.textContent?.trim();
    const roleBadge = el.querySelector('.user-role-badge')?.textContent?.trim();

    expect(userName).toBe('alice');
    expect(roleBadge).toBe('admin');
  });
});
