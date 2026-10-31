# Shared recognizer: reproducibility gate for #48

Latest owner decision: defer [full FUTO recognizer adoption](../specs/full-futo-recognizer-adoption.md)
on separate branch `codex/issue-48-full-futo-adoption`. This supersedes the recommendations
below. Existing proof results remain valid, but only exercise the view/fork-session
path. Full upstream native-source retrieval is currently blocked.

Recommendation: continue a patched shared-recognizer prototype that reuses
upstream recording/orchestration and preserves fork engines and app features.
The owner reaffirmed this maintenance-reuse goal after the view-only proof.
Keep production unchanged until parity is established. The existing opt-in
view prototype passes native/delivery tests; it does not settle the deeper
migration decision. Full parity and performance comparisons remain unproven.

## Prototype evidence

The imported module is pinned and attributed in [PROVENANCE](../../shared/PROVENANCE.md).
`-PsharedRecognizerProof=true` includes it and the fork adapter under
`app/src/sharedProof`, plus tests under `app/src/sharedProofTest`. The normal
build graph excludes all three. No production entry point is replaced.

The shared view constructs the supplied `ForkRecognizerSession`; it does not
construct shared `AudioRecognizer`, `MultiModelRunner`, `ModelManager` or a
second recorder on this path. The adapter delegates capture, runtime ownership,
finalizer and diagnostics to the existing fork RecordingSession. Added boolean
delivery acknowledgement lets the app report rejected handoffs instead of
implicitly claiming success. The default upstream path remains available for
comparison, but its transcribe.cpp JNI is not provided or exercised.

Verified locally:

| Check | Result |
| --- | --- |
| Shared Kotlin compilation | Passed |
| Shared debug lint | Passed |
| Fork adapter and all dev Android test Kotlin compilation | Passed, including native proof source |
| Existing dev unit suite | 280 discovered: 277 passed, 3 skipped, 0 failures/errors |
| Shared instrumentation APK assembly | Passed |
| Integrated dev APK and app instrumentation APK assembly | Passed |
| App dev debug lint | Passed |
| Shared connected instrumentation execution | 1 test passed after phone reconnection |
| Native ASR4ALL/Orukeet proof execution | Passed: Medium emits live partials; both recognize public JFK audio |
| Shared-path delivery, cancellation and native proof suite | 5 tests passed |
| Microphone platform smoke test | 1 test passed |
| Existing session failure, finalizer, native-editor and history diagnostics checks | 18 tests passed |
| Existing history UI suite on proof APK | 14 passed, 2 failed |
| Existing history UI suite on local baseline APK | 13 passed, 3 failed |
| Load/finalization/memory comparison | Not run |

The initial connected test attempt was blocked by a disconnected phone. After
the owner reconnected it, tests ran on device `R5CY302YRGT`. The native proof explicitly
requires installed Orukeet and included ASR4ALL Medium; it uses the public JFK
fixture and current runtimes. Controlled tests cover streaming/final-only
finalization and cancellation through the real shared view and fork session.
These tests now execute successfully. The Activity check uses a real Android
result caller and the production result-building helper; the editor check uses
an actual EditText/input connection and the production insertion coordinator.
This proves those controlled delivery boundaries, not a full external keyboard
interoperability test or replacement of the production Activity/IME service.

The first Activity harness used ActivityScenario and timed out in its cleanup
path. The final harness uses a real registered Activity result receiver and an
instrumentation monitor; the complete five-test proof suite passes. The existing
history UI failures concern missing Retranscribe nodes during scroll assertions;
they also occur on the pre-proof local baseline APK. No migration-specific cause
has been established, and these failures were not hidden or fixed out of scope.

The first integrated APK build exposed a real native packaging conflict:
both upstream `vad-release.aar` and the fork's WebRTC 2.0.10 AAR provide
`lib/arm64-v8a/libvad_jni.so`. The proof uses only the old AAR's `classes.jar`
as a compile-time dependency, so it cannot overwrite the fork's runtime VAD.
No arbitrary `pickFirst` or JNI/runtime substitution is used. The upstream
default recorder is consequently unsupported in this experimental APK; its
source remains for comparison. An injected null model-run configuration also
avoids constructing the unused shared model catalog.

The first assembled dev proof APK was 131,783,827 bytes and contained 30 unique
ARM64 library entries, no duplicate ZIP entries and no upstream replacement
GGUF asset. Against the existing local dev baseline artifact (132,019,476 bytes),
the VAD, Sherpa JNI and ExecuTorch libraries and both bundled ASR4ALL artifacts
match byte-for-byte. Different build paths/revisions mean the size difference
is not evidence of a migration size benefit. This limited payload audit is not
the release verifier or a JNI execution test.

The current source/build/test delta from upstream, excluding provenance
documentation, is six files, 115 added and 18 removed lines: four modified upstream
files plus a new session interface and shared test. It excludes the separate
fork adapter/tests and build integration. All 29 upstream files were imported;
only selected shared view/UI code executes with the factory. The unused native
and catalog source cannot be counted as exercised upstream reuse. Reproduce the
current delta with `git diff c4590aa -- shared` once the import is tracked.

The prototype identifies additional work rather than proving feature parity:
upstream's UI drops detailed routing, waveform bars, catching-up and ending-soon
states; model/permission failures are mapped to its generic error presentation.
Downloads are deliberately not initiated by this adapter. Accessible partial
text, permission recovery and model-specific language selection need explicit
review. This prototype therefore must not be enabled in production.

## Source baseline

The issue worktree initially pointed at `5d52ccb`. The required fork source was
available locally: `cf83820` plus assessment commit `e2863c5`. This isolated
issue branch was fast-forwarded to `e2863c5`; the integration branch was not
modified. This recovers the included ExecuTorch ASR4ALL Medium baseline and its
existing tests rather than attempting a proof against public master.

Upstream Voice Input source is available in the local Git object database at
`c4590aaebb8d4ba6bfb9ac11e802e6c0519daf9b`. Its actual native gitlink is
`26d75933c16b2cec228e34c69e92a6c3c154b97c` at
`app/src/main/cpp/transcribe.cpp`. Do not confuse this with the older
`b9e8a8e348db9a34052b72f8643bbe4597522181` discussed in the adoption spec.
Its `libs` gitlink is `792a06e2dfd6924d0d2b8131654486b0fafe4d55`.
Neither dependency commit is present in this worktree's parent Git object store;
that check alone does not establish availability in separate submodule stores.
The worktree's six submodules were uninitialized at initial inspection. They
were subsequently initialized at their declared pins. Windows long-path checkout
failures were repaired in these newly created submodule worktrees. The pinned
android-libs VAD AAR is now present and its SHA-256 is
`36a503ca6f48d28c27e32da5b06e1e98f88e75150fc9ece0e8b011a786a247c9`.

## Fresh retrieval checks

Run on October 30, 2026 with terminal credential prompting disabled and the
credential helper disabled for these commands:

```powershell
$env:GIT_TERMINAL_PROMPT='0'
git -c credential.helper= ls-remote https://gitlab.futo.org/keyboard/transcribe.cpp.git HEAD
git -c credential.helper= ls-remote https://gitlab.futo.org/keyboard/android-libs.git HEAD
```

The first fails with `could not read Username ... terminal prompts disabled`.
This establishes anonymous access failure here, not that the repository or pin
does not exist. The second succeeds and advertises HEAD
`be4362cf82022e613cd34ff95d579c8502eaad8e`. HEAD reachability does not validate
the required android-libs commit or the VAD AAR. No alternative native source,
model format, or runtime was substituted. Dependency license completeness remains
unverified until the exact source/artifacts can be inspected.

Java and adb are installed; this worktree has no `local.properties`. Subsequent
builds used `ANDROID_HOME=C:/Users/User/AppData/Local/Android/Sdk`. The first
APK configuration attempt failed on an incomplete new phonon submodule checkout;
that checkout was repaired before retrying. Kotlin and test compilation, unit
tests, shared lint and shared test APK assembly then passed as recorded above.

## Proposed minimum patch and ownership

Reinspection of the pinned upstream sources confirms the existing assessment:
`RecognizerView` stores a concrete `AudioRecognizer` and constructs it with
`ModelManager`; that recognizer constructs `MultiModelRunner` and `AudioRecord`.
Changing a runner alone would leave upstream owning microphone policy.

The smallest candidate that preserves current capture policy is a session
interface plus factory at the shared view's construction boundary. Keep an
upstream default factory for its existing consumer. The fork adapter would own
exactly one existing RecordingSession and map its status, magnitude, partial,
terminal, cancellation and error events to the shared view listener. Factory
selection must occur before any default recorder or model manager is created.
The shared view must not require constructing a concrete model manager merely
to use this factory. Preserve upstream's license headers in any imported source.

| Ownership | Intended contents |
| --- | --- |
| Upstream retained | Shared view state, rendering, accessible UI components and sound behavior where compatible |
| Shared patch | Session interface/factory, removal of mandatory model-manager construction on the injected path, lifecycle/event mapping |
| App retained | Capture/routing/focus/VAD/tail and recording retention, lifecycle/catalog/runtime leases, existing engines, history/retranscription, finalizer, diagnostics, Activity/IME delivery |

This candidate reuses the shared view, **not upstream recording orchestration**.
Whether that is enough maintenance benefit is an explicit comparison criterion.
Do not claim it proves the preferred recording/runner migration. Reusing more
upstream capture code requires additional policy hooks and a measured patch.
The prototype and measured initial patch size are recorded above.

An unmodified import also runs `shared:downloadModel` from `preBuild`, fetching
`asr4all-m-gptq-Q8_0.gguf`. This conflicts with preserving the fork's existing
included model and download ownership. The experimental module must separate
the default runtime/catalog/model-download path from an injected session path.
The shared module declares the Whisper native wrapper but its build script does
not itself provide the app's native runtime. Merely compiling that wrapper would
not prove JNI availability. An adapter-only source set may avoid transcribe.cpp;
its Kotlin compilation has now passed, so native access failure does **not block
this adapter-only source path**. Actual ASR4ALL/Orukeet JNI tests now pass;
other model families and full migration parity remain unverified.

## Decision and remaining gates

Do not replace production on present evidence. Continue the deeper shared-flow
proof rather than treating selective ports as the final answer. The current
proof establishes feasibility but not lower maintenance cost: it retains the
entire fork session while introducing another UI boundary and native dependency
handling. Next inject the fork model runner into shared recording/orchestration
and add retained-audio/session/finalization hooks. Compare this with porting fork
features into the unmodified upstream runtime/catalog. There is no measured
speed/accuracy improvement. Keep the experiment isolated for review.
This is an assessment recommendation, not owner acceptance or a claim that all
#48 acceptance gates passed. ADRs 0001–0004 remain in force; no runtime
replacement is proposed. ADR 0004 and the adoption spec record this recommendation.

If adoption is reopened, use these implementation slices:

1. Resolve exact dependency/source and license provenance for whichever path is
   exercised. If using the default native path, obtain the pinned transcribe.cpp
   source; if avoiding it, demonstrate a build with no native/default catalog
   construction or automatic replacement-model download.
2. Preserve the now-proven experimental factory and ASR4ALL/Orukeet native path.
   Extend coverage for cancel-before-load, stop-before-load, all model families,
   stale callbacks, errors and exactly-once release across the new boundary.
3. Reuse the existing session, lifecycle, history, finalizer, diagnostics and
   insertion tests. Add missing cross-boundary assertions for complete retained
   audio, failed/canceled retranscription preserving old text, cleanup eligibility
   and vocabulary exactly once, and content-free ordinary diagnostics.
4. Extend the controlled Activity/native-editor and microphone smoke checks to
   permission recovery, accessibility, real IME hosting and routing. Compare cold/warm
   load, finalization and peak memory on identical public audio and settings.
5. Count upstream files actually reused and the maintained diff. Compare keeping
   the fork, selective view reuse, and a deeper recording adaptation. Only then
   accept a decision in ADR 0004/spec and define gated migration slices.

The verification table distinguishes successful compile/unit/lint checks from
unperformed device/JNI/feature/performance gates. No release, issue closure,
master merge or production flow replacement was performed.
