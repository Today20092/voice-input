# Should we adopt the upstream share architecture?

## Recommendation

Keep our current recognition architecture. Adopt useful upstream behavior in small changes, and evaluate transcribe.cpp as an optional adapter before considering replacement.

Using FUTO Keyboard does not require moving our recognition code into upstream's shared module. The immediate improvement is to make the keyboard reliably select and launch our installed app. We can do that while retaining Orukeet, our model choices, audio history, transcript cleanup, diagnostics, and current Activity/IME behavior.

This is a recommendation about the present code and your use of the standalone fork with FUTO Keyboard. If we later maintain our own keyboard build and embed this recognizer inside it, a reusable Android library would have a real second consumer and should be reconsidered.

## Why keep the current separation?

Our app already has useful interfaces:

- Activity and IME callers use RecognizerView and AudioRecognizer.
- RecordingSession owns capture, stopping, buffered audio, and recognition work.
- RecognitionModelLifecycle owns loading, selection, readiness, and runtime release.
- SpeechBackend and StreamingSpeechBackend allow different inference implementations.

Upstream's shared module combines Android recording, recognition UI, model loaders, and a model manager that returns its WhisperGGML wrapper for all supported models. Its wrapper now calls transcribe.cpp. This is useful for sharing code between FUTO's two products, but it is not inherently a better fit for our multiple runtimes.

Replacing our stack would require re-integrating behavior we already have. It would also contradict [ADR 0001](../adr/0001-use-sherpa-onnx-for-nvidia-streaming.md), which chose Sherpa-ONNX for NVIDIA recognition. A successful comparative experiment could justify a new ADR; the branch's existence does not.

There is still worthwhile architectural work: make IME text insertion a coherent session operation, keep recording UI state together, and make capability/readiness data consistent. Those are bounded changes at existing interfaces, not a wholesale rewrite.

## FUTO Keyboard integration

Two different changes were bundled together upstream:

| Change | What it gives us | Decision |
| --- | --- | --- |
| Shared Android recognition library | Code compiled into another application's build | Defer. Installing a library in our APK cannot replace code inside an already installed keyboard APK. |
| VoiceInputSwitch activity protocol | Configures the keyboard to use a specified external voice-input package | Adopt, SHARE-01. This directly helps your setup. |

I inspected FUTO Keyboard at revision 70a5d390c505a6bbcc4e14966e5628e43ca3f1fc. Its [VoiceInputSwitchActivity](https://github.com/futo-org/android-keyboard/blob/70a5d390c505a6bbcc4e14966e5628e43ca3f1fc/java/src/org/futo/inputmethod/latin/uix/settings/VoiceInputSwitchActivity.kt) accepts a targetPackage plus check, switch, or popup mode. Switching writes the selected package and enables external voice input. Our released package is org.futo.voiceinput.moonshine; use the actual installed application ID so dev builds work too.

The check mode compares the remembered target package, so it is not by itself proof that external input is currently enabled. Our UI should handle cancellation, unsupported older keyboards, delayed results, and manual fallback honestly. The installed keyboard version on your phone was not checked in this review.

This integration routes dictation to our standalone app. It does not automatically share model downloads, settings, memory, or an embedded keyboard UI between the two APKs.

## Engine comparison

| Consideration | Our current implementation | Upstream share | Implication |
| --- | --- | --- | --- |
| Runtime organization | Multiple adapters, including Sherpa-ONNX, Moonshine, and legacy Whisper | A common transcribe.cpp wrapper and GGUF/legacy model loading | Their approach may reduce duplicated runtime work if it can replace enough of ours. Adding it initially increases our runtime count. |
| Model formats | Existing installed artifacts and pinned catalog entries | GGUF for additional model families | Existing ONNX packages cannot simply be reused as GGUF. Conversion, validation, downloads, and rollback need explicit work. |
| Acceleration | Runtime-specific; measure on the phone | The reviewed JNI code explicitly selects TRANSCRIBE_BACKEND_CPU | This branch does not demonstrate GPU acceleration or a speed win. |
| Existing model coverage | Orukeet, Moonshine Small/Medium, NVIDIA choices, Cohere, legacy Whisper | ASR4ALL S/M/L, Parakeet 110M/Unified, Moonshine Small, Nemotron English/multilingual, Whisper | Their app catalog does not establish parity for every model we ship. Generic engine support is not app-level integration. |
| Streaming | Native live transcription plus buffered Parakeet Unified | Engine streaming tiers and batch fallback | Streaming itself is already present here. Compare quality, partial stability, finalization, and memory, not just first partial latency. |
| Reproducibility | Our pinned dependencies and existing build/tests | FUTO-specific transcribe.cpp gitlink | Resolve its source before adopting it. |

The [upstream JNI initialization](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/app/src/main/cpp/voiceinput.cpp#L100-L118) selects the CPU. The public engine's documentation describes broader backend and model support, but that is not proof of the behavior of FUTO's pinned version.

The dependency points to https://gitlab.futo.org/keyboard/transcribe.cpp.git at b9e8a8e348db9a34052b72f8643bbe4597522181. An anonymous clone requested authentication in this session. GitHub's API reported that this commit is absent from handy-computer/transcribe.cpp. Consequently, I reviewed the app's integration and gitlink changes, but could not audit the exact dependency changes or build that revision. This is a reproducibility gap, not evidence that the engine is bad.

SHARE-09 is therefore a bounded experiment. Find reproducible source, integrate behind the existing interface in an experimental build, check coexistence with S1's pinned llama.cpp/GGML, and measure a shared model family on the phone. No default changes or removal of Sherpa/Whisper are authorized by that experiment.

## Feature decisions

| Feature | Recommendation and reason | Work |
| --- | --- | --- |
| Keyboard provider selection and settings launching | Adopt. Direct benefit when using FUTO Keyboard; independent of engine migration. | SHARE-01 |
| IME spacing, repeated partials, cursor/selection handling | Adopt the behavior, with safer rules. Current code spaces only after selected punctuation and sends every partial to setComposingText. Upstream's cursor-forcing heuristic should not be copied blindly. | SHARE-02 |
| Manual model upgrades | Reconsider with a small explicit workflow. The old implementation was deliberately removed; it is not currently available. | SHARE-03 |
| Missing/update-ready notices and refresh | Add on top of version-aware state. Optional updates must not block working dictation. | SHARE-04 |
| Model-specific language and capability settings | Adopt. Our Languages page handles Cohere and Parakeet TDT specially, but can otherwise fall through to Whisper controls and download effects. | SHARE-05 |
| Bluetooth microphone selection | Adopt as an explicit option. Upstream exposes routing and microphone feedback; no explicit Bluetooth route-selection implementation was found in our app source. Device verification is required. | SHARE-06 |
| Partial transcript alongside recording/processing feedback | Adopt the state-management idea while retaining our waveform. Our waveform/status/partial callbacks replace the content independently. | SHARE-07 |
| Streaming failure recovery | Add one bounded replay path for explicitly recoverable failures. Keep existing catch-up behavior; do not automatically re-run slow models just because they lag. | SHARE-08 |
| transcribe.cpp | Experiment first; no wholesale engine migration. | SHARE-09 |
| ASR4ALL Small | First ASR4ALL candidate. Ship only if a measured benefit justifies another runtime/model; defer Medium/Large. | SHARE-10 |
| Parakeet TDT/CTC 110M | Evaluate as a smaller English option against Moonshine Small and existing TDT. Model size alone is not a quality or speed result. | SHARE-11 |
| Help/setup and README comparison | Update the branch-specific facts. Preserve fork-specific installation and update instructions. | SHARE-12 |
| Model subtitles and details | Already substantially implemented; use the existing unfinished Model Options ticket for remaining validation instead of duplicating it. | Existing ticket |
| Missing-model recovery and progress state | Already present. Preserve them; no new feature ticket. | Existing tests |
| Safe-area insets | Our outer theme already applies safeDrawingPadding. Do not add duplicate padding on the strength of upstream's patch alone. | Verify with relevant UI work |
| Global streaming tiers | Keep current per-model profiles. Their default tier is no streaming; copying it would change our product behavior. | No separate port |
| Tail decoding | Their native patch computes preview text during streaming. Our recorder tail drain captures trailing audio after Stop. These are different operations. | Engine experiment only |
| Language-bail replay | Their consumed-Flow bug does not directly apply to our Whisper FloatArray fallback. | Preserve guarantees in SHARE-08/09 |
| NDK/AGP/SDK upgrades and additional ABIs | Do not copy a bundled version bump. The fork ships ARM64, and some of our UI dependencies are already newer than theirs. Upgrade a dependency only with a concrete compatibility need and its tests. | Gate within SHARE-09 if needed |
| Upstream release checks, GitLab CI, nightly branding | Keep our GitHub Releases distribution and package identity. | No port |
| Removing navigation transitions | Conflicts with the direction of our existing predictive-back task. | Keep existing task |
| Shared cache/settings rewrites and old migration removal | No demonstrated need here; importing them would broaden risk without delivering the desired keyboard integration. | Defer |

The existing Model Options ticket remains the place for compact model summaries, details, and variant labels. SHARE-05 addresses the separate language/settings behavior and must reuse the same presentation data where available.

## Correction to the earlier comparison

The earlier overview was too coarse to support a merge decision. In particular, the tracker says safe manual model updates were completed in 683be27, but 47566e2 later deliberately removed that machinery as speculative. Current RecognitionModelStore has one catalog-defined directory and marker per model; its findUpdate, completeUpdate, and activateUpdate implementation is gone. Current ConditionalModelUpdate is the older Whisper/TFLite migration notice.

Our runtime-release and integrity-validation work remains useful, but it is not a versioned upgrade workflow. SHARE-03 reopens the existing ticket with this history recorded. It must not resurrect an elaborate remote update service or show fictitious available upgrades.

## Order of work

1. SHARE-01, SHARE-02, and SHARE-05: keyboard selection, text insertion, and truthful language settings.
2. SHARE-03 then SHARE-04: safe manual upgrades followed by notices. Keep transfers explicit.
3. SHARE-06, SHARE-07, and SHARE-08: microphone choice, coherent live UI, and recoverable failure handling.
4. SHARE-09: separate engine feasibility and measurement work. SHARE-10/11 depend on a successful reproducible integration and an evidence-based go/no-go result.
5. SHARE-12: update product documentation alongside the features that actually ship.

A negative result is a valid completion for an evaluation ticket. Do not ship extra models merely to match upstream's menu. Revisit the architecture only if measurements show a useful replacement across our required models, or if we start maintaining a keyboard build that embeds our code.

## All 35 upstream-only commits

Every upstream-only app-repository commit was inspected at the feature/diff level. Large code moves and vendored-code deletion were treated as architectural changes, not as a line-by-line audit of inference algorithms. Gitlink-only entries below explicitly retain the dependency-source limitation.

| Commit | Subject | Decision |
| --- | --- | --- |
| [27966c9](https://github.com/futo-org/voice-input/commit/27966c9) | Switch to keyboard's shared RecognizerView | Keep our session/backend separation. Take Bluetooth routing and coherent UI state ideas through SHARE-06/07. Do not remove legacy migration or import its global settings/model caches wholesale. |
| [9413511](https://github.com/futo-org/voice-input/commit/9413511) | Use MutableState in DownloadActivity | Already covered: our ModelInfo properties use mutableStateOf and progress updates run through updateModelOnMain. |
| [e93865c](https://github.com/futo-org/voice-input/commit/e93865c) | UI refresh, transcribe.cpp integration, asr4all | Split the bundle: keyboard setup SHARE-01; UI behavior SHARE-07; engine experiment SHARE-09; models SHARE-10/11. Keep current theme, migration, release scheme, and backend choices. |
| [41f77ec](https://github.com/futo-org/voice-input/commit/41f77ec) | Update README | Refresh our comparison and help through SHARE-12; do not replace our README with theirs. |
| [8b6a94e](https://github.com/futo-org/voice-input/commit/8b6a94e) | Update CI | Skip GitLab branch/version plumbing. Our release workflow and version numbering are different. |
| [bb9cf13](https://github.com/futo-org/voice-input/commit/bb9cf13) | Update | Skip GitLab variable/wrapper corrections and the dummy test. Keep meaningful existing tests. |
| [dd6e7c5](https://github.com/futo-org/voice-input/commit/dd6e7c5) | Make uploadNightly executable | Skip; we do not use their nightly publishing script. |
| [70a9ea3](https://github.com/futo-org/voice-input/commit/70a9ea3) | Fix update check and version | Skip FUTO URL cache-busting and GitLab history settings. Our updater uses the fork's GitHub Releases endpoint. |
| [9c7a627](https://github.com/futo-org/voice-input/commit/9c7a627) | Implement basic streaming recognition while recording | We already stream Moonshine/Nemotron and buffer Parakeet Unified. Preserve our capture/session code; assess their engine only in SHARE-09. |
| [53db738](https://github.com/futo-org/voice-input/commit/53db738) | Add tail decoding for reduced latency, and offline fallback | Adopt coherent partial UI and bounded recovery concepts in SHARE-07/08. Native preview-tail tuning belongs to SHARE-09/10, not our recorder tail-drain policy. |
| [c3a0d9b](https://github.com/futo-org/voice-input/commit/c3a0d9b) | Do not crash on missing model | Equivalent user flow already exists: readiness checks return to model-download UI before capture. Preserve it in future adapters. |
| [3ff9ba5](https://github.com/futo-org/voice-input/commit/3ff9ba5) | Remove enter/exit settings transitions | Do not copy. The existing predictive-back ticket governs navigation behavior. |
| [a66d6e8](https://github.com/futo-org/voice-input/commit/a66d6e8) | Fix missing insets on a couple menus | No direct port: our outer UixThemeAuto already applies safeDrawingPadding to settings. Verify during relevant UI changes; avoid double padding. |
| [76dc7a1](https://github.com/futo-org/voice-input/commit/76dc7a1) | Fix launching keyboard's settings | Adopt resilient launching and package visibility as part of SHARE-01. Do not copy unrelated manifest permission changes blindly. |
| [696eadc](https://github.com/futo-org/voice-input/commit/696eadc) | Reduce update frequency on dev version | Skip their cadence policy. The actual patch selects six hours for dev and two days otherwise and checks on settings start; its title alone is misleading. |
| [9b3cd79](https://github.com/futo-org/voice-input/commit/9b3cd79) | Update dev version icon | Skip upstream branding assets; preserve distinct fork distribution. |
| [bf8dc4c](https://github.com/futo-org/voice-input/commit/bf8dc4c) | Integrate with Keyboard activity for switching voice input | Adopt through SHARE-01. This inter-app activity protocol is independent of extracting shared recognition code. |
| [86949dd](https://github.com/futo-org/voice-input/commit/86949dd) | Update string | Adapt setup wording for our app in SHARE-01/12. |
| [65178f8](https://github.com/futo-org/voice-input/commit/65178f8) | Add asr4all-s | Conditional ASR4ALL Small pilot, SHARE-10. The engine gitlink also changes; its internal delta is not independently available here. |
| [207625e](https://github.com/futo-org/voice-input/commit/207625e) | Add additional models | Overlapping Nemotron/Moonshine families do not need duplicate defaults. Consider optional GGUF comparisons in SHARE-09. Preserve our working language selection. |
| [7633e85](https://github.com/futo-org/voice-input/commit/7633e85) | Add additional indicators in settings, improve Nemotron | Adopt model-specific language/capability presentation in SHARE-05 and the existing Model Options ticket. Do not remove our Nemotron auto-detection or cross-model vocabulary corrections. |
| [d4c2bd5](https://github.com/futo-org/voice-input/commit/d4c2bd5) | Add streaming tier option | Existing profiles cover the main need. Keep them; new engine-specific profiles may be added only with measured behavior in SHARE-09/10. No extra universal tier selector now. |
| [aa52c5b](https://github.com/futo-org/voice-input/commit/aa52c5b) | Add Nemotron English | Already available with profiles through Sherpa-ONNX. No duplicate model ticket. |
| [00d649b](https://github.com/futo-org/voice-input/commit/00d649b) | Add space automatically and try to recover from selections | Adapt the intent with stricter selection/session tests in SHARE-02; do not copy cursor-forcing heuristics verbatim. |
| [92e96b2](https://github.com/futo-org/voice-input/commit/92e96b2) | Avoid repeated setComposingText calls | Adopt in SHARE-02 with state reset between sessions and successful-call tracking. |
| [867667a](https://github.com/futo-org/voice-input/commit/867667a) | Update transcribe.cpp and implement model update UI | Implement version-aware upgrades and notices through SHARE-03/04. Preserve validation and staged activation; do not substitute filename existence for integrity. |
| [f4d444b](https://github.com/futo-org/voice-input/commit/f4d444b) | Fix x86 and armv7 compile | Pointer-only engine change; internal source unavailable. No current ARM64 product need. Any engine experiment must reproduce its own supported build. |
| [4dfd8c4](https://github.com/futo-org/voice-input/commit/4dfd8c4) | Unload models after finishing download of potential upgrade | We already release managed runtime artifacts before current downloads. SHARE-03 must coordinate replacement with active sessions; do not copy GlobalScope cleanup after replacement. |
| [a15c965](https://github.com/futo-org/voice-input/commit/a15c965) | Update help menu a bit | Adapt factual setup/help in SHARE-12. Treat upstream's SwiftKey text-loss warning as a report, not a bug reproduced in our build. |
| [2a2f560](https://github.com/futo-org/voice-input/commit/2a2f560) | Update NDK | No standalone port. Keep NDK 28.2 unless SHARE-09 or a demonstrated compatibility problem needs a tested change; upstream uses 30.0.16248370. |
| [fbdf329](https://github.com/futo-org/voice-input/commit/fbdf329) | Change modelsSubtitle | Our modelsSubtitle already uses selectedRecognitionModelSummary. Retain it and finish verification in the existing Model Options ticket. |
| [3525563](https://github.com/futo-org/voice-input/commit/3525563) | Update transcribe.cpp | Pointer-only update to b9e8a8e. SHARE-09 must resolve source availability and changes before use; no inferred performance benefit. |
| [301cd97](https://github.com/futo-org/voice-input/commit/301cd97) | Fix Whisper model names | Upstream corrects typos introduced in its rewritten catalog. Our presentation has its own names; preserve correct labels in the existing Model Options ticket. |
| [dcd9d2b](https://github.com/futo-org/voice-input/commit/dcd9d2b) | Fix bail language not working | Their fix replays audio consumed from a Flow when switching models. Our Whisper fallback passes the same retained FloatArray, so this patch does not apply directly. Preserve replay guarantees in SHARE-08/09. |
| [95e82ad](https://github.com/futo-org/voice-input/commit/95e82ad) | Refresh model update condition after download | Adopt reactive refresh in SHARE-04 for install/delete/selection/upgrade/resume, not just a global download counter. Skip the development-only destructive 'Outdate models' button. |

## Evidence and limits

Compared freshly fetched upstream share 95e82ada5e2513484dccf68295a5ddde6d223730 with fork master c7a28c4facf50f67942221d8185ca2ae4dfb4401 on September 26, 2026. Their merge base is d6e1eb2d139dc1a6342a4681c283686cca4bfceb. The branches have 35 upstream-only and 142 fork-only commits; those numbers are not quality rankings.

Material fork evidence was checked in AudioRecognizer.kt, RecognizerView.kt, VoiceInputMethodService.kt, backend/SpeechBackend.kt, recognition/RecognitionModelLifecycle.kt, recognition/RecognitionModelCatalog.kt, downloader/DownloadActivity.kt, settings/pages/Models.kt, settings/pages/Languages.kt, theme/Theme.kt, migration/MigrationScreen.kt, nemotron/NemotronBackend.kt, ml/WhisperModel.kt, build configuration, the updater, and existing tickets/tests.

The local graph generation was August 28 and reported changed paths, native parse gaps, and excluded third-party code. Searches and coverage checks were followed by current Git source reads; empty graph call traces were not treated as proof that code has no callers. Upstream's shared tree was inspected from Git directly.

Primary references include the [upstream model catalog](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/shared/src/main/java/org/futo/voiceinput/shared/Models.kt), [model loader and presence states](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/shared/src/main/java/org/futo/voiceinput/shared/types/ModelData.kt), [streaming wrapper](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/shared/src/main/java/org/futo/voiceinput/shared/ggml/WhisperGGML.kt), [keyboard switching caller](https://github.com/futo-org/voice-input/blob/95e82ada5e2513484dccf68295a5ddde6d223730/app/src/main/java/org/futo/voiceinput/settings/Hooks.kt), and [our current store](https://github.com/Today20092/voice-input/blob/c7a28c4facf50f67942221d8185ca2ae4dfb4401/app/src/main/java/org/futo/voiceinput/recognition/RecognitionModelCatalog.kt). Context7 documentation for the public engine and Sherpa was supplementary; the checked-in app code determines claims about these revisions.

No app code was changed, no APK was built, and no runtime benchmark or phone compatibility test was performed. Tickets distinguish implementation from device validation. See [the adoption specification](../specs/upstream-share-adoption.md) and [the local tracker](../../tickets.md).
