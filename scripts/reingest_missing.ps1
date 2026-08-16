# scripts/reingest_missing.ps1
# Compares memories_export_full.json against all paginated /api/v1/memory/table records
# and ingests any missing memories with 2000ms delay.
param(
    [string]$BaseUrl = "http://localhost:7070",
    [string]$ApiKey = "spector-dev-key",
    [Parameter(Mandatory=$true)]
    [string]$ExportJsonPath,
    [int]$DelayBetweenMemoriesMs = 2000
)

$headers = @{
    "Authorization" = "Bearer $ApiKey"
    "Content-Type"  = "application/json; charset=utf-8"
}

Write-Host "=== Step 1: Loading Exported Memories ===" -ForegroundColor Cyan
if (-not (Test-Path $ExportJsonPath)) {
    Write-Error "Export file not found: $ExportJsonPath"
    exit 1
}

$rawJson = [System.IO.File]::ReadAllText($ExportJsonPath)
$exportMemories = $rawJson | ConvertFrom-Json
Write-Host "Total exported memories in file: $($exportMemories.Count)" -ForegroundColor Green

Write-Host "`n=== Step 2: Querying All Existing Memories from Spector ===" -ForegroundColor Cyan
$existingIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

$page = 0
$pageSize = 100
while ($true) {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/table?page=$page&pageSize=$pageSize" -Headers $headers -Method Get
    if (-not $resp.rows -or $resp.rows.Count -eq 0) {
        break
    }
    foreach ($m in $resp.rows) {
        if ($m.id) {
            [void]$existingIds.Add($m.id)
        }
    }
    $page++
    if ($page * $pageSize -ge $resp.totalCount) {
        break
    }
}

Write-Host "Found $($existingIds.Count) existing memories in Spector" -ForegroundColor Green

Write-Host "`n=== Step 3: Identifying Missing Memories ===" -ForegroundColor Cyan
$missingMemories = [System.Collections.Generic.List[object]]::new()
foreach ($m in $exportMemories) {
    if (-not $existingIds.Contains($m.id)) {
        $missingMemories.Add($m)
    }
}

Write-Host "Total missing memories to ingest: $($missingMemories.Count)" -ForegroundColor Yellow
if ($missingMemories.Count -eq 0) {
    Write-Host "All $($exportMemories.Count) memories are already present in Spector! Nothing to re-ingest." -ForegroundColor Green
    exit 0
}

Write-Host "`n=== Step 4: Ingesting Missing Memories (Paced at ${DelayBetweenMemoriesMs}ms) ===" -ForegroundColor Cyan
$successCount = 0
$failCount = 0
$idx = 0
$totalMissing = $missingMemories.Count

foreach ($m in $missingMemories) {
    $idx++
    $tier = if ($m.tier) { $m.tier.ToString().ToUpper() } else { "SEMANTIC" }
    
    # Curate tags into comma-separated string
    $cleanTags = @()
    if ($m.tags) {
        foreach ($t in $m.tags) {
            $cleaned = $t.ToString().Trim().ToLower() -replace '[^a-z0-9_-]', ''
            if ($cleaned.Length -gt 0 -and -not $cleanTags.Contains($cleaned)) {
                $cleanTags += $cleaned
            }
        }
    }
    $tagsString = if ($cleanTags.Count -gt 0) { $cleanTags -join ", " } else { "" }
    
    # Timestamp
    $createdAtIso = $m.createdAt
    if (-not $createdAtIso -and $m.timestampMs) {
        $createdAtIso = [System.DateTimeOffset]::FromUnixTimeMilliseconds($m.timestampMs).ToString("o")
    }
    if (-not $createdAtIso) {
        $createdAtIso = [System.DateTimeOffset]::UtcNow.ToString("o")
    }

    # Sanitize text to remove corrupted non-UTF8/control bytes
    $cleanText = [System.Text.RegularExpressions.Regex]::Replace($m.text, '[^\x20-\x7E\r\n\t]', ' ')
    
    $bodyObj = @{
        id = $m.id
        tier = $tier
        text = $cleanText
        tags = $tagsString
        importance = if ($m.importance) { [double]$m.importance } else { 1.0 }
        source = if ($m.source) { $m.source } else { "OBSERVED" }
        createdAt = $createdAtIso
    }
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes(($bodyObj | ConvertTo-Json -Compress))

    $ingested = $false
    $attempt = 0
    while (-not $ingested -and $attempt -lt 3) {
        $attempt++
        try {
            $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/remember" -Headers $headers -Method Post -Body $bodyBytes
            if ($resp -and ($resp.id -or $resp.status -eq "accepted" -or $resp.status -eq "success")) {
                $ingested = $true
                $successCount++
                $pct = [Math]::Round(($idx / $totalMissing) * 100, 1)
                Write-Host "[$idx/$totalMissing - $pct%] Ingested $($m.id) [$tier] -> $tagsString" -ForegroundColor Green
            } else {
                throw "Unexpected response"
            }
        } catch {
            Write-Host "[$idx/$totalMissing] [Attempt $attempt/3 FAILED] $($m.id): $($_.Exception.Message)" -ForegroundColor Yellow
            if ($attempt -lt 3) {
                Start-Sleep -Seconds 2
            } else {
                $failCount++
            }
        }
    }

    if ($DelayBetweenMemoriesMs -gt 0) {
        Start-Sleep -Milliseconds $DelayBetweenMemoriesMs
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  Missing Memories Ingestion Completed" -ForegroundColor Cyan
Write-Host "  Successfully Ingested: $successCount / $totalMissing" -ForegroundColor Green
Write-Host "  Failed:                $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Green" })
Write-Host "==========================================================" -ForegroundColor Cyan
