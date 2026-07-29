[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$FixtureRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedRoot = (Resolve-Path -LiteralPath $FixtureRoot).Path
$manifestPath = Join-Path $resolvedRoot 'fixture-manifest.json'
$sumsPath = Join-Path $resolvedRoot 'SHA256SUMS'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $sumsPath -PathType Leaf)) {
    throw 'Fixture manifests are missing.'
}

$requiredIds = @('mp3-v0', 'mp3-320', 'flac', 'aac', 'ogg-vorbis', 'wav')
$historicalIds = @('aiff', 'alac')
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$entries = @($manifest.fixtures)
if ($entries.Count -ne 6 -or
    [int]$manifest.format_contract.active_required_count -ne 6 -or
    [string]$manifest.format_contract.id -ne 'v1-required-formats-2026-07-28') {
    throw 'Fixture manifest does not declare the exact six-format contract.'
}

$activeIds = @($manifest.format_contract.active_required_format_ids)
$entryIds = @($entries | ForEach-Object { [string]$_.id })
if ((Compare-Object -ReferenceObject $requiredIds -DifferenceObject $activeIds) -or
    (Compare-Object -ReferenceObject $requiredIds -DifferenceObject $entryIds)) {
    throw 'Active fixture IDs or ordering do not match the exact contract.'
}

$historical = @($manifest.format_contract.historical_nonrequired_format_ids | Sort-Object)
if (Compare-Object -ReferenceObject $historicalIds -DifferenceObject $historical) {
    throw 'Historical ALAC/AIFF exclusions are not exact.'
}

if ($entryIds -contains 'alac' -or $entryIds -contains 'aiff') {
    throw 'ALAC or AIFF entered the active fixture matrix.'
}

$expectedNames = @($entries | ForEach-Object { [string]$_.filename }) +
    @('fixture-manifest.json', 'SHA256SUMS')
$actualNames = @(Get-ChildItem -LiteralPath $resolvedRoot -File |
    ForEach-Object { $_.Name })
if (Compare-Object -ReferenceObject ($expectedNames | Sort-Object) `
        -DifferenceObject ($actualNames | Sort-Object)) {
    throw 'Fixture directory membership mismatch.'
}

$sumByName = @{}
foreach ($line in (Get-Content -LiteralPath $sumsPath)) {
    if ($line -notmatch '^([0-9a-f]{64})  ([^\\/]+)$') {
        throw 'Fixture checksum manifest is malformed.'
    }
    if ($sumByName.ContainsKey($Matches[2])) {
        throw 'Fixture checksum manifest contains a duplicate.'
    }
    $sumByName[$Matches[2]] = $Matches[1]
}

$totalBytes = [int64]0
foreach ($entry in $entries) {
    $id = [string]$entry.id
    $filename = [string]$entry.filename
    $path = Join-Path $resolvedRoot $filename
    $file = Get-Item -LiteralPath $path
    if ($file.Length -ne [int64]$entry.size_bytes -or $file.Length -gt 2000000) {
        throw "Fixture size mismatch or limit exceeded for stable ID '$id'."
    }
    if ([int64]$entry.expected_duration_ms -lt 5000 -or
        [int]$entry.duration_tolerance_ms -gt 500) {
        throw "Fixture duration contract is invalid for stable ID '$id'."
    }
    $actualHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne [string]$entry.sha256 -or
        -not $sumByName.ContainsKey($filename) -or
        $sumByName[$filename] -ne [string]$entry.sha256) {
        throw "Fixture SHA-256 mismatch for stable ID '$id'."
    }
    $totalBytes += $file.Length
}

if ($totalBytes -gt 8000000 -or $sumByName.Count -ne 6) {
    throw 'Fixture corpus total or checksum membership is invalid.'
}

$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Verified exact six synthetic fixtures ($totalBytes bytes); manifest SHA-256 $manifestHash."
