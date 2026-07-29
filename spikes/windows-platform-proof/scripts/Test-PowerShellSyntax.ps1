[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$failures = @()
foreach ($path in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $path.FullName,
        [ref]$tokens,
        [ref]$errors)
    if ($errors.Count -ne 0) {
        $failures += $errors | ForEach-Object {
            "$($path.Name): $($_.Message)"
        }
    }
}
if ($failures.Count -ne 0) {
    throw ($failures -join [Environment]::NewLine)
}

$analyzer = Get-Command Invoke-ScriptAnalyzer -ErrorAction SilentlyContinue
if ($null -ne $analyzer) {
    $results = @(Invoke-ScriptAnalyzer -Path $PSScriptRoot -Recurse -Severity Warning,Error)
    if ($results.Count -ne 0) {
        $results | Format-Table -AutoSize | Out-String | Write-Error
    }
    Write-Output 'PowerShell syntax and available PSScriptAnalyzer checks passed.'
}
else {
    Write-Output 'PowerShell syntax passed; PSScriptAnalyzer is not installed in this environment.'
}
