# Seed Spectrayan Company Soul
param(
    [string]$SoulPath = "C:\Users\bhara\.gemini\antigravity\brain\6b7f3f04-e0f5-4635-a487-4fb96dfbf5a5\scratch\spectrayan_soul.json",
    [string]$BaseUrl = "http://localhost:7070",
    [string]$ApiKey = "spector-dev-key"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SoulPath)) {
    Write-Error "Soul file not found: $SoulPath"
    exit 1
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
