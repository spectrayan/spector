#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector Cognitive Dataset Generator
#  Uses Ollama to generate a full cognitive benchmark dataset
#  from a persona definition and optional seed corpus.
# ═══════════════════════════════════════════════════════════════

param(
    [string]$Persona    = "datasets/cognitive-benchmark/persona.json",
    [string]$Output     = "datasets/cognitive-benchmark",
    [string]$OllamaUrl  = "http://localhost:11434",
    [string]$Model      = "llama3.1",
    [string]$AnnotationModel = "llama3.2",
    [int]$CorpusSize    = 5000,
    [int]$NumDays       = 180,
    [int]$ConversationsPerDay = 15,
    [int]$BiographicalDepth   = 15,
    [int]$MaxRetries    = 3,
    [string]$Seed       = "",        # Path to seed corpus directory (optional)
    [string]$Approved   = "",        # Path to approved corpus for incremental generation
    [int]$HeapMb        = 4096,
    [switch]$SkipBuild,
    [switch]$Validate       # Only run validation on existing output (skip generation)
)

$ErrorActionPreference = "Stop"

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector Cognitive Dataset Generator" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Resolve paths ──
$projectRoot = Split-Path -Parent $PSScriptRoot
$benchModule = Join-Path $projectRoot "bench/spector-bench"

$resolvedPersona  = if ([System.IO.Path]::IsPathRooted($Persona)) { $Persona } else { Join-Path $projectRoot $Persona }
$resolvedOutput   = if ([System.IO.Path]::IsPathRooted($Output))  { $Output  } else { Join-Path $projectRoot $Output  }
$resolvedSeed     = if ($Seed -and ![System.IO.Path]::IsPathRooted($Seed)) { Join-Path $projectRoot $Seed } else { $Seed }
$resolvedApproved = if ($Approved -and ![System.IO.Path]::IsPathRooted($Approved)) { Join-Path $projectRoot $Approved } else { $Approved }

# Validate persona exists
if (!(Test-Path $resolvedPersona)) {
    Write-Host "ERROR: Persona file not found: $resolvedPersona" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Create a persona.json file with:" -ForegroundColor Yellow
    Write-Host '  {' -ForegroundColor Gray
    Write-Host '    "name": "Jordan Chen",' -ForegroundColor Gray
    Write-Host '    "age": 32,' -ForegroundColor Gray
    Write-Host '    "occupation": "Senior Software Engineer",' -ForegroundColor Gray
    Write-Host '    "interests": ["distributed systems", "climbing", "cooking", "sci-fi", "guitar"],' -ForegroundColor Gray
    Write-Host '    "life_context": "Works at a fintech startup... (50-2000 chars)",' -ForegroundColor Gray
    Write-Host '    "personality_traits": ["analytical", "curious", "empathetic"],' -ForegroundColor Gray
    Write-Host '    "companion_relationship": "Has been using the AI... (50-500 chars)"' -ForegroundColor Gray
    Write-Host '  }' -ForegroundColor Gray
    exit 1
}

# Check Ollama availability
Write-Host ""
Write-Host "  Checking Ollama at $OllamaUrl..." -ForegroundColor Yellow
try {
    $ollamaResp = Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 5 -ErrorAction SilentlyContinue
    $models = $ollamaResp.models | ForEach-Object { $_.name }
    Write-Host "  Ollama available ($($models.Count) models)" -ForegroundColor Green
    
    if ($models -notcontains $Model -and $models -notcontains "${Model}:latest") {
        Write-Host "  WARNING: Model '$Model' not found. Available: $($models -join ', ')" -ForegroundColor Yellow
        Write-Host "  Pull it with: ollama pull $Model" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR: Cannot reach Ollama at $OllamaUrl" -ForegroundColor Red
    Write-Host "  Start Ollama with: ollama serve" -ForegroundColor Yellow
    Write-Host "  Or specify a different URL: -OllamaUrl http://host:port" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "  Configuration:" -ForegroundColor White
Write-Host "    Persona:      $resolvedPersona" -ForegroundColor Gray
Write-Host "    Output:       $resolvedOutput" -ForegroundColor Gray
Write-Host "    Model:        $Model" -ForegroundColor Gray
Write-Host "    Ann. Model:   $AnnotationModel" -ForegroundColor Gray
Write-Host "    Corpus size:  $CorpusSize" -ForegroundColor Gray
Write-Host "    Days:         $NumDays" -ForegroundColor Gray
Write-Host "    Conv/day:     $ConversationsPerDay" -ForegroundColor Gray
Write-Host "    Bio depth:    ${BiographicalDepth} years" -ForegroundColor Gray
if ($resolvedSeed)     { Write-Host "    Seed:         $resolvedSeed" -ForegroundColor Gray }
if ($resolvedApproved) { Write-Host "    Approved:     $resolvedApproved" -ForegroundColor Gray }
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

# Resolve full classpath
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

# ── JVM arguments ──
$jvmArgs = @(
    "--enable-preview"
    "--add-modules", "jdk.incubator.vector"
    "--enable-native-access=ALL-UNNAMED"
    "-Xmx${HeapMb}m"
    "-cp", $classpath
)

# ── Generator arguments ──
$genArgs = @(
    "--persona=$resolvedPersona"
    "--output=$resolvedOutput"
    "--ollama-url=$OllamaUrl"
    "--model=$Model"
    "--annotation-model=$AnnotationModel"
    "--corpus-size=$CorpusSize"
    "--num-days=$NumDays"
    "--conversations-per-day=$ConversationsPerDay"
    "--biographical-depth=$BiographicalDepth"
    "--max-retries=$MaxRetries"
)
if ($resolvedSeed)     { $genArgs += "--seed=$resolvedSeed" }
if ($resolvedApproved) { $genArgs += "--approved=$resolvedApproved" }

# ── Run generator ──
Write-Host "── Starting Dataset Generation ──" -ForegroundColor Cyan
Write-Host "  This may take 30-60 minutes depending on corpus size and model speed." -ForegroundColor Yellow
Write-Host ""

$mainClass = "com.spectrayan.spector.bench.cognitive.generator.DatasetGeneratorMain"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

& java @jvmArgs $mainClass @genArgs
$exitCode = $LASTEXITCODE

$sw.Stop()
$elapsed = [math]::Round($sw.Elapsed.TotalMinutes, 1)

Write-Host ""
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Interpret exit code ──
switch ($exitCode) {
    0 { Write-Host "  SUCCESS - Dataset generated and validated" -ForegroundColor Green }
    1 { Write-Host "  WARN - Dataset generated but validation failed (see report)" -ForegroundColor Yellow }
    2 { Write-Host "  ERROR - Generation failed (partial output may be available)" -ForegroundColor Red }
    3 { Write-Host "  ERROR - Configuration/setup error" -ForegroundColor Red }
    default { Write-Host "  Unknown exit code: $exitCode" -ForegroundColor Red }
}

Write-Host "  Elapsed: ${elapsed} minutes" -ForegroundColor White
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Show output files ──
if (Test-Path $resolvedOutput) {
    Write-Host ""
    Write-Host "  Generated files:" -ForegroundColor Gray
    Get-ChildItem $resolvedOutput -File | ForEach-Object {
        $size = if ($_.Length -gt 1MB) { "$([math]::Round($_.Length / 1MB, 1)) MB" } else { "$([math]::Round($_.Length / 1KB, 1)) KB" }
        Write-Host "    $($_.Name) ($size)" -ForegroundColor Gray
    }
    
    # Show corpus stats
    $corpusFile = Join-Path $resolvedOutput "corpus.jsonl"
    if (Test-Path $corpusFile) {
        $lineCount = (Get-Content $corpusFile | Measure-Object).Count
        Write-Host ""
        Write-Host "  Corpus: $lineCount memories" -ForegroundColor White
    }
    $queriesFile = Join-Path $resolvedOutput "queries.jsonl"
    if (Test-Path $queriesFile) {
        $queryCount = (Get-Content $queriesFile | Measure-Object).Count
        Write-Host "  Queries: $queryCount" -ForegroundColor White
    }
}

# ── Next steps ──
if ($exitCode -eq 0) {
    Write-Host ""
    Write-Host "  Next step - run the benchmark:" -ForegroundColor Yellow
    Write-Host "    .\scripts\cognitive-benchmark.ps1 -DatasetDir $resolvedOutput" -ForegroundColor Yellow
}

exit $exitCode
