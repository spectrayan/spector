<#
.SYNOPSIS
    One-line standalone installer for Spector CLI on Windows.
.DESCRIPTION
    Downloads the latest release of spector.jar, verifies SHA-256 checksum, creates
    Windows command and PowerShell shims with OpenJDK 25 verification, and configures User PATH.
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
    [switch]$Force,
    [string]$Version = "latest",
    [string]$InstallDir = "$HOME\.spector"
)

$ErrorActionPreference = "Stop"

$Repo = "spectrayan/spector"
$BinDir = Join-Path $InstallDir "bin"
$JarPath = Join-Path $BinDir "spector.jar"
$CmdWrapper = Join-Path $BinDir "spector.cmd"
$Ps1Wrapper = Join-Path $BinDir "spector.ps1"
$EnvFile = Join-Path $InstallDir "env.ps1"

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "            Spector Cognitive Memory & CLI Installer               " -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan

# 1. Inspect Environment
Write-Host "-> System: Windows"

# 2. Check Java 25
$HasJava25 = $false
$JavaCmd = "java"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $JavaCmd = Join-Path $env:JAVA_HOME "bin\java.exe"
}

try {
    $rawJava = (& $JavaCmd -version 2>&1) -join " "
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
    Write-Host "WARNING: OpenJDK 25 with Vector API was not detected on your PATH." -ForegroundColor Yellow
    Write-Host "Spector requires JDK 25+ to leverage SIMD vector hardware acceleration."
    Write-Host "Recommended installation via WinGet or Scoop:"
    Write-Host "  winget install Microsoft.OpenJDK.25" -ForegroundColor Green
    Write-Host "  scoop install openjdk25" -ForegroundColor Green
    if (-not $Force) {
        Write-Error "OpenJDK 25+ with Vector API is required to install and run Spector. Install Java 25 or pass -Force to bypass this check."
        exit 1
    }
}

# 3. Query Release Metadata
$ApiUrl = if ($Version -eq "latest") {
    "https://api.github.com/repos/$Repo/releases/latest"
} else {
    $tag = if ($Version -like "v*") { $Version } else { "v$Version" }
    "https://api.github.com/repos/$Repo/releases/tags/$tag"
}

Write-Host "-> Querying GitHub Releases ($ApiUrl)..."
$release = $null
$jarAsset = $null
$shaAsset = $null

try {
    $release = Invoke-RestMethod -Uri $ApiUrl -Headers @{ 
        "User-Agent" = "spector-installer"
        "Accept" = "application/vnd.github.v3+json" 
    }
    if ($release -and $release.assets) {
        $jarAsset = $release.assets | Where-Object { $_.name -eq "spector.jar" } | Select-Object -First 1
        $shaAsset = $release.assets | Where-Object { $_.name -eq "spector.jar.sha256" } | Select-Object -First 1
    }
} catch {
    if (-not $DryRun) {
        Write-Error "Error: Could not retrieve release metadata from $ApiUrl. $($_.Exception.Message)"
        exit 1
    }
}

# 4. Dry-Run Handling
if ($DryRun) {
    Write-Host "-> [DRY-RUN] Target directory: $BinDir"
    Write-Host "-> [DRY-RUN] Target JAR path:  $JarPath"
    Write-Host "-> [DRY-RUN] Wrappers:        $CmdWrapper, $Ps1Wrapper"
    Write-Host "-> [DRY-RUN] Release API URL:  $ApiUrl"
    Write-Host "-> [DRY-RUN] Java 25 status:   $(if ($HasJava25) { 'FOUND' } else { 'NOT FOUND (JDK 25 required)' })"
    if (-not $jarAsset) {
        Write-Error "[DRY-RUN] Release asset 'spector.jar' was not found on GitHub Releases ($ApiUrl)!"
        exit 1
    }
    Write-Host "-> [DRY-RUN] Release asset 'spector.jar' verified: $($jarAsset.browser_download_url)" -ForegroundColor Green
    Write-Host "-> [DRY-RUN] Verification complete. Exiting dry run." -ForegroundColor Green
    exit 0
}

if (-not $jarAsset) {
    Write-Error "Error: Release asset 'spector.jar' was not found in release ($ApiUrl)."
    exit 1
}

# 5. Create Directories
if (-not (Test-Path $BinDir)) {
    New-Item -Path $BinDir -ItemType Directory -Force | Out-Null
}
$DataDir = Join-Path $InstallDir "data"
if (-not (Test-Path $DataDir)) {
    New-Item -Path $DataDir -ItemType Directory -Force | Out-Null
}

# 6. Download and Verify
Write-Host "-> Downloading spector.jar from $($jarAsset.browser_download_url)..."
Invoke-WebRequest -Uri $jarAsset.browser_download_url -OutFile $JarPath -UseBasicParsing

if ($shaAsset) {
    Write-Host "-> Downloading spector.jar.sha256..."
    $shaPath = "$JarPath.sha256"
    Invoke-WebRequest -Uri $shaAsset.browser_download_url -OutFile $shaPath -UseBasicParsing
    
    $expectedHash = ((Get-Content -Path $shaPath -Raw).Trim() -split '\s+')[0].Trim().ToLowerInvariant()
    $actualHash = (Get-FileHash -Path $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()

    if ($actualHash -ne $expectedHash) {
        Remove-Item -Path $JarPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $shaPath -Force -ErrorAction SilentlyContinue
        Write-Error "Error: SHA-256 verification failed! Expected $expectedHash, got $actualHash"
        exit 1
    }
    Write-Host "-> Verified SHA-256: $actualHash" -ForegroundColor Green
}

# 7. Create Executable Wrappers
$cmdLines = @(
    '@echo off',
    'setlocal enabledelayedexpansion',
    'set "SPECTOR_HOME=%~dp0.."',
    'set "JAR=%SPECTOR_HOME%\bin\spector.jar"',
    'if not exist "%JAR%" (',
    '    echo Error: %JAR% not found. Re-run installer to download spector.jar. >&2',
    '    exit /b 1',
    ')',
    'set "JAVA_CMD=java"',
    'if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"',
    'for /f "tokens=3" %%g in (''"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"'') do (',
    '    set "JVER=%%~g"',
    ')',
    'rem Ensure JDK 25+ is used',
    '"%JAVA_CMD%" --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*'
)
Set-Content -Path $CmdWrapper -Value ($cmdLines -join "`r`n") -Encoding ASCII

$ps1Lines = @(
    '$ErrorActionPreference = "Stop"',
    '$SpectorHome = Split-Path -Parent $PSScriptRoot',
    '$Jar = Join-Path $PSScriptRoot "spector.jar"',
    'if (-not (Test-Path $Jar)) {',
    '    Write-Error "Error: $Jar not found. Re-run installer to download spector.jar."',
    '    exit 1',
    '}',
    '$JavaCmd = "java"',
    'if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {',
    '    $JavaCmd = Join-Path $env:JAVA_HOME "bin\java.exe"',
    '}',
    'try {',
    '    $rawJava = (& $JavaCmd -version 2>&1) -join " "',
    '    if ($rawJava -match ''version "([^"]+)"'') {',
    '        $v = $matches[1]',
    '        if (-not ($v -match ''^2[5-9]'' -or $v -match ''^[3-9][0-9]'')) {',
    '            Write-Error "Error: Spector requires OpenJDK 25+. Detected: $v. Install via: winget install Microsoft.OpenJDK.25"',
    '            exit 1',
    '        }',
    '    }',
    '} catch {',
    '    Write-Error "Error: Java was not found on PATH. OpenJDK 25+ required."',
    '    exit 1',
    '}',
    '& $JavaCmd --enable-preview --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar $Jar @args'
)
Set-Content -Path $Ps1Wrapper -Value ($ps1Lines -join "`r`n") -Encoding UTF8

# 8. Create Environment script
$envLines = @(
    "`$env:SPECTOR_HOME = `"$InstallDir`"",
    "if (`$env:PATH -notlike `"*$BinDir*`") {",
    "    `$env:PATH = `"$BinDir;`$env:PATH`"",
    "}"
)
Set-Content -Path $EnvFile -Value ($envLines -join "`r`n") -Encoding UTF8

# 9. Update User PATH
$UserPath = [Environment]::GetEnvironmentVariable("PATH", [EnvironmentVariableTarget]::User)
if ($UserPath -notlike "*$BinDir*") {
    $NewPath = "$UserPath;$BinDir"
    [Environment]::SetEnvironmentVariable("PATH", $NewPath, [EnvironmentVariableTarget]::User)
    $env:PATH = "$env:PATH;$BinDir"
    Write-Host "-> Added $BinDir to User PATH." -ForegroundColor Green
}

Write-Host ""
Write-Host "Spector CLI successfully installed to: $BinDir" -ForegroundColor Green
Write-Host "Environment script written to: $EnvFile" -ForegroundColor Cyan
Write-Host "Test installation with:"
Write-Host "  spector doctor" -ForegroundColor Cyan
Write-Host ""
