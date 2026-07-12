import {
  Component,
  inject,
  signal,
  computed,
  effect,
  ElementRef,
  ViewChild,
  HostListener,
  OnDestroy,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';

/** Command palette action categories */
type PaletteCategory = 'Recent' | 'Navigation' | 'Action' | 'Theme' | 'Graph' | 'Memory';

/** A command palette action item */
interface PaletteItem {
  id: string;
  icon: string;
  label: string;
  category: PaletteCategory;
  shortcut?: string;
  action: () => void;
  adminOnly?: boolean;
}

const RECENT_COMMANDS_KEY = 'spector.palette.recent';
const MAX_RECENT = 5;

@Component({
  selector: 'cortex-command-palette',
  standalone: true,
  imports: [MatIconModule, FormsModule],
  template: `
    @if (isOpen()) {
      <div class="palette-backdrop" (click)="close()"></div>
      <div class="palette-container" (keydown)="onKeyDown($event)">
        <div class="palette-search-row">
          <mat-icon class="palette-search-icon">search</mat-icon>
          <input
            #searchInput
            class="palette-search"
            type="text"
            placeholder="Type a command..."
            [ngModel]="query()"
            (ngModelChange)="query.set($event)"
            autocomplete="off"
            spellcheck="false"
          />
          <span class="palette-hint cortex-mono">ESC</span>
        </div>

        <div class="palette-results">
          @for (group of groupedResults(); track group.category) {
            <div class="palette-group-label">{{ group.category }}</div>
            @for (item of group.items; track item.id; let i = $index) {
              <button
                class="palette-item"
                [class.palette-item-active]="isActive(item)"
                [attr.data-category]="item.category"
                (click)="executeItem(item)"
                (mouseenter)="activeIndex.set(getGlobalIndex(group.category, i))">
                <mat-icon class="palette-item-icon" [attr.data-category]="item.category">{{ item.icon }}</mat-icon>
                <span class="palette-item-label">{{ item.label }}</span>
                @if (item.shortcut) {
                  <span class="palette-item-shortcut cortex-mono">{{ item.shortcut }}</span>
                }
              </button>
            }
          } @empty {
            <div class="palette-empty">
              <mat-icon>search_off</mat-icon>
              <span>No matching commands</span>
            </div>
          }
        </div>

        <div class="palette-footer">
          <span class="palette-footer-hint">
            <span class="key">↑↓</span> navigate
            <span class="key">↵</span> select
            <span class="key">esc</span> close
          </span>
        </div>
      </div>
    }
  `,
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent implements OnDestroy {
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);

  readonly isOpen = signal(false);
  readonly query = signal('');
  readonly activeIndex = signal(0);
  readonly recentIds = signal<string[]>(this.loadRecentIds());

  private readonly allItems: PaletteItem[] = [
    // Navigation
    { id: 'nav-graph', icon: 'hub', label: 'Graph Explorer', category: 'Navigation', action: () => this.navigateTo('/graph') },
    { id: 'nav-dashboard', icon: 'dashboard', label: 'Dashboard', category: 'Navigation', action: () => this.navigateTo('/dashboard') },
    { id: 'nav-query', icon: 'search', label: 'Query', category: 'Navigation', action: () => this.navigateTo('/query') },
    { id: 'nav-memories', icon: 'psychology', label: 'Memories', category: 'Navigation', action: () => this.navigateTo('/memories') },
    { id: 'nav-settings', icon: 'tune', label: 'Settings', category: 'Navigation', action: () => this.navigateTo('/settings') },
    { id: 'nav-admin', icon: 'admin_panel_settings', label: 'Admin Panel', category: 'Navigation', action: () => this.navigateTo('/admin'), adminOnly: true },
    { id: 'nav-connectors', icon: 'cable', label: 'Connectors', category: 'Navigation', action: () => this.navigateTo('/connectors'), adminOnly: true },
    { id: 'nav-providers', icon: 'smart_toy', label: 'Providers', category: 'Navigation', action: () => this.navigateTo('/providers'), adminOnly: true },
    { id: 'nav-templates', icon: 'category', label: 'Templates', category: 'Navigation', action: () => this.navigateTo('/templates'), adminOnly: true },
    { id: 'nav-users', icon: 'people', label: 'User Management', category: 'Navigation', action: () => this.navigateTo('/users'), adminOnly: true },
    { id: 'nav-replay', icon: 'replay', label: 'Session Replay', category: 'Navigation', action: () => this.navigateTo('/replay') },

    // Theme
    { id: 'theme-toggle', icon: 'contrast', label: 'Toggle Dark / Light Theme', category: 'Theme', shortcut: '', action: () => this.theme.toggle() },

    // Graph
    { id: 'graph-screenshot', icon: 'camera_alt', label: 'Capture Graph Screenshot', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:screenshot') },
    { id: 'graph-share', icon: 'share', label: 'Share Graph', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:share') },
    { id: 'graph-reset', icon: 'restart_alt', label: 'Reset Graph View', category: 'Graph', action: () => this.navigateTo('/graph') },
    { id: 'graph-filters', icon: 'tune', label: 'Toggle Graph Filters', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:filters') },

    // Memory
    { id: 'mem-remember', icon: 'add_circle', label: 'Remember New Memory', category: 'Memory', action: () => this.navigateTo('/studio') },
    { id: 'mem-search', icon: 'manage_search', label: 'Search Memories', category: 'Memory', action: () => this.navigateTo('/memories') },
    { id: 'mem-recall', icon: 'psychology_alt', label: 'Run Recall Query', category: 'Memory', action: () => this.navigateTo('/query') },

    // Actions
    { id: 'action-refresh', icon: 'refresh', label: 'Refresh Page', category: 'Action', shortcut: 'F5', action: () => { window.location.reload(); } },
    { id: 'action-copy-url', icon: 'content_copy', label: 'Copy Current URL', category: 'Action', action: () => this.copyCurrentUrl() },
  ];

  /** Filtered items based on query and admin role */
  readonly filteredItems = computed(() => {
    const q = this.query().toLowerCase().trim();
    const isAdmin = this.auth.isAdmin();
    let items = this.allItems.filter(item => !item.adminOnly || isAdmin);

    if (q) {
      items = items.filter(item =>
        item.label.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q) ||
        item.id.toLowerCase().includes(q)
      );
    }

    return items;
  });

  /** Build recently-used items from stored IDs */
  private readonly recentItems = computed(() => {
    const ids = this.recentIds();
    const isAdmin = this.auth.isAdmin();
    return ids
      .map(id => this.allItems.find(item => item.id === id))
      .filter((item): item is PaletteItem => !!item && (!item.adminOnly || isAdmin));
  });

  /** Group filtered results by category */
  readonly groupedResults = computed(() => {
    const q = this.query().toLowerCase().trim();
    const items = this.filteredItems();
    const groups: { category: string; items: PaletteItem[] }[] = [];
    const categoryOrder: PaletteCategory[] = ['Recent', 'Navigation', 'Graph', 'Memory', 'Theme', 'Action'];

    // Show recently-used only when no query is typed
    if (!q) {
      const recent = this.recentItems();
      if (recent.length > 0) {
        groups.push({ category: 'Recent', items: recent });
      }
    }

    for (const cat of categoryOrder) {
      if (cat === 'Recent') continue; // Already handled above
      const catItems = items.filter(i => i.category === cat);
      if (catItems.length > 0) {
        groups.push({ category: cat, items: catItems });
      }
    }
    return groups;
  });

  /** Flat list for keyboard navigation */
  private readonly flatResults = computed(() => {
    const groups = this.groupedResults();
    return groups.flatMap(g => g.items);
  });

  constructor() {
    // Reset active index when query changes
    effect(() => {
      this.query(); // track
      this.activeIndex.set(0);
    });
  }

  ngOnDestroy(): void {
    // Cleanup if needed
  }

  @HostListener('document:keydown', ['$event'])
  onGlobalKeyDown(event: KeyboardEvent): void {
    // Ctrl+K / Cmd+K to toggle
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      this.toggle();
    }
  }

  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  open(): void {
    this.query.set('');
    this.activeIndex.set(0);
    this.isOpen.set(true);
    // Focus the input after Angular renders it
    setTimeout(() => this.searchInput?.nativeElement?.focus(), 50);
  }

  close(): void {
    this.isOpen.set(false);
    this.query.set('');
  }

  onKeyDown(event: KeyboardEvent): void {
    const items = this.flatResults();
    const currentIndex = this.activeIndex();

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeIndex.set(Math.min(currentIndex + 1, items.length - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.set(Math.max(currentIndex - 1, 0));
        break;
      case 'Enter':
        event.preventDefault();
        if (items[currentIndex]) {
          this.executeItem(items[currentIndex]);
        }
        break;
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
    }
  }

  executeItem(item: PaletteItem): void {
    this.close();
    this.trackRecent(item.id);
    item.action();
  }

  isActive(item: PaletteItem): boolean {
    const items = this.flatResults();
    return items[this.activeIndex()] === item;
  }

  getGlobalIndex(category: string, localIndex: number): number {
    const groups = this.groupedResults();
    let globalIndex = 0;
    for (const group of groups) {
      if (group.category === category) {
        return globalIndex + localIndex;
      }
      globalIndex += group.items.length;
    }
    return 0;
  }

  private navigateTo(path: string): void {
    this.router.navigate([path]);
  }

  /** Navigate to a route and dispatch a custom event (for cross-component communication) */
  private navigateAndEmit(path: string, eventName: string): void {
    this.router.navigate([path]).then(() => {
      // Small delay to let the component initialize if navigating
      setTimeout(() => window.dispatchEvent(new CustomEvent(eventName)), 200);
    });
  }

  /** Copy current page URL to clipboard */
  private copyCurrentUrl(): void {
    navigator.clipboard.writeText(window.location.href).catch(() => {});
  }

  // ── Recently Used Tracking ──────────────────────────────

  private trackRecent(id: string): void {
    const ids = this.recentIds().filter(i => i !== id);
    ids.unshift(id);
    const trimmed = ids.slice(0, MAX_RECENT);
    this.recentIds.set(trimmed);
    this.saveRecentIds(trimmed);
  }

  private loadRecentIds(): string[] {
    try {
      const raw = localStorage.getItem(RECENT_COMMANDS_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed.slice(0, MAX_RECENT) : [];
      }
    } catch { /* ignore */ }
    return [];
  }

  private saveRecentIds(ids: string[]): void {
    try {
      localStorage.setItem(RECENT_COMMANDS_KEY, JSON.stringify(ids));
    } catch { /* storage unavailable */ }
  }
}
