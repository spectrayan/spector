import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService, ChatMessage, ChatSession } from '../../core/services/chat.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="chat-layout">
      <!-- Sidebar -->
      <aside class="chat-sidebar">
        <div class="sidebar-header">
          <h2>Sessions</h2>
          <button class="btn-new" (click)="chatService.createSession()">
            <span>+</span> New Chat
          </button>
        </div>
        <div class="session-list">
          @for (session of chatService.sessions(); track session.sessionId) {
            <div class="session-item"
                 [class.active]="chatService.activeSessionId() === session.sessionId"
                 (click)="chatService.selectSession(session.sessionId)">
              <div class="session-preview">{{ session.preview || 'New conversation' }}</div>
              <div class="session-meta">{{ session.messageCount }} messages</div>
            </div>
          }
        </div>
      </aside>

      <!-- Main Chat Area -->
      <main class="chat-main">
        <div class="messages-container">
          @for (msg of chatService.messages(); track $index) {
            <div class="message" [class]="'message-' + msg.role">
              <div class="message-avatar">
                {{ msg.role === 'user' ? '👤' : '🧠' }}
              </div>
              <div class="message-content">
                <div class="message-role">{{ msg.role === 'user' ? 'You' : 'Spector' }}</div>
                <div class="message-text">{{ msg.content }}</div>
              </div>
            </div>
          }
          @if (chatService.loading()) {
            <div class="message message-assistant">
              <div class="message-avatar">🧠</div>
              <div class="message-content">
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- Input -->
        <div class="chat-input-area">
          <div class="input-container">
            <textarea
              [(ngModel)]="inputText"
              placeholder="Ask Spector anything..."
              (keydown.enter)="onSend($event)"
              rows="1"
            ></textarea>
            <button class="btn-send" (click)="send()" [disabled]="!inputText().trim()">
              ➤
            </button>
          </div>
        </div>
      </main>
    </div>
  `,
  styleUrl: './chat.component.scss'
})
export class ChatComponent implements OnInit {
  readonly chatService = inject(ChatService);
  readonly inputText = signal('');

  ngOnInit() {
    this.chatService.loadSessions();
  }

  send() {
    const text = this.inputText().trim();
    if (!text) return;
    this.chatService.sendMessage(text);
    this.inputText.set('');
  }

  onSend(event: Event) {
    event.preventDefault();
    this.send();
  }
}
