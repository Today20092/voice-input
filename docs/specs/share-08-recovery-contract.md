# SHARE-08 recovery contract preparation

Status: proposed contract and test cases only. No executable recovery implementation or production opt-in. Baseline `eb5e1dad`; Moonshine inspection revision `4a12bbaab4d9c2d5f470aa758c153a30f5610efd`.

## Evidence and gate

Moonshine's reviewed change propagates asynchronous worker failures through finalization, checks feed rejection, preserves the original cause, and joins the worker before native release. Its nine reported fake-engine tests establish these behaviors, not native recoverability. Its owner confirmed no evidenced native error type/code for which fresh same-model full-clip transcription is safe. Closed/not-started channels and unknown native exceptions remain terminal. Successful release alone does not prove recoverability. Full Gradle/native/device validation remains pending.

Do not add an unused production exception or generic retry framework merely to make synthetic tests pass. Introduce the smallest typed contract together with the first evidenced adapter category and its session integration. Fake failures may exercise that contract during implementation; they do not authorize a production classifier.

## Proposed typed boundary

An adapter-classified failure must carry a fixed category, a feed or finalization stage, and the original cause. Category values must correspond to documented runtime conditions, not exception-message matching. No production category is currently admitted. Ordinary exceptions retain the existing terminal path.

Before replay, reject cancellation, exhausted memory, and corrupt-model failures anywhere in the cause chain, including coroutine stack-recovery copies. Walk chains with identity-based cycle protection. Backlog callbacks are status, not failures. Lifecycle misuse, load failures, and failed native retirement remain terminal. Do not infer safety from an exception being an IOException or IllegalStateException.

Record the first eligible stream failure once. Disable feed and partial delivery for that runtime while capture continues to the existing complete PCM buffer and audio-history writer. Pre-load buffered-feed failure must follow the same rule without invalidating installed assets. Retain the original error if later cleanup or replay fails.

At Stop, close the failed runtime successfully before allocating its replacement. Check cancellation and session ownership after suspension points. Use exactly one fresh same-model attempt with pinned variant/profile/language and the complete retained PCM, including the existing tail policy. Do not reread changed preferences, silently switch models, restart the microphone, or recursively retry. The fresh attempt uses full-clip transcription and publishes no partials.

Use both session generation and runtime identity to suppress queued callbacks. Generation alone does not reject an old runtime within the same session. Cancellation stops delivery immediately; an in-flight synchronous native call may have to return before release can complete. Never release native state concurrently with that call.

Only a successful raw final enters the existing history, S1, Harper, personal-vocabulary and final-delivery sequence. Replay failure follows the established error/retranscription path. Standard diagnostics record fixed original/replay categories without audio, transcript, or arbitrary exception-message content. Remove recursive recognition OOM retry when integrating this behavior; inspect the separate model-load OOM retry against the ticket's exclusion.

## Ownership and integration order

- Preserve SHARE-02 `2c8518f415f8d8e15bcbeb70583fde705025c413` checks at legacy partial, streaming partial, and final Main dispatch.
- Incorporate SHARE-07's queued Catching up callback guard once its reviewed revision is available.
- SHARE-06 owns recorder creation/release/reset routing hooks. Replay does not acquire routing; stale cleanup cannot affect a newer capture. Leave appendSamples retention, tail drain and history continuity intact.
- Preserve SHARE-06's proposed CaptureFailed stop handling: finalize retained audio without tail reads after an unexpected recorder exit. Recorder failure alone does not classify a stream failure as recoverable.
- Moonshine owns worker propagation/native cleanup in `4a12bba`. SHARE-08 owns session recovery and classification integration. Agree any adapter classification with its owner before editing it.
- Obtain the coordinator's integrated base before production changes. The restored SHARE-08 branch does not yet contain these changes.

## Runnable tests to implement with the boundary

Use existing Kotlin/JUnit fake backend conventions. Assert observable samples, calls, results and cancellation, rather than helper internals.

| Scenario | Required assertion |
| --- | --- |
| Eligible pre-load or live feed failure | Capture continues; Stop passes all retained samples in order, including tail, to one fresh backend. |
| Eligible async feed/finalization failure after a partial | Partial never becomes final; original failure reaches classification; one fresh attempt occurs. |
| Old-runtime retirement | Close finishes before replacement creation; close failure prevents replacement. |
| Settings change during capture/recovery | Replacement retains the original model, profile and language without overwriting a newer saved selection. |
| Replacement load/transcribe failure | No third attempt; original and secondary failures remain distinguishable. |
| Cancel/reset during close, load or transcription | No final/cleanup delivery; late resources release without touching the newer session. |
| Late partial or Catching up callback | Old generation and old same-generation runtime callbacks are both ignored at dispatch. |
| Excluded errors, direct and wrapped | Cancellation, OOM, corruption and unknown errors never create a recovery backend; cyclic causes terminate classification. |
| Successful recovery | One raw final, one existing cleanup/vocabulary pipeline, one final delivery; retained history remains available. |
| Healthy stream and ordinary backlog | No recovery; existing finalization and Catching up behavior preserved. |

Focused tests do not replace assembly/lint or an injected failure and canceled recovery on the user's phone. Request the shared slot before Gradle/native builds and use at most two workers. No speed claim is supported.
