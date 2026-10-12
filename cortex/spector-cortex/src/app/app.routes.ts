import { Routes } from '@angular/router';
import { ShellComponent } from './core/layout/shell/shell.component';
import { featureGuard } from './core/guards/feature.guard';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
    title: 'Sign In — Spector Cortex',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        title: 'Dashboard — Spector Cortex',
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('./features/chat/agent-chat.component').then((m) => m.AgentChatComponent),
        canActivate: [featureGuard('chatEnabled')],
        title: 'Chat — Spector Cortex',
      },
      {
        path: 'query',
        loadComponent: () =>
          import('./features/query/query.component').then((m) => m.QueryComponent),
        title: 'Query — Spector Cortex',
      },
      {
        path: 'memories',
        loadChildren: () =>
          import('./features/memories/memories.routes').then((m) => m.MEMORIES_ROUTES),
      },
      {
        path: 'graph',
        loadComponent: () =>
          import('./features/graph/graph-explorer.component').then((m) => m.GraphExplorerComponent),
        title: 'Graph — Spector Cortex',
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
        title: 'Settings — Spector Cortex',
      },
      {
        path: 'memory-health',
        loadComponent: () =>
          import('./features/health/health.component').then((m) => m.HealthComponent),
        title: 'Health — Spector Cortex',
      },
      {
        path: 'admin',
        canActivate: [roleGuard('admin', 'super-admin')],
        loadChildren: () =>
          import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: 'control-center',
        redirectTo: 'admin',
        pathMatch: 'full',
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];
