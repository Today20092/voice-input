# Ticket 04: streaming failure recovery categories

Researched 2026-09-30. **No inspected runtime contract admits a production recoverable feed/finalization category.** Keep automatic retained-audio replay disabled until a specific category has stronger evidence. This is a bounded source review, not proof that recovery is impossible.

## Versions and method

The repository's [earlier runtime inspection](moonshine-runtime-segmentation-sources-2026-09-28.md) records installed `ai.moonshine:moonshine-voice:0.0.68`, cached AAR SHA-256 `21a8ab5f7b2e0b962b7ef6f0101a7e19fc716ae82d97c163bbbaa247c3ef4a80`, and source revision `0a99f2e31e6e2913c4c61e4c62f85ce57404ba60`. This review inspected that immutable source revision. The earlier Java bytecode corroboration does not establish native binary/source equivalence. Sherpa inspection uses the maintained runtime's `v1.13.4` source. No build, device, audio experiment, or new artifact bytecode inspection was performed here.

Context7 resolved `/moonshine-ai/moonshine` and `/k2-fsa/sherpa-onnx` and fetched scoped streaming/error documentation. It returned lifecycle APIs and examples, without an explicit safe-replay category. Those current docs are discovery evidence; pinned sources below govern version-specific conclusions. [Current Moonshine API](https://github.com/moonshine-ai/moonshine/blob/main/core/moonshine-c-api.h), [current Sherpa streaming API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/c-api/docs/online-asr.dox).

## Moonshine

The pinned C header declares these error values: `MOONSHINE_ERROR_NONE = 0`, `MOONSHINE_ERROR_UNKNOWN = -1`, `MOONSHINE_ERROR_INVALID_HANDLE = -2`, `MOONSHINE_ERROR_INVALID_ARGUMENT = -3`. It supplies no recoverable/transient stream-failure code. [Pinned header](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/moonshine-c-api.h).

The feed, stop, and stream-transcription C entry points validate the transcriber handle, then catch `std::exception` and collapse failures to `MOONSHINE_ERROR_UNKNOWN`. Thus `-1` cannot distinguish an eligible stream-local condition from memory, model, or other native failures. The existence of non-streaming transcription and fresh-instance creation does not guarantee replay safety after these failures. [Pinned C boundary](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/moonshine-c-api.cpp).

Invalid handles and arguments indicate invalid state/input, not an admitted transient category. Treat all three negative codes as terminal for the proposed replay policy. Do not classify by exception message, generic `RuntimeException`, `IllegalStateException`, or `IOException`. Successful native retirement alone does not resolve the ambiguity of `UNKNOWN`. [Pinned error definitions and descriptions](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/core/moonshine-c-api.cpp), [pinned Android wrapper](https://github.com/moonshine-ai/moonshine/blob/0a99f2e31e6e2913c4c61e4c62f85ce57404ba60/android/java/main/java/ai/moonshine/voice/Transcriber.java).

## Sherpa

`OnlineStream.acceptWaveform` and `inputFinished` delegate to JNI methods returning Kotlin `Unit`; they expose no typed recoverable error or numeric failure result. The C API equivalents return `void`. The C header explicitly prohibits appending more samples after end-of-input. This is normal lifecycle behavior, not a recoverable failure classification. [Pinned Kotlin stream](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/kotlin-api/OnlineStream.kt), [pinned C API](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/c-api/c-api.h).

The inspected public surface provides no documented feed/finalization category authorizing clean same-model full-audio retry. Stream reset and new-stream examples establish ordinary recognition lifecycle only; they do not establish recovery after native failure. This review did not exhaustively audit every JNI/native exception path or every execution provider.

## Admission decision and exclusions

The [SHARE-08 contract](../specs/share-08-recovery-contract.md) already requires an evidenced adapter category before production opt-in. This source review leaves that gate closed. Unknown native failures, closed/not-started channels, lifecycle misuse, load failure, failed retirement, cancellation, memory exhaustion, and corrupt-model failures remain excluded. Backlog status, normal segment completion, and empty transcription are not failures that authorize replay.

Fake-engine tests may prove propagation, cancellation/session guards, retry count, and retained-audio plumbing. They cannot establish native recoverability. To open the gate, obtain an upstream documented category (or reproducible native evidence that distinguishes it), preserve that category across the Android binding, establish safe retirement and replacement, and verify complete-PCM same-model replay without stale delivery. Physical-phone long-dictation, accuracy, memory, and stop-to-final measurements remain outstanding; no phone evidence was collected by this research.
