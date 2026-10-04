# Share beta 7 UI release evidence

Date: October 4, 2026. Source: tag `v1.4.6-share-beta.7`, branch
`codex/parakeet-redux`. Scope: model presentation and the focused test beta.

## Local checks

Using Android Studio's JBR:

```powershell
.\gradlew.bat :app:testStandaloneReleaseUnitTest :app:lintStandaloneRelease :app:assembleDevDebug :app:assembleDevDebugAndroidTest
```

Passed: 205 JVM tests, zero failures, one existing skip; release lint; APK
and instrumentation assembly. An obsolete Cohere Beta-label assertion was
updated before the successful run.

## Physical UI checks

Samsung Galaxy S25 Ultra, Android 16; separate `org.futo.voiceinput.dev` package.
No speech model downloads or inference were performed by these UI tests.

- `ManagedRecognitionModelCatalogTest`: six tests passed, covering Redux,
  Nemotron multilingual/profiles, buffered Unified, visible Whisper variants,
  and scrollable managed/Whisper Details at large fonts.
- `ModelReleaseScreenshotsTest`: one test passed, checking selected-model
  download action, live group, Redux download/model sizes, and source buttons.
  It saves actual Compose UI pixels, excluding system bars. Preferences are
  restored after each test.
- Fresh captures were visually inspected and are in
  `docs/screenshots/v1.4.6-share-beta.7/`.

The screenshot fixture uses the app's Material Surface and theme so inherited
text colors match the production screen. Screenshot exports contain no
notification text or personal recordings.

## Publication checks

The tag's release workflow must pass JVM tests, lint, Android-test compilation,
Harper notice generation and nine Rust tests, signed standalone assembly,
native APK content checks and signature verification before publication.
The public APK has app ID `org.futo.voiceinput.moonshine`, version code 68,
and ARM64 libraries. The stable release must remain latest.

## Issue disposition

- #7: retain beta 5's download correction; close the reported download failure
  after publishing, based on the reporter's October 4 confirmation. The reply
  does not establish a beta version or working dictation.
- #10: UI milestone complete; broader editor, headset and accessibility device
  criteria remain open.
- #15: focused signed beta milestone; no master merge or claim of completed
  comparative calibration.
- #16: hidden settings entry with implementation preserved; compatible upstream
  release and provider testing still required.
- #8 and #11–#14: unfinished requirements remain open. `tickets.md` is the
  historical migration record; GitHub Issues is authoritative.
