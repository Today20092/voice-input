# Shared managed recognition construction (#34)

`managedRecognitionBackend` is now the single mapping from managed runtime/variant and optional language override to the existing `SpeechBackend` adapters. The existing lifecycle model-to-selection conversion is also shared with comparison. No runtime registry, plugin system or new Gradle module was introduced.

Production still updates its explicit settings and owns active-runtime tracking, installation invalidation and cleanup-on-load-failure. Its Parakeet path still acquires the existing warm runtime lease rather than constructing an independent backend. Production Whisper keeps its status/partial/language callbacks. Unknown saved runtime IDs retain the existing Orukeet fallback.

Comparison constructs independent managed adapters, supplies its recording language explicitly to Cohere/Nemotron, and resolves explicit Moonshine/Nemotron/ASR4ALL variants without changing daily settings. Comparison's specialized Whisper model, beam search, hash validation and callback behavior remain unchanged. The constructor performs neither selection writes nor installation invalidation; those policies remain at their existing callers.

## Verification

This is a behavior-preserving consolidation. The catalog-wide JVM selection/readiness test is a preservation check rather than a claimed preexisting functional regression. It enumerates the real catalog, verifies every variant round-trips to its exact readiness model, and checks only its own runtime variant is selected.

- Passed focused JVM command: `:app:testDevDebugUnitTest --tests 'org.futo.voiceinput.recognition.*' --tests org.futo.voiceinput.benchmark.BenchmarkOwnershipTest --tests org.futo.voiceinput.benchmark.BenchmarkCatalogTest --max-workers=2`.
- Passed `:app:compileDevDebugAndroidTestKotlin --max-workers=2`.
- New Android tests construct actual adapters for every managed catalog entry through the shared constructor and comparison entry point, check independent instances, production/comparison type parity and the catalog's streaming contract, and verify no daily backend/variant/language setting changes. Additional multilingual comparison cases cover English, Arabic, French and rejection of unsupported language codes.
- Existing JVM checks retain failed-load closure, cancellation/OOM installation preservation, installation update/session ownership and comparison/dictation exclusion. No private-field reflection, source-string tests or fake configuration registry is used.
- An initial overlapping compile observed the callers after modification but before Gradle discovered the new source file, producing unresolved-symbol errors; a fresh compile passed. This was a build sequencing error, not functional red evidence.

Android construction tests were compiled, not executed: no physical device is attached. They do not claim native model loading/inference parity, warm-runtime reuse measurements or performance results. Device smoke checks with installed assets remain pending, especially actual variant/language use during native decoding. Full integrated tests, lint and signed APK checks belong to the integration run. No version bump or release publication is included.
