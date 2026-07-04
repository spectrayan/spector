import { Component } from '@angular/core';

@Component({
  selector: 'app-connectors',
  standalone: true,
  template: `
    <div class="page-container">
      <header class="page-header">
        <h1>Connectors</h1>
        <p class="subtitle">Connect data sources to feed the cognitive memory engine</p>
      </header>
      <div class="placeholder-card">
        <span class="icon">🔌</span>
        <p>Connector templates coming soon — File System, Git, Confluence, Jira, Slack, and more.</p>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 2rem; max-width: 1200px; margin: 0 auto; }
    .page-header h1 { font-size: 1.5rem; font-weight: 700; color: var(--text-primary); }
    .subtitle { color: var(--text-secondary); font-size: 0.85rem; margin-top: 4px; }
    .placeholder-card { background: var(--bg-secondary); border-radius: 12px; padding: 3rem; text-align: center; border: 1px dashed var(--border); margin-top: 2rem; }
    .icon { font-size: 3rem; display: block; margin-bottom: 1rem; }
    p { color: var(--text-secondary); font-size: 0.9rem; max-width: 400px; margin: 0 auto; line-height: 1.6; }
  `]
})
export class ConnectorsComponent {}
