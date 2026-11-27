---
name: spector-security-remediation
description: >-
  End-to-end remediation runbook for GitHub Dependabot, CodeQL, Trivy container scanning,
  and security-alert tracking issues in spectrayan/spector. Use whenever asked to fix
  Dependabot alerts, code scanning alerts, CVEs, or security-alert issues in Spector.
---

# Spector Security Alert Remediation Runbook

## 1. Fetch Live Alerts & Sync `main`
1. Sync local `main`: `git fetch origin && git checkout main && git pull origin main`.
2. Query live open alerts via `gh api`:
   ```bash
   gh api repos/spectrayan/spector/dependabot/alerts --paginate -q '.[] | select(.state=="open") | [.number, .dependency.manifest_path, .dependency.package.name, .security_advisory.severity, .security_advisory.ghsa_id]'
   gh api repos/spectrayan/spector/code-scanning/alerts --paginate -q '.[] | select(.state=="open") | [.number, .tool.name, .rule.id, .most_recent_instance.location.path]'
   ```
3. Check if Dependabot already opened a green PR for any alert (e.g., Maven `pom.xml` bumps) before duplicating work.

## 2. npm / Lockfile Remediation (All Spector Manifests)
Spector contains multiple npm projects:
- `cortex/spector-cortex`
- `plugins/openclaw`
- `sdks/typescript/spector-client`
- `deploy/npm/spector`

Procedure:
1. Inspect `"overrides"` in `package.json` — stale overrides frequently pin transitive dependencies **down** to a vulnerable version even when the parent package is newer (e.g., `vitest ^5.0.1` pinned down by `"@vitest/mocker": "^4.1.11"`).
2. Update direct ranges / `"overrides"`, regenerate `package-lock.json`, and run `npm audit` across all affected manifests until every manifest reports **`found 0 vulnerabilities`**.
3. Run build & tests in every modified package:
   - `cortex/spector-cortex`: `npm ci && npm run build && npx ng test --watch=false`
   - `plugins/openclaw`: `npm ci && npm run lint && npm run build && npm test`
   - `sdks/typescript/spector-client`: `npm ci && npm run build && npm test`

## 3. Trivy Container Security Scan (`container-docker`)
- When Trivy flags OS packages pre-installed in base images (`curl`, `libcurl4t64`, `libpng16-16t64`, `libfreetype6`), check all three Dockerfiles:
  - `deploy/docker/Dockerfile`
  - `deploy/docker/Dockerfile.synapse-local`
  - `synapse/spector-synapse/Dockerfile`
- Ensure `apt-get dist-upgrade -y --no-install-recommends` and explicit `--only-upgrade` package lists run **after** `COPY` (or invalidate BuildKit cache) so stale Ubuntu layers are never reused across builds.

## 4. PR Description & Alert Tagging Rule (CRITICAL)
- **NEVER** reference security alert numbers with bare `#<N>` or `Closes #<N>` — GitHub interprets `#<N>` as an unrelated **GitHub Issue**, not a security alert!
- Always link canonical security alert URLs in the PR body:
  - `https://github.com/spectrayan/spector/security/dependabot/<N>`
  - `https://github.com/spectrayan/spector/security/code-scanning/<N>`
- Commit and raise the PR using the machine's default git/`gh` identity (`sbharatjoshi`) with `git commit -s`.

## 5. Post-Merge Verification & Cleanup (Mandatory)
1. **Verify Dependabot Alerts Actually Closed**:
   - Check open Dependabot alerts after merge. If GitHub's background worker lags on cached old versions:
     - Verify removal via Dependency Graph Compare / SBOM:
       ```bash
       gh api repos/spectrayan/spector/dependency-graph/compare/<base-sha>...<merge-sha>
       gh api repos/spectrayan/spector/dependency-graph/sbom
       ```
     - Once SBOM confirms the vulnerable version is absent from `main`, close/dismiss stale cached alerts via `gh api` referencing the merged PR and commit SHA.
2. **Audit Orphaned `security-alert` Tracking Issues**:
   - `.github/workflows/codeql-to-issue-sync.yml` creates `label:security-alert` issues automatically on Mondays, but does **not** auto-close them when Code Scanning alerts close.
   - After Code Scanning / Container Security Scan goes green on `main`, list open `label:security-alert` issues (`gh issue list --repo spectrayan/spector --label security-alert --state open`), verify which underlying alerts are now closed, and propose closing the resolved tracking issues.
