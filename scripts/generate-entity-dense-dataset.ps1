#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Entity-Dense Dataset Generator — Detached Runner
#  Auto-resumes from existing corpus files.
#  Designed to run in an external terminal, independent of IDE.
# ═══════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$projectRoot = "d:\git\spector"
$PersonaFile = "d:\git\spector-datasets\entity-dense-baseline\data\persona.json"
$OutputDir   = "d:\git\spector-datasets\entity-dense-baseline\data"
$Model       = "qwen3.5"
$CorpusSize  = 10000
$NumDays     = 400

Write-Host "=== Entity-Dense Dataset Generator ===" -ForegroundColor Cyan

# ── Check existing progress ──
$existingFiles = Get-ChildItem (Join-Path $OutputDir "corpus-day-*.jsonl") -ErrorAction SilentlyContinue
if ($existingFiles) {
    $total = ($existingFiles | ForEach-Object { (Get-Content $_.FullName | Measure-Object -Line).Lines } | Measure-Object -Sum).Sum
    Write-Host "  Found $($existingFiles.Count) day files with $total records. Generator will auto-resume." -ForegroundColor Yellow
}

# ── Resolve classpath ──
$benchModule = Join-Path $projectRoot "bench/spector-bench"
$benchJar = Get-ChildItem (Join-Path $benchModule "target") -Filter "spector-bench-*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|tests" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (!$benchJar) {
    Write-Host "ERROR: spector-bench JAR not found." -ForegroundColor Red; exit 1
}
$classpath = "$($benchJar.FullName);$(Join-Path $benchModule 'target/dependency/*')"

$logFile = Join-Path $OutputDir "generation.log"
Write-Host "  Log: $logFile" -ForegroundColor White
Write-Host "  Starting..." -ForegroundColor Green

java --enable-native-access=ALL-UNNAMED -Xmx4g `
    -cp $classpath `
    com.spectrayan.spector.bench.cognitive.generator.DatasetGeneratorMain `
    "--persona=$PersonaFile" `
    "--output=$OutputDir" `
    "--model=$Model" `
    "--corpus-size=$CorpusSize" `
    "--num-days=$NumDays" `
    "--conversations-per-day=20" `
    "--biographical-depth=30" 2>&1 | Tee-Object -FilePath $logFile -Append

Write-Host "=== Generation complete! ===" -ForegroundColor Green
Read-Host "Press Enter to close"
