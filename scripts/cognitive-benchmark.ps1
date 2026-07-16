#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector Cognitive Memory Benchmark
#  Loads dataset → runs queries → evaluates cognitive vs baseline
#  Outputs summary.json, detail.csv, and console one-liner
# ═══════════════════════════════════════════════════════════════

param(
    [string]$DatasetDir = "datasets/cognitive-benchmark",
    [string]$OutputDir  = "target/benchmark-results",
    [double]$RegressionThreshold = 0,   # 0 = no regression check
    [int]$TopK          = 10,
    [string]$Profile    = "",           # NONE, BALANCED, DEBUGGING, etc. (empty = per-query)
    [string]$OllamaUrl  = "http://localhost:11434",
    [string]$Model      = "nomic-embed-text",
    [int]$HeapMb        = 8192,
    [switch]$SkipBuild,
    [switch]$MiniDataset    # Use the built-in 50-memory mini-dataset for quick tests
)

$ErrorActionPreference = "Stop"

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector Cognitive Memory Benchmark" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Resolve paths ──
$projectRoot = Split-Path -Parent $PSScriptRoot
$benchModule = Join-Path $projectRoot "bench/spector-bench"

if ($MiniDataset) {
    $DatasetDir = Join-Path $benchModule "src/test/resources/cognitive-benchmark-mini"
    Write-Host "  Using mini-dataset (50 memories, 20 queries)" -ForegroundColor Yellow
}

$resolvedDataset = if ([System.IO.Path]::IsPathRooted($DatasetDir)) { $DatasetDir } else { Join-Path $projectRoot $DatasetDir }
$resolvedOutput  = if ([System.IO.Path]::IsPathRooted($OutputDir))  { $OutputDir  } else { Join-Path $projectRoot $OutputDir  }

# Validate dataset exists
if (!(Test-Path (Join-Path $resolvedDataset "corpus.jsonl"))) {
    Write-Host "ERROR: Dataset not found at $resolvedDataset" -ForegroundColor Red
    Write-Host "  Expected files: corpus.jsonl, queries.jsonl, qrels.tsv, entities.jsonl," -ForegroundColor Red
    Write-Host "                  temporal_chains.jsonl, hebbian_edges.jsonl, persona.json" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Generate a dataset first:" -ForegroundColor Yellow
    Write-Host "    .\scripts\cognitive-generate.ps1 --Output datasets/cognitive-benchmark" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Or use the built-in mini-dataset:" -ForegroundColor Yellow
    Write-Host "    .\scripts\cognitive-benchmark.ps1 -MiniDataset" -ForegroundColor Yellow
    exit 1
}

Write-Host "  Dataset:    $resolvedDataset" -ForegroundColor White
Write-Host "  Output:     $resolvedOutput" -ForegroundColor White
Write-Host "  TopK:       $TopK" -ForegroundColor White
Write-Host "  Heap:       ${HeapMb}MB" -ForegroundColor White
if ($RegressionThreshold -gt 0) {
    Write-Host "  Regression: nDCG threshold = $RegressionThreshold" -ForegroundColor White
}
Write-Host ""

# ── Build if needed ──
if (!$SkipBuild) {
    Write-Host "── Building spector-bench module ──" -ForegroundColor Yellow
    Push-Location $projectRoot
    try {
        mvn -B package -pl bench/spector-bench -am -DskipTests --no-transfer-progress
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
    Write-Host "  Run: mvn package -pl bench/spector-bench -am -DskipTests" -ForegroundColor Yellow
    exit 1
}

# Use Maven to resolve full classpath
Write-Host "── Resolving classpath ──" -ForegroundColor Yellow
Push-Location $projectRoot
$cpFile = Join-Path $env:TEMP "spector-bench-cp.txt"
$ErrorActionPreference = "Continue"
mvn -B dependency:build-classpath -pl bench/spector-bench "-Dmdep.outputFile=$cpFile" --no-transfer-progress 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
Pop-Location

$classpath = if (Test-Path $cpFile) {
    "$($benchJar.FullName);$(Get-Content $cpFile)"
} else {
    # Fallback: use the fat JAR or target/classes
    $benchJar.FullName
}

# ── JVM arguments ──
$jvmArgs = @(
    "--enable-preview"
    "--add-modules", "jdk.incubator.vector"
    "--enable-native-access=ALL-UNNAMED"
    "--add-opens", "java.base/java.lang.foreign=ALL-UNNAMED"
    "-Xmx${HeapMb}m"
    "-Dlogback.configurationFile=logback-bench.xml"
    "-Dspector.embedding.cache-dir=$(Join-Path $resolvedDataset '../../.spector-bench')"
    "-cp", $classpath
)

# ── Harness arguments ──
$harnessArgs = @(
    $resolvedDataset
    $resolvedOutput
)
if ($RegressionThreshold -gt 0) {
    $harnessArgs += "$RegressionThreshold"
} else {
    $harnessArgs += "0"
}
$harnessArgs += "$TopK"

# Profile override (5th arg)
if ($Profile -ne "") {
    $harnessArgs += $Profile.ToUpper()
    Write-Host "  Profile override: $($Profile.ToUpper())" -ForegroundColor Yellow
}

# ── Run benchmark ──
Write-Host ""
Write-Host "── Running Cognitive Benchmark ──" -ForegroundColor Cyan
Write-Host ""

$mainClass = "com.spectrayan.spector.bench.cognitive.CognitiveBenchmarkHarness"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

& java @jvmArgs $mainClass @harnessArgs
$exitCode = $LASTEXITCODE

$sw.Stop()
$elapsed = [math]::Round($sw.Elapsed.TotalSeconds, 1)

Write-Host ""
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Interpret exit code ──
switch ($exitCode) {
    0 { Write-Host "  PASS - Cognitive retrieval demonstrates medium+ effect size" -ForegroundColor Green }
    1 { Write-Host "  FAIL - Cohen's d < 0.5 (effect size insufficient)" -ForegroundColor Red }
    2 { Write-Host "  FAIL - nDCG below regression threshold ($RegressionThreshold)" -ForegroundColor Red }
    3 { Write-Host "  FAIL - Dataset validation failed" -ForegroundColor Red }
    4 { Write-Host "  FAIL - Memory instance setup failed" -ForegroundColor Red }
    5 { Write-Host "  WARN - Partial execution (some queries timed out)" -ForegroundColor Yellow }
    default { Write-Host "  Unknown exit code: $exitCode" -ForegroundColor Red }
}

Write-Host "  Elapsed: ${elapsed}s" -ForegroundColor White
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Show output files ──
if (Test-Path $resolvedOutput) {
    Write-Host ""
    Write-Host "  Output files:" -ForegroundColor Gray
    Get-ChildItem $resolvedOutput | ForEach-Object {
        Write-Host "    $($_.Name) ($([math]::Round($_.Length / 1KB, 1)) KB)" -ForegroundColor Gray
    }
}

exit $exitCode
