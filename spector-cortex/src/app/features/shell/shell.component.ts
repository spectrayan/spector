import { Component, inject, signal, computed } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HeaderComponent } from '../header/header.component';
import { MaintenanceBannerComponent } from '../maintenance-banner/maintenance-banner.component';
import { CommandPaletteComponent } from '../command-palette/command-palette.component';
import { EventStreamService } from '../../core/services/event-stream.service';
import { AuthService } from '../../core/services/auth.service';
import { OnboardingTourComponent } from '../onboarding-tour/onboarding-tour.component';

interface NavItem {
  icon: string;
  label: string;
  route: string;
  adminOnly?: boolean;
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

  private readonly eventStream = inject(EventStreamService);
  private readonly auth = inject(AuthService);

  private readonly allNavItems: NavItem[] = [
    // ── Primary (cognitive agent first) ──

    { icon: 'hub', label: 'Graph', route: '/graph' },
    { icon: 'search', label: 'Query', route: '/query' },
    { icon: 'auto_stories', label: 'Memories', route: '/memories' },

  ];

  /** Filtered nav items based on user role. */
  readonly navItems = computed(() =>
    this.allNavItems.filter(item => !item.adminOnly || this.auth.isAdmin())
  );

  constructor() {
    this.eventStream.connect();
  }
}
