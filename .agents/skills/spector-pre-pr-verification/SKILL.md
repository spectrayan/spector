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

When running targeted `-Dtest=...` commands across `-pl` or multi-module reactors, always add `-Dsurefire.failIfNoSpecifiedTests=false` and a bounded timeout (`-Dsurefire.timeout=900`) so modules without matching tests do not abort the build and deadlocks fail fast.

| Touched Area | Mandatory Local Commands / Guardrail Tests |
| :--- | :--- |
| **`nucleus/spector-config`** | `mvn test -pl nucleus/spector-config -Dtest="PostBootstrapSyspropBanTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false` |
| **`memory/spector-kernel`** | `mvn install -pl memory/spector-kernel -Dtest="KernelNamingRulesTest,KernelSealRulesTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false` (run full `mvn install -pl memory/spector-kernel` to verify JaCoCo coverage rules) |
| **`memory/spector-memory`** | `mvn test -pl memory/spector-memory -Dtest="KernelSealBoundaryTest,DuplicateConfigBanTest,EngineFencingIntegrationTest,LazyEncryptedBundlePagesTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false` |
| **`cluster/spector-cluster`** | `mvn test -pl cluster/spector-cluster -Dtest="JdbcControlStoreTest,ControlStoreContractTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false` (verifies H2 default, H2 `MODE=PostgreSQL`, and HSQLDB dialects) |
| **`synapse/spector-mcp`** | `mvn test -pl synapse/spector-mcp -Dtest="ModuleBoundaryTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false` |
| **`synapse/spector-synapse` (controllers, security, auth, credentials, namespaces, fencing, rate limits)** | `mvn test -pl synapse/spector-synapse -Psynapse -Dtest="AdminAuditControllerTest,ControllerSecurityMatrixTest,SynapseMemoryApiE2ETest,NamespaceBudgetAndColdOpenConcurrencyTest,PartitionedOwnerFencingTest,AdmissionControlLoadSheddingTest,<YourNewTests>" -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.timeout=900` |
| **Flyway migrations (`db/migration/*.sql`)** | Include `TenantIdDefectVerificationTest` alongside controller tests |
| **`nucleus/spector-events` or Synapse DTOs** | Also run Cortex UI build & tests (catches TypeScript contract breaks before CI): `cd cortex/spector-cortex && npm run build && npx ng test --watch=false` |
| **`cortex/spector-cortex`** | `cd cortex/spector-cortex && npm run build && npx ng test --watch=false` (CI only runs `ng build`) |
| **Full Synapse CI parity** | `mvn -B clean verify -Psynapse --no-transfer-progress -Dsurefire.useSystemClassLoader=false -Dsurefire.timeout=900` |

## 3. CodeQL Pre-Flight Checklist
Before pushing Java changes, double-check recurring Spector CodeQL findings:
- **AES-GCM IVs** (`java/static-initialization-vector`): Call `SECURE_RANDOM.nextBytes(nonce)` immediately prior to `new GCMParameterSpec(...)`.
- **Error Exposure** (`java/error-message-exposure`, CWE-209): Never serialize `e.getMessage()` into HTTP responses.
- **Numeric Parsing**: Wrap `Integer.parseInt` / `Long.parseLong` in `try/catch (NumberFormatException)` or use `com.spectrayan.spector.commons.ParseUtils`.
- **Dead Type Checks**: Do not follow `instanceof EngramRegion` with `else if (x instanceof EpisodicMemory)` (`EpisodicMemory` implements `EngramRegion`).
- **Narrow vs. Wide Loop Comparisons** (`java/comparison-with-wider-type`): Never compare `long` counters against `int` bounds in `while` / `for` loop conditions (e.g., `while (currentAccountHot >= maxHot)`). Widen the `int` bound to `long` so both operands share the same width, converting back via `Math.toIntExact` only after the loop when bounded.

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

## 6. Windows Host Verification Gotchas
When running Spector Maven gates on a Windows host (Linux CI remains authoritative):
1. **Confirm OpenJDK 25 in the active shell**: On multi-JDK hosts (e.g., JDK 27 also installed), verify both `java -version` and `mvn -version` report Java 25 before building.
2. **Enforce LF line endings**: Spector has no `.gitattributes`. With `core.autocrlf=true`, checked-out template resources get `CRLF` and can fail Linux-aligned assertions; set repo-local `git config core.autocrlf false` and ensure checked-out text resources use `LF`.
3. **Exclude the two Windows-incompatible `spector-synapse` tests locally**:
   - `ProcessKillOwnerRecoveryTest` fails under `-Dsurefire.useSystemClassLoader=false` because passing the expanded Maven classpath via `-cp` exceeds Windows' 32 KB `CreateProcess` command-line limit (it runs standalone with Surefire's default manifest booter and passes on Linux CI).
   - `AgentTokenServiceTest` calls `Files.getPosixFilePermissions(...)` unconditionally, throwing `UnsupportedOperationException` on NTFS (passes on Linux CI).
   - In PowerShell, quote `-D` flags containing `.`, `,`, or `!`:
     ```powershell
     mvn -B test -pl synapse/spector-synapse --no-transfer-progress `
       "-Dsurefire.useSystemClassLoader=false" `
       "-Dtest=!ProcessKillOwnerRecoveryTest,!AgentTokenServiceTest,!*IT" `
       "-Dsurefire.failIfNoSpecifiedTests=false" `
       "-Dsurefire.timeout=900"
     ```
4. **Isolate forked Surefire temp directories**: Passing `-Djava.io.tmpdir=<dir>` only configures the Maven parent JVM; forked Surefire test JVMs inherit `TEMP` and `TMP` from the shell. Set `$env:TEMP` and `$env:TMP` alongside `-Djava.io.tmpdir` (and `-Dmaven.repo.local=<dir>` when relocating caches) so `@TempDir` and test bundles stay in the intended directory.
