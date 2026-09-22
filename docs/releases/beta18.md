## Waveform visibility and bulk dictionary entry

Built directly on beta 17, retaining its waveform, organized settings, models, S1-mini cleanup, audio backups, and retranscription.

- A taller waveform with adaptive display gain makes quiet speech easier to see. Partial bars no longer wait for a complete 20 ms interval. Display scaling never changes recorded audio or recognition input.
- Personal Dictionary supports pasting multiple entries and importing UTF-8 text files up to 1 MiB. Review additions, skip duplicates, and fix malformed mappings before saving. Existing entries stay intact.
- Orukeet uses these dictionary corrections after recognition and optional cleanup. This does not train or directly guide the model. Use `heard phrase => preferred phrase` for exact corrections.
- Exact dictionary spellings take precedence over fuzzy matches, preventing similar entries such as `inshaAllah` and `mashaAllah` from overwriting one another. Literal punctuation in mapping replacements is preserved.
- Back up recordings and other iconless settings now align with the page margin. Audio-history explanations use supporting-text sizing.

## Optional transliteration list

Download `arabic-transliteration.txt`, then open **Speech → Personal Dictionary → Import text file**. Preview and edit the spellings before adding them. The list includes `inshaAllah` and common phrases and vocabulary. It is an optional editable example, not bundled or automatically enabled in the app.

## Validation and phone checks

Unit tests cover waveform visibility, silence, reset, unchanged PCM, UTF-8 validation, import size limits, duplicate handling, the optional transliteration list, and a 1,000-entry dictionary. Emulator UI tests cover bulk-entry preview, cancellation and saving, audio history, and row alignment at narrow/wide phone widths, enlarged text, and light/dark themes. The standalone release build and Android lint are checked before publication.

On your phone, check waveform onset with quiet speech, stop/cancel, and audio-history retranscription. The reported one-to-two-second microphone startup delay has not been reproduced on physical hardware; larger display gain and immediate partial bars address visibility, but do not establish a fix for device-specific capture delays. Debug builds expose content-free `WaveformTiming` timestamps for recorder start, first samples, UI update, and first waveform frame.

## Installation

Signed standalone arm64 APK, version code 53. Install over beta 17 to retain models, settings, dictionary entries, and audio history. Models are not bundled. Do not uninstall or clear app data to update.
