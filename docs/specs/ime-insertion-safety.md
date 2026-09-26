# IME insertion safety

SHARE-02 implements the keyboard constraints in [upstream share adoption](upstream-share-adoption.md).

Each dictation owns one `ImeInsertionSession` and one `InputConnection`. A new input session, editor change, connection replacement, cancellation, completion, or ordinary input-view dismissal ends that ownership. Android configuration-driven recreation of the input view preserves the active session when the editor and connection remain the same.

Before each write, the adapter reads the current selection and text. A successful composing write records its absolute start and the exact accepted text. Later writes require that text to remain at that position with the cursor at its end. `onUpdateSelection` also rejects loss or movement of the composing range. Delayed acknowledgments of this session's own earlier composing writes are accepted. Failed writes are not remembered as successful. A successful final write closes the session, so subsequent delivery cannot commit again.

If ownership is lost, delivery returns false and the existing recognition failure UI is used. The app does not move the cursor, delete surrounding text, or insert the final transcript at another position. Cancellation retains already accepted partial text and finishes composition on the bound connection without changing text or selection. Before its first write, a new session also finishes any residual composition so it cannot replace an earlier utterance accidentally. It does not attempt to restore an earlier selection because that could overwrite a user's edits. Existing audio history remains the recovery path when enabled.

An editor without extracted selection offsets receives previews in the recognition view and a final-only native commit. The adapter reads at most two characters on each side for spacing in this fallback. If surrounding text is unavailable, no boundary spaces are inferred. An absent or replaced connection rejects delivery. Editor exception messages and surrounding text never reach diagnostics or Logcat.

Boundary spacing is recomputed around the replacement, excluding the previous composition and the selected text. Existing whitespace and punctuation are preserved. Chinese, Japanese, Thai, Lao, Khmer, and Myanmar scripts do not acquire automatic spaces; a final language tag for these languages also disables boundary spaces. Structured input fields such as numbers, URLs, email addresses, and passwords do not receive inferred spaces. Blank interim hypotheses leave the selection and previous partial alone. An empty final clears only a verified owned composition; otherwise it leaves selected text alone.

Android editor operations are asynchronous. Their Boolean result reports the input-connection operation, not independent confirmation of what the receiving app displays. Fresh text checks and composition callbacks protect against observed edits; they cannot make an arbitrary third-party editor transactional. No composing-span serialization or guessed cursor movement is used.

Reference contracts: [InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection), [ExtractedText offsets](https://developer.android.com/reference/android/view/inputmethod/ExtractedText), and [InputMethodService lifecycle and selection callbacks](https://developer.android.com/reference/android/inputmethodservice/InputMethodService).

## Verification

On 2026-09-28, using Android Studio's JDK 21, the pinned native submodules, `CMAKE_BUILD_PARALLEL_LEVEL=2`, and the existing Android SDK:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug :app:assembleDevDebugAndroidTest :app:lintDevDebug --max-workers=2 --offline
adb -s emulator-5554 shell am instrument -w -r -e class org.futo.voiceinput.ImeInsertionEditorTest org.futo.voiceinput.dev.test/androidx.test.runner.AndroidJUnitRunner
```

The Gradle checks passed. The JVM suite reported 154 tests, zero failures/errors and one existing skipped test, including all 13 insertion/lifecycle tests passing. All five Android editor tests passed on the Pixel_10 Android 37 emulator with ARM64 translation. The failed native checkout was repaired by enabling Git long-path support for the single missing llama.cpp file; all submodule pins remained unchanged.

### Standards review

No actionable documented-standard violations or unnecessary abstractions were found. Surrounding text stays transient, editor exception content is suppressed, and recognition-generation checks protect delayed partial and final delivery.

### Spec review

Review found a retained composing span that could replace an earlier utterance after cursor movement and cancellation. The regression was reproduced, fixed, and covered by JVM and native editor tests. Review also requested recreation coverage; the service now uses a lifecycle owner tested through editor operations across recreation, dismissal, and editor changes. Follow-up review found no remaining actionable code findings. Android's asynchronous callbacks still require end-to-end verification: a delayed composition-cleared notification may conservatively reject delivery rather than risk overwriting text.

### Remaining device checks

`ImeInsertionSessionTest` checks the editor contract independently of Android. `ImeInsertionEditorTest` exercises the adapter through Android's actual `EditText` input connection. These checks do not replace the following end-to-end device checks:

- Dictate final-only and streaming results into a native text editor and a WebView editor. Select existing text, move the cursor, edit while recognition is pending, and verify existing text survives.
- Rotate during recording and after a partial. Confirm recording continues and the final replaces the same composition exactly once.
- Switch fields/apps during recognition; late partials and final results must not reach the new editor.
- Cancel, dictate the same utterance twice, and exercise cleanup and personal vocabulary with final text different from the partial.
- Test the FUTO Keyboard provider flow and confirm recognition-activity results remain unchanged. Provider interoperability is coordinated with SHARE-01.
