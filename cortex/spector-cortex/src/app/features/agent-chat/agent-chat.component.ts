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

import {
  Component,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
  ElementRef,
  viewChild,
  afterNextRender,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';

import { SynapseApiService } from '../../core/services/synapse-api.service';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
import {
  ConversationDrawerComponent,
  SessionSummary,
} from './components/conversation-drawer/conversation-drawer.component';
import { ThinkingAccordionComponent } from './components/thinking-accordion/thinking-accordion.component';
import { ToolCardComponent } from './components/tool-card/tool-card.component';
import {
  ChatTurnView,
  ChatStreamEvent,
  AgentChatRequest,
} from './models/chat-turn.model';
import {
  reduceChatEvents,
  createInitialTurn,
  hydrateTurnFromHistory,
} from './reducers/chat-turn.reducer';
import { ChatService } from './services/chat.service';

@Component({
  selector: 'cortex-agent-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatChipsModule,
    MatExpansionModule,
    MatSliderModule,
    MatTooltipModule,
    MatSidenavModule,
    MatCheckboxModule,
    MatSelectModule,
    MatMenuModule,
    MatDividerModule,
    CdkTextareaAutosize,
    MarkdownPipe,
    ConversationDrawerComponent,
    ThinkingAccordionComponent,
    ToolCardComponent,
  ],
  templateUrl: './agent-chat.component.html',
  styleUrl: './agent-chat.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgentChatComponent {
  private readonly api = inject(SynapseApiService);
  private readonly chatService = inject(ChatService);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // ── Conversation & Operational Session State ──
  readonly sessions = signal<SessionSummary[]>([]);
  readonly activeSessionId = signal<string | null>(null);
  readonly turns = signal<ChatTurnView[]>([]);
  readonly isDrawerOpen = signal<boolean>(typeof window !== 'undefined' ? window.innerWidth > 768 : true);
  readonly isStreaming = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly hasMoreSessions = signal<boolean>(false);
  readonly loadingHistory = signal<boolean>(false);

  // ── Chat Input State ──
  readonly query = signal('');
  readonly attachedFile = signal<{ name: string; content: string } | null>(null);

  // ── Autoscroll Lock Signals ──
  readonly userScrolledUp = signal<boolean>(false);
  private readonly scrollContainer = viewChild<ElementRef<HTMLDivElement>>('scrollContainer');
  private readonly messagesEnd = viewChild<ElementRef<HTMLDivElement>>('messagesEnd');

  // ── Model & Tier State ──
  readonly models = signal<any[]>([]);
  readonly selectedModel = signal<string | null>(null);
  readonly modelsLoading = signal(true);
  readonly selectedTier = signal('EPISODIC');
  readonly tiers = ['EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'WORKING'];

  // ── Config & Cognitive Pipeline Toggles ──
  readonly showConfig = signal(false);
  readonly showSoulPanel = signal(false);
  readonly showSources = signal(true);
  readonly showScoringTrace = signal(true);
  readonly enableGraphExpansion = signal(true);
  readonly enableTextSearch = signal(true);
  readonly contextDepth = signal(10);
  readonly maxMemories = signal(5);

  // ── Scoring Bias ──
  readonly relevanceWeight = signal(70);
  readonly recencyWeight = signal(20);
  readonly importanceWeight = signal(10);

  // ── Derived Signals ──
  readonly hasTurns = computed(() => this.turns().length > 0);

  readonly selectedModelName = computed(() => {
    const id = this.selectedModel();
    if (!id) return 'Select Model';
    const m = this.models().find((model) => model.id === id);
    return m?.name ?? id;
  });

  readonly normalizedWeights = computed(() => {
    const r = this.relevanceWeight();
    const rec = this.recencyWeight();
    const imp = this.importanceWeight();
    const total = r + rec + imp || 1;
    return {
      relevance: Math.round((r / total) * 100),
      recency: Math.round((rec / total) * 100),
      importance: Math.round((imp / total) * 100),
    };
  });

  private static readonly SESSION_KEY = 'spector_active_session';
  private chatSubscription: Subscription | null = null;

  constructor() {
    this.loadModels();
    this.loadSessions();

    // Check query params for session deep link (e.g. /chat?session=01J8Y...)
    this.route.queryParams.subscribe((params) => {
      const sessionId = params['session'];
      if (sessionId) {
        this.selectSession(sessionId);
      } else {
        this.restoreFromStorage();
      }
    });

    afterNextRender(() => {
      this.scrollToBottom();
    });
  }

  // ── Storage State Persistence ──

  private restoreFromStorage(): void {
    try {
      const savedSession = sessionStorage.getItem(AgentChatComponent.SESSION_KEY);
      if (savedSession && !this.activeSessionId()) {
        this.selectSession(savedSession);
      }
    } catch {
      // Storage unavailable
    }
  }

  private saveToStorage(): void {
    try {
      const sessionId = this.activeSessionId();
      if (sessionId) {
        sessionStorage.setItem(AgentChatComponent.SESSION_KEY, sessionId);
      } else {
        sessionStorage.removeItem(AgentChatComponent.SESSION_KEY);
      }
    } catch {
      // Storage full
    }
  }

  // ── Session Management (REST Endpoints) ──

  loadSessions(): void {
    this.chatService.getSessions(20).subscribe({
      next: (data) => {
        this.sessions.set(data.sessions ?? []);
        this.hasMoreSessions.set(data.hasMore ?? false);
      },
      error: () => {},
    });
  }

  selectSession(sessionId: string): void {
    if (this.isStreaming()) {
      this.cancelStream();
    }

    this.activeSessionId.set(sessionId);
    this.saveToStorage();
    this.loadingHistory.set(true);

    this.chatService.getSessionMessages(sessionId).subscribe({
      next: (data) => {
        if (data.turns && Array.isArray(data.turns)) {
          // Dual-plane structured turns response
          const hydratedTurns = data.turns.map((t) => hydrateTurnFromHistory(t, sessionId));
          this.turns.set(hydratedTurns);
        } else if (data.messages && Array.isArray(data.messages)) {
          // Fallback legacy messages array converted to turns
          const convertedTurns: ChatTurnView[] = [];
          for (let i = 0; i < data.messages.length; i += 2) {
            const userMsg = data.messages[i];
            const assistantMsg = data.messages[i + 1];
            const turn = createInitialTurn(
              `turn_${i}`,
              sessionId,
              Math.floor(i / 2) + 1,
              userMsg?.content || '',
            );
            const finalTurn: ChatTurnView = {
              ...turn,
              status: 'DONE',
              isStreaming: false,
              assistant: { text: assistantMsg?.content || '' },
            };
            convertedTurns.push(finalTurn);
          }
          this.turns.set(convertedTurns);
        } else {
          this.turns.set([]);
        }

        this.loadingHistory.set(false);
        this.scrollToBottom();
      },
      error: () => {
        this.loadingHistory.set(false);
      },
    });
  }

  startNewChat(): void {
    if (this.isStreaming()) {
      this.cancelStream();
    }
    this.activeSessionId.set(null);
    this.turns.set([]);
    this.saveToStorage();
    this.router.navigate([], { queryParams: {} });
  }

  renameSession(event: { sessionId: string; newTitle: string }): void {
    this.sessions.update((list) =>
      list.map((s) => (s.sessionId === event.sessionId ? { ...s, title: event.newTitle } : s)),
    );
    this.chatService.updateSessionTitle(event.sessionId, event.newTitle).subscribe({
      error: () => {},
    });
  }

  deleteSession(sessionId: string): void {
    this.sessions.update((list) => list.filter((s) => s.sessionId !== sessionId));
    if (this.activeSessionId() === sessionId) {
      this.startNewChat();
    }
    this.chatService.deleteSession(sessionId).subscribe({
      error: () => {},
    });
  }

  toggleDrawer(open: boolean): void {
    this.isDrawerOpen.set(open);
  }

  // ── Streaming Dispatch via ChatService, Fetch & Pure Reducer ──

  sendQuery(): void {
    const q = this.query().trim();
    if (!q || this.isStreaming() || this.isLoading()) return;
    this.doSendQuery(q);
  }

  private doSendQuery(prompt: string): void {
    const currentSessionId = this.activeSessionId() || '';
    const turnSeq = this.turns().length + 1;
    const tempTurnId = `turn_${Date.now()}`;

    // Initialize new turn in UI via shared reducer pattern
    const initialTurn = createInitialTurn(tempTurnId, currentSessionId, turnSeq, prompt);
    this.turns.update((t) => [...t, initialTurn]);
    this.query.set('');
    this.isStreaming.set(true);
    this.isLoading.set(true);
    this.userScrolledUp.set(false);
    this.scrollToBottom();

    const requestBody: AgentChatRequest = {
      message: prompt,
      sessionId: currentSessionId || undefined,
      model: this.selectedModel() || undefined,
      contextDepth: this.contextDepth(),
    };

    if (this.chatSubscription) {
      this.chatSubscription.unsubscribe();
    }

    this.chatSubscription = this.chatService.streamMessage(requestBody).subscribe({
      next: (streamEvent: ChatStreamEvent) => {
        this.isLoading.set(false);

        // If error event arrives in stream, ensure streaming state is marked false immediately
        if (streamEvent.type === 'error') {
          this.isStreaming.set(false);
        }

        // If session event, update active session ID
        if (streamEvent.type === 'session' && streamEvent.sessionId) {
          this.activeSessionId.set(streamEvent.sessionId);
          this.saveToStorage();
        }

        // Pure reduction of the active turn
        this.turns.update((turnsList) => {
          if (turnsList.length === 0) return turnsList;
          const lastIndex = turnsList.length - 1;
          const updatedTurn = reduceChatEvents(turnsList[lastIndex], streamEvent);
          const copy = [...turnsList];
          copy[lastIndex] = updatedTurn;
          return copy;
        });

        // Maintain autoscroll lock
        if (!this.userScrolledUp()) {
          this.scrollToBottom();
        }
      },
      error: (err: unknown) => {
        this.handleStreamError(err);
        this.isStreaming.set(false);
        this.isLoading.set(false);
        this.chatSubscription = null;
        this.saveToStorage();
      },
      complete: () => {
        this.isStreaming.set(false);
        this.isLoading.set(false);
        this.chatSubscription = null;
        this.attachedFile.set(null);
        this.saveToStorage();
      },
    });
  }

  private handleStreamError(err: any): void {
    if (err?.name === 'AbortError') return;

    this.turns.update((turnsList) => {
      if (turnsList.length === 0) return turnsList;
      const lastIndex = turnsList.length - 1;
      const current = turnsList[lastIndex];
      const copy = [...turnsList];
      copy[lastIndex] = {
        ...current,
        status: 'ERROR',
        isStreaming: false,
        hasError: true,
        errorMessage: err?.message || 'Error: Stream encountered an unexpected error.',
        error: {
          message: err?.message || 'Error: Stream encountered an unexpected error.',
        },
      };
      return copy;
    });
  }

  cancelStream(): void {
    this.chatService.abortStream('User stopped generation');
    if (this.chatSubscription) {
      this.chatSubscription.unsubscribe();
      this.chatSubscription = null;
    }
    this.isStreaming.set(false);
    this.isLoading.set(false);
  }

  // ── Accordion User Toggle ──

  onThinkingToggle(turnId: string, isCollapsed: boolean): void {
    this.turns.update((turnsList) =>
      turnsList.map((t) => {
        if (t.turnId === turnId) {
          return {
            ...t,
            thinking: {
              ...t.thinking,
              isCollapsed,
              userToggled: true,
            },
          };
        }
        return t;
      }),
    );
  }

  // ── Autoscroll Lock Mechanics ──

  onScroll(): void {
    const el = this.scrollContainer()?.nativeElement;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    // If scrolled up more than 48px from bottom, disengage lock
    this.userScrolledUp.set(distanceFromBottom > 48);
  }

  scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesEnd()?.nativeElement;
      el?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }, 50);
  }

  onTextareaKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendQuery();
    }
  }

  // ── Suggestion Chips (Exact Fixture Text) ──

  useSuggestion(text: string): void {
    this.query.set(text);
  }

  // ── Data & Models ──

  loadModels(): void {
    this.modelsLoading.set(true);
    this.chatService.getModels().subscribe({
      next: (data) => {
        this.models.set(data.models ?? []);
        const active = (data.models ?? []).find((m: any) => m.active);
        if (active) this.selectedModel.set(active.id);
        this.modelsLoading.set(false);
      },
      error: () => this.modelsLoading.set(false),
    });
  }

  // ── Attachments ──

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      this.attachedFile.set({
        name: file.name,
        content: reader.result as string,
      });
      this.snack.open(`Attached: ${file.name}`, 'OK', { duration: 2000 });
    };
    reader.readAsText(file);
    input.value = '';
  }

  removeAttachment(): void {
    this.attachedFile.set(null);
  }

  clearChat(): void {
    this.startNewChat();
  }
}
