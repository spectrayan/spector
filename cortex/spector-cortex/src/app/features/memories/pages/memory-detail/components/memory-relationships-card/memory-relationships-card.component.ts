/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-cortex/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */

import { Component, ChangeDetectionStrategy, input, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  GraphNode,
  GraphEdge,
  MemoryGraphResponse,
} from '@core/services/memory-table.service';

/** Tier color mapping for visual badges. */
const TIER_COLORS: Record<string, string> = {
  WORKING: '#ffb74d',
  EPISODIC: '#66bb6a',
  SEMANTIC: '#42a5f5',
  PROCEDURAL: '#ab47bc',
};

const EDGE_TYPE_ICONS: Record<string, string> = {
  HEBBIAN: 'link',
  TEMPORAL: 'timeline',
  ENTITY: 'category',
};

@Component({
  selector: 'cortex-memory-relationships-card',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './memory-relationships-card.component.html',
  styleUrl: './memory-relationships-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MemoryRelationshipsCardComponent {
  readonly memoryId = input.required<string>();
  readonly graphData = input<MemoryGraphResponse | null>(null);
  readonly graphLoading = input<boolean>(false);
  readonly graphError = input<string | null>(null);

  /** Group graph edges by type for display. */
  readonly hebbianEdges = computed(() => this.edgesOfType('HEBBIAN'));
  readonly temporalEdges = computed(() => this.edgesOfType('TEMPORAL'));
  readonly entityEdges = computed(() => this.edgesOfType('ENTITY'));

  /** Pagination — 10 items per page for each edge type */
  private readonly PAGE_SIZE = 10;
  readonly entityPage = signal(0);
  readonly hebbianPage = signal(0);
  readonly temporalPage = signal(0);

  readonly paginatedEntityEdges = computed(() => {
    const all = this.entityEdges();
    const start = this.entityPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly entityTotalPages = computed(() => Math.ceil(this.entityEdges().length / this.PAGE_SIZE));

  readonly paginatedHebbianEdges = computed(() => {
    const all = this.hebbianEdges();
    const start = this.hebbianPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly hebbianTotalPages = computed(() => Math.ceil(this.hebbianEdges().length / this.PAGE_SIZE));

  readonly paginatedTemporalEdges = computed(() => {
    const all = this.temporalEdges();
    const start = this.temporalPage() * this.PAGE_SIZE;
    return all.slice(start, start + this.PAGE_SIZE);
  });
  readonly temporalTotalPages = computed(() => Math.ceil(this.temporalEdges().length / this.PAGE_SIZE));

  /** Lookup a graph node by ID. */
  graphNodeById(id: string): GraphNode | undefined {
    return this.graphData()?.nodes.find((n) => n.id === id);
  }

  /** Get tier color for a graph node. */
  nodeTierColor(node: GraphNode): string {
    return TIER_COLORS[node.tier] ?? '#9e9e9e';
  }

  /** Get edge type icon. */
  edgeTypeIcon(type: string): string {
    return EDGE_TYPE_ICONS[type] ?? 'link';
  }

  /** Get the neighbor ID from an edge (the one that isn't the focal memory). */
  neighborId(edge: GraphEdge): string {
    return edge.fromId === this.memoryId() ? edge.toId : edge.fromId;
  }

  private edgesOfType(type: string): GraphEdge[] {
    const data = this.graphData();
    if (!data) return [];
    return data.edges.filter((e) => e.type === type);
  }
}
