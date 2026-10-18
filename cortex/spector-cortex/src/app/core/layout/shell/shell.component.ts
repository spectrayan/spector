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

import { Component, inject, signal, computed, DestroyRef } from '@angular/core';
import {
  RouterOutlet,
  RouterLink,
  RouterLinkActive,
  Router,
  NavigationEnd,
} from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HeaderComponent } from '../header/header.component';
import { MaintenanceBannerComponent } from '../maintenance-banner/maintenance-banner.component';
import { CommandPaletteComponent } from '../command-palette/command-palette.component';
import { EventStreamService } from '@core/services/event-stream.service';
import { AuthService } from '@core/services/auth.service';
import { FeatureFlagService } from '@core/services/feature-flag.service';
import { OnboardingTourComponent } from '../onboarding-tour/onboarding-tour.component';

export interface NavItem {
  icon: string;
  label: string;
  route: string;
  adminOnly?: boolean;
  superAdminOnly?: boolean;
  featureFlags?: string[];
}

@Component({
  selector: 'cortex-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatTooltipModule,
    HeaderComponent,
    MaintenanceBannerComponent,
    CommandPaletteComponent,
    OnboardingTourComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  collapsed = signal(false);

  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly eventStream = inject(EventStreamService);
  protected readonly auth = inject(AuthService);
  private readonly featureFlags = inject(FeatureFlagService);

  /** Current router path used to switch between User Workspace (/) and Admin Console (/admin/**). */
  readonly currentUrl = signal(this.router.url || '/dashboard');

  /** True when the active route is inside the Admin Console (`/admin/**`). */
  readonly isAdminConsole = computed(() => {
    const url = this.currentUrl();
    return url === '/admin' || url.startsWith('/admin/') || url.startsWith('/admin?');
  });

  /** User Workspace (`/`) navigation items. */
  readonly workspaceNavItems: NavItem[] = [
    { icon: 'dashboard', label: 'Dashboard', route: '/dashboard' },
    { icon: 'auto_stories', label: 'Memories', route: '/memories' },
    { icon: 'search', label: 'Query', route: '/query' },
    { icon: 'hub', label: 'Graph', route: '/graph' },
    { icon: 'donut_large', label: 'Health', route: '/memory-health' },
    { icon: 'share', label: 'Sharing', route: '/sharing' },
    { icon: 'tune', label: 'Settings', route: '/settings' },
    {
      icon: 'science',
      label: 'Playground',
      route: '/playground',
      featureFlags: ['playgroundEnabled', 'chatEnabled'],
    },
  ];

  /** Admin Console (`/admin/**`) navigation items. */
  readonly adminNavItems: NavItem[] = [
    { icon: 'space_dashboard', label: 'Overview', route: '/admin/overview', adminOnly: true },
    { icon: 'domain', label: 'Tenants', route: '/admin/tenants', superAdminOnly: true },
    { icon: 'group', label: 'Accounts & Agents', route: '/admin/accounts', adminOnly: true },
    { icon: 'folder_special', label: 'Namespaces', route: '/admin/namespaces', adminOnly: true },
    { icon: 'insights', label: 'Analytics', route: '/admin/analytics', adminOnly: true },
    { icon: 'vpn_key', label: 'Keys & Encryption', route: '/admin/keys', adminOnly: true },
    { icon: 'dns', label: 'Infra', route: '/admin/infra', adminOnly: true },
    { icon: 'fact_check', label: 'Audit Log', route: '/admin/audit', adminOnly: true },
    { icon: 'emergency', label: 'Break-Glass', route: '/admin/break-glass', adminOnly: true },
  ];

  /** Filtered nav items based on active layout (Admin Console vs User Workspace), user role, and feature flags. */
  readonly navItems = computed(() => {
    const source = this.isAdminConsole() && this.auth.isAdmin()
      ? this.adminNavItems
      : this.workspaceNavItems;

    return source.filter(item =>
      (!item.superAdminOnly || this.auth.isSuperAdmin()) &&
      (!item.adminOnly || this.auth.isAdmin()) &&
      (!item.featureFlags || this.featureFlags.isAnyEnabled(...item.featureFlags))
    );
  });

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e) => {
        this.currentUrl.set(e.urlAfterRedirects || e.url);
      });

    this.eventStream.connect();
  }
}

