# Experimental shared-recognizer module

Imported from FUTO Voice Input revision
`c4590aaebb8d4ba6bfb9ac11e802e6c0519daf9b`, directory `shared/`.
Source: https://github.com/futo-org/voice-input/tree/c4590aaebb8d4ba6bfb9ac11e802e6c0519daf9b/shared
The repository's [FUTO Source First License](../LICENSE.md) applies.
Imported resources and sound files retain their upstream provenance.

This module is included only with `-PsharedRecognizerProof=true`. Normal builds
do not include it. Its injected session path does not instantiate an upstream
model manager, runner or microphone. The upstream default path remains in source
for comparison; its native runtime is not supplied by this proof.

Maintained changes: `RecognizerSession` and a factory in `RecognizerView`, an
optional model-manager parameter, lifecycle-interface implementation on the
default `AudioRecognizer`, delivery acknowledgement through the listeners, and
skipping the incompatible GGUF download in experimental builds. Shared view,
UI, model and recorder source otherwise retain upstream implementations.
The injected path also accepts a null model-run configuration. The old VAD AAR
is a compile-time classes-only dependency in proof builds, preventing collision
with the fork's newer `libvad_jni.so`. Its default recorder path cannot run in
this experimental APK; only the injected factory path is supported.
See [current evidence and gaps](../docs/research/shared-recognizer-reproducibility-2026-10-30.md).

## Run

Use the toolchain in [development](../docs/development.md), initialize the pinned
submodules, and configure the Android SDK. On Windows long paths, use
`git -c core.longpaths=true submodule update --init --recursive`.

```powershell
.\gradlew.bat :shared:compileDebugKotlin :shared:lintDebug -PsharedRecognizerProof=true
.\gradlew.bat :app:testDevDebugUnitTest :app:compileDevDebugAndroidTestKotlin -PsharedRecognizerProof=true
.\gradlew.bat :shared:connectedDebugAndroidTest -PsharedRecognizerProof=true
.\gradlew.bat :app:connectedDevDebugAndroidTest -PsharedRecognizerProof=true -Pandroid.testInstrumentationRunnerArguments.class=org.futo.voiceinput.proof.SharedBackendBoundaryTest
```

The native test requires the included ASR4ALL Medium assets and an installed,
validated Orukeet installation in the dev app. It fails clearly when required
assets are absent, rather than silently substituting a backend. It uses only
the repository's public JFK fixture. Controlled tests use injected PCM and do
not open a microphone. They exercise the actual shared view, fork session,
finalizer and backend contract; they do not establish live microphone, history,
or complete Activity/IME-service parity. The additional `SharedActivityResultTest`
uses an actual Android result receiver; the editor test commits through a real
EditText input connection. The native test loads current engines rather than
transcribe.cpp. Test settings are restored afterward.

## Rollback

Omit the property to restore the normal dependency and source-set graph. No
production entry point, selection key, model directory, default or runtime was
changed. Removal consists of this optional module, the two conditional build
blocks, and the app's `src/sharedProof`/`src/sharedProofTest` directories.
