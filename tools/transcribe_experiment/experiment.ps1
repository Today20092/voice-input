param([switch]$Build)
$ErrorActionPreference = 'Stop'
$revision = '4807edaf210d0d7e8a6f7fb2a44b65966a2797f0'
$archiveHash = '00c3bc4f2f505b4e6865c559a3c9aad30e98a48c9b6a304a2e73b50cb2f42522'
$work = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force $work | Out-Null
$archive = Join-Path $work 'source.tar.gz'
if (!(Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest "https://codeload.github.com/handy-computer/transcribe.cpp/tar.gz/$revision" -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $archiveHash) {
    throw 'Source archive checksum mismatch; no extraction or build attempted'
}
if (!$Build) {
    Write-Output "Verified source archive $revision. Native build requires -Build and the coordinator slot."
    exit 0
}
# A fresh extraction prevents local source edits or stale files from entering the build.
$extract = Join-Path $work ('source-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $extract | Out-Null
& tar -xzf $archive -C $extract
if ($LASTEXITCODE) { throw 'Source extraction failed' }
$source = Join-Path $extract "transcribe.cpp-$revision"
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$ndk = Join-Path $sdk 'ndk/28.2.13676358'
$cmake = Join-Path $sdk 'cmake/3.22.1/bin/cmake.exe'
$ninja = Join-Path $sdk 'cmake/3.22.1/bin/ninja.exe'
$native = Join-Path $work 'arm64'
& $cmake -S $PSScriptRoot -B $native -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
    "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" `
    '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-26' '-DANDROID_STL=c++_static' `
    '-DCMAKE_BUILD_TYPE=Release' "-DTRANSCRIBE_SOURCE=$source"
if ($LASTEXITCODE) { throw 'Native configure failed' }
& $cmake --build $native --target share09_parakeet --parallel 2
if ($LASTEXITCODE) { throw 'Native build failed' }
$library = Join-Path $native 'libshare09_parakeet.so'
$bin = Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$symbols = & (Join-Path $bin 'llvm-nm.exe') --dynamic --defined-only $library
if ($LASTEXITCODE) { throw 'ELF symbol inspection failed' }
$expected = @('nativeOpen', 'nativeReset', 'nativeTranscribe', 'nativeCancel', 'nativeClose') |
    ForEach-Object { 'Java_org_futo_voiceinput_experiment_NativeParakeet_' + $_ }
$actual = @($symbols | ForEach-Object { ($_ -split '\s+')[-1] })
if (Compare-Object ($expected | Sort-Object) ($actual | Sort-Object)) {
    throw "Unexpected dynamic exports: $symbols"
}
$dynamic = & (Join-Path $bin 'llvm-readelf.exe') --dynamic $library
if ($LASTEXITCODE) { throw 'ELF dependency inspection failed' }
$needed = @($dynamic | Select-String 'NEEDED')
if ($needed -match 'ggml|transcribe|llama|OpenCL|c\+\+_shared') {
    throw "Unexpected runtime dependency: $needed"
}
Copy-Item -LiteralPath (Join-Path $source 'LICENSE') -Destination (Join-Path $native 'TRANSCRIBE-LICENSE.txt')
Copy-Item -LiteralPath (Join-Path $source 'THIRD-PARTY-LICENSES.md') -Destination (Join-Path $native 'THIRD-PARTY-LICENSES.md')
# The pinned default CPU build includes tinyBLAS, whose notice is absent above.
Get-Content -LiteralPath (Join-Path $source 'ggml/src/ggml-cpu/llamafile/sgemm.cpp') -TotalCount 21 |
    ForEach-Object { $_ -replace '^// ?', '' } | Set-Content -LiteralPath (Join-Path $native 'TINYBLAS-LICENSE.txt')
Write-Output $needed
Get-FileHash -LiteralPath $library -Algorithm SHA256
Write-Output 'Five JNI exports only; no shared GGML, llama, OpenCL or C++ runtime dependency. Runtime coexistence still needs device testing.'
