param(
    [string]$Dataset = "locomo",
    [int]$Limit = 0,
    [switch]$All,
    [int]$TopK = 15,
    [int]$DelayMs = 500,
    [string]$GeneratorModel = "llama3.1:latest",
    [string]$JudgeModel = "",
    [string]$Provider = "auto",
    [string]$GeminiApiKey = "",
    [string]$GraphExpansionMode = "ALWAYS",
    [float]$GraphExpansionThreshold = 0.85,
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

if (-not $GeminiApiKey) {
    $GeminiApiKey = if ($env:GEMINI_API_KEY) { $env:GEMINI_API_KEY } elseif ($env:GOOGLE_API_KEY) { $env:GOOGLE_API_KEY } else { "" }
}

$IsGemini = ($Provider -eq "gemini") -or ($Provider -eq "auto" -and ($GeneratorModel -match "gemini" -or $JudgeModel -match "gemini"))

if ($IsGemini -and -not $GeminiApiKey) {
    Write-Host ""
    Write-Host "[ERROR] Gemini model configured (-GeneratorModel '$GeneratorModel'), but no Gemini API key was found." -ForegroundColor Red
    Write-Host "Please provide the API key using one of the following options:" -ForegroundColor Yellow
    Write-Host "  1. Pass the parameter:   .\scripts\run-generative-qa-benchmark.ps1 -GeneratorModel '$GeneratorModel' -GeminiApiKey 'AIzaSy...'" -ForegroundColor White
    Write-Host "  2. Set environment var:  `$env:GEMINI_API_KEY = 'AIzaSy...'" -ForegroundColor White
    Write-Host ""
    exit 1
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

if ($Fresh) {
    if (Test-Path $CheckpointFile) {
        Remove-Item $CheckpointFile -Force -ErrorAction SilentlyContinue
    }
    $IngestedDir = Join-Path $DatasetDir "ingested-memory"
    if (Test-Path $IngestedDir) {
        Remove-Item $IngestedDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$EngineDisplay = if ($Provider -eq "gemini" -or ($Provider -eq "auto" -and ($GeneratorModel -match "gemini" -or $GeminiApiKey))) {
    "Google Gemini REST API"
} else {
    "Local Ollama"
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Spector Memory -- Generative QA (J-Score) Benchmark Pipeline" -ForegroundColor Cyan
Write-Host " Dataset:          $Dataset" -ForegroundColor Yellow
Write-Host " Engine Backend:   $EngineDisplay" -ForegroundColor Yellow
Write-Host " Query Limit:      $LimitDisplay" -ForegroundColor Yellow
Write-Host " Top-K Candidates: $TopK" -ForegroundColor Yellow
Write-Host " MMR Diversity:    $MmrEnabled" -ForegroundColor Yellow
Write-Host " Graph Expansion:  $GraphExpansionMode (Threshold: $GraphExpansionThreshold)" -ForegroundColor Yellow
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
    "-Dspector.memory.graphExpansionMode=$GraphExpansionMode",
    "-Dspector.benchmark.graphExpansionThreshold=$GraphExpansionThreshold",
    "-DgraphExpansionMode=$GraphExpansionMode",
    "-DgraphExpansionThreshold=$GraphExpansionThreshold",
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
Write-Host "`n[Step 2/2] Running Generative QA Evaluation and LLM-as-a-Judge..." -ForegroundColor Green
$EvalScript = Join-Path $ScriptDir "eval_generative_qa_ollama.py"

$PythonArgs = @(
    $EvalScript,
    "--candidates-file", $CandidatesFile,
    "--output-dir", $OutputDir,
    "--provider", $Provider,
    "--generator-model", $GeneratorModel,
    "--judge-model", $JudgeModel,
    "--top-k-context", "$TopK",
    "--delay-ms", "$DelayMs",
    "--limit", "$Limit"
)

if ($GeminiApiKey) {
    $PythonArgs += @("--gemini-api-key", $GeminiApiKey)
}

if ($Fresh) {
    $PythonArgs += "--fresh"
}

& python $PythonArgs

Write-Host "`nGenerative QA Evaluation Finished Successfully!" -ForegroundColor Cyan
