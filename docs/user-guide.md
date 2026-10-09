# User guide

[Back to the README](../README.md)

## Get started

1. Install the signed APK from [GitHub Releases](https://github.com/Today20092/voice-input/releases/latest). Android 8.0 or newer and an ARM64 device are required.
2. Open the app's settings, grant microphone permission, and follow the voice-input setup.
3. Open **Model Options**, keep Orukeet or select another recognizer, and confirm its download.
4. Start dictating. Live text depends on the model; the final result is delivered after recording stops.

The stable app uses `org.futo.voiceinput.moonshine`, a separate package from upstream FUTO Voice Input. Stable 1.4.6 updates this fork's earlier releases and tested betas while retaining settings and downloaded models. Existing model selections are preserved.

Harper English cleanup is optional and off by default. Turn off **Enable Harper cleanup** in the cleanup settings to bypass it; existing beta preferences are preserved. It requires no model download. See the [Harper removal guide](harper-removal.md) for removing its code and native dependency from a future release.

### App updates and Obtainium

<p align="center">
  <a href="https://github.com/Today20092/voice-input/releases/latest"><img alt="Download APK from GitHub" src="https://img.shields.io/badge/GitHub-Download%20APK-B5DFE8?style=for-the-badge&amp;logo=github&amp;logoColor=B5DFE8&amp;labelColor=29626B"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22org.futo.voiceinput.moonshine%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FToday20092%2Fvoice-input%22%2C%22author%22%3A%22Today20092%22%2C%22name%22%3A%22FUTO%20Voice%20Input%20Moonshine%22%7D"><img alt="Add this app to Obtainium" src="https://img.shields.io/badge/Obtainium-Add%20app-C4A7E7?style=for-the-badge&amp;labelColor=493267"></a>
</p>

In builds containing the GitHub updater, **Check for updates** in settings checks this fork's latest stable GitHub release and reports whether an update is available. The update link opens the signed APK in your browser; Android asks you to approve installation. Failed checks do not mean the app is up to date. Beta releases are excluded. Older APKs must be updated manually once to receive this updater.

For update notifications and downloads, [add this app to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22org.futo.voiceinput.moonshine%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FToday20092%2Fvoice-input%22%2C%22author%22%3A%22Today20092%22%2C%22name%22%3A%22FUTO%20Voice%20Input%20Moonshine%22%7D) or tap the badge above. The link opens Obtainium with this fork's app configuration; review and confirm the import. If Obtainium is not installed, the redirect page offers an installation link. This follows [Obtainium's documented deep-link format](https://wiki.obtainium.imranr.dev/deep_links/).

To add it manually, use `https://github.com/Today20092/voice-input` as the app source. Leave prereleases disabled for stable updates. The repository publishes one signed ARM64 APK per release, so no APK filter is needed. See [Obtainium's source documentation](https://wiki.obtainium.imranr.dev/sources/).

This app is not listed in F-Droid's main repository. Its [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) requires free-software licensing; this project's [FUTO Source First license](../LICENSE.md) restricts commercial use. A separate F-Droid-compatible repository would need its own hosting, signing, and index maintenance. GitHub Releases and Obtainium are the supported download paths for now.

## Keyboard setup

For Android's voice input method, enable Voice Input Moonshine in Android's
input-method settings and select it using the system keyboard picker when
available. The Help page's **Open input method settings** button opens those
settings. Names and picker placement vary by device.

FUTO Keyboard's built-in recognizer and this separately installed app are
different choices. Installing this fork does not automatically select it
inside FUTO Keyboard. Stable 1.4.6 hides the dedicated provider setup entry
until an upstream release supports it. Follow
[issue #16](https://github.com/Today20092/voice-input/issues/16) for restoration
and compatibility work.

If your keyboard exposes external or system voice input, choose this app there
or in Android's app chooser, then test a short dictation and cancellation.
Available controls depend on the keyboard version. Phone/editor dictation,
Bluetooth routing and spoken TalkBack coverage remain incomplete.

Upstream's [help change](https://github.com/futo-org/voice-input/commit/a15c965)
reports text loss with Microsoft SwiftKey. That report has not been reproduced
in this fork.

## Personal dictionary and cleanup

Use **Personal Dictionary** for predictable corrections, such as `heard phrase => preferred phrase`. Bulk paste and UTF-8 imports up to 1 MiB let you preview additions, skip duplicates, and fix invalid mappings. These are text corrections; they do not train Orukeet or supply it with recognition hints. The optional [Arabic transliteration example](examples/arabic-transliteration.txt) is an editable starting point, not enabled automatically.

Enable **S1-mini by Superwhisper** under **Transcript Cleanup** for local English rewriting after Stop. It is off by default. Choose style, structure, context, and how long to keep the model warm. If cleanup times out or fails, the app keeps the raw transcript. Final dictionary corrections run after cleanup.

Known non-English input bypasses cleanup. For recognizers that cannot report a language, enabled cleanup assumes English; turn it off for non-English dictation on those paths.

The first cleanup run benchmarks CPU configurations and experimental OpenCL. Auto chooses OpenCL only when its output passes validation and it is at least 15% faster than the best CPU result. Runtime controls and content-free S1 diagnostics are available in settings.

## Audio history and recovery

Open **Audio history** to find recordings by transcript preview, view and copy full text, or retranscribe with the current model, language, dictionary, and cleanup settings. Keep the history screen open while retranscription runs.

Each recording has direct Retranscribe, Copy text, and Delete controls. **Show full transcript** appears when the preview is cut off; Copy text always copies the complete saved transcript.

Recording backups are on by default with 24-hour retention, adjustable from 1 to 720 hours. Saved audio includes canceled and failed attempts so it can be recovered. Storage failures show a warning without blocking ordinary dictation. Audio uses about 1.9 MB per minute in private storage excluded from Android backup.

Delete individual entries or confirm **Clear history** to remove inactive recordings and transcripts. Active recordings and retranscriptions are protected. Turning backups off stops new saves; shortening retention removes older entries. Uninstalling the app or clearing its data removes recordings.

Expiry is checked during app use and by a periodic Android job. Android may delay background deletion while the app or device is stopped.

## Popup and recording UI

The recognition UI shows the selected model and a scrolling waveform driven directly by microphone amplitude. Under **Advanced**, enable **Unobtrusive recognizer popup (beta)** to move the speech-recognition activity near the bottom and remove background dimming. It does not change the voice keyboard layout or recognition engine.

## Diagnostics and privacy

Open **Settings → Support → Diagnostics → Export bug report**, add optional notes, review the summary, and share the ZIP yourself. For intermittent issues, enable detailed mode before reproducing the problem; it stops automatically after 30 minutes.

Standard diagnostics stay local and are on by default, with an off switch and a Clear action. They cover recording, model loading, recognition, cleanup, delivery, downloads, managed failures, and available Android process-exit reasons. Retained standard evidence, including prepared reports, is bounded to seven days and 10 MB.

Standard reports exclude audio, dictated text, personal vocabulary, clipboard or surrounding text, receiving-app names, raw Logcat, URLs, and exception messages. Notes you type into a report are included as entered. Transcript-inclusive exports are separate and require explicit consent. Nothing uploads automatically.

Crash evidence is best effort, and delivery records cannot prove how another app displayed the text. Diagnostics recording and sharing still need a device smoke test. See [the diagnostics guide](diagnostics.md) for archive contents, retention details, and verification.

To collect comparable measurements on your phone, follow the [phone dictation test](phone-performance-test.md). It includes three passages, cold and warm run instructions, and the diagnostic export procedure. Controlled model comparisons will be added after those reports are reviewed.

An [initial S25 Ultra diagnostic baseline](research/phone-diagnostics-baseline-2026-09-25.md) contains 27 completed Orukeet dictations on 1.4.3. The median wait from Stop to result-ready was **0.85 seconds**, ranging from **0.59 to 2.32 seconds** for recordings of 2.88 to 47.88 seconds. These are ordinary-use observations, with no controlled cold/warm split or accuracy assessment.
