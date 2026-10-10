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
