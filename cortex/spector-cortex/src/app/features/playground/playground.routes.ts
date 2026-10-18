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

import { Routes } from '@angular/router';
import { PlaygroundComponent } from './playground.component';

export const PLAYGROUND_ROUTES: Routes = [
  {
    path: '',
    component: PlaygroundComponent,
    children: [
      {
        path: '',
        redirectTo: 'console',
        pathMatch: 'full',
      },
      {
        path: 'console',
        loadComponent: () =>
          import('./playground-console.component').then((m) => m.PlaygroundConsoleComponent),
        title: 'Remember / Recall Console — Spector Playground',
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('../chat/agent-chat.component').then((m) => m.AgentChatComponent),
        title: 'Agent Chat — Spector Playground',
      },
      {
        path: 'persona',
        loadComponent: () =>
          import('./playground-persona.component').then((m) => m.PlaygroundPersonaComponent),
        title: 'Persona & Soul — Spector Playground',
      },
    ],
  },
];
