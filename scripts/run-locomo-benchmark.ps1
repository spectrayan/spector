# LoCoMo Benchmark Execution Script
[CmdletBinding()]
param(
    [string]$DatasetDir = "",
    [string]$OutputDir = "target\benchmark-results\locomo",
    [int]$TopK = 10
)

if (-not $DatasetDir) {
    $DatasetDir = if ($env:LOCOMO_DATASET_DIR) { $env:LOCOMO_DATASET_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "..\spector-datasets\locomo\data" }
}

$ErrorActionPreference = "Stop"

Write-Host "=== Spector Memory: LoCoMo Benchmark Harness ===" -ForegroundColor Cyan
Write-Host "Dataset Directory: $DatasetDir" -ForegroundColor Yellow
Write-Host "Output Directory:  $OutputDir" -ForegroundColor Yellow

if (-not (Test-Path "$DatasetDir\corpus.jsonl")) {
    Write-Host "Dataset not found. Generating LoCoMo dataset via Python..." -ForegroundColor Yellow
    python scripts/fetch_locomo_dataset.py
}

Write-Host "`nRunning LoCoMo Benchmark Harness..." -ForegroundColor Green
mvn test "-Dtest=com.spectrayan.spector.bench.cognitive.locomo.LoCoMoBenchmarkHarness" "-DargLine=--enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED" "-Dsurefire.failIfNoSpecifiedTests=false" -pl nucleus/spector-test-support,bench/spector-bench -o

Write-Host "LoCoMo Benchmark Execution Completed." -ForegroundColor Green
