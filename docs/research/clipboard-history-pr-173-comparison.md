# Clipboard and history comparison

Keep our recovery implementation. Borrow the simpler transcript browsing and bulk-clear ideas from PR #173; do not copy the whole patch. A text-only history option would be the most useful larger addition.

Reviewed September 25, 2026: local commit `0ee8922a33f65686f92f7f43501c7c88022d749f`, and [PR #173](https://github.com/futo-org/voice-input/pull/173) at `f29039a59149f2acb02990246c330b2ac743888d`. This was source inspection, not an Android device test. No implementation changes were made.

## What differs

Neither implementation is a general clipboard-history manager that records everything copied on the phone. Both keep dictation history. The PR additionally offers automatic copying of every completed transcription.

| Behavior | Our implementation | PR #173 |
|---|---|---|
| Saved content | Audio plus its transcript | Completed, nonblank transcript only |
| Default | History enabled, 24-hour retention | History enabled, last 20 results |
| Recovery after recognition fails | Saved audio can be transcribed again using current model/settings | No entry if recognition never produces a result |
| Recovery after text delivery fails | Open history and copy saved text | Tap a history row to copy; optional automatic clipboard copy |
| Retention | Configurable 1–720 hours, with scheduled and on-access cleanup | Count-based; no time expiry |
| Browsing | Date/duration, then open an entry to see text | Text visible directly, with relative timestamps |
| Deletion | Individual entry, with confirmation | Clear the whole list; no individual deletion or confirmation in the added screen |
| Storage | Separate PCM/text files in `noBackupFilesDir` | JSON array in existing preferences DataStore |
| Automatic clipboard writes | Not in the reviewed history flow | Optional, disabled by default |

PR behavior is visible in its [settings](https://github.com/js-commit/voice-input/blob/f29039a59149f2acb02990246c330b2ac743888d/app/src/main/java/org/futo/voiceinput/settings/Settings.kt), [history screen](https://github.com/js-commit/voice-input/blob/f29039a59149f2acb02990246c330b2ac743888d/app/src/main/java/org/futo/voiceinput/settings/pages/History.kt), and [result handling](https://github.com/js-commit/voice-input/blob/f29039a59149f2acb02990246c330b2ac743888d/app/src/main/java/org/futo/voiceinput/RecognizerView.kt).

## Where ours is better

Our recovery starts while recording, before recognition succeeds. PCM is written incrementally, and interrupted files can be read back. The recognizer saves raw text before cleanup, then replaces it with the final text. This covers more failures than a history hook that runs only after a successful result. See [AudioRecognizer.kt](/Users/haithumalqahaf/Downloads/Ayoub/voice-input/app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt:824).

The storage implementation also protects recordings in use from deletion, replaces transcripts through a temporary file and atomic move, and refuses to recreate text for deleted recordings. Failed or canceled retranscription preserves the previous text. These behaviors have six focused store tests, inspected but not run during this comparison. See [AudioHistoryStore.kt](/Users/haithumalqahaf/Downloads/Ayoub/voice-input/app/src/main/java/org/futo/voiceinput/history/AudioHistoryStore.kt:94) and [AudioHistoryStoreTest.kt](/Users/haithumalqahaf/Downloads/Ayoub/voice-input/app/src/test/java/org/futo/voiceinput/history/AudioHistoryStoreTest.kt:16).

Time-based retention limits how long old content remains. The PR's last 20 results can remain indefinitely if the user stops dictating. Our expiry is cleanup-driven, however, not guaranteed deletion at the exact deadline; active recordings/retranscription are protected.

## Where ours is worse

Saving raw audio by default has a larger privacy and storage cost. It retains the voice and whatever the microphone captured, not just recognized words. The cancel path closes the recording but does not delete nonempty audio, so canceled recordings can remain recoverable. That is useful for recovery but deserves explicit wording in settings.

Our 16 kHz, mono, 16-bit PCM costs approximately 1.92 MB per recorded minute, or 115 MB per recorded hour. The store has an age limit but no byte/count cap. The PR is much lighter because it keeps only text, although 20 entries is not a strict byte limit either.

Our list makes finding an old sentence slower: users see date/duration and must open entries to read them. It also lacks bulk clear. See [AudioHistory screen](/Users/haithumalqahaf/Downloads/Ayoub/voice-input/app/src/main/java/org/futo/voiceinput/settings/pages/AudioHistory.kt:105).

## Why I would not copy their storage code

The PR reads the JSON history and later writes a replacement in a separate operation. Two overlapping saves can read the same old list and overwrite one another. A pending save can likewise restore entries after clear-all. This is a code-level race risk, not a reproduced device failure. If adopting text-only storage, perform the whole read/modify/write within one serialized transaction.

Malformed existing JSON prevents the append from completing, and the catch only prints a stack trace. Our recording-save failures notify the user.

Their `NonCancellable` block aims to let a write survive activity teardown. It does not make storage process-death-proof, and it sits inside a lifecycle-scoped launch, so it should not be described as universally loss-proof. Our transcript writes already happen before final result dispatch; there is no reason to transplant that asynchronous hook.

## What to borrow

1. **Show a short transcript preview in each history row.** Keep our date, duration, open, retry, copy, and individual-delete controls.
2. **Add “Clear history” with confirmation.** Keep protection for entries actively recording or transcribing, and report anything that could not be removed.
3. **Offer text-only history alongside audio recovery.** This serves users who want to recover dropped text without retaining their voice. It requires separating transcript retention from audio existence, rather than copying the PR's JSON append routine.
4. **Consider optional automatic copy only if manual recovery is too slow.** Keep it off by default, explain that it replaces the clipboard, perform it before result delivery, and isolate clipboard exceptions. It is a convenience feature, not detection or repair of failed delivery.

My priority would be previews and clear-all first, then a text-only mode and an audio-storage cap. Automatic copying is lower priority because both implementations already support manual copying from history.

The graph index was dated August 28 and did not cover the new history files reliably. All material history findings above were checked against current source instead.
