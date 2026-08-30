# scripts/run-mindspan-benchmark.ps1
# Runs the MindSpan 20-Year Multi-Session Longitudinal Cognitive Memory Benchmark
param(
    [string]$DatasetDir = "d:\git\spector-datasets\mindspan\data",
    [string]$OutputDir = "d:\git\spector-datasets\mindspan\results",
    [string]$GeminiApiKey = $(if ($env:GEMINI_API_KEY) { $env:GEMINI_API_KEY } else { "" }),
    [string]$GeminiModel = "gemini-3.1-flash-lite",
    [int]$TopK = 15,
    [int]$SessionBatchSize = 10,
    [switch]$SmokeTestOnly,
    [int]$SmokeTestLimit = 5
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  🧠 Spector Memory — MindSpan 20-Year Longitudinal Benchmark    " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Dataset Directory: $DatasetDir"
Write-Host "Output Directory:  $OutputDir"
Write-Host "Gemini Model:      $GeminiModel"
Write-Host "Top-K Candidates:  $TopK"
Write-Host "Session Batch Size:$SessionBatchSize"
Write-Host "Smoke Test Only:   $SmokeTestOnly"
if ($SmokeTestOnly) {
    Write-Host "Smoke Test Limit:  $SmokeTestLimit"
}
Write-Host "=================================================================" -ForegroundColor Cyan

# Ensure output directory exists
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$jvmArgs = "--enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang.foreign=ALL-UNNAMED -Xmx8g -Dlogback.configurationFile=logback-bench.xml"

$runnerClass = "com.spectrayan.spector.bench.cognitive.longmemeval.LongMemEvalNaturalRunner"

Write-Host "Starting MindSpan Natural Runner..." -ForegroundColor Green
mvn exec:java -pl bench/spector-bench `
    -Dexec.mainClass="$runnerClass" `
    -Dexec.jvmArgs="$jvmArgs" `
    -DdatasetDir="$DatasetDir" `
    -DoutputDir="$OutputDir" `
    -DgeminiApiKey="$GeminiApiKey" `
    -DgeminiModel="$GeminiModel" `
    -DtopK="$TopK" `
    -DsessionBatchSize="$SessionBatchSize" `
    -DsmokeTestOnly="$($SmokeTestOnly.IsPresent.ToString().ToLower())" `
    -DsmokeTestLimit="$SmokeTestLimit"

Write-Host "`nMindSpan Benchmark execution complete! Results saved in $OutputDir" -ForegroundColor Green
