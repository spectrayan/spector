# Spector Memory Re-ingestion Pipeline
# Re-ingests curated memories sequentially with tag curation, log checks, and retries.

param(
    [string]$ExportPath = "d:\git\spector\exports\memories_export_full.json",
    [string]$BaseUrl = "http://localhost:7070",
    [string]$ApiKey = "spector-dev-key",
    [int]$DelayBetweenMemoriesMs = 2000,
    [int]$MaxRetries = 3,
    [int]$StartIndex = 0,
    [int]$MaxRecords = 0
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ExportPath)) {
    Write-Error "Export file not found: $ExportPath"
    exit 1
}

$rawJson = Get-Content -Path $ExportPath -Raw -Encoding UTF8
$memories = $rawJson | ConvertFrom-Json

$total = $memories.Count
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Spector Cognitive Memory Re-ingestion Pipeline" -ForegroundColor Cyan
Write-Host "  Total memories in archive: $total" -ForegroundColor Cyan
Write-Host "  Starting from index:       $StartIndex" -ForegroundColor Cyan
Write-Host "  Pacing delay:              ${DelayBetweenMemoriesMs}ms" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Function to clean and curate synaptic tags
function Curate-Tags($rawTags, $text, $tier) {
    $curated = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    
    if ($rawTags) {
        foreach ($t in $rawTags) {
            if ($t) {
                $clean = $t.ToString().Trim().ToLowerInvariant() -replace '^[#@\s,]+', '' -replace '[#\s,]+$', ''
                if ($clean.Length -ge 2 -and $clean.Length -le 40 -and $clean -notmatch '^(null|undefined|none|test)$') {
                    [void]$curated.Add($clean)
                }
            }
        }
    }
    
    # If tags are too sparse (< 2), extract relevant domain tags from text
    if ($curated.Count -lt 2 -and $text) {
        $lowerText = $text.ToLowerInvariant()
        $keywordMap = @{
            "bloom" = "bloom-app"; "adhd" = "adhd-care"; "autism" = "autism-support"; "clinical" = "clinical-care"
            "spector" = "spector"; "hebbian" = "hebbian-graph"; "synapse" = "spector-synapse"; "cortex" = "cortex"
            "panama" = "java-panama"; "simd" = "simd-acceleration"; "vector" = "vector-search"; "mcp" = "mcp"
            "agent" = "autonomous-agents"; "memory" = "cognitive-memory"; "consolidation" = "consolidation"
            "pull request" = "pull-request"; "adr" = "architectural-decision"; "spring" = "spring-boot"
        }
        foreach ($k in $keywordMap.Keys) {
            if ($lowerText.Contains($k)) {
                [void]$curated.Add($keywordMap[$k])
            }
        }
    }
    
    return [string[]]$curated
}

$headers = @{
    "Authorization" = "Bearer $ApiKey"
    "Accept"        = "application/json"
}

$successCount = 0
$failCount = 0
$startTime = [System.DateTime]::UtcNow
$endIndex = if ($MaxRecords -gt 0) { [Math]::Min($StartIndex + $MaxRecords, $total) } else { $total }

Write-Host "`nStarting ingestion of $($endIndex - $StartIndex) memories..." -ForegroundColor Yellow

for ($i = $StartIndex; $i -lt $endIndex; $i++) {
    $m = $memories[$i]
    $idx1 = $i + 1
    
    $tier = if ($m.tier) { $m.tier } else { "SEMANTIC" }
    $source = if ($m.source) { $m.source } else { "OBSERVED" }
    $importance = if ($m.importance -ne $null) { [float]$m.importance } else { 5.0 }
    $valence = if ($m.valence -ne $null) { [int]$m.valence } else { 0 }
    $arousal = if ($m.arousal -ne $null) { [int]$m.arousal } else { 0 }
    $timestampMs = if ($m.timestampMs -ne $null -and [long]$m.timestampMs -gt 0) { [long]$m.timestampMs } else { 0 }
    
    $curatedTags = Curate-Tags $m.tags $m.text $tier
    $tagsString = if ($curatedTags -and $curatedTags.Length -gt 0) { ($curatedTags -join ", ") } else { "" }
    
    $payloadObj = [ordered]@{
        id          = $m.id
        text        = $m.text
        tier        = $tier
        source      = $source
        tags        = $tagsString
        importance  = $importance
        valence     = $valence
        arousal     = $arousal
        timestampMs = $timestampMs
    }
    
    $payloadJson = $payloadObj | ConvertTo-Json -Compress
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
    
    $attempt = 0
    $ingested = $false
    
    while (-not $ingested -and $attempt -lt $MaxRetries) {
        $attempt++
        try {
            $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/remember" `
                -Headers $headers `
                -ContentType "application/json; charset=utf-8" `
                -Method Post `
                -Body $payloadBytes `
                -TimeoutSec 60
            
            if ($resp -and ($resp.id -or $resp.status -eq "accepted" -or $resp.status -eq "success")) {
                $ingested = $true
                $successCount++
                
                $pct = [Math]::Round(($idx1 / $endIndex) * 100, 1)
                Write-Host "[$idx1/$endIndex - $pct%] Memory $($m.id) [$tier] -> $tagsString" -ForegroundColor Green
            } else {
                throw "Unexpected response: $($resp | ConvertTo-Json -Compress)"
            }
        }
        catch {
            Write-Host "[$idx1/$endIndex] [Attempt $attempt/$MaxRetries FAILED] $($m.id): $($_.Exception.Message)" -ForegroundColor Yellow
            if ($attempt -lt $MaxRetries) {
                $backoffSec = [Math]::Pow(2, $attempt)
                Write-Host "  Backing off for $backoffSec seconds..." -ForegroundColor Gray
                Start-Sleep -Seconds $backoffSec
            } else {
                $failCount++
                Write-Host "[$idx1/$endIndex] [PERMANENT FAILURE] $($m.id)" -ForegroundColor Red
            }
        }
    }
    
    if ($DelayBetweenMemoriesMs -gt 0) {
        Start-Sleep -Milliseconds $DelayBetweenMemoriesMs
    }
}

$elapsed = [System.DateTime]::UtcNow - $startTime

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  Re-ingestion Completed in $($elapsed.ToString('hh\:mm\:ss'))" -ForegroundColor Cyan
Write-Host "  Successfully Ingested: $successCount / $endIndex" -ForegroundColor Green
Write-Host "  Failed:                $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Green" })
Write-Host "==========================================================" -ForegroundColor Cyan

Write-Host "`n=== Triggering Offline Graph Enrichment ===" -ForegroundColor Magenta
try {
    $trigger = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/enrich-graph?limit=100" -Headers $headers -Method Post
    Write-Host "Graph enricher triggered: $($trigger.message)" -ForegroundColor Green
    
    $status = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/enrich-graph/status" -Headers $headers -Method Get
    Write-Host "Enrichment status: Total=$($status.totalMemories), Enriched=$($status.enrichedMemories), Pending=$($status.pendingMemories), InProgress=$($status.inProgress)" -ForegroundColor Cyan
} catch {
    Write-Host "Graph enricher trigger notice: $($_.Exception.Message)" -ForegroundColor Yellow
}
