param(
    [string]$Dataset = "locomo"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SpectorRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$DatasetsBase = (Resolve-Path (Join-Path $SpectorRoot "../spector-datasets")).Path
$DatasetDir = Join-Path $DatasetsBase "$Dataset/data"
$IngestedMemoryDir = Join-Path $DatasetDir "ingested-memory"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Spector Memory -- Off-Heap MMAP Dataset Integrity Validator" -ForegroundColor Cyan
Write-Host " Dataset:          $Dataset" -ForegroundColor Yellow
Write-Host " Dataset Dir:      $DatasetDir" -ForegroundColor Yellow
Write-Host " Ingested Memory:  $IngestedMemoryDir" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

if (-not (Test-Path $DatasetDir)) {
    Write-Error "Dataset directory not found: $DatasetDir"
}

if (-not (Test-Path $IngestedMemoryDir)) {
    Write-Warning "Ingested memory directory not found. Please run reingest_dataset.ps1 first."
}

$MavenArgs = @(
    "test",
    "-pl", "bench/spector-bench",
    "-am",
    "-o",
    "-Dtest=MmapDatasetIntegrityTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-DskipBenchTests=false",
    "-Ddataset=$Dataset"
)

Push-Location $SpectorRoot
try {
    & mvn $MavenArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Integrity validation test failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$ReportFile = Join-Path $DatasetsBase "$Dataset/results/mmap_validation_report.md"
if (Test-Path $ReportFile) {
    Write-Host "`n"
    Get-Content $ReportFile
}
