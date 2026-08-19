#
# Copyright 2026 Spectrayan
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("smoke", "load", "stress", "spike", "soak", "mixed", "isolation", "lru", "all")]
    [string]$Scenario = "smoke",

    [string]$BaseUrl = "http://localhost:7070",
    [string]$ApiKey = "spector-dev-key",
    [switch]$AuthEnabled,
    [int]$VUs = 0,
    [string]$Duration = "",
    [string]$Out = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Verify k6 is available
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] k6 executable not found in PATH." -ForegroundColor Red
    Write-Host "Please install Grafana k6: https://grafana.com/docs/k6/latest/set-up/install-k6/" -ForegroundColor Yellow
    Write-Host "  Windows (winget): winget install k6"
    Write-Host "  macOS (brew):     brew install k6"
    Write-Host "  Docker:           docker run -i grafana/k6 run - <scenario.js"
    exit 1
}

$ScenarioMap = @{
    "smoke"     = "$ScriptDir/scenarios/01-smoke-test.js"
    "load"      = "$ScriptDir/scenarios/02-load-test.js"
    "stress"    = "$ScriptDir/scenarios/03-stress-test.js"
    "spike"     = "$ScriptDir/scenarios/04-spike-test.js"
    "soak"      = "$ScriptDir/scenarios/05-soak-test.js"
    "mixed"     = "$ScriptDir/scenarios/06-mixed-workload.js"
    "isolation" = "$ScriptDir/scenarios/07-multi-user-isolation.js"
    "lru"       = "$ScriptDir/scenarios/08-user-registry-lru-stress.js"
}

$EnvArgs = @(
    "-e", "BASE_URL=$BaseUrl",
    "-e", "API_KEY=$ApiKey",
    "-e", "AUTH_ENABLED=$($AuthEnabled.IsPresent.ToString().ToLower())"
)

function Run-K6Scenario([string]$Name, [string]$File) {
    Write-Host "`n=======================================================" -ForegroundColor Cyan
    Write-Host " Running Spector k6 Scenario: $Name" -ForegroundColor Cyan
    Write-Host " Script: $File" -ForegroundColor Cyan
    Write-Host " Base URL: $BaseUrl | Auth: $($AuthEnabled.IsPresent)" -ForegroundColor Cyan
    Write-Host "=======================================================`n" -ForegroundColor Cyan

    $ArgsList = @("run") + $EnvArgs
    if ($VUs -gt 0) { $ArgsList += @("--vus", "$VUs") }
    if ($Duration -ne "") { $ArgsList += @("--duration", "$Duration") }
    if ($Out -ne "") { $ArgsList += @("--out", "$Out") }
    $ArgsList += $File

    & k6 $ArgsList
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[FAIL] Scenario $Name failed with exit code $LASTEXITCODE" -ForegroundColor Red
        return $false
    }
    Write-Host "`n[PASS] Scenario $Name completed successfully." -ForegroundColor Green
    return $true
}

if ($Scenario -eq "all") {
    $AllPass = $true
    foreach ($Key in @("smoke", "load", "mixed", "isolation")) {
        $Passed = Run-K6Scenario -Name $Key -File $ScenarioMap[$Key]
        if (-not $Passed) { $AllPass = $false }
    }
    if (-not $AllPass) { exit 1 }
} else {
    $Passed = Run-K6Scenario -Name $Scenario -File $ScenarioMap[$Scenario]
    if (-not $Passed) { exit 1 }
}
