import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface MemoryItem {
  id: string;
  text: string;
  tier: string;
  score: number;
  tags: string[];
  metadata: Record<string, string>;
  createdAt: string;
  lastRecalledAt: string | null;
  recallCount: number;
}

export interface MemoryStats {
  totalCount: number;
  tierDistribution: Record<string, number>;
  storageBytes: number;
  lastActivity: string;
}

@Injectable({ providedIn: 'root' })
export class MemoryService {
  private readonly baseUrl = `${environment.apiUrl}/api/${environment.apiVersion}/memory`;

  readonly memories = signal<MemoryItem[]>([]);
  readonly stats = signal<MemoryStats | null>(null);
  readonly loading = signal(false);

  constructor(private http: HttpClient) {}

  loadMemories(page = 0, pageSize = 20) {
    this.loading.set(true);
    this.http.get<{ items: MemoryItem[] }>(`${this.baseUrl}?page=${page}&pageSize=${pageSize}`).subscribe({
      next: res => {
        this.memories.set(res.items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  loadStats() {
    this.http.get<MemoryStats>(`${this.baseUrl}/stats`).subscribe({
      next: stats => this.stats.set(stats)
    });
  }

  storeMemory(text: string, tags: string[] = []) {
    return this.http.post<{ id: string }>(`${this.baseUrl}`, { text, tags });
  }

  deleteMemory(id: string) {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }

  searchMemories(query: string, topK = 10) {
    return this.http.post<any[]>(`${this.baseUrl}/search`, { query, topK });
  }

  recallMemories(query: string, topK = 10) {
    return this.http.post<any[]>(`${this.baseUrl}/recall`, { query, topK });
  }
}
