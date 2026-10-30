# Next release notes

## Confirmed fixes to include

- Fixed Nemotron 3.5 Multilingual downloads that could not complete in 1.4.5
  ([issue #7](https://github.com/Today20092/voice-input/issues/7)). Unused
  `test_wavs/en.wav` and `test_wavs/ja.wav` samples no longer block installation;
  only the four recognition files are required. The shared downloader also
  supports nested paths. The fix was offered in
  [1.4.6 Share beta 5](v1.4.6-share-beta.5.md).

## Reporter verification

Recorded on 2026-10-04: Palo007 confirmed that the Nemotron download now works
in [their reply](https://github.com/Today20092/voice-input/issues/7#issuecomment-5966271935)
to the beta 5 test request. The original report lists a Poco X8 Pro running
Android 16 / HyperOS. The reply does not explicitly confirm the installed beta
version or a successful dictation test.

Share beta 7 retains the fix and records the reporter confirmation. Close issue
#7 after publishing that beta, limited to the reported download failure. Carry
the same fix and verification limits into the next stable release notes.

## Share beta 7 UI milestone

- Consistent cards and Details fields across every model; source and package buttons.
- Groups for text while speaking, buffered updates, and text after Stop, ordered
  by download size within each group while keeping model families together.
- Separate download size and unpacked model size, without implying measured RAM.
- FUTO Keyboard setup entry hidden pending upstream release support (#16),
  with the implementation retained.
- Fresh S25 Ultra screenshots and release checks documented in
  [beta 7 notes](v1.4.6-share-beta.7.md).

Issues #8 and #10–#16 retain their incomplete device, lifecycle, calibration,
or upstream requirements. This beta does not complete the broader stable-release gate.
