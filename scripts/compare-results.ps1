#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector Recall Evaluation Graph Comparison Script
#  Runs significance tests between Binary and Hypergraph runs
# ═══════════════════════════════════════════════════════════════

param(
    [string]$BinaryResultsDir = "target/benchmark-results-binary",
    [string]$HypergraphResultsDir = "target/benchmark-results-hyper"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$benchModule = Join-Path $projectRoot "bench/spector-bench"

# ── Locate spector-bench jar ──
$benchJar = Get-ChildItem (Join-Path $benchModule "target") -Filter "spector-bench-*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|tests" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (!$benchJar) {
    Write-Host "ERROR: spector-bench JAR not found. Run 'mvn package -pl bench/spector-bench -DskipTests' first." -ForegroundColor Red
    exit 1
}

# ── Resolve classpath ──
$cpFile = Join-Path $env:TEMP "spector-bench-cp.txt"
if (!(Test-Path $cpFile)) {
    Write-Host "Resolving classpath..." -ForegroundColor Yellow
    Push-Location $projectRoot
    $ErrorActionPreference = "Continue"
    mvn -B dependency:build-classpath -pl bench/spector-bench "-Dmdep.outputFile=$cpFile" --no-transfer-progress 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    Pop-Location
}

$classpath = "$($benchJar.FullName);$(Get-Content $cpFile)"

# ── Resolve files ──
$binaryCsv = Join-Path $projectRoot "$BinaryResultsDir/detail.csv"
$hyperCsv = Join-Path $projectRoot "$HypergraphResultsDir/detail.csv"

if (!(Test-Path $binaryCsv)) {
    Write-Host "ERROR: Binary detail.csv not found at $binaryCsv" -ForegroundColor Red
    exit 1
}
if (!(Test-Path $hyperCsv)) {
    Write-Host "ERROR: Hypergraph detail.csv not found at $hyperCsv" -ForegroundColor Red
    exit 1
}

# ── Run Java CompareGraphsRunner ──
java -cp $classpath com.spectrayan.spector.bench.cognitive.CompareGraphsRunner $binaryCsv $hyperCsv
