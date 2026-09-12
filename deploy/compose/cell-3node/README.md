# 3-Node Cell HA Compose Harness (Phase 1)

This harness runs a local 3-node Spector Cell cluster demonstrating **ADR-0034 Phase 1: Cell Ownership Ring** with zero Redis or external coordinator dependencies.

---

## Topology

- **Cell ID**: `cell-us-east-1`
- **Nodes**:
  - `spector-owner-1` (port `7071`)
  - `spector-owner-2` (port `7072`)
  - `spector-owner-3` (port `7073`)
- **Membership**: Static list mounted from `members.txt`.
- **Ring Algorithm**: Ketama consistent hash ring with 160 virtual nodes per member, big-endian truncated 64-bit SHA-256 digest.

---

## Quick Start

```bash
# 1. Build and start the 3 owner nodes
docker compose -f deploy/compose/cell-3node/docker-compose.yml up -d

# 2. View logs across the cluster
docker compose -f deploy/compose/cell-3node/docker-compose.yml logs -f

# 3. Check health on all 3 nodes
curl -s -H "X-API-Key: spector-dev-key" http://localhost:7071/api/v1/system/status
curl -s -H "X-API-Key: spector-dev-key" http://localhost:7072/api/v1/system/status
curl -s -H "X-API-Key: spector-dev-key" http://localhost:7073/api/v1/system/status

# 4. Tear down the cluster
docker compose -f deploy/compose/cell-3node/docker-compose.yml down -v
```

---

## Verifying Invariant J1 & J3 (Single Writer Ownership)

When a request targeting a namespace is sent to a node that is **not** the ring owner, the node returns:
- HTTP Status: `421 Misdirected Request`
- Header / Body: Names the authoritative `ownerId` and ring `epoch`
- Behavior: Zero file access or memory mapping on the non-owner (`runtime.attach` never invoked).
