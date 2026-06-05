param(
    [string]$Mode = "VECTOR_ONLY",
    [string]$Query = "Ivermectin is used to treat onchocerciasis",
    [string]$Config = "spector-bench.yml"
)

$ErrorActionPreference = "Stop"
$root = "D:\git\spector-search"

# Build the JSON-RPC sequence: init + notify + query
$initMsg = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test"}}}'
$notifyMsg = '{"jsonrpc":"2.0","method":"notifications/initialized"}'
$escapedQ = $Query -replace '"','\"'
$queryMsg = "{`"jsonrpc`":`"2.0`",`"id`":2,`"method`":`"tools/call`",`"params`":{`"name`":`"memory_recall`",`"arguments`":{`"query`":`"$escapedQ`",`"top_k`":10,`"tiers`":`"SEMANTIC`",`"text_search_mode`":`"$Mode`"}}}"

# Write all messages to a temp file to pipe as stdin
$tmpIn = [System.IO.Path]::GetTempFileName()
@($initMsg, $notifyMsg, $queryMsg) | Set-Content $tmpIn -Encoding UTF8

# Run server with stdin from file, capture stdout
$javaArgs = "--enable-preview --add-modules jdk.incubator.vector -Xmx4g -jar $root\spector-dist\target\spector.jar serve --config $root\$Config"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = $javaArgs
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.WorkingDirectory = $root

$proc = [System.Diagnostics.Process]::Start($psi)

# Drain stderr in background
$proc.BeginErrorReadLine()

# Wait for server to init
Start-Sleep -Seconds 12

# Send messages with delays
$proc.StandardInput.WriteLine($initMsg)
$proc.StandardInput.Flush()
Start-Sleep -Milliseconds 500

# Read init response
$initResp = $proc.StandardOutput.ReadLine()
Write-Host "Init: OK"

$proc.StandardInput.WriteLine($notifyMsg)
$proc.StandardInput.Flush()
Start-Sleep -Milliseconds 200

# Send query
$proc.StandardInput.WriteLine($queryMsg)
$proc.StandardInput.Flush()

# Read query response
$resp = $proc.StandardOutput.ReadLine()

# Parse
$parsed = $resp | ConvertFrom-Json
$text = $parsed.result.content[0].text

# Extract IDs
$idMatches = [regex]::Matches($text, 'ID:\s*sf-(\d+)\.txt')
$ids = @(); foreach ($m in $idMatches) { $ids += $m.Groups[1].Value }

Write-Host "Mode: $Mode"
Write-Host "Query: $Query"
Write-Host "Results: $($ids.Count) docs"
Write-Host "IDs: $($ids -join ', ')"
Write-Host ""
# Show first result preview
if ($text.Length -gt 0) {
    Write-Host $text.Substring(0, [Math]::Min(500, $text.Length))
}

$proc.Kill() 2>$null
$proc.Dispose()
Remove-Item $tmpIn -Force -ErrorAction SilentlyContinue
