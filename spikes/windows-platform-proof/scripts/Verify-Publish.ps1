[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PublishRoot,

    [string]$ExpectedSourceCommit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedPublish = (Resolve-Path -LiteralPath $PublishRoot).Path
& (Join-Path $PSScriptRoot 'Verify-Fixtures.ps1') `
    -FixtureRoot (Join-Path $resolvedPublish 'fixtures')

$requiredFiles = @(
    'MediaEcosystem.WindowsProof.exe',
    'MediaEcosystem.WindowsProof.dll',
    'MediaEcosystem.WindowsProof.deps.json',
    'MediaEcosystem.WindowsProof.runtimeconfig.json',
    'coreclr.dll',
    'hostfxr.dll',
    'hostpolicy.dll',
    'build-source-commit.txt',
    'schemas\windows-proof-evidence.schema.json'
)
foreach ($relative in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedPublish $relative) -PathType Leaf)) {
        throw "Published self-contained proof is missing '$relative'."
    }
}

$forbiddenKeyExtensions = @('.jks', '.keystore', '.p12', '.pfx', '.pem', '.key')
$forbidden = @(Get-ChildItem -LiteralPath $resolvedPublish -Recurse -File |
    Where-Object { $forbiddenKeyExtensions -contains $_.Extension.ToLowerInvariant() })
if ($forbidden.Count -ne 0) {
    throw 'Published proof contains signing or private-key material.'
}

$mediaExtensions = @(
    '.mp3', '.flac', '.m4a', '.aac', '.ogg', '.oga', '.wav',
    '.aiff', '.aif', '.alac', '.wma', '.opus'
)
$mediaFiles = @(Get-ChildItem -LiteralPath $resolvedPublish -Recurse -File |
    Where-Object { $mediaExtensions -contains $_.Extension.ToLowerInvariant() })
$fixtureRoot = (Resolve-Path -LiteralPath (Join-Path $resolvedPublish 'fixtures')).Path
foreach ($media in $mediaFiles) {
    if ($media.DirectoryName -ne $fixtureRoot) {
        throw 'Published media exists outside the exact fixture directory.'
    }
}
if ($mediaFiles.Count -ne 6) {
    throw 'Published proof does not contain exactly six media fixtures.'
}

$deps = Get-Content -LiteralPath `
    (Join-Path $resolvedPublish 'MediaEcosystem.WindowsProof.deps.json') -Raw |
    ConvertFrom-Json
if ([string]$deps.runtimeTarget.name -notmatch 'win-x64') {
    throw 'Published dependency graph is not the pinned win-x64 target.'
}

$schema = Get-Content -LiteralPath `
    (Join-Path $resolvedPublish 'schemas\windows-proof-evidence.schema.json') -Raw |
    ConvertFrom-Json
if ([string]$schema.'$schema' -ne 'https://json-schema.org/draft/2020-12/schema') {
    throw 'Published evidence schema is invalid.'
}

$publishedSourceCommit = (
    Get-Content -LiteralPath (Join-Path $resolvedPublish 'build-source-commit.txt') -Raw
).Trim()
if ($publishedSourceCommit -ne 'unknown-local-build' -and
    $publishedSourceCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Published source commit marker is invalid.'
}
if (-not [string]::IsNullOrWhiteSpace($ExpectedSourceCommit) -and
    ($ExpectedSourceCommit -notmatch '^[0-9a-f]{40}$' -or
     $publishedSourceCommit -ne $ExpectedSourceCommit)) {
    throw 'Published source commit marker does not match.'
}

Write-Output "Verified unpacked self-contained win-x64 publish at tooling scope only."
