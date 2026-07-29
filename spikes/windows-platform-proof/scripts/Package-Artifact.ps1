[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PublishRoot,

    [Parameter(Mandatory = $true)]
    [string]$OutputZip
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedPublish = (Resolve-Path -LiteralPath $PublishRoot).Path.TrimEnd('\')
$outputFullPath = [System.IO.Path]::GetFullPath($OutputZip)
if (Test-Path -LiteralPath $outputFullPath) {
    throw 'Artifact output already exists; refusing to overwrite it.'
}
$outputParent = Split-Path -Parent $outputFullPath
if (-not (Test-Path -LiteralPath $outputParent -PathType Container)) {
    New-Item -ItemType Directory -Path $outputParent | Out-Null
}

& (Join-Path $PSScriptRoot 'Verify-Publish.ps1') -PublishRoot $resolvedPublish
Compress-Archive -Path (Join-Path $resolvedPublish '*') `
    -DestinationPath $outputFullPath -CompressionLevel Optimal

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($outputFullPath)
try {
    $expected = @(Get-ChildItem -LiteralPath $resolvedPublish -Recurse -File |
        ForEach-Object {
            $_.FullName.Substring($resolvedPublish.Length + 1).Replace('\', '/')
        } | Sort-Object)
    $actual = @($archive.Entries |
        Where-Object { -not $_.FullName.EndsWith('/') } |
        ForEach-Object { $_.FullName.Replace('\', '/') } | Sort-Object)
    if (Compare-Object -ReferenceObject $expected -DifferenceObject $actual) {
        throw 'Artifact ZIP membership differs from the verified publish directory.'
    }
    if (@($actual | Where-Object {
                $_.StartsWith('/') -or $_.Contains('../') -or $_.Contains('..\')
            }).Count -ne 0) {
        throw 'Artifact ZIP contains an unsafe entry name.'
    }
    foreach ($entry in $archive.Entries) {
        if (-not $entry.FullName.EndsWith('/')) {
            $source = Join-Path $resolvedPublish $entry.FullName.Replace('/', '\')
            if ($entry.Length -ne (Get-Item -LiteralPath $source).Length) {
                throw "Artifact ZIP length mismatch for '$($entry.FullName)'."
            }
        }
    }
}
finally {
    $archive.Dispose()
}

$file = Get-Item -LiteralPath $outputFullPath
$sha256 = (Get-FileHash -LiteralPath $outputFullPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Artifact: $($file.Name)"
Write-Output "Bytes: $($file.Length)"
Write-Output "SHA-256: $sha256"
