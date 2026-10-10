# Beta testing and stable promotion

Maintainer workflow recorded 2026-10-10.

## During beta

Publish a signed GitHub prerelease APK containing the combined changes. The
maintainer uses Obtainium to install GitHub versions and tries microphone
dictation during ordinary phone use for a day or two. Address reported issues
in subsequent betas. Do not promote automatically or interpret elapsed time as
approval. Wait until the maintainer says the tested beta is good and requests
stable promotion.

Beta release notes should list all changes that need testing: ASR4ALL models and
PCEC/S1-mini behavior, expandable Model Options, the cleanup settings redesign,
and the thinner waveform and smaller voice-input panel. Include subsequent
fixes and honest verification limits. Keep the full README refresh for stable
promotion; a short, clearly labeled beta announcement can remain in the README.

For each functional change, add its user-visible effect to the beta notes in
the same work session. Record affected documentation in this promotion list
or the release notes' working draft, with an explicit done/pending state. Use
those notes as the stable-promotion inventory instead of reconstructing changes
from memory. Update the full documentation at stable promotion, as requested.

For the 1.4.7 cycle, the full documentation refresh is pending:

- README model starting points and source links.
- Complete model-guide table, including ASR4ALL choices and English Whisper's
  optional legacy role versus multilingual Whisper.
- Current screenshots and explanations for expandable Model Options, Basic
  text cleanup/AI rewrite, and the compact panel/bar waveform.
- Consolidated stable notes, including fixes added after the initial beta.

### Model-guide comparison requested by the maintainer

At stable promotion, make the differences between all selectable models easy to
compare. Use a table or chart with language coverage, download/installed size,
RAM when available, live versus final text, built-in formatting/cleanup, and
publisher accuracy and timing evidence. Link the model cards and identify the
measurement source, hardware, dataset and streaming profile where applicable.

Keep disk size separate from RAM. Label unmeasured RAM or speed as unknown;
do not invent estimates. Publisher WER from different datasets is not a direct
ranking. Our S25 Ultra ASR4ALL fixture figures include the test process and
instrumentation overhead, so they are not universal model-only memory or speed.

Explain that FUTO's ASR4ALL base models have PCEC punctuation, capitalization
and small corrections, and the app skips S1-mini after them. Describe mobile
deployment from the source evidence without claiming all ASR4ALL models beat
every alternative in accuracy or speed.

End the guide with practical starting choices and trade-offs for:

- Slower phones, limited RAM/storage, and stronger phones with more resources.
- English-only dictation with or without live text and built-in cleanup.
- Spanish and supported European languages.
- Arabic and broader multilingual needs, including unsupported languages or
  automatic language detection when the selected model supports it.
- Users who prioritize response time, recognition quality, or keeping legacy
  behavior. Include compatibility and uncertainty alongside the recommendation.

Consider the actual app capabilities and all existing model families, rather
than listing only ASR4ALL or assuming one default suits every user.

## When stable promotion is requested

1. Identify the exact beta the maintainer tested. Promote that implementation,
   with release metadata and documentation changes only, unless new functional
   changes have been explicitly requested and tested.
2. Apply [readme-organizer](C:/Users/User/.codex/skills/readme-organizer/SKILL.md).
   Inspect the whole README, documentation map and actual model catalog. Keep
   installation, badges and useful quick links prominent. Reuse existing guides
   and preserve attribution, privacy defaults, compatibility and storage advice.
3. Refresh the README's model starting points and the complete model guide:
   model names, verified source/package links, languages, live/final behavior,
   download/storage requirements, reasons to choose each, and relevant limits.
   Include all three ASR4ALL base models. Explain built-in PCEC and automatic
   S1-mini bypass. Do not claim an unmeasured universal accuracy advantage.
4. Describe English Whisper as an optional legacy fallback or comparison
   option, rather than necessary for ordinary English dictation. Keep
   multilingual Whisper available and explain its different language use.
   Preserve existing selections and fallback paths.
5. Update waveform and panel descriptions and screenshots, cleanup settings,
   and Model Options documentation. Label historical screenshots. Verify local
   links, anchors, images, remote destinations and badge claims.
6. Write consolidated stable release notes covering the whole beta cycle:
   model additions, cleanup changes, waveform/panel changes, and later fixes.
   Separate maintainer's everyday-use evidence from benchmarks and checks that
   remain incomplete. Do not close unrelated issue acceptance gates implicitly.
7. Set the stable version and a version code higher than every published beta.
   Preserve production application ID, signing key, settings, models and history.
   Run the existing signed release workflow and verify the APK and its signature.
8. Publish a non-prerelease GitHub release as latest. Check release assets and
   updater/Obtainium source visibility. Confirm the stable APK upgrades the beta
   without requiring uninstall or clearing storage.

The initial combined candidate was beta 1; its workflow was canceled before
publication to correct CI packaging memory. The publication candidate is
`v1.4.7-beta.2`. Use the latest actually tested beta when promotion happens.
