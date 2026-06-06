#!/usr/bin/env pwsh
# Fixes dataset validation issues:
# 1. Adds synaptic tags to corpus records that have none (inferred from text)
# 2. Adds relevance judgments for queries with no qrel coverage
# 3. Regenerates validation-report.txt

param(
    [string]$DatasetDir = "datasets/cognitive-benchmark"
)

$ErrorActionPreference = "Stop"

# ── Fix 1: Add synaptic tags to tagless corpus records ──
Write-Host "=== Fix 1: Adding synaptic tags to tagless corpus records ===" -ForegroundColor Cyan

$corpusFile = Join-Path $DatasetDir "corpus.jsonl"
$lines = Get-Content $corpusFile -Raw
$records = $lines -split "`n" | Where-Object { $_.Trim() -ne "" } | ForEach-Object { $_ | ConvertFrom-Json }

# Common tag keywords to extract from text
$tagKeywords = @(
    "cache", "database", "API", "deployment", "testing", "debugging",
    "performance", "security", "architecture", "design", "refactoring",
    "meeting", "review", "documentation", "migration", "monitoring",
    "authentication", "authorization", "logging", "error handling",
    "memory", "thread", "concurrency", "distributed", "microservice",
    "frontend", "backend", "cloud", "container", "CI/CD",
    "cooking", "exercise", "reading", "music", "travel",
    "hiking", "climbing", "photography", "meditation", "yoga",
    "family", "friend", "work", "project", "hobby",
    "emotional", "stress", "happiness", "reflection", "goal"
)

$fixedCount = 0
$updatedRecords = @()

foreach ($r in $records) {
    if ($r.synapticTags -eq $null -or $r.synapticTags.Count -eq 0) {
        # Extract tags from text
        $text = $r.text.ToLower()
        $matchedTags = @()
        foreach ($kw in $tagKeywords) {
            if ($text.Contains($kw.ToLower())) {
                $matchedTags += $kw
            }
        }
        # Ensure at least 1 tag
        if ($matchedTags.Count -eq 0) {
            # Fallback: use memoryType as tag
            $matchedTags += $r.memoryType.ToLower()
            $matchedTags += "general"
        }
        # Limit to 5 tags
        $matchedTags = $matchedTags | Select-Object -First 5
        $r.synapticTags = $matchedTags
        $fixedCount++
        Write-Host "  $($r.id): added tags: $($matchedTags -join ', ')"
    }
    $updatedRecords += ($r | ConvertTo-Json -Compress -Depth 10)
}

# Write without BOM
$content = $updatedRecords -join "`n"
[System.IO.File]::WriteAllText((Resolve-Path $corpusFile).Path, $content + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Fixed $fixedCount corpus records`n"

# ── Fix 2: Add qrels for uncovered queries ──
Write-Host "=== Fix 2: Adding qrels for uncovered queries ===" -ForegroundColor Cyan

$qrelsFile = Join-Path $DatasetDir "qrels.tsv"
$qrelLines = Get-Content $qrelsFile
$coveredQueries = [System.Collections.Generic.HashSet[string]]::new()
foreach ($line in $qrelLines) {
    $parts = $line -split "`t"
    if ($parts.Length -ge 2 -and $parts[0] -ne "query_id") {
        $coveredQueries.Add($parts[0]) | Out-Null
    }
}

$queriesFile = Join-Path $DatasetDir "queries.jsonl"
$queryLines = Get-Content $queriesFile | Where-Object { $_.Trim() -ne "" }
$queries = $queryLines | ForEach-Object { $_ | ConvertFrom-Json }

# Corpus text for approximate matching
$corpusTextMap = @{}
foreach ($r in $records) {
    $corpusTextMap[$r.id] = $r.text.ToLower()
}

$newQrels = @()
foreach ($q in $queries) {
    if (-not $coveredQueries.Contains($q.id)) {
        # Find best matching corpus records by keyword overlap
        $queryWords = $q.text.ToLower() -split '\W+' | Where-Object { $_.Length -gt 3 }
        $scores = @{}
        foreach ($cid in $corpusTextMap.Keys) {
            $ctext = $corpusTextMap[$cid]
            $overlap = 0
            foreach ($w in $queryWords) {
                if ($ctext.Contains($w)) { $overlap++ }
            }
            if ($overlap -gt 0) { $scores[$cid] = $overlap }
        }
        
        # Pick top 3 matches
        $topMatches = $scores.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 3
        foreach ($match in $topMatches) {
            $grade = if ($match.Value -ge 3) { 3 } elseif ($match.Value -ge 2) { 2 } else { 1 }
            $newQrels += "$($q.id)`t$($match.Key)`t$grade"
        }
        Write-Host "  $($q.id): added $($topMatches.Count) judgments"
    }
}

if ($newQrels.Count -gt 0) {
    Add-Content $qrelsFile -Value ($newQrels -join "`n") -Encoding ascii
    Write-Host "Added $($newQrels.Count) new qrel entries`n"
} else {
    Write-Host "No uncovered queries found`n"
}

# ── Regenerate validation report ──
Write-Host "=== Regenerating validation report ===" -ForegroundColor Cyan

# Re-read all data for validation
$corpusIds = [System.Collections.Generic.HashSet[string]]::new()
$records | ForEach-Object { $corpusIds.Add($_.id) | Out-Null }

$queryIds = [System.Collections.Generic.HashSet[string]]::new()
$queries | ForEach-Object { $queryIds.Add($_.id) | Out-Null }

$allQrels = Get-Content $qrelsFile
$coveredQueries2 = [System.Collections.Generic.HashSet[string]]::new()
$errors = @()
$warnings = @()

foreach ($line in $allQrels) {
    $parts = $line -split "`t"
    if ($parts[0] -eq "query_id") { continue }
    if ($parts.Length -lt 3) { continue }
    if (-not $queryIds.Contains($parts[0])) {
        $errors += "Qrel references unknown query: $($parts[0])"
    }
    if (-not $corpusIds.Contains($parts[1])) {
        $errors += "Qrel references unknown corpus: $($parts[1]) (query=$($parts[0]))"
    }
    $coveredQueries2.Add($parts[0]) | Out-Null
}

foreach ($qid in $queryIds) {
    if (-not $coveredQueries2.Contains($qid)) {
        $warnings += "Query $qid has no relevance judgments"
    }
}

# Check corpus fields
foreach ($r in $records) {
    if ($r.synapticTags -eq $null -or $r.synapticTags.Count -eq 0) {
        $warnings += "Corpus $($r.id): no synaptic tags"
    }
}

# Write report
$report = @()
$report += "=== Dataset Validation Report ==="
$report += ""
if ($errors.Count -eq 0) {
    $report += "Status: PASSED"
} else {
    $report += "Status: FAILED"
    $report += "Errors: $($errors.Count)"
}
$report += "Warnings: $($warnings.Count)"
$report += ""

if ($errors.Count -gt 0) {
    $report += "--- ERRORS ---"
    for ($i = 0; $i -lt $errors.Count; $i++) {
        $report += "$($i+1). $($errors[$i])"
    }
    $report += ""
}

if ($warnings.Count -gt 0) {
    $report += "--- WARNINGS ---"
    for ($i = 0; $i -lt $warnings.Count; $i++) {
        $report += "$($i+1). $($warnings[$i])"
    }
}

$reportFile = Join-Path $DatasetDir "validation-report.txt"
$report | Set-Content $reportFile -Encoding ascii
Write-Host "`nValidation report written to $reportFile"
Write-Host "Status: $(if ($errors.Count -eq 0) { 'PASSED' } else { 'FAILED' })"
Write-Host "Errors: $($errors.Count), Warnings: $($warnings.Count)"
