# Run the cognitive benchmark harness with the full classpath
param(
    [string]$DatasetDir = "datasets\cognitive-benchmark",
    [string]$OutputDir = "target\benchmark-results",
    [string]$Profile = ""
)

# Build classpath from Maven dependency:build-classpath output
$depCp = Get-Content "bench\spector-bench\target\bench-cp.txt" -ErrorAction Stop

# Collect all target/classes directories from known modules
$modules = @(
    "bench\spector-bench", "memory\spector-memory", "nucleus\spector-core", "nucleus\spector-commons",
    "nucleus\spector-storage", "memory\spector-index", "memory\spector-embed-api", "memory\spector-embed-ollama",
    "nucleus\spector-config", "memory\spector-ingestion", "nucleus\spector-events", "memory\spector-engine",
    "memory\spector-rag", "memory\spector-gpu", "memory\spector-query", "nucleus\spector-test-support"
)

$modCp = ($modules | ForEach-Object { "$PSScriptRoot\$_\target\classes" } | Where-Object { Test-Path $_ }) -join ";"

$fullCp = "$modCp;$depCp"

$javaArgs = @(
    "--enable-preview",
    "--add-modules", "jdk.incubator.vector",
    "-Xmx28g",
    "-cp", $fullCp,
    "com.spectrayan.spector.bench.cognitive.CognitiveBenchmarkHarness",
    $DatasetDir,
    $OutputDir
)

if ($Profile) {
    $javaArgs += @("", "", $Profile)
}

Write-Host "Running benchmark: dataset=$DatasetDir output=$OutputDir"
& java @javaArgs
Write-Host "Benchmark exited with code: $LASTEXITCODE"
