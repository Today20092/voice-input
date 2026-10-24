# Ticket 16: upstream keyboard setup remains blocked

Reviewed October 24, 2026. [Issue #16](https://github.com/Today20092/voice-input/issues/16) requires a **published compatible upstream release**, followed by physical setup/provider/cancellation/dictation verification before restoring the settings entry.

GitHub's latest published stable is still [FUTO Keyboard 0.1.30](https://github.com/futo-org/android-keyboard/releases/tag/0.1.30), published August 4, 2026 at 20:52:29 UTC. Its tag resolves directly to commit `0246cbe2fb2f130a5dc475c508215760044dea20`. The latest ten published releases also include 0.1.30-rc1 and older candidates, with no later published compatibility release.

The pinned [complete tag tree](https://github.com/futo-org/android-keyboard/tree/0246cbe2fb2f130a5dc475c508215760044dea20) was not truncated and contains no `VoiceInputSwitchActivity`. None of these tag-pinned manifests declares the expected switch action or activity:

- [java/AndroidManifest.xml](https://github.com/futo-org/android-keyboard/blob/0246cbe2fb2f130a5dc475c508215760044dea20/java/AndroidManifest.xml)
- [java/stable/AndroidManifest.xml](https://github.com/futo-org/android-keyboard/blob/0246cbe2fb2f130a5dc475c508215760044dea20/java/stable/AndroidManifest.xml)
- [java/playstore/AndroidManifest.xml](https://github.com/futo-org/android-keyboard/blob/0246cbe2fb2f130a5dc475c508215760044dea20/java/playstore/AndroidManifest.xml)
- [voiceinput-shared/src/main/AndroidManifest.xml](https://github.com/futo-org/android-keyboard/blob/0246cbe2fb2f130a5dc475c508215760044dea20/voiceinput-shared/src/main/AndroidManifest.xml)

This app's retained `settings/KeyboardProvider.kt` addresses `org.futo.inputmethod.latin.action.VoiceInputSwitch` in the stable/unstable keyboard package, passing the actual installed app identity as `targetPackage` and read-only `check` or explicit `switch` as `mode`. `KeyboardProviderSession` expects result 67/68 for remembered-provider checks, 66 for a successful switch, and 0 for cancellation. Remembered status is deliberately distinct from enabled external input. The setup screen/route, missing-activity/manual fallback, strings and tests remain. `settings/pages/Home.kt` keeps only the entry hidden with its #16 comment.

Earlier [SHARE-01 evidence](share-01-keyboard-provider.md) already checked an official 0.1.30 APK and the unsupported/manual fallback. That historical emulator evidence does not establish interoperability with a protocol-supporting release. Current unreleased upstream source, an older screenshot or an activity in a development build would not satisfy this issue's published-release requirement.

## Required frontier

1. Identify a newer published keyboard tag/APK implementing the contract, or document a supported replacement contract from that release's source. Record exact tag/commit, APK identity and supported stable/unstable packages; compare actions, extras, exported visibility and result semantics.
2. On a physical phone, verify read-only provider checks, explicit selection, remembered-versus-enabled status, microphone dictation/insertion, cancellation back to typing, manual settings refresh, recreation and unsupported fallback. Record keyboard/app/device versions; include TalkBack, large text and landscape/system insets.
3. Restore the entry and update instructions only after those checks establish support; rerun `KeyboardProviderSessionTest`, `KeyboardProviderTest` and affected navigation tests against the final integration build.

No compatible published release was found, so no runtime/UI change is justified. Keep #16 open with `needs-info`. This audit used read-only GitHub APIs, source inspection and `git diff --check`; it downloaded no APK, started no build/emulator and performed no device setup. Blocking upstream support cannot be replaced by CI compilation or fabricated phone results.
