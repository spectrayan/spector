#Requires -Version 5.1
<#
.SYNOPSIS
    Spector - Build and Publish Docker Image to Docker Hub

.DESCRIPTION
    Builds the Spector Docker image (backend + frontend unified) and
    pushes it to Docker Hub as spectrayan/spector.

    Pipeline:
      1. Maven builds the Spring Boot fat JAR (on host)
      2. Angular builds the Cortex dashboard (on host)
      3. Docker packages both into a single image (Nginx + JRE)
      4. Push to Docker Hub with version + latest tags

.PARAMETER Command
    The action to perform:
      build   - Build the Docker image only
      push    - Push existing image to Docker Hub
      publish - Build + push (default)
      login   - Docker Hub login
      info    - Show image details

.PARAMETER Version
    Release version (e.g., "0.1.0-alpha"). Defaults to value from pom.xml.

.EXAMPLE
    .\publish.ps1                          # Build + push with pom.xml version
    .\publish.ps1 -Version 0.1.0-alpha     # Build + push with explicit version
    .\publish.ps1 build                    # Build only
    .\publish.ps1 push                     # Push only
    .\publish.ps1 login                    # Docker Hub login
    .\publish.ps1 info                     # Show image info
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("build", "push", "login", "info", "publish", "")]
    [string]$Command = "publish",

    [Parameter()]
    [string]$Version = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

# -- Configuration -------------------------------------------------
$DockerHubOrg  = "spectrayan"
$ImageName     = "spector"
$FullImage     = "$DockerHubOrg/$ImageName"
$Dockerfile    = "deploy/docker/Dockerfile"

# -- Navigate to project root -------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "../..")
Push-Location $ProjectRoot

try {

# -- Resolve version from pom.xml if not provided -----------------
if (-not $Version) {
    $pomContent = Get-Content "pom.xml" -Raw
    if ($pomContent -match '<revision>([^<]+)</revision>') {
        $Version = $Matches[1]
    } else {
        Write-Host "[Spector] Could not extract version from pom.xml" -ForegroundColor Red
        exit 1
    }
}

# -- Helper Functions ----------------------------------------------

function Write-Log   { param([string]$Msg) Write-Host "[Spector] $Msg" -ForegroundColor Cyan }
function Write-Ok    { param([string]$Msg) Write-Host "[Spector] $Msg" -ForegroundColor Green }
function Write-Warn  { param([string]$Msg) Write-Host "[Spector] $Msg" -ForegroundColor Yellow }
function Write-Err   { param([string]$Msg) Write-Host "[Spector] $Msg" -ForegroundColor Red }

function Test-DockerInstalled {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Err "Docker is not installed or not in PATH"
        exit 1
    }
}

function Invoke-Build {
    Test-DockerInstalled

    Write-Log "======================================================="
    Write-Log "  Spector v$Version - Docker Hub Image Build"
    Write-Log "  Image: $FullImage`:$Version"
    Write-Log "======================================================="

    if (-not (Test-Path $Dockerfile)) {
        Write-Err "Dockerfile not found at $Dockerfile"
        exit 1
    }

    # -- Step 1: Build Backend (Maven) --
    Write-Log "Step 1/3: Building backend (Maven - Spring Boot fat JAR)..."
    mvn clean package '-DskipTests' -B -q "-Drevision=$Version"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Maven build failed"
        exit $LASTEXITCODE
    }
    Write-Ok "Backend built successfully."

    # -- Step 2: Build Frontend (Angular) --
    Write-Log "Step 2/3: Building frontend (Angular Cortex)..."
    $uiPath = Join-Path $ProjectRoot "cortex/spector-cortex"
    Push-Location $uiPath
    try {
        npm ci --prefer-offline 2>&1 | ForEach-Object { Write-Host "  $_" }
        if ($LASTEXITCODE -ne 0) {
            Write-Err "npm ci failed"
            exit $LASTEXITCODE
        }
        npm run build 2>&1 | ForEach-Object { Write-Host "  $_" }
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Angular build failed"
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
    Write-Ok "Frontend built successfully."

    # -- Step 3: Docker Image --
    Write-Log "Step 3/3: Building Docker image..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    docker build `
        -f $Dockerfile `
        -t "${FullImage}:${Version}" `
        -t "${FullImage}:latest" `
        --build-arg BUILDKIT_INLINE_CACHE=1 `
        .

    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker build failed"
        exit $LASTEXITCODE
    }

    $sw.Stop()
    Write-Ok "Image built in $([math]::Round($sw.Elapsed.TotalSeconds))s"
    Write-Log "Image tags:"
    docker images $FullImage --format "  {{.Repository}}:{{.Tag}}  Size: {{.Size}}"
}

function Invoke-Push {
    Test-DockerInstalled

    Write-Log "Pushing to Docker Hub..."

    # Check image exists
    $imageExists = docker image inspect "${FullImage}:${Version}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Image '${FullImage}:${Version}' not found. Run: .\publish.ps1 build"
        exit 1
    }

    # Check Docker Hub login
    $loginCheck = docker info 2>$null | Select-String "Username"
    if (-not $loginCheck) {
        Write-Warn "Not logged in to Docker Hub. Running 'docker login'..."
        docker login
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Docker login failed"
            exit 1
        }
    }

    # Push version tag
    Write-Log "Pushing ${FullImage}:${Version}..."
    docker push "${FullImage}:${Version}"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Failed to push ${FullImage}:${Version}"
        exit $LASTEXITCODE
    }
    Write-Ok "Pushed ${FullImage}:${Version}"

    # Push latest tag
    Write-Log "Pushing ${FullImage}:latest..."
    docker push "${FullImage}:latest"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Failed to push ${FullImage}:latest"
        exit $LASTEXITCODE
    }
    Write-Ok "Pushed ${FullImage}:latest"

    Write-Ok "======================================================="
    Write-Ok "  Published: ${FullImage}:${Version}"
    Write-Ok "  Published: ${FullImage}:latest"
    Write-Ok ""
    Write-Ok "  Run it:"
    Write-Ok "    docker run -p 7070:7070 -p 7700:3000 ${FullImage}:${Version}"
    Write-Ok "======================================================="
}

function Invoke-Login {
    Test-DockerInstalled
    Write-Log "Logging in to Docker Hub..."
    docker login
}

function Invoke-Info {
    Test-DockerInstalled
    Write-Log "Docker Hub images for ${FullImage}:"
    docker images $FullImage --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
}

# -- Main ----------------------------------------------------------

Write-Log "Version: $Version"

switch ($Command) {
    "build"   { Invoke-Build }
    "push"    { Invoke-Push }
    "login"   { Invoke-Login }
    "info"    { Invoke-Info }
    default   { Invoke-Build; Invoke-Push }
}

} finally {
    Pop-Location
}
