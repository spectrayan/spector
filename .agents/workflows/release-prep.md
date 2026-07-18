---
description: End-to-end release process for Spector Core — pre-flight checks, version bump, publishing, and tagging.
---

# Workflow: Release Preparation

End-to-end process for preparing a Spector Core release.

## Version Scheme

```
<major>.<minor>.<patch>[-<qualifier>]

Qualifiers (pre-release):
  1.0.0-M1      Milestone (early preview, breaking changes expected)
  1.0.0-RC1     Release Candidate (feature-complete, bug fixes only)
  1.0.0         GA (General Availability)

Post-GA:
  1.0.1         Patch (bugfix, no new features)
  1.1.0         Minor (new features, backward compatible)
  2.0.0         Major (breaking changes)

Development:
  1.1.0-SNAPSHOT   Next development version (never released)
```

## Trigger

When preparing for a tagged release, or the user invokes `/release-prep`.

---

## Pre-Flight

### Step 1: Verify Clean Working Tree

```powershell
git status --porcelain
# Must be clean or only expected changes
```

### Step 2: Decide Release Version

```powershell
git log --oneline (git describe --tags --abbrev=0)..HEAD | Select-Object -First 20
```

| Changes since last tag | Version bump |
|------------------------|-------------|
| Breaking API changes | Major (2.0.0) |
| New features | Minor (1.1.0) |
| Bug fixes only | Patch (1.0.1) |

### Step 3: Full Build

```powershell
mvn -B clean install --no-transfer-progress
```

All tests must pass. Zero tolerance for failures.

### Step 4: SNAPSHOT Dependency Audit

```powershell
mvn -B dependency:tree --no-transfer-progress 2>&1 |
  Select-String "SNAPSHOT" | Select-String -NotMatch "com.spectrayan"
```

If any external SNAPSHOT deps are found, the release MUST NOT proceed.

### Step 5: Circular Dependency Check

```powershell
Select-String -Path "spector-engine/src/main/java/**/*.java" -Pattern "import com.spectrayan.spector.memory" -Recurse
Select-String -Path "spector-memory/src/main/java/**/*.java" -Pattern "import com.spectrayan.spector.engine" -Recurse
```

### Step 6: License Header Check

```powershell
mvn -B license:check --no-transfer-progress
```

---

## Version Bump & Changelog

### Step 7: Set Release Version

The `<revision>` property in the root `pom.xml` is the single source of truth.

```powershell
$RELEASE_VERSION = "1.1.0"

(Get-Content pom.xml) -replace '<revision>[^<]+</revision>', "<revision>$RELEASE_VERSION</revision>" |
  Set-Content pom.xml
```

Verify:
```powershell
mvn -B help:evaluate -Dexpression=project.version -q -DforceStdout
# Should print: 1.1.0
```

### Step 8: Generate Changelog

```powershell
git log --oneline (git describe --tags --abbrev=0)..HEAD
```

Group by conventional commit type and prepend to `CHANGELOG.md`:
```markdown
## [1.1.0] - 2026-06-17
### Added
- ...
### Fixed
- ...
```

---

## Publish

### Step 9: Release Build

```powershell
# GitHub Packages
mvn -B clean deploy --no-transfer-progress `
  -Prelease `
  -Dmaven.deploy.skip=false `
  -DaltDeploymentRepository="github::https://maven.pkg.github.com/spectrayan/spector"
```

For Maven Central:
```powershell
mvn -B clean deploy --no-transfer-progress `
  -Prelease `
  -Dmaven.deploy.skip=false
# central-publishing-maven-plugin handles Sonatype staging
```

### Step 10: Verify Published Artifacts

```powershell
Select-String -Path "spector-commons/.flattened-pom.xml" -Pattern "version"
# Should show the release version, NOT ${revision}
```

---

## Tag & Next Version

### Step 11: Commit, Tag, and Bump

```powershell
$NEXT_VERSION = "1.2.0-SNAPSHOT"

# Commit release
git add -A
git commit -m "chore: release $RELEASE_VERSION"
git tag -a "v$RELEASE_VERSION" -m "Release $RELEASE_VERSION"

# Bump to next SNAPSHOT
(Get-Content pom.xml) -replace '<revision>[^<]+</revision>', "<revision>$NEXT_VERSION</revision>" |
  Set-Content pom.xml
git add pom.xml
git commit -m "chore: prepare $NEXT_VERSION development"

# Push
git push origin main --tags
```

### Step 12: Create GitHub Release

```powershell
gh release create "v$RELEASE_VERSION" `
  --title "Spector $RELEASE_VERSION" `
  --notes-file CHANGELOG.md
```

---

## Rollback

```powershell
git tag -d "v$RELEASE_VERSION"
git push origin --delete "v$RELEASE_VERSION"
git revert HEAD~1   # revert SNAPSHOT bump
git revert HEAD~1   # revert release commit
git push origin main
```

GitHub Packages does NOT support deletion of published versions. Publish a patch instead.
