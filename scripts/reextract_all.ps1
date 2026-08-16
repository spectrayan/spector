# scripts/reextract_all.ps1
# Triggers full graph re-extraction in batches, waiting for each batch to complete
# before triggering the next one. Uses the new POST /api/v1/memory/reextract-graph endpoint.
param(
    [string]$BaseUrl = "http://localhost:7070",
    [string]$ApiKey = "spector-dev-key",
    [int]$BatchSize = 50,
    [int]$PollIntervalSec = 15
)

$headers = @{
    Authorization = "Bearer $ApiKey"
    "Content-Type" = "application/json"
}

Write-Host "=== Spector Full Graph Re-Extraction ===" -ForegroundColor Cyan
Write-Host "Base URL:       $BaseUrl"
Write-Host "Batch Size:     $BatchSize"
Write-Host "Poll Interval:  ${PollIntervalSec}s"
Write-Host ""

$totalBatches = 0
$totalReextracted = 0

while ($true) {
    $totalBatches++
    Write-Host "[Batch $totalBatches] Triggering re-extraction (limit=$BatchSize)..." -ForegroundColor Yellow

    try {
        $triggerResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/reextract-graph?limit=$BatchSize" -Method Post -Headers $headers
        Write-Host "[Batch $totalBatches] $($triggerResp.status): $($triggerResp.message)" -ForegroundColor Green
    } catch {
        Write-Host "[Batch $totalBatches] FAILED to trigger: $($_.Exception.Message)" -ForegroundColor Red
        break
    }

    # Wait for re-extraction to complete
    $waitingLoops = 0
    while ($true) {
        Start-Sleep -Seconds $PollIntervalSec
        $waitingLoops++

        try {
            $status = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/enrich-graph/status" -Headers $headers
            $pct = if ($status.totalMemories -gt 0) { [math]::Round(($status.enrichedMemories / $status.totalMemories) * 100, 1) } else { 0 }

            Write-Host "  [Poll $waitingLoops] Enriched: $($status.enrichedMemories)/$($status.totalMemories) ($pct%) | Entities: $($status.totalEntitiesAdded) | Relations: $($status.totalRelationsAdded) | InProgress: $($status.inProgress) | Duration: $($status.lastRunDurationMs)ms" -ForegroundColor Gray

            if (-not $status.inProgress) {
                Write-Host "[Batch $totalBatches] Complete. Duration: $($status.lastRunDurationMs)ms" -ForegroundColor Green
                if ($status.lastError) {
                    Write-Host "  Last Error: $($status.lastError)" -ForegroundColor Yellow
                }
                break
            }
        } catch {
            Write-Host "  [Poll $waitingLoops] Status check failed: $($_.Exception.Message)" -ForegroundColor Yellow
        }

        if ($waitingLoops -gt 120) {
            Write-Host "[Batch $totalBatches] Timeout after $($waitingLoops * $PollIntervalSec)s" -ForegroundColor Red
            break
        }
    }

    # Check if we should continue - if pendingMemories is 0 or all are enriched
    try {
        $finalStatus = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memory/enrich-graph/status" -Headers $headers
        Write-Host ""
        Write-Host "  Running total - Enriched: $($finalStatus.enrichedMemories)/$($finalStatus.totalMemories) | Entities: $($finalStatus.totalEntitiesAdded) | Relations: $($finalStatus.totalRelationsAdded)" -ForegroundColor Cyan

        if ($finalStatus.pendingMemories -le 0 -or $finalStatus.enrichedMemories -ge $finalStatus.totalMemories) {
            Write-Host ""
            Write-Host "=== ALL MEMORIES RE-EXTRACTED ===" -ForegroundColor Green
            Write-Host "Total batches:    $totalBatches"
            Write-Host "Total memories:   $($finalStatus.totalMemories)"
            Write-Host "Total enriched:   $($finalStatus.enrichedMemories)"
            Write-Host "Total entities:   $($finalStatus.totalEntitiesAdded)"
            Write-Host "Total relations:  $($finalStatus.totalRelationsAdded)"
            break
        }
    } catch {
        Write-Host "  Final status check failed, continuing..." -ForegroundColor Yellow
    }

    Write-Host ""
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
