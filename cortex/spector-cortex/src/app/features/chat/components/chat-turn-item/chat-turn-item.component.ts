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

import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MarkdownPipe } from '../../../../shared/pipes/markdown.pipe';
import { ThinkingAccordionComponent } from '../thinking-accordion/thinking-accordion.component';
import { ToolCardComponent } from '../tool-card/tool-card.component';
import { ChatTurnView } from '../../models/chat-turn.model';

@Component({
  selector: 'cortex-chat-turn-item',
  standalone: true,
  imports: [
    MatIconModule,
    MatTooltipModule,
    MarkdownPipe,
    ThinkingAccordionComponent,
    ToolCardComponent,
  ],
  templateUrl: './chat-turn-item.component.html',
  styleUrl: './chat-turn-item.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatTurnItemComponent {
  readonly turn = input.required<ChatTurnView>();

  readonly thinkingToggle = output<{ turnId: string; expanded: boolean }>();

  onThinkingToggle(expanded: boolean): void {
    this.thinkingToggle.emit({
      turnId: this.turn().turnId,
      expanded,
    });
  }
}
