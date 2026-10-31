# Assess adopting upstream's shared recognizer

Tracked in [GitHub #48](https://github.com/Today20092/voice-input/issues/48).

Follow-up: the [runnable prototype evidence and recommendation](shared-recognizer-reproducibility-2026-10-30.md)
supersede the prototype-not-yet-run status below. This document retains the
initial static assessment.

## Finding

Adopting the shared module may help this standalone fork follow upstream changes.
A second application maintained by us is not a prerequisite for that benefit.
However, the inspected module is not a drop-in replacement for our recognition
flow. Recommend a bounded compatibility prototype and an explicit go/no-go
decision before committing to a production migration.

This is a static source assessment of fork `cf83820`, upstream Voice Input
`c4590aa`, and keyboard `31e720ddcc2df6259069b2ae7cf0f1defaf2cb74`. No prototype,
new build, runtime comparison, or migration was performed for this assessment.
Existing phone checks in the [bundled-model report](../testing/asr4all-bundled-default-2026-10-30.md)
establish the current baseline, not compatibility with the shared module.

## What upstream shares

FUTO Keyboard imports shared `RecognizerView`, `ModelManager`, and model types
for embedded recognition, while also exposing an external voice-input action.
Therefore the module has a real reuse purpose across FUTO's products. Adding it
to our app can align our source with upstream, but cannot replace code already
compiled into an installed keyboard APK. Provider setup/release compatibility is
a separate concern. [Keyboard source](https://github.com/futo-org/android-keyboard/blob/31e720ddcc2df6259069b2ae7cf0f1defaf2cb74/java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt)

The exact current extension limits are:

| Upstream component | Concrete dependency | Consequence for this fork |
| --- | --- | --- |
| `RecognizerView` | Accepts `ModelManager`; privately constructs `AudioRecognizer` | No supplied recorder/session factory to reuse our `RecordingSession` unchanged. |
| `AudioRecognizer` | Constructs `MultiModelRunner(modelManager)` and passes a `Flow<FloatArray>` | No existing `SpeechBackend` injection seam; audio ownership and finalization must be adapted. |
| `MultiModelRunner` | Obtains models through `ModelManager`; invokes `WhisperGGML.infer`; applies glossary processing to partial/final text | Reusing our backends needs a runner boundary and a defined vocabulary-processing owner. |
| `ModelManager` | Caches and returns concrete `shared.ggml.WhisperGGML` instances | Our Sherpa, ExecuTorch, Moonshine and other adapters cannot implement this concrete return type. |
| `ModelLoader` | `loadGGML(context): WhisperGGML` | Its catalog interface is coupled to upstream's runtime wrapper despite supporting several model families. |

These classes are declared without `open`, so ordinary subclass overrides do not
provide the missing seams. A maintained source patch or accepted upstream
extension is required. Sources: [view](https://github.com/futo-org/voice-input/blob/c4590aa/shared/src/main/java/org/futo/voiceinput/shared/RecognizerView.kt),
[recorder](https://github.com/futo-org/voice-input/blob/c4590aa/shared/src/main/java/org/futo/voiceinput/shared/AudioRecognizer.kt),
[runner](https://github.com/futo-org/voice-input/blob/c4590aa/shared/src/main/java/org/futo/voiceinput/shared/whisper/MultiModelRunner.kt),
[manager](https://github.com/futo-org/voice-input/blob/c4590aa/shared/src/main/java/org/futo/voiceinput/shared/whisper/ModelManager.kt),
[model types](https://github.com/futo-org/voice-input/blob/c4590aa/shared/src/main/java/org/futo/voiceinput/shared/types/ModelData.kt).

The module build also automatically downloads/bundles an ASR4ALL Medium **GGUF**
model. Our included Medium package is **ExecuTorch**, with different artifacts and
integrity checks. Native JNI is supplied by upstream's app build, and registers
the shared wrapper class. Our `libvoiceinput` registers the legacy wrapper class;
it cannot simply satisfy the shared wrapper's native methods. Importing the
library must not add duplicate models, unneeded runtimes, or colliding GGML/JNI
symbols. [Library build](https://github.com/futo-org/voice-input/blob/c4590aa/shared/build.gradle),
[upstream JNI](https://github.com/futo-org/voice-input/blob/c4590aa/app/src/main/cpp/voiceinput.cpp),
[fork JNI](../../app/src/main/cpp/voiceinput.cpp), [included package](../../app/src/main/java/org/futo/voiceinput/asr4all/BundledAsr4all.kt).

## Three viable directions

| Direction | Benefit | Cost and decision |
| --- | --- | --- |
| Keep fork orchestration; selectively reuse shared UI/types/helpers | Lowest recording-policy risk; some direct upstream reuse | Static UI functions can be called while retaining our recorder, but shared `RecognizerView` cannot accept it unchanged. This is partial reuse, not a full recognizer migration; report that limit honestly. |
| Adapt the shared recognizer around our runtime and application-policy boundaries | Could reuse future upstream recording/UI fixes while retaining current models and features | Recommended prototype. Requires measured source patches for runner/recorder injection and policy hooks. Count those patches and the code actually reused before claiming lower maintenance. |
| Adopt upstream recording, model manager/catalog and transcribe.cpp together; port fork features afterward | Closest to upstream's complete design | Largest coupled change: model/runtime migration plus app-policy ports. Do not choose this by default. It requires a separate runtime decision, reproducible source/native builds, model compatibility and device evidence. |

The second option is a hypothesis, not a proven best design. If the required
patches recreate most of our orchestration inside shared code, retain selective
reuse and record a no-go instead of forcing a nominal migration.

## Application contracts that must survive

- **Recognition models:** retain all existing `SpeechBackend` and
  `StreamingSpeechBackend` adapters, language detection, live/final-only behavior,
  catching-up feedback, and included ASR4ALL Medium's offline cache recovery.
  Keep saved selection keys and optional downloadable Whisper choices.
  [Interfaces](../../app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt),
  [lifecycle](../../app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelLifecycle.kt).
- **One recording/session owner:** keep complete retained audio, recorder tail
  handling, stop-during-load behavior, cancellation/late-result suppression,
  terminal OOM handling, runtime release and microphone-route cleanup. Upstream's
  recorder cancels its recording job on finish and completes a stop signal; that
  is not evidence of parity with our session policy. Avoid two simultaneous
  recorders or a second competing model lease.
  [Session](../../app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt).
- **Audio history and retranscription:** preserve canceled/no-result recordings,
  expiry/deletion and the old transcript after a failed/canceled retranscription.
  The inspected upstream listener has no retained-audio/history contract; add a
  narrow PCM/session lifecycle hook or retain the fork recorder explicitly.
  [History](../../app/src/main/java/org/futo/voiceinput/history/AudioHistory.kt).
- **Final transcript:** preserve conditional S1 cleanup (including ASR4ALL bypass),
  Harper, then personal vocabulary corrections, before result handoff. Prevent
  double vocabulary replacement when upstream glossary processing is used.
  [Finalizer](../../app/src/main/java/org/futo/voiceinput/TranscriptFinalizer.kt).
- **Diagnostics and delivery:** keep standard diagnostics content-free; retain
  explicit transcript-capture opt-in, retention and actual handoff outcomes.
  Preserve one activity result and IME composition/input-session ownership.
  [Diagnostics](../../app/src/main/java/org/futo/voiceinput/diagnostics/AppDiagnostics.kt),
  [activity](../../app/src/main/java/org/futo/voiceinput/RecognizeActivity.kt),
  [IME](../../app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt).
- **Model ownership:** retain staged validation, activation serialized against
  active sessions/benchmarks, readiness versus optional upgrades, explicit
  download confirmation, selection preservation and safe deletion. Upstream's
  second model cache must not bypass `RecognitionModelLifecycle`.

## Bounded proof and acceptance

1. Pin import provenance and produce a before/after dependency map. Separate
   reusable module changes from optional transcribe.cpp/JNI work; establish
   reproducible retrieval and builds if that native path is exercised. Do not
   carry forward old source-availability assumptions without rechecking them.
2. Choose **one** recorder/runner seam. Prove shared recording/view behavior with
   existing included ASR4ALL Medium and one final-only existing adapter, without
   replacing production defaults/catalog/storage or adding another model bundle.
3. Demonstrate cancellation and finalization through both Activity and IME test
   callers, complete PCM retention/history, and unchanged cleanup/vocabulary order.
   Test a model-update/session race and no late/double delivery. Use deterministic
   doubles first, then native known-audio tests on the S25.
4. Run the existing session, history, cleanup, diagnostics/privacy, model
   lifecycle, UI and insertion regression suites; add seam-specific tests. Verify
   native coexistence, artifact sizes and first-load/partial/final timing against
   the same pinned fixture/settings. Do not infer battery or broad accuracy gains.
5. Report reused upstream files, maintained patch surface, conflicts with recent
   upstream changes, remaining parity work and a go/no-go recommendation. A full
   migration requires a separate implementation plan and resolved feature gates.

[ADR 0004](../adr/0004-keep-external-keyboard-integration.md) and the
[adoption specification](../specs/upstream-share-adoption.md) must permit this
upstream-alignment evaluation without a keyboard fork. ADRs 0001–0003 remain
constraints on runtime selection, cleanup isolation and diagnostic privacy;
module adoption alone does not supersede them. Keyboard setup issue #16 is not
a prerequisite for this assessment. Optional-engine evaluation under #13 is a
separate dependency only if the chosen proof needs that engine.
