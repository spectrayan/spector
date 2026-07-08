import { Routes } from '@angular/router';
import { ShellComponent } from './features/shell/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: '',
        redirectTo: 'chat',
        pathMatch: 'full'
      },
      {
        path: 'chat',
        loadComponent: () => import('./features/agent-chat/agent-chat.component').then(m => m.AgentChatComponent),
        title: 'Chat — Spector Cortex'
      },
      {
        path: 'memories',
        loadComponent: () => import('./features/memory-table/memory-table.component').then(m => m.MemoryTableComponent),
        title: 'Memories — Spector Cortex'
      },
      {
        path: 'dashboard',
        redirectTo: 'chat'
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
        redirectTo: 'chat'
      }
    ]
  }
];
