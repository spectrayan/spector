# Grafana k6 API Performance & Load Testing Suite

This directory contains the automated **Grafana k6** API performance, load, stress, spike, and multi-user isolation testing framework for **Spector Synapse Memory REST APIs** (`/api/v1/memory/*`).

---

## 🏗️ Architecture & Directory Layout

```
bench/k6/
├── config/
│   ├── environments.js         # Base URLs, default credentials, API keys, auth mode toggle
│   └── thresholds.js           # Standardized SLO/SLA targets (P95 < 20ms, Error rate < 1%)
├── data/
│   ├── sample-memories.json    # Pre-generated realistic memories (Episodic, Semantic, Procedural)
│   ├── sample-queries.json     # Cognitive search/recall queries with varying complexity
│   └── sample-users.json       # Seed user identities for multi-tenant tests
├── utils/
│   ├── auth.js                 # Authentication helpers (API Key, JWT token pooling, Session headers)
│   ├── metrics.js              # Custom k6 Trend, Counter, Rate metrics
│   └── generator.js            # Synthetic payload, TSID, and tag generators
├── scenarios/
│   ├── 01-smoke-test.js        # 1 VU verification across all 13 memory endpoints
│   ├── 02-load-test.js         # 50 VUs sustained production load test
│   ├── 03-stress-test.js       # Ramp to 300 VUs: Finds system capacity & breaking point
│   ├── 04-spike-test.js        # 0 -> 250 VUs in 10s: Tests Java 25 virtual thread elasticity
│   ├── 05-soak-test.js         # 30 VUs endurance test for off-heap slab stability
│   ├── 06-mixed-workload.js    # Realistic 60% Recall / 25% Remember / 10% Table / 5% Feedback
│   ├── 07-multi-user-isolation.js # Strict multi-user data isolation verification
│   └── 08-user-registry-lru-stress.js # High-cardinality multi-tenant cache churn & LRU eviction
├── run.ps1                     # PowerShell test runner
└── run.sh                      # Shell test runner
```

---

## ⚡ Prerequisites

Install **Grafana k6**:
- **Windows**: `winget install k6` or `choco install k6`
- **macOS**: `brew install k6`
- **Linux**: `sudo apt-get install k6`
- **Docker**: `docker run -i --net=host grafana/k6 run - <scenario.js`

---

## 🚀 Quickstart

### 1. Launch Spector Synapse
Start Synapse in test/local mode on port `7070`:
```bash
mvn spring-boot:run -pl synapse/spector-synapse -Dspring-boot.run.profiles=test
```

### 2. Run Smoke Test
Verify all endpoints respond with valid schemas and proper status codes:
```powershell
./bench/k6/run.ps1 -Scenario smoke
```
*(or via CLI directly)*:
```bash
k6 run bench/k6/scenarios/01-smoke-test.js
```

### 3. Run Production Mixed Workload
Simulates realistic multi-agent traffic (60% Recall, 25% Remember, 10% Table/Search, 5% Stats):
```powershell
./bench/k6/run.ps1 -Scenario mixed -VUs 50 -Duration 3m
```

### 4. Run Multi-User Isolation Verification
Asserts that User $A$ can never recall User $B$'s private memories across concurrent sessions:
```powershell
./bench/k6/run.ps1 -Scenario isolation -AuthEnabled
```

### 5. Run Virtual-Thread Spike Test
Validates that sudden 0 $\rightarrow$ 250 VU spikes scale smoothly on Java 25 virtual threads:
```powershell
./bench/k6/run.ps1 -Scenario spike
```

---

## 🎯 Target SLO / SLA Thresholds

| Metric | Target SLA | Description |
| :--- | :--- | :--- |
| `http_req_failed` | `< 1.0%` | Maximum allowable error rate under standard load |
| `memory_recall_duration` | `P95 < 30ms` | Fused vector + Hebbian + temporal cognitive recall latency |
| `memory_remember_duration` | `P95 < 15ms` | Asynchronous 202 Accepted cognitive ingest dispatch |
| `user_isolation_violations` | `0` | Strict zero-tolerance for cross-tenant memory leakage |
