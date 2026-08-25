param(
    [string]$Dataset = "locomo",
    [switch]$SkipIngest
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SpectorRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$DatasetsBase = (Resolve-Path (Join-Path $SpectorRoot "../spector-datasets")).Path
$DatasetDir = Join-Path $DatasetsBase "$Dataset/data"
$IngestedMemoryDir = Join-Path $DatasetDir "ingested-memory"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Spector Memory -- Off-Heap Bundle Re-Ingestion Orchestrator" -ForegroundColor Cyan
Write-Host " Dataset:          $Dataset" -ForegroundColor Yellow
Write-Host " Dataset Dir:      $DatasetDir" -ForegroundColor Yellow
Write-Host " Ingested Memory:  $IngestedMemoryDir" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

# Step 1: Wipe stale off-heap partition bundle
if (Test-Path $IngestedMemoryDir) {
    Write-Host "`n[Step 1/2] Removing old off-heap partition bundles..." -ForegroundColor Yellow
    Remove-Item $IngestedMemoryDir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Old bundle directory cleared." -ForegroundColor Green
} else {
    Write-Host "`n[Step 1/2] No existing ingested-memory bundle found. Proceeding with fresh build." -ForegroundColor Green
}

# Step 2: Trigger Spector native RememberPathway ingestion & bundle compilation via Maven
Write-Host "`n[Step 2/2] Running Native Ingestion Pipeline (RememberPathway + Hypergraph Compilation)..." -ForegroundColor Green
$MavenArgs = @(
    "test",
    "-pl", "bench/spector-bench",
    "-am",
    "-o",
    "-Dtest=ContextExportTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-DskipBenchTests=false",
    "-Ddataset=$Dataset",
    "-Dlimit=1"
)

Push-Location $SpectorRoot
try {
    & mvn $MavenArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Re-ingestion failed with Maven exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path (Join-Path $IngestedMemoryDir "runtime/runtime.bundle"))) {
    Write-Error "Failed to generate runtime bundle in $IngestedMemoryDir"
}

Write-Host "`nOff-Heap Partition Bundle Rebuilt & Verified Successfully!" -ForegroundColor Cyan
