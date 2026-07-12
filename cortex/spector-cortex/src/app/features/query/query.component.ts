import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { QueryInputComponent } from '../query-input/query-input.component';
import { QueryHistoryComponent } from '../query-history/query-history.component';
import { PipelineFunnelComponent } from '../pipeline-funnel/pipeline-funnel.component';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { CognitiveProfile, PROFILE_PARAMS } from '../../core/models/memory-types';

@Component({
  selector: 'cortex-query',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatSliderModule,
    MatTooltipModule,
    MatDividerModule,
    QueryInputComponent,
    QueryHistoryComponent,
    PipelineFunnelComponent,
  ],
  templateUrl: './query.component.html',
  styleUrl: './query.component.scss',
})
export class QueryComponent {
  protected readonly state = inject(CortexStateService);

  protected readonly filterText = signal('');

  readonly filteredRecallResults = computed(() => {
    const list = this.state.recallResults();
    const search = this.filterText().toLowerCase().trim();
    if (!search) return list;
    return list.filter(result =>
      result.text.toLowerCase().includes(search) ||
      result.id.toLowerCase().includes(search) ||
      (result.synapticTags && result.synapticTags.some((t: string) => t.toLowerCase().includes(search))) ||
      result.memoryType.toLowerCase().includes(search)
    );
  });

  // Expose cognitive profiles and definitions
  protected readonly CognitiveProfile = CognitiveProfile;
  protected readonly profileParams = PROFILE_PARAMS;
  protected readonly profiles = Object.values(CognitiveProfile);

  // Tenant / namespace list
  protected readonly namespaces = [
    { value: 'default', label: 'Default Core Namespace' },
    { value: 'finance', label: 'Finance & Projection' },
    { value: 'engineering-team-a', label: 'Engineering Team A' },
    { value: 'compliance-vault', label: 'Compliance & Governance' },
    { value: 'wealth-management', label: 'Wealth Management Archives' }
  ];

  protected formatScore(score: number): string {
    return score < 0.01 ? score.toExponential(2) : score.toFixed(4);
  }

  protected getActiveProfileDescription(): string {
    const profile = this.state.activeProfile();
    return PROFILE_PARAMS[profile]?.description ?? 'Custom recall parameters';
  }

  protected getActiveProfileParams() {
    const profile = this.state.activeProfile();
    return PROFILE_PARAMS[profile];
  }
}
