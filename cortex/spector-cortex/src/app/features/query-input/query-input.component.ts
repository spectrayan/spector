import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { CortexStateService } from '../../core/services/cortex-state.service';
import { MemoryTableService } from '../../core/services/memory-table.service';
import { LoggerService } from '../../core/services/logger.service';

@Component({
  selector: 'cortex-query-input',
  imports: [FormsModule, MatIconModule],
  templateUrl: './query-input.component.html',
  styleUrl: './query-input.component.scss',
})
export class QueryInputComponent {
  protected readonly state = inject(CortexStateService);
  private readonly memoryService = inject(MemoryTableService);
  private readonly log = inject(LoggerService);
  protected queryText = '';

  protected submitQuery(): void {
    if (!this.queryText.trim()) return;
    const query = this.queryText.trim();
    this.state.isQueryRunning.set(true);
    this.state.lastQueryText.set(query);

    this.memoryService.recall(
      query,
      this.state.playgroundTopK(),
      this.state.activeProfile(),
      this.state.playgroundValence()
    ).subscribe({
      next: (response) => {
        this.state.recallResults.set(response.results);
        this.state.recallQueryTimeMs.set(response.queryTimeMs);
        this.state.recallTotalMemories.set(response.totalMemories);
        this.state.recallProfile.set(response.profile);
        this.state.isQueryRunning.set(false);

        this.log.info('QueryInput', `Recall complete: ${response.results.length} results in ${response.queryTimeMs}ms`);
        // Note: Real cortex.query.trace SSE event is emitted by the backend
        // and received by EventStreamService — no synthetic push needed
      },
      error: (err) => {
        this.log.error('QueryInput', 'Recall failed:', err);
        this.state.isQueryRunning.set(false);
      },
    });

    this.queryText = '';
  }
}

