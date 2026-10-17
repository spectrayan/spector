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

import { Component, OnInit, inject } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDividerModule } from '@angular/material/divider';
import { RouterLink } from '@angular/router';

import { ThemeService } from '@core/services/theme.service';
import { CortexStateService, NamespaceItem } from '@core/services/cortex-state.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/services/auth.service';
import { SynapseApiService } from '@core/services/synapse-api.service';

@Component({
  selector: 'cortex-header',
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatSelectModule,
    MatChipsModule,
    MatTooltipModule,
    MatMenuModule,
    MatBadgeModule,
    MatProgressBarModule,
    MatDividerModule,
    RouterLink,
  ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
  protected readonly theme = inject(ThemeService);
  protected readonly state = inject(CortexStateService);
  protected readonly notif = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(SynapseApiService);

  ngOnInit(): void {
    this.loadNamespaces();
  }

  loadNamespaces(): void {
    forkJoin({
      introspect: this.api.introspectAccount().pipe(catchError(() => of(null))),
      list: this.api.listNamespaces().pipe(catchError(() => of([]))),
    }).subscribe(({ introspect, list }) => {
      const accountId: string | null =
        introspect?.accountId ?? this.auth.currentUser()?.username ?? null;
      const defaultNsId: string | null = introspect?.defaultNamespaceId ?? null;

      const rawList: any[] = Array.isArray(list) ? [...list] : [];
      if (Array.isArray(introspect?.namespaces)) {
        for (const ns of introspect.namespaces) {
          if (!rawList.some((r) => r.namespaceId === ns.namespaceId)) {
            rawList.push(ns);
          }
        }
      }

      const items: NamespaceItem[] = rawList.map((ns) => ({
        namespaceId: ns.namespaceId,
        slug: ns.slug || ns.namespaceId,
        ownerAccountId: ns.ownerAccountId,
        type: ns.type || 'PERSONAL',
        status: ns.status || 'ACTIVE',
        displayName: ns.displayName ?? null,
        description: ns.description ?? null,
        legalHold: Boolean(ns.legalHold),
        owned: accountId ? ns.ownerAccountId === accountId : true,
      }));

      this.state.setAvailableNamespaces(items, defaultNsId);
    });
  }

  selectNamespace(slugOrId: string): void {
    if (!slugOrId || slugOrId === this.state.currentNamespace()) {
      return;
    }
    this.state.setCurrentNamespace(slugOrId);
    this.api
      .setDefaultNamespace(slugOrId)
      .pipe(catchError(() => of(null)))
      .subscribe();
  }
}

