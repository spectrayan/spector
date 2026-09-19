/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

import '@angular/compiler';
import { Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { SettingsStateService } from './settings-state.service';
import { SynapseApiService } from '../../../core/services/synapse-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ApiKeyService } from '../../../core/services/api-key.service';
import { CortexSnackbarService } from '../../../shared/services/cortex-snackbar.service';

describe('SettingsStateService — Dynamic AI Configuration & Hot-Swap', () => {
  let service: SettingsStateService;
  let mockApi: any;
  let mockToast: any;
  let mockAuth: any;
  let mockHttp: any;
  let mockApiKey: any;

  beforeEach(() => {
    vi.useFakeTimers();

    mockApi = {
      listAvailableProviders: vi.fn().mockReturnValue(of({ providers: [] })),
      listConfigCategories: vi.fn().mockReturnValue(
        of({
          categories: [
            { key: 'memory', label: 'Memory Core' },
            { key: 'recall', label: 'Recall & Search' },
            { key: 'hnsw', label: 'HNSW Index' },
            { key: 'rag', label: 'RAG (Deprecated)' }, // Should be filtered out
          ],
        })
      ),
      getConfigSchema: vi.fn().mockImplementation((cat: string) =>
        of({
          category: cat,
          fields: [
            {
              key: cat === 'memory' ? 'decay-rate' : 'scoring-mode',
              type: cat === 'memory' ? 'number' : 'string',
              defaultValue: cat === 'memory' ? 0.05 : 'COGNITIVE',
              description: 'Test description',
              applyMode: 'LIVE',
              min: 0.0,
              max: 1.0,
            },
          ],
        })
      ),
      getAnnotatedConfig: vi.fn().mockReturnValue(of({})),
      saveConfig: vi.fn().mockReturnValue(of({ status: 'applied', category: 'memory' })),
      deleteConfig: vi.fn().mockReturnValue(of({ status: 'deleted' })),
    };

    mockToast = {
      success: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
    };

    mockAuth = {
      logout: vi.fn(),
    };

    mockHttp = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    };

    mockApiKey = {
      listApiKeys: vi.fn().mockReturnValue(of([])),
    };

    const injector = Injector.create({
      providers: [
        { provide: SettingsStateService, useClass: SettingsStateService },
        { provide: SynapseApiService, useValue: mockApi },
        { provide: CortexSnackbarService, useValue: mockToast },
        { provide: AuthService, useValue: mockAuth },
        { provide: HttpClient, useValue: mockHttp },
        { provide: ApiKeyService, useValue: mockApiKey },
      ],
    });

    service = injector.get(SettingsStateService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads dynamic categories filtering out deprecated rag alias', () => {
    service.loadAiConfig();

    const categories = service.aiConfigCategories();
    expect(categories.length).toBe(3);
    expect(categories.map((c) => c.key)).toEqual(['memory', 'recall', 'hnsw']);
    expect(service.aiConfigFields()['memory']).toBeDefined();
    expect(service.aiConfigFields()['memory'][0].key).toBe('decay-rate');
    expect(service.aiConfigFields()['memory'][0].applyMode).toBe('LIVE');
  });

  it('records dirty field and auto-saves debounced to DB and SpectorMemory', () => {
    service.loadAiConfig();

    service.onAiFieldChange('memory', 'decay-rate', 0.15);

    expect(service.aiConfigDirty()).toBe(true);
    expect(mockApi.saveConfig).not.toHaveBeenCalled();

    // Fast-forward past 600ms debounce
    vi.advanceTimersByTime(700);

    expect(mockApi.saveConfig).toHaveBeenCalledWith('memory', 'user', { 'decay-rate': 0.15 });
    expect(service.aiLastSaveStatus().text).toContain('Applied to SpectorMemory');
    expect(service.aiLastSaveStatus().mode).toBe('applied');
  });

  it('reverts field to system default when revertAiField is called', () => {
    service.loadAiConfig();

    const field = service.aiConfigFields()['memory'][0];
    service.revertAiField('memory', field);

    // Immediate save
    expect(mockApi.saveConfig).toHaveBeenCalledWith('memory', 'user', { 'decay-rate': 0.05 });
  });

  it('resets category to defaults calling deleteConfig and reloading schema', () => {
    service.resetAiCategory('memory');

    expect(mockApi.deleteConfig).toHaveBeenCalledWith('memory', 'user');
    expect(mockToast.success).toHaveBeenCalledWith(expect.stringContaining('Reset memory to system defaults'));
  });
});
