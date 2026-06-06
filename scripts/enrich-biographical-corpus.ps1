#!/usr/bin/env pwsh
# Enriches the biographical corpus records with proper cognitive metadata:
# - synapticTags (inferred from text content and life phase)
# - valence (sentiment: positive/negative/neutral)
# - importance (varied based on significance)
# - arousal (energy level of the memory)

param(
    [string]$DatasetDir = "datasets/cognitive-benchmark"
)

$ErrorActionPreference = "Stop"

$bioFile = Join-Path $DatasetDir "corpus-biographical.jsonl"
$corpusFile = Join-Path $DatasetDir "corpus.jsonl"

Write-Host "=== Enriching biographical corpus records ===" -ForegroundColor Cyan

$lines = Get-Content $bioFile | Where-Object { $_.Trim() -ne "" }
$records = $lines | ForEach-Object { $_ | ConvertFrom-Json }
Write-Host "Loaded $($records.Count) biographical records"

# Life phase tag mappings
$phaseTagMap = @{
    "childhood" = @("childhood", "family", "memory")
    "school" = @("school", "education", "learning")
    "career" = @("career", "work", "professional")
    "family" = @("family", "relationship", "personal")
    "interests" = @("hobby", "personal", "interest")
}

# Topic keywords → tags
$topicTags = @{
    "cook" = "cooking"; "stir-fry" = "cooking"; "soup" = "cooking"; "waffle" = "cooking";
    "dinner" = "cooking"; "pasta" = "cooking"; "meal" = "cooking";
    "bike" = "outdoors"; "park" = "outdoors"; "hike" = "outdoors"; "climb" = "outdoors";
    "yellowstone" = "travel"; "road trip" = "travel"; "beach" = "travel"; "vacation" = "travel";
    "code" = "programming"; "debug" = "programming"; "programming" = "programming";
    "computer" = "technology"; "robot" = "technology"; "hack" = "technology"; "linux" = "technology";
    "cache" = "distributed-systems"; "distributed" = "distributed-systems"; "api" = "distributed-systems";
    "mentor" = "mentorship"; "teach" = "mentorship"; "taught" = "mentorship";
    "friend" = "friendship"; "best friend" = "friendship";
    "mom" = "family"; "dad" = "family"; "parent" = "family"; "grandma" = "family";
    "pet" = "pets"; "dog" = "pets"; "puppy" = "pets"; "rabbit" = "pets"; "retriever" = "pets";
    "debate" = "public-speaking"; "present" = "public-speaking"; "conference" = "public-speaking";
    "nervous" = "growth"; "overwhelm" = "growth"; "adjust" = "growth";
    "proud" = "achievement"; "accomplishment" = "achievement"; "award" = "achievement"; "won" = "achievement";
    "alex" = "relationship"; "priya" = "colleague"
}

# Sentiment keywords
$positiveWords = @("love", "excited", "proud", "amazing", "fun", "sweet", "happy", "cherish",
    "exhilarating", "rewarding", "cool", "fascinated", "wonderful", "incredible", "favorite")
$negativeWords = @("nervous", "scared", "terrifying", "frustrated", "overwhelmed", "disaster",
    "struggled", "tough", "miss", "argue", "butchered", "intimidated")

$fixedCount = 0

foreach ($r in $records) {
    $text = $r.text.ToLower()
    $title = $r.title.ToLower()
    $session = $r.sessionId

    # ── Determine life phase from sessionId ──
    $phase = "general"
    foreach ($key in $phaseTagMap.Keys) {
        if ($session -match $key) { $phase = $key; break }
    }
    $tags = [System.Collections.Generic.List[string]]::new()
    $phaseTagMap[$phase] | ForEach-Object { $tags.Add($_) }

    # ── Extract topic tags ──
    foreach ($keyword in $topicTags.Keys) {
        if ($text.Contains($keyword)) {
            $tag = $topicTags[$keyword]
            if (-not $tags.Contains($tag)) { $tags.Add($tag) }
        }
    }

    # Limit to 5 tags
    $finalTags = $tags | Select-Object -First 5
    $r.synapticTags = @($finalTags)

    # ── Compute valence (sentiment) ──
    $posCount = 0; $negCount = 0
    foreach ($w in $positiveWords) { if ($text.Contains($w)) { $posCount++ } }
    foreach ($w in $negativeWords) { if ($text.Contains($w)) { $negCount++ } }

    if ($posCount -gt $negCount + 1) {
        $r.valence = [int]([Math]::Min(127, 30 + ($posCount * 15)))
    } elseif ($negCount -gt $posCount) {
        $r.valence = [int]([Math]::Max(-128, -20 - ($negCount * 15)))
    } else {
        $r.valence = [int]((Get-Random -Minimum -10 -Maximum 20))
    }

    # ── Compute importance (based on significance markers) ──
    $imp = 1.0
    if ($text -match "first|never forget|still remember|proudest") { $imp += 1.5 }
    if ($text -match "taught|learned|lesson") { $imp += 0.8 }
    if ($text -match "career|job|company|team") { $imp += 0.5 }
    if ($text -match "family|mom|dad|parent") { $imp += 0.5 }
    if ($text -match "award|won|prize|competition") { $imp += 1.0 }
    $imp = [Math]::Min(8.0, [Math]::Max(0.5, $imp))
    $r.importance = [Math]::Round($imp, 1)

    # ── Compute arousal ──
    $arousal = 50
    if ($text -match "excited|exhilarating|nervous|terrifying|scared") { $arousal += 60 }
    if ($text -match "proud|amazing|incredible|love") { $arousal += 30 }
    if ($text -match "calm|quiet|peaceful|subdued|gentle") { $arousal -= 20 }
    if ($text -match "cozy|relaxed|mellow") { $arousal -= 15 }
    $arousal = [Math]::Min(255, [Math]::Max(10, $arousal))
    $r.arousal = $arousal

    $fixedCount++
}

# Write enriched biographical file
$output = $records | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 10 }
$content = $output -join "`n"
[System.IO.File]::WriteAllText((Resolve-Path $bioFile).Path, $content + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $fixedCount biographical records in $bioFile"

# Now update the same records in the main corpus
Write-Host "`n=== Updating main corpus ===" -ForegroundColor Cyan
$corpusLines = Get-Content $corpusFile | Where-Object { $_.Trim() -ne "" }
$corpusRecords = $corpusLines | ForEach-Object { $_ | ConvertFrom-Json }

# Build lookup from enriched biographical records
$bioLookup = @{}
foreach ($r in $records) { $bioLookup[$r.id] = $r }

$updatedCount = 0
for ($i = 0; $i -lt $corpusRecords.Count; $i++) {
    $cr = $corpusRecords[$i]
    if ($bioLookup.ContainsKey($cr.id)) {
        $bio = $bioLookup[$cr.id]
        $cr.synapticTags = $bio.synapticTags
        $cr.valence = $bio.valence
        $cr.importance = $bio.importance
        $cr.arousal = $bio.arousal
        $corpusRecords[$i] = $cr
        $updatedCount++
    }
}

$corpusOutput = $corpusRecords | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 10 }
$corpusContent = $corpusOutput -join "`n"
[System.IO.File]::WriteAllText((Resolve-Path $corpusFile).Path, $corpusContent + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $updatedCount records in main corpus"

# Summary
Write-Host "`n=== Summary ===" -ForegroundColor Green
$tagStats = $records | ForEach-Object { $_.synapticTags.Count } | Measure-Object -Average -Minimum -Maximum
$valStats = $records | ForEach-Object { $_.valence } | Measure-Object -Average -Minimum -Maximum
$impStats = $records | ForEach-Object { $_.importance } | Measure-Object -Average -Minimum -Maximum
$aroStats = $records | ForEach-Object { $_.arousal } | Measure-Object -Average -Minimum -Maximum
Write-Host "Tags:       avg=$([Math]::Round($tagStats.Average,1)), min=$($tagStats.Minimum), max=$($tagStats.Maximum)"
Write-Host "Valence:    avg=$([Math]::Round($valStats.Average,1)), min=$($valStats.Minimum), max=$($valStats.Maximum)"
Write-Host "Importance: avg=$([Math]::Round($impStats.Average,1)), min=$($impStats.Minimum), max=$($impStats.Maximum)"
Write-Host "Arousal:    avg=$([Math]::Round($aroStats.Average,1)), min=$($aroStats.Minimum), max=$($aroStats.Maximum)"
