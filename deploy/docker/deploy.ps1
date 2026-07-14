#Requires -Version 5.1
<#
.SYNOPSIS
    Spector — Build & Deploy Docker Image

.DESCRIPTION
    Builds the Spector Docker image and/or runs it as a container.

.PARAMETER Command
    The action to perform:
      build   - Build the Docker image only
      run     - Run the container (image must exist)
      stop    - Stop the running container
      logs    - Tail container logs
      clean   - Stop + remove container + image
      deploy  - Build + run (default)

.EXAMPLE
    .\deploy.ps1              # Build + run
    .\deploy.ps1 build        # Build only
    .\deploy.ps1 run          # Run only
    .\deploy.ps1 stop         # Stop container
    .\deploy.ps1 logs         # Tail logs
    .\deploy.ps1 clean        # Full cleanup
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("build", "run", "stop", "logs", "clean", "deploy", "")]
    [string]$Command = "deploy"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

# ── Configuration ──────────────────────────────────────────────────
$ImageName     = "spector"
$ContainerName = "spector"
$Dockerfile    = "deploy/docker/Dockerfile"
$DataVolume    = "spector-data"
$HostPortHttp  = 7700
$HostPortApi   = 7070

# ── Navigate to project root ──────────────────────────────────────
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "../..")
Push-Location $ProjectRoot

try {

# ── Helper Functions ───────────────────────────────────────────────

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

    if (-not (Test-Path $Dockerfile)) {
        Write-Err "Dockerfile not found at $Dockerfile"
        exit 1
    }

    # 1. Build Spring Boot fat JAR on Host
    Write-Log "Building backend (Maven - Spring Boot fat JAR)..."
    mvn package '-DskipTests' -B -q
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Maven backend build failed"
        exit $LASTEXITCODE
    }
    Write-Ok "Backend built successfully (Spring Boot fat JAR)."

    # 2. Build Angular Frontend on Host
    Write-Log "Building Angular Cortex Frontend..."
    $uiPath = Join-Path $ProjectRoot "cortex/spector-cortex"
    Push-Location $uiPath
    try {
        Write-Log "Running npm install to ensure all dependencies are present..."
        npm install 2>&1 | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) {
            Write-Err "npm install failed"
            exit $LASTEXITCODE
        }
        npm run build 2>&1 | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Angular frontend build failed"
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
    Write-Ok "Angular frontend built successfully."

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $versionTag = "v$timestamp"

    # Backup current latest image if it exists
    $existing = docker image inspect "${ImageName}:latest" 2>$null
    if ($LASTEXITCODE -eq 0) {
        $backupTag = "backup-$timestamp"
        Write-Log "Backing up current '${ImageName}:latest' image to '${ImageName}:${backupTag}' and '${ImageName}:backup'..."
        docker tag "${ImageName}:latest" "${ImageName}:${backupTag}"
        docker tag "${ImageName}:latest" "${ImageName}:backup"
        Write-Ok "Backups created successfully: ${ImageName}:${backupTag} & ${ImageName}:backup"
    }

    Write-Log "Building Docker image '${ImageName}:latest' and '${ImageName}:${versionTag}'..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    docker build `
        -f $Dockerfile `
        -t "${ImageName}:latest" `
        -t "${ImageName}:${versionTag}" `
        --build-arg BUILDKIT_INLINE_CACHE=1 `
        .

    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker build failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }

    $sw.Stop()
    Write-Ok "Image '${ImageName}:latest' (and '${ImageName}:${versionTag}') built in $([math]::Round($sw.Elapsed.TotalSeconds))s"
    docker images $ImageName --format "  Tag: {{.Tag}}  Size: {{.Size}}  Created: {{.CreatedAt}}"
}

function Invoke-Stop {
    $running = docker ps -q -f "name=$ContainerName" 2>$null
    if ($running) {
        Write-Log "Stopping container '$ContainerName'..."
        docker stop --timeout 30 $ContainerName | Out-Null
        Write-Ok "Container stopped"
    }

    $exists = docker ps -aq -f "name=$ContainerName" 2>$null
    if ($exists) {
        docker rm $ContainerName | Out-Null
        Write-Log "Removed old container"
    }
}

function Invoke-Run {
    Test-DockerInstalled

    # Check image exists
    $imageExists = docker image inspect $ImageName 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Image '$ImageName' not found. Run: .\deploy.ps1 build"
        exit 1
    }

    # Stop existing container
    Invoke-Stop

    # Create data volume if it doesn't exist
    docker volume create $DataVolume 2>$null | Out-Null

    Write-Log "Starting container '$ContainerName'..."
    Write-Log "  HTTP (Nginx+Dashboard) -> http://localhost:$HostPortHttp"
    Write-Log "  API  (SpectorNode)     -> http://localhost:$HostPortApi"
    Write-Log "  Data volume            -> $DataVolume"

    docker run -d `
        --name $ContainerName `
        -p "${HostPortHttp}:3000" `
        -p "${HostPortApi}:7070" `
        -v "${DataVolume}:/data" `
        -e "SPECTOR_DATA_DIR=/data" `
        -e "SPECTOR_DB_ENCRYPT=false" `
        -e "SPECTOR_DIMENSIONS=1024" `
        -e "SPECTOR_OLLAMA_BASE_URL=http://host.docker.internal:11434" `
        -e "SPECTOR_OLLAMA_EMBED_MODEL=qwen3-embedding:0.6b" `
        -e "SPECTOR_OLLAMA_MODEL=spector-extractor:small" `
        -e "SPECTOR_CORS_ORIGINS=http://localhost:4200,http://localhost:7700,http://localhost:3000,*" `
        --add-host=host.docker.internal:host-gateway `
        --restart unless-stopped `
        $ImageName

    if ($LASTEXITCODE -ne 0) {
        Write-Err "Failed to start container"
        exit $LASTEXITCODE
    }

    # Wait for health check
    Write-Log "Waiting for health check..."
    $maxRetries = 30
    for ($i = 0; $i -lt $maxRetries; $i++) {
        $health = docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null
        if ($health -eq "healthy") {
            Write-Ok "Container is healthy!"
            return
        }
        Start-Sleep -Seconds 2
    }

    Write-Warn "Health check timed out after $($maxRetries * 2)s - container may still be starting"
    Write-Warn "Check logs: .\deploy.ps1 logs"
}

function Invoke-Logs {
    $running = docker ps -q -f "name=$ContainerName" 2>$null
    if (-not $running) {
        Write-Err "Container '$ContainerName' is not running"
        exit 1
    }
    docker logs -f $ContainerName
}

function Invoke-Clean {
    Invoke-Stop

    $imageExists = docker image inspect $ImageName 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Log "Removing image '$ImageName'..."
        docker rmi $ImageName | Out-Null
        Write-Ok "Image removed"
    }

    Write-Log "Note: Data volume '$DataVolume' preserved. Remove with: docker volume rm $DataVolume"
}

# ── Main ───────────────────────────────────────────────────────────

switch ($Command) {
    "build"  { Invoke-Build }
    "run"    { Invoke-Run }
    "stop"   { Invoke-Stop }
    "logs"   { Invoke-Logs }
    "clean"  { Invoke-Clean }
    default  { Invoke-Build; Invoke-Run }
}

} finally {
    Pop-Location
}
