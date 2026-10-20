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
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ThemeService } from './theme.service';

if (typeof localStorage === 'undefined') {
  const store: Record<string, string> = {};
  (globalThis as any).localStorage = {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      for (const k in store) delete store[k];
    },
  };
}

describe('ThemeService with View Transitions API (Milestone 3)', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.clear();
    if (typeof document !== 'undefined' && document.documentElement) {
      document.documentElement.removeAttribute('data-theme');
    }
    TestBed.configureTestingModule({
      providers: [ThemeService],
    });
    service = TestBed.inject(ThemeService);
  });

  it('initializes with dark theme by default when no localStorage value exists', () => {
    expect(service.isDark()).toBe(true);
    expect(service.themeLabel()).toBe('Dark');
    expect(service.themeIcon()).toBe('dark_mode');
  });

  it('toggles from dark to light mode when startViewTransition is absent', () => {
    expect(service.isDark()).toBe(true);
    service.toggle();
    expect(service.isDark()).toBe(false);
    expect(service.themeLabel()).toBe('Light');
    expect(service.themeIcon()).toBe('light_mode');
  });

  it('invokes document.startViewTransition when available', () => {
    const startViewTransitionMock = vi.fn((cb: () => void) => {
      cb();
    });
    (document as any).startViewTransition = startViewTransitionMock;

    try {
      service.toggle();
      expect(startViewTransitionMock).toHaveBeenCalled();
      expect(service.isDark()).toBe(false);
    } finally {
      delete (document as any).startViewTransition;
    }
  });

  it('skips startViewTransition when user prefers reduced motion', () => {
    const startViewTransitionMock = vi.fn((cb: () => void) => {
      cb();
    });
    (document as any).startViewTransition = startViewTransitionMock;

    const originalMatchMedia = window.matchMedia;
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('prefers-reduced-motion: reduce'),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    try {
      service.toggle();
      expect(startViewTransitionMock).not.toHaveBeenCalled();
      expect(service.isDark()).toBe(false);
    } finally {
      delete (document as any).startViewTransition;
      window.matchMedia = originalMatchMedia;
    }
  });

  it('resolves CSS variables and falls back cleanly in getCanvasColor', () => {
    const color = service.getCanvasColor('--bg-void', '#030712');
    expect(color).toBeDefined();
  });
});
