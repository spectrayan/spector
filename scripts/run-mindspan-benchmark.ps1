# scripts/run-mindspan-benchmark.ps1
# Runs the MindSpan 20-Year Multi-Session Longitudinal Cognitive Memory Benchmark
param(
    [string]$DatasetDir = "",
    [string]$OutputDir = "",
    [string]$GeminiApiKey = $(if ($env:GEMINI_API_KEY) { $env:GEMINI_API_KEY } else { "" }),
    [string]$GeminiModel = "gemini-3.1-flash-lite",
    [int]$TopK = 20,
    [int]$StartIndex = 0,
    [int]$Limit = 0,
    [int]$IngestLimit = 0,
    [int]$SessionBatchSize = 10,
    [switch]$SmokeTestOnly,
    [int]$SmokeTestLimit = 5,
    [string]$RunQaJudge = "true",
    [int]$Concurrency = 6
)

if (-not $DatasetDir) {
    $DatasetDir = if ($env:MINDSPAN_DATASET_DIR) { $env:MINDSPAN_DATASET_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "..\spector-datasets\mindspan" }
}
if (-not $OutputDir) {
    $OutputDir = if ($env:MINDSPAN_OUTPUT_DIR) { $env:MINDSPAN_OUTPUT_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "..\spector-datasets\mindspan\results" }
}

$DatasetDir = [System.IO.Path]::GetFullPath($DatasetDir)
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  MindSpan 20-Year Longitudinal Cognitive Memory Benchmark       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Dataset Directory: $DatasetDir"
Write-Host "Output Directory:  $OutputDir"
Write-Host "Gemini Model:      $GeminiModel"
Write-Host "Top-K Candidates:  $TopK"
Write-Host "Start Index:       $StartIndex"
Write-Host "Query Limit:       $(if ($Limit -gt 0) { $Limit } else { 'ALL' })"
Write-Host "Session Batch Size:$SessionBatchSize"
Write-Host "Run QA Judge:      $RunQaJudge"
Write-Host "Concurrency:       $Concurrency"
Write-Host "Smoke Test Only:   $SmokeTestOnly"
if ($SmokeTestOnly) {
    Write-Host "Smoke Test Limit:  $SmokeTestLimit"
}
Write-Host "=================================================================" -ForegroundColor Cyan

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$jvmArgs = "--enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang.foreign=ALL-UNNAMED -Xmx8g -Dlogback.configurationFile=logback-bench.xml"
$runnerClass = "com.spectrayan.spector.bench.cognitive.mindspan.MindSpanBenchmarkRunner"

Write-Host "Starting MindSpan Benchmark Runner via Surefire..." -ForegroundColor Green
mvn test -pl bench/spector-bench `
    "-Dtest=MindSpanBenchmarkTest" `
    "-DskipBenchTests=false" `
    "-DdatasetDir=$DatasetDir" `
    "-DoutputDir=$OutputDir" `
    "-DgeminiApiKey=$GeminiApiKey" `
    "-DgeminiModel=$GeminiModel" `
    "-DtopK=$TopK" `
    "-DstartIndex=$StartIndex" `
    "-Dlimit=$Limit" `
    "-DingestLimit=$IngestLimit" `
    "-DsessionBatchSize=$SessionBatchSize" `
    "-DsmokeTestOnly=$($SmokeTestOnly.IsPresent.ToString().ToLower())" `
    "-DsmokeTestLimit=$SmokeTestLimit" `
    "-DrunQaJudge=$RunQaJudge" `
    "-Dconcurrency=$Concurrency"

Write-Host ""
Write-Host "MindSpan Benchmark execution complete! Results saved in $OutputDir" -ForegroundColor Green
