#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector BEIR Benchmark Harness
#  Ingests corpus → runs queries → evaluates nDCG@10
# ═══════════════════════════════════════════════════════════════

param(
    [string]$Dataset = "scifact",
    [string]$DataDir = "datasets",
    [string]$Config  = "spector-local.yml",
    [string]$Jar     = "synapse/spector-dist/target/spector.jar",
    [int]$TopK       = 10,
    [string]$SearchMode = "HYBRID",  # HYBRID, VECTOR_ONLY, KEYWORD_ONLY
    [string]$ScoringMode = "COGNITIVE",  # COGNITIVE, SIMILARITY
    [switch]$SkipIngest,    # Skip ingestion if corpus already loaded
    [int]$MaxIngest  = 0,   # 0 = ingest all; set to limit for quick tests
    [int]$MaxQueries = 0    # 0 = run all queries; set to limit
)

$ErrorActionPreference = "Stop"

# ── Paths ──
$corpusPath  = Join-Path $DataDir "$Dataset/corpus.jsonl"
$queriesPath = Join-Path $DataDir "$Dataset/queries.jsonl"
$qrelsPath   = Join-Path $DataDir "$Dataset/qrels/test.tsv"

if (!(Test-Path $corpusPath)) {
    Write-Host "ERROR: $corpusPath not found. Download with:" -ForegroundColor Red
    Write-Host "  Invoke-WebRequest 'https://public.ukp.informatik.tu-darmstadt.de/thakur/BEIR/datasets/$Dataset.zip' -OutFile '$Dataset.zip'"
    exit 1
}

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector BEIR Benchmark: $Dataset" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Load qrels ──
Write-Host "`nLoading relevance judgments..." -ForegroundColor Yellow
$qrels = @{}
$qrelsLines = Get-Content $qrelsPath | Select-Object -Skip 1  # skip header
foreach ($line in $qrelsLines) {
    $parts = $line -split "\t"
    $qid = $parts[0]; $did = $parts[1]; $score = [int]$parts[2]
    if ($score -gt 0) {
        if (!$qrels.ContainsKey($qid)) { $qrels[$qid] = @{} }
        $qrels[$qid][$did] = $score
    }
}
Write-Host "  Loaded $($qrels.Count) queries with relevance judgments" -ForegroundColor Green

# ── Load queries ──
$queries = @{}
Get-Content $queriesPath | ForEach-Object {
    $q = $_ | ConvertFrom-Json
    $queries[$q._id] = $q.text
}
Write-Host "  Loaded $($queries.Count) queries total" -ForegroundColor Green

# Filter to queries that have qrels
$evalQueries = $queries.Keys | Where-Object { $qrels.ContainsKey($_) }
if ($MaxQueries -gt 0) { $evalQueries = $evalQueries | Select-Object -First $MaxQueries }
Write-Host "  Evaluating $($evalQueries.Count) queries (with ground truth)" -ForegroundColor Green

# ── Start MCP Server ──
Write-Host "`nStarting Spector MCP server..." -ForegroundColor Yellow
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "--enable-preview --add-modules jdk.incubator.vector -Xmx4g -jar $Jar serve --config $Config"
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.WorkingDirectory = (Get-Location).Path

$proc = [System.Diagnostics.Process]::Start($psi)
Start-Sleep -Seconds 12

# MCP helpers — async read with timeout to prevent hangs
$script:pendingRead = $null  # Track pending ReadLineAsync tasks

function send($json) {
    # If there's a stale pending read from a previous timeout, drain it first
    if ($script:pendingRead -ne $null -and -not $script:pendingRead.IsCompleted) {
        $script:pendingRead.Wait(5000)  # Give it 5s to complete
    }
    $script:pendingRead = $null

    $proc.StandardInput.WriteLine($json)
    $proc.StandardInput.Flush()
    $task = $proc.StandardOutput.ReadLineAsync()
    $script:pendingRead = $task
    if ($task.Wait(120000)) {  # 120 second timeout
        $script:pendingRead = $null
        return $task.Result
    } else {
        Write-Host "  TIMEOUT waiting for MCP response" -ForegroundColor Red
        # Don't null pendingRead — it will be drained on next send()
        return $null
    }
}
function notify($json) {
    $proc.StandardInput.WriteLine($json)
    $proc.StandardInput.Flush()
}

# Initialize MCP
$r = send '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"beir-bench","version":"1.0"}}}'
$init = $r | ConvertFrom-Json
if (!$init.result) {
    Write-Host "  FAILED to initialize MCP!" -ForegroundColor Red
    $proc.Kill(); $proc.Dispose(); exit 1
}
Write-Host "  MCP initialized: $($init.result.serverInfo.name)" -ForegroundColor Green
notify '{"jsonrpc":"2.0","method":"notifications/initialized"}'
Start-Sleep -Seconds 1

$mcpId = 1

# ═══════════════════════════════════════════════════
#  PHASE 1: INGEST CORPUS
# ═══════════════════════════════════════════════════
if (!$SkipIngest) {
    Write-Host "`n── Phase 1: Ingesting corpus ──" -ForegroundColor Cyan
    $corpus = Get-Content $corpusPath
    $total = $corpus.Count
    if ($MaxIngest -gt 0) { $corpus = $corpus | Select-Object -First $MaxIngest; $total = $corpus.Count }
    
    $ingested = 0; $errors = 0; $consecutiveErrors = 0
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    
    foreach ($line in $corpus) {
        # Abort if too many consecutive errors (partition full, embedding down, etc.)
        if ($consecutiveErrors -ge 5) {
            Write-Host "  ABORTING ingestion: $consecutiveErrors consecutive errors (partition may be full)" -ForegroundColor Red
            break
        }

        $doc = $line | ConvertFrom-Json
        $text = if ($doc.title) { "$($doc.title). $($doc.text)" } else { $doc.text }
        # Escape JSON special chars
        $text = $text -replace '\\', '\\\\' -replace '"', '\"' -replace "`n", '\n' -replace "`r", '' -replace "`t", '\t'
        $id = "sf-$($doc._id)"
        
        $mcpId++
        $json = "{`"jsonrpc`":`"2.0`",`"id`":$mcpId,`"method`":`"tools/call`",`"params`":{`"name`":`"memory_remember`",`"arguments`":{`"id`":`"$id`",`"text`":`"$text`",`"tier`":`"SEMANTIC`",`"source`":`"INFERRED`",`"tags`":`"beir,scifact`"}}}"
        
        try {
            $resp = send $json
            if ($null -eq $resp) {
                $errors++; $consecutiveErrors++
                Write-Host "  TIMEOUT on $id - skipping" -ForegroundColor Red
                continue
            }
            $parsed = $resp | ConvertFrom-Json
            if ($parsed.error) {
                $errors++; $consecutiveErrors++
                if ($errors -le 5) { Write-Host "  Error on $id`: $($parsed.error.message)" -ForegroundColor Red }
            } else {
                $consecutiveErrors = 0  # Reset on success
            }
        } catch {
            $errors++; $consecutiveErrors++
        }
        
        $ingested++
        if ($ingested % 10 -eq 0 -or $ingested -eq $total) {
            $rate = [math]::Round($ingested / $sw.Elapsed.TotalSeconds, 1)
            $eta = [math]::Round(($total - $ingested) / $rate / 60, 1)
            Write-Host "  [$ingested/$total] $rate docs/sec, ETA: ${eta}m, errors: $errors" -ForegroundColor Gray
        }
    }
    
    $sw.Stop()
    Write-Host "  Ingested $ingested docs in $([math]::Round($sw.Elapsed.TotalMinutes, 1))m, errors: $errors" -ForegroundColor Green
} else {
    Write-Host "`nPhase 1: SKIPPED (SkipIngest flag set)" -ForegroundColor Yellow
}

# ═══════════════════════════════════════════════════
#  PHASE 2: RUN QUERIES
# ═══════════════════════════════════════════════════
Write-Host "`n── Phase 2: Running $($evalQueries.Count) queries (top_k=$TopK) ──" -ForegroundColor Cyan

$results = @{}
$queryIdx = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()

foreach ($qid in $evalQueries) {
    $queryText = $queries[$qid] -replace '\\', '\\\\' -replace '"', '\"' -replace "`n", '\n' -replace "`r", '' -replace "`t", '\t'
    $mcpId++
    $json = "{`"jsonrpc`":`"2.0`",`"id`":$mcpId,`"method`":`"tools/call`",`"params`":{`"name`":`"memory_recall`",`"arguments`":{`"query`":`"$queryText`",`"top_k`":$TopK,`"tiers`":`"SEMANTIC`",`"text_search_mode`":`"$SearchMode`",`"scoring_mode`":`"$ScoringMode`"}}}"
    
    try {
        $resp = send $json
        $parsed = $resp | ConvertFrom-Json
        if ($parsed.result -and $parsed.result.content) {
            $text = $parsed.result.content[0].text
            # Parse recalled memory IDs from the response
            # Handles both MCP-ingested (ID: sf-12345) and CLI-ingested (ID: sf-12345.txt#chunk-0)
            $recalledIds = @()
            $idMatches = [regex]::Matches($text, 'ID:\s*sf-(\d+)(?:\.txt)?(?:#chunk-\d+)?')
            foreach ($m in $idMatches) {
                $recalledIds += $m.Groups[1].Value
            }
            # Deduplicate (multiple chunks from same doc)
            $results[$qid] = @($recalledIds | Select-Object -Unique)
        }
    } catch {
        $results[$qid] = @()
    }
    
    $queryIdx++
    if ($queryIdx % 20 -eq 0) {
        $rate = [math]::Round($queryIdx / $sw.Elapsed.TotalSeconds, 1)
        Write-Host "  [$queryIdx/$($evalQueries.Count)] $rate q/sec" -ForegroundColor Gray
    }
}

$sw.Stop()
$avgLatency = [math]::Round($sw.Elapsed.TotalMilliseconds / $evalQueries.Count, 0)
Write-Host "  Completed $($evalQueries.Count) queries in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s (avg ${avgLatency}ms/query)" -ForegroundColor Green

# Debug: show results summary
$nonEmpty = @($results.Keys | Where-Object { $results[$_].Count -gt 0 })
Write-Host "  Results: $($results.Count) queries, $($nonEmpty.Count) with non-empty results" -ForegroundColor Yellow
if ($nonEmpty.Count -gt 0) {
    $sampleQid = $nonEmpty[0]
    Write-Host "  Sample q=$sampleQid retrieved: $($results[$sampleQid] -join ',')" -ForegroundColor Yellow
    $rel = $qrels[$sampleQid]
    if ($rel) { Write-Host "  Sample q=$sampleQid expected: $($rel.Keys -join ',')" -ForegroundColor Yellow }
} else {
    Write-Host "  WARNING: No results found for any query!" -ForegroundColor Red
    # Debug first 3 eval queries
    $debugQids = @($evalQueries | Select-Object -First 3)
    foreach ($dq in $debugQids) {
        Write-Host "    q=$dq results_key_exists=$($results.ContainsKey($dq)) qrels_key_exists=$($qrels.ContainsKey($dq))" -ForegroundColor Red
    }
}

# ═══════════════════════════════════════════════════
#  PHASE 3: EVALUATE — nDCG@K, Recall@K, MRR@K
# ═══════════════════════════════════════════════════
Write-Host "`n── Phase 3: Evaluation ──" -ForegroundColor Cyan

function Get-DCG($relevances, $k) {
    $dcg = 0.0
    for ($i = 0; $i -lt [Math]::Min($k, $relevances.Count); $i++) {
        $dcg += $relevances[$i] / ([Math]::Log($i + 2) / [Math]::Log(2))
    }
    return $dcg
}

$ndcgSum = 0.0; $recallSum = 0.0; $mrrSum = 0.0
$evaluated = 0; $hits = 0

$debugCount = 0
foreach ($qid in $evalQueries) {
    $relevant = $qrels[$qid]
    $retrieved = $results[$qid]
    
    # Debug first 5 queries
    if ($debugCount -lt 5) {
        $relKeys = if ($relevant) { $relevant.Keys -join ',' } else { "NULL" }
        $retIds = if ($retrieved) { $retrieved -join ',' } else { "NULL" }
        $overlap = @()
        if ($retrieved -and $relevant) {
            foreach ($rid in $retrieved) {
                if ($relevant.ContainsKey($rid)) { $overlap += $rid }
            }
        }
        Write-Host "  DEBUG q=${qid}: retrieved=[$retIds] expected=[$relKeys] overlap=[$($overlap -join ',')]" -ForegroundColor DarkGray
        $debugCount++
    }
    
    if (!$retrieved -or !$relevant) { continue }
    
    # Build relevance list for retrieved docs
    $rels = @()
    foreach ($did in $retrieved) {
        if ($relevant.ContainsKey($did)) {
            $rels += $relevant[$did]
        } else {
            $rels += 0
        }
    }
    
    # nDCG@K
    $dcg = Get-DCG $rels $TopK
    $idealRels = @($relevant.Values | Sort-Object -Descending)
    $idcg = Get-DCG $idealRels $TopK
    $ndcg = if ($idcg -gt 0) { $dcg / $idcg } else { 0 }
    $ndcgSum += $ndcg
    
    # Recall@K
    $foundRelevant = ($rels | Where-Object { $_ -gt 0 }).Count
    $totalRelevant = $relevant.Count
    $recall = if ($totalRelevant -gt 0) { $foundRelevant / $totalRelevant } else { 0 }
    $recallSum += $recall
    
    # MRR@K
    $mrr = 0.0
    for ($i = 0; $i -lt $rels.Count; $i++) {
        if ($rels[$i] -gt 0) { $mrr = 1.0 / ($i + 1); break }
    }
    $mrrSum += $mrr
    
    if ($ndcg -gt 0) { $hits++ }
    $evaluated++
}

$ndcgAvg = if ($evaluated -gt 0) { [math]::Round($ndcgSum / $evaluated, 4) } else { 0 }
$recallAvg = if ($evaluated -gt 0) { [math]::Round($recallSum / $evaluated, 4) } else { 0 }
$mrrAvg = if ($evaluated -gt 0) { [math]::Round($mrrSum / $evaluated, 4) } else { 0 }
$hitRate = if ($evaluated -gt 0) { [math]::Round($hits / $evaluated * 100, 1) } else { 0 }

# ═══════════════════════════════════════════════════
#  RESULTS
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  BEIR Benchmark Results: $Dataset" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dataset:        $Dataset" -ForegroundColor White
Write-Host "  Search Mode:    $SearchMode" -ForegroundColor White
Write-Host "  Queries eval'd: $evaluated" -ForegroundColor White
Write-Host "  Top-K:          $TopK" -ForegroundColor White
Write-Host "  Avg Latency:    ${avgLatency}ms" -ForegroundColor White
Write-Host ""
Write-Host ("  nDCG@{0}:    {1}" -f $TopK, $ndcgAvg) -ForegroundColor Yellow
Write-Host ("  Recall@{0}:  {1}" -f $TopK, $recallAvg) -ForegroundColor Yellow
Write-Host ("  MRR@{0}:     {1}" -f $TopK, $mrrAvg) -ForegroundColor Yellow
Write-Host ("  Hit Rate:   {0}%" -f $hitRate) -ForegroundColor Yellow
Write-Host ""

# Save results to file
$resultsFile = Join-Path $DataDir "$Dataset-results-$($SearchMode.ToLower()).json"
@{
    dataset = $Dataset
    searchMode = $SearchMode
    timestamp = (Get-Date).ToString("o")
    metrics = @{
        "nDCG@$TopK" = $ndcgAvg
        "Recall@$TopK" = $recallAvg
        "MRR@$TopK" = $mrrAvg
        hitRate = $hitRate
        avgLatencyMs = $avgLatency
    }
    config = @{
        topK = $TopK
        queriesEvaluated = $evaluated
        corpusSize = (Get-Content $corpusPath | Measure-Object).Count
    }
} | ConvertTo-Json -Depth 5 | Set-Content $resultsFile
Write-Host "  Results saved to $resultsFile" -ForegroundColor Gray

# Cleanup
$proc.StandardInput.Close()
Start-Sleep -Seconds 2
if (!$proc.HasExited) { $proc.Kill() }
$proc.Dispose()
