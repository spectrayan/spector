#!/usr/bin/env bash
# Copyright 2026 Spectrayan
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(cd "${SCRIPT_DIR}/../spector" && pwd)"

echo "=== Spector Helm Manifest Assertion Suite ==="
echo "Chart directory: ${CHART_DIR}"

# 1. Helm Lint
echo "--- Step 1: Helm Lint ---"
helm lint "${CHART_DIR}"
echo "✓ Helm lint passed cleanly"

# 2. Render default (split-role) manifests
echo "--- Step 2: Render Default (Split-Role) Manifests ---"
TMP_SPLIT="$(mktemp)"
trap 'rm -f "${TMP_SPLIT}"' EXIT
helm template test-split "${CHART_DIR}" > "${TMP_SPLIT}"
echo "✓ Rendered split-role template ($(wc -l < "${TMP_SPLIT}") lines)"

# 3. Guard: requests.memory == limits.memory on owner pod (Req T3, R2.4, Task 0.3)
echo "--- Step 3: Guard requests.memory == limits.memory on Owner ---"
python3 -c '
import sys, re

content = open(sys.argv[1]).read()
docs = content.split("---")

owner_found = False
for doc in docs:
    if "kind: StatefulSet" in doc and "name: test-split-spector-owner" in doc:
        owner_found = True
        req_match = re.search(r"requests:.*?\n\s+memory:\s*([^\n]+)", doc, re.DOTALL)
        lim_match = re.search(r"limits:.*?\n\s+memory:\s*([^\n]+)", doc, re.DOTALL)
        assert req_match, "Owner requests.memory not found!"
        assert lim_match, "Owner limits.memory not found!"
        req_mem = req_match.group(1).strip().strip("\"'\''")
        lim_mem = lim_match.group(1).strip().strip("\"'\''")
        assert req_mem == lim_mem, f"Owner memory mismatch: requests={req_mem} != limits={lim_mem}"
        print(f"✓ Owner memory request and limit match: {req_mem}")
        break

assert owner_found, "Owner StatefulSet (test-split-spector-owner) not found in split render!"
' "${TMP_SPLIT}"

# 4. Guard: owner podAntiAffinity is required, not preferred (Req T2, R2.3, Task 0.4)
echo "--- Step 4: Guard Owner podAntiAffinity is Required ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

for doc in docs:
    if "kind: StatefulSet" in doc and "name: test-split-spector-owner" in doc:
        assert "requiredDuringSchedulingIgnoredDuringExecution" in doc, "Owner podAntiAffinity is not required!"
        assert "kubernetes.io/hostname" in doc, "Owner podAntiAffinity topologyKey kubernetes.io/hostname missing!"
        print("✓ Owner podAntiAffinity is required on kubernetes.io/hostname")
        break
' "${TMP_SPLIT}"

# 5. Guard: no HPA targets the owner StatefulSet (Req T6, R1.6, Task 0.5)
echo "--- Step 5: Guard No HPA Targets Owner StatefulSet ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

for doc in docs:
    if "kind: HorizontalPodAutoscaler" in doc:
        assert "test-split-spector-owner" not in doc, "CRITICAL: Found HPA targeting owner StatefulSet! Violates Invariant T6."
print("✓ Verified zero HPAs target owner StatefulSet")
' "${TMP_SPLIT}"

# 6. Guard: single-role mode renders legacy set name (Req R7.7, T8, B1, Task 1.5)
echo "--- Step 6: Verify Single-Role Backward Compatibility Mode ---"
TMP_SINGLE="$(mktemp)"
trap 'rm -f "${TMP_SPLIT}" "${TMP_SINGLE}"' EXIT
helm template test-single "${CHART_DIR}" --set topology.mode=single-role > "${TMP_SINGLE}"

python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

single_found = False
for doc in docs:
    if "kind: StatefulSet" in doc:
        assert "test-single-spector-owner" not in doc, "Single-role mode rendered -owner suffix!"
        assert "test-single-spector-replica" not in doc, "Single-role mode rendered -replica set!"
        assert "name: test-single-spector" in doc or "name: test-single-spector-node" in doc, f"Unexpected single-role set name"
        single_found = True
        print("✓ Single-role mode correctly preserves legacy set name without -owner suffix")
        break

assert single_found, "StatefulSet not found in single-role render!"
' "${TMP_SINGLE}"

# 7. Guard: Headless Services for Owner and Replica (Req R1.5, Task 1.6)
echo "--- Step 7: Verify Headless Services ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

owner_headless = False
replica_headless = False
for doc in docs:
    if "kind: Service" in doc and "clusterIP: None" in doc:
        if "test-split-spector-owner-headless" in doc or "test-split-spector-owner" in doc:
            owner_headless = True
        if "test-split-spector-replica-headless" in doc or "test-split-spector-replica" in doc:
            replica_headless = True

assert owner_headless, "Owner headless service missing!"
assert replica_headless, "Replica headless service missing!"
print("✓ Owner and Replica headless services confirmed")
' "${TMP_SPLIT}"

# 8. Guard: Port 9090 is not exposed on public Ingress or Service (Req R5.3, T4, Task 3.4, 3.5)
echo "--- Step 8: Guard Replication Port 9090 Isolation ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

for doc in docs:
    if "kind: Ingress" in doc:
        assert "9090" not in doc, "CRITICAL: Port 9090 found in Ingress!"
    if "kind: Service" in doc and ("type: LoadBalancer" in doc or "type: NodePort" in doc):
        assert "9090" not in doc, "CRITICAL: Port 9090 found in LoadBalancer/NodePort service!"
print("✓ Port 9090 is strictly internal and never publicly exposed")
' "${TMP_SPLIT}"

# 9. Guard: Multi-Owner Scale-out (Req R1.6, Task 1.9, 1.10)
echo "--- Step 9: Multi-Owner Scale-out Render ---"
TMP_MULTI="$(mktemp)"
trap 'rm -f "${TMP_SPLIT}" "${TMP_SINGLE}" "${TMP_MULTI}"' EXIT
helm template test-multi "${CHART_DIR}" --set owners.replicas=5 > "${TMP_MULTI}"

python3 -c '
import sys, re

content = open(sys.argv[1]).read()
docs = content.split("---")

for doc in docs:
    if "kind: StatefulSet" in doc and "name: test-multi-spector-owner" in doc:
        rep_match = re.search(r"replicas:\s*(\d+)", doc)
        assert rep_match and rep_match.group(1) == "5", f"Expected 5 owner replicas, got {rep_match.group(1) if rep_match else None}"
        print("✓ Scaled owner replicas to 5 cleanly")
        break
' "${TMP_MULTI}"

# 10. Guard: Coordinator Lease and Least-Privilege RBAC (Req R4.2, R4.4, Task 4.1, 4.2, 4.3)
echo "--- Step 10: Verify Coordinator Lease and Least-Privilege RBAC ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

lease_found = False
role_found = False
for doc in docs:
    if "kind: Lease" in doc and "coordination.k8s.io" in doc:
        assert "spector-coordinator-cell-1" in doc, "Lease name does not match expected cell ID!"
        lease_found = True
    if "kind: Role" in doc and "kind: RoleBinding" not in doc and "test-split-spector-coordinator-role" in doc:
        role_found = True
        assert "*" not in doc, "CRITICAL: Wildcard * found in RBAC Role rules!"
        assert "secrets" not in doc, "CRITICAL: Secrets resource found in RBAC Role rules!"
        assert "leases" in doc and "configmaps" in doc, "Role missing leases or configmaps resources!"

assert lease_found, "Coordinator Lease missing!"
assert role_found, "Coordinator Role missing!"
print("✓ Coordinator Lease and least-privilege RBAC role verified")
' "${TMP_SPLIT}"

# 11. Guard: NetworkPolicies Default-Deny & Whitelist (Req R5.1, R5.2, R5.6, Task 3.1, 3.2, 3.3)
echo "--- Step 11: Verify NetworkPolicies ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

default_deny = False
gateway_ingress = False
repl_ingress = False

for doc in docs:
    if "kind: NetworkPolicy" in doc:
        if "test-split-spector-default-deny" in doc:
            default_deny = True
            assert "podSelector: {}" in doc or "podSelector:\n" in doc
        if "test-split-spector-allow-gateway-ingress" in doc:
            gateway_ingress = True
            assert "port: 7070" in doc
        if "test-split-spector-allow-replication" in doc:
            repl_ingress = True
            assert "port: 9090" in doc

assert default_deny, "Default-deny NetworkPolicy missing!"
assert gateway_ingress, "Gateway ingress NetworkPolicy missing!"
assert repl_ingress, "Replication NetworkPolicy missing!"
print("✓ NetworkPolicies: default-deny, gateway ingress (7070), and replication (9090) confirmed")
' "${TMP_SPLIT}"

# 12. Guard: PodDisruptionBudget for Owner (Req R2.8, Task 2.7)
echo "--- Step 12: Verify Owner PodDisruptionBudget ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

pdb_found = False
for doc in docs:
    if "kind: PodDisruptionBudget" in doc and "test-split-spector-owner-pdb" in doc:
        pdb_found = True
        assert "maxUnavailable: 1" in doc, "Owner PDB maxUnavailable not set to 1!"
        print("✓ Owner PodDisruptionBudget verified with maxUnavailable=1")
        break

assert pdb_found, "Owner PodDisruptionBudget missing!"
' "${TMP_SPLIT}"

# 13. Guard: ServiceMonitor Metrics Scraping (Req R8.1, Task 4.6)
echo "--- Step 13: Verify ServiceMonitor ---"
python3 -c '
import sys

content = open(sys.argv[1]).read()
docs = content.split("---")

sm_found = False
for doc in docs:
    if "kind: ServiceMonitor" in doc:
        sm_found = True
        assert "path: /actuator/prometheus" in doc, "ServiceMonitor scrape path missing!"
        print("✓ ServiceMonitor configured for /actuator/prometheus")
        break

assert sm_found, "ServiceMonitor missing!"
' "${TMP_SPLIT}"

# 14. Guard: Two Cells Coexistence (Req R7.5, Task 1.8)
echo "--- Step 14: Verify Two-Cell Coexistence ---"
TMP_CELL1="$(mktemp)"
TMP_CELL2="$(mktemp)"
trap 'rm -f "${TMP_SPLIT}" "${TMP_SINGLE}" "${TMP_MULTI}" "${TMP_CELL1}" "${TMP_CELL2}"' EXIT
helm template cell-us-east "${CHART_DIR}" --set cell.id=us-east-1 > "${TMP_CELL1}"
helm template cell-us-west "${CHART_DIR}" --set cell.id=us-west-1 > "${TMP_CELL2}"

python3 -c '
import sys

c1 = open(sys.argv[1]).read()
c2 = open(sys.argv[2]).read()

assert "spector-coordinator-us-east-1" in c1, "Cell 1 coordinator lease naming mismatch!"
assert "spector-coordinator-us-west-1" in c2, "Cell 2 coordinator lease naming mismatch!"
assert "spector-ring-us-east-1" in c1, "Cell 1 ring configmap naming mismatch!"
assert "spector-ring-us-west-1" in c2, "Cell 2 ring configmap naming mismatch!"
print("✓ Two cells coexist without resource name collisions")
' "${TMP_CELL1}" "${TMP_CELL2}"

echo "=== ALL 14 MANIFEST TESTS PASSED SUCCESSFULLY ==="
