#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
#  Spector MCP Functional Test — stdio JSON-RPC
#  Sends MCP tool calls via stdin/stdout to the Spector server
# ═══════════════════════════════════════════════════════════════

param(
    [string]$Config = "spector-local.yml",
    [string]$Jar = "synapse/spector-dist/target/spector.jar"
)

$ErrorActionPreference = "Stop"

# MCP JSON-RPC helpers
function New-McpRequest($id, $method, $params) {
    $req = @{ jsonrpc = "2.0"; id = $id; method = $method }
    if ($params) { $req.params = $params }
    return ($req | ConvertTo-Json -Depth 10 -Compress)
}

function Send-McpCall($process, $request) {
    $process.StandardInput.WriteLine($request)
    $process.StandardInput.Flush()
    
    # Read response (may have multiple lines for notifications)
    $timeout = 60000  # 60s for embedding calls
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $timeout) {
        if ($process.StandardOutput.Peek() -ge 0) {
            $line = $process.StandardOutput.ReadLine()
            if ($line -and $line.StartsWith("{")) {
                try {
                    $parsed = $line | ConvertFrom-Json
                    if ($parsed.id) { return $parsed }
                } catch { }
            }
        }
        Start-Sleep -Milliseconds 50
    }
    Write-Host "  TIMEOUT waiting for response" -ForegroundColor Red
    return $null
}

# ─────────── Start server ───────────
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Spector MCP Functional Test Suite" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "--enable-preview --add-modules jdk.incubator.vector -Xmx4g -jar $Jar serve --config $Config"
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

Write-Host "`nStarting Spector MCP server (stdio)..." -ForegroundColor Yellow
$proc = [System.Diagnostics.Process]::Start($psi)
Start-Sleep -Seconds 12

$pass = 0; $fail = 0; $total = 0

function Test-Tool($name, $args_hash, $validate) {
    $script:total++
    $id = $script:total
    Write-Host "`n[$id] Testing: $name" -ForegroundColor White
    
    $req = New-McpRequest $id "tools/call" @{
        name = $name
        arguments = $args_hash
    }
    
    $resp = Send-McpCall $proc $req
    if ($resp -and $resp.result) {
        $content = $resp.result.content
        if ($content -and $content.Count -gt 0) {
            $text = $content[0].text
            Write-Host "  Response: $($text.Substring(0, [Math]::Min(200, $text.Length)))..." -ForegroundColor Gray
            
            if ($validate) {
                $ok = & $validate $text
                if ($ok) {
                    Write-Host "  ✅ PASS" -ForegroundColor Green
                    $script:pass++
                } else {
                    Write-Host "  ❌ FAIL - validation failed" -ForegroundColor Red
                    $script:fail++
                }
            } else {
                Write-Host "  ✅ PASS (response received)" -ForegroundColor Green
                $script:pass++
            }
        } else {
            Write-Host "  ❌ FAIL - empty content" -ForegroundColor Red
            $script:fail++
        }
    } elseif ($resp -and $resp.error) {
        Write-Host "  ❌ FAIL - error: $($resp.error.message)" -ForegroundColor Red
        $script:fail++
    } else {
        Write-Host "  ❌ FAIL - no response" -ForegroundColor Red
        $script:fail++
    }
}

# ─────────── Initialize MCP ───────────
Write-Host "`n[0] Initializing MCP..." -ForegroundColor Yellow
$initReq = New-McpRequest 0 "initialize" @{
    protocolVersion = "2024-11-05"
    capabilities = @{}
    clientInfo = @{ name = "spector-test"; version = "1.0" }
}
$initResp = Send-McpCall $proc $initReq
if ($initResp) {
    Write-Host "  MCP initialized: $($initResp.result.serverInfo.name)" -ForegroundColor Green
    
    # Send initialized notification
    $proc.StandardInput.WriteLine('{"jsonrpc":"2.0","method":"notifications/initialized"}')
    $proc.StandardInput.Flush()
    Start-Sleep -Seconds 1
} else {
    Write-Host "  Failed to initialize MCP!" -ForegroundColor Red
    $proc.Kill()
    exit 1
}

# ─────────── Test 1: memory_status ───────────
Test-Tool "memory_status" @{} { param($t) $t -match "Total|memories|SEMANTIC" }

# ─────────── Test 2: memory_remember (SEMANTIC) ───────────
Test-Tool "memory_remember" @{
    id = "test-semantic-001"
    text = "Spector uses a 4-tier cognitive memory architecture inspired by Atkinson-Shiffrin and Tulving memory models."
    tier = "SEMANTIC"
    source = "USER_STATED"
    tags = "architecture,cognitive,spector"
} { param($t) $t -match "Stored|SEMANTIC" }

# ─────────── Test 3: memory_remember (EPISODIC) ───────────
Test-Tool "memory_remember" @{
    id = "test-episodic-001"
    text = "User mentioned they are testing Spector with Ollama qwen3-embedding model on June 3rd 2026."
    tier = "EPISODIC"
    source = "OBSERVED"
    tags = "testing,ollama,embeddings"
} { param($t) $t -match "Stored|EPISODIC" }

# ─────────── Test 4: memory_remember (PROCEDURAL) ───────────
Test-Tool "memory_remember" @{
    id = "test-procedural-001"
    text = "To run Spector tests: mvn test -pl spector-memory. To build dist: mvn install -DskipTests."
    tier = "PROCEDURAL"
    source = "PROCEDURAL"
    tags = "build,testing,maven"
} { param($t) $t -match "Stored|PROCEDURAL" }

# ─────────── Test 5: memory_scratchpad ───────────
Test-Tool "memory_scratchpad" @{
    text = "Debugging the recall pipeline latency issue. Suspect HNSW ef_search too low."
} { param($t) $t -match "scratchpad|Working|stored" }

# ─────────── Test 6: memory_recall (basic) ───────────
Test-Tool "memory_recall" @{
    query = "cognitive memory architecture"
    top_k = 5
} { param($t) $t -match "score|memory|result" }

# ─────────── Test 7: memory_recall (tier filter) ───────────
Test-Tool "memory_recall" @{
    query = "how to build and test spector"
    tiers = "PROCEDURAL"
    top_k = 5
} { param($t) $t -match "score|result" }

# ─────────── Test 8: memory_reinforce ───────────
Test-Tool "memory_reinforce" @{
    id = "test-semantic-001"
    valence = 100
    outcome = "This architecture explanation was very helpful."
} { param($t) $t -match "Reinforced|valence|updated" }

# ─────────── Test 9: memory_introspect ───────────
Test-Tool "memory_introspect" @{
    topic = "spector architecture"
} { param($t) $t -match "insight|memor" }

# ─────────── Test 10: memory_suppress ───────────
Test-Tool "memory_suppress" @{
    id = "test-episodic-001"
    reason = "Not relevant to current session"
} { param($t) $t -match "Suppressed|suppress" }

# ─────────── Test 11: memory_forget ───────────
Test-Tool "memory_forget" @{
    id = "test-procedural-001"
} { param($t) $t -match "Forgotten|forget|tombstoned" }

# ─────────── Test 12: memory_why_not ───────────
Test-Tool "memory_why_not" @{
    id = "test-semantic-001"
    query = "dark mode user interface"
} { param($t) $t -match "score|rank|similarity|reason|Validation" }

# ─────────── Test 13: memory_status (final) ───────────
Test-Tool "memory_status" @{} { param($t) $t -match "Total|memories" }

# ─────────── Summary ───────────
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Results: $pass PASS / $fail FAIL / $total TOTAL" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

# Cleanup
$proc.StandardInput.Close()
Start-Sleep -Seconds 2
if (!$proc.HasExited) { $proc.Kill() }
$proc.Dispose()

exit $fail
