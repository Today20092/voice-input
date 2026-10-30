# Runs the platform-independent routing contract without configuring native builds.
param([switch]$CompileAndroid)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
function Jar($module, $version) {
    $jar = Get-ChildItem (Join-Path $cache "$module/$version") -Recurse -Filter '*.jar' | Select-Object -First 1
    if (!$jar) { throw "Missing cached dependency $module/$version. Run the project's Gradle dependency setup first." }
    $jar.FullName
}
$stdlib = Jar 'org.jetbrains.kotlin/kotlin-stdlib' '2.1.0'
$junit = Jar 'junit/junit' '4.13.2'
$hamcrest = Jar 'org.hamcrest/hamcrest-core' '1.3'
$annotations = Jar 'org.jetbrains/annotations' '13.0'
$compiler = @(
    (Jar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' '2.1.0'), $stdlib,
    (Jar 'org.jetbrains.kotlin/kotlin-script-runtime' '2.1.0'),
    (Jar 'org.jetbrains.kotlin/kotlin-reflect' '2.1.0'),
    (Jar 'org.jetbrains.intellij.deps/trove4j' '1.0.20200330'),
    (Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm' '1.6.4'), $annotations
) -join [IO.Path]::PathSeparator
$output = Join-Path $repo 'build/microphone-routing-tests.jar'
New-Item -ItemType Directory -Force (Split-Path $output) | Out-Null
$classpath = @($stdlib, $junit, $hamcrest, $annotations) -join [IO.Path]::PathSeparator
$sources = @((Join-Path $repo 'app/src/main/java/org/futo/voiceinput/MicrophoneRouting.kt'), (Join-Path $repo 'app/src/test/java/org/futo/voiceinput/MicrophoneRoutingTest.kt'))
if ($CompileAndroid) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $classpath += [IO.Path]::PathSeparator + (Join-Path $sdk 'platforms/android-35/android.jar')
    $sources += Join-Path $repo 'app/src/main/java/org/futo/voiceinput/AndroidMicrophonePlatform.kt'
}
& java -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp "$output$([IO.Path]::PathSeparator)$classpath" org.junit.runner.JUnitCore org.futo.voiceinput.MicrophoneRoutingTest
exit $LASTEXITCODE
