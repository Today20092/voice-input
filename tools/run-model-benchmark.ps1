[CmdletBinding()]
param(
    [ValidateSet('Pilot', 'Full')][string]$Mode = 'Pilot',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Serial,
    [string]$Package = 'org.futo.voiceinput.dev',
    [string]$Suite,
    [string[]]$Models,
    [string]$ResumeRunId,
    [switch]$DownloadMissing,
    [switch]$ReferencesVerified,
    [switch]$Install,
    [ValidateRange(1,5)][int]$WarmRepetitions = 5,
    [ValidateRange(30,3600)][int]$CaseTimeoutSeconds = 1800,
    [ValidateRange(0,600)][int]$CoolingSeconds = 0,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if (!$Suite) { $Suite = Join-Path $PSScriptRoot '..\benchmarks\audio\phone-room\suite.json' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot '..\benchmarks\results' }
if ($Package -notmatch '^[a-zA-Z0-9_.]+$') { throw 'Invalid package name' }
if ($Serial -and $Serial -notmatch '^[a-zA-Z0-9_.:-]+$') { throw 'Invalid device serial' }
if ($ResumeRunId -and $ResumeRunId -notmatch '^[0-9a-f-]{36}$') { throw 'Invalid run ID' }
if ($Models | Where-Object { $_ -notmatch '^[a-zA-Z0-9_.-]+$' }) { throw 'Invalid model ID' }
if (!(Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }
$taskTemporary = Join-Path ([IO.Path]::GetTempPath()) ("voiceinput-benchmark-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $taskTemporary | Out-Null

function Invoke-BenchmarkAdb {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 60, [string]$SaveTo)
    $stdout = Join-Path $taskTemporary ([guid]::NewGuid().ToString() + '.stdout')
    $stderr = Join-Path $taskTemporary ([guid]::NewGuid().ToString() + '.stderr')
    $allArguments = @()
    if ($Serial) { $allArguments += @('-s', $Serial) }
    $allArguments += $Arguments
    # Start-Process joins arguments on Windows; quote individual paths without shell evaluation.
    $quoted = $allArguments | ForEach-Object { '"' + $_.Replace('"','\"') + '"' }
    $process = Start-Process -FilePath $Adb -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    # Retain the handle so Windows PowerShell 5 can read ExitCode after waiting.
    $process.Handle | Out-Null
    if (!$process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill()
        throw "ADB operation timed out after $TimeoutSeconds seconds"
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "ADB operation failed with exit code $($process.ExitCode). See $stderr" }
    if ($SaveTo) { Copy-Item -LiteralPath $stdout -Destination $SaveTo; return }
    return [IO.File]::ReadAllText($stdout)
}

function Invoke-BenchmarkCommand {
    param([string]$Operation, [string[]]$Extra = @(), [int]$TimeoutSeconds = 120)
    $arguments = @('shell','am','instrument','-w','-r','-e','class','org.futo.voiceinput.benchmark.BenchmarkAdbTest',
        '-e','benchmarkOperation',$Operation) + $Extra + @("$Package.test/androidx.test.runner.AndroidJUnitRunner")
    $result = Invoke-BenchmarkAdb -Arguments $arguments -TimeoutSeconds $TimeoutSeconds
    if ($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Benchmark $Operation failed. Instrumentation output is retained in $taskTemporary"
    }
}

function Read-BenchmarkControl {
    $text = Invoke-BenchmarkAdb -Arguments @('exec-out','run-as',$Package,'cat','files/benchmark-control.json')
    return $text | ConvertFrom-Json
}

try {
    $state = Invoke-BenchmarkAdb -Arguments @('get-state')
    if ($state.Trim() -ne 'device') { throw 'Connect and authorize exactly one phone, or supply -Serial' }
    if ($Install) {
        $repo = Split-Path -Parent $PSScriptRoot
        $appApks = @(Get-ChildItem -LiteralPath "$repo\app\build\outputs\apk\dev\debug" -Filter '*.apk')
        $testApks = @(Get-ChildItem -LiteralPath "$repo\app\build\outputs\apk\androidTest\dev\debug" -Filter '*.apk')
        if ($appApks.Count -ne 1 -or $testApks.Count -ne 1) { throw 'Build assembleDevDebug and assembleDevDebugAndroidTest first' }
        Invoke-BenchmarkAdb -Arguments @('install','-r',$appApks[0].FullName) -TimeoutSeconds 180 | Out-Null
        Invoke-BenchmarkAdb -Arguments @('install','-r',$testApks[0].FullName) -TimeoutSeconds 180 | Out-Null
    }
    Invoke-BenchmarkAdb -Arguments @('shell','am','force-stop',$Package) | Out-Null
    Invoke-BenchmarkCommand -Operation 'catalog'
    $catalog = (Read-BenchmarkControl).models
    if (!$Models -and !$ResumeRunId) {
        $Models = if ($Mode -eq 'Full') { @($catalog.id) } else { @($catalog | Where-Object installed | Select-Object -First 2 -ExpandProperty id) }
    }
    $runId = $ResumeRunId
    if (!$runId) {
        if (!$Models -or ($Models | Where-Object { $_ -notin $catalog.id })) { throw 'Choose model IDs from the supported catalog' }
        $suitePath = (Resolve-Path -LiteralPath $Suite).Path
        $suiteDirectory = Split-Path -Parent $suitePath
        $manifest = Get-Content -LiteralPath $suitePath -Raw | ConvertFrom-Json
        if ($Mode -eq 'Pilot') { $manifest.clips = @($manifest.clips | Select-Object -First 1) }
        if ($ReferencesVerified) { $manifest.clips | ForEach-Object { $_.referenceVerified = $true } }
        Invoke-BenchmarkAdb -Arguments @('shell','run-as',$Package,'mkdir','-p','files/benchmark-import') | Out-Null
        foreach ($clip in $manifest.clips) {
            if ($clip.file -notmatch '^[a-zA-Z0-9_-]+\.(m4a|wav)$') { throw 'Unsafe suite filename' }
            $source = Join-Path $suiteDirectory $clip.file
            if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $clip.sha256) { throw "Original hash mismatch: $($clip.file)" }
            $remote = "/data/local/tmp/voiceinput-benchmark-$($clip.file)"
            Invoke-BenchmarkAdb -Arguments @('push',$source,$remote) | Out-Null
            Invoke-BenchmarkAdb -Arguments @('shell','run-as',$Package,'cp',$remote,"files/benchmark-import/$($clip.file)") | Out-Null
            Invoke-BenchmarkAdb -Arguments @('shell','rm',$remote) | Out-Null
        }
        $temporarySuite = Join-Path $taskTemporary 'suite.json'
        [IO.File]::WriteAllText($temporarySuite, ($manifest | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        Invoke-BenchmarkAdb -Arguments @('push',$temporarySuite,'/data/local/tmp/voiceinput-benchmark-suite.json') | Out-Null
        Invoke-BenchmarkAdb -Arguments @('shell','run-as',$Package,'cp','/data/local/tmp/voiceinput-benchmark-suite.json','files/benchmark-import/suite.json') | Out-Null
        Invoke-BenchmarkAdb -Arguments @('shell','rm','/data/local/tmp/voiceinput-benchmark-suite.json') | Out-Null
        Invoke-BenchmarkCommand -Operation 'prepare' -Extra @('-e','benchmarkModels',($Models -join ','),'-e','benchmarkWarmRepetitions',$WarmRepetitions.ToString())
        $runId = (Read-BenchmarkControl).runId
    } else {
        $persistedText = Invoke-BenchmarkAdb -Arguments @('exec-out','run-as',$Package,'cat',"files/model-benchmarks/$runId/report.json")
        $persisted = $persistedText | ConvertFrom-Json
        $Models = @($persisted.selectedModelIds)
    }
    $recordedIds = if ($ResumeRunId) { @($persisted.cases.modelId) } else { @() }
    $pendingIds = @($Models | Where-Object { $_ -notin $recordedIds })
    $missing = @($catalog | Where-Object { $_.id -in $pendingIds -and !$_.installed })
    Write-Host "Run $runId. Missing model downloads: $([math]::Round(($missing | Measure-Object transferBytes -Sum).Sum / 1MB, 1)) MiB. Installed storage: $([math]::Round(($missing | Measure-Object storageBytes -Sum).Sum / 1MB, 1)) MiB."
    Write-Host 'Reports contain references and transcripts. They remain local; audio is not included in report export.'
    foreach ($modelId in $Models) {
        if ($modelId -in $recordedIds) {
            Write-Host "Preserving recorded case: $modelId"
            continue
        }
        if ($DownloadMissing -and $modelId -in $missing.id) {
            Write-Host "Downloading $modelId"
            try {
                Invoke-BenchmarkCommand -Operation 'download' -Extra @('-e','benchmarkModel',$modelId) -TimeoutSeconds 1260
            } catch {
                Write-Warning "Download for $modelId failed or timed out. Recording its unavailable result and continuing."
                Invoke-BenchmarkAdb -Arguments @('shell','am','force-stop',$Package) | Out-Null
            }
        }
        if ($CoolingSeconds) { Start-Sleep -Seconds $CoolingSeconds }
        Write-Host "Fresh-process comparison: $modelId"
        Invoke-BenchmarkAdb -Arguments @('shell','am','force-stop',$Package) | Out-Null
        try {
            Invoke-BenchmarkCommand -Operation 'run' -Extra @('-e','benchmarkRunId',$runId,'-e','benchmarkModel',$modelId,
                '-e','benchmarkTimeoutSeconds',$CaseTimeoutSeconds.ToString()) -TimeoutSeconds ($CaseTimeoutSeconds + 60)
        } catch {
            Write-Warning "Case $modelId interrupted. Retaining partial results and continuing. Resume ID: $runId"
            Invoke-BenchmarkAdb -Arguments @('shell','am','force-stop',$Package) | Out-Null
        }
    }
    if ($ReferencesVerified) { Invoke-BenchmarkCommand -Operation 'verify-references' -Extra @('-e','benchmarkRunId',$runId) }
    Invoke-BenchmarkCommand -Operation 'export' -Extra @('-e','benchmarkRunId',$runId)
    $output = Join-Path $OutputDirectory $runId
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    Invoke-BenchmarkAdb -Arguments @('exec-out','run-as',$Package,'cat',"files/model-benchmarks/$runId/report.json") -SaveTo (Join-Path $output 'report.json')
    Invoke-BenchmarkAdb -Arguments @('exec-out','run-as',$Package,'cat',"files/model-benchmarks/$runId/report.md") -SaveTo (Join-Path $output 'report.md')
    [IO.File]::WriteAllText((Join-Path $output 'host-run.json'), (@{ runId=$runId; mode=$Mode; coolingSeconds=$CoolingSeconds; caseTimeoutSeconds=$CaseTimeoutSeconds; referencesVerified=[bool]$ReferencesVerified; recordedAt=[DateTime]::UtcNow.ToString('o') } | ConvertTo-Json))
    Write-Host "Saved reports: $output"
} catch {
    if ($runId) { Write-Warning "Durable run ID: $runId. Use -ResumeRunId to continue pending cases." }
    throw
}
