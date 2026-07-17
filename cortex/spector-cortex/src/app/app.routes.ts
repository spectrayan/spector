import { Routes } from '@angular/router';
import { ShellComponent } from './features/shell/shell.component';
import { featureGuard } from './core/guards/feature.guard';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
        title: 'Dashboard — Spector Cortex'
      },
      {
        path: 'chat',
        loadComponent: () => import('./features/agent-chat/agent-chat.component').then(m => m.AgentChatComponent),
        canActivate: [featureGuard('chatEnabled')],
        title: 'Chat — Spector Cortex'
      },
      {
        path: 'query',
        loadComponent: () => import('./features/query/query.component').then(m => m.QueryComponent),
        title: 'Query — Spector Cortex'
      },
      {
        path: 'memories',
        loadComponent: () => import('./features/memory-table/memory-table.component').then(m => m.MemoryTableComponent),
        title: 'Memories — Spector Cortex'
      },
      {
        path: 'memories/:id',
        loadComponent: () => import('./features/memory-detail/memory-detail.component').then(m => m.MemoryDetailComponent),
        title: 'Memory Detail — Spector Cortex'
      },

      {
        path: 'graph',
        loadComponent: () => import('./features/graph-explorer/graph-explorer.component').then(m => m.GraphExplorerComponent),
        title: 'Graph — Spector Cortex'
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent),
        title: 'Settings — Spector Cortex'
      },
      {
        path: '**',
        redirectTo: 'dashboard'
      }
    ]
  }
];
