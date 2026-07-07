import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-shell">
      <nav class="sidebar-nav">
        <div class="nav-brand">
          <span class="brand-icon">🧠</span>
          <span class="brand-text">Spector</span>
        </div>

        <div class="nav-links">
          <a routerLink="/chat" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">💬</span>
            <span class="nav-label">Chat</span>
          </a>
          <a routerLink="/memories" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">🧬</span>
            <span class="nav-label">Memories</span>
          </a>
          <a routerLink="/agents" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">🤖</span>
            <span class="nav-label">Agents</span>
          </a>
          <a routerLink="/connectors" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">🔌</span>
            <span class="nav-label">Connectors</span>
          </a>
        </div>

        <div class="nav-footer">
          <a routerLink="/settings" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">⚙️</span>
            <span class="nav-label">Settings</span>
          </a>
        </div>
      </nav>

      <main class="main-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './app.scss'
})
export class App {
  title = 'Spector Cortex';
}
