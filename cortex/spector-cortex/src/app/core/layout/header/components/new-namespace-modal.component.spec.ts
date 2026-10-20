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
import { of, throwError } from 'rxjs';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NewNamespaceModalComponent } from './new-namespace-modal.component';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { CortexStateService } from '@core/services/cortex-state.service';

describe('NewNamespaceModalComponent', () => {
  let component: NewNamespaceModalComponent;
  let fixture: ComponentFixture<NewNamespaceModalComponent>;
  let mockDialogRef: { close: ReturnType<typeof vi.fn> };
  let mockApi: {
    createNamespace: ReturnType<typeof vi.fn>;
    setDefaultNamespace: ReturnType<typeof vi.fn>;
  };
  let mockState: {
    setCurrentNamespace: ReturnType<typeof vi.fn>;
  };
  let mockSnackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockDialogRef = { close: vi.fn() };
    mockApi = {
      createNamespace: vi
        .fn()
        .mockReturnValue(of({ namespaceId: 'ns-123', slug: 'agent-sandbox', status: 'ACTIVE' })),
      setDefaultNamespace: vi.fn().mockReturnValue(of({ success: true })),
    };
    mockState = {
      setCurrentNamespace: vi.fn(),
    };
    mockSnackBar = { open: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [NewNamespaceModalComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: SynapseApiService, useValue: mockApi },
        { provide: CortexStateService, useValue: mockState },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NewNamespaceModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('initializes with default values and cannot submit when slug is empty', () => {
    expect(component.slug()).toBe('');
    expect(component.type()).toBe('PERSONAL');
    expect(component.canSubmit()).toBe(false);
  });

  it('sanitizes slug input to lowercase hyphens', () => {
    component.onSlugInput('Research Lab 2026!');
    expect(component.slug()).toBe('research-lab-2026-');
    expect(component.canSubmit()).toBe(true);
  });

  it('submits valid namespace, sets active namespace, and closes dialog', () => {
    component.slug.set('agent-sandbox');
    component.displayName.set('Agent Sandbox');
    component.description.set('Diagnostic space');

    component.onSubmit();

    expect(mockApi.createNamespace).toHaveBeenCalledWith({
      slug: 'agent-sandbox',
      displayName: 'Agent Sandbox',
      type: 'PERSONAL',
      description: 'Diagnostic space',
    });
    expect(mockState.setCurrentNamespace).toHaveBeenCalledWith('agent-sandbox');
    expect(mockApi.setDefaultNamespace).toHaveBeenCalledWith('agent-sandbox');
    expect(mockSnackBar.open).toHaveBeenCalled();
    expect(mockDialogRef.close).toHaveBeenCalledWith(
      expect.objectContaining({ created: true, slug: 'agent-sandbox' }),
    );
  });

  it('displays error message if createNamespace fails', () => {
    mockApi.createNamespace.mockReturnValue(
      throwError(() => ({ error: { message: 'Slug already in use' } })),
    );

    component.slug.set('duplicate-slug');
    component.onSubmit();

    expect(component.errorMessage()).toBe('Slug already in use');
    expect(component.isSubmitting()).toBe(false);
    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('closes dialog with null on cancel', () => {
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith(null);
  });
});
