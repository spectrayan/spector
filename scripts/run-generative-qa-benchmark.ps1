param(
    [string]$Dataset = "locomo",
    [int]$Limit = 0,
    [switch]$All,
    [int]$TopK = 10,
    [int]$DelayMs = 500,
    [string]$GeneratorModel = "llama3.1:latest",
    [string]$JudgeModel = "",
    [switch]$EnableReranker,
    [switch]$DisableMmr,
    [string]$TextSearchMode = "HYBRID",
    [switch]$Fresh
)

if ($All) {
    $Limit = 0
}

if (-not $JudgeModel) {
    $JudgeModel = $GeneratorModel
}

$LimitDisplay = if ($Limit -le 0) { "ALL (Entire Dataset)" } else { "$Limit" }
$MmrEnabled = (-not $DisableMmr)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SpectorRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$DatasetsBase = (Resolve-Path (Join-Path $SpectorRoot "../spector-datasets")).Path
$DatasetDir = Join-Path $DatasetsBase "$Dataset/data"
$OutputDir = Join-Path $DatasetsBase "$Dataset/results"
$CandidatesFile = Join-Path $OutputDir "retrieved_candidates.jsonl"
$CheckpointFile = Join-Path $OutputDir "qa_eval_checkpoint.jsonl"

if ($Fresh -and (Test-Path $CheckpointFile)) {
    Remove-Item $CheckpointFile -Force -ErrorAction SilentlyContinue
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Spector Memory -- Generative QA (J-Score) Benchmark Pipeline" -ForegroundColor Cyan
Write-Host " Dataset:          $Dataset" -ForegroundColor Yellow
Write-Host " Query Limit:      $LimitDisplay" -ForegroundColor Yellow
Write-Host " Top-K Candidates: $TopK" -ForegroundColor Yellow
Write-Host " MMR Diversity:    $MmrEnabled" -ForegroundColor Yellow
Write-Host " Reranker Mode:    $EnableReranker ($TextSearchMode)" -ForegroundColor Yellow
Write-Host " Pacing Delay:     $DelayMs ms" -ForegroundColor Yellow
Write-Host " Generator Model:  $GeneratorModel" -ForegroundColor Yellow
Write-Host " Judge Model:      $JudgeModel" -ForegroundColor Yellow
Write-Host " Output Dir:       $OutputDir" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

# Step 1: Export Context via Maven
Write-Host "`n[Step 1/2] Exporting Spector Memory Context and Pure Recall Latencies..." -ForegroundColor Green
$MavenArgs = @(
    "test",
    "-pl", "bench/spector-bench",
    "-am",
    "-o",
    "-Dtest=ContextExportTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-DskipBenchTests=false",
    "-Ddataset=$Dataset",
    "-DtopK=$TopK",
    "-Dlimit=$Limit",
    "-DenableMmr=$($MmrEnabled.ToString().ToLower())",
    "-DenableReranker=$($EnableReranker.IsPresent.ToString().ToLower())",
    "-DtextSearchMode=$TextSearchMode",
    "-DoutputFile=$CandidatesFile"
)

Push-Location $SpectorRoot
try {
    & mvn $MavenArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Context export failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $CandidatesFile)) {
    Write-Error "Failed to generate candidates file: $CandidatesFile"
}

# Step 2: Run Generative QA Evaluation Harness
Write-Host "`n[Step 2/2] Running Local Ollama QA Generation and LLM-as-a-Judge..." -ForegroundColor Green
$EvalScript = Join-Path $ScriptDir "eval_generative_qa_ollama.py"

$PythonArgs = @(
    $EvalScript,
    "--candidates-file", $CandidatesFile,
    "--output-dir", $OutputDir,
    "--generator-model", $GeneratorModel,
    "--judge-model", $JudgeModel,
    "--top-k-context", "$TopK",
    "--delay-ms", "$DelayMs",
    "--limit", "$Limit"
)

if ($Fresh) {
    $PythonArgs += "--fresh"
}

& python $PythonArgs

Write-Host "`nGenerative QA Evaluation Finished Successfully!" -ForegroundColor Cyan
