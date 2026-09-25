# History beta 1

An independent beta from `codex/history-usability`, based on stable 1.4.2 plus
the existing documentation commit `0ee8922`. It does not include the other
in-progress feature branches.

## Changes

- Audio history rows show transcript previews, limited to three lines. Opening an
  entry still shows the full text for selection and copying.
- Clear history asks for confirmation before deleting saved audio and transcripts.
- Recordings currently being captured or transcribed are kept. The screen reports
  deleted, in-use, and failed counts after clearing.
- Existing audio recovery, retention settings, and individual deletion remain.

Text-only history, audio-storage limits, and automatic clipboard copying are not
part of this beta.

## Installation and manual checks

The signed APK uses the existing app ID and version code 54. It replaces the
installed Moonshine fork rather than creating a second app. Its filename identifies
this branch; the app version remains 1.4.2. GitHub builds the APK and runs unit tests;
no local build or test run was performed.

1. Dictate two phrases and a longer paragraph. Open Audio history and check that
   previews help identify them. Open the paragraph and copy its full text.
2. Choose Clear history, then cancel. Confirm the entries remain.
3. Clear again and confirm. Check the result counts and empty list.
4. Retranscribe an entry, then clear history while it is processing. That entry
   should remain; other inactive entries should be deleted.
5. Dictate again to confirm new history still saves. Check individual deletion too.

Use disposable recordings for deletion checks. Keep each branch's APK for testing
and compare them separately before merging their source changes.
