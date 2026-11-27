---
name: spector-pre-pr-verification
description: >-
  Pre-PR local verification, architecture-gate test matrix, local Docker smoke testing,
  and CI failure diagnosis workflow for the Spector repository. Use before committing,
  pushing, or opening a PR in spectrayan/spector, or whenever investigating a Spector CI failure.
---

# Spector Pre-PR Verification & CI Diagnosis

Run the checks matching the areas your diff touches before pushing a PR in `spectrayan/spector`.

## 1. Sync & Hygiene Checks
1. Verify you branched from the latest `origin/main` (`git fetch origin`).
2. Verify zero `synchronized` keywords in modified Java files:
   ```bash
   git diff origin/main -- '*.java' | grep -E '^\+.*\bsynchronized\b' || true
   ```
3. Verify Apache 2.0 license headers across all modules:
   ```bash
   mvn license:check
   ```

## 2. Module-Targeted Build & Architecture Gate Matrix

When modifying upstream modules (`nucleus/*`, `memory/spector-kernel`, `memory/spector-memory`), run `mvn package -DskipTests -q` (or `mvn install -DskipTests -q`) first so downstream modules resolve JPMS `Automatic-Module-Name` JAR manifests.

| Touched Area | Mandatory Local Commands / Guardrail Tests |
| :--- | :--- |
| **`memory/spector-memory` or `memory/spector-kernel`** | `mvn test -pl memory/spector-memory -Dtest="KernelSealBoundaryTest,DuplicateConfigBanTest,<YourNewTests>"` |
| **`synapse/spector-synapse` (controllers, security, auth, credentials)** | `mvn test -pl synapse/spector-synapse -Psynapse -Dtest="AdminAuditControllerTest,ControllerSecurityMatrixTest,SynapseMemoryApiE2ETest,<YourNewTests>"` |
| **Flyway migrations (`db/migration/*.sql`)** | Include `TenantIdDefectVerificationTest` alongside controller tests |
| **`nucleus/spector-events` or Synapse DTOs** | Also run Cortex UI build & tests (catches TypeScript contract breaks before CI): `cd cortex/spector-cortex && npm run build && npx ng test --watch=false` |
| **`cortex/spector-cortex`** | `cd cortex/spector-cortex && npm run build && npx ng test --watch=false` (CI only runs `ng build`) |
| **Full Synapse CI parity** | `mvn -B clean verify -Psynapse --no-transfer-progress -Dsurefire.useSystemClassLoader=false` |

## 3. CodeQL Pre-Flight Checklist
Before pushing Java changes, double-check recurring Spector CodeQL findings:
- **AES-GCM IVs** (`java/static-initialization-vector`): Call `SECURE_RANDOM.nextBytes(nonce)` immediately prior to `new GCMParameterSpec(...)`.
- **Error Exposure** (`java/error-message-exposure`, CWE-209): Never serialize `e.getMessage()` into HTTP responses.
- **Numeric Parsing**: Wrap `Integer.parseInt` / `Long.parseLong` in `try/catch (NumberFormatException)` or use `com.spectrayan.spector.commons.ParseUtils`.
- **Dead Type Checks**: Do not follow `instanceof EngramRegion` with `else if (x instanceof EpisodicMemory)` (`EpisodicMemory` implements `EngramRegion`).

## 4. Local Docker Smoke Test (When Touching Auth, Entrypoint, Nginx, or UI)
Unit tests run with `@ActiveProfiles("test")` and will NOT catch production startup crashes (e.g., missing `SPECTOR_MASTER_ENCRYPTION_KEY` causing Nginx `502 Bad Gateway` on `/api/v1/*`):
1. Rebuild and deploy locally:
   ```bash
   ./deploy/docker/deploy.sh
   ```
2. Verify container health and endpoints on `http://localhost:7700`:
   - `GET http://localhost:7700/api/v1/auth/config` returns `200`.
   - `GET http://localhost:7700/login` renders without `502` or console errors; verify login / signup flow.
   - Inspect `docker logs spector --tail 100` for startup errors or crash loops.

## 5. Diagnosing CI Failures (Never Blindly Rerun Perf Gates)
- Inspect the full failed job log (`gh run view <run-id> --log-failed`) **before** concluding a failure is "transient runner jitter".
- For latency/benchmark failures (e.g., `EncryptedRecallPerformanceGateTest`), inspect concurrent background threads, decryption counts, and `WARN` log volume in the job log before triggering `gh run rerun --failed`.
- Remember CI runs both `x86_64` and `aarch64` legs — verify SIMD lane assumptions hold on both architectures.
