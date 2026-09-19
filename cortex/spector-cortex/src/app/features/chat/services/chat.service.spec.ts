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
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { of } from 'rxjs';
import { ChatService } from './chat.service';
import { ChatStreamClient } from './chat-stream.client';
import { environment } from '../../../../environments/environment';

describe('ChatService (Session REST Client)', () => {
  let service: ChatService;
  let mockHttp: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let mockStreamClient: {
    stream: ReturnType<typeof vi.fn>;
    abort: ReturnType<typeof vi.fn>;
    isActive: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockHttp = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    };
    mockStreamClient = {
      stream: vi.fn(),
      abort: vi.fn(),
      isActive: vi.fn().mockReturnValue(false),
    };

    service = new ChatService(mockHttp as any, mockStreamClient as any);
  });

  it('getSessions sends GET request with bounded limit query parameter', () => {
    mockHttp.get.mockReturnValue(
      of({
        sessions: [
          {
            sessionId: '01J8Y000001',
            preview: 'Austin move',
            lastActivity: '2026-09-19',
            messageCount: 4,
          },
        ],
        hasMore: false,
      }),
    );

    service.getSessions(15).subscribe((res) => {
      expect(res.sessions.length).toBe(1);
      expect(res.sessions[0].sessionId).toBe('01J8Y000001');
    });

    expect(mockHttp.get).toHaveBeenCalledWith(
      `${environment.apiUrl}/chat/sessions`,
      expect.objectContaining({ params: expect.anything() }),
    );
  });

  it('createSession sends POST request with default title', () => {
    mockHttp.post.mockReturnValue(
      of({ id: '01J8Y000002', title: 'New Chat', status: 'ACTIVE' }),
    );

    service.createSession().subscribe((res) => {
      expect(res.id).toBe('01J8Y000002');
      expect(res.title).toBe('New Chat');
    });

    expect(mockHttp.post).toHaveBeenCalledWith(`${environment.apiUrl}/chat/sessions`, {
      title: 'New Chat',
    });
  });

  it('updateSessionTitle sends PATCH request with trimmed title', () => {
    mockHttp.patch.mockReturnValue(
      of({
        sessionId: '01J8Y000001',
        preview: 'Relocation Plans',
        lastActivity: '2026-09-19',
        messageCount: 2,
      }),
    );

    service.updateSessionTitle('01J8Y000001', '  Relocation Plans  ').subscribe((res) => {
      expect(res.preview).toBe('Relocation Plans');
    });

    expect(mockHttp.patch).toHaveBeenCalledWith(
      `${environment.apiUrl}/chat/sessions/01J8Y000001`,
      { title: 'Relocation Plans' },
    );
  });

  it('deleteSession sends DELETE request to operational plane endpoint', () => {
    mockHttp.delete.mockReturnValue(of(null));

    service.deleteSession('01J8Y000001').subscribe();

    expect(mockHttp.delete).toHaveBeenCalledWith(
      `${environment.apiUrl}/chat/sessions/01J8Y000001`,
    );
  });

  it('getSessionMessages retrieves full structured turn history', () => {
    mockHttp.get.mockReturnValue(
      of({
        sessionId: '01J8Y000001',
        title: 'Austin Relocation',
        turns: [
          {
            turnId: 't-1',
            seq: 1,
            status: 'DONE',
            user: { text: 'Move date?' },
            tools: [
              {
                callId: 'c1',
                name: 'memory_recall',
                status: 'success',
                preview: 'Oct 1',
                arguments: {},
              },
            ],
            assistant: { text: 'October 1st' },
            primedMemories: 2,
          },
        ],
      }),
    );

    service.getSessionMessages('01J8Y000001').subscribe((res) => {
      expect(res.sessionId).toBe('01J8Y000001');
      expect(res.turns.length).toBe(1);
      expect(res.turns[0].tools.length).toBe(1);
      expect(res.turns[0].tools[0].name).toBe('memory_recall');
    });

    expect(mockHttp.get).toHaveBeenCalledWith(
      `${environment.apiUrl}/chat/sessions/01J8Y000001/messages`,
    );
  });

  it('delegates streamMessage and abortStream to ChatStreamClient', () => {
    const chatReq = { message: 'Stream this' };
    service.streamMessage(chatReq);
    expect(mockStreamClient.stream).toHaveBeenCalledWith(
      `${environment.apiUrl}/chat/stream`,
      chatReq,
      undefined,
    );

    service.abortStream('Test abort');
    expect(mockStreamClient.abort).toHaveBeenCalledWith('Test abort');
  });
});
