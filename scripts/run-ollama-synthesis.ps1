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
    [string]$ChatModel = "llama3.1:latest",
    [string]$EmbedModel = "nomic-embed-text:latest",
    [int]$TargetRecords = 50000,
    [string]$DatasetDir = "d:\git\spector-datasets\balanced-baseline\data"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Spector Ollama Sequential Dataset Synthesizer" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Chat Model:       $ChatModel"
Write-Host " Embed Model:      $EmbedModel"
Write-Host " Target Records:   $TargetRecords"
Write-Host " Dataset Dir:      $DatasetDir"
Write-Host "------------------------------------------------------------"

# 1. Health check Ollama
try {
    $tags = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -TimeoutSec 5
    Write-Host "[OK] Connected to Ollama daemon (Found $($tags.models.Count) models)" -ForegroundColor Green
} catch {
    Write-Error "[FAIL] Could not connect to Ollama at http://localhost:11434. Please start Ollama before running."
    exit 1
}

# 2. Execute runner via Surefire
Write-Host "[INFO] Launching OllamaDatasetSynthesizerRunner via Maven..." -ForegroundColor Green
Set-Location "d:\git\spector"
mvn test -pl bench/spector-bench `
    -Dtest=OllamaDatasetSynthesizerRunnerTest `
    -DskipBenchTests=false `
    "-DdatasetDir=$DatasetDir" `
    "-DchatModel=$ChatModel" `
    "-DembedModel=$EmbedModel" `
    "-DtargetRecords=$TargetRecords"

Write-Host "[DONE] Ollama dataset synthesis finished." -ForegroundColor Cyan