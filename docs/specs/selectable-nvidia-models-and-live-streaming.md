# Selectable NVIDIA recognition models and live transcription

## Problem Statement

People using voice input have different phones, storage budgets, language needs, and accuracy expectations. The app currently provides a small set of coarse backend choices, while its NVIDIA path produces only final text and is tied to a custom native runtime and a single model download layout. A person who values fast live feedback, multilingual recognition, or better accuracy on a newer phone cannot choose the NVIDIA recognition model and streaming profile that fits them. Model installation, retention, deletion, provenance, and performance expectations are also not presented consistently.

## Solution

Provide a model catalog in Model Options that keeps Moonshine Small as the default while offering Parakeet TDT 0.6B V3, Parakeet Unified EN 0.6B, Nemotron Speech Streaming EN 0.6B, and Nemotron 3.5 ASR Streaming 0.6B. Each recognition model explains why someone might choose it, its approximate download size, language support, transcription behavior, and advisory performance class.

Models and Nemotron streaming profiles are downloaded only after confirmation, retained independently, and deleted explicitly. NVIDIA models use Sherpa-ONNX as their sole runtime. Streaming models provide revisable composing text while speech is in progress; final-only models are labeled honestly. Slower devices preserve every audio sample and may pause partial updates while catching up.

## User Stories

1. As a person dictating text, I want words to appear while I am speaking, so that I can see whether recognition is working before I finish.
2. As a person dictating text, I want the final transcript to replace provisional text cleanly, so that corrections made by the recognizer do not leave duplicate words.
3. As a person using a slower phone, I want the app to preserve all of my speech when recognition falls behind, so that responsiveness never costs missing words.
4. As a person using a slower phone, I want to see a Catching up state, so that paused partial text is not mistaken for a frozen app.
5. As a person using a newer phone, I want to select a more demanding recognition model, so that I can trade hardware resources for better dictation.
6. As a person using an older phone, I want Moonshine Small to remain the default, so that an upgrade does not silently make voice input heavier.
7. As a person choosing a model, I want a short explanation of its strengths, so that technical model names do not require outside research.
8. As a person choosing a model, I want to see whether it provides live or final-only transcription, so that I know when text will appear.
9. As a person choosing a model, I want to see whether it is Light, Balanced, or Demanding, so that I can make an informed choice for my phone.
10. As a person choosing a model, I want performance classes to be advisory rather than blocking, so that the app does not make unreliable assumptions from my phone model.
11. As a person choosing a model, I want to see its language support, so that I do not download a model that cannot transcribe my speech.
12. As an English-only user, I want Nemotron Streaming English, so that I can prioritize low-latency English transcription.
13. As a multilingual user, I want Nemotron 3.5, so that one installed recognition model can serve multiple supported languages.
14. As a multilingual user, I want an Auto-detect recognition language, so that I do not have to change the language before every utterance.
15. As a multilingual user, I want only out-of-the-box languages displayed, so that adaptation-ready languages are not falsely advertised as functional.
16. As a person prioritizing offline accuracy over live feedback, I want Parakeet TDT to remain available, so that final-only recognition is still a valid choice.
17. As a person wanting one English model for offline and buffered live use, I want Parakeet Unified available, so that I can choose its accuracy/latency trade-off.
18. As a Nemotron English user, I want Low latency, Balanced, and Accuracy profiles, so that I can tune live transcription for my priorities.
19. As a Nemotron English user, I want Balanced selected by default, so that the initial profile is a reasonable latency/accuracy compromise.
20. As a person browsing models, I want profiles grouped inside one model card, so that the main list is not filled with duplicate model names.
21. As a person managing storage, I want each profile's installation state shown independently, so that I understand which large package occupies space.
22. As a person selecting an uninstalled model or profile, I want its download size and required free space shown before download, so that a large transfer is never surprising.
23. As a person on cellular data, I want the confirmation to authorize the download explicitly, so that cellular use remains my choice.
24. As a person completing a download, I want the downloaded model or profile selected automatically, so that I can use what I just requested.
25. As a person who switches models, I want installed downloads retained, so that switching back does not require another large transfer.
26. As a person managing storage, I want an explicit delete action for every inactive installed model or profile, so that I control disk usage.
27. As a person using the selected model, I want its delete action disabled until I select another installed model, so that voice input is never left without a usable selection.
28. As a person deleting model assets, I want the runtime released before files are removed, so that deletion is reliable and does not corrupt an active session.
29. As a person downloading model assets, I want incomplete or corrupt downloads rejected, so that a completion marker never represents unusable files.
30. As a person downloading community conversions, I want their source shown, so that artifact provenance is transparent.
31. As a person downloading community conversions, I want immutable revisions and hashes checked, so that mutable or corrupt remote files cannot silently replace the expected model.
32. As a person with a working model version, I want updates to be manual, so that a large background update does not consume data or break recognition unexpectedly.
33. As a person updating a model, I want the working version retained until the replacement validates, so that a failed update does not remove my usable model.
34. As a person whose selected model cannot load, I want the download retained and another installed model offered, so that a temporary runtime problem does not destroy a large download.
35. As a privacy-conscious user, I want all recognition to remain offline after model download, so that dictated audio is not sent to a service.
36. As an IME user, I want live partial text inserted as composing text, so that provisional recognition can be revised without committing duplicates.
37. As a recognition-activity user, I want partial text visible in the recognition UI and only final text returned to the caller, so that the platform contract remains predictable.
38. As a personal-dictionary user, I want vocabulary substitutions applied consistently to partial and final text, so that live feedback matches the committed result.
39. As an app maintainer, I want one NVIDIA inference stack, so that TDT, Unified, and Nemotron do not require parallel custom native implementations.
40. As an app maintainer, I want the existing NVIDIA runtime removed only after parity checks, so that consolidation does not regress working recognition.
41. As an app maintainer, I want Moonshine and legacy Whisper paths preserved, so that the NVIDIA migration does not remove intentional fallbacks.
42. As an app maintainer, I want attribution and model-license information shipped with each model entry, so that distributing model integrations respects their separate licenses.
43. As an app maintainer, I want real-device latency, memory, and thermal measurements, so that performance descriptions reflect phones rather than desktop benchmarks.
44. As a person on an S24/S25-class phone, I want Balanced live transcription to keep pace with speech, so that demanding models feel genuinely interactive.
45. As a person using Balanced mode on a modern phone, I want the first useful partial and a caught-up final result within one second, so that live dictation feels responsive.

## Implementation Decisions

- Keep Moonshine Small as the default recognition model.
- Preserve Moonshine and legacy Whisper behavior and selection paths.
- Add model cards for Parakeet TDT 0.6B V3, Parakeet Unified EN 0.6B, Nemotron Speech Streaming EN 0.6B, and Nemotron 3.5 ASR Streaming 0.6B.
- Treat recognition models as user-facing products rather than exposing serialization formats or conversion repositories as separate choices.
- Label Parakeet TDT as final-only. Do not simulate native cache-aware behavior or imply that it produces true live transcription.
- Present Parakeet Unified as buffered streaming; do not describe it as cache-aware or as supporting the 80 ms Nemotron operating point.
- Present Nemotron Streaming English as the preferred low-latency English NVIDIA model.
- Present Nemotron 3.5 as the multilingual choice with explicit supported recognition languages and Auto-detect.
- Hide Nemotron 3.5 adaptation-ready languages until independently fine-tuned working artifacts exist.
- Use one model card per recognition model. Put profile selection and profile installation state inside that card.
- Offer Nemotron English profiles for 80 ms Low latency, 160 ms Balanced, and 560 ms Accuracy.
- Treat each Nemotron profile as a separate model package and download. Never imply that changing the profile is a free runtime-only setting.
- Use a single recognition model store as the authority for catalog metadata, artifacts, installed state, selection, deletion eligibility, and available updates.
- Continue using the existing speech-backend contracts as the boundary between audio capture/UI and recognition runtimes.
- Use Sherpa-ONNX as the sole NVIDIA recognition runtime, following the accepted architecture decision.
- Stage runtime replacement: first prove Nemotron English live transcription and device behavior; then move Parakeet TDT and Unified after parity checks; then add Nemotron 3.5 languages; finally remove the redundant Rust/JNI runtime and obsolete native build wiring.
- Integrate a Sherpa-ONNX release that supports buffered Unified, cache-aware English Nemotron profiles, and multilingual Nemotron 3.5.
- Package only the app's initially supported arm64 ABI; broader ABI support is not part of this work.
- Resolve native ONNX Runtime packaging deliberately. Do not package incompatible duplicate runtime libraries or depend on first-match packaging behavior.
- Keep downloaded model assets outside the APK. Bundled-model build behavior may remain only where already supported and must not cause every selectable model to ship in normal APKs.
- Give each model package its own directory, immutable identity/version, file manifest, and completion marker.
- Download community-hosted artifacts directly when suitable, but pin every URL to an immutable revision and require a hash for every file.
- Derive displayed download size and required storage from the selected artifact manifest rather than hard-coding estimates from model cards.
- Show one confirmation before any uninstalled model/profile download. The confirmation includes source, transfer size, required free space, and whether the current network is cellular.
- Allow a confirmed download over cellular.
- Validate every artifact before writing the completion marker and selecting the model.
- Retain all installed models and profiles until the user deletes them.
- Disable deletion for the selected model/profile and instruct the user to select another installed choice first.
- Release any warm runtime/session before deleting or replacing its assets.
- Never update model packages automatically. Present updates as an explicit user action.
- Keep the previous valid version until a newly downloaded version is complete and validated.
- On model-load failure, keep downloaded assets and offer installed alternatives rather than deleting the package automatically.
- Use Light, Balanced, and Demanding as advisory performance classes. Do not gate installation by model name, RAM heuristic, chipset, or phone brand.
- Preserve every recorded audio sample when streaming inference falls behind. Pause partial publication if needed, show Catching up, and prioritize a complete final transcript.
- Reuse existing IME composing-text and recognition-overlay behavior for live partial results.
- Apply personal vocabulary consistently to partial and final results.
- Keep inference offline after artifact download; add no recognition telemetry or cloud fallback.
- Surface license/attribution information for Sherpa-ONNX and each model family in the appropriate app notices/model details.

## Testing Decisions

- Tests assert externally visible model-management and recognition behavior, not private Sherpa, JNI, C++, or decoder implementation details.
- The primary model-management seam is the recognition model store. JVM tests cover catalog entries, model/profile identities, manifest sizes, immutable URLs, required hashes, completion markers, installed-state detection, selection, inactive-only deletion, version replacement, and fallback offers.
- The primary recognition seam is the existing streaming speech-backend contract. Tests use a fake backend to verify audio-chunk forwarding, partial publication, finalization, cancellation, personal-vocabulary application, and Catching up/back-pressure behavior.
- Android UI tests cover model-card descriptions, performance classes, live/final-only labels, language choices, grouped profile controls, install/update/delete states, confirmation details, cellular confirmation, progress, and selected-model deletion guards.
- Downloader tests cover insufficient space, interrupted transfer, HTTP failure, hash mismatch, retry, atomic validation, completion-marker timing, and retaining the previous version during an update.
- Selection tests cover selecting an installed package immediately and selecting a newly downloaded package only after validation.
- Failure tests cover Sherpa initialization errors, native-library conflicts, out-of-memory loading, unavailable input connections, and switching to another installed model without deleting the failed package.
- Native integration smoke tests load each pinned artifact family and transcribe a short known audio sample. Large model assets are opt-in/local CI inputs rather than ordinary repository fixtures.
- Parity checks compare Sherpa TDT and Unified results against representative known utterances before the custom NVIDIA runtime is removed.
- Real-device checks measure first-partial latency, finalization latency, real-time factor, peak memory, sustained thermals, and backlog behavior for all Nemotron profiles.
- Initial acceptance on an S24/S25-class device is first useful Balanced-profile text within one second, a caught-up final within one second after stop, no dropped audio, and inference throughput at least as fast as incoming audio.
- Slower devices are expected to enter Catching up without data loss; they are not required to meet the modern-phone timing target.
- Release verification compares APK contents and size before and after Sherpa integration, confirms arm64-only packaging, and proves that incompatible duplicate ONNX Runtime libraries are absent.
- Existing Moonshine, Whisper, IME, recognition-activity, VAD, cancellation, and model-download checks remain regression coverage.

## Out of Scope

- Automatically choosing or blocking models based on phone brand, chipset, or guessed capability.
- Automatically downloading, updating, or deleting model packages.
- Bundling every selectable model in normal APKs.
- Cloud transcription, server-side fallback, or uploading dictated audio.
- Training or fine-tuning adaptation-ready Nemotron 3.5 languages.
- Advertising adaptation-ready languages before working fine-tuned artifacts exist.
- Supporting ABIs other than arm64 in this effort.
- Adding GGUF, MLX, CoreML, or other packaging formats as user-facing model choices.
- Maintaining the custom Rust NVIDIA inference stack after Sherpa parity is established.
- Removing Moonshine or legacy Whisper/GGML recognition.
- Guaranteeing the one-second modern-phone target on lower-end devices.
- Automatically migrating between model versions without user confirmation.

## Further Notes

- The accepted glossary defines Live transcription, Final-only transcription, Streaming profile, Recognition model, Installed model, Recognition language, Performance class, Catching up, and Model artifact.
- The architecture decision records Sherpa-ONNX as the eventual sole NVIDIA runtime and requires a staged cutover.
- Primary-source runtime research found Sherpa support for Parakeet TDT, buffered Parakeet Unified, English Nemotron profiles, and multilingual Nemotron 3.5.
- Sherpa code and model artifacts have separate licenses. Each model entry must carry the correct source and attribution rather than treating the runtime license as covering model weights.
- Approximate sizes supplied during planning are informational only. The final UI must display the actual pinned artifact manifest size.
