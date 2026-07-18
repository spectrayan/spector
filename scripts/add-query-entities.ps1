#!/usr/bin/env pwsh
# Adds entity hints to queries.jsonl by matching query text against known entities
# from entities.jsonl. This enables entity graph traversal at recall time.

param(
    [string]$DatasetDir = "datasets/cognitive-benchmark"
)

$ErrorActionPreference = "Stop"

# Load known entities from entities.jsonl
$entityFile = Join-Path $DatasetDir "entities.jsonl"
$queryFile = Join-Path $DatasetDir "queries.jsonl"
$outputFile = Join-Path $DatasetDir "queries.jsonl"

if (-not (Test-Path $entityFile)) {
    Write-Error "Entity file not found: $entityFile"
    exit 1
}

Write-Host "Loading entities from $entityFile..."
$entityLines = Get-Content $entityFile -Encoding UTF8
$knownEntities = @{}

foreach ($line in $entityLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $rel = $line | ConvertFrom-Json
    $knownEntities[$rel.fromEntity.name] = $rel.fromEntity.type
    $knownEntities[$rel.toEntity.name] = $rel.toEntity.type
}

Write-Host "Loaded $($knownEntities.Count) unique entities"

# Load queries
Write-Host "Loading queries from $queryFile..."
$queryLines = Get-Content $queryFile -Encoding UTF8
$updatedQueries = @()
$matchCount = 0

foreach ($line in $queryLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $query = $line | ConvertFrom-Json
    $queryText = $query.text.ToLower()
    
    # Find entities mentioned in the query text (case-insensitive substring match)
    $matchedEntities = @()
    foreach ($entityName in $knownEntities.Keys) {
        if ($queryText.Contains($entityName.ToLower())) {
            $entityType = $knownEntities[$entityName]
            # Map benchmark entity types to valid EntityType enum values
            $mappedType = switch ($entityType) {
                "HOBBI" { "OTHER" }
                "CONCEPT" { "OTHER" }
                "SOFTWARE" { "PRODUCT" }
                default { $entityType }
            }
            $matchedEntities += @{
                name = $entityName
                type = $mappedType
                relations = @()
            }
        }
    }
    
    # Add entity hints to query
    if ($matchedEntities.Count -gt 0) {
        $query | Add-Member -NotePropertyName "entityHints" -NotePropertyValue $matchedEntities -Force
        $matchCount++
        Write-Host "  $($query.id): matched $($matchedEntities.Count) entities: $(($matchedEntities | ForEach-Object { $_.name }) -join ', ')"
    } else {
        # Keep null entityHints for queries with no entity matches
        $query | Add-Member -NotePropertyName "entityHints" -NotePropertyValue $null -Force
    }
    
    $updatedQueries += ($query | ConvertTo-Json -Compress -Depth 10)
}

# Write updated queries back
Write-Host "`nWriting updated queries to $outputFile..."
$updatedQueries | Set-Content $outputFile -Encoding ascii

Write-Host "Done! $matchCount of $($queryLines.Count) queries now have entity hints"
