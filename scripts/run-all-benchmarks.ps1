#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector Cognitive Memory Benchmarks Orchestrator
#  Runs all runners (harness, sweep, ablation, stack, scale)
#  for all three datasets and compiles comparison reports.
# ═══════════════════════════════════════════════════════════════

param(
    [string]$DatasetsBase = "d:\git\spector-datasets",
    [string]$HeapMb = "8192",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector Benchmark Suite Orchestrator" -ForegroundColor Cyan
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
    $benchJar.FullName
}

$jvmArgs = @(
    "--enable-preview",
    "--add-modules", "jdk.incubator.vector",
    "--enable-native-access=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.foreign=ALL-UNNAMED",
    "-Xmx${HeapMb}m",
    "-Dlogback.configurationFile=logback-bench.xml",
    "-Dspector.embedding.cache-dir=$(Join-Path $DatasetsBase '.spector-bench')",
    "-cp", $classpath
)

# ── Helper to run a Java class ──
function Run-JavaClass($className, $argsList) {
    Write-Host "Running: $className $($argsList -join ' ')" -ForegroundColor Gray
    & java @jvmArgs $className @argsList
    return $LASTEXITCODE
}

# ── Helper to translate summary.json to benchmark-report.md ──
function Generate-BenchmarkReportMarkdown($summaryJsonPath, $markdownOutputPath) {
    if (!(Test-Path $summaryJsonPath)) {
        Write-Host "Warning: Summary JSON not found at $summaryJsonPath. Skipping markdown generation." -ForegroundColor Yellow
        return
    }
    $summary = Get-Content $summaryJsonPath -Raw | ConvertFrom-Json
    
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine("### Summary Metrics")
    [void]$sb.AppendLine("| Metric | Baseline | Similarity | Cognitive |")
    [void]$sb.AppendLine("|---|---|---|---|")
    [void]$sb.AppendLine(("| nDCG@10 | {0:F4} | {1:F4} | {2:F4} |" -f $summary.baseline_metrics.ndcg_at_10, $summary.similarity_metrics.ndcg_at_10, $summary.cognitive_metrics.ndcg_at_10))
    [void]$sb.AppendLine(("| Recall@10 | {0:F4} | {1:F4} | {2:F4} |" -f $summary.baseline_metrics.recall_at_10, $summary.similarity_metrics.recall_at_10, $summary.cognitive_metrics.recall_at_10))
    [void]$sb.AppendLine(("| MRR@10 | {0:F4} | {1:F4} | {2:F4} |" -f $summary.baseline_metrics.mrr_at_10, $summary.similarity_metrics.mrr_at_10, $summary.cognitive_metrics.mrr_at_10))
    [void]$sb.AppendLine(("| Latency (ms) | {0:F1} | {1:F1} | {2:F1} |" -f $summary.baseline_metrics.avg_latency_ms, $summary.similarity_metrics.avg_latency_ms, $summary.cognitive_metrics.avg_latency_ms))
    [void]$sb.AppendLine()
    
    [void]$sb.AppendLine("### Statistical Significance (Cognitive vs Baseline)")
    [void]$sb.AppendLine(("- **Cohen's d**: {0:F4}" -f $summary.cohens_d))
    [void]$sb.AppendLine(("- **p-value**: {0:F6}" -f $summary.p_value))
    [void]$sb.AppendLine(("- **Win/Tie/Loss**: {0} Wins, {1} Ties, {2} Losses" -f $summary.win_tie_loss.wins, $summary.win_tie_loss.ties, $summary.win_tie_loss.losses))
    [void]$sb.AppendLine()
    
    [void]$sb.AppendLine("### Pipeline & Scoring Effects")
    [void]$sb.AppendLine(("- **Similarity vs Baseline (Pipeline effect)**: Cohen's d = {0:F4}, p = {1:F6}" -f $summary.similarity_vs_baseline.cohens_d, $summary.similarity_vs_baseline.p_value))
    [void]$sb.AppendLine(("- **Cognitive vs Similarity (Scoring effect)**: Cohen's d = {0:F4}, p = {1:F6}" -f $summary.cognitive_vs_similarity.cohens_d, $summary.cognitive_vs_similarity.p_value))
    [void]$sb.AppendLine()
    
    [void]$sb.AppendLine("### Subsystem Contributions")
    [void]$sb.AppendLine(("- **Hebbian Graph Boost**: {0:F1}%" -f $summary.subsystem_contributions.hebbian_pct))
    [void]$sb.AppendLine(("- **Temporal Chain Boost**: {0:F1}%" -f $summary.subsystem_contributions.temporal_pct))
    [void]$sb.AppendLine(("- **Entity Graph Boost**: {0:F1}%" -f $summary.subsystem_contributions.entity_pct))
    [void]$sb.AppendLine(("- **Importance Decay Scoring**: {0:F1}%" -f $summary.subsystem_contributions.importance_pct))
    [void]$sb.AppendLine(("- **Valence Filtering**: {0:F1}%" -f $summary.subsystem_contributions.valence_pct))
    [void]$sb.AppendLine(("- **Tag Gating**: {0:F1}%" -f $summary.subsystem_contributions.tag_gating_pct))
    
    $sb.ToString() | Set-Content $markdownOutputPath -Force
    Write-Host "  Converted summary.json to benchmark-report.md" -ForegroundColor Green
}

# ── Datasets to run ──
$datasets = @(
    @{ Name = "balanced-baseline"; OverrideProfiles = @("") },
    @{ Name = "adhd-diversified"; OverrideProfiles = @("", "HYPERFOCUS", "DIVERGENT", "SYSTEMATIZER") },
    @{ Name = "engineer-persona"; OverrideProfiles = @("") }
)

foreach ($ds in $datasets) {
    $name = $ds.Name
    $datasetDir = Join-Path $DatasetsBase "$name\data"
    $resultsDir = Join-Path $DatasetsBase "$name\results"
    
    Write-Host "`n===================================================" -ForegroundColor Cyan
    Write-Host "  PROCESSING DATASET: $name" -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan
    
    # 1. Run the CognitiveBenchmarkHarness (for each profile override)
    foreach ($profile in $ds.OverrideProfiles) {
        $subfolder = if ($profile -eq "") { "per-query" } else { $profile.ToLower() }
        $outputFolder = Join-Path $resultsDir $subfolder
        
        Write-Host "`n  Running CognitiveBenchmarkHarness for profile: '$subfolder'..." -ForegroundColor Yellow
        $harnessArgs = @($datasetDir, $outputFolder, "0", "10")
        if ($profile -ne "") {
            $harnessArgs += $profile
        }
        
        $code = Run-JavaClass "com.spectrayan.spector.bench.cognitive.CognitiveBenchmarkHarness" $harnessArgs
        Write-Host "  Harness exited with code: $code" -ForegroundColor White
        
        # If default run, generate benchmark-report.md from summary.json
        if ($profile -eq "") {
            $summaryJson = Join-Path $outputFolder "summary.json"
            $markdownReport = Join-Path $resultsDir "benchmark-report.md"
            Generate-BenchmarkReportMarkdown $summaryJson $markdownReport
        }
    }
    
    # 2. Run CognitiveProfileSweepRunner
    Write-Host "`n  Running CognitiveProfileSweepRunner..." -ForegroundColor Yellow
    Run-JavaClass "com.spectrayan.spector.bench.cognitive.CognitiveProfileSweepRunner" @($datasetDir, $resultsDir)
    
    # 3. Run RetrievalStackMatrixRunner
    Write-Host "`n  Running RetrievalStackMatrixRunner..." -ForegroundColor Yellow
    Run-JavaClass "com.spectrayan.spector.bench.cognitive.RetrievalStackMatrixRunner" @($datasetDir, $resultsDir)
    
    # 4. Run SubsystemAblationRunner
    Write-Host "`n  Running SubsystemAblationRunner..." -ForegroundColor Yellow
    Run-JavaClass "com.spectrayan.spector.bench.cognitive.SubsystemAblationRunner" @($datasetDir, $resultsDir)
    
    # 5. Run ScalePerformanceRunner
    Write-Host "`n  Running ScalePerformanceRunner..." -ForegroundColor Yellow
    Run-JavaClass "com.spectrayan.spector.bench.cognitive.ScalePerformanceRunner" @($datasetDir, $resultsDir)
    
    # 6. Run ComparisonReportGenerator
    Write-Host "`n  Generating Comparison Report..." -ForegroundColor Yellow
    $comparisonReportFile = Join-Path $resultsDir "comparison-report.md"
    Run-JavaClass "com.spectrayan.spector.bench.cognitive.ComparisonReportGenerator" @($resultsDir, $comparisonReportFile)
    
    Write-Host "`n  Dataset $name complete!" -ForegroundColor Green
}

Write-Host "`n═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ALL BENCHMARKS EXECUTED SUCCESSFULLY" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
