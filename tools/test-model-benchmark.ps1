$ErrorActionPreference = 'Stop'
$taskTokens = $null
$taskErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'run-model-benchmark.ps1'), [ref]$taskTokens, [ref]$taskErrors) | Out-Null
if ($taskErrors.Count) { throw ($taskErrors | Out-String) }
$suitePath = Join-Path $PSScriptRoot '..\benchmarks\audio\phone-room\suite.json'
$manifest = Get-Content -LiteralPath $suitePath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or $manifest.clips.Count -ne 3) { throw 'Unexpected recording manifest' }
foreach ($clip in $manifest.clips) {
    if ($clip.referenceVerified) { throw 'Listening verification is still pending' }
    if ($clip.reference.Length -lt 20) { throw 'Missing reference passage' }
    $source = Join-Path (Split-Path -Parent $suitePath) $clip.file
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $clip.sha256) { throw "Hash mismatch: $($clip.file)" }
}
Write-Output 'PowerShell syntax and three original recording hashes passed. This check does not execute device tests.'
