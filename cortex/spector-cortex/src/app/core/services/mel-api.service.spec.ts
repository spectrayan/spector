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
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MelApiService } from './mel-api.service';
import { environment } from '../../../environments/environment';

describe('MelApiService (Milestone 4)', () => {
  let service: MelApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MelApiService],
    });
    service = TestBed.inject(MelApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('posts execute request to /api/v1/mel/execute', () => {
    const mockRes = {
      status: 'OK',
      combined_output: 'Execution complete',
      elapsed_ms: 12,
      total_elapsed_ms: 12,
      duration_micros: 12000,
      namespace: 'default',
      statement_type: 'RECALL',
      affected_ids: [],
      candidates: [],
      explain_rows: [],
      funnel: {
        totalScanned: 100,
        phase1Alive: 80,
        phase2TagSurvivors: 40,
        phase3ValenceSurvivors: 30,
        phase4MassSurvivors: 25,
        phase5SimdEvaluated: 15,
        phase6FinalReturned: 5,
      },
      funnel_phases: [],
      diagnostics: [],
      statements: [],
    };

    service.execute("RECALL 'test' TOP 5;", 'default').subscribe((res) => {
      expect(res.status).toBe('OK');
      expect(res.funnel.totalScanned).toBe(100);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/mel/execute`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      script: "RECALL 'test' TOP 5;",
      namespace: 'default',
    });
    req.flush(mockRes);
  });

  it('posts validate request to /api/v1/mel/validate', () => {
    const mockRes = {
      valid: true,
      statement_count: 1,
      statements: [{ type: 'RECALL', summary: 'RECALL test', line: 1, column: 1 }],
      diagnostics: [],
    };

    service.validate("RECALL 'test';").subscribe((res) => {
      expect(res.valid).toBe(true);
      expect(res.statement_count).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/mel/validate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ script: "RECALL 'test';" });
    req.flush(mockRes);
  });

  it('fetches MEL schema from /api/v1/mel/schema', () => {
    const mockSchema = {
      keywords: ['RECALL', 'REMEMBER'],
      tiers: ['WORKING', 'EPISODIC'],
      sources: ['EXPERIENCED'],
      clauseTemplates: [],
      snippets: [],
    };

    service.getSchema().subscribe((res) => {
      expect(res.keywords).toContain('RECALL');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/mel/schema`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSchema);
  });

  it('reinforces a candidate memory engram via /api/v1/memory/{id}/reinforce', () => {
    service.reinforce('mem-123', 0.1).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/memory/mem-123/reinforce`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ delta: 0.1 });
    req.flush({ success: true });
  });
});
