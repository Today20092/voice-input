# Disabling or removing Harper

Harper is optional. Its usefulness is still being evaluated; it is not required by any speech backend or by S1-mini.

## Disable it now

Turn off **Enable Harper cleanup** in the cleanup settings. `HARPER_ENABLED` defaults to false for new installs. Upgrades preserve the existing preference. The disabled path returns the input without calling the native adapter, so speech recognition, S1-mini settings, and personal vocabulary remain available. Disabling does not remove the bundled native library from the APK.

## Remove it from a future release

Make a focused removal commit instead of reverting all of PR #4, which also changed release metadata and diagnostics. The following map describes the 1.4.4 integration; search for remaining `Harper`, `HARPER`, and `harper` references when implementing removal.

1. In `AudioRecognizer.kt`, remove the Harper invocation and its `HARPER_FINISHED` event. Pass `cleanupResult.text` directly to `PersonalVocabulary.apply`, keeping S1-mini before personal vocabulary and preserving cancellation, result delivery, and history handling.
2. Remove the `harper/` Kotlin package, its unit tests, `settings/pages/Harper.kt`, the `HarperOptions` call in `settings/pages/S1Mini.kt`, and Harper-specific settings text in `settings/pages/Home.kt`. Remove the two Harper settings declarations once no code reads them. Old DataStore keys can remain unused; do not clear users' settings or app data.
3. Remove `app/src/main/cpp/harper_jni.cpp`, the Harper target block in `app/src/main/cpp/CMakeLists.txt`, and `app/src/main/rust/harper_android/`. Keep the unrelated native targets and libraries.
4. Remove the Harper Rust setup/test/license-generation steps and `libharper.so` / `HARPER-NOTICES.txt` packaging assertions from `.github/workflows/release-apk.yml`. Remove `tools/harper_notices.py` and any generated Harper notices from APK assets. Check that no other component needs Rust before removing toolchain setup elsewhere.
5. Remove Harper-specific live settings export and credits. Preserve compatibility when reading retained diagnostic events: keep the historical event/metric identifiers and outcome labels until retained Harper records no longer need them. Do not renumber persisted identifiers.
6. Update current documentation and ignore rules. Keep historical release notes and attribution with historical source; remove attribution from the current APK only after its Harper dependencies are absent.
7. Run the standalone release unit tests and build. Verify the signed APK has no Harper native library or notices, while other native libraries remain. Check dictation with S1-mini off, personal vocabulary, and an upgrade from 1.4.4 with Harper previously enabled. Confirm old diagnostics still export successfully.

Keep the same application ID and signing key, and increase the version code. Users should not need to uninstall or lose downloaded models to receive a Harper-free update.
