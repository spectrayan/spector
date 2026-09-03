#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector Dataset Embedding & V3 Memory Pre-Generator
#  Runs offline embedding pre-caching and V3 disk pre-ingestion
#  overnight for datasets lacking pre-computed embeddings.
# ═══════════════════════════════════════════════════════════════

param(
    [string]$DatasetsBase = "",
    [string[]]$Datasets   = @("adhd-diversified", "entity-dense-seed2"),
    [string]$Model        = "nomic-embed-text",
    [string]$HeapMb       = "8192",
    [switch]$SkipBuild
)

if (-not $DatasetsBase) {
    $DatasetsBase = if ($env:SPECTOR_DATASETS_DIR) { $env:SPECTOR_DATASETS_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "..\spector-datasets" }
}

$ErrorActionPreference = "Stop"

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector Dataset Pre-Generator (Overnight Task)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Resolve paths ──
$projectRoot = Split-Path -Parent $PSScriptRoot
$benchModule = Join-Path $projectRoot "bench/spector-bench"

# ── Build if needed ──
if (!$SkipBuild) {
    Write-Host "── Building spector-bench module ──" -ForegroundColor Yellow
    Push-Location $projectRoot
    try {
        mvn -B install -pl bench/spector-bench -am -DskipTests --no-transfer-progress
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Maven build failed" -ForegroundColor Red
            exit 1
        }
    } finally {
        Pop-Location
    }
    Write-Host "  Build complete" -ForegroundColor Green
    Write-Host ""
}

# ── Resolve classpath ──
$benchJar = Get-ChildItem (Join-Path $benchModule "target") -Filter "spector-bench-*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|tests" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (!$benchJar) {
    Write-Host "ERROR: spector-bench JAR not found in target/" -ForegroundColor Red
    exit 1
}

Push-Location $projectRoot
$cpFile = Join-Path $env:TEMP "spector-bench-cp.txt"
$ErrorActionPreference = "Continue"
mvn -B dependency:build-classpath -pl bench/spector-bench "-Dmdep.outputFile=$cpFile" --no-transfer-progress 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
Pop-Location

$classpath = if (Test-Path $cpFile) {
    "$($benchJar.FullName);$(Get-Content $cpFile)"
} else {
    $benchJar.FullName
}

$jvmArgs = @(
    "--enable-preview",
    "--add-modules", "jdk.incubator.vector",
    "--enable-native-access=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.foreign=ALL-UNNAMED",
    "-Xmx${HeapMb}m",
    "-Dlogback.configurationFile=logback-bench.xml",
    "-cp", $classpath
)

foreach ($datasetName in $Datasets) {
    $datasetDir = Join-Path $DatasetsBase "$datasetName\data"
    if (-not (Test-Path $datasetDir)) {
        Write-Host "WARNING: Dataset path not found: $datasetDir. Skipping." -ForegroundColor Yellow
        continue
    }

    Write-Host "`n===================================================" -ForegroundColor Cyan
    Write-Host "  PRE-CACHING EMBEDDINGS FOR: $datasetName" -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan

    & java @jvmArgs com.spectrayan.spector.bench.cognitive.DatasetEmbeddingPrecacheRunner "$datasetDir" "$Model" "true"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Dataset $datasetName successfully pre-cached and pre-ingested!" -ForegroundColor Green
    } else {
        Write-Host "  ERROR: Pre-caching failed for $datasetName with exit code $LASTEXITCODE" -ForegroundColor Red
    }
}

Write-Host "`n═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ALL DATASET PRE-GENERATIONS COMPLETED" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
