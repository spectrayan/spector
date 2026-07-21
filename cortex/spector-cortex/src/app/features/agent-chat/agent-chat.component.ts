import {
  Component,
  inject,
  signal,
  computed,
  effect,
  ChangeDetectionStrategy,
  ElementRef,
  viewChild,
  afterNextRender,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

interface ChatMessage {
  id?: string;
  role: 'user' | 'assistant' | 'system' | 'divider';
  content: string;
  sources?: any[];
  latency?: any;
  model?: string;
  memoryTier?: string;
  timestamp: Date;
  scoringBias?: { relevance: number; recency: number; importance: number };
  fileRef?: string;
  trace?: { type: string; data: string }[];  // agent tool call trace
  primedMemories?: number;  // cross-session memories used for context
  _traceOpen?: boolean;    // UI state: trace section expanded
  _sourcesOpen?: boolean;  // UI state: sources section expanded
  sessionId?: string;      // which session this message belongs to
}

interface SessionSummary {
  sessionId: string;
  messageCount: number;
  preview: string;
  startedAt: number;
}

// No Conversation interface — Spector is a single brain, not a conversation list.
// Sessions are internal plumbing managed by the backend.

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
  ],
  templateUrl: './agent-chat.component.html',
  styleUrl: './agent-chat.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgentChatComponent {
  private readonly api = inject(SynapseApiService);
  private readonly snack = inject(MatSnackBar);

  // ── Chat State ──
  readonly query = signal('');
  readonly isLoading = signal(false);
  readonly messages = signal<ChatMessage[]>([]);

  // ── Session (internal — user never sees this) ──
  readonly activeSessionId = signal<string | null>(null);

  // ── Past Sessions (for "Load More") ──
  readonly pastSessions = signal<SessionSummary[]>([]);
  readonly loadingHistory = signal(false);
  readonly hasMoreSessions = signal(false);
  readonly models = signal<any[]>([]);
  readonly selectedModel = signal<string | null>(null);
  readonly modelsLoading = signal(true);

  // ── Memory Tier ──
  readonly selectedTier = signal('EPISODIC');
  readonly tiers = ['EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'WORKING'];

  // ── Config ──
  readonly maxMemories = signal(5);
  readonly contextDepth = signal(10);
  readonly generateResponse = signal(true);
  // System prompt is now managed server-side (companion-system.txt)
  readonly showConfig = signal(false);
  readonly showSoulPanel = signal(false);

  // Agent mode is always on — RAG mode removed per CEO decision
  // readonly agentMode signal removed — agent is the only mode
  readonly showSources = signal(true); // toggle recalled memories visibility

  // ── Cognitive Pipeline Toggles (configurable from settings) ──
  readonly enableGraphExpansion = signal(true);
  readonly enableTextSearch = signal(true);
  readonly showScoringTrace = signal(true);

  // ── Scoring Bias ──
  readonly relevanceWeight = signal(70);
  readonly recencyWeight = signal(20);
  readonly importanceWeight = signal(10);

  // ── File Attachment ──
  readonly attachedFile = signal<{ name: string; content: string } | null>(null);

  // ── Config Info ──
  readonly config = signal<any>(null);

  // ── Agent Soul State ──
  readonly soulLoading = signal(false);
  readonly soulSaving = signal(false);
  readonly soulName = signal('');
  readonly soulPurpose = signal('');
  readonly soulPersonality = signal('');
  readonly soulCommunicationStyle = signal('');
  readonly soulExpertise = signal<string[]>([]);
  readonly soulValues = signal<string[]>([]);
  readonly soulEmotionalBaseline = signal('NEUTRAL');
  readonly soulGuardrails = signal<string[]>([]);
  readonly soulTools = signal<string[]>([]);
  readonly availableTools = signal<any[]>([]);
  private soulLoaded = false;

  // ── Derived ──
  readonly hasMessages = computed(() => this.messages().length > 0);

  readonly selectedModelName = computed(() => {
    const id = this.selectedModel();
    if (!id) return 'Select Model';
    const m = this.models().find(m => m.id === id);
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

  readonly weightSegments = computed(() => {
    const w = this.normalizedWeights();
    return [
      { label: 'Relevance', value: w.relevance, color: '#7c4dff' },
      { label: 'Recency', value: w.recency, color: '#00bcd4' },
      { label: 'Importance', value: w.importance, color: '#ff9800' },
    ];
  });

  private readonly messagesEnd = viewChild<ElementRef>('messagesEnd');

  private static readonly SESSION_KEY = 'spector_active_session';
  private static readonly MESSAGES_KEY = 'spector_chat_messages';

  constructor() {
    // Restore session state from browser storage (survives page refresh + navigation)
    this.restoreFromStorage();

    this.loadModels();
    this.loadConfig();
    this.loadPastSessions();

    // If we restored a sessionId but have no messages, load current session from backend
    if (this.activeSessionId() && this.messages().length === 0) {
      this.loadCurrentSessionMessages();
    }

    // Auto-load soul when panel is opened for the first time
    effect(() => {
      if (this.showSoulPanel() && !this.soulLoaded) {
        this.loadSoul();
      }
    });

    afterNextRender(() => this.scrollToBottom());
  }

  /** Restore session ID and messages from sessionStorage. */
  private restoreFromStorage(): void {
    try {
      const savedSession = sessionStorage.getItem(AgentChatComponent.SESSION_KEY);
      if (savedSession) {
        this.activeSessionId.set(savedSession);
      }
      const savedMessages = sessionStorage.getItem(AgentChatComponent.MESSAGES_KEY);
      if (savedMessages) {
        const parsed = JSON.parse(savedMessages) as ChatMessage[];
        // Restore Date objects (JSON.parse returns strings)
        for (const m of parsed) {
          m.timestamp = new Date(m.timestamp);
        }
        this.messages.set(parsed);
      }
    } catch {
      // Storage unavailable or corrupted — start fresh
    }
  }

  /** Persist current state to sessionStorage. */
  private saveToStorage(): void {
    try {
      const sessionId = this.activeSessionId();
      if (sessionId) {
        sessionStorage.setItem(AgentChatComponent.SESSION_KEY, sessionId);
      }
      // Only save last 50 messages to avoid storage limits
      const msgs = this.messages().slice(-50);
      sessionStorage.setItem(AgentChatComponent.MESSAGES_KEY, JSON.stringify(msgs));
    } catch {
      // Storage full or unavailable — ignore
    }
  }

  /** Load current session messages from the backend (used after page refresh). */
  private loadCurrentSessionMessages(): void {
    const sessionId = this.activeSessionId();
    if (!sessionId) return;

    this.api.chatSessionMessages(sessionId).subscribe({
      next: (data) => {
        const msgs: ChatMessage[] = (data.messages ?? []).map((m: any) => ({
          role: m.role as 'user' | 'assistant',
          content: m.content,
          timestamp: new Date(),
          sessionId,
        }));
        if (msgs.length > 0) {
          this.messages.set(msgs);
          this.scrollToBottom();
        }
      },
      error: () => { },
    });
  }

  // ── Data Loading ──

  loadModels(): void {
    this.modelsLoading.set(true);
    this.api.chatModels().subscribe({
      next: (data) => {
        this.models.set(data.models ?? []);
        const active = (data.models ?? []).find((m: any) => m.active);
        if (active) this.selectedModel.set(active.id);
        this.modelsLoading.set(false);
      },
      error: () => this.modelsLoading.set(false),
    });
  }

  loadConfig(): void {
    this.api.chatConfig().subscribe({
      next: (data) => this.config.set(data),
      error: () => { },
    });
  }

  /** Fetches the list of past sessions so the UI can show "Load previous messages". */
  loadPastSessions(): void {
    this.api.chatSessions(10).subscribe({
      next: (data) => {
        // Exclude the current active session from the list
        const activeId = this.activeSessionId();
        const sessions = (data.sessions ?? []).filter(
          (s: SessionSummary) => s.sessionId !== activeId
        );
        this.pastSessions.set(sessions);
        this.hasMoreSessions.set(data.hasMore ?? false);
      },
      error: () => { },
    });
  }

  /** Loads messages from the most recent past session and prepends them with a divider. */
  loadPastSession(): void {
    const sessions = this.pastSessions();
    if (sessions.length === 0 || this.loadingHistory()) return;

    const nextSession = sessions[0]; // newest unloaded session
    this.loadingHistory.set(true);

    this.api.chatSessionMessages(nextSession.sessionId).subscribe({
      next: (data) => {
        const pastMessages: ChatMessage[] = (data.messages ?? []).map(
          (m: any) => ({
            role: m.role as 'user' | 'assistant',
            content: m.content,
            timestamp: new Date(nextSession.startedAt),
            sessionId: nextSession.sessionId,
          })
        );

        // Add a divider after the past messages
        const divider: ChatMessage = {
          role: 'divider',
          content: 'Previous session',
          timestamp: new Date(nextSession.startedAt),
          sessionId: nextSession.sessionId,
        };

        // Prepend past messages + divider before current messages
        this.messages.update(msgs => [...pastMessages, divider, ...msgs]);

        // Remove the loaded session from the pending list
        this.pastSessions.update(s => s.slice(1));
        this.loadingHistory.set(false);
      },
      error: () => this.loadingHistory.set(false),
    });
  }

  // ── Send Query ──

  sendQuery(): void {
    const q = this.query().trim();
    if (!q || this.isLoading()) return;
    this.doSendQuery(q);
  }

  private doSendQuery(q: string): void {
    const bias = this.normalizedWeights();

    const userMsg: ChatMessage = {
      role: 'user',
      content: q,
      timestamp: new Date(),
      memoryTier: this.selectedTier(),
      scoringBias: bias,
      fileRef: this.attachedFile()?.name,
    };
    this.messages.update(msgs => [...msgs, userMsg]);
    this.query.set('');
    this.isLoading.set(true);
    this.scrollToBottom();

    // Agent mode — tool calling + cognitive memory with automatic context priming
    this.api.agentChat(q, {
      sessionId: this.activeSessionId() ?? undefined,
      model: this.selectedModel() ?? undefined,
      contextDepth: this.contextDepth(),
      enableGraph: this.enableGraphExpansion(),
      enableTextSearch: this.enableTextSearch(),
      enableTrace: this.showScoringTrace(),
    }).subscribe({
      next: (data) => {
        // Track the session ID returned by the backend (auto-managed)
        if (data.sessionId) {
          const previousSessionId = this.activeSessionId();
          // If the backend rotated to a new session, insert a divider
          if (data.isNewSession && previousSessionId && previousSessionId !== data.sessionId) {
            const divider: ChatMessage = {
              role: 'divider',
              content: 'Session ended',
              timestamp: new Date(),
              sessionId: previousSessionId,
            };
            this.messages.update(msgs => [...msgs, divider]);
          }
          this.activeSessionId.set(data.sessionId);
        }
        const assistantMsg: ChatMessage = {
          role: 'assistant',
          content: data.response ?? 'No response generated',
          latency: data.latency,
          model: data.model,
          timestamp: new Date(),
          trace: data.trace ?? [],
          primedMemories: data.primedMemories ?? 0,
        };
        this.messages.update(msgs => [...msgs, assistantMsg]);
        this.isLoading.set(false);
        this.attachedFile.set(null);
        this.saveToStorage();
        this.scrollToBottom();
      },
      error: (err) => {
        const errorMsg: ChatMessage = {
          role: 'assistant',
          content: this.toUserFriendlyError(err),
          timestamp: new Date(),
        };
        this.messages.update(msgs => [...msgs, errorMsg]);
        this.isLoading.set(false);
        this.saveToStorage();
      },
    });
  }

  // ── File Attachment ──

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

  // ── UI Actions ──

  clearChat(): void {
    this.messages.set([]);
    this.activeSessionId.set(null);
    try {
      sessionStorage.removeItem(AgentChatComponent.SESSION_KEY);
      sessionStorage.removeItem(AgentChatComponent.MESSAGES_KEY);
    } catch { }
  }

  // ── Agent Soul Management ──

  loadSoul(): void {
    this.soulLoading.set(true);
    this.api.getAvailableTools().subscribe({
      next: (tools) => {
        this.availableTools.set(tools ?? []);
      },
      error: () => {
        this.availableTools.set([]);
      }
    });

    this.api.getAgentSoul().subscribe({
      next: (data) => {
        this.soulName.set(data.name ?? '');
        this.soulPurpose.set(data.purpose ?? '');
        this.soulPersonality.set(data.personality ?? '');
        this.soulCommunicationStyle.set(data.communicationStyle ?? '');
        this.soulExpertise.set(data.expertiseDomains ?? []);
        this.soulValues.set(data.coreValues ?? []);
        this.soulEmotionalBaseline.set(data.emotionalBaseline ?? 'NEUTRAL');
        this.soulGuardrails.set(data.ethicalGuardrails ?? []);
        this.soulTools.set(data.tools ?? []);
        this.soulLoaded = true;
        this.soulLoading.set(false);
      },
      error: () => {
        // No soul yet — start with defaults
        this.soulLoaded = true;
        this.soulLoading.set(false);
      },
    });
  }

  saveSoul(): void {
    this.soulSaving.set(true);
    const soul = {
      name: this.soulName(),
      purpose: this.soulPurpose(),
      personality: this.soulPersonality(),
      communicationStyle: this.soulCommunicationStyle(),
      expertiseDomains: this.soulExpertise(),
      coreValues: this.soulValues(),
      emotionalBaseline: this.soulEmotionalBaseline(),
      tools: this.soulTools(),
    };
    this.api.updateAgentSoul(soul).subscribe({
      next: () => {
        this.soulSaving.set(false);
        this.snack.open('Soul saved ✨', 'OK', { duration: 2000 });
      },
      error: (err) => {
        this.soulSaving.set(false);
        this.snack.open('Failed to save soul', 'Retry', { duration: 3000 });
      },
    });
  }

  resetSoul(): void {
    this.api.resetAgentSoul().subscribe({
      next: () => {
        this.soulName.set('');
        this.soulPurpose.set('');
        this.soulPersonality.set('');
        this.soulCommunicationStyle.set('');
        this.soulExpertise.set([]);
        this.soulValues.set([]);
        this.soulEmotionalBaseline.set('NEUTRAL');
        this.soulGuardrails.set([]);
        this.soulTools.set([]);
        this.snack.open('Soul reset to default', 'OK', { duration: 2000 });
      },
      error: () => {
        // No soul yet — start with defaults
        this.soulLoaded = true;
        this.soulLoading.set(false);
      },
    });
  }

  isToolEnabled(name: string): boolean {
    return this.soulTools().includes(name);
  }

  toggleTool(name: string, checked: boolean): void {
    const current = this.soulTools();
    if (checked) {
      if (!current.includes(name)) {
        this.soulTools.set([...current, name]);
      }
    } else {
      this.soulTools.set(current.filter(t => t !== name));
    }
  }

  addSoulChip(field: 'expertise' | 'values', event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value.trim();
    if (!value) return;

    if (field === 'expertise') {
      const current = this.soulExpertise();
      if (!current.includes(value)) {
        this.soulExpertise.set([...current, value]);
      }
    } else {
      const current = this.soulValues();
      if (!current.includes(value)) {
        this.soulValues.set([...current, value]);
      }
    }
    input.value = '';
  }

  removeSoulChip(field: 'expertise' | 'values', chipValue: string): void {
    if (field === 'expertise') {
      this.soulExpertise.update(list => list.filter(v => v !== chipValue));
    } else {
      this.soulValues.update(list => list.filter(v => v !== chipValue));
    }
  }

  resetWeights(): void {
    this.relevanceWeight.set(70);
    this.recencyWeight.set(20);
    this.importanceWeight.set(10);
  }

  onTextareaKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendQuery();
    }
  }

  // ── Formatting ──

  formatScore(score: number): string {
    return score?.toFixed(4) ?? '—';
  }

  formatLatency(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  }

  getRecallPercent(latency: any): number {
    if (!latency?.totalMs) return 0;
    return Math.round((latency.recallMs / latency.totalMs) * 100);
  }

  getTierIcon(tier: string): string {
    switch (tier) {
      case 'EPISODIC': return 'auto_stories';
      case 'SEMANTIC': return 'school';
      case 'PROCEDURAL': return 'construction';
      case 'WORKING': return 'memory';
      default: return 'psychology';
    }
  }

  getTierColor(tier: string): string {
    switch (tier) {
      case 'EPISODIC': return '#7c4dff';
      case 'SEMANTIC': return '#00bcd4';
      case 'PROCEDURAL': return '#ff9800';
      case 'WORKING': return '#f44336';
      default: return '#9e9e9e';
    }
  }

  getWeightGradient(): string {
    const segs = this.weightSegments();
    let accum = 0;
    const stops: string[] = [];
    for (const seg of segs) {
      stops.push(`${seg.color} ${accum}%`);
      accum += seg.value;
      stops.push(`${seg.color} ${accum}%`);
    }
    return `conic-gradient(${stops.join(', ')})`;
  }

  // ── Suggestion Chips ──

  useSuggestion(text: string): void {
    this.query.set(text);
    this.sendQuery();
  }

  // ── Error Helpers ──

  isErrorMessage(msg: ChatMessage): boolean {
    return msg.role === 'assistant' && msg.content.startsWith('Error:');
  }

  getErrorDisplayMessage(msg: ChatMessage): string {
    return this.toUserFriendlyError({ message: msg.content });
  }

  private toUserFriendlyError(err: any): string {
    const raw = err?.error?.message ?? err?.message ?? '';
    if (raw.includes('504') || raw.includes('Gateway Time-out') || raw.includes('timeout')) {
      return 'Error: The AI model took too long to respond. This can happen with complex queries. Please try again.';
    }
    if (raw.includes('502') || raw.includes('Bad Gateway')) {
      return 'Error: Unable to reach the AI model. Please check that Ollama is running.';
    }
    if (raw.includes('0 Unknown Error') || raw.includes('Failed to fetch')) {
      return 'Error: Network connection lost. Please check your connection and try again.';
    }
    if (raw.includes('500')) {
      return 'Error: Something went wrong on the server. Please try again in a moment.';
    }
    return 'Error: ' + (raw || 'Something went wrong. Please try again.');
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesEnd()?.nativeElement;
      el?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }, 100);
  }
}
