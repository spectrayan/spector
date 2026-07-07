import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'chat',
    pathMatch: 'full'
  },
  {
    path: 'chat',
    loadComponent: () => import('./features/chat/chat.component').then(m => m.ChatComponent),
    title: 'Chat — Spector Cortex'
  },
  {
    path: 'memories',
    loadComponent: () => import('./features/memories/memories.component').then(m => m.MemoriesComponent),
    title: 'Memories — Spector Cortex'
  },
  {
    path: 'agents',
    loadComponent: () => import('./features/agents/agents.component').then(m => m.AgentsComponent),
    title: 'Agents — Spector Cortex'
  },
  {
    path: 'connectors',
    loadComponent: () => import('./features/connectors/connectors.component').then(m => m.ConnectorsComponent),
    title: 'Connectors — Spector Cortex'
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
];
