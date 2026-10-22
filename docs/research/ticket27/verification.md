# Ticket 27: compact voice-input panel

Issue: https://github.com/Today20092/voice-input/issues/27

## Height ownership

`VoiceInputMethodService.onCreateInputView()` supplies this app's Compose view to Android's IME window. `RecognizerInputMethodWindow()` owns full-width, wrap-content sizing. The previous implementation added a separate cancel/header row, an empty 24dp spacer, 64dp bottom padding, and the bottom system-bar inset. `InnerRecognize()` reserved 120dp for the waveform. No FUTO Keyboard height setting is consulted in this path. Android still supplies the available window size and system insets; this does not establish which build produced the original user screenshot.

The IME now uses a 48dp waveform, adjacent 48dp finish/cancel controls, and 4dp vertical padding. The bottom system inset remains. Normal recording no longer displays the provisional transcript. Recognition state and the partial/final delivery callbacks remain unchanged. The separate recognition activity retains its expanded layout and transcript preview.

The waveform reduces the existing four-second PCM envelope to spaced, rounded vertical bars. It preserves peaks and uses fixed gain rather than normalizing quiet audio to full height. Silence produces short bars. Updates follow captured audio directly, with no timer, decorative animation, or added interpolation. Permission, download, and failure prompts can scroll at large font sizes in short windows; cancel remains outside the scrolling content.

## Verification

Device: Samsung SM-S938U, ARM64, 1440×3120 pixels, density 600dpi. FUTO Keyboard is installed and was the selected input method at the start of testing.

The earlier local debug APK, version `1.4.6-share-beta.7-dev`, commit `1f7a4e4`, passed the existing large-text IME UI test on this phone. Its synthetic fixture screenshot is [before-large-text.png](before-large-text.png). That fixture has a 360dp height limit and uses 2× text. It is a baseline fixture, not the user's original private document screenshot.

Validation results and remaining device checks are recorded below after execution.

## Standards review

No documented-standard violations or actionable defects were found in the scoped changes. A second review covered prompt scrolling and the resource-backed stop icon. Both follow existing Compose patterns and preserve the existing recognition architecture.

## Spec review

The first review identified possible prompt clipping at large font sizes in a short landscape window. The fix gives permission, failure, and model-download prompts scrolling and tests their actions with 2× text in a 280×180dp container. Re-review found no remaining implementation mismatch. Execution results remain necessary to establish actual phone behavior.

The review excludes concurrent Model Options changes and unrelated local documents. No model/backend/default changes belong to this ticket.
