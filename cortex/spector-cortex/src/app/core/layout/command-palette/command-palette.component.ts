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
  effect,
  ElementRef,
  ViewChild,
  HostListener,
  OnDestroy,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { AuthService } from '@core/services/auth.service';
import { FeatureFlagService } from '@core/services/feature-flag.service';
import { ThemeService } from '@core/services/theme.service';

/** Command palette action categories */
type PaletteCategory = 'Recent' | 'Navigation' | 'Action' | 'Theme' | 'Graph' | 'Memory';

/** A command palette action item */
export interface PaletteItem {
  id: string;
  icon: string;
  label: string;
  route?: string;
  category: PaletteCategory;
  shortcut?: string;
  action: () => void;
  adminOnly?: boolean;
  superAdminOnly?: boolean;
  featureFlags?: string[];
}

const RECENT_COMMANDS_KEY = 'spector.palette.recent';
const MAX_RECENT = 5;

@Component({
  selector: 'cortex-command-palette',
  standalone: true,
  imports: [MatIconModule, FormsModule],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent implements OnDestroy {
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly featureFlags = inject(FeatureFlagService);
  private readonly theme = inject(ThemeService);

  readonly isOpen = signal(false);
  readonly query = signal('');
  readonly activeIndex = signal(0);
  readonly recentIds = signal<string[]>(this.loadRecentIds());

  readonly allItems: PaletteItem[] = [
    // User Workspace Navigation
    { id: 'nav-dashboard', icon: 'dashboard', label: 'Dashboard', route: '/dashboard', category: 'Navigation', action: () => this.navigateTo('/dashboard') },
    { id: 'nav-memories', icon: 'psychology', label: 'Memories', route: '/memories', category: 'Navigation', action: () => this.navigateTo('/memories') },
    { id: 'nav-query', icon: 'search', label: 'Query', route: '/query', category: 'Navigation', action: () => this.navigateTo('/query') },
    { id: 'nav-graph', icon: 'hub', label: 'Graph Explorer', route: '/graph', category: 'Navigation', action: () => this.navigateTo('/graph') },
    { id: 'nav-health', icon: 'donut_large', label: 'Memory Health', route: '/memory-health', category: 'Navigation', action: () => this.navigateTo('/memory-health') },
    { id: 'nav-settings', icon: 'tune', label: 'Settings', route: '/settings', category: 'Navigation', action: () => this.navigateTo('/settings') },
    {
      id: 'nav-playground',
      icon: 'science',
      label: 'Playground',
      route: '/playground',
      category: 'Navigation',
      featureFlags: ['playgroundEnabled', 'chatEnabled'],
      action: () => this.navigateTo('/playground'),
    },

    // Admin Console Navigation
    { id: 'nav-admin-overview', icon: 'admin_panel_settings', label: 'Admin Console: Overview', route: '/admin/overview', category: 'Navigation', action: () => this.navigateTo('/admin/overview'), adminOnly: true },
    { id: 'nav-admin-tenants', icon: 'domain', label: 'Admin Console: Tenants', route: '/admin/tenants', category: 'Navigation', action: () => this.navigateTo('/admin/tenants'), superAdminOnly: true },
    { id: 'nav-admin-accounts', icon: 'group', label: 'Admin Console: Accounts & Agents', route: '/admin/accounts', category: 'Navigation', action: () => this.navigateTo('/admin/accounts'), adminOnly: true },
    { id: 'nav-admin-namespaces', icon: 'folder_special', label: 'Admin Console: Namespaces', route: '/admin/namespaces', category: 'Navigation', action: () => this.navigateTo('/admin/namespaces'), adminOnly: true },
    { id: 'nav-admin-analytics', icon: 'insights', label: 'Admin Console: Analytics', route: '/admin/analytics', category: 'Navigation', action: () => this.navigateTo('/admin/analytics'), adminOnly: true },
    { id: 'nav-admin-keys', icon: 'vpn_key', label: 'Admin Console: Keys & Encryption', route: '/admin/keys', category: 'Navigation', action: () => this.navigateTo('/admin/keys'), adminOnly: true },
    { id: 'nav-admin-infra', icon: 'dns', label: 'Admin Console: Infrastructure', route: '/admin/infra', category: 'Navigation', action: () => this.navigateTo('/admin/infra'), adminOnly: true },
    { id: 'nav-admin-audit', icon: 'fact_check', label: 'Admin Console: Audit Log', route: '/admin/audit', category: 'Navigation', action: () => this.navigateTo('/admin/audit'), adminOnly: true },
    { id: 'nav-admin-break-glass', icon: 'emergency', label: 'Admin Console: Break-Glass', route: '/admin/break-glass', category: 'Navigation', action: () => this.navigateTo('/admin/break-glass'), adminOnly: true },

    // Theme
    { id: 'theme-toggle', icon: 'contrast', label: 'Toggle Dark / Light Theme', category: 'Theme', shortcut: '', action: () => this.theme.toggle() },

    // Graph
    { id: 'graph-screenshot', icon: 'camera_alt', label: 'Capture Graph Screenshot', route: '/graph', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:screenshot') },
    { id: 'graph-share', icon: 'share', label: 'Share Graph', route: '/graph', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:share') },
    { id: 'graph-reset', icon: 'restart_alt', label: 'Reset Graph View', route: '/graph', category: 'Graph', action: () => this.navigateTo('/graph') },
    { id: 'graph-filters', icon: 'tune', label: 'Toggle Graph Filters', route: '/graph', category: 'Graph', action: () => this.navigateAndEmit('/graph', 'cortex:graph:filters') },

    // Memory
    { id: 'mem-remember', icon: 'add_circle', label: 'Remember New Memory', route: '/memories', category: 'Memory', action: () => this.navigateTo('/memories') },
    { id: 'mem-search', icon: 'manage_search', label: 'Search Memories', route: '/memories', category: 'Memory', action: () => this.navigateTo('/memories') },
    { id: 'mem-recall', icon: 'psychology_alt', label: 'Run Recall Query', route: '/query', category: 'Memory', action: () => this.navigateTo('/query') },

    // Actions
    { id: 'action-refresh', icon: 'refresh', label: 'Refresh Page', category: 'Action', shortcut: 'F5', action: () => { window.location.reload(); } },
    { id: 'action-copy-url', icon: 'content_copy', label: 'Copy Current URL', category: 'Action', action: () => this.copyCurrentUrl() },
  ];

  private isItemPermitted(item: PaletteItem): boolean {
    if (item.superAdminOnly && !this.auth.isSuperAdmin()) return false;
    if (item.adminOnly && !this.auth.isAdmin()) return false;
    if (item.featureFlags && !this.featureFlags.isAnyEnabled(...item.featureFlags)) return false;
    return true;
  }

  /** Filtered items based on query, user role, and feature flags */
  readonly filteredItems = computed(() => {
    const q = this.query().toLowerCase().trim();
    let items = this.allItems.filter(item => this.isItemPermitted(item));

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
    return ids
      .map(id => this.allItems.find(item => item.id === id))
      .filter((item): item is PaletteItem => !!item && this.isItemPermitted(item));
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
