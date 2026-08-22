<#
.SYNOPSIS
    Runs the sequential Ollama LLM dataset synthesizer with retries, checkpointing, and dense embedding pre-caching.
.PARAMETER ChatModel
    Ollama LLM model name (default: llama3.1:latest).
.PARAMETER EmbedModel
    Ollama embedding model name (default: nomic-embed-text:latest).
.PARAMETER TargetRecords
    Target total corpus record count (default: 50000).
.PARAMETER DatasetDir
    Target dataset directory path.
#>
param(
    [string]$ChatModel = $(if ($env:CHAT_MODEL) { $env:CHAT_MODEL } else { "llama3.1:latest" }),
    [string]$EmbedModel = $(if ($env:EMBED_MODEL) { $env:EMBED_MODEL } else { "nomic-embed-text:latest" }),
    [int]$TargetRecords = $(if ($env:TARGET_RECORDS) { [int]$env:TARGET_RECORDS } else { 50000 }),
    [string]$DatasetDir = $(if ($env:DATASET_DIR) { $env:DATASET_DIR } elseif ($env:SPECTOR_BENCH_DATASET_DIR) { $env:SPECTOR_BENCH_DATASET_DIR } else { Join-Path (Split-Path -Parent $PSScriptRoot) "../spector-datasets/balanced-baseline/data" }),
    [string]$OllamaUrl = $(if ($env:OLLAMA_URL) { $env:OLLAMA_URL } else { "http://localhost:11434" })
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedDataset = if ([System.IO.Path]::IsPathRooted($DatasetDir)) { $DatasetDir } else { Join-Path $projectRoot $DatasetDir }

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Spector Ollama Sequential Dataset Synthesizer" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Chat Model:       $ChatModel"
Write-Host " Embed Model:      $EmbedModel"
Write-Host " Target Records:   $TargetRecords"
Write-Host " Dataset Dir:      $resolvedDataset"
Write-Host " Ollama URL:       $OllamaUrl"
Write-Host "------------------------------------------------------------"

# 1. Validate dataset exists
if (!(Test-Path (Join-Path $resolvedDataset "corpus.jsonl"))) {
    Write-Error "[FAIL] Dataset corpus.jsonl not found at $resolvedDataset. Please provide a valid -DatasetDir or set DATASET_DIR environment variable."
    exit 1
}

# 2. Health check Ollama
try {
    $tags = Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 5
    Write-Host "[OK] Connected to Ollama daemon at $OllamaUrl (Found $($tags.models.Count) models)" -ForegroundColor Green
} catch {
    Write-Error "[FAIL] Could not connect to Ollama at $OllamaUrl. Please start Ollama before running."
    exit 1
}

# 3. Execute runner via Surefire
Write-Host "[INFO] Launching OllamaDatasetSynthesizerRunner via Maven..." -ForegroundColor Green
Set-Location $projectRoot
$env:OLLAMA_LIVE = "true"
mvn test -pl bench/spector-bench `
    -Dtest=OllamaDatasetSynthesizerRunnerTest `
    -DskipBenchTests=false `
    "-DdatasetDir=$resolvedDataset" `
    "-DchatModel=$ChatModel" `
    "-DembedModel=$EmbedModel" `
    "-DollamaUrl=$OllamaUrl" `
    "-DtargetRecords=$TargetRecords"

Write-Host "[DONE] Ollama dataset synthesis finished." -ForegroundColor Cyan