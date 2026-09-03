# LongMemEval Benchmark Execution Script
[CmdletBinding()]
param(
    [string]$DatasetDir = "",
    [string]$OutputDir = "target\benchmark-results\longmemeval",
    [int]$TopK = 10
)

if (-not $DatasetDir) {
    $DatasetDir = if ($env:LONGMEMEVAL_DATASET_DIR) { $env:LONGMEMEVAL_DATASET_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "..\spector-datasets\longmemeval\data" }
}

$ErrorActionPreference = "Stop"

Write-Host "=== Spector Memory: LongMemEval Benchmark Harness ===" -ForegroundColor Cyan
Write-Host "Dataset Directory: $DatasetDir" -ForegroundColor Yellow
Write-Host "Output Directory:  $OutputDir" -ForegroundColor Yellow

if (-not (Test-Path "$DatasetDir\corpus.jsonl")) {
    Write-Host "Dataset not found. Generating LongMemEval dataset via Python..." -ForegroundColor Yellow
    python scripts/fetch_longmemeval_dataset.py
}

Write-Host "`nRunning LongMemEval Benchmark Harness..." -ForegroundColor Green
mvn test "-Dtest=com.spectrayan.spector.bench.cognitive.longmemeval.LongMemEvalBenchmarkHarness" "-DargLine=--enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED" "-Dsurefire.failIfNoSpecifiedTests=false" -pl nucleus/spector-test-support,bench/spector-bench -o

Write-Host "LongMemEval Benchmark Execution Completed." -ForegroundColor Green
