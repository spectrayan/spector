<#
.SYNOPSIS
    Run BEIR benchmark in all 3 search modes and compare results.
.DESCRIPTION
    Runs VECTOR_ONLY, KEYWORD_ONLY, and HYBRID modes sequentially,
    then prints a comparison table. Supports both COGNITIVE and SIMILARITY
    scoring modes for A/B comparison.
.EXAMPLE
    # Compare all search modes with SIMILARITY scoring (pure retrieval)
    .\benchmark-compare.ps1 -ScoringMode SIMILARITY

    # Compare all search modes with both scoring modes
    .\benchmark-compare.ps1 -ScoringMode BOTH

    # Use a specific config (e.g., bge-large)
    .\benchmark-compare.ps1 -Config spector-bench-bge.yml -ScoringMode SIMILARITY
#>
param(
    [string]$Dataset     = "scifact",
    [string]$Config      = "spector-bench.yml",
    [int]$TopK           = 10,
    [int]$MaxQueries     = 0,       # 0 = all queries
    [string]$ScoringMode = "BOTH",  # COGNITIVE, SIMILARITY, or BOTH
    [switch]$SkipIngest             # Skip ingestion (data already loaded)
)

$searchModes = @("VECTOR_ONLY", "KEYWORD_ONLY", "HYBRID")

# Determine which scoring modes to run
$scoringModes = switch ($ScoringMode.ToUpper()) {
    "BOTH"       { @("SIMILARITY", "COGNITIVE") }
    "SIMILARITY" { @("SIMILARITY") }
    "COGNITIVE"  { @("COGNITIVE") }
    default      { @("SIMILARITY") }
}

$results = @{}
$firstRun = $true

foreach ($scoring in $scoringModes) {
    foreach ($search in $searchModes) {
        $key = "$scoring/$search"
        Write-Host "`n$("=" * 64)" -ForegroundColor Magenta
        Write-Host "  $key" -ForegroundColor Magenta
        Write-Host ("=" * 64) -ForegroundColor Magenta

        $ingestFlag = if ($firstRun -and -not $SkipIngest) { @() } else { @("-SkipIngest") }
        $queryFlag  = if ($MaxQueries -gt 0) { @("-MaxQueries", $MaxQueries) } else { @() }

        & "$PSScriptRoot\benchmark-beir.ps1" `
            -Dataset $Dataset -Config $Config `
            -TopK $TopK -SearchMode $search -ScoringMode $scoring `
            @ingestFlag @queryFlag

        $firstRun = $false  # Only ingest on the first run

        # Read results file
        $resFile = Join-Path "datasets" "$Dataset-results-$($search.ToLower()).json"
        if (Test-Path $resFile) {
            $results[$key] = Get-Content $resFile -Raw | ConvertFrom-Json
        }
    }
}

# ═══════════════════════════════════════════════════
#  COMPARISON TABLE
# ═══════════════════════════════════════════════════
$configName = [System.IO.Path]::GetFileNameWithoutExtension($Config)
Write-Host "`n" -NoNewline
Write-Host ("=" * 80) -ForegroundColor Cyan
Write-Host "  BENCHMARK COMPARISON — $Dataset [$configName] (top_k=$TopK)" -ForegroundColor Cyan
Write-Host ("=" * 80) -ForegroundColor Cyan

$header = "{0,-25} {1,10} {2,12} {3,10} {4,10} {5,12}" -f "Scoring/Search", "nDCG@$TopK", "Recall@$TopK", "MRR@$TopK", "Hit Rate", "Latency(ms)"
Write-Host ""
Write-Host $header -ForegroundColor White
Write-Host ("-" * 80) -ForegroundColor Gray

# Track best nDCG for highlighting
$bestNdcg = 0
$bestKey = ""
foreach ($key in $results.Keys) {
    $m = $results[$key].metrics
    $ndcg = [double]$m."nDCG@$TopK"
    if ($ndcg -gt $bestNdcg) { $bestNdcg = $ndcg; $bestKey = $key }
}

foreach ($scoring in $scoringModes) {
    foreach ($search in $searchModes) {
        $key = "$scoring/$search"
        if ($results.ContainsKey($key)) {
            $m = $results[$key].metrics
            $row = "{0,-25} {1,10} {2,12} {3,10} {4,9}% {5,12}" -f $key,
                $m."nDCG@$TopK", $m."Recall@$TopK", $m."MRR@$TopK",
                $m.hitRate, $m.avgLatencyMs
            $color = if ($key -eq $bestKey) { "Green" }
                     elseif ($search -eq "HYBRID") { "Yellow" }
                     else { "White" }
            Write-Host $row -ForegroundColor $color
        } else {
            Write-Host ("{0,-25} {1}" -f $key, "NO RESULTS") -ForegroundColor Red
        }
    }
    if ($scoringModes.Count -gt 1) {
        Write-Host ("-" * 80) -ForegroundColor DarkGray
    }
}
Write-Host ("-" * 80) -ForegroundColor Gray

# Best result callout
if ($bestKey) {
    Write-Host ""
    Write-Host "  🏆 Best: $bestKey — nDCG@$TopK = $bestNdcg" -ForegroundColor Green
    Write-Host ""
}
