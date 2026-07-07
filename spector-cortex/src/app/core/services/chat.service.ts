import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface ChatMessage {
  id?: number;
  sessionId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  model?: string;
  timestamp: string;
}

export interface ChatSession {
  sessionId: string;
  preview: string;
  model: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChatResponse {
  sessionId: string;
  messageId: number;
  role: string;
  content: string;
  model: string;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly baseUrl = `${environment.apiUrl}/api/${environment.apiVersion}/chat`;

  readonly sessions = signal<ChatSession[]>([]);
  readonly activeSessionId = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly loading = signal(false);

  readonly activeSession = computed(() => {
    const id = this.activeSessionId();
    return this.sessions().find(s => s.sessionId === id) ?? null;
  });

  constructor(private http: HttpClient) {}

  loadSessions() {
    this.http.get<ChatSession[]>(`${this.baseUrl}/sessions`).subscribe({
      next: sessions => this.sessions.set(sessions),
      error: err => console.error('[ChatService] Failed to load sessions', err)
    });
  }

  createSession() {
    this.http.post<{ sessionId: string }>(`${this.baseUrl}/sessions`, {}).subscribe({
      next: res => {
        this.activeSessionId.set(res.sessionId);
        this.messages.set([]);
        this.loadSessions();
      }
    });
  }

  selectSession(sessionId: string) {
    this.activeSessionId.set(sessionId);
    this.http.get<{ messages: ChatMessage[] }>(`${this.baseUrl}/sessions/${sessionId}`).subscribe({
      next: detail => this.messages.set(detail.messages ?? [])
    });
  }

  sendMessage(content: string) {
    const sessionId = this.activeSessionId();
    this.loading.set(true);

    // Add optimistic user message
    const userMsg: ChatMessage = {
      sessionId: sessionId ?? '',
      role: 'user',
      content,
      timestamp: new Date().toISOString()
    };
    this.messages.update(msgs => [...msgs, userMsg]);

    this.http.post<ChatResponse>(this.baseUrl, {
      sessionId,
      message: content
    }).subscribe({
      next: res => {
        this.activeSessionId.set(res.sessionId);
        const assistantMsg: ChatMessage = {
          id: res.messageId,
          sessionId: res.sessionId,
          role: 'assistant',
          content: res.content,
          model: res.model,
          timestamp: res.timestamp
        };
        this.messages.update(msgs => [...msgs, assistantMsg]);
        this.loading.set(false);
        this.loadSessions();
      },
      error: () => this.loading.set(false)
    });
  }

  deleteSession(sessionId: string) {
    this.http.delete(`${this.baseUrl}/sessions/${sessionId}`).subscribe({
      next: () => {
        if (this.activeSessionId() === sessionId) {
          this.activeSessionId.set(null);
          this.messages.set([]);
        }
        this.loadSessions();
      }
    });
  }
}
