import { Component, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MemoryService } from '../../core/services/memory.service';

@Component({
  selector: 'app-memories',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="page-container">
      <header class="page-header">
        <h1>Memories</h1>
        <p class="subtitle">Explore and manage the cognitive memory store</p>
      </header>

      <div class="stats-grid">
        @if (memoryService.stats(); as stats) {
          <div class="stat-card">
            <div class="stat-value">{{ stats.totalCount }}</div>
            <div class="stat-label">Total Memories</div>
          </div>
          @for (entry of statsEntries(stats.tierDistribution); track entry[0]) {
            <div class="stat-card">
              <div class="stat-value">{{ entry[1] }}</div>
              <div class="stat-label">{{ entry[0] }}</div>
            </div>
          }
        }
      </div>

      <div class="memory-list">
        @for (memory of memoryService.memories(); track memory.id) {
          <div class="memory-card">
            <div class="memory-tier" [attr.data-tier]="memory.tier">{{ memory.tier }}</div>
            <div class="memory-text">{{ memory.text }}</div>
            <div class="memory-meta">
              <span>Score: {{ memory.score | number:'1.2-2' }}</span>
              <span>Recalls: {{ memory.recallCount }}</span>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 2rem; max-width: 1200px; margin: 0 auto; }
    .page-header h1 { font-size: 1.5rem; font-weight: 700; color: var(--text-primary); }
    .subtitle { color: var(--text-secondary); font-size: 0.85rem; margin-top: 4px; }
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin: 1.5rem 0; }
    .stat-card { background: var(--bg-secondary); border-radius: 12px; padding: 1.25rem; border: 1px solid var(--border); }
    .stat-value { font-size: 1.5rem; font-weight: 700; color: var(--accent); }
    .stat-label { font-size: 0.75rem; color: var(--text-tertiary); text-transform: uppercase; margin-top: 4px; }
    .memory-card { background: var(--bg-secondary); border-radius: 8px; padding: 1rem; margin-bottom: 0.75rem; border: 1px solid var(--border); }
    .memory-tier { font-size: 0.65rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--accent); margin-bottom: 6px; }
    .memory-text { font-size: 0.9rem; color: var(--text-primary); line-height: 1.5; }
    .memory-meta { font-size: 0.75rem; color: var(--text-tertiary); margin-top: 8px; display: flex; gap: 1rem; }
  `]
})
export class MemoriesComponent implements OnInit {
  readonly memoryService = inject(MemoryService);

  ngOnInit() {
    this.memoryService.loadMemories();
    this.memoryService.loadStats();
  }

  statsEntries(dist: Record<string, number>): [string, number][] {
    return Object.entries(dist);
  }
}
