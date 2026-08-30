# Harper as a deterministic transcript-cleanup engine

**Research date:** 2026-08-30
**Verdict:** **Run a bounded Android experiment, but do not ship Harper with every rule auto-applied.** `harper-core` is a credible fit for a small, offline, deterministic cleanup tier. It can replace S1-mini for users who only want conservative spelling/grammar/punctuation corrections, and it can catch residual errors after S1-mini. It cannot replace S1-mini's contextual rewriting, tone, or structure controls.

The recommended default pipeline is:

```text
final ASR text
  -> established-English gate
  -> S1-mini, only when the user selected generative cleanup
  -> Harper conservative auto-fix allowlist
  -> existing PersonalVocabulary correction
  -> IME/activity result
```

Keep **Rules (Harper)** and **Smart rewrite (S1-mini)** independently selectable. If both are enabled, run Harper **after** S1, once, and keep personal vocabulary last. Do not run Harper on streaming partials in the first release.

## Why this is worth a spike

### Verified upstream facts

- Harper is an English-only, offline Rust grammar checker. Upstream says it lints in milliseconds, uses less than 1/50 of LanguageTool's memory, and is small enough for WebAssembly; those are upstream project claims, not Android measurements. [Harper README](https://github.com/Automattic/harper/blob/master/README.md)
- `harper-core` is explicitly published as an embeddable Rust library. Its basic API parses text into `Document`, constructs `LintGroup` with the curated `FstDictionary` and a dialect, then calls `lint`. [harper-core README](https://github.com/Automattic/harper/blob/master/harper-core/README.md)
- A lint contains a character span, category, message, priority, and zero or more suggestions. Suggestion operations are `ReplaceWith`, `InsertAfter`, or `Remove`, and the library provides a mechanical `apply` method. [Lint source](https://github.com/Automattic/harper/blob/master/harper-core/src/linting/lint.rs), [Suggestion source](https://github.com/Automattic/harper/blob/master/harper-core/src/linting/suggestion.rs)
- Rules are individually configurable through `LintGroup`; the official language-server integration exposes examples such as spelling, articles, capitalization, quotes, long sentences, repeated words, spaces, and number suffixes. It also exposes American, British, Australian, Canadian, and Indian English dialects. [LintGroup source](https://github.com/Automattic/harper/blob/master/harper-core/src/linting/lint_group/mod.rs), [official integration configuration](https://writewithharper.com/docs/integrations/language-server)
- The curated dictionary is part of Harper's parsing and analysis. The Rust API provides mutable and merged dictionaries; Harper's official integration combines static, user, workspace, and file dictionaries. [Dictionary trait](https://github.com/Automattic/harper/blob/master/harper-core/src/spell/dictionary.rs), [MutableDictionary](https://github.com/Automattic/harper/blob/master/harper-core/src/spell/mutable_dictionary.rs), [MergedDictionary](https://github.com/Automattic/harper/blob/master/harper-core/src/spell/merged_dictionary.rs), [official dictionary docs](https://writewithharper.com/docs/integrations/language-server#dictionaries)
- Harper currently supports only English. Its language server calls mixed-language English isolation “extremely new and unstable,” so this app should reuse its existing established-English gate rather than depend on Harper to isolate English. [language support](https://github.com/Automattic/harper/blob/master/README.md#language-support), [language-server setting](https://writewithharper.com/docs/integrations/language-server)

### What Harper complements, and what it overlaps

Harper is rule- and dictionary-driven. Its output is bounded to explicit edits with locations and reasons. S1-mini is generative: this app prompts it as a speech-to-text normalizer with styling, structure, and context controls, chunks long transcripts, and falls back to the raw transcript on timeout or runtime failure. See [S1MiniPrompt.kt](../../app/src/main/java/org/futo/voiceinput/s1/S1MiniPrompt.kt) and [S1MiniTranscriptCleaner.kt](../../app/src/main/java/org/futo/voiceinput/s1/S1MiniTranscriptCleaner.kt).

That creates a useful division:

| Need | Harper | S1-mini |
|---|---|---|
| Repeated words, spacing, obvious punctuation/capitalization | Strong fit; deterministic | Can do it, but with generative cost/risk |
| Known grammar and usage patterns | Strong fit when a rule exists | Broader contextual reach |
| Misspellings | Strong fit for written typos; less useful when ASR emits a valid wrong word | Sometimes corrects from context |
| Homophones, semantic substitutions, omitted ideas | Limited; often no detectable rule | Better candidate, but can also change meaning |
| Tone, prose structure, lists, contextual rewriting | Not its purpose | Core value |
| Explainability and exact undo | Strong | Weak unless the app computes a diff |

**Inference:** Harper can reduce S1 invocation only by being a separate **Rules-only** mode. The absence or presence of Harper lints is not a reliable automatic gate for S1: a clean Harper result says nothing about whether punctuation restoration, restructuring, or semantic repair is needed. If a user has requested S1 styling/structure, do not silently skip S1.

## Placement alternatives

| Placement | Advantages | Problems | Recommendation |
|---|---|---|---|
| Harper on raw transcript | Lowest-cost standalone cleanup; preserves deterministic audit trail | ASR punctuation may be sparse; valid-word ASR errors evade spellcheck | **Default when S1 is off** |
| S1 then Harper | Catches residual mechanical errors in final user-facing prose | A broad Harper config may “correct” intentional S1 output | **Default when both are on**, with a narrow allowlist |
| Harper then S1 | May normalize obvious noise before generation | S1 can overwrite the fixes; pays both costs; harder to attribute regressions | Do not ship by default |
| Harper before and after S1 | Useful for evaluation and attribution | Duplicate work and higher latency; overlapping edits | Diagnostics/benchmark builds only |
| Harper on streaming partials | Could improve visible partial text | Text is unstable, fixes will flicker, and the current S1 path runs only at finalization | Later experiment, not v1 |

The current final path is already the right seam: `AudioRecognizer` obtains `rawText`, calls S1 once after recording finishes, applies `PersonalVocabulary`, then delivers the result. [AudioRecognizer.kt](../../app/src/main/java/org/futo/voiceinput/AudioRecognizer.kt). Final IME output is committed with `InputConnection.commitText`, while the recognition activity returns `RecognizerIntent` results. [VoiceInputMethodService.kt](../../app/src/main/java/org/futo/voiceinput/VoiceInputMethodService.kt), [RecognizeActivity.kt](../../app/src/main/java/org/futo/voiceinput/RecognizeActivity.kt).

## Safe application policy

The existence of `Suggestion::apply` proves that automatic application is mechanically supported; it does **not** establish that applying the first suggestion from every lint is safe. Official Harper integrations are designed around diagnostics and user-selected code actions, and some lints have multiple or zero suggestions. [Lint API](https://docs.rs/harper-core/latest/harper_core/linting/struct.Lint.html), [Suggestion API](https://docs.rs/harper-core/latest/harper_core/linting/enum.Suggestion.html)

For v1:

1. Remove overlapping lints, then apply edits from the end of the text toward the beginning (or let Rust apply them while tracking updated spans). Harper supplies `remove_overlaps` for prioritizing overlaps. [harper-core API](https://docs.rs/harper-core/latest/harper_core/fn.remove_overlaps.html)
2. Auto-apply only explicitly reviewed rules with exactly one unambiguous suggestion. Start with whitespace, duplicated words, unclosed/simple punctuation, obvious capitalization, and similarly mechanical cases.
3. Do **not** auto-apply spelling, word choice, agreement, regionalism, style/readability, or multi-suggestion lints until a dictated-speech corpus establishes a low false-change rate.
4. Preserve the pre-Harper text for a one-tap **Undo cleanup** action where the result surface allows it. In the recognition-activity path, conservative application or an explicit preview is safer because there may be no keyboard undo surface.
5. Apply `PersonalVocabulary` last. Also merge personal-vocabulary preferred forms into Harper's dictionary before constructing both the `Document` and linter; Harper's parsing metadata also depends on the dictionary, so changing only the spellchecker is insufficient. [Harper repository integration note](https://github.com/Automattic/harper/blob/master/AGENTS.md#dictionary-and-linting-gotchas)

This matters especially for dictated names, brands, acronyms, code terms, numbers, addresses, and deliberate informal speech. Harper also has real false-positive/false-negative issue categories, and its own rule-authoring guidance demands broad positive and negative tests. [Harper issue labels](https://github.com/Automattic/harper/labels), [rule testing guidance](https://writewithharper.com/docs/contributors/testing-strategy)

## Android implementation shape

### Recommended native boundary

Create a small pinned Rust wrapper crate that depends on `harper-core` and exposes one coarse operation:

```text
lint_and_optionally_fix(
  UTF-8 text,
  dialect,
  enabled-rule profile,
  personal dictionary,
  apply mode
) -> { fixed UTF-8 text, applied edits, remaining lints, timing }
```

Return the fully fixed string from Rust rather than editing a Kotlin `String` by native offsets. Harper's `Span<char>` indexes its internal `Vec<char>` (Unicode scalar values), whereas Kotlin/JVM string indexes are UTF-16 code units. Directly treating Harper offsets as Kotlin offsets will break after emoji and other non-BMP characters. [Span source](https://github.com/Automattic/harper/blob/master/harper-core/src/span.rs), [Document source](https://github.com/Automattic/harper/blob/master/harper-core/src/document.rs). Combining marks and grapheme clusters still need corpus tests even if all edits stay in Rust.

Two workable bridges are:

- **Preferred:** build the Rust wrapper as a `staticlib` with a tiny C ABI and call it from a C++ JNI file. This matches the app's existing JNI/CMake conventions and keeps JNI string/error handling in one language.
- **Alternative:** build a Rust `cdylib` that uses JNI directly and package its `.so`. This has fewer bridge layers but introduces Rust-side JNI lifecycle and exception handling.

Rust officially lists `aarch64-linux-android` as a Tier 2 target using the Android NDK, and Android loads native `.so` libraries with `System.loadLibrary`. Android recommends `RegisterNatives` from `JNI_OnLoad` for early signature validation and smaller export surfaces. [Rust Android target support](https://doc.rust-lang.org/rustc/platform-support/android.html), [Android JNI guidance](https://developer.android.com/ndk/guides/jni-tips)

The app currently builds only `arm64-v8a` with NDK 28.2 and already uses one top-level CMake build. Add a Gradle task that invokes Cargo for `aarch64-linux-android`, imports the resulting static/shared library into the existing CMake target (or packages it through `jniLibs`), and makes assemble tasks depend on that output. Android documents both imported prebuilt libraries and native AAR/Prefab packaging; an internal AAR is attractive later if the wrapper becomes reusable. [app/build.gradle](../../app/build.gradle), [CMakeLists.txt](../../app/src/main/cpp/CMakeLists.txt), [Android CMake guidance](https://developer.android.com/ndk/guides/cmake), [native dependency packaging](https://developer.android.com/build/native-dependencies)

Avoid Harper's WebAssembly/JavaScript package here: the app has no JavaScript host, and native Rust avoids adding one.

### Concurrency and lifetime

Upstream describes a `Linter` as stateless but takes `&mut self` for caching. Current crate metadata has a `concurrent` feature, and dictionary traits are `Send + Sync`; this still does not make one mutable linter safe for simultaneous calls without ownership/locking. [Linter trait](https://github.com/Automattic/harper/blob/master/harper-core/src/linting/mod.rs#L369-L381), [dictionary trait](https://github.com/Automattic/harper/blob/master/harper-core/src/spell/dictionary.rs), [crate features](https://crates.io/crates/harper-core)

Use one thread-confined engine instance (or a mutex) on `Dispatchers.Default`. Run it sequentially after S1 rather than competing with S1's CPU/OpenCL work. Cache the curated dictionary/linter between final transcripts, and rebuild only when dialect, rule profile, or personal dictionary changes.

### File touch map

| Area | Likely change |
|---|---|
| `app/src/main/rust/harper_android/` | New pinned Cargo crate, C ABI, panic containment, lint/fix policy |
| `app/src/main/cpp/` | Small JNI adapter and imported Rust target in CMake |
| `app/build.gradle` / CI | Rust toolchain, Android target/linker, reproducible Cargo build, ABI/package checks |
| `HarperTranscriptCleaner.kt` | English gate, coroutine dispatch, config/dictionary cache, fallback and timing |
| `AudioRecognizer.kt` | Insert Harper after optional S1 and before personal-vocabulary replacement |
| `settings/Settings.kt` and cleanup settings page | Mode, dialect, conservative/aggressive profile, undo/diagnostics controls |
| `docs/third-party/` and About/licenses UI | Apache license, attribution, dependency notices and pinned version |

## UX recommendation

Rename the section conceptually from “S1-mini cleanup” to **Transcript cleanup** and offer:

- **Off**
- **Rules (fast, conservative)** — Harper only; recommended default if the experiment passes
- **Smart rewrite** — S1-mini only; current styling/structure/context settings
- **Smart rewrite + rules** — S1 then conservative Harper

Under Rules, expose English dialect and two profiles:

- **Conservative (recommended):** mechanical allowlist, automatic fixes.
- **Review/experimental:** broader grammar/spelling rules; initially diagnostics-only or preview, not silent replacement.

Show “English only” and “runs fully offline.” Keep personal vocabulary shared with both systems. Do not expose hundreds of individual rule toggles in the first UI; retain an internal rule-ID config so advanced controls can be added later without changing the native boundary.

## Privacy, size, maturity, and licensing

- **Privacy:** `harper-core` is local and requires no network service. Adding it does not require retaining transcript data. Keep diagnostics aggregate-only by default, consistent with the existing S1 diagnostics design. [Harper README](https://github.com/Automattic/harper/blob/master/README.md)
- **Size/performance:** crates.io reports the published `harper-core` 2.8.0 source archive at about 1.8 MB. That is **not** the compressed APK delta, installed native size, RAM use, or latency. No first-party Android benchmark or Android binary-size figure was found. Measure all four on physical devices before deciding. [crates.io metadata](https://crates.io/api/v1/crates/harper-core)
- **Maturity/versioning:** as of the research date, crates.io lists 2.8.0 while GitHub has already released 2.9.1. Recent releases are frequent and the API has had breaking refactors, so pin an exact crate version and Cargo lockfile, and upgrade deliberately. [crates.io](https://crates.io/crates/harper-core), [GitHub releases](https://github.com/Automattic/harper/releases)
- **License:** Harper and `harper-core` declare Apache-2.0. That is workable as a separately attributed dependency inside this FUTO Source First-licensed app, but distribution must include the Apache license, preserve notices, mark modifications to Harper if any, and audit the resolved transitive dependency/data licenses. Harper's curated dictionary files are shipped in the Apache-2.0 repository/package; no separate wordlist notice was found in the inspected upstream tree, which should be rechecked at the pinned tag. [Harper license](https://github.com/Automattic/harper/blob/master/LICENSE), [harper-core manifest](https://github.com/Automattic/harper/blob/master/harper-core/Cargo.toml), [Apache 2.0 redistribution conditions](https://www.apache.org/licenses/LICENSE-2.0#redistribution), [this app's license](../../LICENSE.md)

## Risks and failure modes

1. **Confidently wrong fixes:** valid-word ASR substitutions are outside ordinary spellcheck, while grammar/word-choice rules can misread conversational fragments.
2. **Missing punctuation:** a grammar engine has less syntactic evidence when the recognizer emits an unpunctuated run-on transcript.
3. **Double normalization:** S1 and Harper may disagree about dialect, contractions, style, or punctuation.
4. **Names and technical vocabulary:** without merging the app dictionary before document parsing, false spelling/grammar findings will rise.
5. **Fragmentary dictation:** commands, search queries, addresses, and sentence fragments are not edited prose. Use field/context-aware profiles later; initially keep style/readability rules off.
6. **Offset corruption:** Rust character offsets and Kotlin UTF-16 offsets differ. Keep edit application native and test emoji, combining marks, curly apostrophes, RTL text around English, and CJK/non-English bypasses.
7. **Native build supply chain:** adding Cargo/Rust and another native artifact increases CI, reproducibility, symbol, crash, and license-scanning work.
8. **Fast upstream movement:** rule behavior can change between releases even when the bridge compiles; corpus snapshots must gate upgrades.

## Staged go/no-go plan

### Stage 0 — desktop/native spike (estimated 2–4 developer days)

Pin the published crate, build a small C ABI, and run a fixed corpus without Android UI work. Record every lint and proposed edit, but do not auto-apply in the app.

Corpus minimum:

- 200–500 real or consented/anonymized final transcripts across Moonshine, Parakeet, and Whisper;
- raw and S1-cleaned versions paired where available;
- names, brands, numbers, contractions, fragments, messages, searches, lists, profanity, and personal vocabulary;
- emoji, non-BMP characters, combining marks, smart punctuation, and non-English bypass cases.

### Stage 1 — Android benchmark build (estimated 3–5 days)

Add the bridge behind a developer flag. On at least one low-, mid-, and high-tier arm64 device, measure cold/warm latency, p50/p95, native/Java PSS delta, APK and installed-size delta, thermal impact over repeated dictations, and crash/fallback behavior. Compare raw→Harper, raw→S1, raw→S1→Harper, and no cleanup.

### Stage 2 — correctness gate (estimated 1–2 weeks including corpus review)

Blindly rate original versus candidate output. Track:

- beneficial-change rate;
- harmful meaning/name/number change rate;
- false-positive rate by rule;
- residual errors after S1;
- percentage of transcripts satisfactorily handled by Harper-only;
- latency and memory deltas.

**Proposed go criteria (product estimates, not upstream guarantees):** conservative auto-fixes produce zero observed name/number/negation changes in the adjudicated corpus, harmful changes stay below 0.2%, at least 10% of raw transcripts receive a clearly useful correction, warm p95 added latency stays below 100 ms on the low-tier device, and the packaged size/RAM deltas are acceptable for this app's release budget. Otherwise ship diagnostics/preview only or stop.

### Stage 3 — guarded release (estimated 1–2 additional weeks)

Ship Rules as opt-in first, preserve undo/original text, keep a local per-rule aggregate counter without transcript content, and require corpus plus device benchmarks for every Harper upgrade. Promote Conservative Rules to the default only after real-world false-change evidence supports it.

## Decision

**Go for the experiment; defer production commitment.** Harper is architecturally feasible and product-relevant because it offers an offline, explainable, much smaller cleanup class than a 484 MB generative model. The smartest use is not “Harper automatically fixes everything.” It is a conservative deterministic tier that can stand alone for many users and optionally validate S1's final output. The core unknown is not whether it can compile on Android; it is whether a carefully selected rule subset improves dictated text without silently changing names, numbers, negation, or intent. The staged corpus and device gate should answer that before UI and release work expands.
