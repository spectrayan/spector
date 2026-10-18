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

import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { CortexStateService } from '@core/services/cortex-state.service';
import { AuthService } from '@core/services/auth.service';
import { SharingComponent } from './sharing.component';
import { ShareNamespaceDialogComponent } from '../admin/components/share-namespace-dialog/share-namespace-dialog.component';

describe('SharingComponent — Namespace Access & Audit Transparency (#1067)', () => {
  let apiMock: Record<string, ReturnType<typeof vi.fn>>;
  let dialogMock: { open: ReturnType<typeof vi.fn> };
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let state: CortexStateService;

  beforeEach(() => {
    apiMock = {
      getNamespace: vi.fn().mockReturnValue(
        of({
          namespaceId: 'ns-101',
          slug: 'research-lab',
          ownerAccountId: 'usr-alice',
          type: 'TEAM',
          status: 'ACTIVE',
          legalHold: false,
        }),
      ),
      listNamespaceGrants: vi.fn().mockReturnValue(
        of([
          {
            grantId: 'gr-1',
            granteeAccountId: 'usr-bob',
            namespaceId: 'ns-101',
            role: 'WRITER',
            grantedBy: 'usr-alice',
            grantedAt: '2026-10-18T08:00:00Z',
          },
          {
            grantId: 'gr-2',
            granteeAccountId: 'agent-triage',
            namespaceId: 'ns-101',
            role: 'READER',
            grantedBy: 'usr-alice',
            grantedAt: '2026-10-18T08:05:00Z',
          },
        ]),
      ),
      createNamespaceGrant: vi.fn().mockReturnValue(
        of({
          grantId: 'gr-3',
          granteeAccountId: 'agent-coder',
          namespaceId: 'ns-101',
          role: 'WRITER',
        }),
      ),
      revokeNamespaceGrant: vi.fn().mockReturnValue(of(undefined)),
      listNamespaceAuditEvents: vi.fn().mockReturnValue(
        of([
          {
            id: 'aud-ns-1',
            createdAt: '2026-10-18T08:10:00Z',
            actorId: 'usr-alice',
            action: 'GRANT_NAMESPACE',
            targetType: 'NAMESPACE',
            targetId: 'ns-101',
            outcome: 'SUCCESS',
            details: { granteeAccountId: 'agent-triage', role: 'READER' },
          },
          {
            id: 'aud-bg-2',
            createdAt: '2026-10-18T09:00:00Z',
            actorId: 'admin-ops',
            action: 'BREAK_GLASS_APPROVE',
            targetType: 'BREAK_GLASS',
            targetId: 'bg-session-9',
            outcome: 'SUCCESS',
            details: { ticketRef: 'INC-7700', durationMinutes: 30 },
          },
        ]),
      ),
    };

    dialogMock = {
      open: vi.fn().mockReturnValue({ afterClosed: () => of(true) }),
    };
    snackBarMock = { open: vi.fn() };

    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, SharingComponent],
      providers: [
        CortexStateService,
        { provide: SynapseApiService, useValue: apiMock },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              userId: 'usr-alice',
              username: 'alice',
              tenantId: 'acme',
              roles: ['editor'],
              scopes: ['memory:read', 'memory:write'],
            }),
            isAdmin: signal(false),
            isSuperAdmin: signal(false),
          },
        },
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });

    state = TestBed.inject(CortexStateService);
    state.setCurrentNamespace('research-lab');
  });

  it('renders Who Can Access This Namespace (owner, human collaborator, agent principal) and break-glass audit history', () => {
    const fixture = TestBed.createComponent(SharingComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    expect(apiMock['getNamespace']).toHaveBeenCalledWith('research-lab');
    expect(apiMock['listNamespaceGrants']).toHaveBeenCalledWith('research-lab');
    expect(apiMock['listNamespaceAuditEvents']).toHaveBeenCalledWith('research-lab', 50);

    const el: HTMLElement = fixture.nativeElement;
    const grantsTable = el.querySelector('#sharing-grants-table');
    expect(grantsTable?.textContent).toContain('usr-alice');
    expect(grantsTable?.textContent).toContain('OWNER');
    expect(grantsTable?.textContent).toContain('usr-bob');
    expect(grantsTable?.textContent).toContain('WRITER');
    expect(grantsTable?.textContent).toContain('agent-triage');
    expect(grantsTable?.textContent).toContain('AGENT');

    // Break-glass history banner is displayed and filterable
    const bgBanner = el.querySelector('#sharing-break-glass-history-banner');
    expect(bgBanner).toBeTruthy();
    expect(bgBanner?.textContent).toContain('Break-Glass Emergency Access Recorded (1 event(s))');

    comp.showBreakGlassOnly.set(true);
    fixture.detectChanges();
    expect(comp.filteredAuditEvents().length).toBe(1);
    expect(comp.filteredAuditEvents()[0].action).toBe('BREAK_GLASS_APPROVE');
  });

  it('creates and revokes grants, opens ShareNamespaceDialogComponent, and reloads on currentNamespace change', () => {
    const fixture = TestBed.createComponent(SharingComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    // Create a grant for an agent principal
    comp.newGranteeId = 'agent-coder';
    comp.newPrincipalKind = 'AGENT';
    comp.newRole = 'WRITER';
    comp.createGrant();

    expect(apiMock['createNamespaceGrant']).toHaveBeenCalledWith('research-lab', {
      granteeAccountId: 'agent-coder',
      role: 'WRITER',
      expiresAt: undefined,
    });

    // Revoke a grant
    comp.revokeGrant('gr-1');
    expect(apiMock['revokeNamespaceGrant']).toHaveBeenCalledWith('research-lab', 'gr-1');

    // Open ShareNamespaceDialogComponent
    comp.openShareDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(
      ShareNamespaceDialogComponent,
      expect.objectContaining({
        data: { slug: 'research-lab', namespaceId: 'ns-101' },
      }),
    );

    // Switch active namespace via CortexStateService and verify automatic reload
    state.setCurrentNamespace('personal-notes');
    fixture.detectChanges();
    expect(apiMock['getNamespace']).toHaveBeenCalledWith('personal-notes');
    expect(apiMock['listNamespaceGrants']).toHaveBeenCalledWith('personal-notes');
    expect(apiMock['listNamespaceAuditEvents']).toHaveBeenCalledWith('personal-notes', 50);
  });
});
