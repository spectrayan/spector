<#
.SYNOPSIS
    One-line standalone installer for Spector CLI on Windows.
.DESCRIPTION
    Downloads the latest release of spector.jar, creates Windows command and PowerShell shims,
    verifies OpenJDK 25 prerequisites, and configures the User PATH.
.EXAMPLE
    irm https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.ps1 | iex
.PARAMETER DryRun
    Simulate installation without making system changes.
.PARAMETER Version
    Specific release tag to install (e.g. 'v0.1.0-alpha'). Defaults to 'latest'.
.PARAMETER InstallDir
    Custom destination directory. Defaults to '$HOME\.spector'.
#>

[CmdletBinding()]
param (
    [switch]$DryRun,
    [string]$Version = "latest",
    [string]$InstallDir = "$HOME\.spector"
)

$ErrorActionPreference = "Stop"

$Repo = "spectrayan/spector"
$BinDir = Join-Path $InstallDir "bin"
$JarPath = Join-Path $BinDir "spector.jar"
$CmdWrapper = Join-Path $BinDir "spector.cmd"
$Ps1Wrapper = Join-Path $BinDir "spector.ps1"

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "            Spector Cognitive Memory & CLI Installer               " -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan

# 1. Inspect Environment
Write-Host "-> System: Windows"

# 2. Check Java 25
$HasJava25 = $false
try {
    $rawJava = (java -version 2>&1) -join " "
    if ($rawJava -match 'version "([^"]+)"') {
        $javaVer = $matches[1]
        Write-Host "-> Detected Java version: $javaVer"
        if ($javaVer -match '^2[5-9]' -or $javaVer -match '^[3-9][0-9]') {
            $HasJava25 = $true
        }
    }
} catch {
    Write-Host "Java was not found on PATH." -ForegroundColor Yellow
}

if (-not $HasJava25) {
    Write-Host "OpenJDK 25 with Vector API was not detected on your PATH." -ForegroundColor Yellow
    Write-Host "Spector requires JDK 25+ to leverage SIMD vector hardware acceleration."
    Write-Host "Recommended installation via WinGet or Scoop:"
    Write-Host "  winget install Microsoft.OpenJDK.25" -ForegroundColor Green
    Write-Host "  scoop install openjdk25" -ForegroundColor Green
}

if ($DryRun) {
    Write-Host "-> [DRY-RUN] Target directory: $BinDir"
    Write-Host "-> [DRY-RUN] Wrappers: $CmdWrapper, $Ps1Wrapper"
    Write-Host "-> [DRY-RUN] Verification complete. Exiting dry run." -ForegroundColor Green
    exit 0
}

# 3. Create Directories
if (-not (Test-Path $BinDir)) {
    New-Item -Path $BinDir -ItemType Directory -Force | Out-Null
}
$DataDir = Join-Path $InstallDir "data"
if (-not (Test-Path $DataDir)) {
    New-Item -Path $DataDir -ItemType Directory -Force | Out-Null
}

# 4. Fetch Release Asset
$ApiUrl = if ($Version -eq "latest") {
    "https://api.github.com/repos/$Repo/releases/latest"
} else {
    "https://api.github.com/repos/$Repo/releases/tags/$Version"
}

Write-Host "-> Querying GitHub Releases ($ApiUrl)..."
try {
    $release = Invoke-RestMethod -Uri $ApiUrl -Headers @{ "User-Agent" = "Spector-Installer" }
    $asset = $release.assets | Where-Object { $_.name -eq "spector.jar" -or $_.name -like "*-cli.jar" } | Select-Object -First 1
    if ($asset) {
        Write-Host "-> Downloading spector.jar from $($asset.browser_download_url)..."
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $JarPath -UseBasicParsing
    } else {
        Write-Host "Release asset 'spector.jar' not currently found on GitHub Releases." -ForegroundColor Yellow
        Write-Host "Generated CLI launcher shims ready for local JAR placement."
    }
} catch {
    Write-Host "Could not contact GitHub releases API. Generating launcher shims." -ForegroundColor Yellow
}

# 5. Create Executable Wrappers
$cmdLines = @(
    '@echo off',
    'setlocal',
    'set "SPECTOR_HOME=%~dp0.."',
    'set "JAR=%SPECTOR_HOME%\bin\spector.jar"',
    'if not exist "%JAR%" (',
    '    echo Error: %JAR% not found. Build or download spector.jar first.',
    '    exit /b 1',
    ')',
    'java --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*'
)
Set-Content -Path $CmdWrapper -Value ($cmdLines -join "`r`n") -Encoding ASCII

$ps1Lines = @(
    '$ErrorActionPreference = "Stop"',
    '$SpectorHome = Split-Path -Parent $PSScriptRoot',
    '$Jar = Join-Path $PSScriptRoot "spector.jar"',
    'if (-not (Test-Path $Jar)) {',
    '    Write-Error "Error: $Jar not found. Build or download spector.jar first."',
    '    exit 1',
    '}',
    'java --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar $Jar $args'
)
Set-Content -Path $Ps1Wrapper -Value ($ps1Lines -join "`r`n") -Encoding UTF8

# 6. Update User PATH
$UserPath = [Environment]::GetEnvironmentVariable("PATH", [EnvironmentVariableTarget]::User)
if ($UserPath -notlike "*$BinDir*") {
    $NewPath = "$UserPath;$BinDir"
    [Environment]::SetEnvironmentVariable("PATH", $NewPath, [EnvironmentVariableTarget]::User)
    $env:PATH = "$env:PATH;$BinDir"
    Write-Host "-> Added $BinDir to User PATH." -ForegroundColor Green
}

Write-Host ""
Write-Host "Spector CLI successfully installed to: $BinDir" -ForegroundColor Green
Write-Host "Test installation with:"
Write-Host "  spector doctor" -ForegroundColor Cyan
Write-Host ""
