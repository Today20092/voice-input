# Ticket 39: optional short-transcript AI rewrite bypass

Issue: [#39](https://github.com/Today20092/voice-input/issues/39). The new **Skip short transcripts** toggle lives inside Transcript Cleanup → Rewrite preferences and is **off by default**. Existing saved AI rewrite/model/style preferences are unchanged. Changing this toggle neither enables AI rewrite nor starts a model download.

When enabled, eligible English transcripts of 1–9 words pass through S1 unchanged unless they contain one of the requested fillers (`um`, `uh`, `erm`, `hmm`, `wait`), correction phrases (`you know`, `I mean`, `no actually`, `scratch that`), or an immediately repeated word. Ten or more words retain normal AI rewriting. Matching ignores case and punctuation; straight/curly apostrophes stay inside a word and normalize for repetition comparison. Blank input retains the existing path. Only the first ten tokens are retained, so the heuristic adds bounded intermediate memory even for long dictations.

The existing ASR4ALL built-in bypass, disabled-AI behavior and English eligibility checks precede this policy. A short bypass does not inspect model files, bind/discover the S1 service, run inference or invoke the cleaning callback. It records the allowlisted `short_transcript_bypass` category with zero S1 chunks/native timings and preserves a report UUID where best-effort diagnostics succeed. Requested runtime/preferences remain visible; the shared report projection omits selected-runtime, threads, warm-state and runtime-inspection fields because no runtime executed or was inspected.

Harper Basic text cleanup and personal vocabulary still run afterward. Explicit transcript capture remains available with raw/prepared stages and the bypass reason; the S1 cleaned stage is `not_produced`. The shared delivery pipeline still determines whether Android accepted the final handoff. Ordinary diagnostics contain counts/categories only, never transcript tokens.

## Regression coverage and status

Test-first commit `10aeda2` defines the public policy and existing cleaner/finalizer boundaries before implementation. Its new policy/setting symbols do not exist on the old source, so replay demonstrates compile failure rather than a runtime red; do not report that as a measured behavioral failure.

- `S1MiniShortTranscriptPolicyTest`: 9/10-word boundary, blank, every requested filler/phrase, adjacent repetition, punctuation/case/apostrophes and substring false positives.
- `S1MiniShortTranscriptCleanupTest.optInBypassKeepsLanguageModelAndVocabularyBoundaries`: default-off setting, opted-in unchanged raw text without a model/runtime, filler retaining missing-model behavior, non-English/ASR4ALL gates, disabled-AI behavior, personal vocabulary and raw/not-produced capture semantics.
- `TranscriptCleanupUiTest.shortBypassIsOptInAndDoesNotEnableAiRewrite`: toggling/persistence through the existing settings UI without enabling AI rewrite or requiring a model download.

Only `git diff --check` was run locally. JVM execution, Android-test compilation/UI execution and actual phone latency/quality comparisons remain pending CI/device evidence. No Gradle/native/APK build, emulator or model download ran locally.

This is a disclosed heuristic: a short transcript without the listed signals may still benefit from grammar, tone or structure changes and will skip them when opted in. It does not expand the filler vocabulary beyond the requested examples, promise identical output, or establish Android speed savings from the reporter's desktop measurements. Leave it off when those edits matter.
