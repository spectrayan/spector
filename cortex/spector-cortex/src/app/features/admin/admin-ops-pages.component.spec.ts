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
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SynapseApiService } from '@core/services/synapse-api.service';
import { AuthService } from '@core/services/auth.service';
import { AdminInfraComponent } from './pages/admin-infra.component';
import { AdminAuditComponent } from './pages/admin-audit.component';
import { AdminBreakGlassComponent } from './pages/admin-break-glass.component';

describe('Admin Infra, Audit & Break-Glass Pages (#1066)', () => {
  let isSuperAdminSignal = signal(true);
  let isAdminSignal = signal(true);
  let currentUserSignal = signal<any>({
    userId: 'admin-alice',
    username: 'alice',
    displayName: 'Alice Admin',
    profile: 'TEAM',
    roles: ['admin', 'super-admin'],
    scopes: ['admin'],
    tenantId: 'acme',
  });
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let apiMock: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    isSuperAdminSignal = signal(true);
    isAdminSignal = signal(true);
    currentUserSignal = signal<any>({
      userId: 'admin-alice',
      username: 'alice',
      displayName: 'Alice Admin',
      profile: 'TEAM',
      roles: ['admin', 'super-admin'],
      scopes: ['admin'],
      tenantId: 'acme',
    });
    snackBarMock = { open: vi.fn() };

    apiMock = {
      // Infra: Providers
      listGenerationProviders: vi.fn().mockReturnValue(
        of([
          { name: 'openai', displayName: 'OpenAI', active: true, model: 'gpt-4o' },
          { name: 'anthropic', displayName: 'Anthropic', active: false, model: 'claude-3-7-sonnet' },
        ]),
      ),
      listEmbeddingProviders: vi.fn().mockReturnValue(
        of([{ name: 'onnx-local', displayName: 'ONNX Local', active: true, dimensions: 384 }]),
      ),
      activateGenerationProvider: vi.fn().mockReturnValue(of({ status: 'activated' })),
      activateEmbeddingProvider: vi.fn().mockReturnValue(of({ status: 'activated' })),
      // Infra: Config
      getConfigSchema: vi.fn().mockReturnValue(
        of({
          fields: [
            {
              key: 'efSearch',
              description: 'HNSW search exploration factor',
              defaultValue: 64,
              applyMode: 'LIVE',
            },
          ],
        }),
      ),
      getAnnotatedConfig: vi.fn().mockReturnValue(
        of({
          efSearch: { value: 64, source: 'tenant' },
        }),
      ),
      saveConfig: vi.fn().mockReturnValue(of({ status: 'applied' })),
      deleteConfig: vi.fn().mockReturnValue(of({ status: 'reset' })),
      // Infra: System status (super-admin)
      getSystemStatus: vi.fn().mockReturnValue(
        of({
          status: 'UP',
          version: '0.1.0-alpha.2',
          uptime: '4h 12m 0s',
          simdCapability: 'SPECIES_512 + APPLE_METAL',
        }),
      ),
      getSystemConfig: vi.fn().mockReturnValue(
        of({
          dataDir: '/var/lib/spector',
        }),
      ),
      // Infra: Scheduler Tasks
      listTasks: vi.fn().mockReturnValue(
        of([
          {
            id: 'consolidation-sweep',
            description: 'Periodic consolidation sweep',
            schedule: '0 */15 * * * *',
            state: 'ACTIVE',
            paused: false,
            lastRunAt: '2026-10-18T08:00:00Z',
          },
        ]),
      ),
      getRecentTaskAuditHistory: vi.fn().mockReturnValue(
        of([
          {
            taskId: 'consolidation-sweep',
            status: 'SUCCESS',
            durationMs: 42,
            startedAt: '2026-10-18T08:00:00Z',
          },
        ]),
      ),
      triggerTask: vi.fn().mockReturnValue(of({ status: 'triggered' })),
      pauseTask: vi.fn().mockReturnValue(of({ status: 'paused' })),
      resumeTask: vi.fn().mockReturnValue(of({ status: 'resumed' })),
      // Infra: DLQ & Cache
      listDlq: vi.fn().mockReturnValue(
        of([
          {
            fileName: 'dlq-101.json',
            source: 'webhook-ingest',
            error: 'Embedding timeout',
            failedAt: '2026-10-18T07:50:00Z',
          },
        ]),
      ),
      redriveDlq: vi.fn().mockReturnValue(of({ status: 'redriven' })),
      deleteDlq: vi.fn().mockReturnValue(of({ status: 'deleted' })),
      listAdminCaches: vi.fn().mockReturnValue(
        of([
          {
            name: 'namespace-grants',
            size: 128,
            hitRate: 0.981,
          },
        ]),
      ),
      clearAdminCache: vi.fn().mockReturnValue(of({ status: 'cleared' })),
      clearAllAdminCaches: vi.fn().mockReturnValue(of({ status: 'cleared_all' })),
      // Infra: Connectors & Benchmarks
      listConnectors: vi.fn().mockReturnValue(
        of([
          {
            id: 'conn-github',
            name: 'GitHub Issues Connector',
            type: 'GITHUB',
            status: 'CONNECTED',
            lastSyncAt: '2026-10-18T07:30:00Z',
          },
        ]),
      ),
      reloadConnector: vi.fn().mockReturnValue(of({ status: 'reloaded' })),
      startConnector: vi.fn().mockReturnValue(of({ status: 'started' })),
      stopConnector: vi.fn().mockReturnValue(of({ status: 'stopped' })),
      listBenchmarkSuites: vi.fn().mockReturnValue(
        of({
          suites: [
            {
              id: 'recall_accuracy',
              name: 'Recall Accuracy',
              description: '6-phase cognitive scoring precision',
              estimatedDurationSec: 5,
            },
          ],
        }),
      ),
      runBenchmark: vi.fn().mockReturnValue(
        of({
          suiteId: 'recall_accuracy',
          durationMs: 310,
          results: { ndcgAt10: 0.94, p95Ms: 2.1 },
        }),
      ),
      // Audit Log
      queryAdminAuditEvents: vi.fn().mockReturnValue(
        of([
          {
            id: 'aud-1001',
            createdAt: '2026-10-18T08:05:00Z',
            actorId: 'admin-alice',
            tenantId: 'acme',
            action: 'CONFIG_UPDATE',
            targetType: 'CONFIG',
            targetId: 'hnsw',
            namespaceId: null,
            outcome: 'SUCCESS',
            entryHash: 'a9f5e2c1840392b7f1d4567890abcdef1234567890abcdef1234567890abcdef',
          },
          {
            id: 'aud-1002',
            createdAt: '2026-10-18T08:10:00Z',
            actorId: 'admin-bob',
            tenantId: 'acme',
            action: 'BREAK_GLASS_REQUEST',
            targetType: 'BREAK_GLASS',
            targetId: 'bg-501',
            namespaceId: 'ns-prod',
            outcome: 'DENIED',
            entryHash: 'b8c4d3e2951403c8e2f5678901bcdef02345678901bcdef02345678901bcdef0',
          },
        ]),
      ),
      verifyAdminAuditChain: vi.fn().mockReturnValue(
        of({
          valid: true,
          checkedEvents: 102,
          headHash: 'b8c4d3e2951403c8e2f5678901bcdef02345678901bcdef02345678901bcdef0',
        }),
      ),
      // Break-Glass
      getAdminTenant: vi.fn().mockReturnValue(
        of({
          tenantId: 'acme',
          name: 'Acme Corp',
          status: 'ACTIVE',
          plan: 'ENTERPRISE',
          settings: {
            byokEnabled: false,
            breakGlassApproverRequired: true,
            breakGlassMaxDurationMinutes: 60,
          },
        }),
      ),
      listBreakGlassRequests: vi.fn().mockReturnValue(
        of([
          {
            id: 'bg-pending-1',
            tenantId: 'acme',
            namespaceId: 'ns-finance',
            requesterAccountId: 'admin-bob',
            approverAccountId: null,
            justification: 'Investigating P1 ingestion stall',
            ticketRef: 'INC-4021',
            durationMinutes: 30,
            status: 'PENDING_APPROVAL',
            createdAt: '2026-10-18T08:00:00Z',
          },
          {
            id: 'bg-active-2',
            tenantId: 'acme',
            namespaceId: 'ns-ops',
            requesterAccountId: 'admin-alice',
            approverAccountId: 'admin-bob',
            justification: 'Emergency recall verification for incident INC-4019',
            ticketRef: 'INC-4019',
            durationMinutes: 45,
            status: 'ACTIVE',
            accessScope: 'READ_ONLY',
            createdAt: '2026-10-18T07:45:00Z',
            approvedAt: '2026-10-18T07:50:00Z',
            expiresAt: '2099-10-18T08:35:00Z',
          },
        ]),
      ),
      requestBreakGlass: vi.fn().mockReturnValue(
        of({
          id: 'bg-new-3',
          tenantId: 'acme',
          namespaceId: 'ns-sec',
          requesterAccountId: 'admin-alice',
          justification: 'Critical incident diagnostics',
          ticketRef: 'INC-5001',
          durationMinutes: 30,
          status: 'PENDING_APPROVAL',
        }),
      ),
      approveBreakGlass: vi.fn().mockReturnValue(
        of({
          id: 'bg-pending-1',
          namespaceId: 'ns-finance',
          requesterAccountId: 'admin-bob',
          approverAccountId: 'admin-alice',
          status: 'ACTIVE',
          grantId: 'gr-bg-1',
        }),
      ),
      rejectBreakGlass: vi.fn().mockReturnValue(of({ id: 'bg-pending-1', status: 'REJECTED' })),
      revokeBreakGlass: vi.fn().mockReturnValue(of({ id: 'bg-active-2', status: 'REVOKED' })),
      inspectBreakGlassNamespaceMemories: vi.fn().mockReturnValue(
        of([
          {
            id: 'mem-777',
            tier: 'SEMANTIC',
            salience: 0.91,
            content: 'Read-only emergency memory payload under break-glass session.',
          },
        ]),
      ),
    };

    TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        AdminInfraComponent,
        AdminAuditComponent,
        AdminBreakGlassComponent,
      ],
      providers: [
        { provide: SynapseApiService, useValue: apiMock },
        {
          provide: AuthService,
          useValue: {
            isSuperAdmin: isSuperAdminSignal,
            isAdmin: isAdminSignal,
            currentUser: currentUserSignal,
          },
        },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
  });

  describe('AdminInfraComponent', () => {
    it('shows the super-admin Cluster/SIMD/GPU tab for super-admin and hides it for tenant admin', () => {
      const fixture = TestBed.createComponent(AdminInfraComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      expect(comp.visibleTabs().some((t) => t.id === 'cluster')).toBe(true);
      expect(apiMock['getSystemStatus']).toHaveBeenCalled();

      // Switch to cluster tab and verify SIMD/GPU telemetry renders
      comp.selectTab('cluster');
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('#panel-cluster')?.textContent).toContain(
        'SPECIES_512 + APPLE_METAL',
      );

      // Non-super-admin (tenant admin) cannot see cluster tab and saves config strictly at 'tenant' scope
      isSuperAdminSignal.set(false);
      const tenantAdminFixture = TestBed.createComponent(AdminInfraComponent);
      const tenantAdminComp = tenantAdminFixture.componentInstance;
      tenantAdminFixture.detectChanges();

      expect(tenantAdminComp.visibleTabs().some((t) => t.id === 'cluster')).toBe(false);
      tenantAdminComp.configScope.set('system');
      tenantAdminComp.requestSaveConfigCategory();
      tenantAdminComp.executeConfirmedAction();
      expect(apiMock['saveConfig']).toHaveBeenCalledWith(
        'llm_provider',
        'tenant',
        expect.any(Object),
      );
    });

    it('requires confirmation before activating a provider or saving system-scope infra config', () => {
      const fixture = TestBed.createComponent(AdminInfraComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      // Provider activation confirmation gate
      comp.requestActivateProvider('generation', 'anthropic');
      fixture.detectChanges();
      expect(apiMock['activateGenerationProvider']).not.toHaveBeenCalled();
      expect(
        fixture.nativeElement.querySelector('#infra-confirm-dialog')?.textContent,
      ).toContain('Activate Generation Provider: anthropic');

      comp.executeConfirmedAction();
      fixture.detectChanges();
      expect(apiMock['activateGenerationProvider']).toHaveBeenCalledWith('anthropic');

      // Config update at system scope for super-admin
      comp.selectConfigCategory('hnsw');
      comp.configScope.set('system');
      comp.configFields.set([
        {
          key: 'efSearch',
          description: 'HNSW search exploration factor',
          defaultValue: 64,
          editValue: 128,
          source: 'system',
          applyMode: 'LIVE',
        },
      ]);
      comp.requestSaveConfigCategory();
      fixture.detectChanges();
      expect(apiMock['saveConfig']).not.toHaveBeenCalled();

      comp.executeConfirmedAction();
      fixture.detectChanges();
      expect(apiMock['saveConfig']).toHaveBeenCalledWith('hnsw', 'system', { efSearch: 128 });
    });
  });

  describe('AdminAuditComponent', () => {
    it('loads and filters audit events, verifies the SHA-256 hash chain, and exports CSV', () => {
      const fixture = TestBed.createComponent(AdminAuditComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      expect(apiMock['queryAdminAuditEvents']).toHaveBeenCalled();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('CONFIG_UPDATE');
      expect(el.textContent).toContain('BREAK_GLASS_REQUEST');

      // Trigger SHA-256 chain verification
      comp.verifyHashChain();
      fixture.detectChanges();
      expect(apiMock['verifyAdminAuditChain']).toHaveBeenCalled();
      const chainBanner = el.querySelector('#audit-chain-banner');
      expect(chainBanner?.textContent).toContain('SHA-256 Hash Chain Verified');
      expect(chainBanner?.textContent).toContain('Checked 102 event(s)');

      // Filter by outcome and action
      comp.filters.outcome = 'DENIED';
      comp.filters.action = 'BREAK_GLASS_REQUEST';
      comp.loadAuditEvents();
      expect(apiMock['queryAdminAuditEvents']).toHaveBeenLastCalledWith(
        expect.objectContaining({
          outcome: 'DENIED',
          action: 'BREAK_GLASS_REQUEST',
        }),
      );

      // Export CSV
      comp.exportAuditCsv();
      expect(comp.lastExportedCsv()).toContain(
        'audit_id,timestamp,tenant_id,actor_id,action,target_type,target_id,namespace_id,outcome,entry_hash',
      );
      expect(comp.lastExportedCsv()).toContain('aud-1001');
    });
  });

  describe('AdminBreakGlassComponent', () => {
    it('disables break-glass requests with SPE-820-004 explanation banner when tenant has BYOK enabled', () => {
      apiMock['getAdminTenant'].mockReturnValue(
        of({
          tenantId: 'acme',
          name: 'Acme Corp',
          status: 'ACTIVE',
          plan: 'ENTERPRISE',
          settings: {
            byokEnabled: true,
            breakGlassApproverRequired: true,
            breakGlassMaxDurationMinutes: 60,
          },
        }),
      );

      const fixture = TestBed.createComponent(AdminBreakGlassComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      expect(comp.isByokTenant()).toBe(true);
      const el: HTMLElement = fixture.nativeElement;
      const byokBanner = el.querySelector('#break-glass-byok-prohibited-banner');
      expect(byokBanner).toBeTruthy();
      expect(byokBanner?.textContent).toContain('SPE-820-004');
      expect(
        (el.querySelector('#bg-submit-request-btn') as HTMLButtonElement | null)?.disabled,
      ).toBe(true);

      // Attempting to submit via method is also blocked when BYOK is active
      comp.requestDraft.namespaceId = 'ns-secret';
      comp.requestDraft.justification = 'Attempt on BYOK tenant';
      comp.requestCreateBreakGlass();
      expect(comp.pendingConfirmation()).toBeNull();
      expect(apiMock['requestBreakGlass']).not.toHaveBeenCalled();
    });

    it('enforces dual-control approval, supports session revocation, and renders the read-only watermarked viewer', () => {
      const fixture = TestBed.createComponent(AdminBreakGlassComponent);
      const comp = fixture.componentInstance;
      fixture.detectChanges();

      expect(comp.isByokTenant()).toBe(false);
      expect(comp.pendingRequests().length).toBe(1);
      expect(comp.activeSessions().length).toBe(1);

      const pendingReq = comp.pendingRequests()[0];
      // Caller is 'admin-alice', requester is 'admin-bob' -> not self-requested, caller CAN approve
      expect(comp.isSelfRequested(pendingReq)).toBe(false);

      // If caller is the same as requester ('admin-bob'), dual-control flags self-requested
      currentUserSignal.set({
        userId: 'admin-bob',
        username: 'bob',
        displayName: 'Bob Admin',
        profile: 'TEAM',
        roles: ['admin'],
        scopes: ['admin'],
        tenantId: 'acme',
      });
      expect(comp.isSelfRequested(pendingReq)).toBe(true);

      // Switch back to 'admin-alice' and approve pending request after confirmation
      currentUserSignal.set({
        userId: 'admin-alice',
        username: 'alice',
        displayName: 'Alice Admin',
        profile: 'TEAM',
        roles: ['admin', 'super-admin'],
        scopes: ['admin'],
        tenantId: 'acme',
      });
      comp.requestApprove(pendingReq);
      fixture.detectChanges();
      expect(apiMock['approveBreakGlass']).not.toHaveBeenCalled();

      comp.executeConfirmedAction();
      fixture.detectChanges();
      expect(apiMock['approveBreakGlass']).toHaveBeenCalledWith(
        'bg-pending-1',
        'Approved via dual-control console',
      );

      // Open read-only viewer for active session and verify watermark banner
      const activeSession = comp.activeSessions()[0];
      comp.openWatermarkedViewer(activeSession);
      fixture.detectChanges();

      expect(apiMock['inspectBreakGlassNamespaceMemories']).toHaveBeenCalledWith('ns-ops', 25);
      const el: HTMLElement = fixture.nativeElement;
      const viewer = el.querySelector('#break-glass-viewer');
      expect(viewer?.textContent).toContain(
        'BREAK-GLASS SESSION — READ-ONLY EMERGENCY ACCESS',
      );
      expect(viewer?.textContent).toContain(
        'Read-only emergency memory payload under break-glass session.',
      );
    });
  });
});
