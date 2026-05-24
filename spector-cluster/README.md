# spector-cluster 🌐

> **Distributed gRPC search coordinator, shard nodes, and consistent hashing partitions.**

`spector-cluster` scales Spector Search horizontally across multiple nodes. It implements a coordinator/shard architecture using high-performance gRPC network transports and consistent hash partition mapping to support billion-scale vector indexes.

---

## 🏗️ Core Architecture & Roles

1. **gRPC Coordinator (`ClusterCoordinator`):** Receives incoming query requests, splits searches across multiple physical shard nodes, and performs parallel Reciprocal Rank Fusion (RRF) on the results.
2. **Shard Nodes (`SearchNode`):** Self-contained index workers that run local, low-latency SIMD/HNSW search lookups on their partitioned dataset before returning top candidates.
3. **Consistent Hashing (`ConsistentHashRing`):** Maps document keys to appropriate shard partition segments. Handles node additions and node failures dynamically with minimal data relocation.

---

## 🚀 Operations

### Coordinator Fan-Out & gRPC Merges
```
                  ┌──────────────────────┐
                  │  ClusterCoordinator  │  (gRPC query)
                  └──────────┬───────────┘
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
   ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
   │   SearchNode   │ │   SearchNode   │ │   SearchNode   │  (Index Shards)
   │   (Partition)  │ │   (Partition)  │ │   (Partition)  │
   └────────────────┘ └────────────────┘ └────────────────┘
```
All coordinator communication uses async gRPC callbacks, completely unblocking the network thread.
