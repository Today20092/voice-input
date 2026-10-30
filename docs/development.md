# Development and releases

[Back to the README](../README.md)

## Contributing

Bug reports, device testing, documentation fixes and focused pull requests are welcome. Search [existing issues](https://github.com/Today20092/voice-input/issues) before opening one. For a larger feature or new model, discuss its use case in an issue first, including its download size, runtime needs and license.

For a bug, include the app version, phone and Android version, keyboard/editor, selected model and language, reproduction steps, and expected versus actual behavior. Mention microphone or headset routing when relevant. Review diagnostic exports before sharing and remove private audio, transcripts and personal details.

For model testing, report recording length, time from Stop to final text, recognition errors and any memory or heat problems. State whether results come from this Android app or a publisher benchmark. A report with a clear procedure is more useful than an unsupported speed or accuracy ranking.

For code changes, read [AGENTS.md](../AGENTS.md), link the relevant GitHub issue, keep changes focused and preserve upstream attribution and model notices. Follow the build steps below and run the unit tests and lint for affected code. Changes to microphone routing, text insertion or model inference also need device evidence; document any checks you could not perform. Do not commit signing keys, private recordings or local SDK configuration.

## Low-memory workflow

When the maintainer requests low memory or sets a RAM limit, perform compilation, tests, lint, native/model checks and APK assembly in GitHub CI. Keep local work to source edits and lightweight static checks. Use a connected phone, an emulator or local heavyweight builds only after the user explicitly allows their memory use; a connected device alone is not permission.

After the applicable CI checks pass, publish the signed APK as a GitHub prerelease beta for the maintainer to install and test later. Retain the tested commit and CI links, and clearly list device/emulator checks that have not run. Compiling Android tests does not execute them or satisfy physical-device release gates. Keep the production application ID/signing configuration so installing the beta preserves existing data; stable promotion remains a separate decision.

## Build locally


Install JDK 21, Android SDK platform 36, build tools `36.0.0`, NDK `28.2.13676358`, and CMake `3.22.1`. Initialize the repository's submodules. Point `local.properties` at your Android SDK, or set `ANDROID_HOME`. On Windows, use `git -c core.longpaths=true submodule update --init --recursive` to check out the nested native dependencies.

The Gradle wrapper and Android Gradle Plugin follow the upstream integration documented in [the merge record](testing/upstream-master-integration-2026-10-30.md). The app keeps its existing target SDK, package IDs, version sequence, model defaults, and native-runtime pins.

On macOS or Linux:

```bash
git submodule update --init --recursive
./gradlew :app:assembleDevDebug
./gradlew :app:testDevDebugUnitTest :app:lintDevDebug
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`.

Debug APKs are written to `app/build/outputs/apk/dev/debug/`. For a development build with bundled Parakeet assets, add `-PbundleParakeetModel=true`.

Standalone builds use `:app:assembleStandaloneRelease` and write to `app/build/outputs/apk/standalone/release/`. GitHub Actions supplies the release signing configuration, runs release unit tests, and verifies the APK signature and ARM64 native libraries.

## Releases and repository

[`master`](https://github.com/Today20092/voice-input/tree/master) contains the combined work. Release tags preserve the source for published APKs; historical beta branches may remain available. Historical downloads remain in [GitHub Releases](https://github.com/Today20092/voice-input/releases).

The [release workflow](../.github/workflows/release-apk.yml) builds on configured branch pushes and `v*` tags. A tag run publishes an APK; a branch run uploads an artifact. Before a new release, update the app version and add notes at `docs/releases/<tag>.md`, which the workflow selects automatically. Do not reuse an existing release tag.

For a low-memory beta release, dispatch `release-apk.yml` on the reviewed source
ref with `beta_tag` set to the new `vMAJOR.MINOR.PATCH-beta.N` tag. It must match
`app/build.gradle`'s version and must not already exist. CI signs and verifies
the normal standalone APK before creating the prerelease at that exact commit.
An empty `beta_tag` retains the build-only manual workflow. Normal release
builds explicitly disable S1 speculation and verify its absence from the APK.

Current verification is recorded in the [1.4.6 release notes](releases/v1.4.6.md) and [successful stable release run](https://github.com/Today20092/voice-input/actions/runs/37829884596). Older test counts belong to their dated release notes.

## Stable release requirements

Release maintainers: stable tags must use `vMAJOR.MINOR.PATCH`, with an asset named `futo-voice-input-moonshine-vMAJOR.MINOR.PATCH.apk`. Increment both `versionName` and Android `versionCode` for each stable release, and keep the existing signing key and application ID. The updater compares numeric version-name components because GitHub does not expose APK version codes.
