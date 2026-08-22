# Seed Spectrayan Company Soul
param(
    [string]$SoulPath = $(if ($env:SPECTOR_SOUL_PATH) { $env:SPECTOR_SOUL_PATH } else { "" }),
    [string]$BaseUrl = $(if ($env:SPECTOR_BASE_URL) { $env:SPECTOR_BASE_URL } else { "http://localhost:7070" }),
    [string]$ApiKey = $(if ($env:SPECTOR_API_KEY) { $env:SPECTOR_API_KEY } else { "spector-dev-key" })
)

$ErrorActionPreference = "Stop"

if (-not $SoulPath -or -not (Test-Path $SoulPath)) {
    if (Test-Path "$PSScriptRoot/../config/soul.json") {
        $SoulPath = "$PSScriptRoot/../config/soul.json"
    } elseif (Test-Path "soul.json") {
        $SoulPath = "soul.json"
    } else {
        Write-Error "Soul file not found. Provide -SoulPath <path> or set SPECTOR_SOUL_PATH environment variable."
        exit 1
    }
}

$rawBytes = [System.IO.File]::ReadAllBytes($SoulPath)

$headers = @{
    "Authorization" = "Bearer $ApiKey"
}

Write-Host "Seeding Spectrayan Company Soul profile to $BaseUrl/api/v1/salience/user/default..." -ForegroundColor Cyan

$resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/salience/user/default" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Method Put `
    -Body $rawBytes

Write-Host "Soul profile successfully seeded!" -ForegroundColor Green
$resp | ConvertTo-Json -Depth 3 | Write-Host
